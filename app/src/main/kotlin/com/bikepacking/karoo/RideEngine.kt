package com.bikepacking.karoo

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

class RideEngine(
    val karooSystem: KarooSystemService,
    val settings: AppSettings,
    val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val etaCalculator = EtaCalculator()
    private val statsCalc = StatsCalculator(settings)

    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    @Volatile private var speedKph = 0f
    @Volatile private var powerWatts = 0
    @Volatile private var heartRate = 0
    @Volatile private var cadenceRpm = 0
    @Volatile private var gradePercent = 0f
    @Volatile private var distanceM = 0f
    @Volatile private var remainingM = 0f
    @Volatile private var elapsedSec = 0L
    @Volatile private var movingSec = 0L
    @Volatile private var movingSecManual = 0L
    @Volatile private var temperatureCelsius: Float? = null
    @Volatile private var frontTeeth = 0
    @Volatile private var rearTeeth = 0

    private val power10mHistory = ArrayDeque<Pair<Long, Int>>()
    private val power30mHistory = ArrayDeque<Pair<Long, Int>>()
    private val cadence30sHistory = ArrayDeque<Pair<Long, Int>>()

    @Volatile private var lat = 52.23
    @Volatile private var lon = 21.01
    @Volatile private var headwindKph = 0f
    @Volatile private var windSpeedMs = 0f
    @Volatile private var headwindBearingDeg = -1f

    @Volatile private var lastAltitude = 0f
    @Volatile private var altitudeInitialized = false
    @Volatile private var ascentDoneM = 0
    @Volatile private var ascentLeftM = 0

    @Volatile private var readiness = ReadinessManager.loadCached(context)

    fun start() {
        scope.launch {
            val fresh = ReadinessManager.fetch(context)
            readiness = ReadinessManager.applyBaroAdjustment(fresh, settings.baroSensitive)
            if (fresh.ftpWatts > 0f) settings.ftp = fresh.ftpWatts.toInt()
            statsCalc.todayFactor     = readiness.todayFactor
            statsCalc.ctl             = readiness.ctl
            statsCalc.bodyWeightKg    = readiness.bodyWeightKg
            statsCalc.humidityPercent = readiness.humidityPercent
            if (fresh.wPrimeKj > 0f && fresh.ltpWatts > 0f) {
                statsCalc.setWPrimeParams(fresh.wPrimeKj, fresh.ltpWatts)
            }
        }

        addStream(DataType.Type.SPEED)                    { v, _ -> speedKph = (v * 3.6).toFloat() }
        addStream(DataType.Type.HEART_RATE)               { v, _ -> heartRate = v.toInt() }
        addStream(DataType.Type.POWER)                    { v, nowMs ->
            powerWatts = v.toInt()
            power10mHistory.addLast(nowMs to powerWatts)
            while (power10mHistory.isNotEmpty() && power10mHistory.first().first < nowMs - 600_000L)
                power10mHistory.removeFirst()
            power30mHistory.addLast(nowMs to powerWatts)
            while (power30mHistory.isNotEmpty() && power30mHistory.first().first < nowMs - 1_800_000L)
                power30mHistory.removeFirst()
        }
        addStream(DataType.Type.CADENCE)                  { v, nowMs ->
            cadenceRpm = v.toInt()
            cadence30sHistory.addLast(nowMs to cadenceRpm)
            while (cadence30sHistory.isNotEmpty() && cadence30sHistory.first().first < nowMs - 30_000L)
                cadence30sHistory.removeFirst()
        }
        addStream(DataType.Type.GRADE)                    { v, _ -> gradePercent = v.toFloat() }
        addStream(DataType.Type.DISTANCE)                 { v, _ -> distanceM = v.toFloat() }
        addStream(DataType.Type.ELAPSED_TIME)             { v, _ -> elapsedSec = v.toLong() }
        addStream(DataType.Type.DISTANCE_TO_DESTINATION)  { v, _ -> remainingM = v.toFloat() }
        addStream("ELAPSED_MOVING_TIME")                  { v, _ -> movingSec = v.toLong() }
        addStream(DataType.Type.TEMPERATURE)              { v, _ -> temperatureCelsius = v.toFloat() }
        addStream("TYPE_FRONT_GEAR_TEETH")                { v, _ -> frontTeeth = v.toInt() }
        addStream("TYPE_REAR_GEAR_TEETH")                 { v, _ -> rearTeeth = v.toInt() }

        addStreamFull(DataType.Type.LOCATION) { dp ->
            dp.values["lat"]?.let { lat = it }
            dp.values["lon"]?.let { lon = it }
            dp.values["longitude"]?.let { lon = it }
            dp.values["latitude"]?.let { lat = it }
        }

        addStream("TYPE_EXT::karoo-headwind::headwindSpeed") { v, _ -> headwindKph = (v * 3.6).toFloat() }
        addStream("TYPE_EXT::karoo-headwind::windSpeed")     { v, _ -> windSpeedMs = v.toFloat() }
        addStream("TYPE_EXT::karoo-headwind::headwind")      { v, _ -> headwindBearingDeg = v.toFloat() }

        addStream("ALTITUDE") { v, _ ->
            val alt = v.toFloat()
            if (!altitudeInitialized) { lastAltitude = alt; altitudeInitialized = true }
            else { val d = alt - lastAltitude; if (d > 2f) ascentDoneM += d.toInt(); lastAltitude = alt }
        }

        scope.launch { while (isActive) { updateState(); delay(1_000L) } }
    }

    private fun addStream(typeId: String, onValue: (Double, Long) -> Unit) {
        karooSystem.addConsumer<OnStreamState>(OnStreamState.StartStreaming(dataTypeId = typeId)) { event ->
            val s = event.state as? StreamState.Streaming ?: return@addConsumer
            val value = s.dataPoint.singleValue ?: return@addConsumer
            onValue(value, System.currentTimeMillis())
        }
    }

    private fun addStreamFull(typeId: String, onDataPoint: (io.hammerhead.karooext.models.DataPoint) -> Unit) {
        karooSystem.addConsumer<OnStreamState>(OnStreamState.StartStreaming(dataTypeId = typeId)) { event ->
            val s = event.state as? StreamState.Streaming ?: return@addConsumer
            onDataPoint(s.dataPoint)
        }
    }

    private fun windArrow(d: Float): String {
        if (d < 0f) return "-"
        val a = ((d % 360f) + 360f) % 360f
        return when {
            a < 22.5f || a >= 337.5f -> "↓"; a < 67.5f -> "↘"; a < 112.5f -> "→"
            a < 157.5f -> "↗"; a < 202.5f -> "↑"; a < 247.5f -> "↖"
            a < 292.5f -> "←"; else -> "↙"
        }
    }

    private fun calcNP(history: ArrayDeque<Pair<Long, Int>>): Int {
        if (history.size < 10) return 0
        return history.map { it.second.toDouble().pow(4) }.average().pow(0.25).toInt()
    }

    private fun updateState() {
        val nowMs = System.currentTimeMillis()
        val distanceKm = distanceM / 1000f
        val remainingKm = remainingM / 1000f
        val hasRoute = remainingM > 0f
        val isMoving = speedKph > 1.0f

        if (isMoving) movingSecManual++
        val effectiveMovingSec = if (movingSec > 0L) movingSec else movingSecManual

        etaCalculator.update(nowMs, speedKph, isMoving, effectiveMovingSec * 1000L, elapsedSec * 1000L)

        val smartAvgNet   = if (effectiveMovingSec > 0) distanceKm / (effectiveMovingSec / 3600f) else 0f
        val smartAvgGross = if (elapsedSec > 0) distanceKm / (elapsedSec / 3600f) else 0f
        val smartAvg      = etaCalculator.smartAvgKph(smartAvgNet)
        val trend         = etaCalculator.speedTrend(smartAvg)

        val ftp    = settings.ftp
        val np10   = if (power10mHistory.size >= 10) calcNP(power10mHistory) else powerWatts
        val ifVal  = if (ftp > 0 && np10 > 0) np10.toFloat() / ftp else 0f
        val np30   = calcNP(power30mHistory)
        val if30   = if (ftp > 0 && np30 > 0) np30.toFloat() / ftp else 0f
        val cad30s = if (cadence30sHistory.isNotEmpty())
            cadence30sHistory.map { it.second }.average().toInt() else cadenceRpm

        val twilightMs     = SunCalculator.civilTwilightMs(lat, lon, nowMs)
        val deadlineMs     = minOf(settings.deadlineTodayMs(), twilightMs)
        val predictedSpeed = if (smartAvg > 0f) smartAvg else speedKph
        val etaMs          = if (hasRoute && predictedSpeed > 0f)
            etaCalculator.calculateEtaMs(nowMs, remainingKm, distanceKm, predictedSpeed) else 0L
        val predictedStops = etaCalculator.predictedStopsSec(remainingKm, distanceKm)
        val requiredSpeed  = if (hasRoute && deadlineMs > nowMs)
            etaCalculator.requiredSpeedKph(nowMs, remainingKm, deadlineMs, predictedStops) else 0f
        val isOverDeadline  = etaMs > 0L && deadlineMs > 0L && etaMs > deadlineMs
        val isAfterTwilight = etaMs > 0L && twilightMs > 0L && etaMs > twilightMs

        statsCalc.update(powerWatts, heartRate, effectiveMovingSec, elapsedSec)

        val npWhole    = statsCalc.npWatts()
        val ifWhole    = statsCalc.ifValue()
        val vi         = statsCalc.viValue()
        val tss        = statsCalc.tssValue(effectiveMovingSec)
        val calories   = statsCalc.caloriesKcal()
        val decoupling = statsCalc.decouplingPercent()
        val carbs      = statsCalc.carbsGPerH(if30, elapsedSec, vi, temperatureCelsius ?: 20f)
        val fluid      = statsCalc.fluidLPerH(if30, temperatureCelsius ?: 20f)
        val timeToFinish = if (hasRoute && smartAvgNet > 1f) ((remainingKm / smartAvgNet) * 3600f).toLong() else 0L
        val reserve    = statsCalc.rideReservePercent(tss, if30, remainingKm, smartAvgNet, hasRoute, decoupling)
        val wBalance   = statsCalc.wBalancePercent()

        _state.value = RideState(
            speedKph         = speedKph, powerWatts = powerWatts, heartRate = heartRate,
            cadenceRpm       = cadenceRpm, gradePercent = gradePercent,
            distanceKm       = distanceKm, remainingKm = remainingKm,
            elapsedSec       = elapsedSec, movingSec = effectiveMovingSec, hasRoute = hasRoute,
            np10Watts        = np10, if30Value = if30, ifValue = ifVal, cadenceAvg30sRpm = cad30s,
            smartAvgKph      = smartAvgGross, smartAvgNetKph = smartAvgNet, speedTrend = trend,
            etaTimestamp           = etaMs, requiredSpeedKph = requiredSpeed,
            deadlineTimestamp      = deadlineMs, civilTwilightTimestamp = twilightMs,
            stopRateMinPerKm       = predictedStops / 60f,
            isOverDeadline         = isOverDeadline, isAfterTwilight = isAfterTwilight,
            minutesOverDeadline    = if (isOverDeadline) ((etaMs - deadlineMs) / 60_000L).toInt() else 0,
            windSpeedMs      = windSpeedMs, windDirectionDeg = headwindBearingDeg.toInt(),
            windArrow        = windArrow(headwindBearingDeg), windImpactKph = -headwindKph,
            lastLat = lat, lastLon = lon, temperatureCelsius = temperatureCelsius,
            frontTeeth = frontTeeth, rearTeeth = rearTeeth,
            npWholeWatts = npWhole, ifWholeRide = ifWhole, viValue = vi, tssValue = tss,
            caloriesKcal = calories, avgPowerWatts = npWhole,
            ascentDoneM = ascentDoneM, ascentLeftM = ascentLeftM,
            timeToFinishSec = timeToFinish, decouplingPercent = decoupling,
            carbsGPerH = carbs, fluidLPerH = fluid,
            todayFactor = readiness.todayFactor, rideReservePercent = reserve,
            wBalancePercent = wBalance,
        )
    }

    fun stop() { scope.cancel(); statsCalc.reset() }
}