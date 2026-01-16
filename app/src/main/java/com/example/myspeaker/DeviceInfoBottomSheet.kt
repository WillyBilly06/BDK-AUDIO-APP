package com.example.myspeaker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch

class DeviceInfoBottomSheet : BottomSheetDialogFragment() {

    private var tvDeviceName: TextView? = null
    private var tvFirmwareVersion: TextView? = null
    private var tvConnectionStatus: TextView? = null
    private var statusIndicator: View? = null
    private var tvCodecName: TextView? = null
    private var tvSampleRate: TextView? = null
    private var tvBitsPerSample: TextView? = null
    private var tvChannelMode: TextView? = null
    private var tvPlaybackQuality: TextView? = null

    // Sound controls
    private var switchMuteSound: MaterialSwitch? = null
    private var btnUploadStartup: Button? = null
    private var btnUploadPairing: Button? = null
    private var btnUploadConnected: Button? = null
    private var btnUploadMaxVol: Button? = null
    private var tvStartupStatus: TextView? = null
    private var tvPairingStatus: TextView? = null
    private var tvConnectedStatus: TextView? = null
    private var tvMaxVolStatus: TextView? = null
    private var progressUpload: ProgressBar? = null

    // Track which sound type we're uploading
    private var pendingSoundType: Int = -1

    // Guard to avoid feedback loops
    private var updatingFromDevice = false

    // File picker launcher - initialized in onCreate
    private var pickSoundLauncher: ActivityResultLauncher<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register file picker launcher
        pickSoundLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null && pendingSoundType >= 0) {
                val activity = activity as? MainActivity ?: return@registerForActivityResult
                val ctx = context ?: return@registerForActivityResult
                
                progressUpload?.visibility = View.VISIBLE
                progressUpload?.progress = 0
                progressUpload?.isIndeterminate = true
                setUploadButtonsEnabled(false)
                
                // Convert in background thread
                Thread {
                    try {
                        // Convert audio to WAV format
                        val result = AudioConverter.convertToWav(ctx, uri)
                        
                        if (!result.success || result.wavData == null) {
                            activity.runOnUiThread {
                                progressUpload?.visibility = View.GONE
                                progressUpload?.isIndeterminate = false
                                setUploadButtonsEnabled(true)
                                Toast.makeText(ctx, "Conversion failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                            }
                            return@Thread
                        }
                        
                        val wavData = result.wavData
                        android.util.Log.d("Sound", "Converted to WAV: ${wavData.size} bytes (from ${result.originalFormat})")
                        
                        if (wavData.size > 200 * 1024) {
                            activity.runOnUiThread {
                                progressUpload?.visibility = View.GONE
                                progressUpload?.isIndeterminate = false
                                setUploadButtonsEnabled(true)
                                Toast.makeText(ctx, "File too large after conversion (${wavData.size / 1024}KB, max 200KB)", Toast.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }
                        
                        activity.runOnUiThread {
                            progressUpload?.isIndeterminate = false
                            progressUpload?.progress = 0
                        }
                        
                        activity.uploadSoundFile(pendingSoundType, wavData,
                            onProgress = { progress ->
                                progressUpload?.progress = progress
                            },
                            onComplete = { success ->
                                progressUpload?.visibility = View.GONE
                                setUploadButtonsEnabled(true)
                                if (success) {
                                    Toast.makeText(ctx, "Upload complete (${wavData.size / 1024}KB WAV)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(ctx, "Upload failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } catch (e: Exception) {
                        activity.runOnUiThread {
                            progressUpload?.visibility = View.GONE
                            progressUpload?.isIndeterminate = false
                            setUploadButtonsEnabled(true)
                            Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
            pendingSoundType = -1
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Sync with latest sound status from MainActivity when becoming visible
        (activity as? MainActivity)?.getSoundStatus()?.let { status ->
            updateSoundStatus(status)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_device_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvDeviceName = view.findViewById(R.id.tvDeviceName)
        tvFirmwareVersion = view.findViewById(R.id.tvFirmwareVersion)
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus)
        statusIndicator = view.findViewById(R.id.statusIndicator)
        tvCodecName = view.findViewById(R.id.tvCodecName)
        tvSampleRate = view.findViewById(R.id.tvSampleRate)
        tvBitsPerSample = view.findViewById(R.id.tvBitsPerSample)
        tvChannelMode = view.findViewById(R.id.tvChannelMode)
        tvPlaybackQuality = view.findViewById(R.id.tvPlaybackQuality)

        // Sound controls
        switchMuteSound = view.findViewById(R.id.switchMuteSound)
        btnUploadStartup = view.findViewById(R.id.btnUploadStartup)
        btnUploadPairing = view.findViewById(R.id.btnUploadPairing)
        btnUploadConnected = view.findViewById(R.id.btnUploadConnected)
        btnUploadMaxVol = view.findViewById(R.id.btnUploadMaxVol)
        tvStartupStatus = view.findViewById(R.id.tvStartupStatus)
        tvPairingStatus = view.findViewById(R.id.tvPairingStatus)
        tvConnectedStatus = view.findViewById(R.id.tvConnectedStatus)
        tvMaxVolStatus = view.findViewById(R.id.tvMaxVolStatus)
        progressUpload = view.findViewById(R.id.progressUpload)

        // Setup sound control listeners
        setupSoundControls()

        // Get initial values from arguments
        updateFromArguments()
    }

    private fun setupSoundControls() {
        val activity = activity as? MainActivity

        switchMuteSound?.setOnCheckedChangeListener { _, isChecked ->
            if (!updatingFromDevice) {
                activity?.sendSoundMute(isChecked)
            }
        }

        btnUploadStartup?.setOnClickListener {
            pendingSoundType = 0
            pickSoundLauncher?.launch("audio/*")
        }

        btnUploadPairing?.setOnClickListener {
            pendingSoundType = 1
            pickSoundLauncher?.launch("audio/*")
        }

        btnUploadConnected?.setOnClickListener {
            pendingSoundType = 2
            pickSoundLauncher?.launch("audio/*")
        }

        btnUploadMaxVol?.setOnClickListener {
            pendingSoundType = 3
            pickSoundLauncher?.launch("audio/*")
        }

        // Long press to delete
        btnUploadStartup?.setOnLongClickListener {
            activity?.sendDeleteSound(0)
            Toast.makeText(context, "Deleting startup sound...", Toast.LENGTH_SHORT).show()
            true
        }

        btnUploadPairing?.setOnLongClickListener {
            activity?.sendDeleteSound(1)
            Toast.makeText(context, "Deleting pairing sound...", Toast.LENGTH_SHORT).show()
            true
        }

        btnUploadConnected?.setOnLongClickListener {
            activity?.sendDeleteSound(2)
            Toast.makeText(context, "Deleting connected sound...", Toast.LENGTH_SHORT).show()
            true
        }

        btnUploadMaxVol?.setOnLongClickListener {
            activity?.sendDeleteSound(3)
            Toast.makeText(context, "Deleting max volume sound...", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setUploadButtonsEnabled(enabled: Boolean) {
        btnUploadStartup?.isEnabled = enabled
        btnUploadPairing?.isEnabled = enabled
        btnUploadConnected?.isEnabled = enabled
        btnUploadMaxVol?.isEnabled = enabled
    }

    /**
     * Update sound status from BLE notification
     * @param status bit 0-3 = sound exists flags, bit 7 = muted
     */
    fun updateSoundStatus(status: Int) {
        updatingFromDevice = true
        try {
            switchMuteSound?.isChecked = (status and 0x80) != 0

            val hasStartup = (status and 0x01) != 0
            val hasPairing = (status and 0x02) != 0
            val hasConnected = (status and 0x04) != 0
            val hasMaxVol = (status and 0x08) != 0

            tvStartupStatus?.text = if (hasStartup) "✓" else "—"
            tvPairingStatus?.text = if (hasPairing) "✓" else "—"
            tvConnectedStatus?.text = if (hasConnected) "✓" else "—"
            tvMaxVolStatus?.text = if (hasMaxVol) "✓" else "—"

            context?.let { ctx ->
                val successColor = ContextCompat.getColor(ctx, R.color.success)
                val defaultColor = ContextCompat.getColor(ctx, R.color.text_secondary)

                tvStartupStatus?.setTextColor(if (hasStartup) successColor else defaultColor)
                tvPairingStatus?.setTextColor(if (hasPairing) successColor else defaultColor)
                tvConnectedStatus?.setTextColor(if (hasConnected) successColor else defaultColor)
                tvMaxVolStatus?.setTextColor(if (hasMaxVol) successColor else defaultColor)
            }
        } finally {
            updatingFromDevice = false
        }
    }

    private fun updateFromArguments() {
        val args = arguments ?: return
        val isConnected = args.getBoolean("is_connected", false)

        tvDeviceName?.text = if (isConnected) args.getString("device_name", "Unknown") else "Unknown"
        tvFirmwareVersion?.text = if (isConnected) args.getString("fw_version", "Unknown") else "Unknown"
        tvCodecName?.text = if (isConnected) args.getString("codec_name", "Unknown") else "Unknown"
        tvSampleRate?.text = if (isConnected) args.getString("sample_rate", "Unknown") else "Unknown"
        tvBitsPerSample?.text = if (isConnected) args.getString("bits_per_sample", "Unknown") else "Unknown"
        tvChannelMode?.text = if (isConnected) args.getString("channel_mode", "Unknown") else "Unknown"
        tvPlaybackQuality?.text = if (isConnected) args.getString("playback_quality", "Unknown") else "Unknown"

        // Update sound status
        val soundStatus = args.getInt("sound_status", 0)
        updateSoundStatus(soundStatus)

        context?.let { ctx ->
            if (isConnected) {
                tvConnectionStatus?.text = "Connected"
                tvConnectionStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.success))
                statusIndicator?.setBackgroundResource(R.drawable.status_dot_connected)
            } else {
                tvConnectionStatus?.text = "Disconnected"
                tvConnectionStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.error))
                statusIndicator?.setBackgroundResource(R.drawable.status_dot_disconnected)
            }
        }
    }

    /**
     * Update the device info from MainActivity - called when data changes
     */
    fun updateInfo(
        isConnected: Boolean,
        deviceName: String,
        fwVersion: String,
        codecName: String,
        sampleRate: String,
        bitsPerSample: String,
        channelMode: String,
        playbackQuality: String
    ) {
        tvDeviceName?.text = if (isConnected) deviceName else "Unknown"
        tvFirmwareVersion?.text = if (isConnected) fwVersion else "Unknown"
        tvCodecName?.text = if (isConnected) codecName else "Unknown"
        tvSampleRate?.text = if (isConnected) sampleRate else "Unknown"
        tvBitsPerSample?.text = if (isConnected) bitsPerSample else "Unknown"
        tvChannelMode?.text = if (isConnected) channelMode else "Unknown"
        tvPlaybackQuality?.text = if (isConnected) playbackQuality else "Unknown"

        context?.let { ctx ->
            if (isConnected) {
                tvConnectionStatus?.text = "Connected"
                tvConnectionStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.success))
                statusIndicator?.setBackgroundResource(R.drawable.status_dot_connected)
            } else {
                tvConnectionStatus?.text = "Disconnected"
                tvConnectionStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.error))
                statusIndicator?.setBackgroundResource(R.drawable.status_dot_disconnected)
            }
        }
    }

    companion object {
        const val TAG = "DeviceInfoBottomSheet"

        fun newInstance(
            isConnected: Boolean,
            deviceName: String,
            fwVersion: String,
            codecName: String,
            sampleRate: String,
            bitsPerSample: String,
            channelMode: String,
            playbackQuality: String,
            soundStatus: Int = 0
        ): DeviceInfoBottomSheet {
            val fragment = DeviceInfoBottomSheet()
            fragment.arguments = Bundle().apply {
                putBoolean("is_connected", isConnected)
                putString("device_name", deviceName)
                putString("fw_version", fwVersion)
                putString("codec_name", codecName)
                putString("sample_rate", sampleRate)
                putString("bits_per_sample", bitsPerSample)
                putString("channel_mode", channelMode)
                putString("playback_quality", playbackQuality)
                putInt("sound_status", soundStatus)
            }
            return fragment
        }
    }
}
