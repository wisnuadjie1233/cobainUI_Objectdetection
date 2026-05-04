package com.example.cobainui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var results: List<Detection> = listOf()
    private var sourceImageWidth: Int = 0
    private var sourceImageHeight: Int = 0

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f // Ukuran teks diperbesar agar lebih terlihat
        style = Paint.Style.FILL
    }

    // Fungsi ini menerima hasil deteksi (dengan koordinat bbox)
    // dan dimensi GAMBAR ASLI tempat deteksi dilakukan.
    fun setResults(newResults: List<Detection>, imageHeight: Int, imageWidth: Int) {
        results = newResults
        sourceImageHeight = imageHeight
        sourceImageWidth = imageWidth
        invalidate() // Meminta view untuk menggambar ulang
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (results.isEmpty() || sourceImageWidth == 0 || sourceImageHeight == 0) return

        // Hitung faktor skala untuk menyesuaikan gambar dari model (640x640) ke ukuran view di layar
        val scaleFactor = min(width.toFloat() / sourceImageWidth, height.toFloat() / sourceImageHeight)
        val offsetX = (width - sourceImageWidth * scaleFactor) / 2
        val offsetY = (height - sourceImageHeight * scaleFactor) / 2

        results.forEach {
            // Koordinat bbox dari model (sudah dalam format top, left, bottom, right)
            // perlu diskalakan agar sesuai di layar
            val scaledLeft = it.bbox.left * scaleFactor + offsetX
            val scaledTop = it.bbox.top * scaleFactor + offsetY
            val scaledRight = it.bbox.right * scaleFactor + offsetX
            val scaledBottom = it.bbox.bottom * scaleFactor + offsetY
            
            // Gambar kotak dan label dengan koordinat yang sudah diskalakan
            canvas.drawRect(scaledLeft, scaledTop, scaledRight, scaledBottom, boxPaint)

            val confidencePercentage = (it.confidence * 100).toInt()
            val textToShow = "${it.label} ($confidencePercentage%)"
            
            // Menggambar teks di atas kotak
            canvas.drawText(textToShow, scaledLeft, scaledTop - 10f, textPaint)
        }
    }
}
