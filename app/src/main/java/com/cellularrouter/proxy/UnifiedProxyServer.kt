package com.cellularrouter.proxy

import android.util.Log
import com.cellularrouter.network.NetworkManager
import kotlinx.coroutines.*
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * Unified proxy server that handles both SOCKS5 and HTTP protocols on a single port
 * Protocol is detected by inspecting the first byte of the connection
 */
class UnifiedProxyServer(
    private val port: Int,
    private val networkManager: NetworkManager
) {
    companion object {
        private const val TAG = "UnifiedProxyServer"
        private const val SOCKS5_VERSION: Byte = 0x05
    }
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val socks5Handler = Socks5Handler(networkManager)
    private val httpHandler = HttpHandler(networkManager)
    
    /**
     * Start the proxy server
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return
        }
        
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            
            Log.i(TAG, "Unified proxy server started on port $port")
            
            scope.launch {
                acceptConnections()
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start server on port $port", e)
            throw e
        }
    }
    
    /**
     * Stop the proxy server
     */
    fun stop() {
        if (!isRunning) return
        
        isRunning = false
        
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        
        scope.cancel()
        
        Log.i(TAG, "Proxy server stopped")
    }
    
    /**
     * Accept incoming connections
     */
    private suspend fun acceptConnections() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                val clientSocket = serverSocket?.accept() ?: break
                
                Log.d(TAG, "Accepted connection from ${clientSocket.remoteSocketAddress}")
                
                // Handle each connection in a separate coroutine
                launch {
                    handleConnection(clientSocket)
                }
                
            } catch (e: IOException) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting connection", e)
                }
                break
            }
        }
    }
    
    /**
     * Handle a client connection by detecting protocol and delegating to appropriate handler
     */
    private suspend fun handleConnection(clientSocket: Socket) {
        try {
            // Set socket timeout for protocol detection
            clientSocket.soTimeout = 5000
            
            val input = clientSocket.getInputStream()
            
            // Peek at first byte to detect protocol
            input.mark(1)
            val firstByte = input.read()
            input.reset()
            
            if (firstByte < 0) {
                Log.w(TAG, "Connection closed before protocol detection")
                clientSocket.close()
                return
            }
            
            // Reset timeout for actual data transfer
            clientSocket.soTimeout = 0
            
            when (firstByte.toByte()) {
                SOCKS5_VERSION -> {
                    Log.d(TAG, "Detected SOCKS5 protocol")
                    socks5Handler.handle(clientSocket)
                }
                else -> {
                    // Check if it's an ASCII letter (HTTP methods start with letters)
                    val char = firstByte.toChar()
                    if (char.isLetter()) {
                        Log.d(TAG, "Detected HTTP protocol")
                        httpHandler.handle(clientSocket)
                    } else {
                        Log.w(TAG, "Unknown protocol, first byte: 0x${firstByte.toString(16)}")
                        clientSocket.close()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connection", e)
            try {
                clientSocket.close()
            } catch (e2: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Check if server is running
     */
    fun isRunning(): Boolean = isRunning
}
