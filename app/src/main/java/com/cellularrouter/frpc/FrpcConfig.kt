package com.cellularrouter.frpc

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * FRPC configuration data class
 */
data class FrpcConfig(
    // Server configuration
    val serverAddr: String = "",
    val serverPort: Int = 7000,
    val authToken: String = "",
    val tlsEnable: Boolean = true,
    
    // Proxy configuration
    val proxyType: ProxyType = ProxyType.STCP,
    val proxyName: String = "CellularRouter",
    val secretKey: String = "",
    val localIP: String = "127.0.0.1",
    val localPort: Int = 1080,
    val useCompression: Boolean = true,
    val useEncryption: Boolean = true,
    
    // Additional settings
    val loginFailExit: Boolean = false, // Enable auto-reconnect
    val dnsServer: String = "8.8.8.8" // DNS server for resolving issues
) {
    
    companion object {
        private const val PREFS_NAME = "frpc_config_encrypted"
        private const val KEY_SERVER_ADDR = "server_addr"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_TLS_ENABLE = "tls_enable"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_PROXY_NAME = "proxy_name"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val KEY_LOCAL_IP = "local_ip"
        private const val KEY_LOCAL_PORT = "local_port"
        private const val KEY_USE_COMPRESSION = "use_compression"
        private const val KEY_USE_ENCRYPTION = "use_encryption"
        private const val KEY_LOGIN_FAIL_EXIT = "login_fail_exit"
        private const val KEY_DNS_SERVER = "dns_server"
        
        /**
         * Load configuration from encrypted SharedPreferences
         */
        fun load(context: Context): FrpcConfig {
            val prefs = getEncryptedPrefs(context)
            
            return FrpcConfig(
                serverAddr = prefs.getString(KEY_SERVER_ADDR, "") ?: "",
                serverPort = prefs.getInt(KEY_SERVER_PORT, 7000),
                authToken = prefs.getString(KEY_AUTH_TOKEN, "") ?: "",
                tlsEnable = prefs.getBoolean(KEY_TLS_ENABLE, true),
                proxyType = ProxyType.valueOf(
                    prefs.getString(KEY_PROXY_TYPE, ProxyType.STCP.name) ?: ProxyType.STCP.name
                ),
                proxyName = prefs.getString(KEY_PROXY_NAME, "CellularRouter") ?: "CellularRouter",
                secretKey = prefs.getString(KEY_SECRET_KEY, "") ?: "",
                localIP = prefs.getString(KEY_LOCAL_IP, "127.0.0.1") ?: "127.0.0.1",
                localPort = prefs.getInt(KEY_LOCAL_PORT, 1080),
                useCompression = prefs.getBoolean(KEY_USE_COMPRESSION, true),
                useEncryption = prefs.getBoolean(KEY_USE_ENCRYPTION, true),
                loginFailExit = prefs.getBoolean(KEY_LOGIN_FAIL_EXIT, false),
                dnsServer = prefs.getString(KEY_DNS_SERVER, "8.8.8.8") ?: "8.8.8.8"
            )
        }
        
        private fun getEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
    
    /**
     * Save configuration to encrypted SharedPreferences
     */
    fun save(context: Context) {
        val prefs = getEncryptedPrefs(context)
        
        prefs.edit().apply {
            putString(KEY_SERVER_ADDR, serverAddr)
            putInt(KEY_SERVER_PORT, serverPort)
            putString(KEY_AUTH_TOKEN, authToken)
            putBoolean(KEY_TLS_ENABLE, tlsEnable)
            putString(KEY_PROXY_TYPE, proxyType.name)
            putString(KEY_PROXY_NAME, proxyName)
            putString(KEY_SECRET_KEY, secretKey)
            putString(KEY_LOCAL_IP, localIP)
            putInt(KEY_LOCAL_PORT, localPort)
            putBoolean(KEY_USE_COMPRESSION, useCompression)
            putBoolean(KEY_USE_ENCRYPTION, useEncryption)
            putBoolean(KEY_LOGIN_FAIL_EXIT, loginFailExit)
            putString(KEY_DNS_SERVER, dnsServer)
            apply()
        }
    }
    
    /**
     * Validate configuration
     */
    fun isValid(): Boolean {
        return serverAddr.isNotBlank() &&
                serverPort in 1..65535 &&
                authToken.isNotBlank() &&
                proxyName.isNotBlank() &&
                secretKey.isNotBlank() &&
                localPort in 1..65535
    }
    
    /**
     * Generate TOML configuration file content
     */
    fun toToml(): String {
        return buildString {
            appendLine("serverAddr = \"$serverAddr\"")
            appendLine("serverPort = $serverPort")
            appendLine("auth.token = \"$authToken\"")
            appendLine("transport.tls.enable = $tlsEnable")
            appendLine("loginFailExit = $loginFailExit")
            appendLine("dnsServer = \"$dnsServer\"")
            appendLine()
            appendLine("[[proxies]]")
            appendLine("name = \"$proxyName\"")
            appendLine("type = \"${proxyType.value}\"")
            appendLine("localIP = \"$localIP\"")
            appendLine("localPort = $localPort")
            appendLine("secretKey = \"$secretKey\"")
            appendLine("transport.useCompression = $useCompression")
            appendLine("transport.useEncryption = $useEncryption")
        }
    }
}

/**
 * Proxy type enumeration
 */
enum class ProxyType(val value: String, val displayName: String) {
    STCP("stcp", "STCP (安全TCP)"),
    XTCP("xtcp", "XTCP (P2P TCP)");
    
    companion object {
        fun fromValue(value: String): ProxyType {
            return values().find { it.value == value } ?: STCP
        }
    }
}
