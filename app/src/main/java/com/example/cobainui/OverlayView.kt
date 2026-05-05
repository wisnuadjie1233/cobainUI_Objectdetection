package com.example.cobainui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var detections: List<Detection> = emptyList()
    private var frameWidth: Int = 1
    private var frameHeight: Int = 1

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 55f
        isFakeBoldText = true
    }

    fun setResults(results: List<Detection>, sourceWidth: Int, sourceHeight: Int) {
        detections = results
        frameWidth = sourceWidth
        frameHeight = sourceHeight
        invalidate() // minta redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || frameWidth == 0 || frameHeight == 0) return

        // Scale agar frame kamera pas di tengah layar (letterbox)
        val scaleX = width.toFloat() / frameWidth
        val scaleY = height.toFloat() / frameHeight
        val scale = min(scaleX, scaleY)

        val offsetX = (width - frameWidth * scale) / 2f
        val offsetY = (height - frameHeight * scale) / 2f

        for (d in detections) {
            val left   = d.bbox.left   * scale + offsetX
            val top    = d.bbox.top    * scale + offsetY
            val right  = d.bbox.right  * scale + offsetX
            val bottom = d.bbox.bottom * scale + offsetY

            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawText(
                "${d.label} ${"%.2f".format(d.confidence)}",
                left,
                (top - 20).coerceAtLeast(40f),
                textPaint
            )
        }
    }
}
