package com.example.cobainui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var detections: List<Detection> = emptyList()
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 50f
        isFakeBoldText = true
    }

    fun setResults(results: List<Detection>) {
        detections = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (d in detections) {
            canvas.drawRect(d.bbox, boxPaint)
            canvas.drawText(
                "${d.label} ${"%.2f".format(d.confidence)}",
                d.bbox.left,
                d.bbox.top - 15,
                textPaint
            )
        }
    }
}
