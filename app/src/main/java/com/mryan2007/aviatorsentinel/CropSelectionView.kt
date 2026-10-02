package com.mryan2007.aviatorsentinel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

class CropSelectionView(
    context: Context,
    private val onCropSelected: (Rect) -> Unit
) : View(context) {

    private val cropRect = Rect()
    private var isDragging = false
    private var startX = 0f
    private var startY = 0f

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#99000000") // Semi-transparent dark overlay
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Bright neon green crop outline
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isAntiAlias = true
        isFakeBoldText = true
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw dim background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Cut out selected area
        if (cropRect.width() > 0 && cropRect.height() > 0) {
            canvas.drawRect(cropRect, clearPaint)
            canvas.drawRect(cropRect, borderPaint)

            val text = "Score Strip [${cropRect.width()} x ${cropRect.height()}]"
            canvas.drawText(text, cropRect.left.toFloat(), (cropRect.top - 20).coerceAtLeast(60).toFloat(), textPaint)
        } else {
            val guide = "Drag across the top Aviator score bar & release"
            canvas.drawText(guide, 60f, 150f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                cropRect.set(startX.toInt(), startY.toInt(), startX.toInt(), startY.toInt())
                isDragging = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val currentX = event.x
                    val currentY = event.y
                    cropRect.left = minOf(startX, currentX).toInt()
                    cropRect.top = minOf(startY, currentY).toInt()
                    cropRect.right = maxOf(startX, currentX).toInt()
                    cropRect.bottom = maxOf(startY, currentY).toInt()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                if (cropRect.width() > 30 && cropRect.height() > 15) {
                    onCropSelected(Rect(cropRect))
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
