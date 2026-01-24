# Android App OTA Update Guide

## Summary of Changes

The Android app has been modified to support automatic OTA updates from Google Drive with decryption, matching the recovery firmware's functionality.

## New Files Created

### 1. OtaDownloader.kt
Location: `app/src/main/java/com/example/myspeaker/OtaDownloader.kt`

**Key Features:**
- Downloads encrypted firmware from Google Drive
- Checks for available updates by reading `latest.txt`
- Decrypts firmware using AES-256-CBC (same key as recovery)
- Progress tracking during download
- Automatic cleanup of downloaded files

**Important Constants to Update:**
```kotlin
// Update this with your Google Drive file ID for latest.txt
private const val GDRIVE_LATEST_TXT_ID = "YOUR_LATEST_TXT_FILE_ID"

// AES Key - MUST match recovery_main.cpp and encrypt_firmware.py
private val AES_KEY = byteArrayOf(...)  // Already set to match
```

### 2. NetworkHelper.kt  
Location: `app/src/main/java/com/example/myspeaker/NetworkHelper.kt`

**Key Features:**
- Checks WiFi and mobile data connectivity
- Prompts user to enable WiFi if not connected
- Warns about mobile data usage
- Opens WiFi settings when requested

## Required Changes to MainActivity.kt

### 1. Add Imports

Add these imports at the top of MainActivity.kt:

```kotlin
import kotlinx.coroutines.*
import java.io.File
```

### 2. Add Class Properties

Add these properties to the MainActivity class (around line 130):

```kotlin
// OTA Downloader
private var otaDownloader: OtaDownloader? = null
private var otaDownloadJob: Job? = null
private var currentFirmwareFile: File? = null

// Check for updates button
private lateinit var btnCheckUpdates: Button
```

### 3. Modify onCreate() - Add Button Initialization

Replace the existing button setup (around line 460-500) with:

```kotlin
btnSelectFw = findViewById(R.id.btnSelectFirmware)
btnStartOta = findViewById(R.id.btnStartOta)
btnCheckUpdates = findViewById(R.id.btnCheckUpdates)  // NEW
progressOta = findViewById(R.id.progressOta)
tvOtaStatus = findViewById(R.id.tvOtaStatus)

// Initialize OTA downloader
otaDownloader = OtaDownloader(this)

// NEW: Check for updates button
btnCheckUpdates.setOnClickListener {
    checkForOtaUpdates()
}

// Keep existing file picker (as fallback)
btnSelectFw.setOnClickListener {
    pickFirmwareLauncher.launch("*/*")
}

btnStartOta.setOnClickListener {
    // Check if we have a downloaded firmware file
    val downloadedFile = currentFirmwareFile
    if (downloadedFile != null && downloadedFile.exists()) {
        startOtaFromDownload(downloadedFile)
    } else {
        // Fallback to file picker
        val uri = firmwareUri
        if (uri == null) {
            tvOtaStatus.visibility = View.VISIBLE
            tvOtaStatus.text = "Select firmware first"
        } else {
            startOta(uri)
        }
    }
}
```

### 4. Add New OTA Methods

Add these methods to MainActivity.kt (after the existing startOta method, around line 3100):

```kotlin
/**
 * Check for OTA updates from Google Drive
 */
private fun checkForOtaUpdates() {
    // Check WiFi first
    if (!NetworkHelper.isWifiConnected(this)) {
        NetworkHelper.promptEnableWifi(this) {
            // Retry after user interaction
            checkForOtaUpdates()
        }
        return
    }
    
    runOnUiThread {
        progressOta.visibility = View.VISIBLE
        progressOta.progress = 0
        tvOtaStatus.visibility = View.VISIBLE
        tvOtaStatus.text = "Checking for updates..."
        tvOtaStatus.setTextColor(Color.parseColor("#A1A1AA"))
        btnCheckUpdates.isEnabled = false
    }
    
    otaDownloadJob = CoroutineScope(Dispatchers.Main).launch {
        try {
            val result = otaDownloader?.checkForUpdates(currentFirmwareVersion)
            
            result?.onSuccess { firmwareInfo ->
                if (firmwareInfo != null) {
                    // Update available
                    runOnUiThread {
                        tvOtaStatus.text = "Update available: ${firmwareInfo.version}"
                        tvOtaStatus.setTextColor(Color.parseColor("#10B981"))
                        
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Firmware Update Available")
                            .setMessage("New version ${firmwareInfo.version} is available.\n\nCurrent version: $currentFirmwareVersion\n\nDownload and install?")
                            .setPositiveButton("Download") { _, _ ->
                                downloadAndPrepareOta(firmwareInfo)
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                } else {
                    // No update available
                    runOnUiThread {
                        tvOtaStatus.text = "You have the latest version ($currentFirmwareVersion)"
                        tvOtaStatus.setTextColor(Color.parseColor("#10B981"))
                        
                        // Hide after 3 seconds
                        Handler(Looper.getMainLooper()).postDelayed({
                            progressOta.visibility = View.GONE
                            tvOtaStatus.visibility = View.GONE
                        }, 3000)
                    }
                }
            }?.onFailure { error ->
                runOnUiThread {
                    tvOtaStatus.text = "Error checking updates: ${error.message}"
                    tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                tvOtaStatus.text = "Error: ${e.message}"
                tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
            }
        } finally {
            runOnUiThread {
                btnCheckUpdates.isEnabled = true
            }
        }
    }
}

/**
 * Download firmware from Google Drive
 */
private fun downloadAndPrepareOta(firmwareInfo: OtaDownloader.FirmwareInfo) {
    runOnUiThread {
        progressOta.visibility = View.VISIBLE
        progressOta.progress = 0
        tvOtaStatus.visibility = View.VISIBLE
        tvOtaStatus.text = "Downloading firmware..."
        tvOtaStatus.setTextColor(Color.parseColor("#A1A1AA"))
        setControlsEnabled(false)
    }
    
    otaDownloadJob = CoroutineScope(Dispatchers.Main).launch {
        try {
            val result = otaDownloader?.downloadFirmware(firmwareInfo, object : OtaDownloader.DownloadProgressListener {
                override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percentage: Int) {
                    runOnUiThread {
                        progressOta.progress = percentage
                        tvOtaStatus.text = "Downloading: ${bytesDownloaded / 1024} / ${totalBytes / 1024} KB ($percentage%)"
                    }
                }
                
                override fun onComplete(encryptedFile: File) {
                    runOnUiThread {
                        tvOtaStatus.text = "Download complete. Decrypting..."
                    }
                }
                
                override fun onError(error: String) {
                    runOnUiThread {
                        tvOtaStatus.text = "Download error: $error"
                        tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                        setControlsEnabled(true)
                    }
                }
            })
            
            result?.onSuccess { encryptedFile ->
                // Decrypt firmware
                tvOtaStatus.text = "Decrypting firmware..."
                
                val decryptResult = otaDownloader?.decryptFirmwareFile(encryptedFile)
                decryptResult?.onSuccess { decryptedData ->
                    // Save decrypted firmware to temp file
                    val decryptedFile = File(cacheDir, "firmware_${firmwareInfo.version}.bin")
                    decryptedFile.writeBytes(decryptedData)
                    
                    currentFirmwareFile = decryptedFile
                    
                    runOnUiThread {
                        tvOtaStatus.text = "Ready to flash. Click 'Start OTA' to begin."
                        tvOtaStatus.setTextColor(Color.parseColor("#10B981"))
                        btnStartOta.isEnabled = true
                        setControlsEnabled(true)
                        
                        // Clean up encrypted file
                        encryptedFile.delete()
                    }
                }?.onFailure { error ->
                    runOnUiThread {
                        tvOtaStatus.text = "Decryption error: ${error.message}"
                        tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                        setControlsEnabled(true)
                        encryptedFile.delete()
                    }
                }
            }?.onFailure { error ->
                runOnUiThread {
                    tvOtaStatus.text = "Download failed: ${error.message}"
                    tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                    setControlsEnabled(true)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                tvOtaStatus.text = "Error: ${e.message}"
                tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                setControlsEnabled(true)
            }
        }
    }
}

/**
 * Start OTA from downloaded decrypted firmware file
 */
private fun startOtaFromDownload(firmwareFile: File) {
    val gatt = bluetoothGatt
    val ctrlChar = otaCtrlChar
    val dataChar = otaDataChar

    if (!isConnected || gatt == null || ctrlChar == null || dataChar == null) {
        runOnUiThread {
            tvOtaStatus.visibility = View.VISIBLE
            tvOtaStatus.text = "Not connected"
            tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
        }
        return
    }

    if (isOtaInProgress) {
        runOnUiThread {
            tvOtaStatus.text = "OTA already in progress"
        }
        return
    }

    val gattNN = gatt
    val otaCtrlNN = ctrlChar
    val otaDataNN = dataChar

    espOtaBytes = 0
    currentFwSize = 0
    otaCancelled = false
    isOtaInProgress = true

    runOnUiThread {
        progressOta.visibility = View.VISIBLE
        progressOta.progress = 0
        tvOtaStatus.visibility = View.VISIBLE
        tvOtaStatus.text = "Preparing OTA..."
        tvOtaStatus.setTextColor(Color.parseColor("#A1A1AA"))
        setControlsEnabled(false)
    }

    Thread {
        try {
            val firmwareData = firmwareFile.readBytes()
            val fileSize = firmwareData.size.toLong()
            currentFwSize = fileSize

            runOnUiThread {
                tvOtaStatus.text = "Starting OTA (${fileSize / 1024} KB)..."
            }

            // Send BEGIN command
            val begin = "BEGIN:$fileSize".toByteArray(Charsets.UTF_8)
            writeCharSimple(gattNN, otaCtrlNN, begin)
            Thread.sleep(300)

            if (otaCancelled || !isConnected) {
                throw Exception("OTA cancelled - disconnected")
            }

            // Stream firmware data
            val maxPayload = (currentMtu - 3 - 2).coerceIn(20, 510)
            var offset = 0
            var lastUiUpdate = 0L
            val ackEveryN = 8
            val delayMs = 2L
            var chunkCount = 0
            
            while (offset < firmwareData.size) {
                if (otaCancelled || !isConnected) {
                    throw Exception("OTA cancelled - disconnected")
                }

                val remaining = firmwareData.size - offset
                val chunkSize = minOf(remaining, maxPayload)
                val chunk = firmwareData.copyOfRange(offset, offset + chunkSize)
                chunkCount++
                
                val useAck = (chunkCount % ackEveryN == 0)
                
                var writeSuccess = false
                var retryCount = 0
                val maxWriteRetries = 10
                while (!writeSuccess && retryCount < maxWriteRetries) {
                    writeSuccess = if (useAck) {
                        writeOtaDataWithAck(gattNN, otaDataNN, chunk)
                    } else {
                        writeOtaDataNoResponse(gattNN, otaDataNN, chunk)
                    }
                    if (!writeSuccess) {
                        retryCount++
                        val waitTime = if (retryCount < 3) 10L else 30L
                        Thread.sleep(waitTime)
                    }
                }
                
                if (!writeSuccess) {
                    throw Exception("OTA write failed after $maxWriteRetries retries")
                }
                
                if (!useAck) {
                    Thread.sleep(delayMs)
                }

                offset += chunkSize

                // UI update every ~8KB
                if (offset - lastUiUpdate >= 8192 || offset == firmwareData.size) {
                    lastUiUpdate = offset.toLong()
                    val percent = ((offset * 100) / fileSize).toInt().coerceIn(0, 100)
                    runOnUiThread {
                        progressOta.progress = percent
                        tvOtaStatus.text = "Sending: ${offset / 1024} / ${fileSize / 1024} KB ($percent%)"
                    }
                }
            }
            
            // Flush final packets
            val flushChunk = ByteArray(1) { 0 }
            writeOtaDataWithAck(gattNN, otaDataNN, flushChunk)
            
            // Wait for ESP32 to process
            Thread.sleep(500)

            // Send END command
            val end = "END".toByteArray(Charsets.UTF_8)
            writeCharSimple(gattNN, otaCtrlNN, end)

            runOnUiThread {
                progressOta.progress = 100
                tvOtaStatus.text = "OTA complete! Device will reboot..."
                tvOtaStatus.setTextColor(Color.parseColor("#10B981"))
                
                // Clean up firmware file
                firmwareFile.delete()
                currentFirmwareFile = null
                otaDownloader?.cleanup()
                
                // Re-enable controls after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    isOtaInProgress = false
                    setControlsEnabled(true)
                }, 3000)
            }
        } catch (e: Exception) {
            runOnUiThread {
                tvOtaStatus.text = "OTA failed: ${e.message}"
                tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                isOtaInProgress = false
                setControlsEnabled(true)
                
                // Clean up on error
                firmwareFile.delete()
                currentFirmwareFile = null
            }
        }
    }.start()
}
```

### 5. Clean Up on Activity Destroy

Add cleanup in onDestroy() method:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // Cancel any ongoing download
    otaDownloadJob?.cancel()
    
    // Clean up downloaded files
    otaDownloader?.cleanup()
    currentFirmwareFile?.delete()
    
    // Existing cleanup code...
}
```

## Required Layout Changes (activity_main.xml)

Add a "Check for Updates" button before the existing OTA buttons:

```xml
<!-- Add this button in the OTA section -->
<Button
    android:id="@+id/btnCheckUpdates"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Check for Updates"
    android:backgroundTint="@color/teal_500"
    android:textColor="@android:color/white"
    android:layout_marginBottom="8dp"/>
```

## Required Permissions (AndroidManifest.xml)

Ensure these permissions are added:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

## Required Dependencies (build.gradle)

Add Kotlin coroutines if not already present:

```gradle
dependencies {
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    // ... existing dependencies
}
```

## Usage Flow

1. User clicks "Check for Updates"
2. App checks WiFi connectivity (prompts if not connected)
3. App downloads `latest.txt` from Google Drive
4. App compares versions
5. If update available, prompts user to download
6. Downloads encrypted firmware from Google Drive
7. Decrypts firmware locally using AES-256-CBC
8. Saves decrypted firmware to temp file
9. User clicks "Start OTA" to flash
10. Firmware is sent to ESP32 via BLE
11. Temp files are automatically cleaned up

## Configuration

### Update Google Drive File ID

In `OtaDownloader.kt`, update:
```kotlin
private const val GDRIVE_LATEST_TXT_ID = "YOUR_FILE_ID_HERE"
```

Get this ID from your Google Drive URL after uploading `latest.txt`:
- `https://drive.google.com/file/d/FILE_ID_HERE/view`

### Verify AES Key Match

Ensure the AES key in `OtaDownloader.kt` matches:
- `recovery/main/recovery_main.cpp` (AES_KEY)
- `tools/encrypt_firmware.py` (AES_KEY)

All three must use the same key for encryption/decryption to work!

## Testing

1. Generate test firmware:
   ```
   python tools/encrypt_firmware.py build/app-template.bin --version 1.0.1
   ```

2. Upload to Google Drive and update `latest.txt`

3. Test in app:
   - Check for updates
   - Download firmware
   - Verify decryption
   - Flash to device

## Notes

- Downloaded firmware files are stored in app cache directory
- Files are automatically deleted after successful OTA or on error
- WiFi is strongly preferred for downloads
- Mobile data warning is shown if WiFi unavailable
- All encryption uses AES-256-CBC with PKCS5 padding
