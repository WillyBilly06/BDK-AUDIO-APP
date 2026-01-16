package com.example.myspeaker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*
import java.util.regex.Pattern
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

    // LED Settings characteristic (brightness, colors, gradient)
    private val charUuidLedSettings =
        UUID.fromString("12345678-1234-1234-1234-1234567890b5")

    // Sound Control characteristic (mute status, control commands)
    private val charUuidSoundCtrl =
        UUID.fromString("12345678-1234-1234-1234-1234567890b6")
    
    // Sound Data characteristic (sound file upload)
    private val charUuidSoundData =
        UUID.fromString("12345678-1234-1234-1234-1234567890b7")

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

    // LED Settings UI
    private lateinit var ledSettingsCard: LinearLayout
    private lateinit var seekLedBrightness: SeekBar
    private lateinit var tvBrightnessValue: TextView
    private lateinit var viewColor1: View
    private lateinit var viewColor2: View
    private lateinit var spinnerGradient: Spinner
    private lateinit var seekLedSpeed: SeekBar
    private lateinit var tvSpeedValue: TextView
    private lateinit var ambientControlsContainer: LinearLayout
    
    // LED Settings state
    private var currentLedBrightness: Int = 50
    private var currentLedColor1: Int = Color.WHITE
    private var currentLedColor2: Int = Color.BLUE
    private var currentGradientType: Int = 0
    private var currentLedSpeed: Int = 50
    private var updatingLedSettings: Boolean = false

    // Codec Selection Spinner
    private lateinit var spinnerCodec: Spinner
    private lateinit var codecCard: LinearLayout

    // All possible Bluetooth codec names and types
    private val allCodecNames = arrayOf(
        "SBC (Default)",
        "AAC",
        "aptX",
        "aptX HD",
        "LDAC"
    )

    @Suppress("DEPRECATION")
    private val allCodecTypes = intArrayOf(
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX,
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD,
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC
    )

    // Currently available codecs for the connected device
    private var availableCodecNames = mutableListOf<String>()
    private var availableCodecTypes = mutableListOf<Int>()

    // Bluetooth A2DP for codec control
    private var bluetoothA2dp: BluetoothA2dp? = null
    private var a2dpDevice: BluetoothDevice? = null

    // Companion Device Manager for Android 16+ codec access
    private var companionDeviceManager: CompanionDeviceManager? = null
    private var isDeviceAssociated = false

    // Companion Device association launcher - handles result silently
    private val companionDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Association successful
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    CompanionDeviceManager.EXTRA_ASSOCIATION,
                    AssociationInfo::class.java
                )?.let { associationInfo ->
                    android.util.Log.d("BluetoothCodec", "Associated with device: ${associationInfo.deviceMacAddress}")
                    isDeviceAssociated = true
                }
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)?.also { device ->
                    android.util.Log.d("BluetoothCodec", "Associated with device: ${device.name} (${device.address})")
                    isDeviceAssociated = true
                }
            }
            
            if (isDeviceAssociated) {
                // Refresh codec spinner silently now that we're associated
                handler.postDelayed({ updateCodecSpinner() }, 500)
            }
        } else {
            android.util.Log.w("BluetoothCodec", "Companion device association cancelled or failed")
            // Fail silently - don't show toast
        }
    }

    // Bluetooth state receiver to detect when BT is turned off
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            runOnUiThread {
                                if (isConnected) {
                                    disconnectGatt()
                                    tvStatus.text = "Bluetooth turned off"
                                    btnScanConnect.text = "Connect"
                                }
                            }
                        }
                        BluetoothAdapter.STATE_ON -> {
                            runOnUiThread {
                                tvStatus.text = "Not Connected"
                            }
                        }
                    }
                }
            }
        }
    }

    // Codec config changed receiver - like Bluetooth Codec Changer app
    private val codecConfigReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED") {
                android.util.Log.d("BluetoothCodec", "CODEC_CONFIG_CHANGED broadcast received!")
                
                // Try to get codec status from the intent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val codecStatus = intent.getParcelableExtra(
                        BluetoothCodecStatus.EXTRA_CODEC_STATUS,
                        BluetoothCodecStatus::class.java
                    )
                    android.util.Log.d("BluetoothCodec", "New codec status: $codecStatus")
                    codecStatus?.codecConfig?.let { config ->
                        android.util.Log.d("BluetoothCodec", "New codec type: ${config.codecType}")
                    }
                }
                
                // Update UI when codec changes (from our app or system)
                handler.postDelayed({ updateCodecSpinner() }, 200)
            }
        }
    }

    // A2DP profile listener for codec control
    @android.annotation.SuppressLint("MissingPermission")
    private val a2dpProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            try {
                android.util.Log.d("BluetoothCodec", "A2DP onServiceConnected: profile=$profile, proxy=$proxy")
                if (profile == BluetoothProfile.A2DP) {
                    bluetoothA2dp = proxy as? BluetoothA2dp
                    android.util.Log.d("BluetoothCodec", "A2DP proxy connected: $bluetoothA2dp")
                    
                    // Check connected devices immediately (only if we have permission)
                    if (hasBluetoothPermissions()) {
                        val connectedDevices = bluetoothA2dp?.connectedDevices
                        android.util.Log.d("BluetoothCodec", "A2DP connected devices: ${connectedDevices?.size ?: 0}")
                        connectedDevices?.forEach { device ->
                            android.util.Log.d("BluetoothCodec", "  Device: ${device.name} (${device.address})")
                        }
                        
                        updateCodecSpinner()
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("BluetoothCodec", "A2DP onServiceConnected - permission denied", e)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            android.util.Log.d("BluetoothCodec", "A2DP onServiceDisconnected: profile=$profile")
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = null
            }
        }
    }

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
        "Lava Lamp",          // 20 - LED_EFFECT_LAVA_LAMP
        "Ambient"             // 21 - LED_EFFECT_AMBIENT
    )

    // Gradient type names matching ESP32 enum (from led_config.h LedGradientType)
    private val gradientTypeNames = arrayOf(
        "None",               // 0 - GRADIENT_NONE (solid color1)
        "Horizontal",         // 1 - GRADIENT_LINEAR_H
        "Vertical",           // 2 - GRADIENT_LINEAR_V
        "Radial",             // 3 - GRADIENT_RADIAL
        "Diagonal"            // 4 - GRADIENT_DIAGONAL
    )
    
    // Ambient effect ID (must match ESP32)
    private val LED_EFFECT_AMBIENT = 21

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var levelsChar: BluetoothGattCharacteristic? = null
    private var controlChar: BluetoothGattCharacteristic? = null
    private var eqChar: BluetoothGattCharacteristic? = null
    private var nameChar: BluetoothGattCharacteristic? = null
    private var ledEffectChar: BluetoothGattCharacteristic? = null
    private var ledSettingsChar: BluetoothGattCharacteristic? = null
    private var soundCtrlChar: BluetoothGattCharacteristic? = null
    private var soundDataChar: BluetoothGattCharacteristic? = null

    private var fwVerChar: BluetoothGattCharacteristic? = null

    private var currentDeviceNameStr: String = "Unknown"

    var currentFirmwareVersion: String = "Unknown"

    // Current codec info for Device Info screen
    private var currentCodecName: String = "Unknown"
    private var currentSampleRate: String = "Unknown"
    private var currentBitsPerSample: String = "Unknown"
    private var currentChannelMode: String = "Unknown"
    private var currentPlaybackQuality: String = "Unknown"

    // Reference to the Device Info bottom sheet for live updates
    private var deviceInfoBottomSheet: DeviceInfoBottomSheet? = null

    private var isConnected = false
    
    // Sound status byte: bit 0-3 = sound exists flags, bit 7 = muted
    private var soundStatus: Int = 0
    var isSoundMuted: Boolean
        get() = (soundStatus and 0x80) != 0
        set(value) {
            soundStatus = if (value) soundStatus or 0x80 else soundStatus and 0x7F
        }
    
    fun hasSoundFile(type: Int): Boolean = (soundStatus and (1 shl type)) != 0
    
    // Get current sound status for DeviceInfoBottomSheet sync
    fun getSoundStatus(): Int = soundStatus

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

    // permissions - Android 12+ uses BLUETOOTH_SCAN/CONNECT, older uses location
    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            // Log permission results
            results.forEach { (permission, granted) ->
                android.util.Log.d("Permissions", "$permission: $granted")
            }
            // If all permissions granted, continue with scan
            if (results.values.all { it }) {
                android.util.Log.d("Permissions", "All permissions granted")
            } else {
                android.util.Log.w("Permissions", "Some permissions denied")
                runOnUiThread {
                    tvStatus.text = "Bluetooth permission required"
                }
            }
        }

    /**
     * Check if all required Bluetooth permissions are granted
     */
    private fun hasBluetoothPermissions(): Boolean {
        return blePermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

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
            AppInfoBottomSheet().show(supportFragmentManager, AppInfoBottomSheet.TAG)
        }
        val btnDeviceInfo = findViewById<Button>(R.id.btnDeviceInfo)
        btnDeviceInfo.setOnClickListener {
            val bottomSheet = DeviceInfoBottomSheet.newInstance(
                isConnected = isConnected,
                deviceName = if (isConnected) currentDeviceNameStr else "Unknown",
                fwVersion = if (isConnected) currentFirmwareVersion else "Unknown",
                codecName = currentCodecName,
                sampleRate = currentSampleRate,
                bitsPerSample = currentBitsPerSample,
                channelMode = currentChannelMode,
                playbackQuality = currentPlaybackQuality,
                soundStatus = soundStatus
            )
            deviceInfoBottomSheet = bottomSheet
            bottomSheet.show(supportFragmentManager, DeviceInfoBottomSheet.TAG)
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

        // LED Settings UI
        ledSettingsCard = findViewById(R.id.ledSettingsCard)
        seekLedBrightness = findViewById(R.id.seekLedBrightness)
        tvBrightnessValue = findViewById(R.id.tvBrightnessValue)
        viewColor1 = findViewById(R.id.viewColor1)
        viewColor2 = findViewById(R.id.viewColor2)
        spinnerGradient = findViewById(R.id.spinnerGradient)
        seekLedSpeed = findViewById(R.id.seekLedSpeed)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)
        ambientControlsContainer = findViewById(R.id.ambientControlsContainer)
        setupLedSettingsUI()

        // Codec Selection Spinner
        spinnerCodec = findViewById(R.id.spinnerCodec)
        codecCard = findViewById(R.id.codecCard)
        setupCodecSpinner()

        // Associate device button for codec control
        val btnAssociateDevice = findViewById<Button>(R.id.btnAssociateDevice)
        btnAssociateDevice.setOnClickListener {
            val device = a2dpDevice
            if (device != null) {
                requestCompanionAssociation(device)
            } else {
                Toast.makeText(this, "No A2DP device connected", Toast.LENGTH_SHORT).show()
            }
        }

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

        // Initialize Companion Device Manager for codec control on Android 16+
        companionDeviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
        checkExistingAssociations()

        // Register Bluetooth state receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        // Register codec config changed receiver (like Bluetooth Codec Changer app)
        val codecFilter = IntentFilter("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED")
        registerReceiver(codecConfigReceiver, codecFilter)

        // Get A2DP profile for codec control
        bluetoothAdapter?.getProfileProxy(this, a2dpProfileListener, BluetoothProfile.A2DP)

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

    override fun onResume() {
        super.onResume()
        // Refresh codec info when returning from other screens
        if (isConnected && bluetoothA2dp != null) {
            handler.postDelayed({ updateCodecSpinner() }, 300)
        }
        // Update UI to reflect current connection state
        if (isConnected) {
            tvStatus.text = "Connected"
            btnScanConnect.text = "Disconnect"
            codecCard.visibility = View.VISIBLE
        } else {
            tvStatus.text = "Not Connected"
            btnScanConnect.text = "Connect"
            codecCard.visibility = View.GONE
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
                // Show/hide ambient controls based on selection
                updateAmbientControlsVisibility(position)
                
                if (!updatingFromDevice) {
                    sendLedEffect(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateAmbientControlsVisibility(effectPosition: Int) {
        // Show ambient controls only when Ambient effect is selected
        ambientControlsContainer.visibility = if (effectPosition == LED_EFFECT_AMBIENT) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun setupLedSettingsUI() {
        // Brightness SeekBar
        seekLedBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBrightnessValue.text = progress.toString()
                if (fromUser && !updatingLedSettings) {
                    currentLedBrightness = progress
                    sendLedSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Color 1 click handler - show color picker dialog
        viewColor1.setOnClickListener {
            showColorPickerDialog(currentLedColor1) { color ->
                currentLedColor1 = color
                viewColor1.setBackgroundColor(color)
                sendLedSettings()
            }
        }
        viewColor1.setBackgroundColor(currentLedColor1)

        // Color 2 click handler - show color picker dialog
        viewColor2.setOnClickListener {
            showColorPickerDialog(currentLedColor2) { color ->
                currentLedColor2 = color
                viewColor2.setBackgroundColor(color)
                sendLedSettings()
            }
        }
        viewColor2.setBackgroundColor(currentLedColor2)

        // Gradient Spinner
        val gradientAdapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            gradientTypeNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 14f
                view.setPadding(12, 8, 12, 8)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(Color.parseColor("#1E1E1E"))
                view.textSize = 14f
                view.setPadding(20, 12, 20, 12)
                return view
            }
        }
        gradientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGradient.adapter = gradientAdapter

        spinnerGradient.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updatingLedSettings) {
                    currentGradientType = position
                    sendLedSettings()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Speed SeekBar
        seekLedSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSpeedValue.text = progress.toString()
                if (fromUser && !updatingLedSettings) {
                    currentLedSpeed = progress
                    sendLedSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit) {
        // Create a simple color picker dialog with RGB sliders
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val colorPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100
            ).apply { bottomMargin = 32 }
            setBackgroundColor(initialColor)
        }
        layout.addView(colorPreview)

        var red = Color.red(initialColor)
        var green = Color.green(initialColor)
        var blue = Color.blue(initialColor)

        fun updatePreview() {
            colorPreview.setBackgroundColor(Color.rgb(red, green, blue))
        }

        // Red slider
        val redLabel = TextView(this).apply {
            text = "Red: $red"
            setTextColor(Color.WHITE)
        }
        layout.addView(redLabel)
        val seekRed = SeekBar(this).apply {
            max = 255
            progress = red
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    red = progress
                    redLabel.text = "Red: $red"
                    updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(seekRed)

        // Green slider
        val greenLabel = TextView(this).apply {
            text = "Green: $green"
            setTextColor(Color.WHITE)
        }
        layout.addView(greenLabel)
        val seekGreen = SeekBar(this).apply {
            max = 255
            progress = green
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    green = progress
                    greenLabel.text = "Green: $green"
                    updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(seekGreen)

        // Blue slider
        val blueLabel = TextView(this).apply {
            text = "Blue: $blue"
            setTextColor(Color.WHITE)
        }
        layout.addView(blueLabel)
        val seekBlue = SeekBar(this).apply {
            max = 255
            progress = blue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    blue = progress
                    blueLabel.text = "Blue: $blue"
                    updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(seekBlue)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Select Color")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                onColorSelected(Color.rgb(red, green, blue))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupCodecSpinner() {
        // Set flag to prevent triggering codec change during initialization
        updatingFromDevice = true

        // Initial placeholder - will be updated when A2DP connects
        availableCodecNames.clear()
        availableCodecTypes.clear()
        availableCodecNames.add("Detecting codecs...")

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            ArrayList(availableCodecNames)
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
        spinnerCodec.adapter = adapter

        spinnerCodec.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updatingFromDevice && availableCodecTypes.isNotEmpty()) {
                    setBluetoothCodec(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Hide codec card initially - will show when BLE connects
        codecCard.visibility = View.GONE

        // Reset flag after a delay
        handler.postDelayed({
            updatingFromDevice = false
        }, 500)
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission")
    private fun updateCodecSpinner() {
        android.util.Log.d("BluetoothCodec", "updateCodecSpinner() called")
        
        // Check permissions first
        if (!hasBluetoothPermissions()) {
            android.util.Log.w("BluetoothCodec", "updateCodecSpinner: No Bluetooth permissions")
            return
        }
        
        // Get currently connected A2DP devices and update spinner
        val a2dp = bluetoothA2dp
        if (a2dp == null) {
            android.util.Log.e("BluetoothCodec", "updateCodecSpinner: A2DP proxy is null!")
            return
        }
        
        try {
            val connectedDevices = a2dp.connectedDevices
            android.util.Log.d("BluetoothCodec", "updateCodecSpinner: ${connectedDevices.size} A2DP devices connected")
        
        if (connectedDevices.isNotEmpty()) {
            a2dpDevice = connectedDevices[0]
            android.util.Log.d("BluetoothCodec", "updateCodecSpinner: Using device ${a2dpDevice?.name} (${a2dpDevice?.address})")
            
            // Clear previous codec lists
            val newCodecNames = mutableListOf<String>()
            val newCodecTypes = mutableListOf<Int>()

            var currentCodecIndex = 0
            var currentCodecType = BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC

            // Try to get codec status and available codecs using reflection
            try {
                android.util.Log.d("BluetoothCodec", "Getting codec status via reflection...")
                val getCodecStatusMethod = a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
                android.util.Log.d("BluetoothCodec", "getCodecStatus method: $getCodecStatusMethod")
                
                val codecStatus = getCodecStatusMethod.invoke(a2dp, a2dpDevice)
                android.util.Log.d("BluetoothCodec", "codecStatus result: $codecStatus")
                
                if (codecStatus != null) {
                    // Get selectable codecs
                    val getSelectableMethod = codecStatus.javaClass.getMethod("getCodecsSelectableCapabilities")
                    val selectableCodecs = getSelectableMethod.invoke(codecStatus) as? List<*>
                    android.util.Log.d("BluetoothCodec", "Selectable codecs count: ${selectableCodecs?.size ?: 0}")
                    
                    selectableCodecs?.forEach { codec ->
                        if (codec != null) {
                            val getTypeMethod = codec.javaClass.getMethod("getCodecType")
                            val type = getTypeMethod.invoke(codec) as? Int
                            android.util.Log.d("BluetoothCodec", "  Found codec type: $type")
                            if (type != null && !newCodecTypes.contains(type)) {
                                val name = when (type) {
                                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> "SBC (Default)"
                                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
                                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
                                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
                                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
                                    else -> "Codec $type"
                                }
                                newCodecNames.add(name)
                                newCodecTypes.add(type)
                                android.util.Log.d("BluetoothCodec", "  Added: $name (type=$type)")
                            }
                        }
                    }
                    
                    // Get current codec
                    val getConfigMethod = codecStatus.javaClass.getMethod("getCodecConfig")
                    val codecConfig = getConfigMethod.invoke(codecStatus)
                    android.util.Log.d("BluetoothCodec", "Current codec config: $codecConfig")
                    if (codecConfig != null) {
                        val getTypeMethod = codecConfig.javaClass.getMethod("getCodecType")
                        currentCodecType = getTypeMethod.invoke(codecConfig) as? Int ?: BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC
                        android.util.Log.d("BluetoothCodec", "Current codec type: $currentCodecType")
                        
                        // Extract codec details for Device Info screen
                        try {
                            // Get codec name
                            currentCodecName = when (currentCodecType) {
                                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> "SBC"
                                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
                                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
                                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
                                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
                                else -> "Unknown"
                            }
                            
                            // Get sample rate
                            val getSampleRateMethod = codecConfig.javaClass.getMethod("getSampleRate")
                            val sampleRate = getSampleRateMethod.invoke(codecConfig) as? Int ?: 0
                            currentSampleRate = when (sampleRate) {
                                0x01 -> "44.1 kHz"
                                0x02 -> "48 kHz"
                                0x04 -> "88.2 kHz"
                                0x08 -> "96 kHz"
                                0x10 -> "176.4 kHz"
                                0x20 -> "192 kHz"
                                else -> if (sampleRate > 0) "$sampleRate Hz" else "Unknown"
                            }
                            
                            // Get bits per sample
                            val getBitsMethod = codecConfig.javaClass.getMethod("getBitsPerSample")
                            val bitsPerSample = getBitsMethod.invoke(codecConfig) as? Int ?: 0
                            currentBitsPerSample = when (bitsPerSample) {
                                0x01 -> "16-bit"
                                0x02 -> "24-bit"
                                0x04 -> "32-bit"
                                else -> if (bitsPerSample > 0) "$bitsPerSample-bit" else "Unknown"
                            }
                            
                            // Get channel mode
                            val getChannelModeMethod = codecConfig.javaClass.getMethod("getChannelMode")
                            val channelMode = getChannelModeMethod.invoke(codecConfig) as? Int ?: 0
                            currentChannelMode = when (channelMode) {
                                0x01 -> "Mono"
                                0x02 -> "Stereo"
                                else -> if (channelMode > 0) "Mode $channelMode" else "Unknown"
                            }
                            
                            // Get playback quality (LDAC quality mode from codecSpecific1)
                            val getCodecSpecific1Method = codecConfig.javaClass.getMethod("getCodecSpecific1")
                            val codecSpecific1 = getCodecSpecific1Method.invoke(codecConfig) as? Long ?: 0
                            currentPlaybackQuality = when {
                                currentCodecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> {
                                    when (codecSpecific1.toInt()) {
                                        1000 -> "Quality (990 kbps)"
                                        1001 -> "Quality (660 kbps)"
                                        1002 -> "Quality (330 kbps)"
                                        1003 -> "Best Effort (Adaptive)"
                                        else -> "Default"
                                    }
                                }
                                currentCodecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "High Definition"
                                currentCodecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "Low Latency"
                                currentCodecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "Variable Bitrate"
                                else -> "Standard"
                            }
                            
                            android.util.Log.d("BluetoothCodec", "Codec details: $currentCodecName, $currentSampleRate, $currentBitsPerSample, $currentChannelMode, $currentPlaybackQuality")
                            
                            // Update Device Info bottom sheet if open
                            runOnUiThread {
                                deviceInfoBottomSheet?.updateInfo(
                                    isConnected = isConnected,
                                    deviceName = currentDeviceNameStr,
                                    fwVersion = currentFirmwareVersion,
                                    codecName = currentCodecName,
                                    sampleRate = currentSampleRate,
                                    bitsPerSample = currentBitsPerSample,
                                    channelMode = currentChannelMode,
                                    playbackQuality = currentPlaybackQuality
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BluetoothCodec", "Failed to get codec details", e)
                        }
                    }
                } else {
                    android.util.Log.e("BluetoothCodec", "getCodecStatus returned null!")
                    // This might be due to missing Companion Device association on Android 16+
                    if (!isDeviceAssociated && a2dpDevice != null) {
                        android.util.Log.d("BluetoothCodec", "Not associated - requesting association...")
                        runOnUiThread {
                            requestCompanionAssociation(a2dpDevice!!)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BluetoothCodec", "Failed to get codec status", e)
                e.cause?.let { cause ->
                    android.util.Log.e("BluetoothCodec", "Cause: ${cause.javaClass.name}: ${cause.message}")
                    // Check if it's a security exception - need companion association
                    if (cause is SecurityException && !isDeviceAssociated && a2dpDevice != null) {
                        android.util.Log.d("BluetoothCodec", "SecurityException - requesting association...")
                        runOnUiThread {
                            requestCompanionAssociation(a2dpDevice!!)
                        }
                    }
                }
            }

            // If no codecs were detected, add defaults
            if (newCodecNames.isEmpty()) {
                android.util.Log.w("BluetoothCodec", "No codecs detected, adding SBC default")
                newCodecNames.add("SBC (Default)")
                newCodecTypes.add(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC)
            }

            android.util.Log.d("BluetoothCodec", "Final codec list: $newCodecNames")
            android.util.Log.d("BluetoothCodec", "Final codec types: $newCodecTypes")

            // Find current codec index
            currentCodecIndex = newCodecTypes.indexOf(currentCodecType)
            if (currentCodecIndex < 0) currentCodecIndex = 0
            android.util.Log.d("BluetoothCodec", "Current codec index: $currentCodecIndex")

            // Update the class variables
            availableCodecNames.clear()
            availableCodecNames.addAll(newCodecNames)
            availableCodecTypes.clear()
            availableCodecTypes.addAll(newCodecTypes)

            val finalCodecIndex = currentCodecIndex

            // Update spinner adapter with available codecs
            runOnUiThread {
                updatingFromDevice = true
                android.util.Log.d("BluetoothCodec", "Updating spinner UI with ${newCodecNames.size} codecs")

                val adapter = object : ArrayAdapter<String>(
                    this,
                    android.R.layout.simple_spinner_item,
                    ArrayList(availableCodecNames)
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
                spinnerCodec.adapter = adapter
                spinnerCodec.setSelection(finalCodecIndex)
                spinnerCodec.isEnabled = true

                // Only show codec card when BLE is connected
                codecCard.visibility = if (isConnected) View.VISIBLE else View.GONE
                
                // Hide associate button - we handle association automatically
                val btnAssociate = findViewById<Button>(R.id.btnAssociateDevice)
                btnAssociate.visibility = View.GONE

                handler.postDelayed({
                    updatingFromDevice = false
                }, 500)
            }
        } else {
            // No A2DP device connected
            android.util.Log.w("BluetoothCodec", "No A2DP devices connected")
            runOnUiThread {
                updatingFromDevice = true

                availableCodecNames.clear()
                availableCodecTypes.clear()
                availableCodecNames.add("No A2DP device")

                val adapter = object : ArrayAdapter<String>(
                    this,
                    android.R.layout.simple_spinner_item,
                    ArrayList(availableCodecNames)
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        view.setTextColor(Color.GRAY)
                        view.textSize = 15f
                        view.setPadding(16, 12, 16, 12)
                        return view
                    }
                }
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCodec.adapter = adapter
                spinnerCodec.isEnabled = false
                // Only show codec card when BLE is connected
                codecCard.visibility = if (isConnected) View.VISIBLE else View.GONE

                handler.postDelayed({
                    updatingFromDevice = false
                }, 500)
            }
        }
        } catch (e: SecurityException) {
            android.util.Log.w("BluetoothCodec", "updateCodecSpinner: permission denied", e)
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission")
    private fun setBluetoothCodec(codecIndex: Int) {
        android.util.Log.d("BluetoothCodec", "setBluetoothCodec called with index: $codecIndex")
        
        if (codecIndex < 0 || codecIndex >= availableCodecTypes.size) {
            android.util.Log.e("BluetoothCodec", "Invalid codec index: $codecIndex")
            return
        }

        val a2dp = bluetoothA2dp ?: run {
            android.util.Log.e("BluetoothCodec", "A2DP proxy is null")
            Toast.makeText(this, "A2DP not available. Reconnecting...", Toast.LENGTH_SHORT).show()
            bluetoothAdapter?.getProfileProxy(this, a2dpProfileListener, BluetoothProfile.A2DP)
            return
        }
        
        val connectedDevices = a2dp.connectedDevices
        android.util.Log.d("BluetoothCodec", "Connected A2DP devices: ${connectedDevices.size}")
        
        if (connectedDevices.isEmpty()) {
            android.util.Log.e("BluetoothCodec", "No A2DP devices connected")
            Toast.makeText(this, "No A2DP device connected. Connect speaker via Bluetooth settings first.", Toast.LENGTH_LONG).show()
            return
        }
        
        val device = connectedDevices[0]
        a2dpDevice = device
        android.util.Log.d("BluetoothCodec", "Target device: ${device.name} (${device.address})")

        val codecType = availableCodecTypes[codecIndex]
        val codecName = availableCodecNames[codecIndex]
        android.util.Log.d("BluetoothCodec", "Target codec: $codecName (type=$codecType)")

        // Get current codec before change
        val currentCodecBefore = getCurrentCodecType(a2dp, device)
        android.util.Log.d("BluetoothCodec", "Current codec before change: $currentCodecBefore")

        // Get current codec status to read existing config values
        val currentConfig = invokeGetCodecStatus(a2dp, device)
        android.util.Log.d("BluetoothCodec", "Current codec config: $currentConfig")

        // For LDAC, we can set codecSpecific1 for quality mode (0=default, 1000-1003 for quality levels)
        // For now, use 0 (default/inherit)
        val codecSpecific1 = 0L

        try {
            // Build the codec config - using 0 means inherit from current config (alternative config behavior)
            val codecConfig = buildCodecConfig(
                codecType = codecType,
                sampleRate = 0,  // inherit from current
                bitsPerSample = 0,  // inherit from current
                channelMode = 0,  // inherit from current
                codecSpecific1 = codecSpecific1
            )
            
            android.util.Log.d("BluetoothCodec", "Built codec config: $codecConfig")

            // Two-step apply: if codecSpecific1 != 0, first apply with codecSpecific1=0, then with actual value
            // Per reverse engineering: requiresTwoStep returns true iff codecSpecific1 != 0
            if (codecSpecific1 != 0L) {
                android.util.Log.d("BluetoothCodec", "Two-step apply (codecSpecific1 != 0)")
                
                // Step 1: Apply with codecSpecific1 = 0
                val step1Config = buildCodecConfig(codecType, 0, 0, 0, 0L)
                invokeSetCodec(a2dp, device, step1Config)
                
                // Step 2: After 250ms, apply with actual codecSpecific1
                handler.postDelayed({
                    try {
                        invokeSetCodec(a2dp, device, codecConfig)
                        android.util.Log.d("BluetoothCodec", "Codec set to: $codecName")
                        // Verify codec change after another delay
                        verifyCodecChange(codecType, codecName, currentCodecBefore)
                    } catch (e: Exception) {
                        android.util.Log.e("BluetoothCodec", "Two-step codec change failed", e)
                    }
                }, 250)
            } else {
                // Direct apply
                android.util.Log.d("BluetoothCodec", "Direct apply codec")
                invokeSetCodec(a2dp, device, codecConfig)
                android.util.Log.d("BluetoothCodec", "Codec set to: $codecName")
                // Verify codec change after a delay
                verifyCodecChange(codecType, codecName, currentCodecBefore)
            }
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            android.util.Log.e("BluetoothCodec", "InvocationTargetException", e)
            android.util.Log.e("BluetoothCodec", "Cause: ${cause?.javaClass?.name}: ${cause?.message}")
        } catch (e: Exception) {
            android.util.Log.e("BluetoothCodec", "Exception during codec change", e)
        }
    }

    /**
     * Verify that the codec change was successful. If not, prompt user to enable it in Developer Options.
     */
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission")
    private fun verifyCodecChange(targetCodecType: Int, targetCodecName: String, previousCodecType: Int) {
        handler.postDelayed({
            val a2dp = bluetoothA2dp ?: return@postDelayed
            val device = a2dpDevice ?: return@postDelayed
            
            val currentCodec = getCurrentCodecType(a2dp, device)
            android.util.Log.d("BluetoothCodec", "Verify: target=$targetCodecType, current=$currentCodec, previous=$previousCodecType")
            
            if (currentCodec != targetCodecType) {
                // Codec didn't change - show prompt based on codec type
                val codecTypeName = when (targetCodecType) {
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
                    else -> targetCodecName
                }
                
                runOnUiThread {
                    showCodecEnableDialog(codecTypeName)
                }
            } else {
                android.util.Log.d("BluetoothCodec", "Codec successfully changed to $targetCodecName")
            }
        }, 500)  // Wait 500ms for the codec change to take effect
    }

    /**
     * Show a dialog prompting the user to enable the codec in Developer Options
     */
    private fun showCodecEnableDialog(codecName: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("$codecName Not Enabled")
            .setMessage("$codecName codec is disabled in your phone's Bluetooth settings.\n\n" +
                       "To enable it:\n" +
                       "1. Go to Settings → Developer options\n" +
                       "2. Find 'Bluetooth Audio Codec' or 'Optional codecs'\n" +
                       "3. Enable $codecName\n\n" +
                       "Would you like to open Developer Options?")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    // Try to open Developer Options directly
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fall back to Bluetooth settings
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Get codec status via reflection - matches CodecReflectionKt.invokeGetCodec()
     */
    private fun invokeGetCodecStatus(a2dp: BluetoothA2dp, device: BluetoothDevice): BluetoothCodecStatus? {
        return try {
            val method = a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            method.invoke(a2dp, device) as? BluetoothCodecStatus
        } catch (e: Exception) {
            android.util.Log.e("BluetoothCodec", "invokeGetCodecStatus failed", e)
            null
        }
    }

    /**
     * Set codec via reflection - matches CodecReflectionKt.invokeSetCodec()
     */
    private fun invokeSetCodec(a2dp: BluetoothA2dp, device: BluetoothDevice, codecConfig: BluetoothCodecConfig) {
        android.util.Log.d("BluetoothCodec", "invokeSetCodec: device=${device.address}, config=$codecConfig")
        
        val method = a2dp.javaClass.getMethod(
            "setCodecConfigPreference",
            BluetoothDevice::class.java,
            BluetoothCodecConfig::class.java
        )
        method.invoke(a2dp, device, codecConfig)
        android.util.Log.d("BluetoothCodec", "invokeSetCodec completed")
    }

    /**
     * Build BluetoothCodecConfig using constructor via reflection.
     * This matches the exact behavior of the Bluetooth Codec Changer app.
     * 
     * Constructor: BluetoothCodecConfig(codecType, priority, sampleRate, bitsPerSample, channelMode, 
     *                                   codecSpecific1, codecSpecific2, codecSpecific3, codecSpecific4)
     * 
     * Priority is always 1,000,000 (CODEC_PRIORITY_HIGHEST)
     * 
     * Passing 0 for sampleRate/bitsPerSample/channelMode means "inherit from current config"
     * (this is the "alternative config" behavior from the app)
     */
    @Suppress("DEPRECATION")
    private fun buildCodecConfig(
        codecType: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        channelMode: Int,
        codecSpecific1: Long
    ): BluetoothCodecConfig {
        android.util.Log.d("BluetoothCodec", "buildCodecConfig: type=$codecType, sampleRate=$sampleRate, bits=$bitsPerSample, channel=$channelMode, specific1=$codecSpecific1")
        
        try {
            // Always use constructor via reflection (matches app behavior)
            val constructor = BluetoothCodecConfig::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType,    // codecType
                Int::class.javaPrimitiveType,    // codecPriority
                Int::class.javaPrimitiveType,    // sampleRate
                Int::class.javaPrimitiveType,    // bitsPerSample
                Int::class.javaPrimitiveType,    // channelMode
                Long::class.javaPrimitiveType,   // codecSpecific1
                Long::class.javaPrimitiveType,   // codecSpecific2
                Long::class.javaPrimitiveType,   // codecSpecific3
                Long::class.javaPrimitiveType    // codecSpecific4
            )
            constructor.isAccessible = true
            
            val config = constructor.newInstance(
                codecType,
                1000000,  // priority = 1,000,000 (highest, as per app)
                sampleRate,
                bitsPerSample,
                channelMode,
                codecSpecific1,
                0L,  // codecSpecific2
                0L,  // codecSpecific3
                0L   // codecSpecific4
            )
            
            android.util.Log.d("BluetoothCodec", "buildCodecConfig success: $config")
            return config
        } catch (e: Exception) {
            android.util.Log.e("BluetoothCodec", "buildCodecConfig failed", e)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentCodecType(a2dp: BluetoothA2dp, device: BluetoothDevice): Int {
        return try {
            val getCodecStatusMethod = a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val codecStatus = getCodecStatusMethod.invoke(a2dp, device)
            if (codecStatus != null) {
                val getCodecConfigMethod = codecStatus.javaClass.getMethod("getCodecConfig")
                val codecConfig = getCodecConfigMethod.invoke(codecStatus)
                if (codecConfig != null) {
                    val getCodecTypeMethod = codecConfig.javaClass.getMethod("getCodecType")
                    @Suppress("DEPRECATION")
                    getCodecTypeMethod.invoke(codecConfig) as? Int ?: BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC
                } else {
                    @Suppress("DEPRECATION")
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC
                }
            } else {
                @Suppress("DEPRECATION")
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC
            }
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC
        }
    }

    /**
     * Check if we have any existing Companion Device associations
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun checkExistingAssociations() {
        val cdm = companionDeviceManager ?: return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val associations = cdm.myAssociations
                android.util.Log.d("BluetoothCodec", "Existing associations: ${associations.size}")
                associations.forEach { info ->
                    android.util.Log.d("BluetoothCodec", "  Associated: ${info.deviceMacAddress}")
                }
                isDeviceAssociated = associations.isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                val associations = cdm.associations
                android.util.Log.d("BluetoothCodec", "Existing associations: ${associations.size}")
                associations.forEach { mac ->
                    android.util.Log.d("BluetoothCodec", "  Associated: $mac")
                }
                isDeviceAssociated = associations.isNotEmpty()
            }
            
            if (isDeviceAssociated) {
                android.util.Log.d("BluetoothCodec", "Device is already associated - codec control should work")
            } else {
                android.util.Log.d("BluetoothCodec", "No existing associations - will auto-associate when needed")
            }
        } catch (e: Exception) {
            android.util.Log.e("BluetoothCodec", "Failed to check associations", e)
        }
    }

    /**
     * Check if a specific device is already associated by MAC address
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun isDeviceAlreadyAssociated(deviceAddress: String): Boolean {
        val cdm = companionDeviceManager ?: return false
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.myAssociations.any { info ->
                    info.deviceMacAddress?.toString()?.equals(deviceAddress, ignoreCase = true) == true
                }
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.any { mac ->
                    mac.equals(deviceAddress, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BluetoothCodec", "Failed to check device association", e)
            false
        }
    }

    /**
     * Request Companion Device association for the connected A2DP device.
     * This is required on Android 16+ to access hidden codec APIs.
     * Association happens silently if device is already known.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun requestCompanionAssociation(device: BluetoothDevice) {
        val cdm = companionDeviceManager
        if (cdm == null) {
            android.util.Log.e("BluetoothCodec", "CompanionDeviceManager not available")
            return
        }

        android.util.Log.d("BluetoothCodec", "Requesting companion association for ${device.name} (${device.address})")

        // Check if this specific device is already associated
        if (isDeviceAlreadyAssociated(device.address)) {
            android.util.Log.d("BluetoothCodec", "Device ${device.address} already associated")
            isDeviceAssociated = true
            return
        }

        try {
            // Build a filter to find this specific Bluetooth device by MAC address
            val deviceFilter = BluetoothDeviceFilter.Builder()
                .setAddress(device.address)  // Target this specific device
                .build()

            val associationRequest = AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(true)  // Auto-associate with this exact device
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ uses callback approach
                cdm.associate(
                    associationRequest,
                    mainExecutor,
                    object : CompanionDeviceManager.Callback() {
                        override fun onAssociationPending(intentSender: android.content.IntentSender) {
                            // System requires user confirmation - auto-launch it
                            android.util.Log.d("BluetoothCodec", "Association pending, auto-launching...")
                            try {
                                companionDeviceLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("BluetoothCodec", "Failed to launch association intent", e)
                            }
                        }

                        override fun onAssociationCreated(associationInfo: AssociationInfo) {
                            android.util.Log.d("BluetoothCodec", "Association created: ${associationInfo.deviceMacAddress}")
                            isDeviceAssociated = true
                            runOnUiThread {
                                handler.postDelayed({ updateCodecSpinner() }, 500)
                            }
                        }

                        override fun onFailure(error: CharSequence?) {
                            android.util.Log.e("BluetoothCodec", "Association failed: $error")
                            // Fail silently - don't show toast
                        }
                    }
                )
            } else {
                // Older Android uses deprecated associate method
                @Suppress("DEPRECATION")
                cdm.associate(
                    associationRequest,
                    object : CompanionDeviceManager.Callback() {
                        @Deprecated("Deprecated in Java")
                        override fun onDeviceFound(chooserLauncher: android.content.IntentSender) {
                            android.util.Log.d("BluetoothCodec", "Device found, auto-launching...")
                            try {
                                companionDeviceLauncher.launch(
                                    IntentSenderRequest.Builder(chooserLauncher).build()
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("BluetoothCodec", "Failed to launch chooser", e)
                            }
                        }

                        override fun onFailure(error: CharSequence?) {
                            android.util.Log.e("BluetoothCodec", "Association failed: $error")
                            // Fail silently - don't show toast
                        }
                    },
                    handler
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BluetoothCodec", "Failed to request association", e)
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
        
        // Check if permissions are granted
        if (!hasBluetoothPermissions()) {
            tvStatus.text = "Bluetooth permission required"
            ensurePermissions()
            return
        }

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
                ledSettingsChar = null
                soundCtrlChar = null
                soundDataChar = null
                otaCtrlChar = null
                otaDataChar = null
                isConnected = false
                // Reset sound status
                soundStatus = 0
                // Reset codec info
                currentCodecName = "Unknown"
                currentSampleRate = "Unknown"
                currentBitsPerSample = "Unknown"
                currentChannelMode = "Unknown"
                currentPlaybackQuality = "Unknown"
                runOnUiThread {
                    tvStatus.text = "Not Connected"
                    btnScanConnect.text = "Connect"
                    resetUiToDefaults()
                    // Hide codec card when BLE disconnects
                    codecCard.visibility = View.GONE
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
            ledSettingsChar = controlService?.getCharacteristic(charUuidLedSettings)
            soundCtrlChar = controlService?.getCharacteristic(charUuidSoundCtrl)
            soundDataChar = controlService?.getCharacteristic(charUuidSoundData)
            levelsChar  = levelsService?.getCharacteristic(charUuidLevels)
            otaCtrlChar = controlService?.getCharacteristic(charUuidOtaCtrl)
            otaDataChar = controlService?.getCharacteristic(charUuidOtaData)
            fwVerChar = controlService?.getCharacteristic(charUuidFwVer)

            // Log discovered characteristics
            android.util.Log.d("BLE", "Services discovered - soundCtrlChar=${soundCtrlChar != null}, soundDataChar=${soundDataChar != null}, otaDataChar=${otaDataChar != null}")

            levelsChar?.let { enableNotifications(gatt, it) }
            eqChar?.let { enableNotifications(gatt, it) }
            controlChar?.let { enableNotifications(gatt, it) }
            nameChar?.let { enableNotifications(gatt, it) }
            fwVerChar?.let { enableNotifications(gatt, it) }
            ledEffectChar?.let { enableNotifications(gatt, it) }
            ledSettingsChar?.let { enableNotifications(gatt, it) }
            soundCtrlChar?.let { enableNotifications(gatt, it) }

            // Receive PROG: notifications from ESP
            otaCtrlChar?.let { enableNotifications(gatt, it) }

            isConnected = true
            
            // Update codec spinner now that BLE is connected
            updateCodecSpinner()

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

            handler.postDelayed({
                bluetoothGatt?.let { current ->
                    if (current == gatt) {
                        ledSettingsChar?.let { readCharacteristicCompat(current, it) }
                    }
                }
            }, 550)

            handler.postDelayed({
                bluetoothGatt?.let { current ->
                    if (current == gatt) {
                        soundCtrlChar?.let { readCharacteristicCompat(current, it) }
                    }
                }
            }, 650)

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

                charUuidLedSettings -> {
                    handleLedSettingsData(raw)
                }

                charUuidSoundCtrl -> {
                    if (raw.isNotEmpty()) {
                        // Check if this is a sound upload ACK (0x81, 0x82, 0x83, or 0xE0)
                        val firstByte = raw[0].toInt() and 0xFF
                        if (firstByte >= 0x80) {
                            handleSoundUploadResponse(raw)
                        } else {
                            // Regular sound status update
                            soundStatus = firstByte
                            runOnUiThread {
                                deviceInfoBottomSheet?.updateSoundStatus(soundStatus)
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

                charUuidLedSettings -> {
                    handleLedSettingsData(raw)
                }

                charUuidSoundCtrl -> {
                    if (raw.isNotEmpty()) {
                        // Check if this is a sound upload ACK (0x81, 0x82, 0x83, or 0xE0)
                        val firstByte = raw[0].toInt() and 0xFF
                        if (firstByte >= 0x80) {
                            handleSoundUploadResponse(raw)
                        } else {
                            // Regular sound status update
                            soundStatus = firstByte
                            runOnUiThread {
                                deviceInfoBottomSheet?.updateSoundStatus(soundStatus)
                            }
                        }
                    }
                }
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            android.util.Log.d("Sound", "onCharacteristicWrite: uuid=${characteristic?.uuid}, status=$status")
            if (characteristic?.uuid == charUuidSoundData) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    android.util.Log.d("Sound", "Write complete, calling handleSoundWriteComplete")
                    handleSoundWriteComplete()
                } else {
                    android.util.Log.e("Sound", "onCharacteristicWrite failed: status=$status")
                    // Still signal completion so we don't hang
                    handleSoundWriteComplete()
                }
            }
        }
    }

    private fun handleLedSettingsData(raw: ByteArray) {
        if (raw.size < 10) return
        
        runOnUiThread {
            updatingLedSettings = true
            try {
                // Parse LED settings: [brightness, r1, g1, b1, r2, g2, b2, gradient, speed, effectId]
                currentLedBrightness = raw[0].toInt() and 0xFF
                val r1 = raw[1].toInt() and 0xFF
                val g1 = raw[2].toInt() and 0xFF
                val b1 = raw[3].toInt() and 0xFF
                val r2 = raw[4].toInt() and 0xFF
                val g2 = raw[5].toInt() and 0xFF
                val b2 = raw[6].toInt() and 0xFF
                currentGradientType = raw[7].toInt() and 0xFF
                currentLedSpeed = raw[8].toInt() and 0xFF
                // raw[9] is effectId, handled by LED effect characteristic
                
                currentLedColor1 = Color.rgb(r1, g1, b1)
                currentLedColor2 = Color.rgb(r2, g2, b2)
                
                // Update UI
                seekLedBrightness.progress = currentLedBrightness
                tvBrightnessValue.text = currentLedBrightness.toString()
                viewColor1.setBackgroundColor(currentLedColor1)
                viewColor2.setBackgroundColor(currentLedColor2)
                if (currentGradientType < gradientTypeNames.size) {
                    spinnerGradient.setSelection(currentGradientType)
                }
                seekLedSpeed.progress = currentLedSpeed
                tvSpeedValue.text = currentLedSpeed.toString()
                
                android.util.Log.d("LED", "LED settings: brightness=$currentLedBrightness, gradient=$currentGradientType, speed=$currentLedSpeed")
            } finally {
                updatingLedSettings = false
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
        
        // Reset codec info
        currentCodecName = "Unknown"
        currentSampleRate = "Unknown"
        currentBitsPerSample = "Unknown"
        currentChannelMode = "Unknown"
        currentPlaybackQuality = "Unknown"

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

    private fun sendLedSettings() {
        android.util.Log.d("LED", "sendLedSettings called: isConnected=$isConnected, ledSettingsChar=${ledSettingsChar != null}")
        if (!isConnected || ledSettingsChar == null || bluetoothGatt == null) {
            android.util.Log.w("LED", "Cannot send LED settings: isConnected=$isConnected, ledSettingsChar=${ledSettingsChar != null}, gatt=${bluetoothGatt != null}")
            return
        }

        // Pack LED settings: [brightness, r1, g1, b1, r2, g2, b2, gradient, speed, effectId]
        val currentEffectId = spinnerLedEffect.selectedItemPosition
        val value = byteArrayOf(
            currentLedBrightness.toByte(),
            Color.red(currentLedColor1).toByte(),
            Color.green(currentLedColor1).toByte(),
            Color.blue(currentLedColor1).toByte(),
            Color.red(currentLedColor2).toByte(),
            Color.green(currentLedColor2).toByte(),
            Color.blue(currentLedColor2).toByte(),
            currentGradientType.toByte(),
            currentLedSpeed.toByte(),
            currentEffectId.toByte()
        )
        
        android.util.Log.d("LED", "Sending LED settings: brightness=$currentLedBrightness, gradient=$currentGradientType, speed=$currentLedSpeed")

        ledSettingsChar?.let {
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
        
        android.util.Log.d("LED", "Sent LED settings: brightness=$currentLedBrightness, gradient=$currentGradientType")
    }

    /**
     * Send sound mute command to ESP32
     * @param muted true to mute, false to unmute
     */
    fun sendSoundMute(muted: Boolean) {
        android.util.Log.d("Sound", "sendSoundMute called: isConnected=$isConnected, soundCtrlChar=${soundCtrlChar != null}, muted=$muted")
        if (!isConnected || soundCtrlChar == null || bluetoothGatt == null) {
            android.util.Log.w("Sound", "Cannot send mute: isConnected=$isConnected, soundCtrlChar=${soundCtrlChar != null}")
            return
        }

        // Command format: [cmd=0 (mute), value (0=unmute, 1=mute)]
        val value = byteArrayOf(0x00, if (muted) 0x01 else 0x00)

        soundCtrlChar?.let {
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
        android.util.Log.d("Sound", "Sent sound mute: $muted")
    }

    /**
     * Delete a sound file on ESP32
     * @param soundType 0=startup, 1=pairing, 2=connected, 3=maxvol
     */
    fun sendDeleteSound(soundType: Int) {
        if (!isConnected || soundCtrlChar == null || bluetoothGatt == null) return

        // Command format: [cmd=1 (delete), soundType]
        val value = byteArrayOf(0x01, soundType.toByte())

        soundCtrlChar?.let {
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
        android.util.Log.d("Sound", "Sent delete sound: type=$soundType")
    }

    /**
     * Upload a sound file to ESP32 via BLE
     * @param soundType 0=startup, 1=pairing, 2=connected, 3=maxvol
     * @param data the sound file data (MP3)
     * @param onProgress callback with progress 0-100
     * @param onComplete callback when complete (success: Boolean)
     */
    
    // Sound upload state
    @Volatile private var soundUploadAck: ByteArray? = null
    private val soundUploadLock = Object()
    @Volatile private var soundWriteComplete: Boolean = false
    private val soundWriteLock = Object()
    
    fun uploadSoundFile(soundType: Int, data: ByteArray, onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        android.util.Log.d("Sound", "uploadSoundFile called: type=$soundType, size=${data.size}, connected=$isConnected, soundDataChar=${soundDataChar != null}, gatt=${bluetoothGatt != null}")
        
        if (!isConnected) {
            android.util.Log.e("Sound", "Upload failed: not connected")
            runOnUiThread { onComplete(false) }
            return
        }
        if (soundDataChar == null) {
            android.util.Log.e("Sound", "Upload failed: soundDataChar is null - characteristic not discovered")
            runOnUiThread { onComplete(false) }
            return
        }
        if (bluetoothGatt == null) {
            android.util.Log.e("Sound", "Upload failed: bluetoothGatt is null")
            runOnUiThread { onComplete(false) }
            return
        }

        // Max file size 200KB (WAV at 11025Hz mono = ~9 seconds)
        if (data.size > 200 * 1024) {
            android.util.Log.e("Sound", "Sound file too large: ${data.size} bytes (max 200KB)")
            runOnUiThread { onComplete(false) }
            return
        }

        Thread {
            try {
                val dataChar = soundDataChar ?: run {
                    android.util.Log.e("Sound", "Upload failed in thread: soundDataChar became null")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                val gatt = bluetoothGatt ?: run {
                    android.util.Log.e("Sound", "Upload failed in thread: gatt became null")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }

                // New protocol with ACKs:
                // START: [0x01][soundType][size(4)][reserved(4)]
                // DATA:  [0x02][seq(2)][len(2)][payload...]
                // END:   [0x03]
                // Responses via soundCtrl notification: 0x81=START_OK, 0x82+seq=ACK, 0x83=DONE, 0xE0+code=ERROR
                
                val totalSize = data.size
                
                // --- Send START packet ---
                val startPkt = ByteArray(10).apply {
                    this[0] = 0x01  // START
                    this[1] = soundType.toByte()
                    this[2] = (totalSize and 0xFF).toByte()
                    this[3] = ((totalSize shr 8) and 0xFF).toByte()
                    this[4] = ((totalSize shr 16) and 0xFF).toByte()
                    this[5] = ((totalSize shr 24) and 0xFF).toByte()
                    // Reserved bytes 6-9
                }
                
                soundUploadAck = null
                android.util.Log.d("Sound", "Sending START packet...")
                if (!writeSoundPacket(gatt, dataChar, startPkt)) {
                    android.util.Log.e("Sound", "Failed to write START packet")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                
                // Wait for START_OK (0x81)
                var ack = waitForSoundAck(5000)
                if (ack == null || ack.isEmpty() || ack[0] != 0x81.toByte()) {
                    android.util.Log.e("Sound", "No START_OK received: ${ack?.contentToString()}")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                android.util.Log.d("Sound", "Received START_OK")
                
                // --- Send DATA packets ---
                // ESP32 characteristic max is 512 bytes, so max packet = 512
                // Packet format: [type(1)][seq(2)][len(2)][payload...] = 5 + payload
                // So max payload = 512 - 5 = 507 bytes
                // Also consider MTU: usable MTU payload = MTU - 3 (ATT overhead)
                val mtuPayload = if (currentMtu > 0) currentMtu - 3 else 20
                val maxPayload = minOf(mtuPayload - 5, 507).coerceAtLeast(20)  // Cap at 507 (512-5)
                android.util.Log.d("Sound", "currentMtu=$currentMtu, mtuPayload=$mtuPayload, maxPayload=$maxPayload")
                
                var offset = 0
                var seq = 0
                
                while (offset < totalSize && isConnected) {
                    val chunkLen = minOf(maxPayload, totalSize - offset)
                    
                    val dataPkt = ByteArray(5 + chunkLen).apply {
                        this[0] = 0x02  // DATA
                        this[1] = (seq and 0xFF).toByte()
                        this[2] = ((seq shr 8) and 0xFF).toByte()
                        this[3] = (chunkLen and 0xFF).toByte()
                        this[4] = ((chunkLen shr 8) and 0xFF).toByte()
                        System.arraycopy(data, offset, this, 5, chunkLen)
                    }
                    
                    soundUploadAck = null
                    if (!writeSoundPacket(gatt, dataChar, dataPkt)) {
                        android.util.Log.e("Sound", "Failed to write DATA packet seq=$seq")
                        runOnUiThread { onComplete(false) }
                        return@Thread
                    }
                    
                    // Wait for ACK (0x82 + seq)
                    ack = waitForSoundAck(5000)
                    if (ack == null || ack.size < 3 || ack[0] != 0x82.toByte()) {
                        android.util.Log.e("Sound", "No DATA ACK for seq=$seq: ${ack?.contentToString()}")
                        runOnUiThread { onComplete(false) }
                        return@Thread
                    }
                    
                    val ackSeq = (ack[1].toInt() and 0xFF) or ((ack[2].toInt() and 0xFF) shl 8)
                    if (ackSeq != seq) {
                        android.util.Log.e("Sound", "ACK seq mismatch: got $ackSeq, expected $seq")
                        runOnUiThread { onComplete(false) }
                        return@Thread
                    }
                    
                    offset += chunkLen
                    seq++
                    
                    val progress = (offset * 100) / totalSize
                    runOnUiThread { onProgress(progress) }
                }
                
                // --- Send END packet ---
                val endPkt = byteArrayOf(0x03)
                soundUploadAck = null
                if (!writeSoundPacket(gatt, dataChar, endPkt)) {
                    android.util.Log.e("Sound", "Failed to write END packet")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                
                // Wait for DONE (0x83)
                ack = waitForSoundAck(10000)
                if (ack == null || ack.isEmpty() || ack[0] != 0x83.toByte()) {
                    android.util.Log.e("Sound", "No DONE received: ${ack?.contentToString()}")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                
                android.util.Log.d("Sound", "Sound upload complete: type=$soundType, size=$totalSize")
                runOnUiThread { onComplete(true) }

            } catch (e: Exception) {
                android.util.Log.e("Sound", "Sound upload error", e)
                runOnUiThread { onComplete(false) }
            }
        }.start()
    }
    
    @SuppressLint("MissingPermission")
    private fun writeSoundPacket(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        // Reset write complete flag
        synchronized(soundWriteLock) {
            soundWriteComplete = false
        }
        
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                char,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            char.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
        
        if (!success) {
            android.util.Log.e("Sound", "writeCharacteristic returned false")
            return false
        }
        
        android.util.Log.d("Sound", "writeCharacteristic queued, waiting for callback...")
        
        // Wait for onCharacteristicWrite callback
        val startTime = System.currentTimeMillis()
        synchronized(soundWriteLock) {
            while (!soundWriteComplete && isConnected) {
                val remaining = 5000L - (System.currentTimeMillis() - startTime)
                if (remaining <= 0) {
                    android.util.Log.e("Sound", "Write timeout waiting for callback after ${System.currentTimeMillis() - startTime}ms")
                    return false
                }
                try {
                    (soundWriteLock as Object).wait(remaining.coerceAtMost(100))
                } catch (e: InterruptedException) {
                    return false
                }
            }
        }
        
        android.util.Log.d("Sound", "Write callback received, soundWriteComplete=$soundWriteComplete")
        return soundWriteComplete
    }
    
    // Called from onCharacteristicWrite for soundDataChar
    private fun handleSoundWriteComplete() {
        android.util.Log.d("Sound", "handleSoundWriteComplete called")
        synchronized(soundWriteLock) {
            soundWriteComplete = true
            (soundWriteLock as Object).notifyAll()
        }
    }
    
    private fun waitForSoundAck(timeoutMs: Long): ByteArray? {
        val startTime = System.currentTimeMillis()
        synchronized(soundUploadLock) {
            while (soundUploadAck == null && isConnected) {
                val remaining = timeoutMs - (System.currentTimeMillis() - startTime)
                if (remaining <= 0) break
                try {
                    (soundUploadLock as Object).wait(remaining.coerceAtMost(100))
                } catch (e: InterruptedException) {
                    break
                }
            }
            return soundUploadAck
        }
    }
    
    // Called from onCharacteristicChanged when soundCtrlChar notification received
    private fun handleSoundUploadResponse(value: ByteArray) {
        if (value.isNotEmpty() && value[0].toInt() and 0x80 != 0) {
            // This is an upload response (0x81, 0x82, 0x83, or 0xE0)
            android.util.Log.d("Sound", "Upload response: ${value.contentToString()}")
            synchronized(soundUploadLock) {
                soundUploadAck = value
                (soundUploadLock as Object).notifyAll()
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

        // Unregister Bluetooth state receiver
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }

        // Unregister codec config receiver
        try {
            unregisterReceiver(codecConfigReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }

        // Close A2DP profile proxy
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp)
        bluetoothA2dp = null
    }
}




