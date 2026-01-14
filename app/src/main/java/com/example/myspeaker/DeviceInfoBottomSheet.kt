package com.example.myspeaker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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

        // Get initial values from arguments
        updateFromArguments()
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

        if (isConnected) {
            tvConnectionStatus?.text = "Connected"
            tvConnectionStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            statusIndicator?.setBackgroundResource(R.drawable.status_dot_connected)
        } else {
            tvConnectionStatus?.text = "Disconnected"
            tvConnectionStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
            statusIndicator?.setBackgroundResource(R.drawable.status_dot_disconnected)
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
            playbackQuality: String
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
            }
            return fragment
        }
    }
}
