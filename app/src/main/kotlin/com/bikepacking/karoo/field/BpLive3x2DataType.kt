package com.bikepacking.karoo.field

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import com.bikepacking.karoo.DataFreshness
import com.bikepacking.karoo.FreshnessTracker
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "BP_LIVE3X2"
private const val RENDER_TAG = "QBOT_RENDER"

private data class DemoScenario(
    val label: String,
    val speed: Float, val power: Int, val hr: Int,
    val cad: Int, val grade: Float, val front: Int, val rear: Int,
    val wBal: Int, val ftp: Int, val maxHr: Int
)

private val DEMO_SCENARIOS = listOf(
    DemoScenario("Nominal",     22.0f, 150, 125, 65,  1.0f, 40, 15, 80, 200, 180),
    DemoScenario("Speed+",      32.0f, 180, 135, 68, -1.0f, 50, 11, 75, 200, 180),
    DemoScenario("High power",  18.0f, 320, 165, 72,  6.0f, 34, 28, 45, 200, 180),
    DemoScenario("Grind",       12.0f, 250, 155, 45,  8.0f, 34, 36, 30, 200, 180),
    DemoScenario("Spinning",    35.0f,  80, 110, 95, -4.0f, 50, 11, 90, 200, 180),
    DemoScenario("All bad",      8.0f, 360, 185, 50, 14.0f, 34, 42,  5, 200, 180),
    DemoScenario("HR high",     20.0f, 200, 175, 65,  3.0f, 34, 28, 60, 200, 180),
    DemoScenario("Sweet spot",  25.0f, 170, 130, 63,  1.5f, 38, 19, 70, 200, 180),
)

class BpLive3x2DataType(
    private val rideEngine: RideEngine,
    extension: String
) : DataTypeImpl(extension, "BP_LIVE3X2") {

    private var viewScope: CoroutineScope? = null

    private val powerAvg = RollingAverage()
    private val hrAvg = RollingAverage()
    private val cadAvg = RollingAverage()

    companion object {
        private const val DEMO_MODE = false

        private val COLORS = mapOf(
            FieldState.GOOD to (0xFF0A2E1A).toInt(),
            FieldState.WARN to (0xFF7F1D1D).toInt(),
            FieldState.BAD to (0xFFB91C1C).toInt(),
        )

        fun live3x2Color(state: FieldState): Int =
            COLORS[state] ?: Color.TRANSPARENT

        fun genericValueTextColorForState(state: FieldState): Int = when (state) {
            FieldState.GOOD -> (0xFF22C55E).toInt()
            FieldState.WARN -> (0xFFF97316).toInt()
            FieldState.BAD -> (0xFFEF4444).toInt()
            FieldState.OK, FieldState.NEUTRAL -> Color.WHITE
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        FieldPreprocessor.reset()
        FieldClassifier.resetHysteresis()

        if (DEMO_MODE) {
            startDemo(context, scope, emitter)
        } else {
            startLive(context, scope, emitter)
        }
    }

    private fun startLive(context: Context, scope: CoroutineScope, emitter: ViewEmitter) {
        scope.launch {
            rideEngine.state.collectLatest { state ->
                try {
                    Log.d(RENDER_TAG, "LIVE bind start")
                    val v = RemoteViews(context.packageName, R.layout.field_live_3x2)
                    val nowMs = System.currentTimeMillis()
                    val ftp = rideEngine.settings.ftp
                    val maxHr = rideEngine.settings.maxHr
                    val todayProfile = TodayProfile.from(state.todayFactor)
                    val smoothedGrade = FieldPreprocessor.smoothedGrade(state.gradePercent)
                    val freshness = rideEngine.getFreshness()

                    // Debug: log HR values and freshness
                    val hrFresh = freshness.getFreshness("heartRate", nowMs)
                    Log.d(TAG, "QEXT_LIVE_RENDER hr=${state.heartRate} hrFresh=${hrFresh.name} " +
                        "speed=${state.speedKph} power=${state.powerWatts} cadence=${state.cadenceRpm}")

                    val smoothedPower = powerAvg.add(state.powerWatts.toFloat(), nowMs)
                    val smoothedHr = hrAvg.add(state.heartRate.toFloat(), nowMs)
                    val smoothedCad = cadAvg.add(state.cadenceRpm.toFloat(), nowMs)

                    val ctx = RideContext.from(
                        state = state,
                        smoothedPower = smoothedPower.toInt(),
                        smoothedHr = smoothedHr.toInt(),
                        smoothedCad = smoothedCad.toInt(),
                        smoothedGrade = smoothedGrade,
                        todayFactor = todayProfile.todayFactor,
                        ftp = ftp,
                        maxHr = maxHr,
                    )

                    // Speed - no background tint, color only value
                    val speedFresh = freshness.getFreshness("speed", nowMs)
                    val speedState = FieldClassifier.speed(ctx)
                    if (speedFresh == DataFreshness.MISSING) {
                        v.setTextViewText(R.id.tv_speed, "--")
                        v.setTextColor(R.id.tv_speed, Color.WHITE)
                        v.setInt(R.id.slot_speed, "setBackgroundColor", Color.TRANSPARENT)
                    } else {
                        v.setTextViewText(R.id.tv_speed, FieldFormatter.speed(ctx.rider.speedKph))
                        v.setTextColor(R.id.tv_speed, genericValueTextColorForState(speedState))
                        v.setInt(R.id.slot_speed, "setBackgroundColor", Color.TRANSPARENT)
                        if (speedFresh == DataFreshness.STALE) v.setFloat(R.id.slot_speed, "setAlpha", 0.5f)
                    }

                    // Power - no background tint, color only value
                    val powerFresh = freshness.getFreshness("power", nowMs)
                    val powerState = FieldClassifier.power(ctx)
                    if (powerFresh == DataFreshness.MISSING) {
                        v.setTextViewText(R.id.tv_power, "--")
                        v.setTextColor(R.id.tv_power, Color.WHITE)
                        v.setInt(R.id.slot_power, "setBackgroundColor", Color.TRANSPARENT)
                    } else {
                        v.setTextViewText(R.id.tv_power, FieldFormatter.power(ctx.rider.smoothedPowerWatts))
                        v.setTextColor(R.id.tv_power, genericValueTextColorForState(powerState))
                        v.setInt(R.id.slot_power, "setBackgroundColor", Color.TRANSPARENT)
                        if (powerFresh == DataFreshness.STALE) v.setFloat(R.id.slot_power, "setAlpha", 0.5f)
                    }

                    // HR
                    val hrAge = freshness.getAgeMs("hr", nowMs)
                    val hrState = FieldClassifier.hr(ctx.rider.smoothedHeartRate, ctx.maxHr, nowMs)
                    val hrAlpha = if (hrFresh == DataFreshness.STALE) 0.5f else 1.0f
                    Log.d(TAG, "HR_UI display=${if (hrFresh == DataFreshness.MISSING) "--" else FieldFormatter.hr(ctx.rider.smoothedHeartRate)} freshness=$hrFresh ageMs=$hrAge alpha=$hrAlpha")
                    if (hrFresh == DataFreshness.MISSING) {
                        v.setTextViewText(R.id.tv_hr, "--")
                        v.setTextColor(R.id.tv_hr, Color.WHITE)
                        v.setInt(R.id.slot_hr, "setBackgroundColor", Color.TRANSPARENT)
                    } else {
                        v.setTextViewText(R.id.tv_hr, FieldFormatter.hr(ctx.rider.smoothedHeartRate))
                        v.setTextColor(R.id.tv_hr, genericValueTextColorForState(hrState))
                        v.setInt(R.id.slot_hr, "setBackgroundColor", live3x2Color(hrState))
                        if (hrFresh == DataFreshness.STALE) v.setFloat(R.id.slot_hr, "setAlpha", 0.5f)
                    }

                    // Cadence
                    val cadFresh = freshness.getFreshness("cadence", nowMs)
                    val cadState = FieldClassifier.cadence(ctx.rider.smoothedCadenceRpm, ctx.rider.smoothedGradePercent)
                    if (cadFresh == DataFreshness.MISSING) {
                        v.setTextViewText(R.id.tv_cadence, "--")
                        v.setTextColor(R.id.tv_cadence, Color.WHITE)
                        v.setInt(R.id.slot_cadence, "setBackgroundColor", Color.TRANSPARENT)
                    } else {
                        v.setTextViewText(R.id.tv_cadence, FieldFormatter.cadence(ctx.rider.smoothedCadenceRpm))
                        v.setTextColor(R.id.tv_cadence, genericValueTextColorForState(cadState))
                        v.setInt(R.id.slot_cadence, "setBackgroundColor", live3x2Color(cadState))
                        if (cadFresh == DataFreshness.STALE) v.setFloat(R.id.slot_cadence, "setAlpha", 0.5f)
                    }

                    // Grade
                    val gradeFresh = freshness.getFreshness("grade", nowMs)
                    val gradeState = FieldClassifier.grade(ctx.rider.smoothedGradePercent)
                    if (gradeFresh == DataFreshness.MISSING) {
                        v.setTextViewText(R.id.tv_grade, "--")
                        v.setTextColor(R.id.tv_grade, Color.WHITE)
                        v.setInt(R.id.slot_grade, "setBackgroundColor", Color.TRANSPARENT)
                    } else {
                        v.setTextViewText(R.id.tv_grade, FieldFormatter.grade(ctx.rider.smoothedGradePercent))
                        v.setTextColor(R.id.tv_grade, genericValueTextColorForState(gradeState))
                        v.setInt(R.id.slot_grade, "setBackgroundColor", live3x2Color(gradeState))
                        if (gradeFresh == DataFreshness.STALE) v.setFloat(R.id.slot_grade, "setAlpha", 0.5f)
                    }

                    // Gear
                    val gearFresh = freshness.getFreshness("gear", nowMs)
                    val gearState = FieldClassifier.gear(ctx, nowMs)
                    if (gearFresh == DataFreshness.MISSING) {
                        v.setTextViewText(R.id.tv_gear, "--")
                        v.setTextColor(R.id.tv_gear, Color.WHITE)
                        v.setInt(R.id.slot_gear, "setBackgroundColor", Color.TRANSPARENT)
                    } else {
                        v.setTextViewText(R.id.tv_gear, FieldFormatter.gear(ctx.rider.frontTeeth, ctx.rider.rearTeeth))
                        v.setTextColor(R.id.tv_gear, genericValueTextColorForState(gearState))
                        v.setInt(R.id.slot_gear, "setBackgroundColor", live3x2Color(gearState))
                        if (gearFresh == DataFreshness.STALE) v.setFloat(R.id.slot_gear, "setAlpha", 0.5f)
                    }

                    val pwrResult = PowerAdvisor.assess(ctx)
                    val gearResult = GearAdvisor.assess(ctx)
                    Log.d(TAG, "pwr=%s[%d-%d] %s gear=%s %s".format(
                        pwrResult.state, pwrResult.targetLowWatts, pwrResult.targetHighWatts, pwrResult.reasonCode,
                        gearResult.state, gearResult.reasonCode,
                    ))

                    emitter.updateView(v)
                    Log.d(RENDER_TAG, "LIVE bind ok")
                } catch (t: Throwable) {
                    Log.e(RENDER_TAG, "LIVE render failed", t)
                    runCatching { emitter.updateView(fallbackRemoteViews(context, "LIVE ERR")) }
                        .onFailure { Log.e(RENDER_TAG, "LIVE fallback render failed", it) }
                }
            }
        }
    }

    private fun fallbackRemoteViews(context: Context, text: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.render_error).apply {
            setTextViewText(R.id.tv_render_error, text)
        }
    }

    private fun startDemo(context: Context, scope: CoroutineScope, emitter: ViewEmitter) {
        scope.launch {
            val v = RemoteViews(context.packageName, R.layout.field_live_3x2)
            var idx = 0

            while (isActive) {
                FieldPreprocessor.reset()
                FieldClassifier.resetHysteresis()
                powerAvg.reset()
                hrAvg.reset()
                cadAvg.reset()
                val nowMs = System.currentTimeMillis()
                val s = DEMO_SCENARIOS[idx]
                val smoothedGrade = FieldPreprocessor.smoothedGrade(s.grade)

                val smoothedPower = powerAvg.add(s.power.toFloat(), nowMs)
                val smoothedHr = hrAvg.add(s.hr.toFloat(), nowMs)
                val smoothedCad = cadAvg.add(s.cad.toFloat(), nowMs)

                val ctx = RideContext.from(
                    state = demoRideState(s),
                    smoothedPower = smoothedPower.toInt(),
                    smoothedHr = smoothedHr.toInt(),
                    smoothedCad = smoothedCad.toInt(),
                    smoothedGrade = smoothedGrade,
                    todayFactor = 1.0f,
                    ftp = s.ftp,
                    maxHr = s.maxHr,
                )

                val speedState = classifySpeedDemo(s.speed)
                v.setTextViewText(R.id.tv_speed, FieldFormatter.speed(s.speed))
                setSlotGeneric(v, R.id.slot_speed, R.id.tv_speed, speedState)

                val powerState = FieldClassifier.power(ctx)
                v.setTextViewText(R.id.tv_power, FieldFormatter.power(smoothedPower.toInt()))
                setSlotGeneric(v, R.id.slot_power, R.id.tv_power, powerState)

                val hrState = FieldClassifier.hr(smoothedHr.toInt(), s.maxHr, nowMs)
                v.setTextViewText(R.id.tv_hr, FieldFormatter.hr(smoothedHr.toInt()))
                setSlotGeneric(v, R.id.slot_hr, R.id.tv_hr, hrState)

                val cadState = FieldClassifier.cadence(smoothedCad.toInt(), smoothedGrade)
                v.setTextViewText(R.id.tv_cadence, FieldFormatter.cadence(smoothedCad.toInt()))
                setSlotGeneric(v, R.id.slot_cadence, R.id.tv_cadence, cadState)

                val gradeState = FieldClassifier.grade(smoothedGrade)
                v.setTextViewText(R.id.tv_grade, FieldFormatter.grade(smoothedGrade))
                setSlotGeneric(v, R.id.slot_grade, R.id.tv_grade, gradeState)

                val gearState = FieldClassifier.gear(ctx, nowMs)
                v.setTextViewText(R.id.tv_gear, FieldFormatter.gear(s.front, s.rear))
                setSlotGeneric(v, R.id.slot_gear, R.id.tv_gear, gearState)

                Log.d("BP_LIVE3X2", "scenario=${s.label} gR=%.1f gD=%s gS=%s gear=%s".format(
                    smoothedGrade, FieldFormatter.grade(smoothedGrade), gradeState, gearState
                ))

                emitter.updateView(v)

                idx = (idx + 1) % DEMO_SCENARIOS.size
                delay(2_500L)
            }
        }
    }

    private fun demoRideState(s: DemoScenario): com.bikepacking.karoo.RideState =
        com.bikepacking.karoo.RideState(
            speedKph = s.speed,
            powerWatts = s.power,
            heartRate = s.hr,
            cadenceRpm = s.cad,
            gradePercent = s.grade,
            frontTeeth = s.front,
            rearTeeth = s.rear,
        )

    private fun classifySpeedDemo(kph: Float): FieldState {
        if (kph < 1f) return FieldState.NEUTRAL
        return when {
            kph > 25f -> FieldState.GOOD
            kph < 15f -> FieldState.BAD
            else -> FieldState.OK
        }
    }

    private fun setSlotGeneric(v: RemoteViews, slotId: Int, textId: Int, state: FieldState) {
        v.setInt(slotId, "setBackgroundColor", live3x2Color(state))
        v.setTextColor(textId, genericValueTextColorForState(state))
    }
}
