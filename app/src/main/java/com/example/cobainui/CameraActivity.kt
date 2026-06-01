package com.example.cobainui

import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.graphics.Matrix

class CameraActivity : AppCompatActivity() {

    private lateinit var foodDetector: FoodDetector
    private lateinit var overlayView: OverlayView
    private lateinit var viewFinder: PreviewView

    // 🔑 TAMBAHKAN: simpan hasil deteksi terakhir dari analyzer
    private var latestDetections: List<Detection> = emptyList()
    private var latestFrameWidth: Int = 0
    private var latestFrameHeight: Int = 0

    // 🔑 1. TAMBAHKAN INI
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)

        viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

        foodDetector = FoodDetector(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // 🔑 2. TAMBAHKAN TOMBOL JEPRET (contoh pakai fab/button)
        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            takePhotoAndAnalyze()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // 🔑 3. INISIALISASI ImageCapture
            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        if (bitmap != null) {
                            val matrix = Matrix().apply {
                                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                            }
                            val rotated = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )

                            val detections = foodDetector.detect(rotated)
                            // 🔑 TAMBAHKAN: simpan hasil terakhir
                            synchronized(this) {
                                latestDetections = detections
                                latestFrameWidth = rotated.width
                                latestFrameHeight = rotated.height
                            }
                            runOnUiThread {
                                overlayView.setResults(detections, rotated.width, rotated.height)
                            }
                        }
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // 🔑 4. BIND imageCapture juga di sini
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // 🔑 5. TAMBAHKAN FUNGSI INI
    private fun takePhotoAndAnalyze() {
        val detections = synchronized(this) { latestDetections }

        if (detections.isEmpty()) {
            Toast.makeText(this, "Tidak ada makanan yang terdeteksi di layar", Toast.LENGTH_SHORT).show()
            return
        }

        val countMap = detections.groupingBy { it.label }.eachCount()
        val nutrition = calculateNutrition(countMap)

        runOnUiThread {
            showResultDialog(countMap, nutrition)
        }
    }


    // 🔑 6. TAMBAHKAN FUNGSI INI
    private fun showResultDialog(countMap: Map<String, Int>, nutrition: Nutrition) {
        val message = buildString {
            appendLine("📸 Terdeteksi:")
            countMap.forEach { (food, count) ->
                appendLine("• $food: $count porsi")
            }
            appendLine()
            appendLine("🔥 Total Kalori: ${nutrition.calories} kkal")
            appendLine("🧈 Lemak: %.1f g".format(nutrition.fat))
            appendLine("🥩 Protein: %.1f g".format(nutrition.protein))
            appendLine("🍚 Karbo: %.1f g".format(nutrition.carbs))
        }

        AlertDialog.Builder(this)
            .setTitle("Hasil Analisis Makanan")
            .setMessage(message)
            .setPositiveButton("Oke") { dialog, _ ->
                // 1. UPDATE SHAREDPREFERENCES DULU (Untuk Update UI Home Cepat)
                val sharedPref = getSharedPreferences("UserStats", MODE_PRIVATE)
                val currentConsumed = sharedPref.getFloat("consumed_calories", 0f)
                val newTotal = currentConsumed + nutrition.calories.toFloat()

                with(sharedPref.edit()) {
                    putFloat("consumed_calories", newTotal)
                    apply()
                }

                // 2. SIMPAN KE FIRESTORE
                saveNutritionToFirestore(nutrition, countMap.keys.toList())

                dialog.dismiss()
                // JANGAN panggil finish() di sini, biarkan fungsi saveNutritionToFirestore yang memanggilnya setelah sukses
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveNutritionToFirestore(nutrition: Nutrition, foodList: List<String>) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "Gagal simpan: User tidak login", Toast.LENGTH_SHORT).show()
            return
        }

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        // Data History
        val historyData = hashMapOf(
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "calories" to nutrition.calories,
            "protein" to nutrition.protein,
            "fat" to nutrition.fat,
            "carbs" to nutrition.carbs,
            "foods" to foodList
        )

        // Gunakan Batch atau jalankan sekaligus
        val batch = db.batch()

        val historyRef = db.collection("users").document(userId).collection("history").document()
        batch.set(historyRef, historyData)

        val dailyRef = db.collection("users").document(userId).collection("daily_totals").document(today)

        // Gunakan set dengan Merge agar tidak error jika dokumen belum ada
        // Note: Untuk update angka akurat di Firestore, lebih baik pakai transaction seperti kodemu sebelumnya
        // Tapi agar tidak "stuck", pastikan OnSuccessListener memanggil finish()

        db.runTransaction { transaction ->
            val snapshot = transaction.get(dailyRef)
            if (!snapshot.exists()) {
                transaction.set(dailyRef, hashMapOf(
                    "totalCalories" to nutrition.calories,
                    "totalProtein" to nutrition.protein,
                    "totalFat" to nutrition.fat,
                    "totalCarbs" to nutrition.carbs
                ))
            } else {
                val oldCal = snapshot.getDouble("totalCalories") ?: 0.0
                transaction.update(dailyRef, "totalCalories", oldCal + nutrition.calories)
                // tambahkan update protein, fat, carb jika perlu
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Berhasil dicatat!", Toast.LENGTH_SHORT).show()
            finish() // <--- INI PENTING: Menutup kamera hanya setelah Firestore Berhasil
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Gagal update data", e)
            finish() // Tetap tutup meskipun gagal agar user kembali ke Home
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

