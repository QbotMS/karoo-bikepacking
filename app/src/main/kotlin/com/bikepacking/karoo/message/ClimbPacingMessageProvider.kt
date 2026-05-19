package com.bikepacking.karoo.message

import com.bikepacking.karoo.field.ClimbContext
import com.bikepacking.karoo.field.ClimbDifficulty
import com.bikepacking.karoo.field.ClimbInfo

class ClimbPacingMessageProvider(
    private val state: ClimbPacingMessageState = ClimbPacingMessageState()
) {

    fun generate(
        climbContext: ClimbContext,
        nowMs: Long,
        routeAscentRemainingM: Double? = null,
        ftpW: Int = 0,
        todayFactor: Float = 1.0f,
        wBalPercent: Int? = null,
        hrDriftPercent: Float? = null,
        routeDistanceRemainingKm: Float? = null,
        currentPowerW: Int = 0,
    ): List<RideMessage> {
        val messages = mutableListOf<RideMessage>()

        if (climbContext.climbCount == 0) return messages

        maybeClimbAhead(climbContext, nowMs)?.let { messages.add(it) }
        maybeClimbSummary(climbContext, nowMs, routeAscentRemainingM)?.let { messages.add(it) }
        maybeClimbTarget(climbContext, nowMs, ftpW, todayFactor, wBalPercent, hrDriftPercent, routeAscentRemainingM, routeDistanceRemainingKm)?.let { messages.add(it) }
        maybeEaseOff(climbContext, nowMs, ftpW, todayFactor, wBalPercent, hrDriftPercent, routeAscentRemainingM, routeDistanceRemainingKm, currentPowerW)?.let { messages.add(it) }

        return messages
    }

    private fun maybeClimbAhead(ctx: ClimbContext, nowMs: Long): RideMessage? {
        if (ctx.isOnClimb) return null
        val next = ctx.nextClimb ?: return null
        if (state.hasShownAhead(next.index)) return null
        if (next.distanceToStartM <= 0) return null

        val threshold = advanceDistanceM(next)
        if (next.distanceToStartM > threshold) return null

        state.markAheadShown(next.index)
        return RideMessage(
            type = "CLIMB_AHEAD",
            module = RideMessageModule.CLIMB_PACING,
            severity = MessageSeverity.WARNING,
            priority = 50,
            line1 = "PODJAZD ${next.label}",
            line2 = formatAheadLine2(next),
            minDisplayMs = 8000L,
            cooldownMs = 60000L,
            createdAtMs = nowMs,
        )
    }

    private fun maybeClimbSummary(
        ctx: ClimbContext,
        nowMs: Long,
        routeAscentRemainingM: Double?,
    ): RideMessage? {
        val completed = ctx.lastCompletedClimb ?: return null
        if (state.hasShownSummary(completed.index)) return null

        val assessment = assessClimbPacing(completed)

        state.markSummaryShown(completed.index)
        return RideMessage(
            type = "CLIMB_SUMMARY",
            module = RideMessageModule.CLIMB_PACING,
            severity = summarySeverity(assessment),
            priority = summaryPriority(assessment),
            line1 = "${completed.label} ${assessmentText(assessment)}",
            line2 = formatSummaryLine2(completed, routeAscentRemainingM),
            minDisplayMs = 8000L,
            cooldownMs = 60000L,
            createdAtMs = nowMs,
        )
    }

    private fun assessClimbPacing(completed: ClimbInfo): ClimbPacingAssessment {
        if (state.wasEaseOffShownOnClimb(completed.index)) {
            return ClimbPacingAssessment.TOO_HARD
        }
        return ClimbPacingAssessment.UNKNOWN
    }

    private fun assessmentText(assessment: ClimbPacingAssessment): String = when (assessment) {
        ClimbPacingAssessment.TOO_HARD -> "ZA MOCNO"
        ClimbPacingAssessment.OK -> "OK"
        ClimbPacingAssessment.CONSERVATIVE -> "OSZCZĘDNIE"
        ClimbPacingAssessment.UNKNOWN -> "OK"
    }

    private fun summarySeverity(assessment: ClimbPacingAssessment): MessageSeverity = when (assessment) {
        ClimbPacingAssessment.TOO_HARD -> MessageSeverity.WARNING
        else -> MessageSeverity.INFO
    }

    private fun summaryPriority(assessment: ClimbPacingAssessment): Int = when (assessment) {
        ClimbPacingAssessment.TOO_HARD -> 45
        ClimbPacingAssessment.OK -> 40
        ClimbPacingAssessment.CONSERVATIVE -> 35
        ClimbPacingAssessment.UNKNOWN -> 40
    }

    private fun maybeClimbTarget(
        ctx: ClimbContext,
        nowMs: Long,
        ftpW: Int,
        todayFactor: Float,
        wBalPercent: Int?,
        hrDriftPercent: Float?,
        routeAscentRemainingM: Double?,
        routeDistanceRemainingKm: Float?,
    ): RideMessage? {
        val climb = ctx.activeClimb ?: return null
        if (state.hasShownTarget(climb.index)) return null

        val target = ClimbPowerTargetCalculator.calculate(
            activeClimb = climb,
            ftp = ftpW,
            todayFactor = todayFactor,
            wBalancePercent = wBalPercent,
            hrDrift = hrDriftPercent,
            routeAscentRemainingM = routeAscentRemainingM,
            routeDistanceRemainingKm = routeDistanceRemainingKm,
        ) ?: return null

        state.markTargetShown(climb.index)
        state.storeTargetForClimb(climb.index, target.targetLowW, target.targetHighW)
        return RideMessage(
            type = "CLIMB_HOLD_TARGET",
            module = RideMessageModule.CLIMB_PACING,
            severity = MessageSeverity.INFO,
            priority = 45,
            line1 = "${climb.label} · ${climb.averageGradePercent.toInt()}%",
            line2 = "TRZYMAJ ${target.targetLowW}–${target.targetHighW}W",
            minDisplayMs = 8000L,
            cooldownMs = 120000L,
            createdAtMs = nowMs,
        )
    }

    private fun maybeEaseOff(
        ctx: ClimbContext,
        nowMs: Long,
        ftpW: Int,
        todayFactor: Float,
        wBalPercent: Int?,
        hrDriftPercent: Float?,
        routeAscentRemainingM: Double?,
        routeDistanceRemainingKm: Float?,
        currentPowerW: Int,
    ): RideMessage? {
        val climb = ctx.activeClimb ?: return null
        if (currentPowerW <= 0) return null

        val target = ClimbPowerTargetCalculator.calculate(
            activeClimb = climb,
            ftp = ftpW,
            todayFactor = todayFactor,
            wBalancePercent = wBalPercent,
            hrDrift = hrDriftPercent,
            routeAscentRemainingM = routeAscentRemainingM,
            routeDistanceRemainingKm = routeDistanceRemainingKm,
        ) ?: return null

        if (climb.distanceToEndM < 150.0) return null

        val routeDistLeft = routeDistanceRemainingKm ?: 0f
        if (routeDistLeft > 0f && routeDistLeft < 2f) return null

        if (!state.canShowEaseOff(climb.index, nowMs)) return null

        val thresholdMultiplier = if (wBalPercent != null && wBalPercent < 50) 1.0f else 1.08f
        val threshold = (target.targetHighW * thresholdMultiplier).roundToInt()
        if (currentPowerW <= threshold) return null

        state.markEaseOffShown(climb.index, nowMs)
        return RideMessage(
            type = "CLIMB_EASE_OFF",
            module = RideMessageModule.CLIMB_PACING,
            severity = MessageSeverity.ALARM,
            priority = 70,
            line1 = "ODPUŚĆ!",
            line2 = "CEL ${target.targetLowW}–${target.targetHighW}W",
            minDisplayMs = 8000L,
            cooldownMs = 60000L,
            createdAtMs = nowMs,
        )
    }

    fun advanceDistanceM(climb: ClimbInfo): Double {
        return when (climb.difficultyBucket) {
            ClimbDifficulty.FLAT, ClimbDifficulty.ROLLING -> 250.0
            ClimbDifficulty.MODERATE, ClimbDifficulty.UNKNOWN -> 500.0
            ClimbDifficulty.STEEP, ClimbDifficulty.VERY_STEEP -> 800.0
        }
    }

    private fun formatAheadLine2(climb: ClimbInfo): String {
        val lengthInt = climb.lengthM.toInt()
        val gradeInt = climb.averageGradePercent.toInt()
        return "${lengthInt}m · ${gradeInt}%"
    }

    private fun formatSummaryLine2(climb: ClimbInfo, routeAscentRemainingM: Double?): String {
        val elevInt = climb.totalElevationM.toInt()
        val base = "+${elevInt}m"
        if (routeAscentRemainingM != null) {
            val remInt = routeAscentRemainingM.toInt()
            return "$base · zostało ${remInt}m"
        }
        return base
    }

    private fun Float.roundToInt(): Int = (this + 0.5f).toInt()

    fun resetState() {
        state.reset()
    }
}
