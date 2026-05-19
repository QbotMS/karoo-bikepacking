package com.bikepacking.karoo.datatypes

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.bikepacking.karoo.R
import com.bikepacking.karoo.RideEngine
import com.bikepacking.karoo.field.StatsFormattedValue
import com.bikepacking.karoo.field.StatsValueFormatter
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

private const val TAG = "BP_STATS"
private const val RENDER_TAG = "QBOT_RENDER"

class BpStatsDataType(
    private val rideEngine: RideEngine,
    extension: String
) : DataTypeImpl(extension, "BP_STATS") {

    private var viewScope: CoroutineScope? = null

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        scope.launch {
            rideEngine.state.collectLatest { state ->
                try {
                    Log.d(RENDER_TAG, "STATS bind start")
                    val v = RemoteViews(context.packageName, R.layout.field_stats)

                    // ROW 1: NP | IF | VI
                    bindUnit(v, R.id.tv_np, R.id.tv_np_unit, StatsValueFormatter.npW(state.npWholeWatts))
                    bindUnit(v, R.id.tv_if, R.id.tv_if_unit, StatsValueFormatter.ifValue(state.ifWholeRide))
                    bindUnit(v, R.id.tv_vi, R.id.tv_vi_unit, StatsValueFormatter.vi(state.viValue))
                    v.setTextColor(R.id.tv_vi, viColor(state.viValue))

                    // ROW 2: CARB IN | FLUID IN | KCAL
                    bindUnit(v, R.id.tv_carb, R.id.tv_carb_unit, StatsValueFormatter.carbsG(state.carbsGPerH))
                    bindUnit(v, R.id.tv_fluid, R.id.tv_fluid_unit, StatsValueFormatter.fluidL(state.fluidLPerH))
                    bindUnit(v, R.id.tv_cal, R.id.tv_cal_unit, StatsValueFormatter.calories(state.caloriesKcal))

                    // ROW 3: TSS | ΔV | RSRV
                    bindUnit(v, R.id.tv_tss, R.id.tv_tss_unit, StatsValueFormatter.tss(state.tssValue))

                    // ΔV - deadline delta
                    val dltavStatus = state.deadlineStatus
                    val dltavDelta = state.deadlineDeltaKph
                    val dltavFormatted = if (dltavStatus == "--" || state.etaTimestamp <= 0L || state.deadlineTimestamp <= 0L) {
                        StatsFormattedValue("--")
                    } else {
                        StatsValueFormatter.deadlineDeltaValue(dltavDelta, dltavStatus)
                    }
                    bindUnit(v, R.id.tv_dltav, R.id.tv_dltav_unit, dltavFormatted)
                    // Color only the value, not background
                    val dltavColor = when {
                        dltavStatus == "--" -> Color.WHITE
                        dltavStatus == "OK" || dltavDelta <= 0f -> Color.parseColor("#22C55E") // green
                        dltavDelta > 2.5f || dltavStatus == "LATE" || dltavStatus == "IMPOSSIBLE" -> Color.parseColor("#EF4444") // red
                        dltavDelta > 1.0f -> Color.parseColor("#F97316") // orange
                        else -> Color.parseColor("#F59E0B") // amber
                    }
                    v.setTextColor(R.id.tv_dltav, dltavColor)

                    bindUnit(v, R.id.tv_rsrv, R.id.tv_rsrv_unit, StatsValueFormatter.reserveNumber(state.rideReservePercent))
                    v.setTextColor(R.id.tv_rsrv, reserveColor(state.rideReservePercent))

                    // ROW 4: UP | UP LEFT | ETA
                    bindUnit(v, R.id.tv_ascent_done, R.id.tv_ascent_done_unit, StatsValueFormatter.ascentM(state.ascentDoneM))
                    bindUnit(v, R.id.tv_ascent_left, R.id.tv_ascent_left_unit,
                        StatsValueFormatter.ascentLeftM(state.ascentLeftM, state.hasRoute))
                    bindUnit(v, R.id.tv_eta, R.id.tv_eta_unit, StatsValueFormatter.etaTime(state.etaTimestamp))

                    // ROW 5: V AVG TOTAL | TIME TOTAL | TIME STOP
                    val elapsedAvg = if (state.distanceKm > 0f && state.elapsedSec > 0L) {
                        (state.distanceKm / (state.elapsedSec / 3600f)).coerceIn(0f, 120f)
                    } else 0f
                    bindUnit(v, R.id.tv_all, R.id.tv_all_unit, StatsValueFormatter.avgAll(elapsedAvg))
                    bindUnit(v, R.id.tv_mov, R.id.tv_mov_unit, StatsValueFormatter.elapsedTime(state.elapsedSec))
                    val stoppedSec = (state.elapsedSec - state.movingSec).coerceAtLeast(0L)
                    bindUnit(v, R.id.tv_stop, R.id.tv_stop_unit, StatsValueFormatter.stopTime(stoppedSec))

                    // ROW 6: BURN BAT | BAT LEFT | RD BAT
                    bindUnit(v, R.id.tv_bat, R.id.tv_bat_unit, StatsValueFormatter.batteryPerHour(state.batteryDropPerHour))
                    bindUnit(v, R.id.tv_left, R.id.tv_left_unit, StatsValueFormatter.batteryRuntime(state.batteryRuntimeSec))
                    bindUnit(v, R.id.tv_tmp, R.id.tv_tmp_unit, StatsValueFormatter.rdBat(state.rearDerailleurBatteryPercent))

                    emitter.updateView(v)
                    Log.d(RENDER_TAG, "STATS bind ok")

                } catch (t: Throwable) {
                    Log.e(RENDER_TAG, "STATS render failed", t)
                    runCatching { emitter.updateView(fallbackRemoteViews(context, "STATS ERR")) }
                        .onFailure { Log.e(RENDER_TAG, "STATS fallback render failed", it) }
                }
            }
        }
    }

    private fun fallbackRemoteViews(context: Context, text: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.render_error).apply {
            setTextViewText(R.id.tv_render_error, text)
        }
    }

    private fun bindUnit(v: RemoteViews, valueId: Int, unitId: Int, fv: StatsFormattedValue) {
        v.setTextViewText(valueId, fv.main)
        if (fv.unit != null && fv.main != "--") {
            v.setTextViewText(unitId, fv.unit)
            v.setViewVisibility(unitId, View.VISIBLE)
        } else {
            v.setViewVisibility(unitId, View.GONE)
        }
    }

    private fun viColor(vi: Float): Int = when {
        vi <= 0f   -> Color.WHITE
        vi < 1.05f -> Color.WHITE
        vi < 1.10f -> Color.parseColor("#F59E0B")
        else       -> Color.parseColor("#EF4444")
    }

    private fun decouplingColor(dec: Float): Int = when {
        dec < 3f  -> Color.parseColor("#4ADE80")
        dec < 7f  -> Color.parseColor("#F59E0B")
        else      -> Color.parseColor("#EF4444")
    }

    private fun reserveColor(reserve: Int): Int = when {
        reserve >= 40 -> Color.parseColor("#22C55E")
        reserve >= 20 -> Color.parseColor("#F59E0B")
        else          -> Color.parseColor("#EF4444")
    }
}
