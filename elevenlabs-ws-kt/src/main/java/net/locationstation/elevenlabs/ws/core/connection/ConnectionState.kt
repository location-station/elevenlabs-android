package net.locationstation.elevenlabs.ws.core.connection

/**
 * Represents all possible states of the WebSocket connection.
 */
sealed class ConnectionState {
    /**
     * Initial state - no connection attempt has been made.
     */
    object Idle : ConnectionState()
    
    /**
     * Actively attempting to establish a connection.
     * 
     * @property attemptNumber Current connection attempt number
     * @property startTime Timestamp when connection attempt started
     */
    data class Connecting(
        val attemptNumber: Int = 1,
        val startTime: Long = System.currentTimeMillis()
    ) : ConnectionState()
    
    /**
     * Successfully connected and ready for communication.
     * 
     * @property sessionId Unique identifier for this session
     * @property connectedAt Timestamp when connection was established
     */
    data class Connected(
        val sessionId: String,
        val connectedAt: Long = System.currentTimeMillis()
    ) : ConnectionState()
    
    /**
     * Connection lost, attempting to reconnect.
     * 
     * @property attemptNumber Current reconnection attempt number
     * @property nextRetryIn Milliseconds until next retry attempt
     * @property reason Human-readable reason for reconnection
     */
    data class Reconnecting(
        val attemptNumber: Int,
        val nextRetryIn: Long,
        val reason: String
    ) : ConnectionState()
    
    /**
     * In the process of disconnecting.
     * 
     * @property reason The reason for disconnection
     */
    data class Disconnecting(
        val reason: DisconnectReason
    ) : ConnectionState()
    
    /**
     * Disconnected from the server.
     * 
     * @property reason The reason for disconnection
     * @property canReconnect Whether automatic reconnection should be attempted
     */
    data class Disconnected(
        val reason: DisconnectReason,
        val canReconnect: Boolean = false
    ) : ConnectionState()
    
    /**
     * Connection failed with an error.
     * 
     * @property error The exception that caused the failure
     * @property canRetry Whether the connection can be retried
     */
    data class Failed(
        val error: Throwable,
        val canRetry: Boolean = false
    ) : ConnectionState()
}

/**
 * Reasons for disconnection.
 */
enum class DisconnectReason {
    /** User explicitly closed the connection */
    USER_INITIATED,
    /** Server closed the connection */
    SERVER_INITIATED,
    /** Network connectivity was lost */
    NETWORK_ERROR,
    /** Authentication credentials were invalid */
    AUTHENTICATION_FAILED,
    /** Connection timed out */
    TIMEOUT,
    /** Unknown disconnection reason */
    UNKNOWN
}

/**
 * Events related to connection state changes.
 */
sealed class ConnectionEvent {
    /**
     * Connection state has changed.
     * 
     * @property from Previous state
     * @property to New state
     */
    data class StateChanged(
        val from: ConnectionState,
        val to: ConnectionState
    ) : ConnectionEvent()
    
    /**
     * Connection metrics update.
     * 
     * @property latencyMs Current latency in milliseconds
     * @property messagesSent Total messages sent
     * @property messagesReceived Total messages received
     * @property bytesTransferred Total bytes transferred
     */
    data class MetricsUpdate(
        val latencyMs: Long,
        val messagesSent: Long,
        val messagesReceived: Long,
        val bytesTransferred: Long
    ) : ConnectionEvent()
    
    /**
     * Connection quality has changed.
     * 
     * @property quality Current connection quality
     */
    data class QualityChanged(
        val quality: ConnectionQuality
    ) : ConnectionEvent()
}

/**
 * Connection quality indicators.
 */
enum class ConnectionQuality {
    /** Excellent connection with low latency */
    EXCELLENT,
    /** Good connection with acceptable latency */
    GOOD,
    /** Fair connection with noticeable latency */
    FAIR,
    /** Poor connection with high latency or packet loss */
    POOR,
    /** No connection */
    OFFLINE
}
