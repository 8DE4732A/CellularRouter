package com.cellularrouter.proxy

import android.util.Log
import com.cellularrouter.TrafficMonitor
import com.cellularrouter.network.NetworkManager
import kotlinx.coroutines.*
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer

/**
 * SOCKS5 protocol handler
 * Implements RFC 1928
 */
class Socks5Handler(
    private val networkManager: NetworkManager
) {
    companion object {
        private const val TAG = "Socks5Handler"
        
        // SOCKS5 constants
        private const val SOCKS_VERSION: Byte = 0x05
        private const val METHOD_NO_AUTH: Byte = 0x00
        private const val CMD_CONNECT: Byte = 0x01
        private const val ATYP_IPV4: Byte = 0x01
        private const val ATYP_DOMAIN: Byte = 0x03
        private const val ATYP_IPV6: Byte = 0x04
        private const val REP_SUCCESS: Byte = 0x00
        private const val REP_FAILURE: Byte = 0x01
        
        private const val BUFFER_SIZE = 8192
    }
    
    /**
     * Handle a SOCKS5 connection
     */
    suspend fun handle(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            // Step 1: Version negotiation
            val versionBuffer = ByteArray(2)
            if (input.read(versionBuffer) != 2) {
                Log.w(TAG, "Failed to read version")
                return@withContext
            }
            
            val version = versionBuffer[0]
            val nmethods = versionBuffer[1].toInt() and 0xFF
            
            if (version != SOCKS_VERSION) {
                Log.w(TAG, "Invalid SOCKS version: $version")
                return@withContext
            }
            
            // Read methods
            val methods = ByteArray(nmethods)
            if (input.read(methods) != nmethods) {
                Log.w(TAG, "Failed to read methods")
                return@withContext
            }
            
            // Send method selection (no auth)
            output.write(byteArrayOf(SOCKS_VERSION, METHOD_NO_AUTH))
            output.flush()
            
            // Step 2: Request
            val requestHeader = ByteArray(4)
            if (input.read(requestHeader) != 4) {
                Log.w(TAG, "Failed to read request header")
                return@withContext
            }
            
            val cmd = requestHeader[1]
            val atyp = requestHeader[3]
            
            if (cmd != CMD_CONNECT) {
                Log.w(TAG, "Unsupported command: $cmd")
                sendReply(output, REP_FAILURE)
                return@withContext
            }
            
            // Parse target address
            val targetHost: String
            val targetPort: Int
            
            when (atyp) {
                ATYP_IPV4 -> {
                    val addr = ByteArray(4)
                    if (input.read(addr) != 4) {
                        sendReply(output, REP_FAILURE)
                        return@withContext
                    }
                    targetHost = "${addr[0].toInt() and 0xFF}.${addr[1].toInt() and 0xFF}.${addr[2].toInt() and 0xFF}.${addr[3].toInt() and 0xFF}"
                }
                ATYP_DOMAIN -> {
                    val len = input.read()
                    if (len < 0) {
                        sendReply(output, REP_FAILURE)
                        return@withContext
                    }
                    val domain = ByteArray(len)
                    if (input.read(domain) != len) {
                        sendReply(output, REP_FAILURE)
                        return@withContext
                    }
                    targetHost = String(domain)
                }
                ATYP_IPV6 -> {
                    // IPv6 not supported in this version
                    Log.w(TAG, "IPv6 not supported")
                    sendReply(output, REP_FAILURE)
                    return@withContext
                }
                else -> {
                    Log.w(TAG, "Invalid address type: $atyp")
                    sendReply(output, REP_FAILURE)
                    return@withContext
                }
            }
            
            // Read port
            val portBytes = ByteArray(2)
            if (input.read(portBytes) != 2) {
                sendReply(output, REP_FAILURE)
                return@withContext
            }
            targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
            
            Log.d(TAG, "SOCKS5 request: $targetHost:$targetPort")
            
            // Connect to target
            val remoteSocket = Socket()
            
            try {
                // Bind to cellular network
                if (!networkManager.bindSocketToCellular(remoteSocket)) {
                    Log.w(TAG, "Failed to bind to cellular network")
                }
                
                remoteSocket.connect(java.net.InetSocketAddress(targetHost, targetPort), 10000)
                Log.d(TAG, "Connected to $targetHost:$targetPort")
                
                // Send success reply
                sendReply(output, REP_SUCCESS)
                
                // Relay data bidirectionally
                relay(clientSocket, remoteSocket)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to target", e)
                sendReply(output, REP_FAILURE)
            } finally {
                remoteSocket.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling SOCKS5 connection", e)
        } finally {
            clientSocket.close()
        }
    }
    
    /**
     * Send SOCKS5 reply
     */
    private fun sendReply(output: java.io.OutputStream, rep: Byte) {
        try {
            val reply = byteArrayOf(
                SOCKS_VERSION,
                rep,
                0x00,  // Reserved
                ATYP_IPV4,
                0, 0, 0, 0,  // Bind address
                0, 0  // Bind port
            )
            output.write(reply)
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply", e)
        }
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
