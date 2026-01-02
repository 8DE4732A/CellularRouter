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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cellularrouter.data.ProxyConfig
import com.cellularrouter.databinding.ActivityMainBinding
import com.cellularrouter.frpc.FrpcConfig
import com.cellularrouter.frpc.FrpcService
import com.cellularrouter.frpc.ProxyType
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    
    private var frpcService: FrpcService? = null
    private var frpcBound = false
    
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
    
    private val frpcConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FrpcService.FrpcBinder
            frpcService = binder.getService()
            frpcBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            frpcService = null
            frpcBound = false
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
        
        // FRPC控制 - 如果UI元素存在
        try {
            binding.frpcConfigButton.setOnClickListener {
                showFrpcConfigDialog()
            }
            
            binding.frpcControlButton.setOnClickListener {
                toggleFrpc()
            }
        } catch (e: Exception) {
            // UI元素不存在，使用菜单代替
        }
        
        // Show info dialog on first launch
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_launch", true)) {
            showInfoDialog()
            prefs.edit().putBoolean("first_launch", false).apply()
        }
        
        // Bind to services
        bindToService()
        bindToFrpcService()

        // Check IP button
        try {
            binding.checkIpButton.setOnClickListener {
                checkProxyIp()
            }
        } catch (e: Exception) {
            // Button might not be in layout yet
        }
    }

    private fun checkProxyIp() {
        val networkManager = proxyService?.getNetworkManager()
        if (networkManager == null) {
            Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!networkManager.isCellularAvailable()) {
            Toast.makeText(this, R.string.error_cellular_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.check_ip_title)
            .setMessage(R.string.checking_ip)
            .setPositiveButton("OK", null)
            .show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val network = networkManager.getCellularNetworkObject()
                if (network == null) {
                   withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, R.string.error_cellular_unavailable, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val url = java.net.URL("http://ip-api.com/json")
                val connection = network.openConnection(url) as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Simple JSON parsing to find "query" field which holds the IP
                val ip = try {
                    val queryIndex = response.indexOf("\"query\":\"")
                    if (queryIndex != -1) {
                        val startIndex = queryIndex + 9
                        val endIndex = response.indexOf("\"", startIndex)
                        if (endIndex != -1) {
                            response.substring(startIndex, endIndex)
                        } else response
                    } else response
                } catch (e: Exception) {
                    response
                }

                withContext(Dispatchers.Main) {
                    if (dialog.isShowing) {
                        dialog.setMessage("Current IP: $ip\n\nFull Response:\n$response")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (dialog.isShowing) {
                        dialog.setMessage(getString(R.string.error_check_ip, e.message))
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_frpc_config -> {
                showFrpcConfigDialog()
                true
            }
            R.id.menu_frpc_toggle -> {
                toggleFrpc()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val toggleItem = menu?.findItem(R.id.menu_frpc_toggle)
        val isRunning = frpcService?.isRunning() == true
        toggleItem?.title = if (isRunning) {
            getString(R.string.frpc_stop)
        } else {
            getString(R.string.frpc_start)
        }
        toggleItem?.setIcon(
            if (isRunning) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        if (frpcBound) {
            unbindService(frpcConnection)
            frpcBound = false
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
     * Bind to FrpcService
     */
    private fun bindToFrpcService() {
        val intent = Intent(this, FrpcService::class.java)
        bindService(intent, frpcConnection, Context.BIND_AUTO_CREATE)
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
        
        // Update FRPC menu
        invalidateOptionsMenu()
        
        // Update FRPC status UI if elements exist
        try {
            val frpcIsRunning = frpcService?.isRunning() == true
            binding.frpcStatusText.text = if (frpcIsRunning) {
                getString(R.string.frpc_running)
            } else {
                getString(R.string.frpc_stopped)
            }
            binding.frpcStatusText.setTextColor(
                ContextCompat.getColor(this, if (frpcIsRunning) R.color.success else R.color.error)
            )
            
           binding.frpcControlButton.text = if (frpcIsRunning) {
                getString(R.string.frpc_stop)
            } else {
                getString(R.string.frpc_start)
            }
            binding.frpcControlButton.setIconResource(
                if (frpcIsRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        } catch (e: Exception) {
            // UI元素不存在，已通过菜单控制
        }
    }
    
    /**
     * Toggle FRPC service on/off
     */
    private fun toggleFrpc() {
        if (frpcService?.isRunning() == true) {
            stopFrpcService()
        } else {
            startFrpcService()
        }
    }
    
    /**
     * Start FRPC service
     */
    private fun startFrpcService() {
        // Check WiFi availability
        val networkManager = proxyService?.getNetworkManager()
        if (networkManager?.isWifiAvailable() != true) {
            Toast.makeText(this, R.string.error_frpc_wifi_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Load and validate config
        val config = FrpcConfig.load(this)
        if (!config.isValid()) {
            Toast.makeText(this, R.string.error_frpc_invalid_config, Toast.LENGTH_SHORT).show()
            showFrpcConfigDialog()
            return
        }
        
        // Start service
        val intent = Intent(this, FrpcService::class.java).apply {
            action = FrpcService.ACTION_START
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "正在启动FRPC...", Toast.LENGTH_SHORT).show()
        updateUI()
    }
    
    /**
     * Stop FRPC service
     */
    private fun stopFrpcService() {
        val intent = Intent(this, FrpcService::class.java).apply {
            action = FrpcService.ACTION_STOP
        }
        startService(intent)
        
        Toast.makeText(this, "正在停止FRPC...", Toast.LENGTH_SHORT).show()
        updateUI()
    }
    
    /**
     * Show FRPC configuration dialog
     */
    private fun showFrpcConfigDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_frpc_config, null)
        val config = FrpcConfig.load(this)
        
        // Initialize views
        val serverAddrEdit = dialogView.findViewById<TextInputEditText>(R.id.serverAddrEditText)
        val serverPortEdit = dialogView.findViewById<TextInputEditText>(R.id.serverPortEditText)
        val authTokenEdit = dialogView.findViewById<TextInputEditText>(R.id.authTokenEditText)
        val tlsSwitch = dialogView.findViewById<SwitchMaterial>(R.id.tlsEnableSwitch)
        val proxyTypeDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.proxyTypeDropdown)
        val proxyNameEdit = dialogView.findViewById<TextInputEditText>(R.id.proxyNameEditText)
        val secretKeyEdit = dialogView.findViewById<TextInputEditText>(R.id.secretKeyEditText)
        val compressionSwitch = dialogView.findViewById<SwitchMaterial>(R.id.useCompressionSwitch)
        val encryptionSwitch = dialogView.findViewById<SwitchMaterial>(R.id.useEncryptionSwitch)
        
        // Set up proxy type dropdown
        val proxyTypes = ProxyType.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, proxyTypes)
        proxyTypeDropdown.setAdapter(adapter)
        
        // Populate with saved config
        serverAddrEdit.setText(config.serverAddr)
        serverPortEdit.setText(config.serverPort.toString())
        authTokenEdit.setText(config.authToken)
        tlsSwitch.isChecked = config.tlsEnable
        proxyTypeDropdown.setText(config.proxyType.displayName, false)
        proxyNameEdit.setText(config.proxyName)
        secretKeyEdit.setText(config.secretKey)
        compressionSwitch.isChecked = config.useCompression
        encryptionSwitch.isChecked = config.useEncryption
        
        // Sync local port with proxy config
        val proxyConfig = ProxyConfig.load(this)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.frpc_config_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                // Save configuration
                val selectedTypeDisplayName = proxyTypeDropdown.text.toString()
                val proxyType = ProxyType.values().find { it.displayName == selectedTypeDisplayName }
                    ?: ProxyType.STCP
                
                val newConfig = FrpcConfig(
                    serverAddr = serverAddrEdit.text.toString(),
                    serverPort = serverPortEdit.text.toString().toIntOrNull() ?: 7000,
                    authToken = authTokenEdit.text.toString(),
                    tlsEnable = tlsSwitch.isChecked,
                    proxyType = proxyType,
                    proxyName = proxyNameEdit.text.toString(),
                    secretKey = secretKeyEdit.text.toString(),
                    localIP = "127.0.0.1",
                    localPort = proxyConfig.port,
                    useCompression = compressionSwitch.isChecked,
                    useEncryption = encryptionSwitch.isChecked
                )
                
                newConfig.save(this)
                Toast.makeText(this, "FRPC配置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
