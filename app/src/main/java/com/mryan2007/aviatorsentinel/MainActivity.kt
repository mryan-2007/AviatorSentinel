package com.mryan2007.aviatorsentinel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etThreshold: EditText
    private lateinit var etStreakTarget: EditText
    private lateinit var btnPermissionOverlay: Button
    private lateinit var btnStartFloating: Button
    private lateinit var tvStatus: TextView

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startDetectionServices(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etThreshold = findViewById(R.id.etThreshold)
        etStreakTarget = findViewById(R.id.etStreakTarget)
        btnPermissionOverlay = findViewById(R.id.btnPermissionOverlay)
        btnStartFloating = findViewById(R.id.btnStartFloating)
        tvStatus = findViewById(R.id.tvStatus)

        // Set default values requested by mryan-2007
        etThreshold.setText("2.00")
        etStreakTarget.setText("7")

        btnPermissionOverlay.setOnClickListener {
            checkAndRequestOverlayPermission()
        }

        btnStartFloating.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please enable 'Display over other apps' first", Toast.LENGTH_LONG).show()
                checkAndRequestOverlayPermission()
                return@setOnClickListener
            }

            // Save user settings
            val prefs = getSharedPreferences("aviator_prefs", Context.MODE_PRIVATE)
            val threshold = etThreshold.text.toString().toDoubleOrNull() ?: 2.00
            val streakTarget = etStreakTarget.text.toString().toIntOrNull() ?: 7
            prefs.edit()
                .putFloat("threshold", threshold.toFloat())
                .putInt("streak_target", streakTarget)
                .apply()

            // Request MediaProjection (Screen Capture)
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
        }

        updateOverlayButtonState()
    }

    override fun onResume() {
        super.onResume()
        updateOverlayButtonState()
    }

    private fun updateOverlayButtonState() {
        if (Settings.canDrawOverlays(this)) {
            btnPermissionOverlay.isEnabled = false
            btnPermissionOverlay.text = "✓ Floating Window Allowed"
        } else {
            btnPermissionOverlay.isEnabled = true
            btnPermissionOverlay.text = "1. Grant Floating Permission"
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startDetectionServices(resultCode: Int, data: Intent) {
        // 1. Launch Screen Capture Foreground Service
        val captureServiceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("EXTRA_RESULT_CODE", resultCode)
            putExtra("EXTRA_DATA", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(captureServiceIntent)
        } else {
            startService(captureServiceIntent)
        }

        // 2. Launch Floating HUD & Crop Controller Service
        val overlayServiceIntent = Intent(this, FloatingOverlayService::class.java)
        startService(overlayServiceIntent)

        Toast.makeText(this, "Aviator Sentinel Started! Open Chrome.", Toast.LENGTH_LONG).show()
        moveTaskToBack(true) // Minimize app so user can switch to Chrome
    }
}
