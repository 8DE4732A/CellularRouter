package com.cellularrouter

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cellularrouter.data.ProxyConfig
import com.cellularrouter.databinding.ActivityMainBinding

/**
 * Main activity for the Cellular Router app
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        private const val UPDATE_INTERVAL_MS = 1000L
    }
    
    private lateinit var binding: ActivityMainBinding
    private var proxyService: ProxyService? = null
    private var isBound = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ProxyService.ProxyBinder
            proxyService = binder.getService()
            isBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            proxyService = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
        
        // Load saved configuration
        val config = ProxyConfig.load(this)
        binding.portEditText.setText(config.port.toString())
        
        // Set up click listeners
        binding.controlButton.setOnClickListener {
            toggleProxy()
        }
        
        binding.resetStatsButton.setOnClickListener {
            resetStats()
        }
        
        // Show info dialog on first launch
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_launch", true)) {
            showInfoDialog()
            prefs.edit().putBoolean("first_launch", false).apply()
        }
        
        // Bind to service
        bindToService()
    }
    
    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    /**
     * Bind to ProxyService
     */
    private fun bindToService() {
        val intent = Intent(this, ProxyService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Toggle proxy service on/off
     */
    private fun toggleProxy() {
        val service = proxyService
        
        if (service == null) {
            Toast.makeText(this, "服务未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (service.isRunning()) {
            // Stop proxy
            stopProxy()
        } else {
            // Start proxy
            startProxy()
        }
    }
    
    /**
     * Start the proxy service
     */
    private fun startProxy() {
        // Validate port
        val portText = binding.portEditText.text.toString()
        val port = portText.toIntOrNull()
        
        if (port == null || !ProxyConfig.isValidPort(port)) {
            Toast.makeText(this, R.string.invalid_port, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if cellular is available
        val networkManager = proxyService?.getNetworkManager()
        if (networkManager?.isCellularAvailable() != true) {
            Toast.makeText(this, R.string.error_cellular_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save configuration
        val config = ProxyConfig(port)
        config.save(this)
        
        // Start service
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_START
            putExtra(ProxyService.EXTRA_PORT, port)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        updateUI()
    }
    
    /**
     * Stop the proxy service
     */
    private fun stopProxy() {
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_STOP
        }
        startService(intent)
        
        updateUI()
    }
    
    /**
     * Reset traffic statistics
     */
    private fun resetStats() {
        AlertDialog.Builder(this)
            .setTitle("重置统计")
            .setMessage("确定要重置流量统计吗？")
            .setPositiveButton("确定") { _, _ ->
                TrafficMonitor.reset()
                updateUI()
                Toast.makeText(this, "统计已重置", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * Update UI with current state
     */
    private fun updateUI() {
        val service = proxyService
        val networkManager = service?.getNetworkManager()
        
        // Update network status
        if (networkManager != null) {
            val wifiConnected = networkManager.isWifiConnected()
            binding.wifiStatusText.text = if (wifiConnected) {
                getString(R.string.connected)
            } else {
                getString(R.string.disconnected)
            }
            binding.wifiStatusText.setTextColor(
                ContextCompat.getColor(this, if (wifiConnected) R.color.success else R.color.error)
            )
            
            val cellularInfo = networkManager.getCellularNetworkInfo()
            binding.cellularStatusText.text = cellularInfo
            binding.cellularStatusText.setTextColor(
                ContextCompat.getColor(this, 
                    if (cellularInfo == "已连接") R.color.success else R.color.error
                )
            )
        } else {
            binding.wifiStatusText.text = getString(R.string.unavailable)
            binding.cellularStatusText.text = getString(R.string.unavailable)
        }
        
        // Update traffic stats
        binding.uploadText.text = TrafficMonitor.formatBytes(TrafficMonitor.getUploaded())
        binding.downloadText.text = TrafficMonitor.formatBytes(TrafficMonitor.getDownloaded())
        binding.totalText.text = TrafficMonitor.formatBytes(TrafficMonitor.getTotal())
        
        // Update control button
        val isRunning = service?.isRunning() == true
        if (isRunning) {
            binding.controlButton.text = getString(R.string.stop_proxy)
            binding.controlButton.setIconResource(android.R.drawable.ic_media_pause)
            binding.portEditText.isEnabled = false
        } else {
            binding.controlButton.text = getString(R.string.start_proxy)
            binding.controlButton.setIconResource(android.R.drawable.ic_media_play)
            binding.portEditText.isEnabled = true
        }
    }
    
    /**
     * Show info dialog
     */
    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.info_title)
            .setMessage(R.string.info_message)
            .setPositiveButton("知道了", null)
            .show()
    }
}
