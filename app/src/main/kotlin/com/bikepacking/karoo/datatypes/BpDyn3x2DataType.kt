package com.bikepacking.karoo.datatypes

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import android.util.Log
import com.bikepacking.karoo.DataFreshness
import com.bikepacking.karoo.R
import com.bikepacking.karoo.RideEngine
import com.bikepacking.karoo.field.DynValueFormatter
import com.bikepacking.karoo.message.RideMessage
import com.bikepacking.karoo.message.RideMessageRenderer
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

private const val TAG = "DYN4x2"
private const val RENDER_TAG = "QBOT_RENDER"

class BpDyn3x2DataType(
    private val rideEngine: RideEngine,
    extension: String,
) : DataTypeImpl(extension, "BP_DYN3X2") {

    private var viewScope: CoroutineScope? = null

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        scope.launch {
            rideEngine.state.collectLatest { state ->
                try {
                    Log.d(RENDER_TAG, "DYN bind start")
                    val v = RemoteViews(context.packageName, R.layout.field_dyn_3x2)
                    val msg = state.activeRideMessage

                    if (msg != null) {
                        bindMessage(v, msg)
                    } else {
                        bindNormal(v, state, context)
                    }
                    emitter.updateView(v)
                    Log.d(RENDER_TAG, "DYN bind ok")
                } catch (t: Throwable) {
                    Log.e(RENDER_TAG, "DYN render failed", t)
                    runCatching { emitter.updateView(fallbackRemoteViews(context, "DYN ERR")) }
                        .onFailure { Log.e(RENDER_TAG, "DYN fallback render failed", it) }
                }
            }
        }
    }

    private fun fallbackRemoteViews(context: Context, text: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.render_error).apply {
            setTextViewText(R.id.tv_render_error, text)
        }
    }

    // ── message mode ──────────────────────────────────────

    private val allSlotIds = intArrayOf(
        R.id.slot_if10, R.id.slot_hrd, R.id.slot_wbal,
        R.id.slot_dtd, R.id.slot_avg, R.id.slot_tmp, R.id.slot_wind,
    )

    private fun bindMessage(v: RemoteViews, msg: RideMessage) {
        for (slotId in allSlotIds) {
            v.setViewVisibility(slotId, View.GONE)
        }
        v.setViewVisibility(R.id.slot_dist, View.VISIBLE)

        RideMessageRenderer.render(
            v = v,
            msg = msg,
            containerId = R.id.slot_dist,
            line1Id = R.id.tv_dist,
            line2Id = R.id.tv_label_dist,
        )
    }

    // ── normal mode (real data) ──────────────────────────

    private fun bindNormal(v: RemoteViews, state: com.bikepacking.karoo.RideState, context: Context) {
        for (slotId in allSlotIds) {
            v.setViewVisibility(slotId, View.VISIBLE)
        }
        v.setViewVisibility(R.id.slot_dist, View.VISIBLE)
        v.setInt(R.id.slot_dist, "setBackgroundColor", DynValueFormatter.colorInt("#111827"))
        v.setFloat(R.id.tv_dist, "setTextSize", 22f)
        v.setFloat(R.id.tv_label_dist, "setTextSize", 8f)
        v.setInt(R.id.tv_dist, "setGravity", Gravity.CENTER)
        v.setInt(R.id.tv_label_dist, "setGravity", Gravity.START or Gravity.TOP)
        bindDataValues(v, state, context)
    }

    // ── Spannable helpers ────────────────────────────────

    /** IF10 -> "IF" normal + "10" at ~72% size */
    private fun labelIf10(): SpannableString {
        val text = "IF10"
        return SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.72f), 2, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** NP10 -> "NP" normal + "10" at ~72% size */
    private fun labelNp10(): SpannableString {
        val text = "NP10"
        return SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.72f), 2, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** "82%" -> "82" normal + "%" at ~65% size */
    private fun valueWithPercent(value: String): SpannableString {
        val pctIdx = value.indexOf('%')
        if (pctIdx < 0) return SpannableString(value)
        return SpannableString(value).apply {
            setSpan(RelativeSizeSpan(0.65f), pctIdx, pctIdx + 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** "12.4" -> "12" normal + ".4" at -2sp (20sp if base is 22sp) */
    private fun valueWithDecimalSmaller(value: String, baseSp: Int = 22): SpannableString {
        val dotIdx = value.indexOf('.')
        if (dotIdx < 0) return SpannableString(value)
        val decimalSp = (baseSp - 2).coerceAtLeast(12)
        return SpannableString(value).apply {
            setSpan(AbsoluteSizeSpan(decimalSp, true), dotIdx, value.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // ── data binding ─────────────────────────────────────

    private fun bindDataValues(v: RemoteViews, state: com.bikepacking.karoo.RideState, context: Context) {
        val nowMs = System.currentTimeMillis()
        val freshness = rideEngine.getFreshness()

        // D — distance done with smaller decimal
        try {
            val d = DynValueFormatter.distanceDone(state.distanceKm)
            v.setTextViewText(R.id.tv_dist, valueWithDecimalSmaller(d.value))
            v.setTextColor(R.id.tv_dist, DynValueFormatter.colorInt(d.colorHex))
        } catch (e: Exception) { Log.e(TAG, "dist bind failed", e) }

        try {
            val f = DynValueFormatter.if10(state.if10Value)
            v.setTextViewText(R.id.tv_if10, f.value)
            v.setTextColor(R.id.tv_if10, DynValueFormatter.colorInt(f.colorHex))
        } catch (e: Exception) { Log.e(TAG, "if10 bind failed", e) }

        // HRD — Local HR Drift/Strain
        try {
            val hrdStatus = state.hrdStatus
            val hrdPct = state.hrdPct
            val display = when (hrdStatus) {
                "WAIT" -> "WAIT"
                "BASE" -> "BASE"
                "INVALID" -> "--"
                "OK" -> "OK"
                "+" -> "+${hrdPct.toInt()}%"
                "++" -> "++${hrdPct.toInt()}%"
                "HOT" -> "HOT"
                else -> "--"
            }
            val colorHex = when (hrdStatus) {
                "WAIT", "BASE", "INVALID" -> "#94A3B8"  // gray
                "OK" -> "#22C55E"  // green
                "+" -> "#F59E0B"  // amber
                "++" -> "#F97316"  // orange
                "HOT" -> "#EF4444"  // red
                else -> "#94A3B8"
            }
            v.setTextViewText(R.id.tv_hrd, display)
            v.setTextColor(R.id.tv_hrd, DynValueFormatter.colorInt(colorHex))
        } catch (e: Exception) { Log.e(TAG, "hrd bind failed", e) }

        // W' — value with smaller %, neutral background
        try {
            val f = DynValueFormatter.wbal(state.wBalancePercent)
            v.setTextViewText(R.id.tv_wbal, valueWithPercent(f.value))
            v.setTextColor(R.id.tv_wbal, DynValueFormatter.colorInt(f.colorHex))
            v.setInt(R.id.slot_wbal, "setBackgroundColor", Color.TRANSPARENT)
        } catch (e: Exception) { Log.e(TAG, "wbal bind failed", e) }

        // DTD — distance to dest with smaller decimal
        try {
            val f = DynValueFormatter.distanceToDest(state.remainingKm, state.hasRoute)
            v.setTextViewText(R.id.tv_dtd, valueWithDecimalSmaller(f.value))
            v.setTextColor(R.id.tv_dtd, DynValueFormatter.colorInt(f.colorHex))
        } catch (e: Exception) { Log.e(TAG, "dtd bind failed", e) }

        // Vśr — avg speed with smaller decimal
        try {
            val f = DynValueFormatter.avgNetSpeed(state.smartAvgNetKph)
            v.setTextViewText(R.id.tv_avg, valueWithDecimalSmaller(f.value))
            v.setTextColor(R.id.tv_avg, DynValueFormatter.colorInt(f.colorHex))
        } catch (e: Exception) { Log.e(TAG, "avg bind failed", e) }

        // T — temperature with freshness
        try {
            val tempFresh = freshness.getFreshness("temp", nowMs)
            if (tempFresh == DataFreshness.MISSING) {
                v.setTextViewText(R.id.tv_tmp, "--")
                v.setTextColor(R.id.tv_tmp, Color.WHITE)
            } else {
                val f = DynValueFormatter.temperature(state.temperatureCelsius)
                v.setTextViewText(R.id.tv_tmp, f.value)
                v.setTextColor(R.id.tv_tmp, DynValueFormatter.colorInt(f.colorHex))
                if (tempFresh == DataFreshness.STALE) v.setFloat(R.id.slot_tmp, "setAlpha", 0.5f)
            }
        } catch (e: Exception) { Log.e(TAG, "tmp bind failed", e) }

        // W — wind value + background tint with freshness
        try {
            val windFresh = freshness.getFreshness("wind", nowMs)
            if (windFresh == DataFreshness.MISSING) {
                v.setTextViewText(R.id.tv_wind, "--")
                v.setTextColor(R.id.tv_wind, Color.WHITE)
                v.setInt(R.id.slot_wind, "setBackgroundColor", DynValueFormatter.colorInt("#111827"))
            } else {
                val f = DynValueFormatter.windWithError(state.windArrow, state.windSpeedMs, state.windImpactKph, state.headwindError)
                v.setTextViewText(R.id.tv_wind, f.value)
                v.setTextColor(R.id.tv_wind, DynValueFormatter.colorInt(f.colorHex))
                val windBg = f.bgHex ?: DynValueFormatter.windBgColor(state.windImpactKph)
                v.setInt(R.id.slot_wind, "setBackgroundColor", DynValueFormatter.colorInt(windBg))
                if (windFresh == DataFreshness.STALE) v.setFloat(R.id.slot_wind, "setAlpha", 0.5f)
            }
        } catch (e: Exception) { Log.e(TAG, "wind bind failed", e) }

        // Labels — top-left, small, technical
        try { v.setTextViewText(R.id.tv_label_dist, "D") } catch (e: Exception) { Log.e(TAG, "label dist failed", e) }
        try { v.setTextViewText(R.id.tv_label_if10, labelIf10()) } catch (e: Exception) { Log.e(TAG, "label if10 failed", e) }
        try { v.setTextViewText(R.id.tv_label_hrd, "") } catch (e: Exception) { }
        try { v.setTextViewText(R.id.tv_label_wbal, "W'") } catch (e: Exception) { Log.e(TAG, "label wbal failed", e) }
        try { v.setTextViewText(R.id.tv_label_dtd, "DTD") } catch (e: Exception) { Log.e(TAG, "label dtd failed", e) }
        try { v.setTextViewText(R.id.tv_label_avg, "Vsr") } catch (e: Exception) { Log.e(TAG, "label avg failed", e) }
        try { v.setTextViewText(R.id.tv_label_tmp, "T") } catch (e: Exception) { Log.e(TAG, "label tmp failed", e) }
        try { v.setTextViewText(R.id.tv_label_wind, "W") } catch (e: Exception) { Log.e(TAG, "label wind failed", e) }
    }

    // ── color helpers removed — now in DynValueFormatter ──
}
