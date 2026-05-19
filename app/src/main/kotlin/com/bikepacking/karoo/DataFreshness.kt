package com.bikepacking.karoo

enum class DataFreshness { FRESH, STALE, MISSING }

data class FreshnessConfig(
    val staleMs: Long,
    val missingMs: Long,
)

object FreshnessConfigurations {
    val HR = FreshnessConfig(staleMs = 5_000L, missingMs = 12_000L)
    val POWER = FreshnessConfig(staleMs = 3_000L, missingMs = 8_000L)
    val CADENCE = FreshnessConfig(staleMs = 3_000L, missingMs = 8_000L)
    val SPEED = FreshnessConfig(staleMs = 5_000L, missingMs = 12_000L)
    val GRADE = FreshnessConfig(staleMs = 8_000L, missingMs = 20_000L)
    val GEAR = FreshnessConfig(staleMs = 5_000L, missingMs = 15_000L)
    val TEMP = FreshnessConfig(staleMs = 60_000L, missingMs = 180_000L)
    val WIND = FreshnessConfig(staleMs = 20_000L, missingMs = 60_000L)
}

class FreshnessTracker {

    private val lastUpdateMs = mutableMapOf<String, Long>()
    private val configs = mutableMapOf<String, FreshnessConfig>()

    fun configure(key: String, config: FreshnessConfig) {
        configs[key] = config
    }

    fun touch(key: String, nowMs: Long = System.currentTimeMillis()) {
        lastUpdateMs[key] = nowMs
    }

    fun getFreshness(key: String, nowMs: Long = System.currentTimeMillis()): DataFreshness {
        val config = configs[key] ?: return DataFreshness.MISSING
        val last = lastUpdateMs[key] ?: return DataFreshness.MISSING
        val age = nowMs - last
        return when {
            age >= config.missingMs -> DataFreshness.MISSING
            age >= config.staleMs -> DataFreshness.STALE
            else -> DataFreshness.FRESH
        }
    }

    fun getAgeMs(key: String, nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastUpdateMs[key] ?: return -1L
        return nowMs - last
    }

    fun reset() {
        lastUpdateMs.clear()
    }
}
