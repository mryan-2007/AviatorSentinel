package com.mryan2007.aviatorsentinel

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private lateinit var streakDetector: AviatorStreakDetector
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420

    companion object {
        var isMonitoringActive = false
        var selectedCropRect: Rect? = null
        const val NOTIFICATION_ID = 2007
        const val CHANNEL_ID = "aviator_sentinel_channel"
        const val ACTION_STOP = "com.mryan2007.aviatorsentinel.STOP_SERVICE"
        const val ACTION_CROP = "com.mryan2007.aviatorsentinel.TRIGGER_CROP"
    }

    override fun onCreate() {
        super.onCreate()
        streakDetector = AviatorStreakDetector(this)
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CROP -> {
                FloatingOverlayService.instance?.openCropSelector()
                return START_NOT_STICKY
            }
        }

        val resultCode = intent.getIntExtra("EXTRA_RESULT_CODE", Activity.RESULT_CANCELED)
        val data = intent.getParcelableExtra<Intent>("EXTRA_DATA")

        if (resultCode == Activity.RESULT_OK && data != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            setupVirtualDisplay()
            start10HzCaptureLoop()
        }

        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AviatorScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    /**
     * Executes 10 checks per second (every 100ms) as requested.
     * Uses perceptual change detection: only runs OCR when the cropped score box pixels change.
     */
    private fun start10HzCaptureLoop() {
        captureJob?.cancel()
        captureJob = scope.launch {
            while (isActive) {
                if (isMonitoringActive && selectedCropRect != null) {
                    processScreenFrame()
                }
                delay(100L) // 100ms interval = exactly 10 checks per second
            }
        }
    }

    private suspend fun processScreenFrame() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val crop = selectedCropRect ?: return
            if (crop.width() <= 0 || crop.height() <= 0) return

            // Ensure crop is within screen bounds
            val safeLeft = crop.left.coerceIn(0, screenWidth - 1)
            val safeTop = crop.top.coerceIn(0, screenHeight - 1)
            val safeRight = crop.right.coerceIn(safeLeft + 1, screenWidth)
            val safeBottom = crop.bottom.coerceIn(safeTop + 1, screenHeight)

            val fullBitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)

            // Crop only the user selected score bar box
            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap,
                safeLeft,
                safeTop,
                safeRight - safeLeft,
                safeBottom - safeTop
            )
            fullBitmap.recycle()

            // Pass to streak detector
            streakDetector.analyzeCroppedScoreBitmap(croppedBitmap)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aviator CV Sentinel Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors Aviator score bar with 10 Hz OCR"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val cropIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_CROP
        }
        val pCropIntent = PendingIntent.getService(
            this, 1, cropIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aviator Sentinel Active")
            .setContentText("Monitoring top score bar (<2.00x streak)")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_menu_crop, "Crop Area", pCropIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureJob?.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        isMonitoringActive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
