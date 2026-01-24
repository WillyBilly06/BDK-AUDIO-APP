package com.example.myspeaker

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OTA Firmware Update Activity
 * 
 * Uses shared BLE connection from MainActivityRedesign for fast, reliable updates.
 * Implements the same batched ACK protocol as the original MainActivity.
 */
class OtaActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OtaActivity"
        
        // Shared connection from MainActivityRedesign
        @Volatile var sharedGatt: BluetoothGatt? = null
        @Volatile var sharedCmdChar: BluetoothGattCharacteristic? = null
        @Volatile var sharedMtu: Int = 23
        @Volatile var sharedIsConnected: Boolean = false
        
        // OTA write synchronization
        @Volatile var otaWriteComplete: Boolean = false
        val otaWriteLock = Object()
        
        fun handleOtaWriteComplete(success: Boolean) {
            synchronized(otaWriteLock) {
                otaWriteComplete = success
                (otaWriteLock as Object).notifyAll()
            }
        }
    }

    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvLatestVersion: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressDownload: ProgressBar
    private lateinit var progressUpload: ProgressBar
    private lateinit var tvDownloadProgress: TextView
    private lateinit var tvUploadProgress: TextView
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnStartOta: Button
    private lateinit var btnBack: ImageButton
    
    private var otaDownloader: OtaDownloader? = null
    private var firmwareData: ByteArray? = null
    private var firmwareInfo: OtaDownloader.FirmwareInfo? = null
    private var currentVersion = "Unknown"
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var isOtaInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ota)
        
        currentVersion = intent.getStringExtra("firmware_version") ?: "Unknown"
        
        initViews()
        setupListeners()
        
        otaDownloader = OtaDownloader(this)
        tvCurrentVersion.text = "Current: $currentVersion"
        
        // Check if we have a shared connection
        if (sharedGatt != null && sharedCmdChar != null && sharedIsConnected) {
            tvStatus.text = "Ready - Check for updates"
        } else {
            tvStatus.text = "Not connected to device"
            btnCheckUpdate.isEnabled = false
        }
    }
    
    private fun initViews() {
        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        tvLatestVersion = findViewById(R.id.tvLatestVersion)
        tvStatus = findViewById(R.id.tvStatus)
        progressDownload = findViewById(R.id.progressDownload)
        progressUpload = findViewById(R.id.progressUpload)
        tvDownloadProgress = findViewById(R.id.tvDownloadProgress)
        tvUploadProgress = findViewById(R.id.tvUploadProgress)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnStartOta = findViewById(R.id.btnStartOta)
        btnBack = findViewById(R.id.btnBack)
        
        btnStartOta.isEnabled = false
    }
    
    private fun setupListeners() {
        btnBack.setOnClickListener { 
            if (isOtaInProgress) {
                Toast.makeText(this, "Cannot exit during OTA update", Toast.LENGTH_SHORT).show()
            } else {
                finish() 
            }
        }
        
        btnCheckUpdate.setOnClickListener {
            checkForUpdates()
        }
        
        btnStartOta.setOnClickListener {
            startOtaUpdate()
        }
    }
    
    private fun checkForUpdates() {
        tvStatus.text = "Checking for updates..."
        btnCheckUpdate.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    otaDownloader?.checkForUpdates(currentVersion)
                }
                
                result?.onSuccess { info ->
                    if (info != null) {
                        firmwareInfo = info
                        tvLatestVersion.text = "Available: ${info.version}"
                        tvStatus.text = "Update available!"
                        btnStartOta.isEnabled = true
                    } else {
                        tvLatestVersion.text = "Available: $currentVersion"
                        tvStatus.text = "You have the latest version"
                    }
                }?.onFailure { error ->
                    tvStatus.text = "Error: ${error.message}"
                }
                
            } catch (e: Exception) {
                tvStatus.text = "Error checking updates: ${e.message}"
            }
            btnCheckUpdate.isEnabled = true
        }
    }
    
    private fun startOtaUpdate() {
        val info = firmwareInfo ?: return
        isOtaInProgress = true
        btnStartOta.isEnabled = false
        btnCheckUpdate.isEnabled = false
        
        tvStatus.text = "Downloading firmware..."
        progressDownload.visibility = View.VISIBLE
        tvDownloadProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    otaDownloader?.downloadFirmware(info, object : OtaDownloader.DownloadProgressListener {
                        override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percentage: Int) {
                            handler.post {
                                progressDownload.progress = percentage
                                tvDownloadProgress.text = "$percentage%"
                            }
                        }
                        
                        override fun onComplete(encryptedFile: File) {
                            handler.post {
                                tvStatus.text = "Download complete, decrypting..."
                            }
                        }
                        
                        override fun onError(error: String) {
                            handler.post {
                                tvStatus.text = "Download error: $error"
                            }
                        }
                    })
                }
                
                result?.onSuccess { encryptedFile ->
                    // Decrypt the firmware
                    tvStatus.text = "Decrypting firmware..."
                    val decryptResult = withContext(Dispatchers.IO) {
                        otaDownloader?.decryptFirmwareFile(encryptedFile)
                    }
                    
                    decryptResult?.onSuccess { decryptedData ->
                        firmwareData = decryptedData
                        tvStatus.text = "Ready to upload firmware"
                        startFirmwareUpload()
                    }?.onFailure { error ->
                        tvStatus.text = "Decryption error: ${error.message}"
                        isOtaInProgress = false
                        btnCheckUpdate.isEnabled = true
                    }
                }?.onFailure { error ->
                    tvStatus.text = "Download error: ${error.message}"
                    isOtaInProgress = false
                    btnCheckUpdate.isEnabled = true
                }
                
            } catch (e: Exception) {
                tvStatus.text = "Error: ${e.message}"
                isOtaInProgress = false
                btnCheckUpdate.isEnabled = true
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun startFirmwareUpload() {
        val firmware = firmwareData ?: return
        val gatt = sharedGatt
        val localCmdChar = sharedCmdChar
        
        if (gatt == null || localCmdChar == null || !sharedIsConnected) {
            tvStatus.text = "Not connected to device"
            isOtaInProgress = false
            btnCheckUpdate.isEnabled = true
            return
        }
        
        tvStatus.text = "Uploading firmware..."
        progressUpload.visibility = View.VISIBLE
        tvUploadProgress.visibility = View.VISIBLE
        progressUpload.progress = 0
        
        // Run OTA on background thread (same as old MainActivity)
        Thread {
            try {
                val fileSize = firmware.size
                
                handler.post {
                    tvStatus.text = "Starting OTA (${fileSize / 1024} KB)..."
                }
                
                // Send OTA BEGIN command using unified protocol
                val beginCmd = BleUnifiedProtocol.buildOtaBegin(fileSize)
                writeCharSimple(gatt, localCmdChar, beginCmd)
                Thread.sleep(300)
                
                if (!sharedIsConnected) {
                    throw Exception("Disconnected during OTA")
                }
                
                // Stream firmware with BATCHED ACK for speed + reliability
                val maxPayload = (sharedMtu - 3 - 3).coerceIn(20, 508) // -3 for BLE, -3 for cmd header
                val ackEveryN = 8  // ACK every 8th packet
                val delayMs = 2L   // 2ms between no-response packets
                var chunkCount = 0
                var offset = 0
                var lastUiUpdate = 0L
                
                while (offset < firmware.size) {
                    if (!sharedIsConnected) {
                        throw Exception("Disconnected during OTA")
                    }
                    
                    val remaining = firmware.size - offset
                    val chunkSize = minOf(remaining, maxPayload)
                    val chunk = firmware.copyOfRange(offset, offset + chunkSize)
                    chunkCount++
                    
                    // Build OTA_DATA command
                    val dataCmd = BleUnifiedProtocol.buildOtaData(chunkCount and 0xFF, chunk)
                    
                    // ACK every Nth packet to force sync and ensure ordering
                    val useAck = (chunkCount % ackEveryN == 0)
                    
                    var writeSuccess = false
                    var retryCount = 0
                    val maxWriteRetries = 10
                    
                    while (!writeSuccess && retryCount < maxWriteRetries) {
                        writeSuccess = if (useAck) {
                            writeOtaDataWithAck(gatt, localCmdChar, dataCmd)
                        } else {
                            writeOtaDataNoResponse(gatt, localCmdChar, dataCmd)
                        }
                        if (!writeSuccess) {
                            retryCount++
                            val waitTime = if (retryCount < 3) 10L else 30L
                            Thread.sleep(waitTime)
                        }
                    }
                    
                    if (!writeSuccess) {
                        throw Exception("Write failed after $maxWriteRetries retries")
                    }
                    
                    // Small delay for no-response packets
                    if (!useAck) {
                        Thread.sleep(delayMs)
                    }
                    
                    offset += chunkSize
                    
                    // UI update every ~8KB
                    if (offset - lastUiUpdate >= 8192 || offset >= firmware.size) {
                        lastUiUpdate = offset.toLong()
                        val percent = ((offset * 100) / firmware.size).coerceIn(0, 100)
                        handler.post {
                            progressUpload.progress = percent
                            tvUploadProgress.text = "$percent%"
                            tvStatus.text = "Sending: ${offset / 1024} / ${firmware.size / 1024} KB ($percent%)"
                        }
                    }
                }
                
                // Send OTA END command
                val endCmd = BleUnifiedProtocol.buildOtaEnd()
                writeCharSimple(gatt, localCmdChar, endCmd)
                
                handler.post {
                    tvStatus.text = "Waiting for device..."
                }
                Thread.sleep(1000)
                
                handler.post {
                    tvStatus.text = "Update complete! Device will restart."
                    isOtaInProgress = false
                }
                
            } catch (e: Exception) {
                handler.post {
                    tvStatus.text = "OTA Error: ${e.message}"
                    isOtaInProgress = false
                    btnCheckUpdate.isEnabled = true
                }
            }
        }.start()
    }
    
    @SuppressLint("MissingPermission")
    private fun writeCharSimple(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, data: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun writeOtaDataWithAck(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        synchronized(otaWriteLock) {
            otaWriteComplete = false
        }
        
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            char.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
        
        if (!success) return false
        
        // Wait for ACK
        val startTime = System.currentTimeMillis()
        synchronized(otaWriteLock) {
            while (!otaWriteComplete && sharedIsConnected) {
                val remaining = 5000L - (System.currentTimeMillis() - startTime)
                if (remaining <= 0) return false
                try {
                    (otaWriteLock as Object).wait(remaining.coerceAtMost(100))
                } catch (e: InterruptedException) {
                    return false
                }
            }
        }
        return otaWriteComplete
    }
    
    @SuppressLint("MissingPermission")
    private fun writeOtaDataNoResponse(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            char.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        otaDownloader?.cleanup()
        // Don't close shared connection!
    }
}
