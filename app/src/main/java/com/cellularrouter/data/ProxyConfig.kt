package com.cellularrouter.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Proxy configuration data class
 */
data class ProxyConfig(
    val port: Int = DEFAULT_PORT
) {
    companion object {
        const val DEFAULT_PORT = 1080
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
        
        private const val PREFS_NAME = "proxy_config"
        private const val KEY_PORT = "port"
        
        /**
         * Load configuration from SharedPreferences
         */
        fun load(context: Context): ProxyConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
            return ProxyConfig(port)
        }
        
        /**
         * Validate port number
         */
        fun isValidPort(port: Int): Boolean {
            return port in MIN_PORT..MAX_PORT
        }
    }
    
    /**
     * Save configuration to SharedPreferences
     */
    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PORT, port)
            .apply()
    }
    
    /**
     * Validate this configuration
     */
    fun isValid(): Boolean {
        return isValidPort(port)
    }
}
