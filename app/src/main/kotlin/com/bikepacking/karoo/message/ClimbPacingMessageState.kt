package com.bikepacking.karoo.message

data class ClimbTargetRange(
    val lowW: Int,
    val highW: Int,
)

class ClimbPacingMessageState {
    private val shownAheadIndices = mutableSetOf<Int>()
    private val shownSummaryIndices = mutableSetOf<Int>()
    private val shownTargetIndices = mutableSetOf<Int>()
    private val lastEaseOffAtMsByIndex = mutableMapOf<Int, Long>()
    private val easeOffCooldownMs = 60000L
    private val storedTargetPerClimbIndex = mutableMapOf<Int, ClimbTargetRange>()

    fun hasShownAhead(index: Int): Boolean = index in shownAheadIndices
    fun markAheadShown(index: Int) { shownAheadIndices.add(index) }

    fun hasShownSummary(index: Int): Boolean = index in shownSummaryIndices
    fun markSummaryShown(index: Int) { shownSummaryIndices.add(index) }

    fun hasShownTarget(index: Int): Boolean = index in shownTargetIndices
    fun markTargetShown(index: Int) { shownTargetIndices.add(index) }

    fun canShowEaseOff(index: Int, nowMs: Long): Boolean {
        val last = lastEaseOffAtMsByIndex[index] ?: return true
        return nowMs - last >= easeOffCooldownMs
    }

    fun markEaseOffShown(index: Int, nowMs: Long) {
        lastEaseOffAtMsByIndex[index] = nowMs
    }

    fun storeTargetForClimb(index: Int, lowW: Int, highW: Int) {
        storedTargetPerClimbIndex[index] = ClimbTargetRange(lowW, highW)
    }

    fun getTargetForClimb(index: Int): ClimbTargetRange? = storedTargetPerClimbIndex[index]

    fun wasEaseOffShownOnClimb(index: Int): Boolean = lastEaseOffAtMsByIndex.containsKey(index)

    fun reset() {
        shownAheadIndices.clear()
        shownSummaryIndices.clear()
        shownTargetIndices.clear()
        lastEaseOffAtMsByIndex.clear()
        storedTargetPerClimbIndex.clear()
    }
}
