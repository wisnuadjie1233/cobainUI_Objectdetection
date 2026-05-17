package com.example.cobainui

import android.util.Log

data class Nutrition(
    val calories: Int,
    val fat: Float,
    val protein: Float,
    val carbs: Float
)

val nutritionDatabase = mapOf(
    "nasi putih" to Nutrition(175, 0.3f, 3.5f, 39f),
    "ayam goreng" to Nutrition(250, 15f, 27f, 0f),
    "tempe goreng" to Nutrition(193, 10.8f, 18.2f, 9.4f),
    "tahu goreng" to Nutrition(144, 8.7f, 15.6f, 3.5f),
    "sambal" to Nutrition(30, 1f, 0.5f, 5f),
    // Tambahkan sesuai label modelmu, pastikan huruf kecil
)

fun calculateNutrition(countMap: Map<String, Int>): Nutrition {
    var totalCal = 0
    var totalFat = 0f
    var totalProtein = 0f
    var totalCarbs = 0f

    for ((label, count) in countMap) {
        val lookupKey = label.lowercase().trim()
        val perItem = nutritionDatabase[lookupKey]

        // 🔑 TAMBAHKAN LOG INI
        if (perItem == null) {
            Log.w("NUTRITION", "Label '$label' (lookup: '$lookupKey') tidak ada di database!")
        }

        val safeItem = perItem ?: Nutrition(0, 0f, 0f, 0f)
        totalCal += safeItem.calories * count
        totalFat += safeItem.fat * count
        totalProtein += safeItem.protein * count
        totalCarbs += safeItem.carbs * count
    }

    return Nutrition(totalCal, totalFat, totalProtein, totalCarbs)
}
