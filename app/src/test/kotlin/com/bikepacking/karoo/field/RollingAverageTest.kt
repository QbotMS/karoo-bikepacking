package com.bikepacking.karoo.field

import org.junit.Assert.assertEquals
import org.junit.Test

class RollingAverageTest {

    @Test
    fun `returns value when single sample added`() {
        val avg = RollingAverage()
        assertEquals(150f, avg.add(150f, 1000L), 0.001f)
    }

    @Test
    fun `averages multiple samples within window`() {
        val avg = RollingAverage()
        avg.add(100f, 1000L)
        avg.add(200f, 2000L)
        assertEquals(200f, avg.add(300f, 3000L), 0.001f)
    }

    @Test
    fun `drops samples outside window`() {
        val avg = RollingAverage(windowMs = 3000L)
        avg.add(100f, 0L)
        avg.add(200f, 1000L)
        // old sample at 0L should be dropped
        assertEquals(150f, avg.add(100f, 3001L), 0.001f)
    }

    @Test
    fun `returns zero when no samples`() {
        val avg = RollingAverage()
        assertEquals(0f, avg.average(), 0.001f)
    }

    @Test
    fun `reset clears all samples`() {
        val avg = RollingAverage()
        avg.add(150f, 1000L)
        avg.reset()
        assertEquals(0f, avg.average(), 0.001f)
    }

    @Test
    fun `average works with integer values`() {
        val avg = RollingAverage(windowMs = 5000L)
        avg.add(100f, 0L)
        avg.add(110f, 1000L)
        avg.add(90f, 2000L)
        assertEquals(100f, avg.add(100f, 3000L), 0.001f)
    }
}
