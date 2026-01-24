package com.example.myspeaker

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    companion object {
        // Callback for immediate control updates
        var onControlChanged: ((bassBoost: Boolean, bypassDsp: Boolean, channelFlip: Boolean) -> Unit)? = null
        // Callback for codec selection - returns selected codec type (0-4)
        var onCodecSelected: ((codecType: Int) -> Unit)? = null
        // Callback for sound mute control
        var onSoundMuteChanged: ((muted: Boolean) -> Unit)? = null
        // Callback for sound delete
        var onSoundDelete: ((soundType: Int) -> Unit)? = null
        // Callback for sound upload - returns (soundType, wavData, onProgress, onComplete)
        var onSoundUpload: ((soundType: Int, data: ByteArray, onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) -> Unit)? = null
        // Currently available codecs (name to type mapping)
        var availableCodecs: List<Pair<String, Int>> = listOf(
            Pair("SBC", 0),
            Pair("AAC", 1),
            Pair("aptX", 2),
            Pair("aptX HD", 3),
            Pair("LDAC", 4)
        )
        
        // Device info data for showing bottom sheet in Settings
        var deviceInfoData: DeviceInfoData? = null
    }
    
    data class DeviceInfoData(
        val isConnected: Boolean,
        val deviceName: String,
        val fwVersion: String,
        val codecName: String,
        val sampleRate: String,
        val bitsPerSample: String,
        val channelMode: String,
        val playbackQuality: String,
        val soundStatus: Int
    )

    private var switchBassBoost: MaterialSwitch? = null
    private var switchBypassDsp: MaterialSwitch? = null
    private var switchChannelFlip: MaterialSwitch? = null
    private var etDeviceName: EditText? = null
    private var tvCurrentCodec: TextView? = null
    private var tvAppVersion: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupListeners()
        loadSettings()
    }

    private fun initViews() {
        switchBassBoost = findViewById(R.id.switchBassBoost)
        switchBypassDsp = findViewById(R.id.switchBypassDsp)
        switchChannelFlip = findViewById(R.id.switchChannelFlip)
        etDeviceName = findViewById(R.id.etDeviceName)
        tvCurrentCodec = findViewById(R.id.tvCurrentCodec)
        tvAppVersion = findViewById(R.id.tvAppVersion)

        // Set app version
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion?.text = "v${pInfo.versionName}"
        } catch (e: Exception) {
            tvAppVersion?.text = "v1.0.0"
        }
    }

    private fun setupListeners() {
        // Back button
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        // Connect to Other Device
        findViewById<LinearLayout>(R.id.btnOtherDevice)?.setOnClickListener {
            val intent = Intent(this, ConnectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        // Apply Device Name
        findViewById<Button>(R.id.btnApplyName)?.setOnClickListener {
            val newName = etDeviceName?.text?.toString()?.trim() ?: return@setOnClickListener
            if (newName.isNotEmpty()) {
                // Send to MainActivity to apply via BLE
                val resultIntent = Intent()
                resultIntent.putExtra("action", "rename_device")
                resultIntent.putExtra("new_name", newName)
                setResult(RESULT_OK, resultIntent)
                Toast.makeText(this, "Name will be applied", Toast.LENGTH_SHORT).show()
            }
        }

        // Device Info - show bottom sheet directly, stay in settings
        findViewById<LinearLayout>(R.id.btnDeviceInfo)?.setOnClickListener {
            showDeviceInfoBottomSheet()
        }

        // Bluetooth Codec - show picker dialog, stay in settings
        findViewById<LinearLayout>(R.id.btnBluetoothCodec)?.setOnClickListener {
            showCodecPicker()
        }

        // Bass Boost toggle
        switchBassBoost?.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("BDKAudioPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("bass_boost", isChecked).apply()
            
            // Call callback immediately to send to ESP32
            onControlChanged?.invoke(
                isChecked,
                switchBypassDsp?.isChecked ?: false,
                switchChannelFlip?.isChecked ?: false
            )
            
            val resultIntent = Intent()
            resultIntent.putExtra("action", "toggle_bass_boost")
            resultIntent.putExtra("enabled", isChecked)
            setResult(RESULT_OK, resultIntent)
        }

        // Bypass DSP toggle
        switchBypassDsp?.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("BDKAudioPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("bypass_dsp", isChecked).apply()
            
            // Call callback immediately to send to ESP32
            onControlChanged?.invoke(
                switchBassBoost?.isChecked ?: false,
                isChecked,
                switchChannelFlip?.isChecked ?: false
            )
            
            val resultIntent = Intent()
            resultIntent.putExtra("action", "toggle_bypass_dsp")
            resultIntent.putExtra("enabled", isChecked)
            setResult(RESULT_OK, resultIntent)
        }

        // Channel Flip toggle
        switchChannelFlip?.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("BDKAudioPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("channel_flip", isChecked).apply()
            
            // Call callback immediately to send to ESP32
            onControlChanged?.invoke(
                switchBassBoost?.isChecked ?: false,
                switchBypassDsp?.isChecked ?: false,
                isChecked
            )
            
            val resultIntent = Intent()
            resultIntent.putExtra("action", "toggle_channel_flip")
            resultIntent.putExtra("enabled", isChecked)
            setResult(RESULT_OK, resultIntent)
        }

        // Firmware Update
        findViewById<LinearLayout>(R.id.btnFirmwareUpdate)?.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("action", "show_firmware_update")
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        // App Info
        findViewById<LinearLayout>(R.id.btnAppInfo)?.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("action", "show_app_info")
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("BDKAudioPrefs", MODE_PRIVATE)
        
        // Load toggle states from intent first (latest from ESP32), then fallback to prefs
        val bassBoost = intent.getBooleanExtra("bass_boost", prefs.getBoolean("bass_boost", false))
        val bypassDsp = intent.getBooleanExtra("bypass_dsp", prefs.getBoolean("bypass_dsp", false))
        val channelFlip = intent.getBooleanExtra("channel_flip", prefs.getBoolean("channel_flip", false))
        
        switchBassBoost?.isChecked = bassBoost
        switchBypassDsp?.isChecked = bypassDsp
        switchChannelFlip?.isChecked = channelFlip
        
        // Load device name from intent or prefs
        val deviceName = intent.getStringExtra("device_name") 
            ?: prefs.getString("device_name", "BDK SPEAKER")
        etDeviceName?.setText(deviceName)
        
        // Load current codec from intent or prefs
        val codec = intent.getStringExtra("current_codec") 
            ?: prefs.getString("current_codec", "Unknown")
        tvCurrentCodec?.text = codec
    }
    
    private fun showCodecPicker() {
        val codecs = availableCodecs
        val codecNames = codecs.map { it.first }.toTypedArray()
        val currentCodec = tvCurrentCodec?.text?.toString() ?: "Unknown"
        val currentIndex = codecs.indexOfFirst { it.first == currentCodec }.coerceAtLeast(0)
        
        android.app.AlertDialog.Builder(this, R.style.Theme_MySpeakerControl_Dialog)
            .setTitle("Select Bluetooth Codec")
            .setSingleChoiceItems(codecNames, currentIndex) { dialog, which ->
                val selectedCodec = codecs[which]
                
                // Call callback to have MainActivity handle the A2DP codec change
                onCodecSelected?.invoke(selectedCodec.second)
                
                // Update UI immediately (MainActivity will also update via A2DP)
                tvCurrentCodec?.text = selectedCodec.first
                
                // Save preference
                val prefs = getSharedPreferences("BDKAudioPrefs", MODE_PRIVATE)
                prefs.edit().putString("current_codec", selectedCodec.first).apply()
                
                Toast.makeText(this, "Codec: ${selectedCodec.first}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeviceInfoBottomSheet() {
        val data = deviceInfoData
        if (data == null) {
            Toast.makeText(this, "Device info not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val bottomSheet = DeviceInfoBottomSheet.newInstance(
            isConnected = data.isConnected,
            deviceName = data.deviceName,
            fwVersion = data.fwVersion,
            codecName = data.codecName,
            sampleRate = data.sampleRate,
            bitsPerSample = data.bitsPerSample,
            channelMode = data.channelMode,
            playbackQuality = data.playbackQuality,
            soundStatus = data.soundStatus
        )
        bottomSheet.show(supportFragmentManager, DeviceInfoBottomSheet.TAG)
    }
}
