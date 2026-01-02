package com.cellularrouter

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe traffic monitor for tracking network usage
 */
object TrafficMonitor {
    private val uploadedBytes = AtomicLong(0)
    private val downloadedBytes = AtomicLong(0)
    
    /**
     * Record uploaded bytes
     */
    fun addUpload(bytes: Long) {
        uploadedBytes.addAndGet(bytes)
    }
    
    /**
     * Record downloaded bytes
     */
    fun addDownload(bytes: Long) {
        downloadedBytes.addAndGet(bytes)
    }
    
    /**
     * Get total uploaded bytes
     */
    fun getUploaded(): Long = uploadedBytes.get()
    
    /**
     * Get total downloaded bytes
     */
    fun getDownloaded(): Long = downloadedBytes.get()
    
    /**
     * Get total traffic (upload + download)
     */
    fun getTotal(): Long = uploadedBytes.get() + downloadedBytes.get()
    
    /**
     * Reset all counters
     */
    fun reset() {
        uploadedBytes.set(0)
        downloadedBytes.set(0)
    }
    
    /**
     * Format bytes to human-readable string
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
