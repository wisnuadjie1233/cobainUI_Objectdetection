package com.example.cobainui

import android.widget.LinearLayout
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class AnalysisHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_history)

        findViewById<ImageButton>(R.id.btn_back_history)?.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        val sharedPref = getSharedPreferences("UserStats", MODE_PRIVATE)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val consumedToday = sharedPref.getFloat("consumed_calories", 0f)

        val calMon = if (dayOfWeek == Calendar.MONDAY) consumedToday else sharedPref.getFloat("history_cal_Mon", 0f)
        val calTue = if (dayOfWeek == Calendar.TUESDAY) consumedToday else sharedPref.getFloat("history_cal_Tue", 0f)
        val calWed = if (dayOfWeek == Calendar.WEDNESDAY) consumedToday else sharedPref.getFloat("history_cal_Wed", 0f)
        val calThu = if (dayOfWeek == Calendar.THURSDAY) consumedToday else sharedPref.getFloat("history_cal_Thu", 0f)
        val calFri = if (dayOfWeek == Calendar.FRIDAY) consumedToday else sharedPref.getFloat("history_cal_Fri", 0f)
        val calSat = if (dayOfWeek == Calendar.SATURDAY) consumedToday else sharedPref.getFloat("history_cal_Sat", 0f)
        val calSun = if (dayOfWeek == Calendar.SUNDAY) consumedToday else sharedPref.getFloat("history_cal_Sun", 0f)

        val urutanHari = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
        val totalMingguIni = calMon + calTue + calWed + calThu + calFri + calSat + calSun
        val avgCal = if (urutanHari > 0) totalMingguIni / urutanHari else 0f

        val tvAverageTotal = findViewById<TextView>(R.id.tv_average_total)
        tvAverageTotal?.text = String.format(Locale.US, "%,.0f kkal", avgCal)

        setupBar(R.id.bar_senin, R.id.tv_val_senin, R.id.tv_targetlabel_senin, calMon, urutanHari >= 1)
        setupBar(R.id.bar_selasa, R.id.tv_val_selasa, R.id.tv_targetlabel_selasa, calTue, urutanHari >= 2)
        setupBar(R.id.bar_rabu, R.id.tv_val_rabu, R.id.tv_targetlabel_rabu, calWed, urutanHari >= 3)
        setupBar(R.id.bar_kamis, R.id.tv_val_kamis, R.id.tv_targetlabel_kamis, calThu, urutanHari >= 4)
        setupBar(R.id.bar_jumat, R.id.tv_val_jumat, R.id.tv_targetlabel_jumat, calFri, urutanHari >= 5)
        setupBar(R.id.bar_sabtu, R.id.tv_val_sabtu, R.id.tv_targetlabel_sabtu, calSat, urutanHari >= 6)
        setupBar(R.id.bar_minggu, R.id.tv_val_minggu, R.id.tv_targetlabel_minggu, calSun, urutanHari >= 7)

        updateNutrientCards(sharedPref)
        displayFoodHistory(sharedPref)
    }

    private fun setupBar(barId: Int, textId: Int, targetLabelId: Int, value: Float, isVisible: Boolean) {
        val bar = findViewById<ProgressBar>(barId)
        val textVal = findViewById<TextView>(textId)
        val textTarget = findViewById<TextView>(targetLabelId)

        val sharedPref = getSharedPreferences("UserStats", MODE_PRIVATE)
        val targetUser = sharedPref.getFloat("daily_target_calories", 2000f)

        textTarget?.text = String.format(Locale.US, "%.1fk", targetUser / 1000)

        bar?.let {
            it.max = targetUser.toInt()
            it.progress = if (value > targetUser) targetUser.toInt() else value.toInt()

            // --- TRIK KHUSUS: Mengatur Opacity Per Layer ---
            it.alpha = 1.0f // Bar utama tetap cerah

            val layerDrawable = it.progressDrawable as? android.graphics.drawable.LayerDrawable

            // 1. Set Wadah (Background) jadi pudar (77/255 = 0.3)
            layerDrawable?.findDrawableByLayerId(android.R.id.background)?.let { bg ->
                bg.alpha = 77
            }

            // 2. Set Isi (Progress) tetap 1.0f / Solid (255/255)
            layerDrawable?.findDrawableByLayerId(android.R.id.progress)?.let { prog ->
                prog.alpha = 255 // DI SINI TADI KAMU SALAH TULIS 'bg', HARUSNYA 'prog'
            }
        }

        if (isVisible) {
            textVal?.text = if (value >= 1000) String.format(Locale.US, "%.2fk", value / 1000) else value.toInt().toString()
            textVal?.visibility = View.VISIBLE
        } else {
            textVal?.visibility = View.INVISIBLE
        }
    }

    private fun displayFoodHistory(pref: android.content.SharedPreferences) {
        val container = findViewById<LinearLayout>(R.id.container_food_history)
        val tvEmpty = findViewById<TextView>(R.id.tv_empty_history)

        // Bersihkan container dulu agar tidak double saat di-refresh
        container?.removeAllViews()

        val data = pref.getString("daily_food_history", "")

        if (!data.isNullOrEmpty()) {
            tvEmpty?.visibility = View.GONE

            val entries = data.split("#")
            entries.forEach { entry ->
                val d = entry.split("|")
                if (d.size >= 3) {
                    // INFLATE (Ambil layout card yang kita buat tadi)
                    val itemView = layoutInflater.inflate(R.layout.item_food_history, container, false)

                    val tvMenu = itemView.findViewById<TextView>(R.id.item_tv_menu)
                    val tvJam = itemView.findViewById<TextView>(R.id.item_tv_jam)
                    val tvKalori = itemView.findViewById<TextView>(R.id.item_tv_kalori)

                    tvMenu.text = d[0]   // Nama Makanan
                    tvJam.text = d[1]    // Jam
                    tvKalori.text = d[2] // Kalori (sudah termasuk kata "kkal")

                    // Masukkan card ke dalam container utama
                    container?.addView(itemView)
                }
            }
        } else {
            tvEmpty?.visibility = View.VISIBLE
        }
    }

    private fun updateNutrientCards(pref: android.content.SharedPreferences) {
        findViewById<TextView>(R.id.tv_protein_avg)?.text = "${pref.getFloat("consumed_protein", 0f).toInt()}g avg"
        findViewById<TextView>(R.id.tv_carbs_avg)?.text = "${pref.getFloat("consumed_carbs", 0f).toInt()}g avg"
        findViewById<TextView>(R.id.tv_sugar_avg)?.text = "${pref.getFloat("consumed_sugar", 0f).toInt()}g avg"
        findViewById<TextView>(R.id.tv_fat_avg)?.text = "${pref.getFloat("consumed_fat", 0f).toInt()}g avg"
    }
}