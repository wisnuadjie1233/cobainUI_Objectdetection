package com.example.cobainui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class Detection(
    val label: String,
    val confidence: Float,
    val bbox: RectF
)

class FoodDetector(context: Context) {
    private val interpreter: Interpreter
    private val inputSize = 640
    private val labels: List<String>

    init {
        // Menggunakan model 'best.tflite' sesuai referensi Roboflow
        interpreter = Interpreter(loadModelFile(context, "best.tflite"))
        labels = context.assets.open("labels.txt").bufferedReader().readLines()
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        // Alokasi buffer untuk input model: 1 gambar, 640x640, 3 channel (RGB), 4 byte per float
        val buffer = ByteBuffer.allocateDirect(4 * 1 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        return buffer
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val input = preprocess(bitmap)

        // --- PERBAIKAN: Ubah ukuran output agar sesuai dengan model ---
        val maxDetections = 300
        val output = Array(1) { Array(maxDetections) { FloatArray(6) } }

        interpreter.run(input, output)

        val results = mutableListOf<Detection>()
        val rawDetections = output[0]

        for (rawDetection in rawDetections) {
            val confidence = rawDetection[4]
            // Menggunakan confidence threshold 0.5f (50%) seperti contoh
            if (confidence > 0.5f) { 
                val classId = rawDetection[5].toInt()
                val label = labels.getOrElse(classId) { "unknown" }
                results.add(
                    Detection(
                        label = label,
                        confidence = confidence,
                        bbox = RectF(rawDetection[0], rawDetection[1], rawDetection[2], rawDetection[3])
                    )
                )
            }
        }
        return results
    }

    fun close() {
        interpreter.close()
    }
}
