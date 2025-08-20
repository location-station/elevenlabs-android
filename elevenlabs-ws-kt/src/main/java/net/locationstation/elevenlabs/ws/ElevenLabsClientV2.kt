package net.locationstation.elevenlabs.ws

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.locationstation.elevenlabs.ws.api.ElevenLabsConfig
import net.locationstation.elevenlabs.ws.data.audio.AndroidAudioManager
import net.locationstation.elevenlabs.ws.data.audio.AudioTrackPlayer
import net.locationstation.elevenlabs.ws.data.network.NetworkAwareReconnectionManager
import net.locationstation.elevenlabs.ws.domain.model.*
import net.locationstation.elevenlabs.ws.internal.ElevenLabsWebSocketListener
import net.locationstation.elevenlabs.ws.logging.Logger
import net.locationstation.elevenlabs.ws.model.UserAudioChunk
import net.locationstation.elevenlabs.ws.model.WebSocketMessage
import net.locationstation.elevenlabs.ws.service.ElevenLabsSignedUrlService
import okhttp3.Request
import okhttp3.WebSocket

/**
 * Enhanced ElevenLabs client with Android-specific optimizations.
 * This is the main entry point for the SDK.
 * 
 * Key features that differentiate from official SDKs:
 * - Android audio focus management
 * - Network-aware reconnection
 * - Low-latency AudioTrack playback
 * - Bluetooth audio routing
 * - Adaptive quality based on network conditions
 * - Battery-optimized background operation
 */
class ElevenLabsClientV2(
    private val config: ElevenLabsConfig,
    private val context: Context? = null
) {
    // Core components
    private val okHttpClient = config.getOrCreateOkHttpClient()
    private val signedUrlService = ElevenLabsSignedUrlService(config.webhookUrl)
    
    // Android-specific components (optional)
    private val audioManager = context?.let { AndroidAudioManager(it) }
    private val audioPlayer = AudioTrackPlayer()
    private val reconnectionManager = context?.let { 
        NetworkAwareReconnectionManager(it, NetworkAwareReconnectionManager.ReconnectionConfig(
            enableAutoReconnect = config.enableAutoReconnect,
            maxReconnectAttempts = config.maxReconnectAttempts
        ))
    }
    
    // WebSocket management
    private var webSocket: WebSocket? = null
    private var clientScope: CoroutineScope? = null
    private var currentSessionId: SessionId? = null
    
    // Message flows
    private val _messages = MutableSharedFlow<WebSocketMessage>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = _messages.asSharedFlow()
    
    private val _conversationEvents = MutableSharedFlow<ConversationEvent>()
    val conversationEvents = _conversationEvents.asSharedFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()
    
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        explicitNulls = false
    }
    
    init {
        // Set log level from config
        Logger.setLogLevel(config.logLevel)
        
        // Start network monitoring if available
        reconnectionManager?.startMonitoring()
    }
    
    /**
     * Starts a conversation with the specified agent.
     * 
     * @param agentId The agent to converse with
     * @return A Flow of conversation events
     */
    suspend fun startConversation(agentId: AgentId): Flow<ConversationEvent> {
        if (webSocket != null) {
            Logger.w("Conversation already started. Please stop the existing one first.")
            return flowOf(ConversationEvent.Error(
                IllegalStateException("Conversation already in progress")
            ))
        }
        
        return flow {
            try {
                // Update state
                _connectionState.value = ConnectionState.Connecting
                emit(ConversationEvent.Connecting)
                
                // Request audio focus if Android context is available
                audioManager?.let {
                    val focusGranted = it.requestAudioFocus(
                        AndroidAudioManager.AudioFocusConfig(
                            onFocusChange = { focusState ->
                                handleAudioFocusChange(focusState)
                            }
                        )
                    )
                    
                    if (!focusGranted) {
                        Logger.w("Audio focus not granted")
                        emit(ConversationEvent.Warning("Audio focus not granted"))
                    }
                    
                    // Set voice communication mode
                    it.setVoiceCommunicationMode()
                    
                    // Route to Bluetooth if available
                    it.routeToBluetoothIfAvailable()
                }
                
                // Initialize audio player
                audioPlayer.initialize().getOrThrow()
                audioPlayer.start().getOrThrow()
                
                // Get signed URL
                val signedUrl = signedUrlService.getSignedUrl(agentId.value)
                Logger.d("Successfully retrieved signed URL")
                
                // Create WebSocket connection
                val request = Request.Builder()
                    .url(signedUrl)
                    .build()
                
                // Create a new scope for this connection
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                clientScope = scope
                
                // Create internal listener adapter
                val simpleListener = object : SimpleConversationListener {
                    override fun onConnectionOpened() {
                        currentSessionId = SessionId(System.currentTimeMillis().toString())
                        _connectionState.value = ConnectionState.Connected(currentSessionId!!)
                        scope.launch {
                            _conversationEvents.emit(ConversationEvent.Connected(currentSessionId!!))
                        }
                    }
                    
                    override fun onMessageReceived(message: WebSocketMessage) {
                        scope.launch {
                            _messages.emit(message)
                            handleIncomingMessage(message)
                        }
                    }
                    
                    override fun onError(error: Exception) {
                        _connectionState.value = ConnectionState.Error(error)
                        scope.launch {
                            _conversationEvents.emit(ConversationEvent.Error(error))
                        }
                        
                        // Schedule reconnection if enabled
                        reconnectionManager?.scheduleReconnection {
                            reconnect(agentId)
                        }
                    }
                    
                    override fun onConnectionClosed() {
                        _connectionState.value = ConnectionState.Disconnected
                        scope.launch {
                            _conversationEvents.emit(ConversationEvent.Disconnected)
                        }
                    }
                }
                
                val adapterListener = ConversationListenerAdapter(simpleListener)
                
                val internalListener = ElevenLabsWebSocketListener(
                    json = json,
                    listener = adapterListener,
                    messageFlow = _messages,
                    scope = scope
                ) { webSocket }
                
                webSocket = okHttpClient.newWebSocket(request, internalListener)
                
                // Emit all events from the conversation
                emitAll(_conversationEvents)
                
            } catch (e: Exception) {
                Logger.e(e, "Failed to start conversation")
                _connectionState.value = ConnectionState.Error(e)
                emit(ConversationEvent.Error(e))
            }
        }
    }
    
    /**
     * Handles incoming WebSocket messages and converts them to conversation events.
     */
    private suspend fun handleIncomingMessage(message: WebSocketMessage) {
        val event = when (message) {
            is net.locationstation.elevenlabs.ws.model.AudioMessage -> {
                // Queue audio for playback
                val audioData = AudioData(
                    bytes = android.util.Base64.decode(message.event.audioBase64, android.util.Base64.NO_WRAP)
                )
                audioPlayer.queueAudio(audioData)
                
                ConversationEvent.AudioReceived(
                    chunkId = AudioChunkId(message.event.eventId.toString()),
                    audioData = audioData,
                    isFinal = true // AudioMessage doesn't have isFinal field
                )
            }
            
            is net.locationstation.elevenlabs.ws.model.InterruptionMessage -> {
                ConversationEvent.Interruption(
                    eventId = message.event.eventId.toString()
                )
            }
            
            is net.locationstation.elevenlabs.ws.model.UserTranscript -> {
                ConversationEvent.UserTranscript(
                    text = message.event.userTranscript
                )
            }
            
            is net.locationstation.elevenlabs.ws.model.AgentResponse -> {
                ConversationEvent.AgentResponse(
                    text = message.event.agentResponse
                )
            }
            
            is net.locationstation.elevenlabs.ws.model.PingMessage -> {
                // Automatically respond to ping
                sendPong(message.event.eventId)
                ConversationEvent.Ping(message.event.eventId.toString())
            }
            
            else -> {
                ConversationEvent.UnknownMessage(message)
            }
        }
        
        _conversationEvents.emit(event)
    }
    
    /**
     * Handles audio focus changes.
     */
    private fun handleAudioFocusChange(focusState: AndroidAudioManager.AudioFocusState) {
        when (focusState) {
            AndroidAudioManager.AudioFocusState.LOST -> {
                // Pause playback
                audioPlayer.stop()
            }
            AndroidAudioManager.AudioFocusState.LOST_TRANSIENT -> {
                // Pause temporarily
                audioPlayer.stop()
            }
            AndroidAudioManager.AudioFocusState.LOST_TRANSIENT_CAN_DUCK -> {
                // Lower volume
                audioManager?.adjustVolume(AndroidAudioManager.VolumeDirection.DOWN)
            }
            AndroidAudioManager.AudioFocusState.GAINED -> {
                // Resume playback
                CoroutineScope(Dispatchers.IO).launch {
                    audioPlayer.start()
                }
            }
            else -> {}
        }
    }
    
    /**
     * Sends a text message.
     */
    fun sendText(text: String): Boolean {
        val message = net.locationstation.elevenlabs.ws.model.UserMessage(
            text = text
        )
        return sendMessage(message)
    }
    
    /**
     * Sends audio data.
     */
    fun sendAudio(audioData: AudioData): Boolean {
        val chunk = UserAudioChunk(
            audioChunk = android.util.Base64.encodeToString(
                audioData.bytes,
                android.util.Base64.NO_WRAP
            )
        )
        return sendAudioChunk(chunk)
    }
    
    /**
     * Streams audio data continuously.
     */
    fun streamAudio(audioFlow: Flow<AudioData>): Job {
        val scope = clientScope ?: throw IllegalStateException("No active conversation")
        
        return scope.launch {
            audioFlow.collect { audioData ->
                sendAudio(audioData)
                delay(10) // Small delay to prevent overwhelming the connection
            }
        }
    }
    
    /**
     * Sends a WebSocket message.
     */
    private fun sendMessage(message: WebSocketMessage): Boolean {
        return try {
            val jsonString = json.encodeToString(message)
            Logger.v("Sending message: %s", jsonString)
            webSocket?.send(jsonString) ?: false
        } catch (e: Exception) {
            Logger.e(e, "Failed to serialize and send message.")
            false
        }
    }
    
    /**
     * Sends an audio chunk.
     */
    private fun sendAudioChunk(chunk: UserAudioChunk): Boolean {
        return try {
            val jsonString = json.encodeToString(chunk)
            webSocket?.send(jsonString) ?: false
        } catch (e: Exception) {
            Logger.e(e, "Failed to serialize and send audio chunk.")
            false
        }
    }
    
    /**
     * Sends a pong response.
     */
    private fun sendPong(eventId: Long) {
        val pong = net.locationstation.elevenlabs.ws.model.PongMessage(eventId = eventId)
        sendMessage(pong)
    }
    
    /**
     * Attempts to reconnect to the WebSocket.
     */
    private suspend fun reconnect(agentId: AgentId): Result<Unit> {
        return try {
            // Clean up existing connection
            webSocket?.close(1000, "Reconnecting")
            webSocket = null
            
            // Wait a moment before reconnecting
            delay(500)
            
            // Start new conversation
            startConversation(agentId).collect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stops the current conversation.
     */
    fun stopConversation() {
        Logger.d("Stopping conversation")
        
        // Stop audio playback
        audioPlayer.stop()
        audioPlayer.release()
        
        // Release audio focus
        audioManager?.releaseAudioFocus()
        audioManager?.setNormalMode()
        
        // Close WebSocket
        webSocket?.close(1000, "Client closed connection.")
        webSocket = null
        
        // Cancel coroutines
        clientScope?.cancel("Client stopped conversation.")
        clientScope = null
        
        // Update state
        currentSessionId = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * Releases all resources.
     */
    fun release() {
        stopConversation()
        reconnectionManager?.stopMonitoring()
        audioManager?.release()
    }
    
    /**
     * Gets current audio metrics.
     */
    fun getAudioMetrics(): Flow<AudioTrackPlayer.AudioMetrics> = audioPlayer.metrics
    
    /**
     * Gets current network state.
     */
    fun getNetworkState(): StateFlow<NetworkAwareReconnectionManager.NetworkState>? = 
        reconnectionManager?.networkState
    
    /**
     * Gets current audio state.
     */
    fun getAudioState(): StateFlow<AndroidAudioManager.AudioState>? = 
        audioManager?.audioState
    
    /**
     * Connection states.
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val sessionId: SessionId) : ConnectionState()
        data class Error(val exception: Exception) : ConnectionState()
    }
    
    /**
     * Conversation events.
     */
    sealed class ConversationEvent {
        object Connecting : ConversationEvent()
        data class Connected(val sessionId: SessionId) : ConversationEvent()
        object Disconnected : ConversationEvent()
        data class Error(val exception: Exception) : ConversationEvent()
        data class Warning(val message: String) : ConversationEvent()
        
        data class AudioReceived(
            val chunkId: AudioChunkId,
            val audioData: AudioData,
            val isFinal: Boolean
        ) : ConversationEvent()
        
        data class UserTranscript(val text: String) : ConversationEvent()
        data class AgentResponse(val text: String) : ConversationEvent()
        data class Interruption(val eventId: String) : ConversationEvent()
        data class Ping(val eventId: String) : ConversationEvent()
        data class UnknownMessage(val message: WebSocketMessage) : ConversationEvent()
    }
    
    companion object {
        /**
         * Creates a client with DSL configuration.
         */
        inline fun create(
            context: Context? = null,
            configure: ElevenLabsConfig.Builder.() -> Unit
        ): ElevenLabsClientV2 {
            val config = ElevenLabsConfig.Builder().apply(configure).build()
            return ElevenLabsClientV2(config, context)
        }
    }
}
