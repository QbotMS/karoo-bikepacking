package com.bikepacking.karoo.field

data class RawClimbData(
    val startDistanceM: Double,
    val lengthM: Double,
    val elevationM: Double,
)

object ClimbContextProvider {

    fun computeContext(
        climbs: List<RawClimbData>,
        progressM: Double,
        currentSpeedMps: Double? = null,
    ): ClimbContext {
        if (climbs.isEmpty()) return ClimbContext()

        val sorted = climbs.sortedBy { it.startDistanceM }
        val total = sorted.size

        var activeIndex: Int? = null
        var nextIndex: Int? = null
        var lastCompletedIndex: Int? = null

        for (i in sorted.indices) {
            val c = sorted[i]
            val start = c.startDistanceM
            val end = start + c.lengthM

            when {
                progressM >= start && progressM < end -> activeIndex = i
                progressM >= end -> lastCompletedIndex = i
                nextIndex == null && progressM < start -> nextIndex = i
            }
        }

        fun buildActiveInfo(raw: RawClimbData, index: Int): ClimbInfo {
            val endM = raw.startDistanceM + raw.lengthM
            val distanceToStart = raw.startDistanceM - progressM
            val distanceToEnd = endM - progressM

            val progressOnClimb = (progressM - raw.startDistanceM).coerceIn(0.0, raw.lengthM)
            val fraction = if (raw.lengthM > 0) progressOnClimb / raw.lengthM else 0.0
            val remainingElevation = (raw.elevationM * (1.0 - fraction))
                .coerceIn(0.0, raw.elevationM)

            return ClimbInfo(
                index = index,
                total = total,
                startDistanceM = raw.startDistanceM,
                lengthM = raw.lengthM,
                totalElevationM = raw.elevationM,
                distanceToStartM = distanceToStart,
                distanceToEndM = distanceToEnd,
                remainingElevationM = remainingElevation,
                progressOnClimbPercent = (fraction * 100).coerceIn(0.0, 100.0),
            )
        }

        fun buildNextInfo(raw: RawClimbData, index: Int): ClimbInfo {
            val endM = raw.startDistanceM + raw.lengthM
            return ClimbInfo(
                index = index,
                total = total,
                startDistanceM = raw.startDistanceM,
                lengthM = raw.lengthM,
                totalElevationM = raw.elevationM,
                distanceToStartM = raw.startDistanceM - progressM,
                distanceToEndM = endM - progressM,
                remainingElevationM = raw.elevationM,
                progressOnClimbPercent = 0.0,
            )
        }

        fun buildCompletedInfo(raw: RawClimbData, index: Int): ClimbInfo {
            val endM = raw.startDistanceM + raw.lengthM
            return ClimbInfo(
                index = index,
                total = total,
                startDistanceM = raw.startDistanceM,
                lengthM = raw.lengthM,
                totalElevationM = raw.elevationM,
                distanceToStartM = raw.startDistanceM - progressM,
                distanceToEndM = endM - progressM,
                remainingElevationM = 0.0,
                progressOnClimbPercent = 100.0,
            )
        }

        val activeInfo = activeIndex?.let { buildActiveInfo(sorted[it], it) }
        val nextInfo = nextIndex?.let { buildNextInfo(sorted[it], it) }
        val lastInfo = lastCompletedIndex?.let { buildCompletedInfo(sorted[it], it) }

        val timeToClimbStartSec = if (currentSpeedMps != null && currentSpeedMps > 0 && nextInfo != null) {
            val dist = (nextInfo.startDistanceM - progressM).coerceAtLeast(0.0)
            if (dist > 0) dist / currentSpeedMps else null
        } else null

        val difficultyBucket = (activeInfo ?: nextInfo)?.difficultyBucket ?: ClimbDifficulty.UNKNOWN

        return ClimbContext(
            climbCount = total,
            activeClimb = activeInfo,
            nextClimb = nextInfo,
            lastCompletedClimb = lastInfo,
            timeToClimbStartSec = timeToClimbStartSec,
            difficultyBucket = difficultyBucket,
        )
    }

    fun isClimbAhead(context: ClimbContext, thresholdM: Double): Boolean {
        val dist = context.distanceToNextClimbStartM ?: return false
        return dist in 0.0..thresholdM
    }
}
