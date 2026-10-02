package com.mryan2007.aviatorsentinel

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class AviatorStreakDetector(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastRecordedRawScore: String = ""
    private var lastRecordedMultiplier: Double = -1.0
    private var currentStreak: Int = 0

    // Perceptual hash of previous frame to skip OCR if score hasn't changed (saves CPU & battery)
    private var lastFrameHash: Long = 0L

    // Regex to match Aviator multipliers e.g. "1.14x", "12.50x", "3.00", "1.99X", "2x"
    private val multiplierPattern = Pattern.compile("([0-9]+(?:[.,][0-9]{1,2})?)\\s*[xX]?")

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    suspend fun analyzeCroppedScoreBitmap(bitmap: Bitmap) = withContext(Dispatchers.Default) {
        val currentHash = computeSimpleHash(bitmap)
        // If image pixels are identical or within tiny threshold, skip OCR (score hasn't updated yet)
        if (currentHash == lastFrameHash) {
            bitmap.recycle()
            return@withContext
        }
        lastFrameHash = currentHash

        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                processOcrText(visionText.text)
                bitmap.recycle()
            }
            .addOnFailureListener {
                bitmap.recycle()
            }
    }

    /**
     * Solves the user's question: "there is no specific interval for the score to be updated, it is randomized, will it be a problem?"
     * No! By checking at 10 Hz and maintaining state (lastRecordedMultiplier),
     * we only trigger when a genuinely NEW multiplier appears in the leftmost slot!
     */
    private fun processOcrText(rawText: String) {
        val cleaned = rawText.replace("\n", " ").trim()
        val matcher = multiplierPattern.matcher(cleaned)

        if (matcher.find()) {
            val numberStr = matcher.group(1)?.replace(",", ".") ?: return
            val multiplierValue = numberStr.toDoubleOrNull() ?: return

            // Check if this score is distinct from the previous round's score
            if (multiplierValue != lastRecordedMultiplier) {
                lastRecordedMultiplier = multiplierValue
                lastRecordedRawScore = cleaned

                handleNewScoreReceived(multiplierValue)
            }
        }
    }

    private fun handleNewScoreReceived(multiplier: Double) {
        val prefs = context.getSharedPreferences("aviator_prefs", Context.MODE_PRIVATE)
        val threshold = prefs.getFloat("threshold", 2.00f).toDouble()
        val streakTarget = prefs.getInt("streak_target", 7)

        // Rule: Count when leftmost number is strictly LESS than threshold (e.g. < 2.00x)
        if (multiplier < threshold) {
            currentStreak++
        } else {
            // Rule: If it is not consecutive (< 2.00x), the counting will restart
            currentStreak = 0
        }

        // Update floating HUD badge
        FloatingOverlayService.instance?.updateStreakHUD(currentStreak, streakTarget, multiplier)

        // Rule: Vibrate when it will count for 7 consecutive times
        if (currentStreak >= streakTarget) {
            triggerStreakAlertVibration()
        }
    }

    /**
     * Distinct pulsing vibration alert for 7 consecutive rounds < 2.00x
     */
    private fun triggerStreakAlertVibration() {
        if (!vibrator.hasVibrator()) return

        val timings = longArrayOf(0, 300, 150, 300, 150, 600)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    /**
     * Fast downsampled 8x8 average hash for pixel change detection
     */
    private fun computeSimpleHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val pixel = scaled.getPixel(x, y)
                val lum = (pixel and 0xFF)
                hash = (hash shl 1) or (if (lum > 128) 1L else 0L)
            }
        }
        scaled.recycle()
        return hash
    }
}
