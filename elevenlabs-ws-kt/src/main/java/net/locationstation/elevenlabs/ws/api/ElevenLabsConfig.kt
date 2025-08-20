package net.locationstation.elevenlabs.ws.api

import net.locationstation.elevenlabs.ws.core.audio.AudioFormat
import net.locationstation.elevenlabs.ws.logging.Logger
import net.locationstation.elevenlabs.ws.utils.exceptions.ErrorRecoveryStrategy
import net.locationstation.elevenlabs.ws.utils.exceptions.ExponentialBackoffStrategy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Configuration for the ElevenLabs WebSocket client.
 * 
 * @property agentId The ID of the ElevenLabs agent to connect to
 * @property webhookUrl The URL to retrieve signed WebSocket URLs from
 * @property okHttpClient Custom OkHttpClient instance (optional)
 * @property audioFormat Audio format for sending/receiving audio
 * @property errorRecoveryStrategy Strategy for handling connection errors
 * @property enableAutoReconnect Whether to automatically reconnect on disconnection
 * @property maxReconnectAttempts Maximum number of reconnection attempts
 * @property connectionTimeoutMs Connection timeout in milliseconds
 * @property responseTimeoutMs Response timeout in milliseconds
 * @property pingIntervalMs WebSocket ping interval in milliseconds
 * @property logLevel Minimum log level for SDK logging
 * @property enableMetrics Whether to collect connection metrics
 */
data class ElevenLabsConfig(
    val agentId: String,
    val webhookUrl: String,
    val okHttpClient: OkHttpClient? = null,
    val audioFormat: AudioFormat = AudioFormat.DEFAULT,
    val errorRecoveryStrategy: ErrorRecoveryStrategy = ExponentialBackoffStrategy(),
    val enableAutoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val connectionTimeoutMs: Long = 30000,
    val responseTimeoutMs: Long = 30000,
    val pingIntervalMs: Long = 20000,
    val logLevel: Logger.LogLevel = Logger.LogLevel.INFO,
    val enableMetrics: Boolean = true
) {
    
    init {
        require(agentId.isNotBlank()) { "Agent ID cannot be blank" }
        require(webhookUrl.isNotBlank()) { "Webhook URL cannot be blank" }
        require(maxReconnectAttempts > 0) { "Max reconnect attempts must be positive" }
        require(connectionTimeoutMs > 0) { "Connection timeout must be positive" }
        require(responseTimeoutMs > 0) { "Response timeout must be positive" }
        require(pingIntervalMs > 0) { "Ping interval must be positive" }
    }
    
    /**
     * Creates or returns the OkHttpClient configured for this connection.
     */
    fun getOrCreateOkHttpClient(): OkHttpClient {
        return okHttpClient ?: OkHttpClient.Builder()
            .connectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(responseTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(responseTimeoutMs, TimeUnit.MILLISECONDS)
            .pingInterval(pingIntervalMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * Builder for creating ElevenLabsConfig instances.
     */
    class Builder {
        private var agentId: String? = null
        private var webhookUrl: String? = null
        private var okHttpClient: OkHttpClient? = null
        private var audioFormat = AudioFormat.DEFAULT
        private var errorRecoveryStrategy: ErrorRecoveryStrategy = ExponentialBackoffStrategy()
        private var enableAutoReconnect = true
        private var maxReconnectAttempts = 5
        private var connectionTimeoutMs = 30000L
        private var responseTimeoutMs = 30000L
        private var pingIntervalMs = 20000L
        private var logLevel = Logger.LogLevel.INFO
        private var enableMetrics = true
        
        fun agentId(id: String) = apply { this.agentId = id }
        fun webhookUrl(url: String) = apply { this.webhookUrl = url }
        fun okHttpClient(client: OkHttpClient) = apply { this.okHttpClient = client }
        fun audioFormat(format: AudioFormat) = apply { this.audioFormat = format }
        fun errorRecoveryStrategy(strategy: ErrorRecoveryStrategy) = apply { 
            this.errorRecoveryStrategy = strategy 
        }
        fun enableAutoReconnect(enable: Boolean) = apply { this.enableAutoReconnect = enable }
        fun maxReconnectAttempts(attempts: Int) = apply { this.maxReconnectAttempts = attempts }
        fun connectionTimeoutMs(timeout: Long) = apply { this.connectionTimeoutMs = timeout }
        fun responseTimeoutMs(timeout: Long) = apply { this.responseTimeoutMs = timeout }
        fun pingIntervalMs(interval: Long) = apply { this.pingIntervalMs = interval }
        fun logLevel(level: Logger.LogLevel) = apply { this.logLevel = level }
        fun enableMetrics(enable: Boolean) = apply { this.enableMetrics = enable }
        
        fun build(): ElevenLabsConfig {
            return ElevenLabsConfig(
                agentId = requireNotNull(agentId) { "Agent ID is required" },
                webhookUrl = requireNotNull(webhookUrl) { "Webhook URL is required" },
                okHttpClient = okHttpClient,
                audioFormat = audioFormat,
                errorRecoveryStrategy = errorRecoveryStrategy,
                enableAutoReconnect = enableAutoReconnect,
                maxReconnectAttempts = maxReconnectAttempts,
                connectionTimeoutMs = connectionTimeoutMs,
                responseTimeoutMs = responseTimeoutMs,
                pingIntervalMs = pingIntervalMs,
                logLevel = logLevel,
                enableMetrics = enableMetrics
            )
        }
    }
}
