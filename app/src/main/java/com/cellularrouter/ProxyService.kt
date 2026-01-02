package com.cellularrouter

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cellularrouter.data.ProxyConfig
import com.cellularrouter.network.NetworkManager
import com.cellularrouter.proxy.UnifiedProxyServer

/**
 * Foreground service that runs the proxy server
 */
class ProxyService : Service() {
    
    companion object {
        private const val TAG = "ProxyService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "proxy_service_channel"
        const val ACTION_START = "com.cellularrouter.action.START"
        const val ACTION_STOP = "com.cellularrouter.action.STOP"
        const val EXTRA_PORT = "port"
    }
    
    private val binder = ProxyBinder()
    private var proxyServer: UnifiedProxyServer? = null
    private var networkManager: NetworkManager? = null
    private var currentPort: Int = ProxyConfig.DEFAULT_PORT
    
    /**
     * Binder for clients to access service
     */
    inner class ProxyBinder : Binder() {
        fun getService(): ProxyService = this@ProxyService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize network manager
        networkManager = NetworkManager(this)
        networkManager?.start()
        
        // Create notification channel
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, ProxyConfig.DEFAULT_PORT)
                startProxyServer(port)
            }
            ACTION_STOP -> {
                stopProxyServer()
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        stopProxyServer()
        networkManager?.stop()
        networkManager = null
    }
    
    /**
     * Start the proxy server
     */
    fun startProxyServer(port: Int) {
        if (proxyServer?.isRunning() == true) {
            Log.w(TAG, "Proxy server already running")
            return
        }
        
        currentPort = port
        
        try {
            val netMgr = networkManager ?: throw IllegalStateException("NetworkManager not initialized")
            
            proxyServer = UnifiedProxyServer(port, netMgr)
            proxyServer?.start()
            
            Log.i(TAG, "Proxy server started on port $port")
            
            // Start foreground service
            val notification = createNotification("代理服务运行中", "端口: $port")
            startForeground(NOTIFICATION_ID, notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy server", e)
            proxyServer = null
        }
    }
    
    /**
     * Stop the proxy server
     */
    fun stopProxyServer() {
        proxyServer?.stop()
        proxyServer = null
        Log.i(TAG, "Proxy server stopped")
    }
    
    /**
     * Check if proxy server is running
     */
    fun isRunning(): Boolean {
        return proxyServer?.isRunning() == true
    }
    
    /**
     * Get current port
     */
    fun getPort(): Int = currentPort
    
    /**
     * Get network manager
     */
    fun getNetworkManager(): NetworkManager? = networkManager
    
    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "代理服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "蜂窝网络代理服务通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create notification
     */
    private fun createNotification(title: String, text: String): Notification {
        // Intent to open main activity
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Update notification
     */
    fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
