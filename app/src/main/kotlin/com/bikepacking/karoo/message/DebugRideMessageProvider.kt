package com.bikepacking.karoo.message

class DebugRideMessageProvider(
    private val config: DynamicMessagesConfig = DynamicMessagesConfig(),
) {
    fun generate(nowMs: Long): RideMessage? {
        return when (config.debugMessageMode) {
            DebugMessageMode.OFF -> null
            DebugMessageMode.INFO -> RideMessage(
                type = "DEBUG_INFO",
                module = RideMessageModule.CLIMB_PACING,
                severity = MessageSeverity.INFO,
                priority = 10,
                line1 = "INFO TEST",
                line2 = "ciemne tło · żółty tekst",
                minDisplayMs = 60000L,
                cooldownMs = 60000L,
                createdAtMs = nowMs,
            )
            DebugMessageMode.WARNING -> RideMessage(
                type = "DEBUG_WARNING",
                module = RideMessageModule.CLIMB_PACING,
                severity = MessageSeverity.WARNING,
                priority = 20,
                line1 = "UWAGA TEST",
                line2 = "ciemne tło · żółty tekst",
                minDisplayMs = 60000L,
                cooldownMs = 60000L,
                createdAtMs = nowMs,
            )
            DebugMessageMode.ALARM -> RideMessage(
                type = "DEBUG_ALARM",
                module = RideMessageModule.CLIMB_PACING,
                severity = MessageSeverity.ALARM,
                priority = 60,
                line1 = "ALARM TEST",
                line2 = "czerwone tło · biały tekst",
                minDisplayMs = 60000L,
                cooldownMs = 60000L,
                createdAtMs = nowMs,
            )
            DebugMessageMode.CLIMB_AHEAD -> RideMessage(
                type = "CLIMB_AHEAD",
                module = RideMessageModule.CLIMB_PACING,
                severity = MessageSeverity.WARNING,
                priority = 50,
                line1 = "PODJAZD 3/7",
                line2 = "850m · 6%",
                minDisplayMs = 60000L,
                cooldownMs = 60000L,
                createdAtMs = nowMs,
            )
            DebugMessageMode.CLIMB_TARGET -> RideMessage(
                type = "CLIMB_HOLD_TARGET",
                module = RideMessageModule.CLIMB_PACING,
                severity = MessageSeverity.INFO,
                priority = 45,
                line1 = "3/7 · 7%",
                line2 = "TRZYMAJ 190–210W",
                minDisplayMs = 60000L,
                cooldownMs = 60000L,
                createdAtMs = nowMs,
            )
            DebugMessageMode.EASE_OFF -> RideMessage(
                type = "CLIMB_EASE_OFF",
                module = RideMessageModule.CLIMB_PACING,
                severity = MessageSeverity.ALARM,
                priority = 70,
                line1 = "ODPUŚĆ!",
                line2 = "CEL 190–210W",
                minDisplayMs = 60000L,
                cooldownMs = 60000L,
                createdAtMs = nowMs,
            )
            DebugMessageMode.SUMMARY -> RideMessage(
                type = "CLIMB_SUMMARY",
                module = RideMessageModule.CLIMB_PACING,
                severity = MessageSeverity.WARNING,
                priority = 45,
                line1 = "3/7 ZA MOCNO",
                line2 = "+54m · zostało 420m",
                minDisplayMs = 60000L,
                cooldownMs = 60000L,
                createdAtMs = nowMs,
            )
        }
    }
}
