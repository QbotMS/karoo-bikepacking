package com.bikepacking.karoo.field

class RollingAverage(private val windowMs: Long = 3000L) {
    private data class Sample(val value: Float, val timestampMs: Long)
    private val samples = ArrayDeque<Sample>()

    fun add(value: Float, timestampMs: Long): Float {
        samples.addLast(Sample(value, timestampMs))
        val cutoff = timestampMs - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
        return average()
    }

    fun average(): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.value.toDouble()
        return (sum / samples.size).toFloat()
    }

    fun reset() {
        samples.clear()
    }
}
