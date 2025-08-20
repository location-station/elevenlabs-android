package net.locationstation.elevenlabs.ws.domain.model

/**
 * Value classes for type-safe domain models.
 * These provide zero-overhead abstractions for common types.
 */

/**
 * Represents an ElevenLabs agent identifier.
 * @property value The unique agent ID string
 */
@JvmInline
value class AgentId(val value: String) {
    init {
        require(value.isNotBlank()) { "Agent ID cannot be blank" }
    }
}

/**
 * Represents a webhook URL for authentication.
 * @property value The webhook URL string
 */
@JvmInline
value class WebhookUrl(val value: String) {
    init {
        require(value.isNotBlank()) { "Webhook URL cannot be blank" }
        require(value.startsWith("http://") || value.startsWith("https://")) {
            "Webhook URL must start with http:// or https://"
        }
    }
}

/**
 * Represents a WebSocket session identifier.
 * @property value The unique session ID string
 */
@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Session ID cannot be blank" }
    }
}

/**
 * Represents an API key for direct authentication.
 * @property value The API key string
 */
@JvmInline
value class ApiKey(val value: String) {
    init {
        require(value.isNotBlank()) { "API key cannot be blank" }
    }
    
    /**
     * Returns a masked version of the API key for logging.
     */
    fun masked(): String {
        return if (value.length > 8) {
            "${value.take(4)}...${value.takeLast(4)}"
        } else {
            "***"
        }
    }
}

/**
 * Represents a voice identifier in ElevenLabs.
 * @property value The voice ID string
 */
@JvmInline
value class VoiceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Voice ID cannot be blank" }
    }
}

/**
 * Represents an audio chunk identifier.
 * @property value The chunk ID
 */
@JvmInline
value class AudioChunkId(val value: String)

/**
 * Represents a conversation turn identifier.
 * @property value The turn ID
 */
@JvmInline
value class TurnId(val value: String)

/**
 * Represents a timestamp in milliseconds.
 * @property value The timestamp value
 */
@JvmInline
value class TimestampMs(val value: Long) {
    init {
        require(value >= 0) { "Timestamp cannot be negative" }
    }
    
    /**
     * Converts to seconds.
     */
    fun toSeconds(): Double = value / 1000.0
}

/**
 * Represents audio data as a ByteArray.
 * This is not a value class due to ByteArray equality issues.
 */
data class AudioData(
    val bytes: ByteArray,
    val format: AudioFormat? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as AudioData
        
        if (!bytes.contentEquals(other.bytes)) return false
        if (format != other.format) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (format?.hashCode() ?: 0)
        return result
    }
}

/**
 * Audio format information.
 */
data class AudioFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val encoding: String
)
