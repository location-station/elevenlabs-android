package net.locationstation.elevenlabs.ws.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.locationstation.elevenlabs.ws.logging.Logger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow
import android.Manifest
import androidx.annotation.RequiresPermission

/**
 * Manages network-aware WebSocket reconnection with exponential backoff.
 * This is a key differentiator - intelligent reconnection based on network conditions.
 * 
 * Features:
 * - Network state monitoring (WiFi, Cellular, Offline)
 * - Adaptive reconnection strategy based on network quality
 * - Exponential backoff with jitter
 * - Circuit breaker pattern for failing connections
 * - Bandwidth estimation for quality adaptation
 */
class NetworkAwareReconnectionManager(
    private val context: Context,
    private val config: ReconnectionConfig = ReconnectionConfig()
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private val _networkState = MutableStateFlow(NetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    private val _reconnectionState = MutableStateFlow(ReconnectionState.IDLE)
    val reconnectionState: StateFlow<ReconnectionState> = _reconnectionState.asStateFlow()
    
    private val reconnectAttempts = AtomicInteger(0)
    private var reconnectJob: Job? = null
    private val connectionFailures = mutableListOf<Long>()
    
    /**
     * Configuration for network-aware reconnection.
     */
    data class ReconnectionConfig(
        val enableAutoReconnect: Boolean = true,
        val maxReconnectAttempts: Int = 5,
        val baseDelayMs: Long = 1000,
        val maxDelayMs: Long = 30000,
        val jitterFactor: Double = 0.1,
        val preferWifi: Boolean = true,
        val pauseOnMetered: Boolean = false,
        val minBandwidthKbps: Int = 64,
        val circuitBreakerThreshold: Int = 3,
        val circuitBreakerWindowMs: Long = 60000,
        val onNetworkChange: ((NetworkState) -> Unit)? = null,
        val onReconnectAttempt: ((Int) -> Unit)? = null
    )
    
    /**
     * Network state information.
     */
    data class NetworkState(
        val isConnected: Boolean = false,
        val networkType: NetworkType = NetworkType.NONE,
        val isMetered: Boolean = false,
        val signalStrength: SignalStrength = SignalStrength.UNKNOWN,
        val estimatedBandwidthKbps: Int = 0,
        val hasInternetCapability: Boolean = false
    )
    
    /**
     * Network types.
     */
    enum class NetworkType {
        NONE, WIFI, CELLULAR, ETHERNET, VPN, OTHER
    }
    
    /**
     * Signal strength levels.
     */
    enum class SignalStrength {
        EXCELLENT, GOOD, FAIR, POOR, UNKNOWN
    }
    
    /**
     * Reconnection states.
     */
    enum class ReconnectionState {
        IDLE,
        WAITING,
        RECONNECTING,
        CONNECTED,
        FAILED,
        CIRCUIT_BREAKER_OPEN
    }
    
    /**
     * Starts monitoring network changes.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @Suppress("ObsoleteSdkInt")
    fun startMonitoring() {
        Logger.d("Starting network monitoring")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerNetworkCallbackApi24()
        } else {
            registerNetworkCallbackLegacy()
        }
        
        // Update initial state
        updateNetworkState()
    }
    
    /**
     * Stops monitoring network changes.
     */
    fun stopMonitoring() {
        Logger.d("Stopping network monitoring")
        
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null
        
        cancelReconnection()
    }

    @Suppress("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.N)
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun registerNetworkCallbackApi24() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Logger.d("Network available: $network")
                updateNetworkState()
                handleNetworkAvailable()
            }
            
            override fun onLost(network: Network) {
                Logger.d("Network lost: $network")
                updateNetworkState()
                handleNetworkLost()
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Logger.d("Network capabilities changed")
                updateNetworkState()
                handleNetworkCapabilitiesChanged(networkCapabilities)
            }
        }
        
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }
    
    @Suppress("DEPRECATION")
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun registerNetworkCallbackLegacy() {
        // For API < 24, use deprecated methods
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkState()
                handleNetworkAvailable()
            }
            
            override fun onLost(network: Network) {
                updateNetworkState()
                handleNetworkLost()
            }
        }
        
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }
    
    /**
     * Updates the current network state.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun updateNetworkState() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        
        val isConnected = capabilities != null
        val networkType = getNetworkType(capabilities)
        val isMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        val signalStrength = estimateSignalStrength(capabilities)
        val bandwidth = estimateBandwidth(capabilities)
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        val newState = NetworkState(
            isConnected = isConnected,
            networkType = networkType,
            isMetered = isMetered,
            signalStrength = signalStrength,
            estimatedBandwidthKbps = bandwidth,
            hasInternetCapability = hasInternet
        )
        
        _networkState.value = newState
        config.onNetworkChange?.invoke(newState)
        
        Logger.d("Network state updated: $newState")
    }
    
    /**
     * Determines the network type from capabilities.
     */
    private fun getNetworkType(capabilities: NetworkCapabilities?): NetworkType {
        return when {
            capabilities == null -> NetworkType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            else -> NetworkType.OTHER
        }
    }
    
    /**
     * Estimates signal strength from network capabilities.
     */
    private fun estimateSignalStrength(capabilities: NetworkCapabilities?): SignalStrength {
        if (capabilities == null) return SignalStrength.UNKNOWN
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val strength = capabilities.signalStrength
            when {
                strength >= -50 -> SignalStrength.EXCELLENT
                strength >= -60 -> SignalStrength.GOOD
                strength >= -70 -> SignalStrength.FAIR
                strength >= -80 -> SignalStrength.POOR
                else -> SignalStrength.UNKNOWN
            }
        } else {
            // For older versions, assume good if connected
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                SignalStrength.GOOD
            } else {
                SignalStrength.UNKNOWN
            }
        }
    }
    
    /**
     * Estimates bandwidth from network capabilities.
     */
    private fun estimateBandwidth(capabilities: NetworkCapabilities?): Int {
        if (capabilities == null) return 0
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.linkDownstreamBandwidthKbps
        } else {
            // Estimate based on network type
            when (getNetworkType(capabilities)) {
                NetworkType.WIFI -> 10000 // 10 Mbps estimate
                NetworkType.CELLULAR -> 1000 // 1 Mbps estimate
                NetworkType.ETHERNET -> 100000 // 100 Mbps estimate
                else -> 100 // Conservative estimate
            }
        }
    }
    
    /**
     * Handles network becoming available.
     */
    private fun handleNetworkAvailable() {
        if (!config.enableAutoReconnect) return
        
        val state = _networkState.value
        
        // Check if we should reconnect based on preferences
        if (config.pauseOnMetered && state.isMetered) {
            Logger.d("Network is metered, skipping reconnection")
            return
        }
        
        if (config.preferWifi && state.networkType != NetworkType.WIFI) {
            Logger.d("Prefer WiFi is enabled, current network is ${state.networkType}")
            // Still reconnect but with longer delay
            scheduleReconnection(delayMultiplier = 2.0)
        } else {
            scheduleReconnection()
        }
    }
    
    /**
     * Handles network being lost.
     */
    private fun handleNetworkLost() {
        cancelReconnection()
        _reconnectionState.value = ReconnectionState.WAITING
    }
    
    /**
     * Handles network capabilities changing.
     */
    private fun handleNetworkCapabilitiesChanged(capabilities: NetworkCapabilities) {
        val bandwidth = estimateBandwidth(capabilities)
        
        if (bandwidth < config.minBandwidthKbps) {
            Logger.w("Bandwidth below minimum: ${bandwidth}Kbps < ${config.minBandwidthKbps}Kbps")
            // Consider pausing or reducing quality
        }
    }
    
    /**
     * Schedules a reconnection attempt.
     */
    fun scheduleReconnection(
        delayMultiplier: Double = 1.0,
        onReconnect: suspend () -> Result<Unit> = { Result.success(Unit) }
    ) {
        if (!config.enableAutoReconnect) {
            Logger.d("Auto-reconnect disabled")
            return
        }
        
        if (isCircuitBreakerOpen()) {
            Logger.w("Circuit breaker is open, not scheduling reconnection")
            _reconnectionState.value = ReconnectionState.CIRCUIT_BREAKER_OPEN
            return
        }
        
        val attempts = reconnectAttempts.get()
        if (attempts >= config.maxReconnectAttempts) {
            Logger.w("Max reconnection attempts reached: $attempts")
            _reconnectionState.value = ReconnectionState.FAILED
            return
        }
        
        cancelReconnection()
        
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            val delay = calculateBackoffDelay(attempts, delayMultiplier)
            Logger.d("Scheduling reconnection attempt ${attempts + 1} in ${delay}ms")
            
            _reconnectionState.value = ReconnectionState.WAITING
            delay(delay)
            
            if (!isActive) return@launch
            
            _reconnectionState.value = ReconnectionState.RECONNECTING
            reconnectAttempts.incrementAndGet()
            config.onReconnectAttempt?.invoke(reconnectAttempts.get())
            
            try {
                val result = onReconnect()
                if (result.isSuccess) {
                    Logger.d("Reconnection successful")
                    _reconnectionState.value = ReconnectionState.CONNECTED
                    reconnectAttempts.set(0)
                    connectionFailures.clear()
                } else {
                    Logger.w("Reconnection failed: ${result.exceptionOrNull()}")
                    recordFailure()
                    scheduleReconnection(delayMultiplier, onReconnect)
                }
            } catch (e: Exception) {
                Logger.e(e, "Reconnection attempt failed")
                recordFailure()
                scheduleReconnection(delayMultiplier, onReconnect)
            }
        }
    }
    
    /**
     * Cancels any pending reconnection.
     */
    fun cancelReconnection() {
        reconnectJob?.cancel()
        reconnectJob = null
    }
    
    /**
     * Resets the reconnection state.
     */
    fun reset() {
        cancelReconnection()
        reconnectAttempts.set(0)
        connectionFailures.clear()
        _reconnectionState.value = ReconnectionState.IDLE
    }
    
    /**
     * Calculates the backoff delay with jitter.
     */
    private fun calculateBackoffDelay(attempt: Int, multiplier: Double): Long {
        val exponentialDelay = config.baseDelayMs * 2.0.pow(attempt.toDouble())
        val clampedDelay = min(exponentialDelay, config.maxDelayMs.toDouble())
        val jitter = (Math.random() - 0.5) * 2 * config.jitterFactor
        val delayWithJitter = clampedDelay * (1 + jitter) * multiplier
        return delayWithJitter.toLong().coerceAtLeast(0)
    }
    
    /**
     * Records a connection failure for circuit breaker.
     */
    private fun recordFailure() {
        val now = System.currentTimeMillis()
        connectionFailures.add(now)
        
        // Remove old failures outside the window
        connectionFailures.removeAll { 
            now - it > config.circuitBreakerWindowMs 
        }
    }
    
    /**
     * Checks if the circuit breaker is open.
     */
    private fun isCircuitBreakerOpen(): Boolean {
        val now = System.currentTimeMillis()
        val recentFailures = connectionFailures.count { 
            now - it <= config.circuitBreakerWindowMs 
        }
        return recentFailures >= config.circuitBreakerThreshold
    }
    
    /**
     * Checks if the current network meets requirements.
     */
    fun isNetworkSuitable(): Boolean {
        val state = _networkState.value
        
        if (!state.isConnected) return false
        if (!state.hasInternetCapability) return false
        if (config.pauseOnMetered && state.isMetered) return false
        if (config.preferWifi && state.networkType != NetworkType.WIFI) return false
        if (state.estimatedBandwidthKbps < config.minBandwidthKbps) return false
        
        return true
    }
    
    /**
     * Gets the recommended quality level based on network conditions.
     */
    fun getRecommendedQuality(): QualityLevel {
        val state = _networkState.value
        
        return when {
            !state.isConnected -> QualityLevel.OFFLINE
            state.estimatedBandwidthKbps < 64 -> QualityLevel.LOW
            state.estimatedBandwidthKbps < 256 -> QualityLevel.MEDIUM
            state.estimatedBandwidthKbps < 1024 -> QualityLevel.HIGH
            else -> QualityLevel.ULTRA
        }
    }
    
    /**
     * Quality levels for adaptive streaming.
     */
    enum class QualityLevel {
        OFFLINE, LOW, MEDIUM, HIGH, ULTRA
    }
}
