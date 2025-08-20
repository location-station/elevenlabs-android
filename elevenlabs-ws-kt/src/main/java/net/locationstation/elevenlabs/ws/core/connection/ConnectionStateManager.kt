package net.locationstation.elevenlabs.ws.core.connection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.locationstation.elevenlabs.ws.logging.Logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages WebSocket connection state transitions and related events.
 * This class ensures thread-safe state management and validates all state transitions.
 */
class ConnectionStateManager {
    
    companion object {
        private const val TAG = "ConnectionStateManager"
    }
    
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    /**
     * Current connection state as a StateFlow for observing state changes.
     */
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<ConnectionEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    /**
     * Connection events as a SharedFlow for observing events.
     */
    val events: SharedFlow<ConnectionEvent> = _events.asSharedFlow()
    
    // Metrics tracking
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val bytesTransferred = AtomicLong(0)
    private var lastLatencyMs: Long = 0
    private var connectionStartTime: Long = 0
    
    // Thread safety
    private val stateMutex = Mutex()
    
    /**
     * Attempts to transition to a new state.
     * 
     * @param newState The desired new state
     * @return true if the transition was successful, false otherwise
     */
    suspend fun transitionTo(newState: ConnectionState): Boolean = stateMutex.withLock {
        val oldState = _state.value
        
        if (isValidTransition(oldState, newState)) {
            _state.value = newState
            
            // Track connection start time for metrics
            if (newState is ConnectionState.Connected) {
                connectionStartTime = System.currentTimeMillis()
                resetMetrics()
            }
            
            // Emit state change event
            _events.tryEmit(ConnectionEvent.StateChanged(oldState, newState))
            
            logStateTransition(oldState, newState)
            handleSideEffects(oldState, newState)
            
            return true
        } else {
            Logger.w(
                TAG, 
                "Invalid state transition from ${oldState::class.simpleName} to ${newState::class.simpleName}"
            )
            return false
        }
    }
    
    /**
     * Handles side effects of state transitions.
     */
    private suspend fun handleSideEffects(from: ConnectionState, to: ConnectionState) {
        when (to) {
            is ConnectionState.Connected -> {
                // Emit initial quality as GOOD when connected
                _events.tryEmit(ConnectionEvent.QualityChanged(ConnectionQuality.GOOD))
            }
            
            is ConnectionState.Disconnected -> {
                // Emit offline quality when disconnected
                _events.tryEmit(ConnectionEvent.QualityChanged(ConnectionQuality.OFFLINE))
                
                // Emit final metrics if we were tracking them
                if (connectionStartTime > 0) {
                    emitMetricsUpdate()
                }
            }
            
            else -> { /* No side effects for other states */ }
        }
    }
    
    /**
     * Validates if a state transition is allowed.
     */
    private fun isValidTransition(
        from: ConnectionState, 
        to: ConnectionState
    ): Boolean {
        return when (from) {
            is ConnectionState.Idle -> 
                to is ConnectionState.Connecting
                
            is ConnectionState.Connecting -> 
                to is ConnectionState.Connected || 
                to is ConnectionState.Failed ||
                to is ConnectionState.Disconnecting
                
            is ConnectionState.Connected -> 
                to is ConnectionState.Disconnecting || 
                to is ConnectionState.Reconnecting
                
            is ConnectionState.Reconnecting -> 
                to is ConnectionState.Connected || 
                to is ConnectionState.Failed ||
                to is ConnectionState.Disconnected
                
            is ConnectionState.Disconnecting -> 
                to is ConnectionState.Disconnected
                
            is ConnectionState.Disconnected -> 
                to is ConnectionState.Connecting || 
                to is ConnectionState.Idle
                
            is ConnectionState.Failed -> 
                to is ConnectionState.Connecting || 
                to is ConnectionState.Idle
        }
    }
    
    /**
     * Logs state transitions for debugging.
     */
    private fun logStateTransition(from: ConnectionState, to: ConnectionState) {
        val fromName = from::class.simpleName ?: "Unknown"
        val toName = to::class.simpleName ?: "Unknown"
        
        Logger.i(TAG, "State transition: $fromName → $toName")
        
        // Log additional context for specific states
        when (to) {
            is ConnectionState.Connected -> 
                Logger.d(TAG, "Connected with session ID: ${to.sessionId}")
            is ConnectionState.Reconnecting -> 
                Logger.d(TAG, "Reconnecting (attempt #${to.attemptNumber}): ${to.reason}")
            is ConnectionState.Failed -> 
                Logger.e(TAG, "Connection failed", to.error)
            is ConnectionState.Disconnected -> 
                Logger.d(TAG, "Disconnected: ${to.reason}, can reconnect: ${to.canReconnect}")
            else -> { /* No additional logging needed */ }
        }
    }
    

    
    /**
     * Updates message sent counter.
     */
    fun incrementMessagesSent() {
        messagesSent.incrementAndGet()
    }
    
    /**
     * Updates message received counter.
     */
    fun incrementMessagesReceived() {
        messagesReceived.incrementAndGet()
    }
    
    /**
     * Updates bytes transferred counter.
     * 
     * @param bytes Number of bytes transferred
     */
    fun addBytesTransferred(bytes: Long) {
        bytesTransferred.addAndGet(bytes)
    }
    
    /**
     * Updates connection latency.
     * 
     * @param latencyMs Latency in milliseconds
     */
    suspend fun updateLatency(latencyMs: Long) {
        lastLatencyMs = latencyMs
        
        // Determine connection quality based on latency
        val quality = when {
            latencyMs < 50 -> ConnectionQuality.EXCELLENT
            latencyMs < 150 -> ConnectionQuality.GOOD
            latencyMs < 300 -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
        
        _events.tryEmit(ConnectionEvent.QualityChanged(quality))
    }
    
    /**
     * Emits current metrics as an event.
     */
    suspend fun emitMetricsUpdate() {
        _events.tryEmit(
            ConnectionEvent.MetricsUpdate(
                latencyMs = lastLatencyMs,
                messagesSent = messagesSent.get(),
                messagesReceived = messagesReceived.get(),
                bytesTransferred = bytesTransferred.get()
            )
        )
    }
    
    /**
     * Resets all metrics counters.
     */
    private fun resetMetrics() {
        messagesSent.set(0)
        messagesReceived.set(0)
        bytesTransferred.set(0)
        lastLatencyMs = 0
    }
    
    /**
     * Gets the current connection duration in milliseconds.
     * 
     * @return Duration in milliseconds, or 0 if not connected
     */
    fun getConnectionDuration(): Long {
        val currentState = _state.value
        return if (currentState is ConnectionState.Connected) {
            System.currentTimeMillis() - currentState.connectedAt
        } else {
            0
        }
    }
    
    /**
     * Checks if the connection is currently active.
     */
    fun isConnected(): Boolean {
        return _state.value is ConnectionState.Connected
    }
    
    /**
     * Checks if the connection is in a transitional state.
     */
    fun isTransitioning(): Boolean {
        return when (_state.value) {
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            is ConnectionState.Disconnecting -> true
            else -> false
        }
    }
}
