package com.mryan2007.aviatorsentinel

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var hudFloatingView: View? = null
    private var cropSelectionView: CropSelectionView? = null
    private var isMinimized = false

    private lateinit var tvStreakBadge: TextView
    private lateinit var tvStatusText: TextView
    private lateinit var btnCrop: Button
    private lateinit var btnToggleStart: Button
    private lateinit var btnMinimize: ImageView

    companion object {
        var instance: FloatingOverlayService? = null
        const val ACTION_CROP_SELECTED = "com.mryan2007.aviatorsentinel.CROP_SELECTED"
        const val ACTION_UPDATE_STREAK = "com.mryan2007.aviatorsentinel.UPDATE_STREAK"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initFloatingHUD()
    }

    private fun initFloatingHUD() {
        hudFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_hud, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        tvStreakBadge = hudFloatingView!!.findViewById(R.id.tvStreakBadge)
        tvStatusText = hudFloatingView!!.findViewById(R.id.tvStatusText)
        btnCrop = hudFloatingView!!.findViewById(R.id.btnCrop)
        btnToggleStart = hudFloatingView!!.findViewById(R.id.btnToggleStart)
        btnMinimize = hudFloatingView!!.findViewById(R.id.btnMinimize)

        // Draggable floating window handling
        hudFloatingView!!.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(hudFloatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        // Minimize / Expand HUD
        btnMinimize.setOnClickListener {
            toggleMinimize(params)
        }

        // Open full-screen translucent Crop selector
        btnCrop.setOnClickListener {
            openCropSelector()
        }

        // Start / Pause toggle
        btnToggleStart.setOnClickListener {
            val isMonitoring = ScreenCaptureService.isMonitoringActive
            if (isMonitoring) {
                ScreenCaptureService.isMonitoringActive = false
                btnToggleStart.text = "Start"
                tvStatusText.text = "Paused"
            } else {
                if (ScreenCaptureService.selectedCropRect == null) {
                    Toast.makeText(this, "Please press 'Crop' first to select Aviator top score strip!", Toast.LENGTH_LONG).show()
                } else {
                    ScreenCaptureService.isMonitoringActive = true
                    btnToggleStart.text = "Pause"
                    tvStatusText.text = "Scanning @ 10 Hz"
                }
            }
        }

        windowManager.addView(hudFloatingView, params)
    }

    private fun toggleMinimize(params: WindowManager.LayoutParams) {
        val container = hudFloatingView?.findViewById<View>(R.id.hudContentContainer)
        if (isMinimized) {
            container?.visibility = View.VISIBLE
            isMinimized = false
        } else {
            container?.visibility = View.GONE
            isMinimized = true
        }
        windowManager.updateViewLayout(hudFloatingView, params)
    }

    fun openCropSelector() {
        if (cropSelectionView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val cropParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        cropSelectionView = CropSelectionView(this) { selectedRect ->
            // Callback when user confirms crop rectangle
            onCropConfirmed(selectedRect)
        }

        windowManager.addView(cropSelectionView, cropParams)
    }

    private fun onCropConfirmed(rect: Rect) {
        if (cropSelectionView != null) {
            windowManager.removeView(cropSelectionView)
            cropSelectionView = null
        }

        // Send to ScreenCaptureService
        ScreenCaptureService.selectedCropRect = rect
        tvStatusText.text = "Area: [${rect.width()}x${rect.height()} px]"
        Toast.makeText(this, "Score Area Cropped! Press Start to Monitor.", Toast.LENGTH_SHORT).show()
    }

    fun updateStreakHUD(currentStreak: Int, targetStreak: Int, lastScore: Double?) {
        tvStreakBadge.text = "🔥 Streak: $currentStreak / $targetStreak"
        if (lastScore != null) {
            tvStatusText.text = "Last: ${String.format("%.2f", lastScore)}x"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hudFloatingView?.let { windowManager.removeView(it) }
        cropSelectionView?.let { windowManager.removeView(it) }
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
