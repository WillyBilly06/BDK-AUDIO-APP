package com.example.myspeaker

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AppInfoBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imgIcon: ImageView = view.findViewById(R.id.imgAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val tvVersion: TextView = view.findViewById(R.id.tvAppVersion)

        // Set app icon
        imgIcon.setImageResource(R.mipmap.ic_launcher)

        // App name from resources
        tvName.text = getString(R.string.app_name)

        // Version from package manager
        val versionName = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }

        tvVersion.text = "Version $versionName"
    }

    companion object {
        const val TAG = "AppInfoBottomSheet"
    }
}
