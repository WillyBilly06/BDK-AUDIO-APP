package com.example.myspeaker

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

/**
 * Network connectivity helper for OTA updates
 * Ensures WiFi is available before downloading firmware
 */
object NetworkHelper {
    
    /**
     * Check if WiFi is connected
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected && networkInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }
    
    /**
     * Check if any network (WiFi or Mobile) is connected
     */
    fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }
    
    /**
     * Prompt user to enable WiFi
     */
    fun promptEnableWifi(context: Context, onContinue: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("WiFi Required")
            .setMessage("OTA updates require WiFi to download firmware. Would you like to enable WiFi now?")
            .setPositiveButton("Open WiFi Settings") { _, _ ->
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Use Mobile Data") { _, _ ->
                if (isNetworkConnected(context)) {
                    // Warn about mobile data usage
                    AlertDialog.Builder(context)
                        .setTitle("Use Mobile Data?")
                        .setMessage("Downloading firmware over mobile data may use significant data allowance. Continue?")
                        .setPositiveButton("Continue") { _, _ ->
                            onContinue()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("No Network")
                        .setMessage("No internet connection available. Please connect to WiFi or enable mobile data.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    /**
     * Show warning if using mobile data
     */
    fun warnMobileDataUsage(context: Context, firmwareSizeKB: Int, onContinue: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Mobile Data Warning")
            .setMessage("You are about to download ${firmwareSizeKB / 1024} MB of firmware over mobile data. This may consume significant data allowance. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                onContinue()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
