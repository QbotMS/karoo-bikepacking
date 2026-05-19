package com.bikepacking.karoo.field

enum class ClimbDifficulty {
    UNKNOWN,
    FLAT,
    ROLLING,
    MODERATE,
    STEEP,
    VERY_STEEP,
}

data class ClimbInfo(
    val index: Int,
    val total: Int,
    val startDistanceM: Double,
    val lengthM: Double,
    val totalElevationM: Double,
    val distanceToStartM: Double,
    val distanceToEndM: Double,
    val remainingElevationM: Double,
    val progressOnClimbPercent: Double,
) {
    val endDistanceM: Double
        get() = startDistanceM + lengthM

    val label: String
        get() = "${index + 1}/$total"

    val averageGradePercent: Double
        get() = if (lengthM > 0) (totalElevationM / lengthM * 100) else 0.0

    val difficultyBucket: ClimbDifficulty
        get() = when {
            averageGradePercent < 2.0 -> ClimbDifficulty.FLAT
            averageGradePercent < 4.0 -> ClimbDifficulty.ROLLING
            averageGradePercent < 7.0 -> ClimbDifficulty.MODERATE
            averageGradePercent < 10.0 -> ClimbDifficulty.STEEP
            else -> ClimbDifficulty.VERY_STEEP
        }
}
