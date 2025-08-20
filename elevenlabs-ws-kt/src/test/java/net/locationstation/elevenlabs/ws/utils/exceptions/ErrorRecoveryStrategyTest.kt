package net.locationstation.elevenlabs.ws.utils.exceptions

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.locationstation.elevenlabs.ws.logging.Logger
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ErrorRecoveryStrategy implementations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ErrorRecoveryStrategyTest {
    
    @Before
    fun setup() {
        // Set up test logger that doesn't use Android Log
        Logger.setLogWriter(object : Logger.LogWriter {
            override fun log(level: Logger.LogLevel, tag: String, message: String, throwable: Throwable?) {
                // Print to console for test visibility
                println("[$level] $tag: $message")
                throwable?.printStackTrace()
            }
        })
    }
    
    @Test
    fun `ExponentialBackoffStrategy should retry recoverable connection errors`() = runTest {
        val strategy = ExponentialBackoffStrategy(maxAttempts = 3)
        val error = ElevenLabsException.ConnectionException(
            isRecoverable = true,
            message = "Connection lost"
        )
        
        assertThat(strategy.shouldRetry(error, 1)).isTrue()
        assertThat(strategy.shouldRetry(error, 2)).isTrue()
        assertThat(strategy.shouldRetry(error, 3)).isFalse() // Max attempts reached
    }
    
    @Test
    fun `ExponentialBackoffStrategy should not retry non-recoverable errors`() = runTest {
        val strategy = ExponentialBackoffStrategy()
        val error = ElevenLabsException.ConnectionException(
            isRecoverable = false,
            message = "Connection refused"
        )
        
        assertThat(strategy.shouldRetry(error, 1)).isFalse()
    }
    
    @Test
    fun `ExponentialBackoffStrategy should calculate proper delays`() {
        val strategy = ExponentialBackoffStrategy(
            baseDelayMs = 1000,
            maxDelayMs = 10000,
            jitterFactor = 0.0 // No jitter for predictable testing
        )
        
        assertThat(strategy.getRetryDelay(1)).isEqualTo(1000)  // 1 * 1000
        assertThat(strategy.getRetryDelay(2)).isEqualTo(2000)  // 2 * 1000
        assertThat(strategy.getRetryDelay(3)).isEqualTo(4000)  // 4 * 1000
        assertThat(strategy.getRetryDelay(4)).isEqualTo(8000)  // 8 * 1000
        assertThat(strategy.getRetryDelay(5)).isEqualTo(10000) // Capped at max
    }
    
    @Test
    fun `ExponentialBackoffStrategy should add jitter`() {
        val strategy = ExponentialBackoffStrategy(
            baseDelayMs = 1000,
            jitterFactor = 0.2
        )
        
        val delay = strategy.getRetryDelay(2)
        // Base delay is 2000, with up to 20% jitter (400ms)
        assertThat(delay).isAtLeast(2000)
        assertThat(delay).isAtMost(2400)
    }
    
    @Test
    fun `LinearBackoffStrategy should calculate linear delays`() {
        val strategy = LinearBackoffStrategy(
            delayIncrementMs = 1000,
            maxDelayMs = 5000
        )
        
        assertThat(strategy.getRetryDelay(1)).isEqualTo(1000)
        assertThat(strategy.getRetryDelay(2)).isEqualTo(2000)
        assertThat(strategy.getRetryDelay(3)).isEqualTo(3000)
        assertThat(strategy.getRetryDelay(5)).isEqualTo(5000) // Capped at max
        assertThat(strategy.getRetryDelay(10)).isEqualTo(5000) // Still capped
    }
    
    @Test
    fun `NoRetryStrategy should never retry`() = runTest {
        val strategy = NoRetryStrategy()
        
        val connectionError = ElevenLabsException.ConnectionException(
            isRecoverable = true,
            message = "Test"
        )
        assertThat(strategy.shouldRetry(connectionError, 1)).isFalse()
        
        val rateLimitError = ElevenLabsException.RateLimitException(
            message = "Rate limited"
        )
        assertThat(strategy.shouldRetry(rateLimitError, 1)).isFalse()
        
        assertThat(strategy.getRetryDelay(1)).isEqualTo(0)
    }
    
    @Test
    fun `should retry rate limit exceptions`() = runTest {
        val strategy = ExponentialBackoffStrategy()
        val error = ElevenLabsException.RateLimitException(
            retryAfter = 5000,
            message = "Rate limit exceeded"
        )
        
        assertThat(strategy.shouldRetry(error, 1)).isTrue()
    }
    
    @Test
    fun `should not retry authentication exceptions`() = runTest {
        val strategy = ExponentialBackoffStrategy()
        val error = ElevenLabsException.AuthenticationException(
            code = 401,
            message = "Invalid API key"
        )
        
        assertThat(strategy.shouldRetry(error, 1)).isFalse()
    }
    
    @Test
    fun `should retry connection timeouts`() = runTest {
        val strategy = ExponentialBackoffStrategy()
        val error = ElevenLabsException.TimeoutException(
            timeoutType = TimeoutType.CONNECTION,
            message = "Connection timeout"
        )
        
        assertThat(strategy.shouldRetry(error, 1)).isTrue()
    }
}
