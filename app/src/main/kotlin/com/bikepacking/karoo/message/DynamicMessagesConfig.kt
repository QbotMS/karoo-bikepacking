package com.bikepacking.karoo.message

enum class DebugMessageMode {
    OFF,
    INFO,
    WARNING,
    ALARM,
    CLIMB_AHEAD,
    CLIMB_TARGET,
    EASE_OFF,
    SUMMARY,
}

data class DynamicMessagesConfig(
    val enabled: Boolean = true,
    val alarmMessagesEnabled: Boolean = true,
    val informationalMessagesEnabled: Boolean = true,
    val debugMessageMode: DebugMessageMode = DebugMessageMode.OFF,
    val moduleEnabled: Map<RideMessageModule, Boolean> = mapOf(
        RideMessageModule.CLIMB_PACING to true,
        RideMessageModule.ROUTE_HORIZON to true,
        RideMessageModule.RIDE_TREND to false,
        RideMessageModule.FINISH_ROUTE_RISK to false,
        RideMessageModule.INTAKE to false,
        RideMessageModule.LIGHT_TWILIGHT to true,
        RideMessageModule.STOP_PAUSE to false,
        RideMessageModule.WEATHER_SHIFT to false,
        RideMessageModule.CALIBRATION_SETUP to true,
        RideMessageModule.SENSOR_DATA to true,
        RideMessageModule.BATTERY_DEVICE to true,
    )
) {
    fun isModuleEnabled(module: RideMessageModule): Boolean = moduleEnabled[module] ?: true
}
