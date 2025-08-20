package net.locationstation.elevenlabs.ws.logging

/**
 * Production-ready logging system with configurable levels and interceptors.
 * This implementation provides structured logging with support for external
 * logging frameworks and crash reporting services.
 */
object Logger {
    private const val TAG_PREFIX = "ElevenLabs"
    private var logLevel = LogLevel.INFO
    private val interceptors = mutableListOf<LogInterceptor>()
    private var logWriter: LogWriter = AndroidLogWriter()
    
    /**
     * Log levels from least to most important.
     */
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARNING, ERROR, NONE
    }
    
    /**
     * Interface for intercepting log messages.
     */
    interface LogInterceptor {
        fun intercept(level: LogLevel, tag: String, message: String, throwable: Throwable?)
    }
    
    /**
     * Interface for writing log messages (allows testing).
     */
    interface LogWriter {
        fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
    }
    
    /**
     * Default Android log writer.
     */
    private class AndroidLogWriter : LogWriter {
        override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
            try {
                when (level) {
                    LogLevel.VERBOSE -> android.util.Log.v(tag, message, throwable)
                    LogLevel.DEBUG -> android.util.Log.d(tag, message, throwable)
                    LogLevel.INFO -> android.util.Log.i(tag, message, throwable)
                    LogLevel.WARNING -> android.util.Log.w(tag, message, throwable)
                    LogLevel.ERROR -> android.util.Log.e(tag, message, throwable)
                    LogLevel.NONE -> { /* No logging */ }
                }
            } catch (e: Exception) {
                // Fallback for testing or non-Android environments
                println("[$level] $tag: $message")
                throwable?.printStackTrace()
            }
        }
    }
    
    /**
     * Sets a custom log writer (useful for testing).
     */
    fun setLogWriter(writer: LogWriter) {
        logWriter = writer
    }
    
    /**
     * Sets the minimum log level that will be output.
     */
    fun setLogLevel(level: LogLevel) {
        logLevel = level
    }
    
    /**
     * Adds a log interceptor for external logging systems.
     */
    fun addInterceptor(interceptor: LogInterceptor) {
        interceptors.add(interceptor)
    }
    
    /**
     * Removes a log interceptor.
     */
    fun removeInterceptor(interceptor: LogInterceptor) {
        interceptors.remove(interceptor)
    }
    
    /**
     * Clears all log interceptors.
     */
    fun clearInterceptors() {
        interceptors.clear()
    }
    
    // Verbose logging
    fun v(tag: String, message: String, vararg args: Any?) {
        log(LogLevel.VERBOSE, tag, message, args, null)
    }
    
    // Debug logging
    fun d(tag: String, message: String, vararg args: Any?) {
        log(LogLevel.DEBUG, tag, message, args, null)
    }
    
    // Info logging
    fun i(tag: String, message: String, vararg args: Any?) {
        log(LogLevel.INFO, tag, message, args, null)
    }
    
    // Warning logging
    fun w(tag: String, message: String, vararg args: Any?, throwable: Throwable? = null) {
        log(LogLevel.WARNING, tag, message, args, throwable)
    }
    
    // Error logging
    fun e(tag: String, message: String, vararg args: Any?, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, args, throwable)
    }
    
    // Legacy methods for backward compatibility
    fun v(message: String, vararg args: Any?) = v("General", message, *args)
    fun d(message: String, vararg args: Any?) = d("General", message, *args)
    fun i(message: String, vararg args: Any?) = i("General", message, *args)
    fun w(message: String, vararg args: Any?) = w("General", message, *args)
    fun e(throwable: Throwable?, message: String, vararg args: Any?) = 
        e("General", message, *args, throwable = throwable)
    
    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
        args: Array<out Any?>,
        throwable: Throwable?
    ) {
        // Check if we should log this level
        if (level.ordinal < logLevel.ordinal) return
        
        // Format the message
        val formattedMessage = if (args.isNotEmpty()) {
            try {
                String.format(message, *args)
            } catch (e: Exception) {
                // Fallback if formatting fails
                "$message ${args.joinToString()}"
            }
        } else {
            message
        }
        
        // Create full tag
        val fullTag = "$TAG_PREFIX:$tag"
        
        // Write log
        logWriter.log(level, fullTag, formattedMessage, throwable)
        
        // Notify interceptors
        interceptors.forEach { interceptor ->
            try {
                interceptor.intercept(level, fullTag, formattedMessage, throwable)
            } catch (e: Exception) {
                // Don't let interceptor errors break logging
                println("Interceptor threw exception: $e")
            }
        }
    }
    
    /**
     * Sets debug mode - convenience method for backward compatibility.
     */
    fun setDebugEnabled(enabled: Boolean) {
        logLevel = if (enabled) LogLevel.DEBUG else LogLevel.INFO
    }
}
