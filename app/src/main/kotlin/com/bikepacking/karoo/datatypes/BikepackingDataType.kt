package com.bikepacking.karoo.datatypes

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import com.bikepacking.karoo.HrZone
import com.bikepacking.karoo.PowerZone
import com.bikepacking.karoo.R
import com.bikepacking.karoo.RideEngine
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val TAG = "BP"

class BpLiveDataType(
    private val rideEngine: RideEngine,
    extension: String
) : DataTypeImpl(extension, "BP_LIVE") {

    private var viewScope: CoroutineScope? = null

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        scope.launch {
            rideEngine.state.collectLatest { state ->
                val v = RemoteViews(context.packageName, R.layout.field_live)

                // SPEED + tło wartości wg relacji speed/avg
                try {
                    v.setTextViewText(R.id.tv_speed, "%.1f".format(state.speedKph))
                    val netAvg = if (state.movingSec > 0) state.distanceKm / (state.movingSec / 3600f) else 0f
                    val grossAvg = if (state.elapsedSec > 0) state.distanceKm / (state.elapsedSec / 3600f) else 0f
                    val spdBg = when {
                        netAvg < 0.1f                   -> Color.TRANSPARENT
                        state.speedKph > netAvg * 1.15f -> Color.parseColor("#14532D")
                        state.speedKph < netAvg * 0.85f -> Color.parseColor("#7F1D1D")
                        else                            -> Color.TRANSPARENT
                    }
                    v.setInt(R.id.tv_speed, "setBackgroundColor", spdBg)
                    v.setTextViewText(R.id.tv_speed_avg, "%.1f-%.1f".format(netAvg, grossAvg))
                } catch (e: Exception) { Log.e(TAG, "speed", e) }

                // POWER
                try {
                    v.setTextViewText(R.id.tv_power, "${state.powerWatts}")
                    if (state.powerWatts > 0 && rideEngine.settings.ftp > 0) {
                        val pz = PowerZone.fromPower(state.powerWatts, rideEngine.settings.ftp)
                        v.setInt(R.id.tv_power, "setBackgroundColor", Color.parseColor(pz.colorHex))
                    } else {
                        v.setInt(R.id.tv_power, "setBackgroundColor", Color.TRANSPARENT)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "power", e)
                    v.setInt(R.id.tv_power, "setBackgroundColor", Color.TRANSPARENT)
                }

                // IF30
                try {
                    val ifStr = "%.2f".format(state.if30Value)
                    v.setTextViewText(R.id.tv_if30, if (ifStr.startsWith("0.")) ifStr.substring(1) else ifStr)
                } catch (e: Exception) { Log.e(TAG, "if30", e) }

                // NP10
                try {
                    v.setTextViewText(R.id.tv_np10, "${state.np10Watts}")
                } catch (e: Exception) { Log.e(TAG, "np10", e) }

                // WIND
                try {
                    val hasWind = state.windSpeedMs > 0.1f
                    if (hasWind) {
                        v.setTextViewText(R.id.tv_wind_arrow, state.windArrow)
                        v.setTextViewText(R.id.tv_wind_speed, "%.1f".format(state.windSpeedMs))
                        v.setTextViewText(R.id.tv_wind_impact, "%+.1f".format(state.windImpactKph))
                        v.setTextColor(R.id.tv_wind_impact,
                            if (state.windImpactKph < -0.5f) Color.parseColor("#EF4444")
                            else if (state.windImpactKph > 0.5f) Color.parseColor("#22C55E")
                            else Color.WHITE)
                        val windBg = when {
                            state.windImpactKph < -0.5f -> Color.parseColor("#7F1D1D")
                            state.windImpactKph >  0.5f -> Color.parseColor("#14532D")
                            else                        -> Color.TRANSPARENT
                        }
                        v.setInt(R.id.wind_box, "setBackgroundColor", windBg)
                    } else {
                        v.setTextViewText(R.id.tv_wind_arrow, "-")
                        v.setTextViewText(R.id.tv_wind_speed, "00.0")
                        v.setTextViewText(R.id.tv_wind_impact, "0.0")
                        v.setTextColor(R.id.tv_wind_impact, Color.parseColor("#8A96AD"))
                        v.setInt(R.id.wind_box, "setBackgroundColor", Color.TRANSPARENT)
                    }
                } catch (e: Exception) { Log.e(TAG, "wind", e) }

                // HR ZONE — "Z0" gdy brak danych
                try {
                    if (state.heartRate > 0 && rideEngine.settings.maxHr > 0) {
                        val hz = HrZone.fromHr(state.heartRate, rideEngine.settings.maxHr)
                        v.setTextViewText(R.id.tv_hr_zone, hz.label)
                        v.setInt(R.id.hr_cell, "setBackgroundColor", Color.parseColor(hz.colorHex))
                    } else if (state.heartRate > 0) {
                        v.setTextViewText(R.id.tv_hr_zone, "${state.heartRate}")
                        v.setInt(R.id.hr_cell, "setBackgroundColor", Color.parseColor("#090E1C"))
                    } else {
                        v.setTextViewText(R.id.tv_hr_zone, "Z0")
                        v.setInt(R.id.hr_cell, "setBackgroundColor", Color.parseColor("#090E1C"))
                    }
                } catch (e: Exception) { Log.e(TAG, "hrZone", e); v.setTextViewText(R.id.tv_hr_zone, "Z0") }

                // CADENCE
                try {
                    v.setTextViewText(R.id.tv_cadence, "${state.cadenceAvg30sRpm}")
                } catch (e: Exception) { Log.e(TAG, "cadence", e) }

                // W' BALANCE — liczba + % osobno
                try {
                    val wbal = state.wBalancePercent
                    if (wbal < 0) {
                        v.setTextViewText(R.id.tv_w_prime, "--")
                        v.setTextViewText(R.id.tv_w_prime_pct, "")
                        v.setTextColor(R.id.tv_w_prime, Color.parseColor("#8A96AD"))
                        v.setTextColor(R.id.tv_w_prime_pct, Color.parseColor("#8A96AD"))
                        v.setInt(R.id.wprime_cell, "setBackgroundColor", Color.parseColor("#090E1C"))
                    } else {
                        val wColor = when {
                            wbal > 60 -> Color.parseColor("#22C55E")
                            wbal > 30 -> Color.parseColor("#F59E0B")
                            else      -> Color.parseColor("#EF4444")
                        }
                        v.setTextViewText(R.id.tv_w_prime, "$wbal")
                        v.setTextViewText(R.id.tv_w_prime_pct, "%")
                        v.setTextColor(R.id.tv_w_prime, wColor)
                        v.setTextColor(R.id.tv_w_prime_pct, wColor)
                        v.setInt(R.id.wprime_cell, "setBackgroundColor",
                            if (wbal < 20) Color.parseColor("#1a0808") else Color.parseColor("#090E1C"))
                    }
                } catch (e: Exception) { Log.e(TAG, "wprime", e); v.setTextViewText(R.id.tv_w_prime, "--") }

                // GRADE — sign 15sp + liczba 18sp + % 15sp
                try {
                    val g = state.gradePercent
                    val bg = when {
                        g > 10f -> "#991B1B"; g > 7f -> "#DC2626"; g > 4f -> "#EF4444"
                        g >  1f -> "#F59E0B"; g < -7f -> "#064E3B"; g < -4f -> "#15803D"
                        g < -1f -> "#22C55E"; else -> "#090E1C"
                    }
                    v.setTextViewText(R.id.tv_grade_sign, if (g >= 0f) "+" else "-")
                    v.setTextViewText(R.id.tv_grade, "%.1f".format(abs(g)))
                    v.setTextViewText(R.id.tv_grade_pct, "%")
                    v.setInt(R.id.grade_cell, "setBackgroundColor", Color.parseColor(bg))
                } catch (e: Exception) { Log.e(TAG, "grade", e) }

                try { emitter.updateView(v) } catch (e: Exception) { Log.e(TAG, "updateView", e) }
            }
        }
    }
}

class BpEtaDataType(
    private val rideEngine: RideEngine,
    extension: String
) : DataTypeImpl(extension, "BP_ETA") {

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var viewScope: CoroutineScope? = null

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        scope.launch {
            rideEngine.state.collectLatest { state ->
                val v = RemoteViews(context.packageName, R.layout.field_eta)

                try { v.setTextViewText(R.id.tv_dst, "%.1f".format(state.distanceKm)) } catch (e: Exception) { Log.e(TAG, "dst", e) }
                try { v.setTextViewText(R.id.tv_poz, if (state.hasRoute) "%.1f".format(state.remainingKm) else "--") } catch (e: Exception) { Log.e(TAG, "poz", e) }

                try {
                    if (state.temperatureCelsius != null) {
                        v.setTextViewText(R.id.tv_temp, "%.0f°".format(state.temperatureCelsius))
                        v.setTextColor(R.id.tv_temp, when {
                            state.temperatureCelsius < 5f  -> Color.parseColor("#7DD3FC")
                            state.temperatureCelsius < 28f -> Color.WHITE
                            else -> Color.parseColor("#F97316")
                        })
                    } else {
                        v.setTextViewText(R.id.tv_temp, "--°")
                        v.setTextColor(R.id.tv_temp, Color.parseColor("#8A96AD"))
                    }
                } catch (e: Exception) { Log.e(TAG, "temp", e) }

                try {
                    v.setTextViewText(R.id.tv_gear,
                        if (state.frontTeeth > 0 && state.rearTeeth > 0)
                            "${state.frontTeeth}-${state.rearTeeth}" else "--")
                } catch (e: Exception) { Log.e(TAG, "gear", e) }

                try {
                    v.setTextViewText(R.id.tv_eta,
                        if (state.etaTimestamp > 0L) timeFmt.format(Date(state.etaTimestamp)) else "--:--")
                } catch (e: Exception) { Log.e(TAG, "eta", e) }

                // ── ETA STATUS ────────────────────────────────────────────────────────────
                try {
                    when {
                        state.isOverDeadline -> {
                            v.setInt(R.id.eta_stripe, "setBackgroundColor", Color.parseColor("#EF4444"))
                            v.setInt(R.id.cell_eta, "setBackgroundColor", Color.parseColor("#2d0f0f"))
                            v.setTextViewText(R.id.tv_eta_status, "⚠ +${state.minutesOverDeadline} min")
                            v.setTextColor(R.id.tv_eta_status, Color.parseColor("#EF4444"))
                        }
                        state.isAfterTwilight -> {
                            v.setInt(R.id.eta_stripe, "setBackgroundColor", Color.parseColor("#F59E0B"))
                            v.setInt(R.id.cell_eta, "setBackgroundColor", Color.parseColor("#1a1400"))
                            v.setTextViewText(R.id.tv_eta_status, "⚠ po zmroku")
                            v.setTextColor(R.id.tv_eta_status, Color.parseColor("#F59E0B"))
                        }
                        state.etaTimestamp > 0L -> {
                            v.setInt(R.id.eta_stripe, "setBackgroundColor", Color.parseColor("#22C55E"))
                            v.setInt(R.id.cell_eta, "setBackgroundColor", Color.parseColor("#0F1929"))
                            val buf = ((state.deadlineTimestamp - state.etaTimestamp) / 60_000L).toInt().coerceAtLeast(0)
                            v.setTextViewText(R.id.tv_eta_status, "✓ +$buf min")
                            v.setTextColor(R.id.tv_eta_status, Color.parseColor("#22C55E"))
                        }
                        else -> {
                            // etaTimestamp == 0: albo brak trasy, albo za mało danych (< 30s ruchu)
                            v.setInt(R.id.eta_stripe, "setBackgroundColor", Color.parseColor("#2a3550"))
                            v.setInt(R.id.cell_eta, "setBackgroundColor", Color.parseColor("#0F1929"))
                            val statusText = if (!state.hasRoute) "brak trasy" else "obliczam…"
                            v.setTextViewText(R.id.tv_eta_status, statusText)
                            v.setTextColor(R.id.tv_eta_status, Color.parseColor("#8A96AD"))
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "etaStatus", e) }

                try {
                    v.setTextViewText(R.id.tv_required,
                        if (state.requiredSpeedKph > 0f && state.requiredSpeedKph < 999f)
                            "%.1f".format(state.requiredSpeedKph) else "--")
                    v.setTextColor(R.id.tv_required,
                        if (state.isOverDeadline || state.isAfterTwilight) Color.parseColor("#EF4444") else Color.WHITE)
                } catch (e: Exception) { Log.e(TAG, "required", e) }

                try {
                    v.setTextViewText(R.id.tv_deadline,
                        if (state.deadlineTimestamp > 0L) timeFmt.format(Date(state.deadlineTimestamp)) else "--:--")
                    v.setTextColor(R.id.tv_deadline,
                        if (state.isOverDeadline) Color.parseColor("#EF4444") else Color.WHITE)
                } catch (e: Exception) { Log.e(TAG, "deadline", e) }

                try {
                    v.setTextViewText(R.id.tv_twilight,
                        if (state.civilTwilightTimestamp > 0L) timeFmt.format(Date(state.civilTwilightTimestamp)) else "--:--")
                } catch (e: Exception) { Log.e(TAG, "twilight", e) }

                try { emitter.updateView(v) } catch (e: Exception) { Log.e(TAG, "updateView ETA", e) }
            }
        }
    }
}