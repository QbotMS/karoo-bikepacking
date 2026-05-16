package com.bikepacking.karoo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ReadinessManager {

    private const val TAG = "ReadinessManager"
    private const val PI_URL = "https://ankle-wool-undusted.ngrok-free.dev/ride-readiness"
    private const val TIMEOUT_MS = 10_000
    private const val PREFS_NAME = "readiness_cache"

    data class ReadinessData(
        val todayFactor: Float = 1.0f,
        val ftpWatts: Float = 0f,
        val ltpWatts: Float = 0f,
        val wPrimeKj: Float = 0f,
        val ctl: Float = 60f,
        val atl: Float = 40f,
        val bodyWeightKg: Float = 75f,
        val humidityPercent: Float = 50f,
        val xertStatus: String = "--",
        val hrvToday: Int = 0,
        val hrvBaseline30d: Float = 0f,
        val hrvDeviation30d: Float = 0f,
        val sleepTodayH: Float = 0f,
        val sleepBaseline30d: Float = 0f,
        val sleepDev: Float = 0f,
        val restingHrDev: Float = 0f,
        val pressureHpa: Float = 1013f,
        val pressureChange24h: Float = 0f,
        val pressureDeficit: Float = 0f,
        val baroMultiplier: Float = 1.0f,
        val fetchTimestampMs: Long = 0L,
        val partial: Boolean = false
    )

    suspend fun fetch(context: Context): ReadinessData = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(PI_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "Pi: kod ${conn.responseCode}"); return@withContext loadCached(context)
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            val sig = json.optJSONObject("signals")
            val partial = json.optJSONArray("sources")?.toString()?.contains("partial") == true
            val data = ReadinessData(
                todayFactor      = json.optDouble("todayFactor", 1.0).toFloat(),
                ftpWatts         = json.optDouble("ftpWatts", 0.0).toFloat(),
                ltpWatts         = json.optDouble("ltpWatts", 0.0).toFloat(),
                wPrimeKj         = json.optDouble("wPrimeKj", 0.0).toFloat(),
                ctl              = json.optDouble("ctl", 60.0).toFloat(),
                atl              = json.optDouble("atl", 40.0).toFloat(),
                bodyWeightKg     = json.optDouble("bodyWeightKg", 75.0).toFloat(),
                humidityPercent  = json.optDouble("humidityPercent", 50.0).toFloat(),
                xertStatus       = sig?.optString("xertStatus", "--") ?: "--",
                hrvToday         = sig?.optInt("hrvToday", 0) ?: 0,
                hrvBaseline30d   = sig?.optDouble("hrvBaseline30d", 0.0)?.toFloat() ?: 0f,
                hrvDeviation30d  = sig?.optDouble("hrvDeviation30d", 0.0)?.toFloat() ?: 0f,
                sleepTodayH      = sig?.optDouble("sleepTodayH", 0.0)?.toFloat() ?: 0f,
                sleepBaseline30d = sig?.optDouble("sleepBaseline30d", 0.0)?.toFloat() ?: 0f,
                sleepDev         = sig?.optDouble("sleepDev", 0.0)?.toFloat() ?: 0f,
                restingHrDev     = sig?.optDouble("restingHrDev", 0.0)?.toFloat() ?: 0f,
                pressureHpa      = json.optDouble("pressureHpa", 1013.0).toFloat(),
                pressureChange24h = json.optDouble("pressureChange24h", 0.0).toFloat(),
                pressureDeficit  = json.optDouble("pressureDeficit", 0.0).toFloat(),
                baroMultiplier   = json.optDouble("baroMultiplier", 1.0).toFloat(),
                fetchTimestampMs = System.currentTimeMillis(),
                partial          = partial
            )
            saveToCache(context, data)
            data
        } catch (e: Exception) {
            Log.e(TAG, "Błąd Pi: ${e.message}"); loadCached(context)
        }
    }

    fun applyBaroAdjustment(data: ReadinessData, baroSensitive: Boolean): ReadinessData {
        if (!baroSensitive) return data
        val m = data.baroMultiplier.coerceIn(0.80f, 1.00f)
        if (m >= 1.00f) return data
        return data.copy(todayFactor = (data.todayFactor * m).coerceIn(0.70f, 1.10f))
    }

    fun loadCached(context: Context): ReadinessData {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ReadinessData(
            todayFactor      = p.getFloat("todayFactor", 1.0f),
            ftpWatts         = p.getFloat("ftpWatts", 0f),
            ltpWatts         = p.getFloat("ltpWatts", 0f),
            wPrimeKj         = p.getFloat("wPrimeKj", 0f),
            ctl              = p.getFloat("ctl", 60f),
            atl              = p.getFloat("atl", 40f),
            bodyWeightKg     = p.getFloat("bodyWeightKg", 75f),
            humidityPercent  = p.getFloat("humidityPercent", 50f),
            xertStatus       = p.getString("xertStatus", "--") ?: "--",
            hrvToday         = p.getInt("hrvToday", 0),
            hrvBaseline30d   = p.getFloat("hrvBaseline30d", 0f),
            hrvDeviation30d  = p.getFloat("hrvDeviation30d", 0f),
            sleepTodayH      = p.getFloat("sleepTodayH", 0f),
            sleepBaseline30d = p.getFloat("sleepBaseline30d", 0f),
            sleepDev         = p.getFloat("sleepDev", 0f),
            restingHrDev     = p.getFloat("restingHrDev", 0f),
            pressureHpa      = p.getFloat("pressureHpa", 1013f),
            pressureChange24h = p.getFloat("pressureChange24h", 0f),
            pressureDeficit  = p.getFloat("pressureDeficit", 0f),
            baroMultiplier   = p.getFloat("baroMultiplier", 1.0f),
            fetchTimestampMs = p.getLong("fetchTimestampMs", 0L),
            partial          = p.getBoolean("partial", true)
        )
    }

    fun lastFetchTimestampMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong("fetchTimestampMs", 0L)

    private fun saveToCache(context: Context, data: ReadinessData) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putFloat("todayFactor", data.todayFactor)
            putFloat("ftpWatts", data.ftpWatts)
            putFloat("ltpWatts", data.ltpWatts)
            putFloat("wPrimeKj", data.wPrimeKj)
            putFloat("ctl", data.ctl)
            putFloat("atl", data.atl)
            putFloat("bodyWeightKg", data.bodyWeightKg)
            putFloat("humidityPercent", data.humidityPercent)
            putString("xertStatus", data.xertStatus)
            putInt("hrvToday", data.hrvToday)
            putFloat("hrvBaseline30d", data.hrvBaseline30d)
            putFloat("hrvDeviation30d", data.hrvDeviation30d)
            putFloat("sleepTodayH", data.sleepTodayH)
            putFloat("sleepBaseline30d", data.sleepBaseline30d)
            putFloat("sleepDev", data.sleepDev)
            putFloat("restingHrDev", data.restingHrDev)
            putFloat("pressureHpa", data.pressureHpa)
            putFloat("pressureChange24h", data.pressureChange24h)
            putFloat("pressureDeficit", data.pressureDeficit)
            putFloat("baroMultiplier", data.baroMultiplier)
            putLong("fetchTimestampMs", data.fetchTimestampMs)
            putBoolean("partial", data.partial)
            apply()
        }
    }
}