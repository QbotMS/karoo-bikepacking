package com.bikepacking.karoo.message

class MessagePriorityEngine(
    private val config: DynamicMessagesConfig = DynamicMessagesConfig()
) {
    private var activeMessage: RideMessage? = null
    private var activeSinceMs: Long = 0L
    private val lastShownAtByKey = mutableMapOf<String, Long>()

    fun select(candidates: List<RideMessage>, nowMs: Long = System.currentTimeMillis()): RideMessage? {
        activeMessage?.let { msg ->
            if (nowMs - activeSinceMs < msg.minDisplayMs) {
                return msg
            }
        }

        if (!config.enabled) {
            activeMessage = null
            return null
        }

        val selected = candidates
            .filter { isModuleAndSeverityAllowed(it) }
            .filter { isCooldownRespected(it, nowMs) }
            .maxWithOrNull(
                compareBy<RideMessage> { it.severity.level }
                    .thenBy { it.priority }
            )

        activeMessage = selected
        activeSinceMs = if (selected != null) nowMs else 0L

        selected?.let { lastShownAtByKey[messageKey(it)] = nowMs }

        return selected
    }

    fun dismissCurrent() {
        activeMessage = null
        activeSinceMs = 0L
    }

    fun currentActiveMessage(): RideMessage? = activeMessage

    private fun isModuleAndSeverityAllowed(msg: RideMessage): Boolean {
        if (!config.isModuleEnabled(msg.module)) return false
        return when (msg.severity) {
            MessageSeverity.INFO, MessageSeverity.WARNING -> config.informationalMessagesEnabled
            MessageSeverity.ALARM, MessageSeverity.CRITICAL -> config.alarmMessagesEnabled
        }
    }

    private fun isCooldownRespected(msg: RideMessage, nowMs: Long): Boolean {
        val key = messageKey(msg)
        val last = lastShownAtByKey[key] ?: return true
        return (nowMs - last) >= msg.cooldownMs
    }

    companion object {
        fun messageKey(msg: RideMessage): String = "${msg.module.name}_${msg.type}"
    }
}
