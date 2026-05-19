package com.bikepacking.karoo

import android.app.Activity
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var settings: AppSettings
    private lateinit var etFtp: EditText
    private lateinit var etMaxHr: EditText
    private lateinit var tvDeadline: TextView
    private lateinit var tvTwilightTime: TextView
    private lateinit var cbCapTwilight: CheckBox
    private lateinit var tvActiveDeadline: TextView
    private lateinit var tvPiFtp: TextView
    private lateinit var tvPiToday: TextView
    private lateinit var tvPiHrv: TextView
    private lateinit var tvPiSleep: TextView
    private lateinit var tvPiPressure: TextView
    private lateinit var tvPiStatus: TextView
    private lateinit var btnRefreshPi: TextView
    private lateinit var tvFtpSource: TextView
    private lateinit var cbBaroSensitive: CheckBox
    private lateinit var tvBaroInfo: TextView

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "QBOT_SETUP"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)

        etFtp            = findViewById(R.id.et_ftp)
        etMaxHr          = findViewById(R.id.et_maxhr)
        tvDeadline       = findViewById(R.id.tv_deadline)
        tvTwilightTime   = findViewById(R.id.tv_twilight_time)
        cbCapTwilight    = findViewById(R.id.cb_cap_twilight)
        tvActiveDeadline = findViewById(R.id.tv_active_deadline)
        tvPiFtp          = findViewById(R.id.tv_pi_ftp)
        tvPiToday        = findViewById(R.id.tv_pi_today)
        tvPiHrv          = findViewById(R.id.tv_pi_hrv)
        tvPiSleep        = findViewById(R.id.tv_pi_sleep)
        tvPiPressure     = findViewById(R.id.tv_pi_pressure)
        tvPiStatus       = findViewById(R.id.tv_pi_status)
        btnRefreshPi     = findViewById(R.id.btn_refresh_pi)
        tvFtpSource      = findViewById(R.id.tv_ftp_source)
        cbBaroSensitive  = findViewById(R.id.cb_baro_sensitive)
        tvBaroInfo       = findViewById(R.id.tv_baro_info)

        // Załaduj wartości
        if (settings.ftp > 0) etFtp.setText("${settings.ftp}")
        if (settings.maxHr > 0) etMaxHr.setText("${settings.maxHr}")
        tvDeadline.text = settings.deadlineTime
        cbCapTwilight.isChecked = settings.capToTwilight
        cbBaroSensitive.isChecked = settings.baroSensitive

        updateTwilightTime()
        updateActiveDeadline()

        // Pokaż cache natychmiast
        displayPiStatus(ReadinessManager.loadCached(this))

        // FTP autosave
        etFtp.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val v = s.toString().toIntOrNull() ?: 0
                if (v > 0) {
                    settings.ftp = v
                    tvFtpSource.text = "recznie"
                    tvFtpSource.setTextColor(Color.parseColor("#3A4560"))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // MaxHR autosave
        etMaxHr.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val v = s.toString().toIntOrNull() ?: 0
                if (v > 0) settings.maxHr = v
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        tvDeadline.setOnClickListener {
            val parts = settings.deadlineTime.split(":").map { it.toIntOrNull() ?: 0 }
            TimePickerDialog(this, { _, h, m ->
                val t = "%02d:%02d".format(h, m)
                settings.deadlineTime = t
                tvDeadline.text = t
                updateActiveDeadline()
            }, parts.getOrElse(0) { 19 }, parts.getOrElse(1) { 0 }, true).show()
        }

        cbCapTwilight.setOnCheckedChangeListener { _, checked ->
            settings.capToTwilight = checked
            updateActiveDeadline()
        }

        cbBaroSensitive.setOnCheckedChangeListener { _, checked ->
            settings.baroSensitive = checked
            updateBaroInfo(ReadinessManager.loadCached(this))
        }

        btnRefreshPi.setOnClickListener {
            Log.d(TAG, "SETUP_REFRESH_CLICK")
            refreshFromPi()
        }

        // Auto-refresh jeśli cache starszy niż 2h
        val cacheAge = System.currentTimeMillis() - ReadinessManager.lastFetchTimestampMs(this)
        if (cacheAge > 2 * 3600 * 1000L) refreshFromPi()
    }

    private fun refreshFromPi() {
        Log.d(TAG, "SETUP_REFRESH_START")
        tvPiStatus.text = "Pobieranie z Pi..."
        tvPiStatus.setTextColor(Color.parseColor("#F59E0B"))
        btnRefreshPi.isEnabled = false
        btnRefreshPi.alpha = 0.5f

        activityScope.launch {
            val data = ReadinessManager.fetch(this@MainActivity)

            Log.d(TAG, "SETUP_REFRESH_RESULT: todayFactor=${data.todayFactor} " +
                "ftp=${data.ftpWatts} hrvToday=${data.hrvToday} sleepTodayH=${data.sleepTodayH} " +
                "timestamp=${data.fetchTimestampMs} partial=${data.partial} " +
                "warnings=${data.warningReasons.joinToString(",")}")

            if (data.ftpWatts > 0f) {
                settings.ftp = data.ftpWatts.toInt()
                etFtp.setText("${data.ftpWatts.toInt()}")
                tvFtpSource.text = "z Xert"
                tvFtpSource.setTextColor(Color.parseColor("#22C55E"))
                Log.d(TAG, "TODAY_FACTOR_VALUE: ${data.todayFactor}")
            } else {
                tvFtpSource.text = "brak"
                tvFtpSource.setTextColor(Color.parseColor("#EF4444"))
            }

            if (data.hrvToday > 0) {
                Log.d(TAG, "HRV_TODAY_VALUE: ${data.hrvToday}")
                Log.d(TAG, "HRV_30D_VALUE: baseline=${data.hrvBaseline30d} deviation=${data.hrvDeviation30d}")
            }
            if (data.sleepTodayH > 0f) {
                Log.d(TAG, "SLEEP_TODAY_VALUE: ${data.sleepTodayH}")
                Log.d(TAG, "SLEEP_30D_VALUE: baseline=${data.sleepBaseline30d} deviation=${data.sleepDev}")
            }

            displayPiStatus(data)

            // Update timestamp display
            if (data.fetchTimestampMs > 0L) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                tvPiStatus.text = "Ostatnia: ${timeFormat.format(Date(data.fetchTimestampMs))}"
                tvPiStatus.setTextColor(Color.parseColor("#9CA3AF"))
                Log.d(TAG, "SETUP_REFRESH_SUCCESS: timestamp=${data.fetchTimestampMs}")
            } else {
                tvPiStatus.text = "Błąd pobierania"
                tvPiStatus.setTextColor(Color.parseColor("#EF4444"))
                Log.d(TAG, "SETUP_REFRESH_ERROR: no timestamp")
            }

            btnRefreshPi.isEnabled = true
            btnRefreshPi.alpha = 1.0f
        }
    }

    private fun displayPiStatus(data: ReadinessManager.ReadinessData) {
        // FTP
        tvPiFtp.text = if (data.ftpWatts > 0f) "${data.ftpWatts.toInt()}" else "--"

        // Today Factor (z korektą baro jeśli włączona)
        val adjusted = ReadinessManager.applyBaroAdjustment(data, settings.baroSensitive)
        val tfColor = when {
            adjusted.todayFactor >= 0.90f -> "#22C55E"
            adjusted.todayFactor >= 0.80f -> "#F59E0B"
            else                          -> "#EF4444"
        }
        tvPiToday.text = "%.2f".format(adjusted.todayFactor)
        tvPiToday.setTextColor(Color.parseColor(tfColor))

        // HRV
        tvPiHrv.text = if (data.hrvToday > 0) {
            val sign = if (data.hrvDeviation30d >= 0) "+" else ""
            "${data.hrvToday} / ${data.hrvBaseline30d.toInt()} ($sign${"%.1f".format(data.hrvDeviation30d)})"
        } else "--"
        tvPiHrv.setTextColor(hrvColor(data.hrvDeviation30d))

        // Sen
        tvPiSleep.text = if (data.sleepTodayH > 0f) {
            val sign = if (data.sleepDev >= 0) "+" else ""
            "${"%.1f".format(data.sleepTodayH)}h / ${"%.1f".format(data.sleepBaseline30d)}h ($sign${"%.1f".format(data.sleepDev)}h)"
        } else "--"
        tvPiSleep.setTextColor(sleepColor(data.sleepDev))
        
        // ── SETUP recovery diagnostic log ──
        Log.d("QBOT_SETUP_RECOVERY", "displayed hrv=${data.hrvToday} sleepTodayH=${data.sleepTodayH} " +
            "hrvBaseline=${data.hrvBaseline30d} sleepBaseline=${data.sleepBaseline30d} " +
            "hrvDev=${data.hrvDeviation30d} sleepDev=${data.sleepDev} " +
            "fetchTs=${data.fetchTimestampMs} partial=${data.partial}")

        // Ciśnienie
        tvPiPressure.text = if (data.pressureHpa > 0f) {
            val changeSign = if (data.pressureChange24h >= 0) "+" else ""
            "${"%.0f".format(data.pressureHpa)} hPa / $changeSign${"%.1f".format(data.pressureChange24h)} hPa/24h"
        } else "--"

        // Status timestamp + warning reasons
        if (data.fetchTimestampMs > 0L) {
            val age = System.currentTimeMillis() - data.fetchTimestampMs
            val ageStr = when {
                age < 3600_000L  -> "przed ${age / 60_000L} min"
                else -> SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(data.fetchTimestampMs))
            }

            val statusParts = mutableListOf<String>()

            if (data.warningReasons.isNotEmpty()) {
                // Show specific warning reasons, not just a triangle
                statusParts.add("⚠ ${data.warningReasons.joinToString(", ")}")
            } else if (data.profileComplete) {
                statusParts.add("✓")
            } else {
                statusParts.add("⚠ niekompletny")
            }

            statusParts.add("Pobrano: $ageStr")

            tvPiStatus.text = statusParts.joinToString(" | ")
            tvPiStatus.setTextColor(
                when {
                    data.profileComplete -> Color.parseColor("#22C55E")
                    data.warningReasons.isNotEmpty() -> Color.parseColor("#F59E0B")
                    else -> Color.parseColor("#EF4444")
                }
            )
        } else {
            tvPiStatus.text = "Brak danych — sprawdz Pi"
            tvPiStatus.setTextColor(Color.parseColor("#EF4444"))
        }

        if (data.ftpWatts > 0f && !data.partial) {
            tvFtpSource.text = "z Xert"
            tvFtpSource.setTextColor(Color.parseColor("#22C55E"))
        }

        updateBaroInfo(data)
    }

    private fun updateBaroInfo(data: ReadinessManager.ReadinessData) {
        if (!settings.baroSensitive || data.pressureHpa <= 0f) {
            tvBaroInfo.text = ""
            return
        }
        val m = data.baroMultiplier
        val deficit = data.pressureDeficit
        tvBaroInfo.text = when {
            m >= 1.00f -> "Cisnienie normalne — brak korekty"
            m >= 0.97f -> "Lekki niz (deficit ${deficit.toInt()} hPa) — korekta ${((1f-m)*100).toInt()}%"
            m >= 0.94f -> "Wyrazny niz (deficit ${deficit.toInt()} hPa) — korekta ${((1f-m)*100).toInt()}%"
            else       -> "Gleboki niz (deficit ${deficit.toInt()} hPa) — korekta ${((1f-m)*100).toInt()}%"
        }
        tvBaroInfo.setTextColor(
            if (m >= 1.00f) Color.parseColor("#22C55E") else Color.parseColor("#F59E0B")
        )
    }

    private fun hrvColor(dev: Float): Int = when {
        dev >= -3f  -> Color.parseColor("#22C55E")
        dev >= -8f  -> Color.parseColor("#F59E0B")
        else        -> Color.parseColor("#EF4444")
    }

    private fun sleepColor(dev: Float): Int = when {
        dev >= -0.5f -> Color.parseColor("#22C55E")
        dev >= -1.5f -> Color.parseColor("#F59E0B")
        else         -> Color.parseColor("#EF4444")
    }

    private fun updateTwilightTime() {
        val ms = SunCalculator.civilDuskMs(settings.lastLat, settings.lastLon, System.currentTimeMillis())
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvTwilightTime.text = if (ms > 0L && ms != Long.MAX_VALUE)
            "dzis: ${fmt.format(Date(ms))}" else "dzis: brak danych GPS"
    }

    private fun updateActiveDeadline() {
        val nowMs = System.currentTimeMillis()
        val hardMs = settings.deadlineTodayMs()
        val twilMs = SunCalculator.civilDuskMs(settings.lastLat, settings.lastLon, nowMs)
        val activeMs = if (settings.capToTwilight && twilMs > 0L && twilMs != Long.MAX_VALUE)
            minOf(hardMs, twilMs) else hardMs
        tvActiveDeadline.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(activeMs))
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}