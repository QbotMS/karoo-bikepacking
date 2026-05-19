package com.bikepacking.karoo

import kotlin.math.*

object SunCalculator {

    fun civilDuskMs(latDeg: Double, lonDeg: Double, dateMs: Long): Long = civilTwilightMs(latDeg, lonDeg, dateMs, rise = false)

    fun civilDawnMs(latDeg: Double, lonDeg: Double, dateMs: Long): Long = civilTwilightMs(latDeg, lonDeg, dateMs, rise = true)

    private fun civilTwilightMs(latDeg: Double, lonDeg: Double, dateMs: Long, rise: Boolean): Long {
        val jd = dateMs / 86_400_000.0 + 2_440_587.5
        val n = floor(jd - 2_451_545.0 + 0.0008).toLong().toDouble()
        val g = (357.5291 + 0.98560028 * n) % 360.0
        val gRad = g * PI / 180.0
        val c = 1.9148 * sin(gRad) + 0.0200 * sin(2 * gRad) + 0.0003 * sin(3 * gRad)
        val lambda = (g + c + 180.0 + 102.9372) % 360.0
        val lambdaRad = lambda * PI / 180.0
        val sinD = sin(-6.0 * PI / 180.0)
        val cosLat = cos(latDeg * PI / 180.0)
        val sinLat = sin(latDeg * PI / 180.0)
        val sinDec = sin(lambdaRad) * sin(23.4393 * PI / 180.0)
        val cosDec = cos(asin(sinDec))
        val cosH = (sinD - sinLat * sinDec) / (cosLat * cosDec)
        if (cosH < -1.0 || cosH > 1.0) return if (cosH < -1.0) Long.MAX_VALUE else 0L
        val H = acos(cosH) * 180.0 / PI
        val jStar = 2_451_545.0 + 0.0009 + (-lonDeg / 360.0) + n
        val jTransit = jStar + 0.0053 * sin(gRad) - 0.0069 * sin(2 * lambdaRad)
        val jEvent = if (rise) jTransit - H / 360.0 else jTransit + H / 360.0
        return ((jEvent - 2_440_587.5) * 86_400_000.0).toLong()
    }
}
