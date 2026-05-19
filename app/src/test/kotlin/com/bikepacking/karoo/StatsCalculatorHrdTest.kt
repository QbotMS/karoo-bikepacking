package com.bikepacking.karoo

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StatsCalculatorHrdTest {

    private lateinit var stats: StatsCalculator

    @Before
    fun setup() {
        stats = StatsCalculator(ftpWatts = 200)
    }

    @Test
    fun `0-20 min returns WAIT`() {
        val now = System.currentTimeMillis()
        val result = stats.updateHRD(
            nowMs = now,
            elapsedSec = 15 * 60L,
            movingSec = 15 * 60L,
            powerWatts = 150,
            hrBpm = 120,
            cadenceRpm = 80,
        )
        assertEquals("WAIT", result.status)
        assertEquals("WAIT", result.phase)
    }

    @Test
    fun `no HR returns INVALID`() {
        val now = System.currentTimeMillis()
        val result = stats.updateHRD(
            nowMs = now,
            elapsedSec = 40 * 60L,
            movingSec = 40 * 60L,
            powerWatts = 150,
            hrBpm = 0,
            cadenceRpm = 80,
        )
        assertEquals("INVALID", result.status)
    }

    @Test
    fun `cadence zero returns INVALID`() {
        val now = System.currentTimeMillis()
        val result = stats.updateHRD(
            nowMs = now,
            elapsedSec = 40 * 60L,
            movingSec = 40 * 60L,
            powerWatts = 150,
            hrBpm = 120,
            cadenceRpm = 0,
        )
        assertEquals("INVALID", result.status)
    }
}