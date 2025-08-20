package net.locationstation.elevenlabs.ws

import net.locationstation.elevenlabs.ws.internal.ElevenLabsWebSocketListener
import net.locationstation.elevenlabs.ws.logging.Logger
import net.locationstation.elevenlabs.ws.model.UserAudioChunk
import net.locationstation.elevenlabs.ws.model.WebSocketMessage
import net.locationstation.elevenlabs.ws.service.ElevenLabsSignedUrlService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit

/**
 * A client for interacting with the ElevenLabs Conversational AI WebSocket API.
 * This client authenticates by retrieving a signed URL from a user-provided webhook.
 *
 * @param client The OkHttpClient to use for the WebSocket connection.
 * @param agentId The ID of the agent to converse with.
 * @param signedUrlService A service to retrieve a signed websocket URL.
 */
class ElevenLabsClient internal constructor(
    private val client: OkHttpClient,
    private val agentId: String,
    private val signedUrlService: ElevenLabsSignedUrlService
) {
    private var webSocket: WebSocket? = null
    private var clientScope: CoroutineScope? = null

    private val _messages = MutableSharedFlow<WebSocketMessage>()
    /**
     * A hot flow of incoming messages from the WebSocket.
     * Use this for reactive, event-driven handling of messages.
     * This flow remains active for the duration of the connection.
     */
    val messages = _messages.asSharedFlow()

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        explicitNulls = false  // Don't include null values in serialized JSON
    }

    /**
     * Starts a new conversation. This is a suspend function.
     * It first asynchronously fetches a signed URL and then establishes the WebSocket connection.
     *
     * @param listener A listener to handle WebSocket events via callbacks.
     * @throws Exception if fetching the signed URL fails.
     */
    suspend fun startConversation(listener: ConversationListener) {
        if (webSocket != null) {
            Logger.w("Conversation already started. Please stop the existing one first.")
            return
        }

        // Create a new scope for this connection's lifecycle
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        clientScope = scope

        // Asynchronously get the signed URL for the conversation.
        // This will propagate exceptions to the caller (e.g., for network failures).
        val signedUrl = signedUrlService.getSignedUrl(agentId)
        Logger.d("Successfully retrieved signed URL.")

        val request = Request.Builder()
            .url(signedUrl)
            .build()

        val internalListener = ElevenLabsWebSocketListener(json, listener, _messages, scope) { webSocket }
        webSocket = client.newWebSocket(request, internalListener)
    }

    /**
     * Sends a structured message (e.g., UserMessage, PongMessage) to the server.
     *
     * @param message The WebSocketMessage to send. It will be serialized to JSON.
     * @return True if the message was enqueued to be sent, false otherwise.
     */
    fun sendMessage(message: WebSocketMessage): Boolean {
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
     * Sends a chunk of user audio to the server.
     * Note: This uses a special JSON structure.
     *
     * @param chunk The audio chunk to send.
     * @return True if the chunk was enqueued to be sent, false otherwise.
     */
    fun sendAudioChunk(chunk: UserAudioChunk): Boolean {
        return try {
            val jsonString = json.encodeToString(chunk)
//            Logger.v("Sending audio chunk: %s", jsonString)
            webSocket?.send(jsonString) ?: false
        } catch (e: Exception) {
            Logger.e(e, "Failed to serialize and send audio chunk.")
            false
        }
    }

    /**
     * Stops the current conversation, closes the WebSocket connection, and cancels related coroutines.
     */
    fun stopConversation() {
        Logger.d("Stopping conversation.")
        webSocket?.close(1000, "Client closed connection.")
        webSocket = null
        clientScope?.cancel("Client stopped conversation.")
        clientScope = null
    }

    /**
     * A builder for convenient setup of the [ElevenLabsClient].
     */
    class Builder {
        private var agentId: String? = null
        private var webhookUrl: String? = null
        private var client: OkHttpClient? = null

        fun agentId(id: String) = apply { this.agentId = id }
        fun webhookUrl(url: String) = apply { this.webhookUrl = url }
        fun client(client: OkHttpClient) = apply { this.client = client }

        fun build(): ElevenLabsClient {
            val finalClient = client ?: OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build()

            val finalWebhookUrl = requireNotNull(webhookUrl) { "Webhook URL is required." }
            val signedUrlService = ElevenLabsSignedUrlService(finalWebhookUrl)

            return ElevenLabsClient(
                client = finalClient,
                agentId = requireNotNull(agentId) { "Agent ID is required." },
                signedUrlService = signedUrlService
            )
        }
    }
}