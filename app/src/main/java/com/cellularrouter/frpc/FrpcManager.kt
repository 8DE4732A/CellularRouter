package com.cellularrouter.frpc

import android.content.Context
import android.util.Log
import com.cellularrouter.network.NetworkManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Manages frpc process lifecycle
 */
class FrpcManager(
    private val context: Context,
    private val networkManager: NetworkManager
) {
    companion object {
        private const val TAG = "FrpcManager"
        private const val FRPC_LIB_NAME = "libfrpc.so"
        private const val CONFIG_FILE_NAME = "frpc.toml"
    }
    
    private var frpcProcess: Process? = null
    private var monitorJob: Job? = null
    private var isRunning = false
    
    /**
     * Start frpc with given configuration
     */
    suspend fun start(config: FrpcConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if already running
            if (isRunning) {
                return@withContext Result.failure(Exception("FRPC is already running"))
            }
            
            // Validate configuration
            if (!config.isValid()) {
                return@withContext Result.failure(Exception("Invalid FRPC configuration"))
            }
            
            // Check WiFi availability
            if (!networkManager.isWifiAvailable()) {
                return@withContext Result.failure(Exception("WiFi network not available"))
            }
            
            // Generate configuration file
            val configFile = generateConfigFile(config)
            if (!configFile.exists()) {
                return@withContext Result.failure(Exception("Failed to create config file"))
            }
            
            // Get frpc executable
            val frpcExecutable = getFrpcExecutable()
            if (!frpcExecutable.exists()) {
                return@withContext Result.failure(Exception("FRPC executable not found"))
            }
            
            // Make executable
            frpcExecutable.setExecutable(true)
            
            // Bind process to WiFi network
            if (!networkManager.bindProcessToWifi()) {
                Log.w(TAG, "Failed to bind process to WiFi, continuing anyway")
            }
            
            // Start frpc process
            val command = arrayOf(frpcExecutable.absolutePath, "-c", configFile.absolutePath)
            Log.d(TAG, "Starting FRPC: ${command.joinToString(" ")}")
            
            frpcProcess = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            
            isRunning = true
            
            // Monitor process output
            monitorOutput()
            
            Log.i(TAG, "FRPC started successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FRPC", e)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Stop frpc process
     */
    fun stop() {
        try {
            isRunning = false
            monitorJob?.cancel()
            frpcProcess?.destroy()
            frpcProcess = null
            
            // Unbind process from WiFi
            networkManager.unbindProcess()
            
            Log.i(TAG, "FRPC stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping FRPC", e)
        }
    }
    
    /**
     * Check if frpc is running
     */
    fun isRunning(): Boolean {
        val process = frpcProcess
        return isRunning && process != null && process.isAlive
    }
    
    /**
     * Generate TOML configuration file
     */
    private fun generateConfigFile(config: FrpcConfig): File {
        val configDir = File(context.filesDir, "frpc")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        val configFile = File(configDir, CONFIG_FILE_NAME)
        configFile.writeText(config.toToml())
        
        // Set file permissions to private
        configFile.setReadable(true, true)
        configFile.setWritable(true, true)
        
        Log.d(TAG, "Config file created: ${configFile.absolutePath}")
        return configFile
    }
    
    /**
     * Get frpc executable file from native library
     */
    private fun getFrpcExecutable(): File {
        // Extract from APK's lib directory
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val frpcLib = File(nativeLibDir, FRPC_LIB_NAME)
        
        if (!frpcLib.exists()) {
            Log.e(TAG, "FRPC library not found in: ${nativeLibDir.absolutePath}")
            Log.d(TAG, "Available files: ${nativeLibDir.listFiles()?.joinToString { it.name }}")
        }
        
        return frpcLib
    }
    
    /**
     * Monitor frpc process output
     */
    private fun monitorOutput() {
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            val process = frpcProcess ?: return@launch
            
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (isRunning && reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "FRPC: $line")
                    }
                }
                
                val exitCode = process.waitFor()
                Log.i(TAG, "FRPC process exited with code: $exitCode")
                
                if (isRunning && exitCode != 0) {
                    // Process exited unexpectedly
                    Log.w(TAG, "FRPC exited unexpectedly, cleaning up")
                    cleanup()
                }
                
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Error monitoring FRPC output", e)
                    cleanup()
                }
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        isRunning = false
        monitorJob?.cancel()
        frpcProcess?.destroyForcibly()
        frpcProcess = null
    }
    
    /**
     * Get frpc process status information
     */
    fun getStatus(): String {
        return when {
            !isRunning -> "未运行"
            frpcProcess == null -> "已停止"
            frpcProcess?.isAlive == true -> "运行中"
            else -> "已停止"
        }
    }
}
