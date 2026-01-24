package com.example.myspeaker

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.*

/**
 * BDK Audio - Main Control Activity (Redesigned)
 * 
 * Clean, minimalist interface with:
 * - Bottom navigation (Sound | LED tabs)
 * - Settings gear for advanced options
 */
class MainActivityRedesign : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "BDKAudioPrefs"
        
        // === Unified BLE Protocol UUIDs (matches ESP32 ble_unified.h) ===
        private val SERVICE_UUID_UNIFIED = BleUnifiedProtocol.SERVICE_UUID
        private val CHAR_UUID_CMD = BleUnifiedProtocol.CHAR_CMD_UUID
        private val CHAR_UUID_STATUS = BleUnifiedProtocol.CHAR_STATUS_UUID
        private val CHAR_UUID_METER = BleUnifiedProtocol.CHAR_METER_UUID
        
        // CCCD for notifications
        private val CCCD_UUID = BleUnifiedProtocol.CCCD_UUID
        
        // === Legacy UUIDs (kept for reference/fallback) ===
        private val SERVICE_UUID_CONTROL_LEGACY = UUID.fromString("12345678-1234-1234-1234-1234567890ad")
        private val CHAR_UUID_CONTROL_LEGACY = UUID.fromString("12345678-1234-1234-1234-1234567890ae")
        private val SERVICE_UUID_LEVELS_LEGACY = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
        private val CHAR_UUID_LEVELS_LEGACY = UUID.fromString("12345678-1234-1234-1234-1234567890ac")
        
        // LED effect ID for "Off" (brightness = 0)
        private const val LED_EFFECT_OFF = 255
        
        // LED effect ID for Ambient (for showing ambient controls)
        private const val LED_EFFECT_AMBIENT = 21
    }
    
    // LED effect names matching ESP32 enum order (from led_config.h LedEffectId)
    private val ledEffectNames = arrayOf(
        "Spectrum Bars",      // 0
        "Beat Pulse",         // 1
        "Ripple",             // 2
        "Fire",               // 3
        "Plasma",             // 4
        "Matrix Rain",        // 5
        "VU Meter",           // 6
        "Starfield",          // 7
        "Wave",               // 8
        "Fireworks",          // 9
        "Rainbow Wave",       // 10
        "Particle Burst",     // 11
        "Kaleidoscope",       // 12
        "Frequency Spiral",   // 13
        "Bass Reactor",       // 14
        "Meteor Shower",      // 15
        "Breathing",          // 16
        "DNA Helix",          // 17
        "Audio Scope",        // 18
        "Bouncing Balls",     // 19
        "Lava Lamp",          // 20
        "Ambient"             // 21
    )

    // UI Elements - Top Bar
    private lateinit var tvDeviceName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var btnSettings: ImageButton

    // UI Elements - Tabs
    private lateinit var tabSound: ScrollView
    private lateinit var tabLed: ScrollView
    private lateinit var navSound: LinearLayout
    private lateinit var navLed: LinearLayout
    private lateinit var navSoundIcon: ImageView
    private lateinit var navSoundText: TextView
    private lateinit var navLedIcon: ImageView
    private lateinit var navLedText: TextView

    // UI Elements - Sound Tab
    private lateinit var tvCurrentPreset: TextView
    private lateinit var tvCodecBadge: TextView
    private lateinit var bar30: ProgressBar
    private lateinit var bar60: ProgressBar
    private lateinit var bar100: ProgressBar
    private lateinit var tvVal30: TextView
    private lateinit var tvVal60: TextView
    private lateinit var tvVal100: TextView
    private lateinit var btnFineTune: LinearLayout
    private lateinit var fineTuneContent: LinearLayout
    private lateinit var tvFineTuneArrow: TextView
    private lateinit var seekBass: SeekBar
    private lateinit var seekMid: SeekBar
    private lateinit var seekTreble: SeekBar
    private lateinit var tvBassLabel: TextView
    private lateinit var tvMidLabel: TextView
    private lateinit var tvTrebleLabel: TextView

    // UI Elements - LED Tab
    private lateinit var tvCurrentEffect: TextView
    private var effectSelectorHeader: View? = null
    private var tvSelectedEffect: TextView? = null
    private var ivEffectDropdown: ImageView? = null
    private var effectListContainer: View? = null
    private var rvEffectList: androidx.recyclerview.widget.RecyclerView? = null
    private var ledEffectAdapter: LedEffectAdapter? = null
    private var isEffectListExpanded = false
    private lateinit var seekBrightness: SeekBar
    private lateinit var tvBrightness: TextView
    private lateinit var seekLedSpeed: SeekBar
    private lateinit var tvLedSpeed: TextView
    private lateinit var viewColor1: View
    private lateinit var viewColor2: View
    private lateinit var spinnerGradient: Spinner
    private var ambientControlsContainer: View? = null
    private var effectPreviewView: LedEffectPreviewView? = null
    
    // Quick select effect buttons for highlighting
    private val effectButtonMap = mutableMapOf<Int, FrameLayout>()  // effectId -> button
    
    // Reference to DeviceInfoBottomSheet for live updates
    private var deviceInfoBottomSheet: DeviceInfoBottomSheet? = null

    // Bluetooth - Unified Protocol (3 characteristics)
    private var bluetoothGatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null      // Write commands
    private var statusChar: BluetoothGattCharacteristic? = null   // Status notifications
    private var meterChar: BluetoothGattCharacteristic? = null    // Audio level notifications
    private var isConnected = false
    private var useUnifiedProtocol = true  // Flag to use new protocol (fallback to legacy if needed)
    
    // A2DP for codec control
    private var bluetoothA2dp: BluetoothA2dp? = null
    private var a2dpDevice: BluetoothDevice? = null

    // State
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var currentPresetId = 0
    private var currentEffectId = 0
    private var isFineTuneExpanded = false
    private var currentLedBrightness = 100
    private var currentLedSpeed = 50
    private var currentLedColor1 = Color.WHITE
    private var currentLedColor2 = Color.parseColor("#00AAFF")
    private var currentGradientType = 0
    private var updatingFromDevice = false  // Flag to prevent sending while receiving
    
    // Saved brightness before turning LED off (to restore when switching to other effect)
    private var savedBrightnessBeforeOff = 100
    
    // Saved ambient settings (to restore when switching back to ambient)
    private var savedAmbientSpeed = 50
    private var savedAmbientColor1 = Color.WHITE
    private var savedAmbientColor2 = Color.parseColor("#00AAFF")
    private var savedAmbientGradient = 0
    
    // Gradient type names matching ESP32 enum
    private val gradientTypeNames = arrayOf(
        "None",           // 0 - Solid color1
        "Horizontal",     // 1 - Left to right
        "Vertical",       // 2 - Top to bottom
        "Radial",         // 3 - Center outward
        "Diagonal"        // 4 - Corner to corner
    )
    
    // Device info state
    private var currentDeviceNameStr = "BDK SPEAKER"
    private var currentFirmwareVersion = "Unknown"
    private var currentCodecName = "Unknown"
    private var currentSampleRate = "Unknown"
    private var currentBitsPerSample = "Unknown"
    private var currentChannelMode = "Unknown"
    private var currentPlaybackQuality = "Unknown"
    private var soundStatus = 0
    
    // Control state
    private var bassBoostEnabled = false
    
    // OTA state
    private var currentMtu: Int = 23
    @Volatile private var isOtaInProgress: Boolean = false
    @Volatile private var otaWriteComplete: Boolean = false
    private val otaWriteLock = Object()
    @Volatile private var otaCheckResponse: String? = null
    private val otaCheckLock = Object()
    
    // Sound upload state
    @Volatile private var soundWriteComplete: Boolean = false
    private val soundWriteLock = Object()
    @Volatile private var soundUploadAck: ByteArray? = null
    private val soundUploadLock = Object()

    // A2DP profile listener for codec control
    @SuppressLint("MissingPermission")
    private val a2dpProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = proxy as? BluetoothA2dp
                // Get connected A2DP device
                bluetoothA2dp?.connectedDevices?.firstOrNull()?.let { device ->
                    a2dpDevice = device
                    updateCodecFromSystem()
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = null
                a2dpDevice = null
            }
        }
    }

    // Settings launcher
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val action = result.data?.getStringExtra("action")
            handleSettingsResult(action, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_redesign)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load saved control states
        bassBoostEnabled = prefs.getBoolean("bass_boost", false)
        channelFlipEnabled = prefs.getBoolean("channel_flip", false)
        bypassDspEnabled = prefs.getBoolean("bypass_dsp", false)
        currentCodecName = prefs.getString("current_codec", "Unknown") ?: "Unknown"
        currentFirmwareVersion = prefs.getString("firmware_version", "Unknown") ?: "Unknown"
        currentDeviceNameStr = prefs.getString("device_name", "BDK SPEAKER") ?: "BDK SPEAKER"
        
        initViews()
        setupListeners()
        setupEqPresets()
        setupLedEffects()
        
        // Connect to A2DP profile for codec control
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.getProfileProxy(this, a2dpProfileListener, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            android.util.Log.e("A2DP", "Failed to get A2DP proxy", e)
        }
        
        // Handle auto-connect from ConnectionActivity
        handleAutoConnect()
    }

    private fun initViews() {
        // Top Bar
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        btnSettings = findViewById(R.id.btnSettings)

        // Tabs
        tabSound = findViewById(R.id.tabSound)
        tabLed = findViewById(R.id.tabLed)
        navSound = findViewById(R.id.navSound)
        navLed = findViewById(R.id.navLed)
        navSoundIcon = findViewById(R.id.navSoundIcon)
        navSoundText = findViewById(R.id.navSoundText)
        navLedIcon = findViewById(R.id.navLedIcon)
        navLedText = findViewById(R.id.navLedText)

        // Sound Tab
        tvCurrentPreset = findViewById(R.id.tvCurrentPreset)
        tvCodecBadge = findViewById(R.id.tvCodecBadge)
        bar30 = findViewById(R.id.bar30)
        bar60 = findViewById(R.id.bar60)
        bar100 = findViewById(R.id.bar100)
        tvVal30 = findViewById(R.id.tvVal30)
        tvVal60 = findViewById(R.id.tvVal60)
        tvVal100 = findViewById(R.id.tvVal100)
        btnFineTune = findViewById(R.id.btnFineTune)
        fineTuneContent = findViewById(R.id.fineTuneContent)
        tvFineTuneArrow = findViewById(R.id.tvFineTuneArrow)
        seekBass = findViewById(R.id.seekBass)
        seekMid = findViewById(R.id.seekMid)
        seekTreble = findViewById(R.id.seekTreble)
        tvBassLabel = findViewById(R.id.tvBassLabel)
        tvMidLabel = findViewById(R.id.tvMidLabel)
        tvTrebleLabel = findViewById(R.id.tvTrebleLabel)

        // LED Tab
        tvCurrentEffect = findViewById(R.id.tvCurrentEffect)
        effectSelectorHeader = findViewById(R.id.effectSelectorHeader)
        tvSelectedEffect = findViewById(R.id.tvSelectedEffect)
        ivEffectDropdown = findViewById(R.id.ivEffectDropdown)
        effectListContainer = findViewById(R.id.effectListContainer)
        rvEffectList = findViewById(R.id.rvEffectList)
        seekBrightness = findViewById(R.id.seekBrightness)
        tvBrightness = findViewById(R.id.tvBrightness)
        seekLedSpeed = findViewById(R.id.seekLedSpeed)
        tvLedSpeed = findViewById(R.id.tvLedSpeed)
        viewColor1 = findViewById(R.id.viewColor1)
        viewColor2 = findViewById(R.id.viewColor2)
        spinnerGradient = findViewById(R.id.spinnerGradient)
        ambientControlsContainer = findViewById(R.id.ambientControlsContainer)
        effectPreviewView = findViewById(R.id.effectPreviewView)

        // Load saved device name and codec
        tvDeviceName.text = currentDeviceNameStr
        tvCodecBadge.text = currentCodecName
        
        // Setup LED effect spinner
        setupLedEffectSpinner()
        
        // Setup gradient spinner
        setupGradientSpinner()
        
        // Setup color previews
        updateColorPreview(viewColor1, currentLedColor1)
        updateColorPreview(viewColor2, currentLedColor2)
    }

    private fun setupListeners() {
        // Settings button
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            // Pass current values so SettingsActivity shows latest from ESP32
            intent.putExtra("bass_boost", bassBoostEnabled)
            intent.putExtra("bypass_dsp", bypassDspEnabled)
            intent.putExtra("channel_flip", channelFlipEnabled)
            intent.putExtra("device_name", currentDeviceNameStr)
            intent.putExtra("current_codec", currentCodecName)
            intent.putExtra("firmware_version", currentFirmwareVersion)
            
            // Register callback for immediate control updates
            SettingsActivity.onControlChanged = { bass, bypass, flip ->
                bassBoostEnabled = bass
                bypassDspEnabled = bypass
                channelFlipEnabled = flip
                sendControlByte()
            }
            
            // Register callback for codec selection
            SettingsActivity.onCodecSelected = { codecType ->
                val a2dp = bluetoothA2dp
                val device = a2dpDevice
                if (a2dp != null && device != null) {
                    try {
                        setCodecPreference(a2dp, device, codecType)
                        currentCodecName = when (codecType) {
                            0 -> "SBC"
                            1 -> "AAC"
                            2 -> "aptX"
                            3 -> "aptX HD"
                            4 -> "LDAC"
                            else -> "Unknown"
                        }
                        prefs.edit().putString("current_codec", currentCodecName).apply()
                        runOnUiThread {
                            tvCodecBadge.text = currentCodecName
                        }
                        // Refresh audio info after codec change
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            updateCodecFromSystem()
                        }, 1000)
                    } catch (e: Exception) {
                        android.util.Log.e("Codec", "Failed to set codec", e)
                    }
                }
            }
            
            // Provide device info data for showing bottom sheet in Settings
            val isConnected = bluetoothGatt != null
            SettingsActivity.deviceInfoData = SettingsActivity.DeviceInfoData(
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
            
            // Register callback for sound mute control
            SettingsActivity.onSoundMuteChanged = { muted ->
                sendSoundMute(muted)
            }
            
            // Register callback for sound delete
            SettingsActivity.onSoundDelete = { soundType ->
                sendDeleteSound(soundType)
            }
            
            // Register callback for sound upload
            SettingsActivity.onSoundUpload = { soundType, data, onProgress, onComplete ->
                uploadSoundFile(soundType, data, onProgress, onComplete)
            }
            
            settingsLauncher.launch(intent)
        }

        // Bottom navigation
        navSound.setOnClickListener { switchToTab(0) }
        navLed.setOnClickListener { switchToTab(1) }

        // Fine tune toggle
        btnFineTune.setOnClickListener {
            isFineTuneExpanded = !isFineTuneExpanded
            android.util.Log.d("EQ", "Fine tune clicked - expanded: $isFineTuneExpanded")
            fineTuneContent.visibility = if (isFineTuneExpanded) View.VISIBLE else View.GONE
            tvFineTuneArrow.text = if (isFineTuneExpanded) "▲" else "▼"
            android.util.Log.d("EQ", "fineTuneContent visibility: ${fineTuneContent.visibility}, height: ${fineTuneContent.height}")
        }

        // EQ Sliders
        setupSeekBar(seekBass, tvBassLabel, "Bass")
        setupSeekBar(seekMid, tvMidLabel, "Mid")
        setupSeekBar(seekTreble, tvTrebleLabel, "Treble")

        // Brightness slider
        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBrightness.text = progress.toString()
                if (fromUser) {
                    sendLedBrightness(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Speed slider
        seekLedSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvLedSpeed.text = progress.toString()
                if (fromUser && !updatingFromDevice) {
                    currentLedSpeed = progress
                    sendLedSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Color pickers
        viewColor1.setOnClickListener { showColorPicker(1) }
        viewColor2.setOnClickListener { showColorPicker(2) }
    }

    private fun setupSeekBar(seekBar: SeekBar, label: TextView, name: String) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 12
                label.text = "${if (db >= 0) "+" else ""}$db dB"
                if (fromUser) {
                    sendEq()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun switchToTab(tabIndex: Int) {
        val accentColor = ContextCompat.getColor(this, R.color.bdk_accent_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.bdk_text_tertiary)

        when (tabIndex) {
            0 -> {
                tabSound.visibility = View.VISIBLE
                tabLed.visibility = View.GONE
                navSoundIcon.setColorFilter(accentColor)
                navSoundText.setTextColor(accentColor)
                navLedIcon.setColorFilter(inactiveColor)
                navLedText.setTextColor(inactiveColor)
            }
            1 -> {
                tabSound.visibility = View.GONE
                tabLed.visibility = View.VISIBLE
                navSoundIcon.setColorFilter(inactiveColor)
                navSoundText.setTextColor(inactiveColor)
                navLedIcon.setColorFilter(accentColor)
                navLedText.setTextColor(accentColor)
            }
        }
    }

    private fun setupEqPresets() {
        val presets = listOf(
            Triple(R.id.presetBalanced, "Balanced", intArrayOf(50, 50, 50)),
            Triple(R.id.presetDeepBass, "Deep Bass", intArrayOf(85, 45, 40)),
            Triple(R.id.presetClearVocals, "Vocals", intArrayOf(40, 70, 55)),
            Triple(R.id.presetBrightClear, "Bright", intArrayOf(45, 55, 80)),
            Triple(R.id.presetPunchy, "Punchy", intArrayOf(75, 40, 70)),
            Triple(R.id.presetWarm, "Warm", intArrayOf(65, 55, 35)),
            Triple(R.id.presetStudio, "Studio", intArrayOf(50, 52, 50)),
            Triple(R.id.presetClub, "Club", intArrayOf(80, 45, 65)),
            Triple(R.id.presetGaming, "Gaming", intArrayOf(75, 55, 70))
        )

        presets.forEachIndexed { index, (id, name, values) ->
            findViewById<FrameLayout>(id)?.setOnClickListener {
                applyEqPreset(index, name, values[0], values[1], values[2])
            }
        }

        // More presets toggle
        val tvMorePresets = findViewById<TextView>(R.id.tvMorePresets)
        val extendedContainer = findViewById<LinearLayout>(R.id.extendedPresetsContainer)
        tvMorePresets?.setOnClickListener {
            if (extendedContainer?.visibility == View.VISIBLE) {
                extendedContainer.visibility = View.GONE
                tvMorePresets.text = "More presets ▼"
            } else {
                extendedContainer?.visibility = View.VISIBLE
                tvMorePresets.text = "Less presets ▲"
            }
        }

        // Custom presets
        findViewById<FrameLayout>(R.id.presetCustom1)?.setOnClickListener { loadCustomPreset(1) }
        findViewById<FrameLayout>(R.id.presetCustom1)?.setOnLongClickListener { saveCustomPreset(1); true }
        findViewById<FrameLayout>(R.id.presetCustom2)?.setOnClickListener { loadCustomPreset(2) }
        findViewById<FrameLayout>(R.id.presetCustom2)?.setOnLongClickListener { saveCustomPreset(2); true }
    }
    
    // Emoji map for effects
    private val effectEmojis = arrayOf(
        "📊", // 0 - Spectrum Bars
        "💓", // 1 - Beat Pulse
        "🌊", // 2 - Ripple
        "🔥", // 3 - Fire
        "🟣", // 4 - Plasma
        "💚", // 5 - Matrix Rain
        "📶", // 6 - VU Meter
        "⭐", // 7 - Starfield
        "〰️", // 8 - Wave
        "🎆", // 9 - Fireworks
        "🌈", // 10 - Rainbow Wave
        "💥", // 11 - Particle Burst
        "🔮", // 12 - Kaleidoscope
        "🌀", // 13 - Frequency Spiral
        "🎵", // 14 - Bass Reactor
        "☄️", // 15 - Meteor Shower
        "💨", // 16 - Breathing
        "🧬", // 17 - DNA Helix
        "📈", // 18 - Audio Scope
        "⚽", // 19 - Bouncing Balls
        "🫧", // 20 - Lava Lamp
        "✨", // 21 - Ambient
        "⭕"  // OFF
    )
    
    private fun setupLedEffectSpinner() {
        // Create effect items list including OFF
        val effectItems = ledEffectNames.mapIndexed { index, name ->
            LedEffectItem(index, name, effectEmojis.getOrElse(index) { "🎨" })
        } + LedEffectItem(LED_EFFECT_OFF, "Off", "⭕")
        
        // Setup RecyclerView with LinearLayoutManager
        rvEffectList?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        ledEffectAdapter = LedEffectAdapter(effectItems) { selectedEffect ->
            onEffectSelected(selectedEffect)
        }
        rvEffectList?.adapter = ledEffectAdapter
        
        // Header click toggles list visibility
        effectSelectorHeader?.setOnClickListener {
            toggleEffectList()
        }
    }
    
    private fun toggleEffectList() {
        isEffectListExpanded = !isEffectListExpanded
        
        effectListContainer?.visibility = if (isEffectListExpanded) View.VISIBLE else View.GONE
        
        // Rotate arrow
        ivEffectDropdown?.animate()
            ?.rotation(if (isEffectListExpanded) 180f else 0f)
            ?.setDuration(200)
            ?.start()
    }
    
    private fun onEffectSelected(effect: LedEffectItem) {
        val effectId = effect.id
        
        // Update UI
        tvSelectedEffect?.text = effect.name
        ledEffectAdapter?.setSelectedEffect(effectId)
        
        // Collapse list
        isEffectListExpanded = false
        effectListContainer?.visibility = View.GONE
        ivEffectDropdown?.animate()?.rotation(0f)?.setDuration(200)?.start()
        
        // Show/hide ambient controls
        updateAmbientControlsVisibility(effectId)
        
        if (!updatingFromDevice) {
            // Save ambient settings if switching away from ambient
            if (currentEffectId == LED_EFFECT_AMBIENT && effectId != LED_EFFECT_AMBIENT) {
                saveCurrentAmbientSettings()
            }
            
            // If switching to ambient, restore ambient settings
            if (effectId == LED_EFFECT_AMBIENT && currentEffectId != LED_EFFECT_AMBIENT) {
                restoreAmbientSettings()
            }
            
            applyLedEffect(effectId, effect.name)
            
            // Also send all LED settings after changing effect
            if (effectId != LED_EFFECT_AMBIENT) {
                sendLedSettings()
            }
        }
    }
    
    private fun updateAmbientControlsVisibility(effectPosition: Int) {
        // Show color/gradient controls only when Ambient effect is selected
        ambientControlsContainer?.visibility = if (effectPosition == LED_EFFECT_AMBIENT) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
    
    private fun setupGradientSpinner() {
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            gradientTypeNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 14f
                view.setPadding(16, 12, 16, 12)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(Color.parseColor("#1E1E1E"))
                view.textSize = 14f
                view.setPadding(24, 16, 24, 16)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGradient.adapter = adapter
        
        // Prevent initial selection from triggering listener
        updatingFromDevice = true
        spinnerGradient.setSelection(currentGradientType)
        
        spinnerGradient.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updatingFromDevice) {
                    currentGradientType = position
                    sendLedSettings()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Reset flag after a short delay to allow initial setup to complete
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updatingFromDevice = false
        }, 500)
    }
    
    private fun showColorPicker(colorIndex: Int) {
        val colors = arrayOf(
            "White" to Color.WHITE,
            "Red" to Color.RED,
            "Orange" to Color.parseColor("#FF6600"),
            "Yellow" to Color.YELLOW,
            "Green" to Color.GREEN,
            "Cyan" to Color.CYAN,
            "Blue" to Color.BLUE,
            "Purple" to Color.parseColor("#9900FF"),
            "Pink" to Color.parseColor("#FF00AA"),
            "Warm White" to Color.parseColor("#FFE4B5")
        )
        
        val colorNames = colors.map { it.first }.toTypedArray()
        val currentColor = if (colorIndex == 1) currentLedColor1 else currentLedColor2
        val currentIndex = colors.indexOfFirst { it.second == currentColor }.coerceAtLeast(0)
        
        android.app.AlertDialog.Builder(this, R.style.Theme_MySpeakerControl_Dialog)
            .setTitle(if (colorIndex == 1) "Primary Color" else "Secondary Color")
            .setSingleChoiceItems(colorNames, currentIndex) { dialog, which ->
                val selectedColor = colors[which].second
                if (colorIndex == 1) {
                    currentLedColor1 = selectedColor
                    updateColorPreview(viewColor1, selectedColor)
                } else {
                    currentLedColor2 = selectedColor
                    updateColorPreview(viewColor2, selectedColor)
                }
                sendLedSettings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateColorPreview(view: View, color: Int) {
        val drawable = view.background as? GradientDrawable ?: GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = 8 * resources.displayMetrics.density
        drawable.setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#333333"))
        view.background = drawable
    }

    private fun setupLedEffects() {
        // Quick select buttons with correct effect IDs
        val effects = listOf(
            Triple(R.id.effectSpectrumBars, "Spectrum Bars", 0),
            Triple(R.id.effectBeatPulse, "Beat Pulse", 1),
            Triple(R.id.effectBassReactor, "Bass Reactor", 14),
            Triple(R.id.effectRainbowWave, "Rainbow Wave", 10),
            Triple(R.id.effectAmbient, "Ambient", 21),
            Triple(R.id.effectOff, "Off", LED_EFFECT_OFF)
        )

        // Store button references for highlighting
        effects.forEach { (id, _, effectId) ->
            findViewById<FrameLayout>(id)?.let { button ->
                effectButtonMap[effectId] = button
            }
        }

        effects.forEach { (id, name, effectId) ->
            findViewById<FrameLayout>(id)?.setOnClickListener {
                if (effectId == LED_EFFECT_OFF) {
                    // Save current brightness before turning off
                    if (currentLedBrightness > 0) {
                        savedBrightnessBeforeOff = currentLedBrightness
                    }
                    // Save ambient settings if currently on ambient
                    if (currentEffectId == LED_EFFECT_AMBIENT) {
                        saveCurrentAmbientSettings()
                    }
                    // Update highlight for Off button
                    updateEffectButtonHighlight(LED_EFFECT_OFF)
                    // Turn off by setting brightness to 0
                    currentLedBrightness = 0
                    seekBrightness.progress = 0
                    tvBrightness.text = "0"
                    sendLedSettings()
                    tvCurrentEffect.text = "Off"
                } else {
                    // Save ambient settings if switching away from ambient
                    if (currentEffectId == LED_EFFECT_AMBIENT && effectId != LED_EFFECT_AMBIENT) {
                        saveCurrentAmbientSettings()
                    }
                    
                    // Restore brightness if it was off
                    val wasOff = currentLedBrightness == 0
                    if (wasOff) {
                        // Restore the saved brightness instead of setting to 100
                        currentLedBrightness = savedBrightnessBeforeOff
                        seekBrightness.progress = currentLedBrightness
                        tvBrightness.text = currentLedBrightness.toString()
                    }
                    
                    // If switching to ambient, restore ambient settings (which handles sending)
                    if (effectId == LED_EFFECT_AMBIENT) {
                        restoreAmbientSettings()
                        applyLedEffect(effectId, name)
                    } else {
                        // For non-ambient: send effect first, then settings after delay
                        applyLedEffect(effectId, name)
                        // Delay settings write to avoid BLE write conflicts
                        handler.postDelayed({
                            sendLedSettings()
                            android.util.Log.d("LED", "Sent settings after effect change: brightness=$currentLedBrightness, effect=$effectId")
                        }, 150)
                    }
                    
                    // Also update the expandable list to match
                    updatingFromDevice = true
                    tvSelectedEffect?.text = name
                    ledEffectAdapter?.setSelectedEffect(effectId)
                    updatingFromDevice = false
                }
            }
        }
    }
    
    private fun saveCurrentAmbientSettings() {
        savedAmbientSpeed = currentLedSpeed
        savedAmbientColor1 = currentLedColor1
        savedAmbientColor2 = currentLedColor2
        savedAmbientGradient = currentGradientType
        android.util.Log.d("LED", "Saved ambient: speed=$savedAmbientSpeed, gradient=$savedAmbientGradient")
    }
    
    private fun restoreAmbientSettings() {
        currentLedSpeed = savedAmbientSpeed
        currentLedColor1 = savedAmbientColor1
        currentLedColor2 = savedAmbientColor2
        currentGradientType = savedAmbientGradient
        
        // Update UI
        updatingFromDevice = true
        seekLedSpeed.progress = currentLedSpeed
        tvLedSpeed.text = currentLedSpeed.toString()
        updateColorPreview(viewColor1, currentLedColor1)
        updateColorPreview(viewColor2, currentLedColor2)
        if (currentGradientType < gradientTypeNames.size) {
            spinnerGradient.setSelection(currentGradientType)
        }
        updatingFromDevice = false
        
        android.util.Log.d("LED", "Restored ambient: speed=$currentLedSpeed, gradient=$currentGradientType, color1=$currentLedColor1")
        
        // Send the restored settings to ESP32 after a short delay to ensure effect ID is set
        handler.postDelayed({
            sendLedSettings()
            android.util.Log.d("LED", "Sent ambient settings to ESP32")
        }, 100)
    }
    
    private fun updateEffectButtonHighlight(selectedEffectId: Int) {
        effectButtonMap.forEach { (effectId, button) ->
            if (effectId == selectedEffectId) {
                // Selected - apply accent border highlight
                button.setBackgroundResource(R.drawable.bdk_effect_selected_bg)
            } else {
                // Not selected - use normal background
                button.setBackgroundResource(R.drawable.bdk_preset_item_bg)
            }
        }
        
        // Update the live effect preview background
        updateEffectPreviewBackground(selectedEffectId)
    }
    
    private fun updateEffectPreviewBackground(effectId: Int) {
        // Update the live animated preview view with the current effect
        effectPreviewView?.setEffect(effectId)
    }

    private fun applyEqPreset(presetId: Int, name: String, bass: Int, mid: Int, treble: Int) {
        currentPresetId = presetId
        tvCurrentPreset.text = name

        // Convert 0-100 to dB range
        val bassDb = ((bass - 50) * 12 / 50).coerceIn(-12, 12)
        val midDb = ((mid - 50) * 12 / 50).coerceIn(-12, 12)
        val trebleDb = ((treble - 50) * 12 / 50).coerceIn(-12, 12)

        seekBass.progress = bassDb + 12
        seekMid.progress = midDb + 12
        seekTreble.progress = trebleDb + 12

        tvBassLabel.text = "${if (bassDb >= 0) "+" else ""}$bassDb dB"
        tvMidLabel.text = "${if (midDb >= 0) "+" else ""}$midDb dB"
        tvTrebleLabel.text = "${if (trebleDb >= 0) "+" else ""}$trebleDb dB"

        sendEq()
        // Toast removed for cleaner UX
    }

    private fun applyLedEffect(effectId: Int, name: String) {
        currentEffectId = effectId
        tvCurrentEffect.text = name
        sendLedEffect(effectId)
        // Update quick select button highlights
        updateEffectButtonHighlight(effectId)
        // Toast removed for cleaner UX
    }

    private fun loadCustomPreset(slot: Int) {
        // Check if anything is saved in this slot
        val hasSaved = prefs.contains("custom_${slot}_bass")
        
        if (!hasSaved) {
            Toast.makeText(this, "Custom $slot: Nothing saved\nLong press to save current settings", Toast.LENGTH_SHORT).show()
            return
        }
        
        val bass = prefs.getInt("custom_${slot}_bass", 50)
        val mid = prefs.getInt("custom_${slot}_mid", 50)
        val treble = prefs.getInt("custom_${slot}_treble", 50)
        val name = prefs.getString("custom_${slot}_name", "Custom $slot") ?: "Custom $slot"
        
        applyEqPreset(100 + slot, name, bass, mid, treble)
    }

    private fun saveCustomPreset(slot: Int) {
        val bass = ((seekBass.progress - 12) * 50 / 12) + 50
        val mid = ((seekMid.progress - 12) * 50 / 12) + 50
        val treble = ((seekTreble.progress - 12) * 50 / 12) + 50
        
        // Check if something is already saved
        val hasSaved = prefs.contains("custom_${slot}_bass")
        
        if (hasSaved) {
            // Show confirmation dialog
            android.app.AlertDialog.Builder(this, R.style.Theme_MySpeakerControl_Dialog)
                .setTitle("Override Custom $slot?")
                .setMessage("Do you want to override the current saved settings?")
                .setPositiveButton("Yes") { _, _ ->
                    doSaveCustomPreset(slot, bass, mid, treble)
                }
                .setNegativeButton("No", null)
                .show()
        } else {
            doSaveCustomPreset(slot, bass, mid, treble)
        }
    }
    
    private fun doSaveCustomPreset(slot: Int, bass: Int, mid: Int, treble: Int) {
        prefs.edit()
            .putInt("custom_${slot}_bass", bass)
            .putInt("custom_${slot}_mid", mid)
            .putInt("custom_${slot}_treble", treble)
            .apply()
        
        Toast.makeText(this, "Saved to Custom $slot", Toast.LENGTH_SHORT).show()
    }

    // === BLE Communication ===

    @SuppressLint("MissingPermission")
    private fun handleAutoConnect() {
        val autoConnect = intent.getBooleanExtra("auto_connect", false)
        val deviceAddress = intent.getStringExtra("device_address")
        val deviceName = intent.getStringExtra("device_name")

        if (autoConnect && deviceAddress != null) {
            tvDeviceName.text = deviceName ?: "BDK SPEAKER"
            tvStatus.text = "Connecting..."
            
            prefs.edit().putString("device_name", deviceName).apply()
            
            handler.postDelayed({
                connectToDevice(deviceAddress)
            }, 500)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(address: String) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = bluetoothManager.adapter?.getRemoteDevice(address) ?: return
        
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        isConnected = true
                        OtaActivity.sharedIsConnected = true
                        updateConnectionStatus(true)
                        // Request large MTU for faster OTA (same as old MainActivity)
                        if (gatt != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            gatt.requestMtu(517)
                        }
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        OtaActivity.sharedIsConnected = false
                        OtaActivity.sharedGatt = null
                        OtaActivity.sharedCmdChar = null
                        updateConnectionStatus(false)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Try Unified Protocol first (new 3-characteristic architecture)
                val unifiedService = gatt?.getService(SERVICE_UUID_UNIFIED)
                
                if (unifiedService != null) {
                    // Found unified service - use new protocol
                    useUnifiedProtocol = true
                    cmdChar = unifiedService.getCharacteristic(CHAR_UUID_CMD)
                    statusChar = unifiedService.getCharacteristic(CHAR_UUID_STATUS)
                    meterChar = unifiedService.getCharacteristic(CHAR_UUID_METER)
                    
                    android.util.Log.d("BLE", "Using UNIFIED protocol - cmdChar: ${cmdChar != null}, statusChar: ${statusChar != null}, meterChar: ${meterChar != null}")
                    
                    // Enable notifications on STATUS and METER characteristics
                    var notifyDelay = 50L
                    statusChar?.let { 
                        handler.postDelayed({ enableNotifications(gatt!!, it) }, notifyDelay)
                        notifyDelay += 100 
                    }
                    meterChar?.let { 
                        handler.postDelayed({ enableNotifications(gatt!!, it) }, notifyDelay)
                        notifyDelay += 100 
                    }
                    
                    // Request full status from device
                    handler.postDelayed({
                        sendUnifiedCommand(BleUnifiedProtocol.buildRequestStatus())
                    }, notifyDelay + 200)
                    
                    // Share for OTA
                    OtaActivity.sharedGatt = gatt
                    OtaActivity.sharedCmdChar = cmdChar
                    
                    runOnUiThread {
                        if (cmdChar != null && statusChar != null) {
                            Toast.makeText(this@MainActivityRedesign, "Connected (Unified)", Toast.LENGTH_SHORT).show()
                            updateCodecFromSystem()
                        } else {
                            Toast.makeText(this@MainActivityRedesign, "Error: Unified characteristics not found", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    // Fallback to legacy protocol (won't work with new ESP32 firmware)
                    useUnifiedProtocol = false
                    android.util.Log.w("BLE", "Unified service not found - device may have old firmware")
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivityRedesign, "Error: Device has incompatible firmware", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        @Deprecated("use onCharacteristicRead(gatt, characteristic, value, status) instead")
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) return
            @Suppress("DEPRECATION")
            val raw = characteristic.value ?: return
            handleCharacteristicData(characteristic.uuid, raw)
        }
        
        @Deprecated("use onCharacteristicChanged(gatt, characteristic, value) instead")
        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic == null) return
            @Suppress("DEPRECATION")
            val raw = characteristic.value ?: return
            handleCharacteristicData(characteristic.uuid, raw)
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
                OtaActivity.sharedMtu = mtu
                android.util.Log.d("BLE", "MTU changed to: $mtu")
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            // Handle CMD write ACK for both local and OtaActivity (unified protocol)
            if (characteristic?.uuid == CHAR_UUID_CMD) {
                val success = status == BluetoothGatt.GATT_SUCCESS
                handleOtaWriteComplete(success)
                OtaActivity.handleOtaWriteComplete(success)
                // Sound write complete always needs to be signaled so the wait doesn't hang
                handleSoundWriteComplete()
            }
        }
    }
    
    private fun handleCharacteristicData(uuid: UUID, raw: ByteArray) {
        // === UNIFIED PROTOCOL ===
        when (uuid) {
            CHAR_UUID_STATUS -> {
                handleUnifiedStatusNotification(raw)
            }
            CHAR_UUID_METER -> {
                handleUnifiedMeterNotification(raw)
            }
        }
    }
    
    // Handle STATUS characteristic notifications (unified protocol)
    private fun handleUnifiedStatusNotification(raw: ByteArray) {
        if (raw.isEmpty()) return
        
        val respId = raw[0]
        val payload = if (raw.size > 1) raw.sliceArray(1 until raw.size) else ByteArray(0)
        
        android.util.Log.d("BLE", "STATUS notification: respId=0x${String.format("%02X", respId)}, len=${payload.size}")
        
        when (respId) {
            BleUnifiedProtocol.Resp.STATUS_EQ -> {
                BleUnifiedProtocol.parseStatusEq(payload)?.let { eq ->
                    runOnUiThread {
                        updatingFromDevice = true
                        try {
                            seekBass.progress = eq.bass + 12
                            seekMid.progress = eq.mid + 12
                            seekTreble.progress = eq.treble + 12
                            
                            tvBassLabel.text = "${if (eq.bass >= 0) "+" else ""}${eq.bass} dB"
                            tvMidLabel.text = "${if (eq.mid >= 0) "+" else ""}${eq.mid} dB"
                            tvTrebleLabel.text = "${if (eq.treble >= 0) "+" else ""}${eq.treble} dB"
                        } finally {
                            updatingFromDevice = false
                        }
                    }
                }
            }
            BleUnifiedProtocol.Resp.STATUS_CONTROL -> {
                if (payload.isNotEmpty()) {
                    val b = payload[0].toInt() and 0xFF
                    bassBoostEnabled = (b and 0x01) != 0
                    channelFlipEnabled = (b and 0x02) != 0
                    bypassDspEnabled = (b and 0x04) != 0
                    
                    prefs.edit()
                        .putBoolean("bass_boost", bassBoostEnabled)
                        .putBoolean("channel_flip", channelFlipEnabled)
                        .putBoolean("bypass_dsp", bypassDspEnabled)
                        .apply()
                }
            }
            BleUnifiedProtocol.Resp.STATUS_NAME -> {
                val name = String(payload, Charsets.UTF_8).trim()
                currentDeviceNameStr = name
                runOnUiThread {
                    tvDeviceName.text = name
                    prefs.edit().putString("device_name", name).apply()
                }
            }
            BleUnifiedProtocol.Resp.STATUS_FW -> {
                val ver = String(payload, Charsets.UTF_8).trim()
                currentFirmwareVersion = ver
                android.util.Log.d("BLE", "Firmware version: $ver")
            }
            BleUnifiedProtocol.Resp.STATUS_LED -> {
                BleUnifiedProtocol.parseStatusLed(payload)?.let { led ->
                    runOnUiThread {
                        updatingFromDevice = true
                        try {
                            // Update effect
                            currentEffectId = led.effectId
                            if (led.effectId < ledEffectNames.size) {
                                tvSelectedEffect?.text = ledEffectNames[led.effectId]
                                ledEffectAdapter?.setSelectedEffect(led.effectId)
                                tvCurrentEffect.text = ledEffectNames[led.effectId]
                            } else if (led.effectId == LED_EFFECT_OFF) {
                                tvSelectedEffect?.text = "Off"
                                ledEffectAdapter?.setSelectedEffect(led.effectId)
                                tvCurrentEffect.text = "Off"
                            }
                            updateEffectButtonHighlight(led.effectId)
                            
                            // Update brightness
                            currentLedBrightness = led.brightness
                            seekBrightness.progress = led.brightness
                            tvBrightness.text = led.brightness.toString()
                            
                            // Update speed
                            currentLedSpeed = led.speed
                            seekLedSpeed.progress = led.speed
                            tvLedSpeed.text = led.speed.toString()
                            
                            // Update colors
                            currentLedColor1 = Color.rgb(led.r1, led.g1, led.b1)
                            updateColorPreview(viewColor1, currentLedColor1)
                            currentLedColor2 = Color.rgb(led.r2, led.g2, led.b2)
                            updateColorPreview(viewColor2, currentLedColor2)
                            
                            // Update gradient
                            currentGradientType = led.gradient
                            if (led.gradient < gradientTypeNames.size) {
                                spinnerGradient.setSelection(led.gradient)
                            }
                            
                            android.util.Log.d("LED", "Synced: effect=${led.effectId}, brightness=${led.brightness}, speed=${led.speed}")
                        } finally {
                            updatingFromDevice = false
                        }
                    }
                }
            }
            BleUnifiedProtocol.Resp.STATUS_SOUND -> {
                if (payload.isNotEmpty()) {
                    soundStatus = payload[0].toInt() and 0xFF
                    android.util.Log.d("Sound", "Updated soundStatus to: $soundStatus")
                    runOnUiThread {
                        deviceInfoBottomSheet?.updateSoundStatus(soundStatus)
                    }
                }
            }
            BleUnifiedProtocol.Resp.ACK_OK -> {
                if (payload.isNotEmpty()) {
                    android.util.Log.d("BLE", "ACK_OK for cmd: 0x${String.format("%02X", payload[0])}")
                }
            }
            BleUnifiedProtocol.Resp.ACK_ERROR -> {
                if (payload.size >= 2) {
                    android.util.Log.e("BLE", "ACK_ERROR for cmd: 0x${String.format("%02X", payload[0])}, error: 0x${String.format("%02X", payload[1])}")
                }
            }
            BleUnifiedProtocol.Resp.OTA_PROGRESS -> {
                if (payload.isNotEmpty()) {
                    val percent = payload[0].toInt() and 0xFF
                    android.util.Log.d("OTA", "Progress: $percent%")
                    // OTA progress handled by upload thread
                }
            }
            BleUnifiedProtocol.Resp.OTA_READY -> {
                android.util.Log.d("OTA", "OTA_READY received")
                // Device is ready for OTA - handled by upload thread
            }
            BleUnifiedProtocol.Resp.OTA_COMPLETE -> {
                android.util.Log.d("OTA", "OTA_COMPLETE received")
                // OTA completion handled by upload thread
            }
            BleUnifiedProtocol.Resp.OTA_FAILED -> {
                val errorCode = if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0
                android.util.Log.e("OTA", "OTA_FAILED with error: $errorCode")
                // OTA failure handled by upload thread
            }
            BleUnifiedProtocol.Resp.SOUND_PROGRESS -> {
                if (payload.isNotEmpty()) {
                    val percent = payload[0].toInt() and 0xFF
                    handleSoundUploadProgress(percent)
                }
            }
            BleUnifiedProtocol.Resp.SOUND_READY -> {
                handleSoundUploadReady()
            }
            BleUnifiedProtocol.Resp.SOUND_COMPLETE -> {
                handleSoundUploadComplete()
            }
            BleUnifiedProtocol.Resp.SOUND_FAILED -> {
                val errorCode = if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0
                handleSoundUploadFailed(errorCode)
            }
            BleUnifiedProtocol.Resp.FULL_STATUS -> {
                BleUnifiedProtocol.parseFullStatus(payload)?.let { status ->
                    runOnUiThread {
                        updatingFromDevice = true
                        try {
                            // Update EQ
                            seekBass.progress = status.eq.bass + 12
                            seekMid.progress = status.eq.mid + 12
                            seekTreble.progress = status.eq.treble + 12
                            tvBassLabel.text = "${if (status.eq.bass >= 0) "+" else ""}${status.eq.bass} dB"
                            tvMidLabel.text = "${if (status.eq.mid >= 0) "+" else ""}${status.eq.mid} dB"
                            tvTrebleLabel.text = "${if (status.eq.treble >= 0) "+" else ""}${status.eq.treble} dB"
                            
                            // Update control
                            val b = status.controlByte
                            bassBoostEnabled = (b and 0x01) != 0
                            channelFlipEnabled = (b and 0x02) != 0
                            bypassDspEnabled = (b and 0x04) != 0
                            
                            // Update LED
                            currentEffectId = status.led.effectId
                            if (status.led.effectId < ledEffectNames.size) {
                                tvSelectedEffect?.text = ledEffectNames[status.led.effectId]
                                tvCurrentEffect.text = ledEffectNames[status.led.effectId]
                            }
                            currentLedBrightness = status.led.brightness
                            seekBrightness.progress = status.led.brightness
                            tvBrightness.text = status.led.brightness.toString()
                            currentLedSpeed = status.led.speed
                            seekLedSpeed.progress = status.led.speed
                            tvLedSpeed.text = status.led.speed.toString()
                            currentLedColor1 = Color.rgb(status.led.r1, status.led.g1, status.led.b1)
                            updateColorPreview(viewColor1, currentLedColor1)
                            currentLedColor2 = Color.rgb(status.led.r2, status.led.g2, status.led.b2)
                            updateColorPreview(viewColor2, currentLedColor2)
                            currentGradientType = status.led.gradient
                            
                            // Update sound
                            soundStatus = status.soundStatus
                            
                            // Update name
                            currentDeviceNameStr = status.deviceName
                            tvDeviceName.text = status.deviceName
                            prefs.edit().putString("device_name", status.deviceName).apply()
                            
                            // Update firmware
                            currentFirmwareVersion = status.firmwareVersion
                            
                            android.util.Log.d("BLE", "Full status synced: name=${status.deviceName}, fw=${status.firmwareVersion}")
                        } finally {
                            updatingFromDevice = false
                        }
                    }
                }
            }
            BleUnifiedProtocol.Resp.PONG -> {
                android.util.Log.d("BLE", "PONG received")
            }
        }
    }
    
    // Handle METER characteristic notifications (audio levels)
    private fun handleUnifiedMeterNotification(raw: ByteArray) {
        if (raw.size < 3) return
        
        val l30 = raw[0].toInt() and 0xFF
        val l60 = raw[1].toInt() and 0xFF
        val l100 = raw[2].toInt() and 0xFF
        
        runOnUiThread {
            bar30.progress = l30.coerceIn(0, 120)
            bar60.progress = l60.coerceIn(0, 120)
            bar100.progress = l100.coerceIn(0, 120)
            
            tvVal30.text = "$l30 dB"
            tvVal60.text = "$l60 dB"
            tvVal100.text = "$l100 dB"
        }
    }
    
    // Placeholder methods for sound upload (to be implemented)
    private fun handleSoundUploadProgress(percent: Int) {
        android.util.Log.d("Sound", "Upload progress: $percent%")
    }
    
    private fun handleSoundUploadReady() {
        android.util.Log.d("Sound", "Ready for next chunk")
        // Set the ack so upload thread can continue
        synchronized(soundUploadLock) {
            soundUploadAck = byteArrayOf(BleUnifiedProtocol.Resp.SOUND_READY)
            (soundUploadLock as Object).notifyAll()
        }
    }
    
    private fun handleSoundUploadComplete() {
        android.util.Log.d("Sound", "Upload complete!")
        // Set the ack so upload thread knows it's complete
        synchronized(soundUploadLock) {
            soundUploadAck = byteArrayOf(BleUnifiedProtocol.Resp.SOUND_COMPLETE)
            (soundUploadLock as Object).notifyAll()
        }
        runOnUiThread {
            Toast.makeText(this, "Sound upload complete!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleSoundUploadFailed(errorCode: Int) {
        android.util.Log.e("Sound", "Upload failed with error: $errorCode")
        // Set the ack so upload thread knows it failed
        synchronized(soundUploadLock) {
            soundUploadAck = byteArrayOf(BleUnifiedProtocol.Resp.SOUND_FAILED, errorCode.toByte())
            (soundUploadLock as Object).notifyAll()
        }
        runOnUiThread {
            Toast.makeText(this, "Sound upload failed: $errorCode", Toast.LENGTH_LONG).show()
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(ch, true)
        val descriptor = ch.getDescriptor(CCCD_UUID)
        descriptor?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(it)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun readCharacteristic(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        gatt.readCharacteristic(ch)
    }

    private fun updateConnectionStatus(connected: Boolean) {
        if (connected) {
            tvStatus.text = "Connected"
            statusDot.setBackgroundResource(R.drawable.bdk_status_dot_connected)
        } else {
            tvStatus.text = "Disconnected"
            statusDot.setBackgroundResource(R.drawable.bdk_status_dot)
        }
    }
    
    // === Unified BLE Protocol Send ===
    
    @SuppressLint("MissingPermission")
    private fun sendUnifiedCommand(data: ByteArray) {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) {
            android.util.Log.w("BLE", "Cannot send command: isConnected=$isConnected, cmdChar=${cmdChar != null}")
            return
        }
        
        android.util.Log.d("BLE", "Sending command: ${data.joinToString(" ") { String.format("%02X", it) }}")
        
        cmdChar?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    it,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                it.value = data
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendEq() {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) {
            android.util.Log.w("BLE", "Cannot send EQ: isConnected=$isConnected, cmdChar=${cmdChar != null}")
            return
        }

        val bass = seekBass.progress - 12
        val mid = seekMid.progress - 12
        val treble = seekTreble.progress - 12

        sendUnifiedCommand(BleUnifiedProtocol.buildSetEq(bass, mid, treble))
    }

    @SuppressLint("MissingPermission")
    private fun sendLedEffect(effectId: Int) {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) {
            android.util.Log.w("BLE", "Cannot send LED effect: isConnected=$isConnected, cmdChar=${cmdChar != null}")
            return
        }

        sendUnifiedCommand(BleUnifiedProtocol.buildSetLedEffect(effectId))
    }

    @SuppressLint("MissingPermission")
    private fun sendLedBrightness(brightness: Int) {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) {
            android.util.Log.w("BLE", "Cannot send LED brightness: isConnected=$isConnected, cmdChar=${cmdChar != null}")
            return
        }
        
        currentLedBrightness = brightness
        sendLedSettings()
    }
    
    @SuppressLint("MissingPermission")
    private fun sendLedSettings() {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) return

        sendUnifiedCommand(BleUnifiedProtocol.buildSetLed(
            effectId = currentEffectId,
            brightness = currentLedBrightness,
            speed = currentLedSpeed,
            r1 = Color.red(currentLedColor1),
            g1 = Color.green(currentLedColor1),
            b1 = Color.blue(currentLedColor1),
            r2 = Color.red(currentLedColor2),
            g2 = Color.green(currentLedColor2),
            b2 = Color.blue(currentLedColor2),
            gradient = currentGradientType
        ))
    }

    // === Settings Result Handler ===

    // Control state flags
    private var bypassDspEnabled = false
    private var channelFlipEnabled = false

    private fun handleSettingsResult(action: String?, data: Intent?) {
        when (action) {
            "show_device_info" -> {
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
            "show_firmware_update" -> {
                // Show OTA bottom sheet using existing BLE connection
                showOtaBottomSheet()
            }
            "show_app_info" -> {
                AppInfoBottomSheet().show(supportFragmentManager, AppInfoBottomSheet.TAG)
            }
            "toggle_bass_boost" -> {
                bassBoostEnabled = data?.getBooleanExtra("enabled", false) ?: false
                sendControlByte()
            }
            "toggle_bypass_dsp" -> {
                bypassDspEnabled = data?.getBooleanExtra("enabled", false) ?: false
                sendControlByte()
            }
            "toggle_channel_flip" -> {
                channelFlipEnabled = data?.getBooleanExtra("enabled", false) ?: false
                sendControlByte()
            }
            "rename_device" -> {
                val newName = data?.getStringExtra("new_name") ?: return
                tvDeviceName.text = newName
                currentDeviceNameStr = newName
                prefs.edit().putString("device_name", newName).apply()
                sendDeviceName(newName)
            }
        }
    }
    
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun updateCodecFromSystem() {
        val a2dp = bluetoothA2dp ?: return
        val device = a2dpDevice ?: return
        
        try {
            val getCodecStatusMethod = a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val codecStatus = getCodecStatusMethod.invoke(a2dp, device)
            
            if (codecStatus != null) {
                val getCodecConfigMethod = codecStatus.javaClass.getMethod("getCodecConfig")
                val codecConfig = getCodecConfigMethod.invoke(codecStatus)
                
                if (codecConfig != null) {
                    // Get codec type
                    val getCodecTypeMethod = codecConfig.javaClass.getMethod("getCodecType")
                    val codecType = getCodecTypeMethod.invoke(codecConfig) as Int
                    
                    currentCodecName = when (codecType) {
                        0 -> "SBC"
                        1 -> "AAC"
                        2 -> "aptX"
                        3 -> "aptX HD"
                        4 -> "LDAC"
                        else -> "Unknown"
                    }
                    
                    // Get sample rate
                    try {
                        val getSampleRateMethod = codecConfig.javaClass.getMethod("getSampleRate")
                        val sampleRate = getSampleRateMethod.invoke(codecConfig) as Int
                        currentSampleRate = when (sampleRate) {
                            0x01 -> "44.1 kHz"
                            0x02 -> "48 kHz"
                            0x04 -> "88.2 kHz"
                            0x08 -> "96 kHz"
                            0x10 -> "176.4 kHz"
                            0x20 -> "192 kHz"
                            else -> "$sampleRate Hz"
                        }
                    } catch (e: Exception) {
                        currentSampleRate = "Unknown"
                    }
                    
                    // Get bits per sample
                    try {
                        val getBitsPerSampleMethod = codecConfig.javaClass.getMethod("getBitsPerSample")
                        val bitsPerSample = getBitsPerSampleMethod.invoke(codecConfig) as Int
                        currentBitsPerSample = when (bitsPerSample) {
                            0x01 -> "16 bit"
                            0x02 -> "24 bit"
                            0x04 -> "32 bit"
                            else -> "$bitsPerSample bit"
                        }
                    } catch (e: Exception) {
                        currentBitsPerSample = "Unknown"
                    }
                    
                    // Get channel mode
                    try {
                        val getChannelModeMethod = codecConfig.javaClass.getMethod("getChannelMode")
                        val channelMode = getChannelModeMethod.invoke(codecConfig) as Int
                        currentChannelMode = when (channelMode) {
                            0x01 -> "Mono"
                            0x02 -> "Stereo"
                            else -> "Unknown"
                        }
                    } catch (e: Exception) {
                        currentChannelMode = "Unknown"
                    }
                    
                    // Determine playback quality based on codec and sample rate
                    currentPlaybackQuality = when {
                        codecType == 4 && currentSampleRate.contains("96") -> "Hi-Res"
                        codecType == 4 -> "High"
                        codecType == 3 -> "High"
                        codecType == 2 -> "Standard"
                        codecType == 1 -> "Standard"
                        else -> "Standard"
                    }
                    
                    prefs.edit().putString("current_codec", currentCodecName).apply()
                    runOnUiThread {
                        tvCodecBadge.text = currentCodecName
                    }
                    
                    android.util.Log.d("Codec", "Audio info: $currentCodecName, $currentSampleRate, $currentBitsPerSample, $currentChannelMode, $currentPlaybackQuality")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Codec", "Failed to get codec status", e)
        }
    }
    
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun showCodecPicker() {
        val a2dp = bluetoothA2dp
        val device = a2dpDevice
        
        // Get available codecs from system
        val codecs = mutableListOf<Pair<String, Int>>()
        codecs.add(Pair("SBC", 0))
        codecs.add(Pair("AAC", 1))
        codecs.add(Pair("aptX", 2))
        codecs.add(Pair("aptX HD", 3))
        codecs.add(Pair("LDAC", 4))
        
        val codecNames = codecs.map { it.first }.toTypedArray()
        val currentIndex = codecs.indexOfFirst { it.first == currentCodecName }.coerceAtLeast(0)
        
        android.app.AlertDialog.Builder(this, R.style.Theme_MySpeakerControl_Dialog)
            .setTitle("Select Bluetooth Codec")
            .setSingleChoiceItems(codecNames, currentIndex) { dialog, which ->
                val selectedCodec = codecs[which]
                
                if (a2dp != null && device != null) {
                    try {
                        setCodecPreference(a2dp, device, selectedCodec.second)
                        currentCodecName = selectedCodec.first
                        prefs.edit().putString("current_codec", currentCodecName).apply()
                        tvCodecBadge.text = currentCodecName
                        Toast.makeText(this, "Codec changed to: $currentCodecName", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("Codec", "Failed to set codec", e)
                        Toast.makeText(this, "Failed to change codec", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "A2DP not connected", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun setCodecPreference(a2dp: BluetoothA2dp, device: BluetoothDevice, codecType: Int) {
        try {
            // Build codec config using reflection
            val codecConfigClass = Class.forName("android.bluetooth.BluetoothCodecConfig")
            val constructor = codecConfigClass.getDeclaredConstructor(
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
            
            val codecConfig = constructor.newInstance(
                codecType,
                1000000,  // highest priority
                0,        // inherit sample rate
                0,        // inherit bits per sample
                0,        // inherit channel mode
                0L, 0L, 0L, 0L  // codec specifics
            )
            
            // Set codec preference
            val setCodecMethod = a2dp.javaClass.getMethod(
                "setCodecConfigPreference",
                BluetoothDevice::class.java,
                codecConfigClass
            )
            setCodecMethod.invoke(a2dp, device, codecConfig)
            
            android.util.Log.d("Codec", "Codec preference set to type $codecType")
        } catch (e: Exception) {
            android.util.Log.e("Codec", "Failed to set codec preference", e)
            throw e
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun sendDeviceName(name: String) {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) return
        
        sendUnifiedCommand(BleUnifiedProtocol.buildSetName(name))
    }
    
    @SuppressLint("MissingPermission")
    private fun sendControlByte() {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) return

        var v = 0
        if (bassBoostEnabled) v = v or 0x01
        if (channelFlipEnabled) v = v or 0x02
        if (bypassDspEnabled) v = v or 0x04

        sendUnifiedCommand(BleUnifiedProtocol.buildSetControl(v))
    }
    
    // === Sound Control Functions (for DeviceInfoBottomSheet) ===
    
    @SuppressLint("MissingPermission")
    fun sendSoundMute(muted: Boolean) {
        android.util.Log.d("Sound", "sendSoundMute called: isConnected=$isConnected, cmdChar=${cmdChar != null}, muted=$muted")
        if (!isConnected || cmdChar == null || bluetoothGatt == null) {
            android.util.Log.w("Sound", "Cannot send mute: isConnected=$isConnected, cmdChar=${cmdChar != null}")
            return
        }
        
        sendUnifiedCommand(BleUnifiedProtocol.buildSoundMute(muted))
        android.util.Log.d("Sound", "Sent sound mute: $muted")
    }
    
    @SuppressLint("MissingPermission")
    fun sendDeleteSound(soundType: Int) {
        if (!isConnected || cmdChar == null || bluetoothGatt == null) return
        
        sendUnifiedCommand(BleUnifiedProtocol.buildSoundDelete(soundType))
        android.util.Log.d("Sound", "Sent delete sound: type=$soundType")
    }
    
    fun getSoundStatus(): Int = soundStatus
    
    // ================== SOUND UPLOAD FUNCTIONS ==================
    
    fun uploadSoundFile(soundType: Int, data: ByteArray, onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        android.util.Log.d("Sound", "uploadSoundFile called: type=$soundType, size=${data.size}, connected=$isConnected, cmdChar=${cmdChar != null}")
        
        // Validate sound type (0-3)
        if (soundType < 0 || soundType > 3) {
            android.util.Log.e("Sound", "Upload failed: invalid soundType=$soundType (must be 0-3)")
            runOnUiThread { onComplete(false) }
            return
        }
        
        if (!isConnected) {
            android.util.Log.e("Sound", "Upload failed: not connected")
            runOnUiThread { onComplete(false) }
            return
        }
        if (cmdChar == null) {
            android.util.Log.e("Sound", "Upload failed: cmdChar is null")
            runOnUiThread { onComplete(false) }
            return
        }
        if (bluetoothGatt == null) {
            android.util.Log.e("Sound", "Upload failed: bluetoothGatt is null")
            runOnUiThread { onComplete(false) }
            return
        }

        // Max file size 200KB
        if (data.size > 200 * 1024) {
            android.util.Log.e("Sound", "Sound file too large: ${data.size} bytes (max 200KB)")
            runOnUiThread { onComplete(false) }
            return
        }

        Thread {
            try {
                val localCmdChar = cmdChar ?: run {
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                val gatt = bluetoothGatt ?: run {
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }

                val totalSize = data.size
                
                // --- Send START packet using unified protocol ---
                val startCmd = BleUnifiedProtocol.buildSoundUploadStart(soundType, totalSize)
                
                android.util.Log.d("Sound", "Sending SOUND_UP_START: type=$soundType, size=$totalSize")
                
                soundUploadAck = null
                if (!writeSoundPacket(gatt, localCmdChar, startCmd)) {
                    android.util.Log.e("Sound", "Failed to write START packet")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                
                // Wait for SOUND_READY (0x31)
                var ack = waitForSoundAck(5000)
                if (ack == null || ack.isEmpty() || ack[0] != BleUnifiedProtocol.Resp.SOUND_READY) {
                    android.util.Log.e("Sound", "No SOUND_READY received: ${ack?.contentToString()}")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                android.util.Log.d("Sound", "Received SOUND_READY")
                
                // --- Send DATA packets ---
                val mtuPayload = if (currentMtu > 0) currentMtu - 3 else 20
                val maxPayload = minOf(mtuPayload - 3, 507).coerceAtLeast(20)  // -3 for cmd+seq overhead
                
                var offset = 0
                var seq = 0
                
                while (offset < totalSize && isConnected) {
                    val chunkLen = minOf(maxPayload, totalSize - offset)
                    val chunkData = data.sliceArray(offset until offset + chunkLen)
                    
                    val dataCmd = BleUnifiedProtocol.buildSoundUploadData(seq and 0xFF, chunkData)
                    
                    soundUploadAck = null
                    if (!writeSoundPacket(gatt, localCmdChar, dataCmd)) {
                        android.util.Log.e("Sound", "Failed to write DATA packet seq=$seq")
                        runOnUiThread { onComplete(false) }
                        return@Thread
                    }
                    
                    // Wait for SOUND_READY (0x31) for next chunk
                    ack = waitForSoundAck(5000)
                    if (ack == null || ack.isEmpty() || ack[0] != BleUnifiedProtocol.Resp.SOUND_READY) {
                        android.util.Log.e("Sound", "No DATA ACK for seq=$seq: ${ack?.contentToString()}")
                        runOnUiThread { onComplete(false) }
                        return@Thread
                    }
                    
                    offset += chunkLen
                    seq++
                    
                    val progress = (offset * 100) / totalSize
                    runOnUiThread { onProgress(progress) }
                }
                
                // --- Send END packet ---
                val endCmd = BleUnifiedProtocol.buildSoundUploadEnd()
                soundUploadAck = null
                if (!writeSoundPacket(gatt, localCmdChar, endCmd)) {
                    android.util.Log.e("Sound", "Failed to write END packet")
                    runOnUiThread { onComplete(false) }
                    return@Thread
                }
                
                // Wait for SOUND_COMPLETE (0x32)
                ack = waitForSoundAck(10000)
                if (ack == null || ack.isEmpty() || ack[0] != BleUnifiedProtocol.Resp.SOUND_COMPLETE) {
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
        synchronized(soundWriteLock) {
            soundWriteComplete = false
        }
        
        val success = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
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
        
        // Wait for onCharacteristicWrite callback
        val startTime = System.currentTimeMillis()
        synchronized(soundWriteLock) {
            while (!soundWriteComplete && isConnected) {
                val remaining = 5000L - (System.currentTimeMillis() - startTime)
                if (remaining <= 0) {
                    android.util.Log.e("Sound", "Write timeout")
                    return false
                }
                try {
                    (soundWriteLock as Object).wait(remaining.coerceAtMost(100))
                } catch (e: InterruptedException) {
                    return false
                }
            }
        }
        
        return soundWriteComplete
    }
    
    private fun handleSoundWriteComplete() {
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
    
    private fun handleSoundUploadResponse(value: ByteArray) {
        if (value.isNotEmpty()) {
            val firstByte = value[0].toInt() and 0xFF
            if ((firstByte and 0xF0) == 0xA0 || (firstByte and 0xF0) == 0xE0) {
                android.util.Log.d("Sound", "Upload response: ${value.contentToString()}")
                synchronized(soundUploadLock) {
                    soundUploadAck = value
                    (soundUploadLock as Object).notifyAll()
                }
            }
        }
    }

    // ================== OTA FUNCTIONS ==================
    
    private fun handleOtaWriteComplete(success: Boolean) {
        synchronized(otaWriteLock) {
            otaWriteComplete = success
            (otaWriteLock as Object).notifyAll()
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun writeCharSimple(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, data: ByteArray) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
        
        val success = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
        
        val startTime = System.currentTimeMillis()
        synchronized(otaWriteLock) {
            while (!otaWriteComplete && isConnected) {
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
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
    
    /**
     * Start OTA update with firmware data.
     * Uses the existing BLE connection with unified protocol.
     */
    @SuppressLint("MissingPermission")
    fun startOtaWithData(
        firmwareData: ByteArray,
        onProgress: (Int, String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val gatt = bluetoothGatt
        val localCmdChar = cmdChar
        
        if (!isConnected || gatt == null || localCmdChar == null) {
            onComplete(false, "Not connected to device")
            return
        }
        
        if (isOtaInProgress) {
            onComplete(false, "OTA already in progress")
            return
        }
        
        isOtaInProgress = true
        
        Thread {
            try {
                val fileSize = firmwareData.size
                
                runOnUiThread { onProgress(0, "Starting OTA (${fileSize / 1024} KB)...") }
                
                // Send OTA_BEGIN command using unified protocol
                val beginCmd = BleUnifiedProtocol.buildOtaBegin(fileSize)
                writeCharSimple(gatt, localCmdChar, beginCmd)
                Thread.sleep(300)
                
                if (!isConnected) {
                    throw Exception("Disconnected during OTA")
                }
                
                // Stream firmware with batched ACK using unified protocol
                val maxPayload = (currentMtu - 3 - 3).coerceIn(20, 508)  // -3 for BLE overhead, -3 for cmd+seq
                val ackEveryN = 8
                val delayMs = 2L
                var chunkCount = 0
                var offset = 0
                var lastUiUpdate = 0L
                
                while (offset < firmwareData.size) {
                    if (!isConnected) {
                        throw Exception("Disconnected during OTA")
                    }
                    
                    val remaining = firmwareData.size - offset
                    val chunkSize = minOf(remaining, maxPayload)
                    val chunkData = firmwareData.copyOfRange(offset, offset + chunkSize)
                    val dataCmd = BleUnifiedProtocol.buildOtaData(chunkCount and 0xFF, chunkData)
                    chunkCount++
                    
                    val useAck = (chunkCount % ackEveryN == 0)
                    
                    var writeSuccess = false
                    var retryCount = 0
                    while (!writeSuccess && retryCount < 10) {
                        writeSuccess = if (useAck) {
                            writeOtaDataWithAck(gatt, localCmdChar, dataCmd)
                        } else {
                            writeOtaDataNoResponse(gatt, localCmdChar, dataCmd)
                        }
                        if (!writeSuccess) {
                            retryCount++
                            Thread.sleep(if (retryCount < 3) 10L else 30L)
                        }
                    }
                    
                    if (!writeSuccess) {
                        throw Exception("Write failed after 10 retries")
                    }
                    
                    if (!useAck) {
                        Thread.sleep(delayMs)
                    }
                    
                    offset += chunkSize
                    
                    // UI update every ~8KB
                    if (offset - lastUiUpdate >= 8192 || offset >= firmwareData.size) {
                        lastUiUpdate = offset.toLong()
                        val percent = ((offset * 100) / firmwareData.size).coerceIn(0, 100)
                        runOnUiThread { 
                            onProgress(percent, "Sending: ${offset / 1024} / ${firmwareData.size / 1024} KB ($percent%)") 
                        }
                    }
                }
                
                // Send OTA END command
                val endCmd = BleUnifiedProtocol.buildOtaEnd()
                writeCharSimple(gatt, localCmdChar, endCmd)
                
                runOnUiThread { onProgress(100, "Waiting for device...") }
                Thread.sleep(1000)
                
                isOtaInProgress = false
                runOnUiThread { onComplete(true, "Update complete! Device will restart.") }
                
            } catch (e: Exception) {
                isOtaInProgress = false
                runOnUiThread { onComplete(false, "OTA Error: ${e.message}") }
            }
        }.start()
    }
    
    private fun showOtaBottomSheet() {
        // Share BLE connection with OtaActivity
        OtaActivity.sharedGatt = bluetoothGatt
        OtaActivity.sharedCmdChar = cmdChar
        OtaActivity.sharedMtu = currentMtu
        OtaActivity.sharedIsConnected = isConnected
        
        // Launch OtaActivity with firmware version
        val intent = android.content.Intent(this, OtaActivity::class.java)
        intent.putExtra("firmware_version", currentFirmwareVersion)
        startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
        
        // Close A2DP proxy
        bluetoothA2dp?.let {
            try {
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothManager.adapter?.closeProfileProxy(BluetoothProfile.A2DP, it)
            } catch (e: Exception) {
                android.util.Log.e("A2DP", "Failed to close A2DP proxy", e)
            }
        }
        bluetoothA2dp = null
        
        // Clear settings callbacks
        SettingsActivity.onControlChanged = null
        SettingsActivity.onCodecSelected = null
        SettingsActivity.onSoundMuteChanged = null
        SettingsActivity.onSoundDelete = null
        SettingsActivity.onSoundUpload = null
    }
}
