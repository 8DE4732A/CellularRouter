package com.cellularrouter.frpc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cellularrouter.MainActivity
import com.cellularrouter.R
import com.cellularrouter.network.NetworkManager
import kotlinx.coroutines.*

/**
 * Foreground service for running frpc
 */
class FrpcService : Service() {
    
    companion object {
        private const val TAG = "FrpcService"
        const val ACTION_START = "com.cellularrouter.frpc.START"
        const val ACTION_STOP = "com.cellularrouter.frpc.STOP"
        const val EXTRA_CONFIG = "config"
        
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "frpc_service"
        private const val CHANNEL_NAME = "FRPC Service"
    }
    
    private val binder = FrpcBinder()
    private lateinit var networkManager: NetworkManager
    private lateinit var frpcManager: FrpcManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    inner class FrpcBinder : Binder() {
        fun getService(): FrpcService = this@FrpcService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        networkManager = NetworkManager(this)
        frpcManager = FrpcManager(this, networkManager)
        networkManager.start()
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = FrpcConfig.load(this)
                startFrpc(config)
            }
            ACTION_STOP -> {
                stopFrpc()
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
        
        stopFrpc()
        networkManager.stop()
        serviceScope.cancel()
    }
    
    /**
     * Start frpc with configuration
     */
    private fun startFrpc(config: FrpcConfig) {
        if (frpcManager.isRunning()) {
            Log.w(TAG, "FRPC is already running")
            return
        }
        
        // Start as foreground service
        val notification = createNotification(
            "FRPC运行中",
            "连接到: ${config.serverAddr}:${config.serverPort}"
        )
        startForeground(NOTIFICATION_ID, notification)
        
        // Start frpc in coroutine
        serviceScope.launch {
            val result = frpcManager.start(config)
            
            result.onSuccess {
                Log.i(TAG, "FRPC started successfully")
                updateNotification(
                    "FRPC已连接",
                    "服务器: ${config.serverAddr}"
                )
            }.onFailure { e ->
                Log.e(TAG, "Failed to start FRPC", e)
                updateNotification(
                    "FRPC启动失败",
                    e.message ?: "未知错误"
                )
                
                // Stop service after delay
                serviceScope.launch {
                    delay(3000)
                    stopSelf()
                }
            }
        }
    }
    
    /**
     * Stop frpc
     */
    private fun stopFrpc() {
        frpcManager.stop()
        Log.i(TAG, "FRPC stopped")
    }
    
    /**
     * Check if frpc is running
     */
    fun isRunning(): Boolean {
        return frpcManager.isRunning()
    }
    
    /**
     * Get frpc status
     */
    fun getStatus(): String {
        return frpcManager.getStatus()
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "FRPC服务通知"
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Update notification
     */
    private fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
