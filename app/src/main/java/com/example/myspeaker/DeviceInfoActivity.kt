package com.example.myspeaker

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DeviceInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Device Info"

        val tvDeviceName = findViewById<TextView>(R.id.tvDeviceName)
        val tvFirmwareVersion = findViewById<TextView>(R.id.tvFirmwareVersion)
        val tvConnectionStatus = findViewById<TextView>(R.id.tvConnectionStatus)
        val statusIndicator = findViewById<View>(R.id.statusIndicator)
        
        // Audio info views
        val tvCodecName = findViewById<TextView>(R.id.tvCodecName)
        val tvSampleRate = findViewById<TextView>(R.id.tvSampleRate)
        val tvBitsPerSample = findViewById<TextView>(R.id.tvBitsPerSample)
        val tvChannelMode = findViewById<TextView>(R.id.tvChannelMode)
        val tvPlaybackQuality = findViewById<TextView>(R.id.tvPlaybackQuality)

        // Read values that MainActivity put into the Intent
        val isConnected = intent.getBooleanExtra("is_connected", false)
        val deviceName = if (isConnected) intent.getStringExtra("device_name") ?: "Unknown" else "Unknown"
        val fwVersion  = if (isConnected) intent.getStringExtra("fw_version") ?: "Unknown" else "Unknown"
        
        // Read codec info
        val codecName = intent.getStringExtra("codec_name") ?: "Unknown"
        val sampleRate = intent.getStringExtra("sample_rate") ?: "Unknown"
        val bitsPerSample = intent.getStringExtra("bits_per_sample") ?: "Unknown"
        val channelMode = intent.getStringExtra("channel_mode") ?: "Unknown"
        val playbackQuality = intent.getStringExtra("playback_quality") ?: "Unknown"

        tvDeviceName.text = deviceName
        tvFirmwareVersion.text = fwVersion
        
        // Set audio info
        tvCodecName.text = codecName
        tvSampleRate.text = sampleRate
        tvBitsPerSample.text = bitsPerSample
        tvChannelMode.text = channelMode
        tvPlaybackQuality.text = playbackQuality

        // Update connection status based on is_connected flag
        if (isConnected) {
            tvConnectionStatus.text = "Connected"
            tvConnectionStatus.setTextColor(getColor(R.color.success))
            statusIndicator.setBackgroundResource(R.drawable.status_dot_connected)
        } else {
            tvConnectionStatus.text = "Disconnected"
            tvConnectionStatus.setTextColor(getColor(R.color.error))
            statusIndicator.setBackgroundResource(R.drawable.status_dot_disconnected)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
