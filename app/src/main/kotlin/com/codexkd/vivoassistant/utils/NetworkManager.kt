package com.codexkd.vivoassistant.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * NetworkManager — Monitors connectivity and API reachability.
 *
 * The assistant MUST degrade gracefully when offline:
 * - Disable cloud AI conversation
 * - Keep local utilities active (brightness, alarms, etc.)
 * - Notify user clearly
 */
class NetworkManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Observable network state
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _networkType = MutableStateFlow("None")
    val networkType: StateFlow<String> = _networkType

    // Ping client — minimal, no logging
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    // ═══════════════════════════════════════════════
    // NETWORK CALLBACK
    // ═══════════════════════════════════════════════

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState()
            Log.d(TAG, "Network available")
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
            _networkType.value = "None"
            Log.d(TAG, "Network lost")
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = hasInternet
            _networkType.value = getNetworkTypeName(caps)
        }
    }

    // ═══════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            updateNetworkState()
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Network monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════
    // STATE CHECKS
    // ═══════════════════════════════════════════════

    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun getConnectionType(): String {
        val network = connectivityManager.activeNetwork ?: return "None"
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return "None"
        return getNetworkTypeName(caps)
    }

    /**
     * Measure actual API latency in milliseconds.
     * Used to determine if API calls will be fast enough for voice responses.
     */
    suspend fun measureLatency(): Long = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) return@withContext Long.MAX_VALUE

        val startTime = System.currentTimeMillis()
        return@withContext try {
            val request = Request.Builder()
                .url("https://www.google.com/generate_204")
                .head()
                .build()
            pingClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) System.currentTimeMillis() - startTime
                else Long.MAX_VALUE
            }
        } catch (e: IOException) {
            Long.MAX_VALUE
        }
    }

    /**
     * Determine AI quality based on connection type.
     */
    fun getAIQuality(): AIQuality {
        if (!isNetworkAvailable()) return AIQuality.OFFLINE

        return when (getConnectionType()) {
            "WiFi"   -> AIQuality.EXCELLENT
            "4G/LTE" -> AIQuality.GOOD
            "3G"     -> AIQuality.DEGRADED
            "2G"     -> AIQuality.POOR
            else     -> AIQuality.GOOD
        }
    }

    // ═══════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════

    private fun updateNetworkState() {
        _isOnline.value = isNetworkAvailable()
        _networkType.value = getConnectionType()
    }

    private fun getNetworkTypeName(caps: NetworkCapabilities): String = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)       -> "WiFi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)   -> getCellularType(caps)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)   -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)        -> "VPN"
        else -> "Unknown"
    }

    private fun getCellularType(caps: NetworkCapabilities): String {
        return when {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "4G/LTE"
            else -> "Mobile Data"
        }
    }

    enum class AIQuality {
        EXCELLENT,  // WiFi — full AI features
        GOOD,       // 4G — full features, minor delay
        DEGRADED,   // 3G — reduce response length
        POOR,       // 2G — warn user, basic only
        OFFLINE     // No internet — disable AI
    }

    companion object {
        private const val TAG = "NetworkManager"

        @Volatile
        private var INSTANCE: NetworkManager? = null

        fun getInstance(context: Context): NetworkManager {
            return INSTANCE ?: synchronized(this) {
                NetworkManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
