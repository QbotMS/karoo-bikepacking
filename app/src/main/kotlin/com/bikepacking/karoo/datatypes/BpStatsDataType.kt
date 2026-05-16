package com.bikepacking.karoo.datatypes

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
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

class BpStatsDataType(
    private val rideEngine: RideEngine,
    extension: String
) : DataTypeImpl(extension, "BP_STATS") {

    private var viewScope: CoroutineScope? = null

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // Anuluj poprzedni scope — zapobiega duplikacji coroutines
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        // Ukryj nagłówek Karoo OS (tak jak BP LIVE i BP ETA)
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        scope.launch {
            rideEngine.state.collectLatest { state ->
                try {
                    val v = RemoteViews(context.packageName, R.layout.field_stats)

                    // ── WIERSZ 1: NP | IF | VI ─────────────────────────────
                    v.setTextViewText(R.id.tv_np,
                        if (state.npWholeWatts > 0) "${state.npWholeWatts}" else "--")

                    v.setTextViewText(R.id.tv_if,
                        if (state.ifWholeRide > 0f) "%.2f".format(state.ifWholeRide) else "--")

                    v.setTextViewText(R.id.tv_vi,
                        if (state.viValue > 0f) "%.2f".format(state.viValue) else "--")
                    // VI kolor: zielony gdy <1.05, żółty 1.05-1.10, czerwony >1.10
                    v.setTextColor(R.id.tv_vi, viColor(state.viValue))

                    // ── WIERSZ 2: TSS | CALORIES | DECOUPLING ──────────────
                    v.setTextViewText(R.id.tv_tss,
                        if (state.tssValue > 0f) "${state.tssValue.toInt()}" else "--")

                    v.setTextViewText(R.id.tv_calories,
                        if (state.caloriesKcal > 0) "${state.caloriesKcal}" else "--")

                    val decStr = if (state.decouplingPercent != 0f)
                        "%+.1f".format(state.decouplingPercent) else "--"
                    v.setTextViewText(R.id.tv_decoupling, decStr)
                    v.setTextColor(R.id.tv_decoupling, decouplingColor(state.decouplingPercent))

                    // ── WIERSZ 3: D+ DONE | D+ LEFT | TIME LEFT ────────────
                    v.setTextViewText(R.id.tv_ascent_done,
                        if (state.ascentDoneM > 0) "${state.ascentDoneM}" else "--")

                    v.setTextViewText(R.id.tv_ascent_left,
                        if (state.hasRoute) "${state.ascentLeftM}" else "--")

                    v.setTextViewText(R.id.tv_time_left,
                        if (state.hasRoute && state.timeToFinishSec > 0L)
                            formatTime(state.timeToFinishSec) else "--")

                    // ── WIERSZ 4: CARBS | FLUID | STOPPED ─────────────────
                    v.setTextViewText(R.id.tv_carbs,
                        if (state.carbsGPerH > 0) "${state.carbsGPerH}" else "--")

                    v.setTextViewText(R.id.tv_fluid,
                        if (state.fluidLPerH > 0f) "%.2f".format(state.fluidLPerH) else "--")

                    val stoppedMin = ((state.elapsedSec - state.movingSec) / 60L).coerceAtLeast(0L)
                    v.setTextViewText(R.id.tv_stopped, "$stoppedMin")

                    // ── WIERSZ 5: RIDE RESERVE | TODAY FACTOR ─────────────
                    val reserve = state.rideReservePercent
                    val reserveStr = if (reserve >= 0) "${reserve}%" else "${reserve}%"
                    v.setTextViewText(R.id.tv_reserve, reserveStr)
                    v.setTextColor(R.id.tv_reserve, reserveColor(reserve))

                    // Pasek postępu — ProgressBar (RemoteViews safe)
                    val progress = reserve.coerceIn(0, 100)
                    v.setProgressBar(R.id.reserve_progress, 100, progress, false)

                    v.setTextViewText(R.id.tv_today_factor,
                        "%.2f".format(state.todayFactor))

                    emitter.updateView(v)

                } catch (_: Exception) {
                    // Cichy catch — nie crashujemy pola przy błędzie renderowania
                }
            }
        }
    }

    // ── Kolory ────────────────────────────────────────────────────────────────

    private fun viColor(vi: Float): Int = when {
        vi <= 0f   -> Color.WHITE
        vi < 1.05f -> Color.WHITE
        vi < 1.10f -> Color.parseColor("#F59E0B")  // żółty
        else       -> Color.parseColor("#EF4444")  // czerwony
    }

    private fun decouplingColor(dec: Float): Int = when {
        dec < 3f  -> Color.parseColor("#4ADE80")   // zielony — ok
        dec < 7f  -> Color.parseColor("#F59E0B")   // żółty — uwaga
        else      -> Color.parseColor("#EF4444")   // czerwony — problem
    }

    private fun reserveColor(reserve: Int): Int = when {
        reserve >= 40 -> Color.parseColor("#22C55E")   // zielony
        reserve >= 20 -> Color.parseColor("#F59E0B")   // pomarańczowy
        else          -> Color.parseColor("#EF4444")   // czerwony — opary
    }

    // ── Formatowanie czasu h:mm lub mm min ────────────────────────────────────
    private fun formatTime(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}:${m.toString().padStart(2, '0')}" else "${m}min"
    }
}