package com.bikepacking.karoo

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.OnNavigationState
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
    private val statsCalc = StatsCalculator(settings.ftp)
    private val fileLogger = QBotRideFileLogger(context)
    private val freshness = FreshnessTracker().apply {
        // Configure all sensor keys - canonical names only
        configure("heartRate", FreshnessConfigurations.HR)
        configure("power", FreshnessConfigurations.POWER)
        configure("cadence", FreshnessConfigurations.CADENCE)
        configure("speed", FreshnessConfigurations.SPEED)
        configure("grade", FreshnessConfigurations.GRADE)
        configure("gear", FreshnessConfigurations.GEAR)
        configure("temp", FreshnessConfigurations.TEMP)
        configure("wind", FreshnessConfigurations.WIND)
    }

    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    @Volatile private var speedKph = 0f
    @Volatile private var powerWatts = 0
    @Volatile private var heartRate = 0
    @Volatile private var cadenceRpm = 0
    @Volatile private var gradePercent = 0f
    @Volatile private var distanceM = 0f
    @Volatile private var remainingM = 0f
    @Volatile private var onRoute = false
    @Volatile private var elapsedSec = 0L
    @Volatile private var movingSec = 0L
    @Volatile private var movingSecManual = 0L
    @Volatile private var temperatureCelsius: Float? = null
    @Volatile private var frontTeeth = 0
    @Volatile private var rearTeeth = 0
    @Volatile private var gradeSampleThisTick = false

    private val power10mHistory = ArrayDeque<Pair<Long, Int>>()
    private val power30mHistory = ArrayDeque<Pair<Long, Int>>()
    private val cadence30sHistory = ArrayDeque<Pair<Long, Int>>()

    @Volatile private var lat = 52.23
    @Volatile private var lon = 21.01
    @Volatile private var headwindKph = 0f
    @Volatile private var windSpeedMs = 0f
    @Volatile private var headwindBearingDeg = -1f

    @Volatile private var ascentDoneM = 0
    @Volatile private var ascentLeftFromStreamM: Int? = null
    @Volatile private var ascentLeftFromRouteM = 0
    @Volatile private var routeDistanceM = 0.0
    @Volatile private var routeClimbs: List<OnNavigationState.NavigationState.Climb> = emptyList()
    @Volatile private var routeAscentTotalM = 0

    private enum class TimeUnitMode { UNKNOWN, SECONDS, MILLIS }
    @Volatile private var elapsedTimeMode = TimeUnitMode.UNKNOWN
    @Volatile private var movingTimeMode = TimeUnitMode.UNKNOWN

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

        addStream(DataType.Type.SPEED)        { v, nowMs ->
            speedKph = (v * 3.6).toFloat()
            val before = freshness.getFreshness("speed", nowMs)
            freshness.touch("speed", nowMs)
            val after = freshness.getFreshness("speed", nowMs)
            Log.d(TAG, "QEXT_SENSOR_SAMPLE key=speed value=$speedKph before=$before after=$after")
        }
        addStream(DataType.Type.HEART_RATE)   { v, nowMs ->
            heartRate = v.toInt()
            val before = freshness.getFreshness("heartRate", nowMs)
            freshness.touch("heartRate", nowMs)
            val after = freshness.getFreshness("heartRate", nowMs)
            Log.d(TAG, "QEXT_SENSOR_SAMPLE key=heartRate value=$heartRate before=$before after=$after")
        }
        addStream(DataType.Type.POWER)        { v, nowMs ->
            powerWatts = v.toInt()
            power10mHistory.addLast(nowMs to powerWatts)
            while (power10mHistory.isNotEmpty() && power10mHistory.first().first < nowMs - 600_000L)
                power10mHistory.removeFirst()
            power30mHistory.addLast(nowMs to powerWatts)
            while (power30mHistory.isNotEmpty() && power30mHistory.first().first < nowMs - 1_800_000L)
                power30mHistory.removeFirst()
            val before = freshness.getFreshness("power", nowMs)
            freshness.touch("power", nowMs)
            val after = freshness.getFreshness("power", nowMs)
            Log.d(TAG, "QEXT_SENSOR_SAMPLE key=power value=$powerWatts before=$before after=$after")
        }
        addStream(DataType.Type.CADENCE)      { v, nowMs ->
            cadenceRpm = v.toInt()
            cadence30sHistory.addLast(nowMs to cadenceRpm)
            while (cadence30sHistory.isNotEmpty() && cadence30sHistory.first().first < nowMs - 30_000L)
                cadence30sHistory.removeFirst()
            val before = freshness.getFreshness("cadence", nowMs)
            freshness.touch("cadence", nowMs)
            val after = freshness.getFreshness("cadence", nowMs)
            Log.d(TAG, "QEXT_SENSOR_SAMPLE key=cadence value=$cadenceRpm before=$before after=$after")
        }

        // Grade / nachylenie - próba różnych typów
        addStream(DataType.Type.ELEVATION_GRADE)  { v, nowMs -> gradePercent = v.toFloat(); gradeSampleThisTick = true; freshness.touch("grade", nowMs) }
        addStream("TYPE_ELEVATION_GRADE")         { v, nowMs -> gradePercent = v.toFloat(); gradeSampleThisTick = true; freshness.touch("grade", nowMs) }
        addStream("TYPE_GRADE")                   { v, nowMs -> gradePercent = v.toFloat(); gradeSampleThisTick = true; freshness.touch("grade", nowMs) }
        addStream(DataType.Type.DISTANCE)     { v, _ -> distanceM = v.toFloat() }
        addStream(DataType.Type.ELAPSED_TIME) { v, _ -> elapsedSec = normalizeElapsedTimeToSec(v) }
        addStream("ELAPSED_MOVING_TIME")      { v, _ -> movingSec = normalizeMovingTimeToSec(v) }
        addStream(DataType.Type.TEMPERATURE)  { v, nowMs ->
            if (v > -50f && v < 60f) {  // Valid range check
                temperatureCelsius = v.toFloat()
                freshness.touch("temp", nowMs)
                Log.d(TAG, "QEXT_SENSOR_SAMPLE key=temp value=$temperatureCelsius")
            }
        }
        // Gear - try multiple Karoo SDK types for shifting/drivetrain
        // SHIFTING_GEARS - only touch freshness when both front and rear are valid
        addStream(DataType.Type.SHIFTING_GEARS) { v, nowMs ->
            val gearStr = v.toString()
            Log.d(TAG, "QEXT_GEAR_SUBSCRIBE_OK type=SHIFTING_GEARS")
            Log.d(TAG, "QEXT_GEAR_SAMPLE raw=$gearStr")
            try {
                if (gearStr.contains("x")) {
                    val parts = gearStr.split("x")
                    val newFront = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val newRear = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    if (newFront > 0 && newRear > 0) {
                        frontTeeth = newFront
                        rearTeeth = newRear
                        val display = "${frontTeeth}x${rearTeeth}"
                        Log.d(TAG, "QEXT_GEAR_PARSED display=$display")
                        val before = freshness.getFreshness("gear", nowMs)
                        freshness.touch("gear", nowMs)
                        val after = freshness.getFreshness("gear", nowMs)
                        Log.d(TAG, "QEXT_SENSOR_TOUCH key=gear before=$before after=$after")
                    } else {
                        Log.d(TAG, "QEXT_GEAR_NO_SAMPLE invalid gear values")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gear parse error: $gearStr", e)
                Log.d(TAG, "QEXT_GEAR_NO_SAMPLE")
            }
        }
        // SHIFTING_FRONT_GEAR - only touch when rear also valid
        addStream("SHIFTING_FRONT_GEAR") { v, nowMs ->
            frontTeeth = v.toInt()
            Log.d(TAG, "QEXT_GEAR_SUBSCRIBE_OK type=SHIFTING_FRONT_GEAR")
            Log.d(TAG, "QEXT_GEAR_SAMPLE frontTeeth=$frontTeeth")
            if (frontTeeth > 0 && rearTeeth > 0) {
                val before = freshness.getFreshness("gear", nowMs)
                freshness.touch("gear", nowMs)
                val after = freshness.getFreshness("gear", nowMs)
                Log.d(TAG, "QEXT_SENSOR_TOUCH key=gear before=$before after=$after")
            }
        }
        // SHIFTING_REAR_GEAR - only touch when front also valid
        addStream("SHIFTING_REAR_GEAR") { v, nowMs ->
            rearTeeth = v.toInt()
            Log.d(TAG, "QEXT_GEAR_SUBSCRIBE_OK type=SHIFTING_REAR_GEAR")
            Log.d(TAG, "QEXT_GEAR_SAMPLE rearTeeth=$rearTeeth")
            if (frontTeeth > 0 && rearTeeth > 0) {
                val before = freshness.getFreshness("gear", nowMs)
                freshness.touch("gear", nowMs)
                val after = freshness.getFreshness("gear", nowMs)
                Log.d(TAG, "QEXT_SENSOR_TOUCH key=gear before=$before after=$after")
            }
        }

        // Dystans do celu — dane w values map, nie w singleValue
        addStreamFull(DataType.Type.DISTANCE_TO_DESTINATION) { dp ->
            Log.d(TAG, "DISTANCE_TO_DESTINATION values=${dp.values}")
            dp.values["FIELD_DISTANCE_TO_DESTINATION_ID"]?.let { remainingM = it.toFloat() }
            dp.values["FIELD_ON_ROUTE_ID"]?.let { onRoute = it == 1.0 }
        }

        // Przewyższenie pozostałe — gotowy stream Karoo, jeśli aktywna trasa go udostępnia
        addStreamFull(DataType.Type.ELEVATION_REMAINING) { dp ->
            Log.d(TAG, "ELEVATION_REMAINING values=${dp.values}")
            dp.values["FIELD_ASCENT_REMAINING_ID"]?.let { ascentLeftFromStreamM = it.toInt() }
            dp.values["FIELD_ELEVATION_REMAINING_ID"]?.let { ascentLeftFromStreamM = it.toInt() }
        }

        // Przewyższenie pozostałe — fallback z oficjalnego NavigationState/climbs
        karooSystem.addConsumer<OnNavigationState> { event ->
            when (val navState = event.state) {
                is OnNavigationState.NavigationState.NavigatingRoute -> {
                    onRoute = true
                    routeDistanceM = navState.routeDistance
                    routeClimbs = navState.climbs
                    routeAscentTotalM = navState.climbs.sumOf { it.totalElevation }.roundToInt().coerceAtLeast(0)
                    if (remainingM <= 0f && routeDistanceM > 0.0) remainingM = routeDistanceM.toFloat()
                    ascentLeftFromRouteM = calculateAscentLeftFromClimbs()
                    Log.d(TAG, "NAV NavigatingRoute distance=$routeDistanceM climbs=${routeClimbs.size} ascentTotal=$routeAscentTotalM ascentLeftRoute=$ascentLeftFromRouteM")
                }
                is OnNavigationState.NavigationState.NavigatingToDestination -> {
                    onRoute = true
                    routeClimbs = navState.climbs
                    routeAscentTotalM = navState.climbs.sumOf { it.totalElevation }.roundToInt().coerceAtLeast(0)
                    ascentLeftFromRouteM = calculateAscentLeftFromClimbs()
                    Log.d(TAG, "NAV NavigatingToDestination climbs=${routeClimbs.size} ascentTotal=$routeAscentTotalM ascentLeftRoute=$ascentLeftFromRouteM")
                }
                is OnNavigationState.NavigationState.Idle -> {
                    onRoute = false
                    routeDistanceM = 0.0
                    routeClimbs = emptyList()
                    ascentLeftFromStreamM = null
                    ascentLeftFromRouteM = 0
                    routeAscentTotalM = 0
                    Log.d(TAG, "NAV Idle")
                }
            }
        }

        addStreamFull(DataType.Type.LOCATION) { dp ->
            dp.values["lat"]?.let { lat = it }
            dp.values["lon"]?.let { lon = it }
            dp.values["longitude"]?.let { lon = it }
            dp.values["latitude"]?.let { lat = it }
        }

        addStream("TYPE_EXT::karoo-headwind::headwindSpeed") { v, nowMs ->
            val valid = v > -50f && v < 200f
            if (valid) {
                headwindKph = (v * 3.6).toFloat()
                freshness.touch("wind", nowMs)
                Log.d(TAG, "QEXT_SENSOR_SAMPLE key=wind headwind=$headwindKph")
            }
        }
        addStream("TYPE_EXT::karoo-headwind::windSpeed")     { v, nowMs ->
            val valid = v > -50f && v < 200f
            if (valid) {
                windSpeedMs = v.toFloat()
                freshness.touch("wind", nowMs)
            }
        }
        addStream("TYPE_EXT::karoo-headwind::headwind")      { v, nowMs ->
            val valid = v >= 0f && v < 360f
            if (valid) {
                headwindBearingDeg = v.toFloat()
                freshness.touch("wind", nowMs)
            }
        }

        // Przewyższenie zrealizowane — gotowy licznik Karoo, nie liczenie z surowej wysokości
        addStream(DataType.Type.ELEVATION_GAIN) { v, _ ->
            ascentDoneM = v.toInt()
            Log.d(TAG, "ELEVATION_GAIN value=$v ascentDoneM=$ascentDoneM")
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

    private fun normalizeElapsedTimeToSec(v: Double): Long {
        val raw = v.toLong()
        elapsedTimeMode = detectTimeUnit(raw, elapsedTimeMode)
        return if (elapsedTimeMode == TimeUnitMode.MILLIS) raw / 1000L else raw
    }

    private fun normalizeMovingTimeToSec(v: Double): Long {
        val raw = v.toLong()
        movingTimeMode = detectTimeUnit(raw, movingTimeMode)
        return if (movingTimeMode == TimeUnitMode.MILLIS) raw / 1000L else raw
    }

    private fun detectTimeUnit(raw: Long, currentMode: TimeUnitMode): TimeUnitMode {
        if (currentMode != TimeUnitMode.UNKNOWN) return currentMode
        if (raw <= 0L) return TimeUnitMode.UNKNOWN
        return if (raw >= 1000L) TimeUnitMode.MILLIS else TimeUnitMode.SECONDS
    }

    private fun calculateAscentLeftFromClimbs(): Int {
        val climbs = routeClimbs
        if (climbs.isEmpty()) return 0

        // Guard: only use remainingM if it's valid (non-negative and <= route distance)
        val remainingValid = remainingM >= 0f && remainingM.toDouble() <= routeDistanceM
        val progressM = if (routeDistanceM > 0.0 && remainingValid) {
            (routeDistanceM - remainingM.toDouble()).coerceAtLeast(0.0)
        } else {
            distanceM.toDouble().coerceAtLeast(0.0)
        }

        return climbs.sumOf { climb ->
            val startM = climb.startDistance
            val lengthM = climb.length.coerceAtLeast(1.0)
            val endM = startM + lengthM
            val elevationM = climb.totalElevation

            when {
                progressM <= startM -> elevationM
                progressM >= endM -> 0.0
                else -> {
                    val remainingFraction = ((endM - progressM) / lengthM).coerceIn(0.0, 1.0)
                    elevationM * remainingFraction
                }
            }
        }.roundToInt().coerceAtLeast(0)
    }

    private fun windArrow(d: Float): String {
        if (d < 0f) return "-"
        // karoo-headwind provides direction wind is blowing TOWARD
        // We need to invert: 0° (wind to north) = tailwind when riding north = ↑
        val a = ((d + 180f) % 360f)
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
        val hasRoute = onRoute || remainingM > 0f || routeDistanceM > 0.0 || routeClimbs.isNotEmpty() || ascentLeftFromStreamM != null
        ascentLeftFromRouteM = calculateAscentLeftFromClimbs()
        val ascentLeftM = ascentLeftFromStreamM ?: ascentLeftFromRouteM.takeIf { it > 0 } ?: routeAscentTotalM
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
        val if10   = if (ftp > 0 && np10 > 0) np10.toFloat() / ftp else 0f
        val cad30s = if (cadence30sHistory.isNotEmpty())
            cadence30sHistory.map { it.second }.average().toInt() else cadenceRpm

        val twilightMs = SunCalculator.civilDuskMs(lat, lon, nowMs)
        val deadlineMs = minOf(settings.deadlineTodayMs(), twilightMs)

        // Prędkość predykowana: minimum 60s ruchu, fallback na bieżącą jeśli avg zbyt niska
        val predictedSpeed = when {
            effectiveMovingSec < 60L -> 0f          // za mało danych — nie pokazuj ETA
            smartAvg >= 3f           -> smartAvg    // normalny przypadek
            speedKph >= 3f           -> speedKph    // avg jeszcze niska, użyj bieżącej
            else                     -> 0f
        }

        val etaMs          = if (hasRoute && predictedSpeed > 0f)
            etaCalculator.calculateEtaMs(nowMs, remainingKm, distanceKm, predictedSpeed) else 0L
        val predictedStops = etaCalculator.predictedStopsSec(remainingKm, distanceKm)
        val requiredSpeed  = if (hasRoute && deadlineMs > nowMs)
            etaCalculator.requiredSpeedKph(nowMs, remainingKm, deadlineMs, predictedStops) else 0f
        val isOverDeadline  = etaMs > 0L && deadlineMs > 0L && etaMs > deadlineMs
        val isAfterTwilight = etaMs > 0L && twilightMs > 0L && etaMs > twilightMs

        // Deadline delta: how much faster/slower needed to meet deadline
        val deadlineDeltaKph = if (hasRoute && deadlineMs > nowMs && smartAvgNet > 1f && requiredSpeed > 0f) {
            requiredSpeed - smartAvgNet
        } else 0f
        val deadlineStatus = when {
            !hasRoute || deadlineMs <= 0L || etaMs <= 0L -> "--"
            deadlineMs <= nowMs -> "LATE"
            deadlineDeltaKph <= 0f -> "OK"
            deadlineDeltaKph > 2.5f -> "IMPOSSIBLE"
            else -> "LATE"
        }

        statsCalc.update(powerWatts, heartRate, effectiveMovingSec, elapsedSec)

        val npWhole    = statsCalc.npWatts()
        val ifWhole    = statsCalc.ifValue()
        val vi         = statsCalc.viValue()
        val tss        = statsCalc.tssValue(effectiveMovingSec)
        val calories   = statsCalc.caloriesKcal()
        val decoupling = statsCalc.decouplingPercent()
        val carbs      = statsCalc.carbsGPerH(if10, effectiveMovingSec, vi, temperatureCelsius ?: 20f, readiness.bodyWeightKg)
        val fluid      = statsCalc.fluidLPerH(if10, temperatureCelsius ?: 20f)
        val timeToFinish = if (hasRoute && smartAvgNet > 1f) ((remainingKm / smartAvgNet) * 3600f).toLong() else 0L
        val reserve    = statsCalc.rideReservePercent(tss, if10, decoupling)
        val wBalance   = statsCalc.wBalancePercent()

        // HRD: Local HR Drift/Strain
        val hrdResult = statsCalc.updateHRD(nowMs, elapsedSec, effectiveMovingSec, powerWatts, heartRate, cadenceRpm)
        val hrdStatus = hrdResult.status
        val hrdPct = hrdResult.pct
        val hrdPhase = hrdResult.phase
        val hrdValid = hrdResult.valid
        val hrdReason = hrdResult.reason

        _state.value = RideState(
            speedKph         = speedKph, powerWatts = powerWatts, heartRate = heartRate,
            cadenceRpm       = cadenceRpm, gradePercent = gradePercent,
            distanceKm       = distanceKm, remainingKm = remainingKm,
            elapsedSec       = elapsedSec, movingSec = effectiveMovingSec, hasRoute = hasRoute,
            np10Watts        = np10, if10Value = if10, ifValue = if10, cadenceAvg30sRpm = cad30s,
            smartAvgKph      = smartAvg, smartAvgNetKph = smartAvgNet, speedTrend = trend,
            etaTimestamp           = etaMs, requiredSpeedKph = requiredSpeed,
            deadlineTimestamp      = deadlineMs, deadlineDeltaKph = deadlineDeltaKph, deadlineStatus = deadlineStatus,
            civilTwilightTimestamp = twilightMs,
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
            hrdStatus = hrdStatus, hrdPct = hrdPct, hrdPhase = hrdPhase, hrdValid = hrdValid, hrdReason = hrdReason,
            wBalancePercent = wBalance,
        )

        // Log tick to file
        val gradeFresh = freshness.getFreshness("grade", nowMs)
        val gradeState = if (gradeFresh == DataFreshness.MISSING) "missing" else "ok"
        fileLogger.tick(
            state = _state.value,
            speedState = null, powerState = null, powerReason = null,
            hrState = null, cadenceState = null,
            gradeState = gradeState,
            gearState = null, gearReason = null,
            climbActive = false, climbIndex = 0, climbCount = 0,
            distanceToTopM = null, ascentLeftM = null, avgGradePct = null,
            freshness = freshness,
            hrdStatus = hrdStatus, hrdPct = hrdPct, hrdPhase = hrdPhase, hrdValid = hrdValid, hrdReason = hrdReason,
        )
    }

    // ── Dynamic Message Support (stub - no real messages yet) ──
    data class DynMessage(val active: Boolean = false, val severity: DynMessageSeverity = DynMessageSeverity.INFO, val line1: String = "", val line2: String = "")
    enum class DynMessageSeverity { INFO, ALERT }

    // Returns null when no real message - UI must handle null (show empty/neutral)
    fun getDynMessage(): DynMessage? = null

    // ── Freshness Support ──
    fun getFreshness(): FreshnessTracker = freshness

    fun stop() { fileLogger.stop(); freshness.reset(); scope.cancel(); statsCalc.reset() }
}

private const val TAG = "BikepackingRideEngine"
