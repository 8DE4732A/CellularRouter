package com.cellularrouter.proxy

import android.util.Log
import com.cellularrouter.TrafficMonitor
import com.cellularrouter.network.NetworkManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket

/**
 * HTTP/HTTPS proxy handler
 * Supports CONNECT method for HTTPS and direct proxying for HTTP
 */
class HttpHandler(
    private val networkManager: NetworkManager
) {
    companion object {
        private const val TAG = "HttpHandler"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 10000
    }
    
    /**
     * Handle an HTTP proxy connection
     */
    suspend fun handle(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val output = clientSocket.getOutputStream()
            
            // Read HTTP request line
            val requestLine = input.readLine() ?: return@withContext
            Log.d(TAG, "HTTP request: $requestLine")
            
            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                Log.w(TAG, "Invalid HTTP request line")
                return@withContext
            }
            
            val method = parts[0]
            val url = parts[1]
            
            when (method.uppercase()) {
                "CONNECT" -> handleConnect(clientSocket, url, input, output)
                else -> handleHttpProxy(clientSocket, requestLine, input, output)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling HTTP connection", e)
        } finally {
            clientSocket.close()
        }
    }
    
    /**
     * Handle CONNECT method for HTTPS tunneling
     */
    private suspend fun handleConnect(
        clientSocket: Socket,
        authority: String,
        input: BufferedReader,
        output: java.io.OutputStream
    ) = withContext(Dispatchers.IO) {
        try {
            // Parse host and port
            val (host, port) = parseAuthority(authority)
            
            Log.d(TAG, "CONNECT to $host:$port")
            
            // Skip remaining headers
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }
            
            // Connect to target server
            val remoteSocket = Socket()
            
            try {
                // Bind to cellular network
                if (!networkManager.bindSocketToCellular(remoteSocket)) {
                    Log.w(TAG, "Failed to bind to cellular network")
                }
                
                remoteSocket.connect(java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT)
                Log.d(TAG, "Connected to $host:$port")
                
                // Send connection established response
                val response = "HTTP/1.1 200 Connection Established\r\n\r\n"
                output.write(response.toByteArray())
                output.flush()
                
                // Relay data bidirectionally
                relay(clientSocket, remoteSocket)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $host:$port", e)
                val response = "HTTP/1.1 502 Bad Gateway\r\n\r\n"
                output.write(response.toByteArray())
                output.flush()
            } finally {
                remoteSocket.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in CONNECT handler", e)
        }
    }
    
    /**
     * Handle direct HTTP proxy (for non-HTTPS requests)
     */
    private suspend fun handleHttpProxy(
        clientSocket: Socket,
        requestLine: String,
        input: BufferedReader,
        output: java.io.OutputStream
    ) = withContext(Dispatchers.IO) {
        try {
            // Parse URL to extract host and port
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext
            
            val url = parts[1]
            val (host, port, path) = parseUrl(url)
            
            Log.d(TAG, "HTTP proxy to $host:$port$path")
            
            // Read headers
            val headers = mutableListOf<String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                headers.add(line)
            }
            
            // Connect to target server
            val remoteSocket = Socket()
            
            try {
                // Bind to cellular network
                if (!networkManager.bindSocketToCellular(remoteSocket)) {
                    Log.w(TAG, "Failed to bind to cellular network")
                }
                
                remoteSocket.connect(java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT)
                
                // Send modified request to target
                val remoteOutput = remoteSocket.getOutputStream()
                val modifiedRequest = "${parts[0]} $path ${parts[2]}\r\n"
                remoteOutput.write(modifiedRequest.toByteArray())
                
                // Forward headers (except proxy-specific ones)
                headers.forEach { header ->
                    if (!header.startsWith("Proxy-", ignoreCase = true)) {
                        remoteOutput.write("$header\r\n".toByteArray())
                    }
                }
                remoteOutput.write("\r\n".toByteArray())
                remoteOutput.flush()
                
                // Relay response and data
                relay(clientSocket, remoteSocket)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to proxy HTTP request", e)
                val response = "HTTP/1.1 502 Bad Gateway\r\n\r\n"
                output.write(response.toByteArray())
                output.flush()
            } finally {
                remoteSocket.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in HTTP proxy handler", e)
        }
    }
    
    /**
     * Parse authority (host:port) from CONNECT request
     */
    private fun parseAuthority(authority: String): Pair<String, Int> {
        val parts = authority.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 443 else 443
        return Pair(host, port)
    }
    
    /**
     * Parse URL to extract host, port, and path
     */
    private fun parseUrl(url: String): Triple<String, Int, String> {
        val urlWithoutProtocol = url.removePrefix("http://").removePrefix("https://")
        val slashIndex = urlWithoutProtocol.indexOf('/')
        
        val hostPort: String
        val path: String
        
        if (slashIndex >= 0) {
            hostPort = urlWithoutProtocol.substring(0, slashIndex)
            path = urlWithoutProtocol.substring(slashIndex)
        } else {
            hostPort = urlWithoutProtocol
            path = "/"
        }
        
        val colonIndex = hostPort.indexOf(':')
        val host: String
        val port: Int
        
        if (colonIndex >= 0) {
            host = hostPort.substring(0, colonIndex)
            port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: 80
        } else {
            host = hostPort
            port = 80
        }
        
        return Triple(host, port, path)
    }
    
    /**
     * Relay data between client and remote server
     */
    private suspend fun relay(clientSocket: Socket, remoteSocket: Socket) = coroutineScope {
        val job1 = launch {
            relayData(clientSocket.getInputStream(), remoteSocket.getOutputStream(), true)
        }
        
        val job2 = launch {
            relayData(remoteSocket.getInputStream(), clientSocket.getOutputStream(), false)
        }
        
        // Wait for either direction to finish
        job1.join()
        job2.cancelAndJoin()
    }
    
    /**
     * Relay data from input to output stream
     */
    private suspend fun relayData(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        isUpload: Boolean
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (isActive) {
                val count = input.read(buffer)
                if (count <= 0) break
                
                output.write(buffer, 0, count)
                output.flush()
                
                // Track traffic
                if (isUpload) {
                    TrafficMonitor.addUpload(count.toLong())
                } else {
                    TrafficMonitor.addDownload(count.toLong())
                }
            }
        } catch (e: IOException) {
            // Connection closed or error
        }
    }
}
