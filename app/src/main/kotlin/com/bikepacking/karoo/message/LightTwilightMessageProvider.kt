package com.bikepacking.karoo.message

import com.bikepacking.karoo.SunCalculator
import java.util.Calendar
import java.util.TimeZone

class LightTwilightMessageProvider(
    private val sunCalc: SunCalculator = SunCalculator
) {
    private var lastDuskWarningDay: Int = -1
    private var lastDawnInfoDay: Int = -1

    fun generate(
        nowMs: Long,
        latitude: Double,
        longitude: Double,
        etaMs: Long?,
        hasRoute: Boolean,
        remainingKm: Float?,
        isRiding: Boolean,
    ): List<RideMessage> {
        val messages = mutableListOf<RideMessage>()
        val dayOfYear = dayOfYear(nowMs)

        val noonTodayMs = (nowMs / 86_400_000L) * 86_400_000L + 43_200_000L
        val civilDuskMs = sunCalc.civilDuskMs(latitude, longitude, noonTodayMs)
        val civilDawnMs = sunCalc.civilDawnMs(latitude, longitude, noonTodayMs)

        if (civilDuskMs > 0L && civilDuskMs != Long.MAX_VALUE) {
            val msBeforeDusk = civilDuskMs - nowMs

            val canShowDusk = dayOfYear != lastDuskWarningDay
            if (canShowDusk) {
                val willFinishBeforeDusk = etaMs != null && etaMs + 15 * 60_000L < civilDuskMs

                if (msBeforeDusk in (20 * 60_000L)..(35 * 60_000L) && !willFinishBeforeDusk) {
                    messages.add(
                        RideMessage(
                            type = "TWILIGHT_WARNING_30",
                            module = RideMessageModule.LIGHT_TWILIGHT,
                            severity = MessageSeverity.WARNING,
                            priority = 50,
                            line1 = "ZMIERZCH ZA 30M",
                            line2 = "WŁĄCZ LAMPĘ",
                            minDisplayMs = 8000L,
                            cooldownMs = 60_000L,
                            createdAtMs = nowMs
                        )
                    )
                    lastDuskWarningDay = dayOfYear
                }
            }

            if (nowMs >= civilDuskMs && isRiding) {
                messages.add(
                    RideMessage(
                        type = "TWILIGHT_ALARM",
                        module = RideMessageModule.LIGHT_TWILIGHT,
                        severity = MessageSeverity.ALARM,
                        priority = 60,
                        line1 = "JUŻ PO ZMIERZCHU",
                        line2 = "SPRAWDŹ LAMPĘ",
                        minDisplayMs = 10_000L,
                        cooldownMs = 300_000L,
                        createdAtMs = nowMs
                    )
                )
            }
        }

        if (civilDawnMs > 0L && civilDawnMs != Long.MAX_VALUE) {
            val canShowDawn = dayOfYear != lastDawnInfoDay
            val msBeforeDawn = civilDawnMs - nowMs

            if (msBeforeDawn in (20 * 60_000L)..(35 * 60_000L) && canShowDawn) {
                messages.add(
                    RideMessage(
                        type = "DAWN_INFO",
                        module = RideMessageModule.LIGHT_TWILIGHT,
                        severity = MessageSeverity.INFO,
                        priority = 30,
                        line1 = "ŚWIT ZA 30M",
                        line2 = "ŚWIATŁO WRACA",
                        minDisplayMs = 6000L,
                        cooldownMs = 60_000L,
                        createdAtMs = nowMs
                    )
                )
                lastDawnInfoDay = dayOfYear
            }
        }

        return messages
    }

    fun resetDailyTrack() {
        lastDuskWarningDay = -1
        lastDawnInfoDay = -1
    }

    private fun dayOfYear(ms: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return cal.get(Calendar.DAY_OF_YEAR)
    }
}
