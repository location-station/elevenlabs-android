package net.locationstation.elevenlabs.ws.core.audio

/**
 * Audio formats supported by ElevenLabs WebSocket API.
 */
enum class AudioFormat(
    val sampleRate: Int,
    val encoding: String,
    val channels: Int = 1,
    val bitsPerSample: Int = 16
) {
    /** PCM 16-bit mono at 16kHz (recommended for voice) */
    PCM_16000(16000, "pcm_16000", 1, 16),
    
    /** PCM 16-bit mono at 22.05kHz */
    PCM_22050(22050, "pcm_22050", 1, 16),
    
    /** PCM 16-bit mono at 24kHz */
    PCM_24000(24000, "pcm_24000", 1, 16),
    
    /** µ-law 8-bit mono at 8kHz (telephony standard) */
    ULAW_8000(8000, "ulaw_8000", 1, 8);
    
    /**
     * Calculates the byte rate for this audio format.
     */
    val byteRate: Int
        get() = sampleRate * channels * (bitsPerSample / 8)
    
    /**
     * Calculates the number of bytes per millisecond.
     */
    val bytesPerMillisecond: Int
        get() = byteRate / 1000
    
    companion object {
        /**
         * Gets an AudioFormat from its encoding string.
         */
        fun fromString(value: String): AudioFormat? {
            return values().firstOrNull { it.encoding == value }
        }
        
        /**
         * The default audio format recommended for most use cases.
         */
        val DEFAULT = PCM_16000
    }
}
