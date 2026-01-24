package com.example.myspeaker

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * OTA Firmware Downloader with Google Drive Integration
 * Downloads encrypted firmware from Google Drive and handles decryption
 */
class OtaDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "OtaDownloader"
        
        // Google Drive file IDs - UPDATE THESE WITH YOUR FILES
        private const val GDRIVE_LATEST_TXT_ID = "1fHQ4qn4enJ5hXY0BJX1fTKX09guNOb2y"
        
        // AES-256 Key - MUST MATCH recovery_main.cpp and encrypt_firmware.py!
        private val AES_KEY = byteArrayOf(
            0x5A.toByte(), 0x2B.toByte(), 0x9C.toByte(), 0x4E.toByte(),
            0x1F.toByte(), 0x8D.toByte(), 0x6A.toByte(), 0x3C.toByte(),
            0x7B.toByte(), 0x0E.toByte(), 0x4F.toByte(), 0x2D.toByte(),
            0x8C.toByte(), 0x5A.toByte(), 0x1B.toByte(), 0x9E.toByte(),
            0x3D.toByte(), 0x6C.toByte(), 0x0F.toByte(), 0x4A.toByte(),
            0x7E.toByte(), 0x2B.toByte(), 0x8D.toByte(), 0x5C.toByte(),
            0x1A.toByte(), 0x9F.toByte(), 0x3E.toByte(), 0x6B.toByte(),
            0x0D.toByte(), 0x4C.toByte(), 0x7A.toByte(), 0x2E.toByte()
        )
        
        private const val AES_BLOCK_SIZE = 16
        private const val DOWNLOAD_CHUNK_SIZE = 4096
    }
    
    data class FirmwareInfo(
        val version: String,
        val fileId: String
    )
    
    interface DownloadProgressListener {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, percentage: Int)
        fun onComplete(encryptedFile: File)
        fun onError(error: String)
    }
    
    /**
     * Check for available firmware updates
     */
    suspend fun checkForUpdates(currentVersion: String): Result<FirmwareInfo?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates. Current version: $currentVersion")
            
            // Download latest.txt from Google Drive
            val latestTxtUrl = "https://drive.google.com/uc?export=download&id=$GDRIVE_LATEST_TXT_ID"
            val latestContent = downloadTextFile(latestTxtUrl)
            
            // Parse: VERSION,FILE_ID
            val parts = latestContent.trim().split(",")
            if (parts.size != 2) {
                return@withContext Result.failure(Exception("Invalid latest.txt format"))
            }
            
            val availableVersion = parts[0].trim()
            val firmwareFileId = parts[1].trim()
            
            Log.d(TAG, "Available version: $availableVersion, Current: $currentVersion")
            
            // Compare versions
            if (isNewerVersion(availableVersion, currentVersion)) {
                Result.success(FirmwareInfo(availableVersion, firmwareFileId))
            } else {
                Result.success(null) // No update available
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download encrypted firmware from Google Drive
     */
    suspend fun downloadFirmware(
        firmwareInfo: FirmwareInfo,
        listener: DownloadProgressListener
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading firmware ${firmwareInfo.version} (ID: ${firmwareInfo.fileId})")
            
            // Create temp file for encrypted firmware
            val encryptedFile = File(context.cacheDir, "firmware_${firmwareInfo.version}.enc")
            if (encryptedFile.exists()) {
                encryptedFile.delete()
            }
            
            // Google Drive direct download URL
            val downloadUrl = "https://drive.google.com/uc?export=download&id=${firmwareInfo.fileId}"
            
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            try {
                connection.connect()
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("HTTP error: $responseCode"))
                }
                
                val contentLength = connection.contentLength.toLong()
                Log.d(TAG, "Content length: $contentLength bytes")
                
                connection.inputStream.use { input ->
                    FileOutputStream(encryptedFile).use { output ->
                        val buffer = ByteArray(DOWNLOAD_CHUNK_SIZE)
                        var totalDownloaded = 0L
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            
                            val percentage = if (contentLength > 0) {
                                ((totalDownloaded * 100) / contentLength).toInt()
                            } else {
                                0
                            }
                            
                            withContext(Dispatchers.Main) {
                                listener.onProgress(totalDownloaded, contentLength, percentage)
                            }
                        }
                        
                        output.flush()
                    }
                }
                
                Log.d(TAG, "Download complete: ${encryptedFile.absolutePath} (${encryptedFile.length()} bytes)")
                
                withContext(Dispatchers.Main) {
                    listener.onComplete(encryptedFile)
                }
                
                Result.success(encryptedFile)
                
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading firmware", e)
            withContext(Dispatchers.Main) {
                listener.onError(e.message ?: "Download failed")
            }
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt a chunk of encrypted firmware data
     * Returns the decrypted data (handles IV extraction and padding removal)
     */
    fun decryptChunk(encryptedData: ByteArray, isFirstChunk: Boolean): DecryptResult {
        if (isFirstChunk && encryptedData.size < AES_BLOCK_SIZE) {
            return DecryptResult(ByteArray(0), null, "First chunk must contain IV")
        }
        
        return if (isFirstChunk) {
            // Extract IV from first 16 bytes
            val iv = encryptedData.copyOfRange(0, AES_BLOCK_SIZE)
            val ciphertext = encryptedData.copyOfRange(AES_BLOCK_SIZE, encryptedData.size)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(AES_KEY, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decrypted = cipher.doFinal(ciphertext)
            DecryptResult(decrypted, iv, null)
        } else {
            // Continue decryption with provided IV
            DecryptResult(ByteArray(0), null, "IV required for subsequent chunks")
        }
    }
    
    /**
     * Decrypt entire firmware file
     */
    fun decryptFirmwareFile(encryptedFile: File): Result<ByteArray> {
        return try {
            val encryptedData = encryptedFile.readBytes()
            
            if (encryptedData.size < AES_BLOCK_SIZE) {
                return Result.failure(Exception("Encrypted file too small"))
            }
            
            // Extract IV (first 16 bytes)
            val iv = encryptedData.copyOfRange(0, AES_BLOCK_SIZE)
            val ciphertext = encryptedData.copyOfRange(AES_BLOCK_SIZE, encryptedData.size)
            
            // Decrypt
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(AES_KEY, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decrypted = cipher.doFinal(ciphertext)
            
            Log.d(TAG, "Decryption complete: ${decrypted.size} bytes")
            Result.success(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error", e)
            Result.failure(e)
        }
    }
    
    data class DecryptResult(
        val decryptedData: ByteArray,
        val iv: ByteArray?,
        val error: String?
    )
    
    private suspend fun downloadTextFile(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connect()
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun isNewerVersion(available: String, current: String): Boolean {
        try {
            val availableParts = available.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            for (i in 0 until maxOf(availableParts.size, currentParts.size)) {
                val a = availableParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                
                if (a > c) return true
                if (a < c) return false
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
            return false
        }
    }
    
    /**
     * Clean up downloaded firmware files
     */
    fun cleanup() {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("firmware_") && file.name.endsWith(".enc")) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up", e)
        }
    }
}
