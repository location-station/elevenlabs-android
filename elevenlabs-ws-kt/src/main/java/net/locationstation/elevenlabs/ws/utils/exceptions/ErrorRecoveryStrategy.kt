package net.locationstation.elevenlabs.ws.utils.exceptions

import kotlinx.coroutines.delay
import net.locationstation.elevenlabs.ws.logging.Logger
import kotlin.math.min
import kotlin.random.Random

/**
 * Strategy for recovering from errors with retry logic.
 */
interface ErrorRecoveryStrategy {
    /**
     * Determines if an error should trigger a retry attempt.
     * 
     * @param error The exception that occurred
     * @param attemptNumber The current attempt number (1-based)
     * @return true if the operation should be retried
     */
    suspend fun shouldRetry(error: ElevenLabsException, attemptNumber: Int): Boolean
    
    /**
     * Calculates the delay before the next retry attempt.
     * 
     * @param attemptNumber The current attempt number (1-based)
     * @return Delay in milliseconds before retrying
     */
    fun getRetryDelay(attemptNumber: Int): Long
    
    /**
     * Executes the retry delay with proper logging.
     */
    suspend fun executeRetryDelay(attemptNumber: Int) {
        val delayMs = getRetryDelay(attemptNumber)
        Logger.d("ErrorRecovery", "Waiting ${delayMs}ms before retry attempt #$attemptNumber")
        delay(delayMs)
    }
}

/**
 * Exponential backoff strategy with jitter for retry attempts.
 * 
 * @property maxAttempts Maximum number of retry attempts
 * @property baseDelayMs Initial delay in milliseconds
 * @property maxDelayMs Maximum delay cap in milliseconds
 * @property jitterFactor Factor for randomization (0.0 to 1.0)
 */
class ExponentialBackoffStrategy(
    private val maxAttempts: Int = 5,
    private val baseDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000,
    private val jitterFactor: Double = 0.2
) : ErrorRecoveryStrategy {
    
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(baseDelayMs > 0) { "baseDelayMs must be positive" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0.0 and 1.0" }
    }
    
    override suspend fun shouldRetry(
        error: ElevenLabsException, 
        attemptNumber: Int
    ): Boolean {
        if (attemptNumber >= maxAttempts) {
            Logger.w("ErrorRecovery", "Max retry attempts ($maxAttempts) reached")
            return false
        }
        
        return when (error) {
            is ElevenLabsException.ConnectionException -> {
                if (error.isRecoverable) {
                    Logger.d("ErrorRecovery", "Connection error is recoverable, will retry")
                    true
                } else {
                    Logger.w("ErrorRecovery", "Connection error is not recoverable")
                    false
                }
            }
            is ElevenLabsException.RateLimitException -> {
                Logger.d("ErrorRecovery", "Rate limit exceeded, will retry")
                true
            }
            is ElevenLabsException.TimeoutException -> {
                when (error.timeoutType) {
                    TimeoutType.CONNECTION, TimeoutType.RESPONSE -> {
                        Logger.d("ErrorRecovery", "Timeout is retryable")
                        true
                    }
                    else -> {
                        Logger.w("ErrorRecovery", "Timeout type ${error.timeoutType} is not retryable")
                        false
                    }
                }
            }
            is ElevenLabsException.AuthenticationException -> {
                // Don't retry auth failures (likely a configuration issue)
                Logger.w("ErrorRecovery", "Authentication failures are not retryable")
                false
            }
            else -> {
                Logger.w("ErrorRecovery", "Error type ${error::class.simpleName} is not retryable")
                false
            }
        }
    }
    
    override fun getRetryDelay(attemptNumber: Int): Long {
        // Calculate exponential delay: base * 2^(attempt-1)
        val exponentialDelay = baseDelayMs * (1L shl (attemptNumber - 1))
        
        // Add jitter to prevent thundering herd
        val jitter = (Random.nextDouble() * jitterFactor * exponentialDelay).toLong()
        
        // Cap at maximum delay
        return min(exponentialDelay + jitter, maxDelayMs)
    }
}

/**
 * Linear backoff strategy with fixed delay increments.
 * 
 * @property maxAttempts Maximum number of retry attempts
 * @property delayIncrementMs Delay increment for each retry
 * @property maxDelayMs Maximum delay cap in milliseconds
 */
class LinearBackoffStrategy(
    private val maxAttempts: Int = 3,
    private val delayIncrementMs: Long = 2000,
    private val maxDelayMs: Long = 10000
) : ErrorRecoveryStrategy {
    
    override suspend fun shouldRetry(
        error: ElevenLabsException,
        attemptNumber: Int
    ): Boolean {
        if (attemptNumber >= maxAttempts) return false
        
        return when (error) {
            is ElevenLabsException.ConnectionException -> error.isRecoverable
            is ElevenLabsException.TimeoutException -> true
            else -> false
        }
    }
    
    override fun getRetryDelay(attemptNumber: Int): Long {
        val delay = delayIncrementMs * attemptNumber
        return min(delay, maxDelayMs)
    }
}

/**
 * No retry strategy - fails immediately without retrying.
 */
class NoRetryStrategy : ErrorRecoveryStrategy {
    override suspend fun shouldRetry(error: ElevenLabsException, attemptNumber: Int) = false
    override fun getRetryDelay(attemptNumber: Int) = 0L
}
