package net.locationstation.elevenlabs.ws.utils.exceptions

/**
 * Base exception class for all ElevenLabs SDK exceptions.
 * This sealed class hierarchy provides type-safe error handling with detailed context.
 */
sealed class ElevenLabsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Thrown when authentication with the ElevenLabs API fails.
     * 
     * @property code HTTP status code if available
     * @property message Detailed error message
     */
    data class AuthenticationException(
        val code: Int? = null,
        override val message: String
    ) : ElevenLabsException(message)
    
    /**
     * Thrown when WebSocket connection issues occur.
     * 
     * @property isRecoverable Whether the connection can be automatically retried
     * @property message Detailed error message
     * @property cause Original exception if available
     */
    data class ConnectionException(
        val isRecoverable: Boolean = true,
        override val message: String,
        override val cause: Throwable? = null
    ) : ElevenLabsException(message, cause)
    
    /**
     * Thrown when audio processing fails.
     * 
     * @property errorType The specific type of audio error
     * @property message Detailed error message
     */
    data class AudioException(
        val errorType: AudioErrorType,
        override val message: String
    ) : ElevenLabsException(message)
    
    /**
     * Thrown when message parsing or handling fails.
     * 
     * @property invalidMessage The raw message that couldn't be processed
     * @property message Detailed error message
     */
    data class MessageException(
        val invalidMessage: String?,
        override val message: String
    ) : ElevenLabsException(message)
    
    /**
     * Thrown when API rate limits are exceeded.
     * 
     * @property retryAfter Milliseconds to wait before retrying (if provided by server)
     * @property message Detailed error message
     */
    data class RateLimitException(
        val retryAfter: Long? = null,
        override val message: String
    ) : ElevenLabsException(message)
    
    /**
     * Thrown when the WebSocket connection times out.
     * 
     * @property timeoutType The type of timeout that occurred
     * @property message Detailed error message
     */
    data class TimeoutException(
        val timeoutType: TimeoutType,
        override val message: String
    ) : ElevenLabsException(message)
    
    /**
     * Thrown when configuration is invalid.
     * 
     * @property parameter The configuration parameter that is invalid
     * @property message Detailed error message
     */
    data class ConfigurationException(
        val parameter: String,
        override val message: String
    ) : ElevenLabsException(message)
}

/**
 * Types of audio errors that can occur.
 */
enum class AudioErrorType {
    /** Failed to encode audio data */
    ENCODING_FAILED,
    /** Failed to decode audio data */
    DECODING_FAILED,
    /** Audio buffer exceeded maximum capacity */
    BUFFER_OVERFLOW,
    /** Audio format is not supported */
    FORMAT_UNSUPPORTED,
    /** Audio sample rate mismatch */
    SAMPLE_RATE_MISMATCH,
    /** Audio processing was interrupted */
    PROCESSING_INTERRUPTED
}

/**
 * Types of timeout errors that can occur.
 */
enum class TimeoutType {
    /** Connection attempt timed out */
    CONNECTION,
    /** Waiting for response timed out */
    RESPONSE,
    /** Authentication timed out */
    AUTHENTICATION,
    /** Idle connection timed out */
    IDLE
}
