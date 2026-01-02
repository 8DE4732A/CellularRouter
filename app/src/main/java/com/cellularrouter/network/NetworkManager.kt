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
    
    @Volatile
    private var wifiNetwork: Network? = null
    
    private val cellularCallback = object : ConnectivityManager.NetworkCallback() {
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
    
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                wifiNetwork = network
                Log.d(TAG, "WiFi network available: $network")
            }
        }
        
        override fun onLost(network: Network) {
            if (network == wifiNetwork) {
                wifiNetwork = null
                Log.d(TAG, "WiFi network lost")
            }
        }
    }
    
    /**
     * Start monitoring network changes
     */
    fun start() {
        // Request cellular network
        val cellularRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)
        
        // Request WiFi network
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)
        
        // Try to find existing networks
        findCellularNetwork()
        findWifiNetwork()
    }
    
    /**
     * Stop monitoring network changes
     */
    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(cellularCallback)
            connectivityManager.unregisterNetworkCallback(wifiCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callbacks", e)
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
    
    /**
     * Find and cache WiFi network
     */
    private fun findWifiNetwork() {
        connectivityManager.allNetworks.forEach { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                wifiNetwork = network
                Log.d(TAG, "Found WiFi network: $network")
                return
            }
        }
    }
    
    /**
     * Check if WiFi network is available
     */
    fun isWifiAvailable(): Boolean {
        return wifiNetwork != null
    }
    
    /**
     * Bind a socket to the WiFi network
     * @return true if binding was successful
     */
    fun bindSocketToWifi(socket: Socket): Boolean {
        val network = wifiNetwork
        if (network == null) {
            Log.w(TAG, "WiFi network not available")
            return false
        }
        
        return try {
            network.bindSocket(socket)
            Log.d(TAG, "Socket bound to WiFi network")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind socket to WiFi", e)
            false
        }
    }
    
    /**
     * Bind a process to the WiFi network (for frpc)
     * @return true if binding was successful
     */
    fun bindProcessToWifi(): Boolean {
        val network = wifiNetwork
        if (network == null) {
            Log.w(TAG, "WiFi network not available for process binding")
            return false
        }
        
        return try {
            connectivityManager.bindProcessToNetwork(network)
            Log.d(TAG, "Process bound to WiFi network")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind process to WiFi", e)
            false
        }
    }
    
    /**
     * Unbind process from network
     */
    fun unbindProcess() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            Log.d(TAG, "Process network binding cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind process", e)
        }
    }
    
    /**
     * Get WiFi network info string
     */
    fun getWifiNetworkInfo(): String {
        val network = wifiNetwork ?: return "未连接"
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return when {
            capabilities == null -> "未连接"
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "已连接"
            else -> "无网络"
        }
    }
}
