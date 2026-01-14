package com.example.myspeaker

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val devices: List<ScanResult>,
    private val onDeviceClick: (ScanResult) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvDeviceAddress: TextView = view.findViewById(R.id.tvDeviceAddress)
        val tvSignalStrength: TextView = view.findViewById(R.id.tvSignalStrength)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val result = devices[position]
        val device = result.device

        holder.tvDeviceName.text = device.name ?: "ESP32 Device"
        holder.tvDeviceAddress.text = device.address
        holder.tvSignalStrength.text = "${result.rssi} dBm"

        holder.itemView.setOnClickListener {
            onDeviceClick(result)
        }
    }

    override fun getItemCount(): Int = devices.size
}
