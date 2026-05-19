package com.bikepacking.karoo.field

data class ClimbContext(
    val climbCount: Int = 0,
    val activeClimb: ClimbInfo? = null,
    val nextClimb: ClimbInfo? = null,
    val lastCompletedClimb: ClimbInfo? = null,
    val timeToClimbStartSec: Double? = null,
    val difficultyBucket: ClimbDifficulty = ClimbDifficulty.UNKNOWN,
) {
    val isOnClimb: Boolean
        get() = activeClimb != null

    val hasClimbAhead: Boolean
        get() = nextClimb != null && nextClimb!!.distanceToStartM > 0

    val distanceToNextClimbStartM: Double?
        get() = nextClimb?.distanceToStartM?.takeIf { it > 0 }

    val distanceToActiveClimbEndM: Double?
        get() = activeClimb?.distanceToEndM
}
