package com.example.cobainui

import android.content.Context
import android.graphics.*
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val label: String,
    val confidence: Float,
    val bbox: RectF
)

class FoodDetector(context: Context) {
    private val interpreter: Interpreter
    private val inputSize = 640
    private val labels: List<String>
    private val numClasses: Int
    private val confThreshold = 0.5f
    private val iouThreshold = 0.45f

    init {
        interpreter = Interpreter(loadModelFile(context, "best_float32.tflite"))
        labels = context.assets.open("labels.txt").bufferedReader().readLines()
        numClasses = labels.size
        Log.d("YOLO", "Labels: $labels")

        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.d("YOLO", "Output shape: ${outputShape.contentToString()}")
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(modelPath)
        val stream = FileInputStream(fd.fileDescriptor)
        return stream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(4 * 1 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buffer.putFloat((p shr 16 and 0xFF) / 255f)
            buffer.putFloat((p shr 8 and 0xFF) / 255f)
            buffer.putFloat((p and 0xFF) / 255f)
        }
        return buffer
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val input = preprocess(bitmap)
        val shape = interpreter.getOutputTensor(0).shape()

        return when {
            shape.size == 3 && shape[2] == 6 -> detectEndToEnd(input, bitmap)
            shape.size == 3 && shape[1] == numClasses + 4 -> detectRaw(input, bitmap)
            else -> {
                Log.e("YOLO", "Unknown shape: ${shape.contentToString()}")
                emptyList()
            }
        }
    }

    private fun detectRaw(input: ByteBuffer, bitmap: Bitmap): List<Detection> {
        val numAnchors = 8400
        val output = Array(1) { Array(numClasses + 4) { FloatArray(numAnchors) } }
        interpreter.run(input, output)

        val candidates = mutableListOf<Detection>()
        val scaleX = bitmap.width.toFloat() / inputSize
        val scaleY = bitmap.height.toFloat() / inputSize

        for (i in 0 until numAnchors) {
            var bestScore = 0f
            var classId = -1
            for (c in 0 until numClasses) {
                val s = output[0][4 + c][i]
                if (s > bestScore) {
                    bestScore = s
                    classId = c
                }
            }
            if (bestScore > confThreshold) {
                val x = output[0][0][i]
                val y = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]
                candidates.add(
                    Detection(
                        label = labels[classId],
                        confidence = bestScore,
                        bbox = RectF(
                            (x - w / 2) * scaleX,
                            (y - h / 2) * scaleY,
                            (x + w / 2) * scaleX,
                            (y + h / 2) * scaleY
                        )
                    )
                )
            }
        }
        return applyNMS(candidates)
    }

    private fun detectEndToEnd(input: ByteBuffer, bitmap: Bitmap): List<Detection> {
        val output = Array(1) { Array(300) { FloatArray(6) } }  // <- ganti 100 jadi 300
        interpreter.run(input, output)
        val list = mutableListOf<Detection>()
        for (i in 0 until 300) {  // <- ganti 100 jadi 300
            val conf = output[0][i][4]
            if (conf > confThreshold) {
                val c = output[0][i][5].toInt()
                list.add(
                    Detection(
                        label = labels.getOrElse(c) { "?" },
                        confidence = conf,
                        bbox = RectF(
                            output[0][i][0] * bitmap.width,
                            output[0][i][1] * bitmap.height,
                            output[0][i][2] * bitmap.width,
                            output[0][i][3] * bitmap.height
                        )
                    )
                )
            }
        }
        return list
    }


    private fun applyNMS(list: MutableList<Detection>): List<Detection> {
        val sorted = list.sortedByDescending { it.confidence }.toMutableList()
        val keep = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val cur = sorted.removeAt(0)
            keep.add(cur)
            sorted.removeAll { iou(cur.bbox, it.bbox) > iouThreshold }
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        return inter / (areaA + areaB - inter + 1e-6f)
    }
}
