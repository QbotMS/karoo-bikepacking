package com.bikepacking.karoo.message

import android.graphics.Color
import android.view.Gravity
import android.widget.RemoteViews

object RideMessageRenderer {

    private const val MSG_TEXT_SIZE_SP = 19f

    private const val BG_INFO = "#111827"
    private const val BG_ALARM = "#991B1B"
    private const val TEXT_INFO = "#FACC15"
    private const val TEXT_ALARM = "#FFFFFF"

    fun render(
        v: RemoteViews,
        msg: RideMessage,
        containerId: Int,
        line1Id: Int,
        line2Id: Int,
        textSizeSp: Float = MSG_TEXT_SIZE_SP,
    ) {
        val isAlarm = msg.severity == MessageSeverity.ALARM
            || msg.severity == MessageSeverity.CRITICAL
        val bgColor = if (isAlarm) Color.parseColor(BG_ALARM) else Color.parseColor(BG_INFO)
        val textColor = if (isAlarm) Color.parseColor(TEXT_ALARM) else Color.parseColor(TEXT_INFO)

        v.setInt(containerId, "setBackgroundColor", bgColor)
        v.setTextViewText(line1Id, msg.line1)
        v.setTextViewText(line2Id, msg.line2)
        v.setTextColor(line1Id, textColor)
        v.setTextColor(line2Id, textColor)
        v.setFloat(line1Id, "setTextSize", textSizeSp)
        v.setFloat(line2Id, "setTextSize", textSizeSp)
        v.setInt(line1Id, "setGravity", Gravity.CENTER)
        v.setInt(line2Id, "setGravity", Gravity.CENTER)
    }
}
