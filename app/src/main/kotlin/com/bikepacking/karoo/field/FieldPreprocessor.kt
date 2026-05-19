package com.bikepacking.karoo.field

object FieldPreprocessor {
    private const val GRADE_WINDOW = 5
    private val gradeHistory = ArrayDeque<Float>(GRADE_WINDOW)

    fun smoothedGrade(raw: Float): Float {
        gradeHistory.addLast(raw)
        while (gradeHistory.size > GRADE_WINDOW) gradeHistory.removeFirst()
        return if (gradeHistory.isEmpty()) raw else gradeHistory.average().toFloat()
    }

    fun reset() {
        gradeHistory.clear()
    }
}
