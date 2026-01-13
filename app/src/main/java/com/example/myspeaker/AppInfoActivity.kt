package com.example.myspeaker

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AppInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_info)

        // Enable back arrow in the top bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "App Info"

        val imgIcon: ImageView = findViewById(R.id.imgAppIcon)
        val tvName: TextView = findViewById(R.id.tvAppName)
        val tvVersion: TextView = findViewById(R.id.tvAppVersion)

        // Set app icon
        imgIcon.setImageResource(R.mipmap.ic_launcher)

        // App name from resources
        tvName.text = getString(R.string.app_name)

        // Version from package manager
        val versionName = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }

        tvVersion.text = "Version $versionName"
    }

    // Handle toolbar back arrow
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}
