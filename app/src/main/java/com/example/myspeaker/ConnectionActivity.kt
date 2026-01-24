package com.example.myspeaker

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * BDK Audio Connection Screen
 * 
 * Premium pairing experience with:
 * - Animated scanning visualization
 * - Auto-connect to saved devices
 * - Device list for multiple speakers
 * - Timeout handling with troubleshooting tips
 */
class ConnectionActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "BDKAudioPrefs"
        private const val KEY_SAVED_DEVICE_ADDRESS = "saved_device_address"
        private const val KEY_SAVED_DEVICE_NAME = "saved_device_name"
        private const val SCAN_TIMEOUT_MS = 15000L  // 15 seconds timeout
        private const val AUTO_CONNECT_DELAY_MS = 4000L  // Wait 4 seconds before auto-connecting to show animation
    }

    // UI Elements
    private lateinit var pulseCircle1: View
    private lateinit var pulseCircle2: View
    private lateinit var pulseCircle3: View
    private lateinit var ivBluetoothIcon: ImageView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvStatusDetail: TextView
    private lateinit var progressScanning: ProgressBar
    private lateinit var cardDeviceList: View
    private lateinit var rvDevices: RecyclerView
    private lateinit var btnRetry: Button
    private lateinit var tvManualConnect: TextView
    private lateinit var overlayTimeout: View
    private lateinit var btnOverlayRetry: Button
    private lateinit var btnOverlayManual: Button

    // Bluetooth
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }
    
    // State
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val foundDevices = mutableListOf<ScanResult>()
    private var deviceAdapter: ConnectionDeviceAdapter? = null
    private lateinit var prefs: SharedPreferences
    private var pulseAnimatorSet: AnimatorSet? = null

    // Service UUID for BDK devices
    private val serviceUuidControl = ParcelUuid.fromString("12345678-1234-1234-1234-1234567890ad")

    // Permission request
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startScanning()
        } else {
            showTimeoutOverlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initViews()
        setupListeners()
        setupDeviceList()
        
        // Start pulse animation
        startPulseAnimation()
        
        // Check permissions and start scanning
        if (hasBluetoothPermissions()) {
            startScanning()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun initViews() {
        pulseCircle1 = findViewById(R.id.pulseCircle1)
        pulseCircle2 = findViewById(R.id.pulseCircle2)
        pulseCircle3 = findViewById(R.id.pulseCircle3)
        ivBluetoothIcon = findViewById(R.id.ivBluetoothIcon)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvStatusDetail = findViewById(R.id.tvStatusDetail)
        progressScanning = findViewById(R.id.progressScanning)
        cardDeviceList = findViewById(R.id.cardDeviceList)
        rvDevices = findViewById(R.id.rvDevices)
        btnRetry = findViewById(R.id.btnRetry)
        tvManualConnect = findViewById(R.id.tvManualConnect)
        overlayTimeout = findViewById(R.id.overlayTimeout)
        btnOverlayRetry = findViewById(R.id.btnOverlayRetry)
        btnOverlayManual = findViewById(R.id.btnOverlayManual)
    }

    private fun setupListeners() {
        btnRetry.setOnClickListener { restartScanning() }
        tvManualConnect.setOnClickListener { showAllDevices() }
        btnOverlayRetry.setOnClickListener { 
            hideTimeoutOverlay()
            restartScanning()
        }
        btnOverlayManual.setOnClickListener {
            hideTimeoutOverlay()
            showAllDevices()
        }
    }

    private fun setupDeviceList() {
        deviceAdapter = ConnectionDeviceAdapter { device ->
            connectToDevice(device)
        }
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = deviceAdapter
    }

    private fun startPulseAnimation() {
        val duration = 1800L  // Faster breathing cycle
        
        // Set initial states to match animation start values to prevent jerking
        pulseCircle1.scaleX = 1.0f
        pulseCircle1.scaleY = 1.0f
        pulseCircle1.alpha = 0.6f
        
        pulseCircle2.scaleX = 1.0f
        pulseCircle2.scaleY = 1.0f
        pulseCircle2.alpha = 0.7f
        
        pulseCircle3.scaleX = 1.0f
        pulseCircle3.scaleY = 1.0f
        pulseCircle3.alpha = 0.85f
        
        val pulse1 = createPulseAnimator(pulseCircle1, 1.0f, 1.2f, duration, 0L, 0.5f, 0.8f)
        val pulse2 = createPulseAnimator(pulseCircle2, 1.0f, 1.15f, duration, 0L, 0.6f, 0.9f)
        val pulse3 = createPulseAnimator(pulseCircle3, 1.0f, 1.1f, duration, 0L, 0.75f, 1.0f)
        
        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(pulse1, pulse2, pulse3)
            start()
        }
    }

    private fun createPulseAnimator(view: View, startScale: Float, endScale: Float, duration: Long, delay: Long, alphaMin: Float, alphaMax: Float): AnimatorSet {
        // Use LinearInterpolator for smoother continuous animation
        val smoothInterpolator = android.view.animation.LinearInterpolator()
        
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", startScale, endScale).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = smoothInterpolator
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", startScale, endScale).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = smoothInterpolator
        }
        val alpha = ObjectAnimator.ofFloat(view, "alpha", alphaMin, alphaMax).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = smoothInterpolator
        }
        
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimatorSet?.cancel()
    }

    // Bluetooth permissions
    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun hasBluetoothPermissions(): Boolean {
        return blePermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        permissionLauncher.launch(blePermissions)
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (isScanning) return
        
        foundDevices.clear()
        deviceAdapter?.updateDevices(emptyList())
        
        updateStatus("Searching for devices...", "Make sure your speaker is powered on")
        progressScanning.visibility = View.VISIBLE
        cardDeviceList.visibility = View.GONE
        btnRetry.visibility = View.GONE
        tvManualConnect.visibility = View.GONE

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        bleScanner?.startScan(null, settings, scanCallback)

        // Set timeout
        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (!isScanning) return
        isScanning = false
        handler.removeCallbacks(scanTimeoutRunnable)
        bleScanner?.stopScan(scanCallback)
    }

    private fun restartScanning() {
        stopScanning()
        startScanning()
    }

    private val scanTimeoutRunnable = Runnable {
        stopScanning()
        
        if (foundDevices.isEmpty()) {
            showTimeoutOverlay()
        } else if (foundDevices.size == 1) {
            // Auto-connect to the only device found
            connectToDevice(foundDevices[0])
        } else {
            // Show device list for selection
            showDeviceList()
        }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: return
            
            // Only show BDK devices (filter by name prefix or service UUID)
            if (!deviceName.startsWith("BDK") && !deviceName.contains("Speaker", ignoreCase = true)) {
                // Check if it has our service UUID
                val hasService = result.scanRecord?.serviceUuids?.contains(serviceUuidControl) == true
                if (!hasService) return
            }

            // Check if already in list
            val existing = foundDevices.find { it.device.address == result.device.address }
            if (existing == null) {
                foundDevices.add(result)
                runOnUiThread { onDeviceFound(result) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                updateStatus("Scan failed", "Error code: $errorCode")
                progressScanning.visibility = View.GONE
                btnRetry.visibility = View.VISIBLE
            }
        }
    }

    // Auto-connect runnable for single device
    private var autoConnectRunnable: Runnable? = null
    private var autoConnectDevice: ScanResult? = null

    @SuppressLint("MissingPermission")
    private fun onDeviceFound(result: ScanResult) {
        val savedAddress = prefs.getString(KEY_SAVED_DEVICE_ADDRESS, null)
        
        // Cancel any pending auto-connect when we find more devices
        if (foundDevices.size > 1 && autoConnectRunnable != null) {
            handler.removeCallbacks(autoConnectRunnable!!)
            autoConnectRunnable = null
            autoConnectDevice = null
        }
        
        // Check if this is the saved device - auto-connect immediately
        if (result.device.address == savedAddress) {
            updateStatus("Found ${result.device.name}", "Connecting...")
            handler.removeCallbacks(scanTimeoutRunnable)
            handler.postDelayed({ connectToDevice(result) }, AUTO_CONNECT_DELAY_MS)
            return
        }

        // For single device: show it in the list and start auto-connect countdown
        if (foundDevices.size == 1) {
            // Show the device in the list for user to tap
            showDeviceList()
            updateStatus("Found ${result.device.name}", "Auto-connecting in 2s... Tap to connect now")
            
            // Start auto-connect countdown
            handler.removeCallbacks(scanTimeoutRunnable)
            autoConnectDevice = result
            autoConnectRunnable = Runnable { 
                autoConnectDevice?.let { connectToDevice(it) }
            }
            handler.postDelayed(autoConnectRunnable!!, AUTO_CONNECT_DELAY_MS)
            return
        }

        // For multiple devices: show the list for user to choose (no auto-connect)
        updateStatus("Found ${foundDevices.size} devices", "Select a device to connect")
        showDeviceList()
    }

    private fun showDeviceList() {
        cardDeviceList.visibility = View.VISIBLE
        tvManualConnect.visibility = View.VISIBLE
        deviceAdapter?.updateDevices(foundDevices)
    }

    private fun showAllDevices() {
        // Show all found devices for manual selection
        if (foundDevices.isNotEmpty()) {
            showDeviceList()
        } else {
            restartScanning()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(result: ScanResult) {
        // Cancel any pending auto-connect
        autoConnectRunnable?.let { handler.removeCallbacks(it) }
        autoConnectRunnable = null
        autoConnectDevice = null
        
        stopScanning()
        stopPulseAnimation()
        
        val deviceName = result.device.name ?: "BDK Speaker"
        updateStatus("Connecting to $deviceName...", "Please wait")
        progressScanning.visibility = View.VISIBLE
        cardDeviceList.visibility = View.GONE

        // Save this device for next time
        prefs.edit()
            .putString(KEY_SAVED_DEVICE_ADDRESS, result.device.address)
            .putString(KEY_SAVED_DEVICE_NAME, deviceName)
            .apply()

        // Navigate to MainActivityRedesign with device info
        val intent = Intent(this, MainActivityRedesign::class.java).apply {
            putExtra("device_address", result.device.address)
            putExtra("device_name", deviceName)
            putExtra("auto_connect", true)
        }
        startActivity(intent)
        finish()
        
        // Custom transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun updateStatus(status: String, detail: String) {
        tvConnectionStatus.text = status
        tvStatusDetail.text = detail
    }

    private fun showTimeoutOverlay() {
        stopPulseAnimation()
        progressScanning.visibility = View.GONE
        overlayTimeout.visibility = View.VISIBLE
        overlayTimeout.alpha = 0f
        overlayTimeout.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideTimeoutOverlay() {
        overlayTimeout.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { 
                overlayTimeout.visibility = View.GONE 
                startPulseAnimation()
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        stopPulseAnimation()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Adapter for the device list
     */
    inner class ConnectionDeviceAdapter(
        private val onDeviceClick: (ScanResult) -> Unit
    ) : RecyclerView.Adapter<ConnectionDeviceAdapter.DeviceViewHolder>() {

        private var devices = listOf<ScanResult>()

        fun updateDevices(newDevices: List<ScanResult>) {
            devices = newDevices
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_connection_device, parent, false)
            return DeviceViewHolder(view)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            val result = devices[position]
            holder.bind(result)
        }

        override fun getItemCount() = devices.size

        inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
            private val tvDeviceInfo: TextView = itemView.findViewById(R.id.tvDeviceInfo)
            private val ivSignalStrength: ImageView = itemView.findViewById(R.id.ivSignalStrength)

            @SuppressLint("MissingPermission")
            fun bind(result: ScanResult) {
                val deviceName = result.device.name ?: "Unknown Device"
                val rssi = result.rssi
                
                tvDeviceName.text = deviceName
                tvDeviceInfo.text = getSignalQuality(rssi)
                
                // Signal strength icon color based on RSSI
                val signalColor = when {
                    rssi >= -50 -> getColor(R.color.bdk_status_connected)
                    rssi >= -70 -> getColor(R.color.bdk_accent_primary)
                    else -> getColor(R.color.bdk_status_warning)
                }
                ivSignalStrength.setColorFilter(signalColor)

                itemView.setOnClickListener { onDeviceClick(result) }
            }

            private fun getSignalQuality(rssi: Int): String {
                return when {
                    rssi >= -50 -> "Excellent signal"
                    rssi >= -60 -> "Good signal"
                    rssi >= -70 -> "Fair signal"
                    else -> "Weak signal"
                }
            }
        }
    }
}
