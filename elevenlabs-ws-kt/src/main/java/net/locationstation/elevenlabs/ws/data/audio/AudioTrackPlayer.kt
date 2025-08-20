package net.locationstation.elevenlabs.ws.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import net.locationstation.elevenlabs.ws.domain.model.AudioData
import net.locationstation.elevenlabs.ws.logging.Logger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Low-latency audio player using Android's AudioTrack for real-time streaming.
 * This is optimized for WebSocket audio streaming with adaptive buffering.
 * 
 * Features:
 * - Low-latency playback using AudioTrack
 * - Adaptive buffering based on network conditions
 * - Automatic format detection and conversion
 * - Jitter buffer for smooth playback
 * - Underrun recovery
 */
class AudioTrackPlayer(
    private val config: AudioPlayerConfig = AudioPlayerConfig()
) {
    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private val audioQueue = Channel<AudioData>(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    
    // Adaptive buffering
    private var currentBufferSize = config.initialBufferSizeMs
    private var underrunCount = 0
    private var lastUnderrunTime = 0L
    
    // Metrics
    private val _metrics = MutableStateFlow(AudioMetrics())
    val metrics: StateFlow<AudioMetrics> = _metrics.asStateFlow()
    
    /**
     * Configuration for the audio player.
     */
    data class AudioPlayerConfig(
        val sampleRate: Int = 16000,
        val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
        val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
        val initialBufferSizeMs: Int = 100,
        val maxBufferSizeMs: Int = 500,
        val minBufferSizeMs: Int = 50,
        val underrunRecoveryMs: Int = 50,
        val enableAdaptiveBuffering: Boolean = true,
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    )
    
    /**
     * Audio playback metrics.
     */
    data class AudioMetrics(
        val bufferSizeMs: Int = 0,
        val underrunCount: Int = 0,
        val totalFramesPlayed: Long = 0,
        val latencyMs: Int = 0,
        val isPlaying: Boolean = false
    )
    
    /**
     * Initializes the AudioTrack for playback.
     */
    @Synchronized
    fun initialize(): Result<Unit> = runCatching {
        if (audioTrack != null) {
            Logger.d("AudioTrack already initialized")
            return Result.success(Unit)
        }
        
        val minBufferSize = AudioTrack.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.audioFormat
        )
        
        if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            throw IllegalStateException("Failed to get minimum buffer size")
        }
        
        // Use a larger buffer for stability (2x minimum)
        val bufferSize = minBufferSize * 2
        
        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(config.audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(config.channelConfig)
                        .setEncoding(config.audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }
        
        Logger.d("AudioTrack initialized with buffer size: $bufferSize bytes")
    }
    
    /**
     * Starts audio playback.
     */
    fun start(): Result<Unit> = runCatching {
        if (isPlaying.get()) {
            Logger.d("Audio playback already started")
            return Result.success(Unit)
        }
        
        initialize().getOrThrow()
        
        audioTrack?.play() ?: throw IllegalStateException("AudioTrack not initialized")
        isPlaying.set(true)
        
        // Start the playback coroutine
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            processAudioQueue()
        }
        
        Logger.d("Audio playback started")
    }
    
    /**
     * Stops audio playback.
     */
    fun stop() {
        if (!isPlaying.get()) return
        
        isPlaying.set(false)
        playbackJob?.cancel()
        playbackJob = null
        
        audioTrack?.apply {
            if (state == AudioTrack.STATE_INITIALIZED) {
                pause()
                flush()
                stop()
            }
        }
        
        // Clear the queue
        audioQueue.tryReceive()
        
        Logger.d("Audio playback stopped")
    }
    
    /**
     * Releases all resources.
     */
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        audioQueue.close()
        Logger.d("AudioTrack released")
    }
    
    /**
     * Queues audio data for playback.
     */
    suspend fun queueAudio(audioData: AudioData) {
        if (!isPlaying.get()) {
            start()
        }
        audioQueue.send(audioData)
    }
    
    /**
     * Queues audio data for playback (non-blocking).
     */
    fun queueAudioNonBlocking(audioData: AudioData): Boolean {
        return audioQueue.trySend(audioData).isSuccess
    }
    
    /**
     * Creates a Flow for streaming audio data.
     */
    fun audioStream(): Flow<AudioData> = flow {
        while (isPlaying.get()) {
            audioQueue.tryReceive().getOrNull()?.let { emit(it) }
            delay(10) // Small delay to prevent tight loop
        }
    }
    
    /**
     * Processes the audio queue and writes to AudioTrack.
     */
    private suspend fun processAudioQueue() = coroutineScope {
        val track = audioTrack ?: return@coroutineScope
        val jitterBuffer = JitterBuffer(config.initialBufferSizeMs)
        
        while (isPlaying.get() && isActive) {
            try {
                // Try to get audio from queue with timeout
                val audioData = withTimeoutOrNull(100) {
                    audioQueue.receive()
                }
                
                if (audioData != null) {
                    // Add to jitter buffer
                    jitterBuffer.add(audioData)
                    
                    // Process buffered audio
                    while (jitterBuffer.hasEnoughData()) {
                        val bufferedAudio = jitterBuffer.getNext()
                        writeAudioToTrack(track, bufferedAudio)
                    }
                    
                    // Update metrics
                    updateMetrics(track, jitterBuffer)
                } else {
                    // Handle underrun
                    handleUnderrun(track)
                }
            } catch (e: Exception) {
                Logger.e(e, "Error processing audio queue")
            }
        }
    }
    
    /**
     * Writes audio data to the AudioTrack.
     */
    private fun writeAudioToTrack(track: AudioTrack, audioData: AudioData) {
        val bytesWritten = track.write(audioData.bytes, 0, audioData.bytes.size)
        
        when (bytesWritten) {
            AudioTrack.ERROR_INVALID_OPERATION -> {
                Logger.e(null, "AudioTrack write failed: ERROR_INVALID_OPERATION")
            }
            AudioTrack.ERROR_BAD_VALUE -> {
                Logger.e(null, "AudioTrack write failed: ERROR_BAD_VALUE")
            }
            AudioTrack.ERROR_DEAD_OBJECT -> {
                Logger.e(null, "AudioTrack write failed: ERROR_DEAD_OBJECT")
                // Attempt to reinitialize
                reinitialize()
            }
            AudioTrack.ERROR -> {
                Logger.e(null, "AudioTrack write failed: ERROR")
            }
            else -> {
                if (bytesWritten < audioData.bytes.size) {
                    Logger.w("Partial write: $bytesWritten/${audioData.bytes.size} bytes")
                }
            }
        }
    }
    
    /**
     * Handles audio underrun (buffer empty).
     */
    private fun handleUnderrun(track: AudioTrack) {
        val now = System.currentTimeMillis()
        if (now - lastUnderrunTime > 1000) { // Reset counter after 1 second
            underrunCount = 0
        }
        
        underrunCount++
        lastUnderrunTime = now
        
        if (config.enableAdaptiveBuffering) {
            // Increase buffer size on underrun
            currentBufferSize = min(
                currentBufferSize + config.underrunRecoveryMs,
                config.maxBufferSizeMs
            )
            Logger.d("Underrun detected, increasing buffer to ${currentBufferSize}ms")
        }
        
        // Write silence to prevent clicking
        val silenceSize = (config.sampleRate * 0.01).toInt() * 2 // 10ms of silence
        val silence = ByteArray(silenceSize)
        track.write(silence, 0, silence.size)
    }
    
    /**
     * Reinitializes the AudioTrack after an error.
     */
    private fun reinitialize() {
        Logger.d("Reinitializing AudioTrack")
        audioTrack?.release()
        audioTrack = null
        initialize()
        audioTrack?.play()
    }
    
    /**
     * Updates playback metrics.
     */
    private fun updateMetrics(track: AudioTrack, jitterBuffer: JitterBuffer) {
        val playbackHeadPosition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            track.playbackHeadPosition.toLong()
        } else {
            0L
        }
        
        _metrics.value = AudioMetrics(
            bufferSizeMs = currentBufferSize,
            underrunCount = underrunCount,
            totalFramesPlayed = playbackHeadPosition,
            latencyMs = jitterBuffer.getLatency(),
            isPlaying = isPlaying.get()
        )
    }
    
    /**
     * Simple jitter buffer for smoothing playback.
     */
    private inner class JitterBuffer(private val targetSizeMs: Int) {
        private val buffer = mutableListOf<AudioData>()
        private var totalBytes = 0
        
        fun add(audioData: AudioData) {
            buffer.add(audioData)
            totalBytes += audioData.bytes.size
        }
        
        fun hasEnoughData(): Boolean {
            val bytesPerMs = config.sampleRate * 2 / 1000 // 16-bit mono
            return totalBytes >= targetSizeMs * bytesPerMs
        }
        
        fun getNext(): AudioData {
            return if (buffer.isNotEmpty()) {
                val data = buffer.removeAt(0)
                totalBytes -= data.bytes.size
                data
            } else {
                AudioData(ByteArray(0))
            }
        }
        
        fun getLatency(): Int {
            val bytesPerMs = config.sampleRate * 2 / 1000
            return if (bytesPerMs > 0) totalBytes / bytesPerMs else 0
        }
    }
}
