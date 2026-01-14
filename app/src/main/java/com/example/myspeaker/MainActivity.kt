package com.example.myspeaker

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    companion object {
        var lastDeviceName: String = "Unknown"
        var lastFirmwareVersion: String = "Unknown"
    }
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val foundDevices = mutableListOf<ScanResult>()

    // UUIDs must match your ESP32 sketch
    private val serviceUuidControl =
        UUID.fromString("12345678-1234-1234-1234-1234567890ad")
    private val charUuidControl =
        UUID.fromString("12345678-1234-1234-1234-1234567890ae")

    private val serviceUuidLevels =
        UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val charUuidLevels =
        UUID.fromString("12345678-1234-1234-1234-1234567890ac")

    private val charUuidEq =
        UUID.fromString("12345678-1234-1234-1234-1234567890af")
    private val charUuidName =
        UUID.fromString("12345678-1234-1234-1234-1234567890b0")

    private val charUuidFwVer =
        UUID.fromString("12345678-1234-1234-1234-1234567890b3")

    // LED Effect characteristic
    private val charUuidLedEffect =
        UUID.fromString("12345678-1234-1234-1234-1234567890b4")

    // CCCD for notifications
    private val cccdUuid =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // OTA characteristics (must match ESP32)
    private val charUuidOtaCtrl =
        UUID.fromString("12345678-1234-1234-1234-1234567890b1")
    private val charUuidOtaData =
        UUID.fromString("12345678-1234-1234-1234-1234567890b2")

    private lateinit var tvStatus: TextView
    private lateinit var tvLevels: TextView
    private lateinit var btnScanConnect: Button
    private lateinit var switchBass: MaterialSwitch
    private lateinit var switchFlip: MaterialSwitch
    private lateinit var switchBypass: MaterialSwitch

    private lateinit var seekBass: SeekBar
    private lateinit var seekMid: SeekBar
    private lateinit var seekTreble: SeekBar
    private lateinit var tvBassLabel: TextView
    private lateinit var tvMidLabel: TextView
    private lateinit var tvTrebleLabel: TextView

    private lateinit var etDeviceName: EditText
    private lateinit var btnApplyName: Button

    private lateinit var btnAppInfo: Button

    // Level bars
    private lateinit var bar30: ProgressBar
    private lateinit var bar60: ProgressBar
    private lateinit var bar100: ProgressBar
    private lateinit var tvVal30: TextView
    private lateinit var tvVal60: TextView
    private lateinit var tvVal100: TextView

    private lateinit var btnSelectFw: Button
    private lateinit var btnStartOta: Button

    // OTA Progress UI
    private lateinit var progressOta: ProgressBar
    private lateinit var tvOtaStatus: TextView

    // LED Effect Spinner
    private lateinit var spinnerLedEffect: Spinner

    // LED effect names matching ESP32 enum order (from led_config.h LedEffectId)
    private val ledEffectNames = arrayOf(
        "Spectrum Bars",      // 0 - LED_EFFECT_SPECTRUM_BARS
        "Beat Pulse",         // 1 - LED_EFFECT_BEAT_PULSE
        "Ripple",             // 2 - LED_EFFECT_RIPPLE
        "Fire",               // 3 - LED_EFFECT_FIRE
        "Plasma",             // 4 - LED_EFFECT_PLASMA
        "Matrix Rain",        // 5 - LED_EFFECT_RAIN
        "VU Meter",           // 6 - LED_EFFECT_VU_METER
        "Starfield",          // 7 - LED_EFFECT_STARFIELD
        "Wave",               // 8 - LED_EFFECT_WAVE
        "Fireworks",          // 9 - LED_EFFECT_FIREWORKS
        "Rainbow Wave",       // 10 - LED_EFFECT_RAINBOW_WAVE
        "Particle Burst",     // 11 - LED_EFFECT_PARTICLE_BURST
        "Kaleidoscope",       // 12 - LED_EFFECT_KALEIDOSCOPE
        "Frequency Spiral",   // 13 - LED_EFFECT_FREQUENCY_SPIRAL
        "Bass Reactor",       // 14 - LED_EFFECT_BASS_REACTOR
        "Meteor Shower",      // 15 - LED_EFFECT_METEOR_SHOWER
        "Breathing",          // 16 - LED_EFFECT_BREATHING
        "DNA Helix",          // 17 - LED_EFFECT_DNA_HELIX
        "Audio Scope",        // 18 - LED_EFFECT_AUDIO_SCOPE
        "Bouncing Balls",     // 19 - LED_EFFECT_BOUNCING_BALLS
        "Lava Lamp"           // 20 - LED_EFFECT_LAVA_LAMP
    )

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var levelsChar: BluetoothGattCharacteristic? = null
    private var controlChar: BluetoothGattCharacteristic? = null
    private var eqChar: BluetoothGattCharacteristic? = null
    private var nameChar: BluetoothGattCharacteristic? = null
    private var ledEffectChar: BluetoothGattCharacteristic? = null

    private var fwVerChar: BluetoothGattCharacteristic? = null

    private var currentDeviceNameStr: String = "Unknown"

    var currentFirmwareVersion: String = "Unknown"

    private var isConnected = false

    // Guard to avoid feedback loops when we update switches from BLE
    private var updatingFromDevice = false

    private var otaCtrlChar: BluetoothGattCharacteristic? = null
    private var otaDataChar: BluetoothGattCharacteristic? = null

    private var firmwareUri: Uri? = null

    // Current negotiated MTU (default 23)
    private var currentMtu: Int = 23

    // Current firmware size + progress reported by ESP32
    @Volatile private var currentFwSize: Long = 0
    @Volatile private var espOtaBytes: Long = 0

    // OTA state tracking
    @Volatile private var isOtaInProgress: Boolean = false
    @Volatile private var otaCancelled: Boolean = false

    // permissions
    private val blePermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* ignore result */ }

    private val pickFirmwareLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                firmwareUri = uri
                runOnUiThread {
                    tvOtaStatus.visibility = View.VISIBLE
                    tvOtaStatus.text = "Firmware selected"
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSelectFw = findViewById(R.id.btnSelectFirmware)
        btnStartOta = findViewById(R.id.btnStartOta)
        progressOta = findViewById(R.id.progressOta)
        tvOtaStatus = findViewById(R.id.tvOtaStatus)

        btnSelectFw.setOnClickListener {
            pickFirmwareLauncher.launch("*/*")
        }
        btnAppInfo = findViewById(R.id.btnAppInfo)
        btnAppInfo.setOnClickListener {
            startActivity(android.content.Intent(this, AppInfoActivity::class.java))
        }
        val btnDeviceInfo = findViewById<Button>(R.id.btnDeviceInfo)
        btnDeviceInfo.setOnClickListener {
            val intent = Intent(this, DeviceInfoActivity::class.java)
            intent.putExtra("device_name", if (isConnected) currentDeviceNameStr else "Unknown")
            intent.putExtra("fw_version", if (isConnected) currentFirmwareVersion else "Unknown")
            intent.putExtra("is_connected", isConnected)
            startActivity(intent)
        }
        btnStartOta.setOnClickListener {
            val uri = firmwareUri
            if (uri == null) {
                tvOtaStatus.visibility = View.VISIBLE
                tvOtaStatus.text = "Select firmware first"
            } else {
                startOta(uri)
            }
        }

        // Insets so UI is not under status / nav bars / keyboard
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootScroll)) { view, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = sysBars.left + 16,
                right = sysBars.right + 16,
                top = sysBars.top + 16,
                bottom = maxOf(sysBars.bottom, ime.bottom) + 16
            )
            WindowInsetsCompat.CONSUMED
        }

        tvStatus = findViewById(R.id.tvStatus)
        tvLevels = findViewById(R.id.tvLevels)
        btnScanConnect = findViewById(R.id.btnScanConnect)
        switchBass = findViewById(R.id.switchBassBoost)
        switchFlip = findViewById(R.id.switchChannelFlip)
        switchBypass = findViewById(R.id.switchBypass)

        seekBass = findViewById(R.id.seekBass)
        seekMid = findViewById(R.id.seekMid)
        seekTreble = findViewById(R.id.seekTreble)
        tvBassLabel = findViewById(R.id.tvBassLabel)
        tvMidLabel = findViewById(R.id.tvMidLabel)
        tvTrebleLabel = findViewById(R.id.tvTrebleLabel)

        etDeviceName = findViewById(R.id.etDeviceName)
        btnApplyName = findViewById(R.id.btnApplyName)

        bar30 = findViewById(R.id.bar30)
        bar60 = findViewById(R.id.bar60)
        bar100 = findViewById(R.id.bar100)
        tvVal30 = findViewById(R.id.tvVal30)
        tvVal60 = findViewById(R.id.tvVal60)
        tvVal100 = findViewById(R.id.tvVal100)

        // LED Effect Spinner
        spinnerLedEffect = findViewById(R.id.spinnerLedEffect)
        setupLedEffectSpinner()

        // Device list views
        deviceListContainer = findViewById(R.id.deviceListContainer)
        deviceRecyclerView = findViewById(R.id.deviceRecyclerView)

        // Button for other device
        val btnOtherDevice = findViewById<Button>(R.id.btnOtherDevice)
        btnOtherDevice.setOnClickListener {
            disconnectGatt()
            startScan()
        }

        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter

        if (bluetoothAdapter == null) {
            tvStatus.text = "Bluetooth not supported"
            btnScanConnect.isEnabled = false
            return
        }

        ensurePermissions()

        btnScanConnect.setOnClickListener {
            if (!isConnected) startScan() else disconnectGatt()
        }

        switchBass.setOnCheckedChangeListener { _, _ ->
            if (!updatingFromDevice) sendControlByte()
        }
        switchFlip.setOnCheckedChangeListener { _, _ ->
            if (!updatingFromDevice) sendControlByte()
        }
        switchBypass.setOnCheckedChangeListener { _, _ ->
            if (!updatingFromDevice) sendControlByte()
        }

        setupEqSeekBar(seekBass, tvBassLabel) { sendEq() }
        setupEqSeekBar(seekMid, tvMidLabel) { sendEq() }
        setupEqSeekBar(seekTreble, tvTrebleLabel) { sendEq() }

        btnApplyName.setOnClickListener { sendDeviceName() }

        // Scroll to show Apply Name button when device name field is focused
        // Scroll to show Apply Name button when device name field is focused
        // Scroll to show Apply Name button when device name field is clicked
        etDeviceName.setOnClickListener {
            etDeviceName.postDelayed({
                val scrollView = findViewById<android.widget.ScrollView>(R.id.rootScroll)
                val card = (etDeviceName.parent as? View)?.parent as? View
                if (card != null) {
                    scrollView.smoothScrollTo(0, card.bottom)
                }
            }, 400)
        }
    }

    private fun setupLedEffectSpinner() {
        // Custom adapter with dark theme styling
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            ledEffectNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 15f
                view.setPadding(16, 12, 16, 12)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(Color.parseColor("#1E1E1E"))
                view.textSize = 15f
                view.setPadding(24, 16, 24, 16)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLedEffect.adapter = adapter

        spinnerLedEffect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updatingFromDevice) {
                    sendLedEffect(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun ensurePermissions() {
        val missing = blePermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    // ========= EQ Seekbar helpers =========

    private fun setupEqSeekBar(
        seekBar: SeekBar,
        label: TextView,
        onStop: () -> Unit
    ) {
        val db = seekBar.progress - 12
        label.text = "$db dB"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress - 12
                label.text = "$value dB"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { onStop() }
        })
    }

    // ========= SCANNING =========

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device ?: return
            val record = result.scanRecord

            var isCandidate = false
            val wanted = ParcelUuid(serviceUuidControl)
            val uuids = record?.serviceUuids
            if (uuids != null && uuids.any { it == wanted }) {
                isCandidate = true
            }
            if (!isCandidate) return

            val idx = foundDevices.indexOfFirst { it.device.address == device.address }
            if (idx == -1) foundDevices.add(result) else foundDevices[idx] = result
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning = false
            btnScanConnect.text = "Connect"
            tvStatus.text = "Scan failed ($errorCode)"
        }
    }

    private fun startScan() {
        if (isScanning) return
        ensurePermissions()

        val adapter = bluetoothAdapter ?: run {
            tvStatus.text = "Bluetooth not available"
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            tvStatus.text = "BLE scanner not available"
            return
        }

        foundDevices.clear()
        tvStatus.text = "Scanning..."
        isScanning = true
        btnScanConnect.text = "Scanning..."

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)

        handler.postDelayed({
            stopScan()
            showDevicePicker()
        }, 2000)
    }

    private fun stopScan() {
        if (!isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanner.stopScan(scanCallback)
        isScanning = false
        btnScanConnect.text = if (isConnected) "Disconnect" else "Connect"
    }

    private fun showDevicePicker() {
        if (foundDevices.isEmpty()) {
            tvStatus.text = "No devices found"
            hideDeviceList()
            return
        }

        // Auto-connect if only one device found
        if (foundDevices.size == 1) {
            val device = foundDevices[0].device
            val name = device.name ?: "ESP32 Device"
            tvStatus.text = "Connecting to $name..."
            hideDeviceList()
            connectToDevice(device)
            return
        }

        // Multiple devices - show integrated list
        tvStatus.text = "${foundDevices.size} devices found"
        showDeviceList()
    }

    private lateinit var deviceListContainer: LinearLayout
    private lateinit var deviceRecyclerView: RecyclerView

    private fun showDeviceList() {
        deviceRecyclerView.layoutManager = LinearLayoutManager(this)
        deviceRecyclerView.adapter = DeviceAdapter(foundDevices) { result ->
            val device = result.device
            val name = device.name ?: "ESP32 Device"
            tvStatus.text = "Connecting to $name..."
            hideDeviceList()
            connectToDevice(device)
        }
        deviceListContainer.visibility = View.VISIBLE
    }

    private fun hideDeviceList() {
        deviceListContainer.visibility = View.GONE
    }

    // ========= GATT CONNECTION =========

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                runOnUiThread {
                    tvStatus.text = "Discovering services..."
                    btnScanConnect.text = "Disconnect"
                    hideDeviceList()
                }

                // Request large MTU for faster OTA
                if (gatt != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt.requestMtu(517)
                }

                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Cancel OTA if in progress
                if (isOtaInProgress) {
                    otaCancelled = true
                    runOnUiThread {
                        tvOtaStatus.text = "OTA cancelled - disconnected"
                        tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                        progressOta.visibility = View.GONE
                    }
                }
                isOtaInProgress = false

                bluetoothGatt = null
                levelsChar = null
                controlChar = null
                eqChar = null
                nameChar = null
                ledEffectChar = null
                otaCtrlChar = null
                otaDataChar = null
                isConnected = false
                runOnUiThread {
                    tvStatus.text = "Not Connected"
                    btnScanConnect.text = "Connect"
                    resetUiToDefaults()
                }
            }
        }

        override fun onMtuChanged(
            gatt: BluetoothGatt?,
            mtu: Int,
            status: Int
        ) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) return

            val controlService = gatt.getService(serviceUuidControl)
            val levelsService  = gatt.getService(serviceUuidLevels)

            controlChar = controlService?.getCharacteristic(charUuidControl)
            eqChar      = controlService?.getCharacteristic(charUuidEq)
            nameChar    = controlService?.getCharacteristic(charUuidName)
            ledEffectChar = controlService?.getCharacteristic(charUuidLedEffect)
            levelsChar  = levelsService?.getCharacteristic(charUuidLevels)
            otaCtrlChar = controlService?.getCharacteristic(charUuidOtaCtrl)
            otaDataChar = controlService?.getCharacteristic(charUuidOtaData)
            fwVerChar = controlService?.getCharacteristic(charUuidFwVer)

            levelsChar?.let { enableNotifications(gatt, it) }
            eqChar?.let { enableNotifications(gatt, it) }
            controlChar?.let { enableNotifications(gatt, it) }
            nameChar?.let { enableNotifications(gatt, it) }
            fwVerChar?.let { enableNotifications(gatt, it) }
            ledEffectChar?.let { enableNotifications(gatt, it) }

            // Receive PROG: notifications from ESP
            otaCtrlChar?.let { enableNotifications(gatt, it) }

            isConnected = true

            // Sequential sync: CONTROL -> EQ -> NAME -> LED EFFECT
            handler.postDelayed({
                bluetoothGatt?.let { current ->
                    if (current == gatt) controlChar?.let { readCharacteristicCompat(current, it) }
                }
            }, 50)


            handler.postDelayed({
                bluetoothGatt?.let { current ->
                    if (current == gatt) eqChar?.let { readCharacteristicCompat(current, it) }
                }
            }, 150)

            handler.postDelayed({
                bluetoothGatt?.let { current ->
                    if (current == gatt) nameChar?.let { readCharacteristicCompat(current, it) }
                }
            }, 250)

            handler.postDelayed({
                bluetoothGatt?.let { current ->
                    if (current == gatt) {
                        fwVerChar?.let { readCharacteristicCompat(current, it) }
                    }
                }
            }, 350)

            handler.postDelayed({
                bluetoothGatt?.let { current ->
                    if (current == gatt) {
                        ledEffectChar?.let { readCharacteristicCompat(current, it) }
                    }
                }
            }, 450)

            runOnUiThread {
                tvStatus.text = "Connected"
                btnScanConnect.text = "Disconnect"
            }
        }

        @Deprecated("use onCharacteristicChanged(gatt, characteristic, value) instead")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            @Suppress("DEPRECATION")
            val raw = characteristic.value ?: return

            when (characteristic.uuid) {

                // ESP32 -> phone OTA progress notifications
                charUuidOtaCtrl -> {
                    val txt = raw.toString(Charsets.UTF_8).trim()
                    if (txt.startsWith("PROG:")) {
                        val payload = txt.removePrefix("PROG:")
                        val parts = payload.split("/")
                        if (parts.isNotEmpty()) {
                            val recv = parts[0].toLongOrNull() ?: 0L
                            espOtaBytes = recv

                            val total = currentFwSize
                            if (total > 0) {
                                val percent = ((recv * 100) / total).toInt().coerceIn(0, 100)
                                runOnUiThread {
                                    progressOta.progress = percent
                                    tvOtaStatus.text = "ESP32: ${recv / 1024} / ${total / 1024} KB ($percent%)"
                                }
                            }
                        }
                    }
                }

                charUuidLevels -> {
                    val str = raw.toString(Charsets.UTF_8).trim()
                    val parts = str.split(",")

                    runOnUiThread {
                        tvLevels.text = str

                        if (parts.size >= 3) {
                            val l30  = parts[0].toIntOrNull() ?: 0
                            val l60  = parts[1].toIntOrNull() ?: 0
                            val l100 = parts[2].toIntOrNull() ?: 0

                            bar30.progress  = l30.coerceIn(0, 120)
                            bar60.progress  = l60.coerceIn(0, 120)
                            bar100.progress = l100.coerceIn(0, 120)

                            tvVal30.text = "$l30 dB"
                            tvVal60.text = "$l60 dB"
                            tvVal100.text = "$l100 dB"
                        }
                    }
                }

                charUuidControl -> {
                    if (raw.isNotEmpty()) {
                        val b = raw[0].toInt() and 0xFF
                        val bassOn   = (b and 0x01) != 0
                        val flipOn   = (b and 0x02) != 0
                        val bypassOn = (b and 0x04) != 0

                        runOnUiThread {
                            updatingFromDevice = true
                            try {
                                switchBass.isChecked   = bassOn
                                switchFlip.isChecked   = flipOn
                                switchBypass.isChecked = bypassOn
                            } finally {
                                updatingFromDevice = false
                            }
                        }
                    }
                }

                charUuidEq -> {
                    if (raw.size >= 3) {
                        val bass   = raw[0].toInt().toByte().toInt()
                        val mid    = raw[1].toInt().toByte().toInt()
                        val treble = raw[2].toInt().toByte().toInt()
                        val EQ_RANGE = 12

                        runOnUiThread {
                            seekBass.progress   = bass   + EQ_RANGE
                            seekMid.progress    = mid    + EQ_RANGE
                            seekTreble.progress = treble + EQ_RANGE

                            tvBassLabel.text   = "$bass dB"
                            tvMidLabel.text    = "$mid dB"
                            tvTrebleLabel.text = "$treble dB"
                        }
                    }
                }

                charUuidName -> {
                    val name = raw.toString(Charsets.UTF_8).trim()
                    runOnUiThread {
                        etDeviceName.setText(name)
                        etDeviceName.hint = name
                    }
                    MainActivity.lastDeviceName = name
                }

                charUuidFwVer -> {
                    val ver = raw.toString(Charsets.UTF_8).trim()
                    currentFirmwareVersion = ver
                    runOnUiThread {
                        MainActivity.lastFirmwareVersion = ver
                    }
                }

                charUuidLedEffect -> {
                    if (raw.isNotEmpty()) {
                        val effectId = raw[0].toInt() and 0xFF
                        runOnUiThread {
                            updatingFromDevice = true
                            try {
                                if (effectId < ledEffectNames.size) {
                                    spinnerLedEffect.setSelection(effectId)
                                }
                            } finally {
                                updatingFromDevice = false
                            }
                        }
                    }
                }
            }
        }

        @Deprecated("use onCharacteristicRead(gatt, characteristic, value, status) instead")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) return
            @Suppress("DEPRECATION")
            val raw = characteristic.value ?: return

            when (characteristic.uuid) {
                charUuidName -> {
                    val name = raw.toString(Charsets.UTF_8).trim()
                    currentDeviceNameStr = name
                    runOnUiThread {
                        etDeviceName.setText(name)
                        etDeviceName.hint = name
                    }
                    MainActivity.lastDeviceName = name
                }

                charUuidEq -> {
                    if (raw.size >= 3) {
                        val bass   = raw[0].toInt().toByte().toInt()
                        val mid    = raw[1].toInt().toByte().toInt()
                        val treble = raw[2].toInt().toByte().toInt()
                        val EQ_RANGE = 12

                        runOnUiThread {
                            seekBass.progress   = bass   + EQ_RANGE
                            seekMid.progress    = mid    + EQ_RANGE
                            seekTreble.progress = treble + EQ_RANGE

                            tvBassLabel.text   = "$bass dB"
                            tvMidLabel.text    = "$mid dB"
                            tvTrebleLabel.text = "$treble dB"
                        }
                    }
                }

                charUuidControl -> {
                    if (raw.isNotEmpty()) {
                        val b = raw[0].toInt() and 0xFF
                        val bassOn   = (b and 0x01) != 0
                        val flipOn   = (b and 0x02) != 0
                        val bypassOn = (b and 0x04) != 0

                        runOnUiThread {
                            updatingFromDevice = true
                            try {
                                switchBass.isChecked   = bassOn
                                switchFlip.isChecked   = flipOn
                                switchBypass.isChecked = bypassOn
                            } finally {
                                updatingFromDevice = false
                            }
                        }
                    }
                }

                charUuidFwVer -> {
                    val ver = raw.toString(Charsets.UTF_8).trim()
                    currentFirmwareVersion = ver
                    MainActivity.lastFirmwareVersion = ver
                }

                charUuidLedEffect -> {
                    if (raw.isNotEmpty()) {
                        val effectId = raw[0].toInt() and 0xFF
                        runOnUiThread {
                            updatingFromDevice = true
                            try {
                                if (effectId < ledEffectNames.size) {
                                    spinnerLedEffect.setSelection(effectId)
                                }
                            } finally {
                                updatingFromDevice = false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(ch, true)
        val descriptor = ch.getDescriptor(cccdUuid)
        descriptor?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(it)
            }
        }
    }

    private fun readCharacteristicCompat(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.readCharacteristic(ch)
        } else {
            @Suppress("DEPRECATION")
            gatt.readCharacteristic(ch)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        ensurePermissions()
        bluetoothGatt?.close()
        bluetoothGatt = null

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(this, false, gattCallback)
        }
    }

    private fun disconnectGatt() {
        stopScan()
        // Cancel any ongoing OTA
        if (isOtaInProgress) {
            otaCancelled = true
        }
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        isOtaInProgress = false
        tvStatus.text = "Not Connected"
        btnScanConnect.text = "Connect"
        resetUiToDefaults()
    }

    private fun resetUiToDefaults() {
        // Reset state variables
        currentDeviceNameStr = "Unknown"
        currentFirmwareVersion = "Unknown"
        lastDeviceName = "Unknown"
        lastFirmwareVersion = "Unknown"

        // Reset level meters to 0
        bar30.progress = 0
        bar60.progress = 0
        bar100.progress = 0
        tvVal30.text = "0 dB"
        tvVal60.text = "0 dB"
        tvVal100.text = "0 dB"

        // Reset DSP controls to defaults (all off)
        updatingFromDevice = true
        try {
            switchBass.isChecked = false
            switchFlip.isChecked = false
            switchBypass.isChecked = false
        } finally {
            updatingFromDevice = false
        }

        // Reset EQ to 0 dB (center position = 12)
        seekBass.progress = 12
        seekMid.progress = 12
        seekTreble.progress = 12
        tvBassLabel.text = "0 dB"
        tvMidLabel.text = "0 dB"
        tvTrebleLabel.text = "0 dB"

        // Reset device name field
        etDeviceName.setText("")
        etDeviceName.hint = "Device Name"

        // Reset LED effect spinner to first item
        updatingFromDevice = true
        try {
            spinnerLedEffect.setSelection(0)
        } finally {
            updatingFromDevice = false
        }

        // Hide device list if visible
        hideDeviceList()
    }

    // ========= SEND DATA =========

    private fun sendControlByte() {
        if (!isConnected || controlChar == null || bluetoothGatt == null) return

        var v = 0
        if (switchBass.isChecked)   v = v or 0x01
        if (switchFlip.isChecked)   v = v or 0x02
        if (switchBypass.isChecked) v = v or 0x04

        val value = byteArrayOf(v.toByte())
        controlChar?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    it,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                it.value = value
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
    }

    private fun sendEq() {
        if (!isConnected || eqChar == null || bluetoothGatt == null) return

        val bass = seekBass.progress - 12
        val mid = seekMid.progress - 12
        val treble = seekTreble.progress - 12

        val value = byteArrayOf(bass.toByte(), mid.toByte(), treble.toByte())
        eqChar?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    it,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                it.value = value
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
    }

    private fun sendLedEffect(effectId: Int) {
        if (!isConnected || ledEffectChar == null || bluetoothGatt == null) return

        val value = byteArrayOf(effectId.toByte())
        ledEffectChar?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    it,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                it.value = value
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
    }

    // OTA WITH PROGRESS BAR AND DISCONNECT HANDLING
    // Disable/enable all controls during OTA
    private fun setControlsEnabled(enabled: Boolean) {
        btnScanConnect.isEnabled = enabled
        switchBass.isEnabled = enabled
        switchFlip.isEnabled = enabled
        switchBypass.isEnabled = enabled
        seekBass.isEnabled = enabled
        seekMid.isEnabled = enabled
        seekTreble.isEnabled = enabled
        etDeviceName.isEnabled = enabled
        btnApplyName.isEnabled = enabled
        spinnerLedEffect.isEnabled = enabled
        btnStartOta.isEnabled = enabled
        btnSelectFw.isEnabled = enabled
    }

    private fun startOta(uri: Uri) {
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

        // Prevent multiple OTA sessions
        if (isOtaInProgress) {
            runOnUiThread {
                tvOtaStatus.text = "OTA already in progress"
            }
            return
        }

        val gattNN = gatt
        val otaCtrlNN = ctrlChar
        val otaDataNN = dataChar

        // Reset state
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
            // Disable all controls during OTA
            setControlsEnabled(false)
        }

        Thread {
            try {
                // --- file size ---
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                    ?: throw Exception("Cannot open firmware")
                val fileSize = pfd.statSize
                pfd.close()
                currentFwSize = fileSize

                runOnUiThread {
                    tvOtaStatus.text = "Starting OTA (${fileSize / 1024} KB)..."
                }

                // --- BEGIN:<size> ---
                val begin = "BEGIN:$fileSize".toByteArray(Charsets.UTF_8)
                writeCharSimple(gattNN, otaCtrlNN, begin)
                Thread.sleep(300)

                // Check if cancelled
                if (otaCancelled || !isConnected) {
                    throw Exception("OTA cancelled - disconnected")
                }

                // --- stream firmware with no-response writes ---
                val input = contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open input stream")

                // payload MTU - 3 (ATT header). Clamp to [20, 512]
                val maxChunk = (currentMtu - 3).coerceIn(20, 512)
                val buffer = ByteArray(maxChunk)

                var total = 0L
                var lastUiUpdate = 0L

                while (true) {
                    // Check if cancelled or disconnected
                    if (otaCancelled || !isConnected) {
                        input.close()
                        throw Exception("OTA cancelled - disconnected")
                    }

                    val read = input.read(buffer)
                    if (read <= 0) break

                    val chunk = buffer.copyOf(read)
                    writeCharNoResp(gattNN, otaDataNN, chunk)

                    total += read

                    // UI status every ~2KB
                    if (total - lastUiUpdate >= 2048 || total == fileSize) {
                        lastUiUpdate = total
                        val percent = ((total * 100) / fileSize).toInt().coerceIn(0, 100)
                        runOnUiThread {
                            progressOta.progress = percent
                            tvOtaStatus.text = "Sending: ${total / 1024} / ${fileSize / 1024} KB ($percent%)"
                        }
                    }

                    // PROGRESS-BASED THROTTLING
                    val espProgress = espOtaBytes
                    val ahead = total - espProgress

                    val window1 = 32L * maxChunk
                    val window2 = 64L * maxChunk
                    val window3 = 128L * maxChunk

                    when {
                        ahead > window3 -> Thread.sleep(20)
                        ahead > window2 -> Thread.sleep(30)
                        ahead > window1 -> Thread.sleep(40)
                        else            -> Thread.sleep(20)
                    }
                }

                // Wait for ESP32 to finish writing
                runOnUiThread {
                    tvOtaStatus.text = "Waiting for ESP32 to finish..."
                }
                Thread.sleep(2000)
                input.close()

                // Check again before sending END
                if (otaCancelled || !isConnected) {
                    throw Exception("OTA cancelled - disconnected")
                }

                // --- END ---
                val endCmd = "END".toByteArray(Charsets.UTF_8)
                writeCharSimple(gattNN, otaCtrlNN, endCmd)

                runOnUiThread {
                    progressOta.progress = 100
                    tvOtaStatus.text = "OTA complete! Rebooting..."
                    tvOtaStatus.setTextColor(Color.parseColor("#22C55E"))
                    // Re-enable all controls
                    setControlsEnabled(true)
                }

                isOtaInProgress = false

            } catch (e: Exception) {
                e.printStackTrace()
                isOtaInProgress = false

                runOnUiThread {
                    progressOta.visibility = View.GONE
                    tvOtaStatus.text = "OTA failed: ${e.message}"
                    tvOtaStatus.setTextColor(Color.parseColor("#EF4444"))
                    // Re-enable all controls
                    setControlsEnabled(true)
                }

                // Try to send ABORT if still connected
                try {
                    val g = bluetoothGatt
                    val ch = otaCtrlChar
                    if (g != null && ch != null && isConnected && !otaCancelled) {
                        writeCharSimple(g, ch, "ABORT".toByteArray(Charsets.UTF_8))
                    }
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    // control / misc writes (with response)
    private fun writeCharSimple(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                ch,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            ch.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        }
    }

    // high-speed data writes (no response)
    private fun writeCharNoResp(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                ch,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
        } else {
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            ch.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        }
    }

    private fun sendDeviceName() {
        if (!isConnected || nameChar == null || bluetoothGatt == null) return

        val name = etDeviceName.text.toString()
        if (name.isEmpty()) return

        val value = name.toByteArray(Charsets.UTF_8)
        nameChar?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    it,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                it.value = value
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        otaCancelled = true
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}




