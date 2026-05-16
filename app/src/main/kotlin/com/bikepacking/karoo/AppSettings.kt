package com.bikepacking.karoo

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bikepacking_prefs", Context.MODE_PRIVATE)

    var ftp: Int
        get() = prefs.getInt("ftp", 200)
        set(v) = prefs.edit().putInt("ftp", v).apply()

    var maxHr: Int
        get() = prefs.getInt("max_hr", 185)
        set(v) = prefs.edit().putInt("max_hr", v).apply()

    var deadlineTime: String
        get() = prefs.getString("deadline_time", "19:00") ?: "19:00"
        set(v) = prefs.edit().putString("deadline_time", v).apply()

    var capToTwilight: Boolean
        get() = prefs.getBoolean("cap_to_twilight", true)
        set(v) = prefs.edit().putBoolean("cap_to_twilight", v).apply()

    var baroSensitive: Boolean
        get() = prefs.getBoolean("baro_sensitive", false)
        set(v) = prefs.edit().putBoolean("baro_sensitive", v).apply()

    fun deadlineTodayMs(): Long {
        val parts = deadlineTime.split(":").map { it.toIntOrNull() ?: 0 }
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, parts.getOrElse(0) { 19 })
        cal.set(Calendar.MINUTE, parts.getOrElse(1) { 0 })
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    var lastLat: Double
        get() = prefs.getFloat("last_lat", 52.23f).toDouble()
        set(v) = prefs.edit().putFloat("last_lat", v.toFloat()).apply()

    var lastLon: Double
        get() = prefs.getFloat("last_lon", 21.01f).toDouble()
        set(v) = prefs.edit().putFloat("last_lon", v.toFloat()).apply()
}