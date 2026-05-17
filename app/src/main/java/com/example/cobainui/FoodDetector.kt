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

private data class Prep(
    val buffer: ByteBuffer,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val origW: Int,
    val origH: Int
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
        Log.d("YOLO", "Labels: $labels | Output shape: ${interpreter.getOutputTensor(0).shape().contentToString()}")
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(modelPath)
        val stream = FileInputStream(fd.fileDescriptor)
        return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    /** Letterbox: jaga aspek rasio, pad abu-abu 114 */
    private fun preprocess(bitmap: Bitmap): Prep {
        val scale = min(inputSize.toFloat() / bitmap.width, inputSize.toFloat() / bitmap.height)
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val padded = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(scaled, (inputSize - newW) / 2f, (inputSize - newH) / 2f, null)

        val buffer = ByteBuffer.allocateDirect(4 * 1 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        padded.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (p in pixels) {
            buffer.putFloat((p shr 16 and 0xFF) / 255f)
            buffer.putFloat((p shr 8 and 0xFF) / 255f)
            buffer.putFloat((p and 0xFF) / 255f)
        }

        return Prep(
            buffer = buffer,
            scale = scale,
            padX = (inputSize - newW) / 2f,
            padY = (inputSize - newH) / 2f,
            origW = bitmap.width,
            origH = bitmap.height
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val prep = preprocess(bitmap)
        val shape = interpreter.getOutputTensor(0).shape()

        return when {
            shape.size == 3 && shape[2] == 6 -> detectEndToEnd(prep)
            shape.size == 3 && shape[1] == numClasses + 4 -> detectRaw(prep)
            else -> {
                Log.e("YOLO", "Unknown shape: ${shape.contentToString()}")
                emptyList()
            }
        }
    }

    /** Model sudah include NMS — output [1, 300, 6] */
    private fun detectEndToEnd(prep: Prep): List<Detection> {
        val output = Array(1) { Array(300) { FloatArray(6) } }
        interpreter.run(prep.buffer, output)

        val list = mutableListOf<Detection>()
        for (i in 0 until 300) {
            val conf = output[0][i][4]
            if (conf > confThreshold) {
                val c = output[0][i][5].toInt()

                // Koordinat model dalam space 640×640 letterbox → kembalikan ke gambar asli
                val x1 = (output[0][i][0] * inputSize - prep.padX) / prep.scale
                val y1 = (output[0][i][1] * inputSize - prep.padY) / prep.scale
                val x2 = (output[0][i][2] * inputSize - prep.padX) / prep.scale
                val y2 = (output[0][i][3] * inputSize - prep.padY) / prep.scale

                list.add(
                    Detection(
                        label = labels.getOrElse(c) { "?" },
                        confidence = conf,
                        bbox = RectF(
                            x1.coerceIn(0f, prep.origW.toFloat()),
                            y1.coerceIn(0f, prep.origH.toFloat()),
                            x2.coerceIn(0f, prep.origW.toFloat()),
                            y2.coerceIn(0f, prep.origH.toFloat())
                        )
                    )
                )
            }
        }
        return list
    }

    /** Kalau model output raw [1, 84, 8400] */
    private fun detectRaw(prep: Prep): List<Detection> {
        val numAnchors = 8400
        val output = Array(1) { Array(numClasses + 4) { FloatArray(numAnchors) } }
        interpreter.run(prep.buffer, output)

        val candidates = mutableListOf<Detection>()
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
                // ✅ URUTAN BENAR: x, y, w, h
                val x = output[0][0][i]
                val y = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]

                val x1 = (x - w / 2) * inputSize
                val y1 = (y - h / 2) * inputSize
                val x2 = (x + w / 2) * inputSize
                val y2 = (y + h / 2) * inputSize

                candidates.add(
                    Detection(
                        label = labels[classId],
                        confidence = bestScore,
                        bbox = RectF(
                            (x1 - prep.padX) / prep.scale,
                            (y1 - prep.padY) / prep.scale,
                            (x2 - prep.padX) / prep.scale,
                            (y2 - prep.padY) / prep.scale
                        )
                    )
                )
            }
        }
        return applyNMS(candidates)
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
