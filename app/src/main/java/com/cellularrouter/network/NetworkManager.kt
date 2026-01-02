package com.cellularrouter.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.net.Socket

/**
 * Manages network state and binding sockets to cellular network
 */
class NetworkManager(private val context: Context) {
    companion object {
        private const val TAG = "NetworkManager"
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    @Volatile
    private var cellularNetwork: Network? = null
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                cellularNetwork = network
                Log.d(TAG, "Cellular network available: $network")
            }
        }
        
        override fun onLost(network: Network) {
            if (network == cellularNetwork) {
                cellularNetwork = null
                Log.d(TAG, "Cellular network lost")
            }
        }
    }
    
    /**
     * Start monitoring network changes
     */
    fun start() {
        // Request cellular network
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        // Try to find existing cellular network
        findCellularNetwork()
    }
    
    /**
     * Stop monitoring network changes
     */
    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }
    
    /**
     * Find and cache cellular network
     */
    private fun findCellularNetwork() {
        connectivityManager.allNetworks.forEach { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                cellularNetwork = network
                Log.d(TAG, "Found cellular network: $network")
                return
            }
        }
    }
    
    /**
     * Bind a socket to the cellular network
     * @return true if binding was successful
     */
    fun bindSocketToCellular(socket: Socket): Boolean {
        val network = cellularNetwork
        if (network == null) {
            Log.w(TAG, "Cellular network not available")
            return false
        }
        
        return try {
            network.bindSocket(socket)
            Log.d(TAG, "Socket bound to cellular network")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind socket to cellular", e)
            false
        }
    }
    
    /**
     * Check if cellular network is available
     */
    fun isCellularAvailable(): Boolean {
        return cellularNetwork != null
    }
    
    /**
     * Check if WiFi is connected
     */
    fun isWifiConnected(): Boolean {
        connectivityManager.allNetworks.forEach { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return true
            }
        }
        return false
    }
    
    /**
     * Get cellular network info string
     */
    fun getCellularNetworkInfo(): String {
        val network = cellularNetwork ?: return "不可用"
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return when {
            capabilities == null -> "不可用"
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "已连接"
            else -> "无网络"
        }
    }
}
