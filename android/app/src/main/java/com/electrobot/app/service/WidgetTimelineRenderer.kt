package com.electrobot.app.service

import android.content.Context
import android.graphics.*
import com.electrobot.app.model.DailySchedule
import com.electrobot.app.model.ScheduleInterval
import java.util.Calendar
import java.util.Locale

object WidgetTimelineRenderer {

    fun renderTimeline(
        context: Context,
        schedule: DailySchedule?,
        queueName: String,
        targetWidthDp: Int = 320,
        targetHeightDp: Int = 58,
        nowCal: Calendar = Calendar.getInstance()
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        val widthPx = (targetWidthDp * density).toInt().coerceAtLeast(600)
        val heightPx = (targetHeightDp * density).toInt().coerceAtLeast(120)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Paints
        val boxOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF9F0A.toInt() // Amber orange
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2EFF9F0A.toInt() // Translucent amber fill
            style = Paint.Style.FILL
        }

        val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4A4D5C.toInt() // Dark grey baseline
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        val hourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8E92A4.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        val halfHourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF65697B.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        val quarterTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF444755.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }

        val hourTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8E92A4.toInt()
            textSize = 9.5f * scaledDensity
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        val durationTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 11f * scaledDensity
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val redNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF453A.toInt() // Apple / Material Red
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }

        val redPointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF453A.toInt()
            style = Paint.Style.FILL
        }

        // 2. Geometry
        val boxTop = 4f * density
        val boxBottom = heightPx * 0.54f
        val baselineY = heightPx * 0.65f
        val hourTickBottom = baselineY + 7f * density
        val halfHourTickBottom = baselineY + 4.5f * density
        val quarterTickBottom = baselineY + 2.5f * density
        val hourTextY = baselineY + 16f * density

        // 3. Time Window: 6.5 hours (390 min), past 45 min visible on left
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val windowStartMinutes = nowMinutes - 45
        val windowDurationMinutes = 390
        val windowEndMinutes = windowStartMinutes + windowDurationMinutes

        fun toX(minute: Int): Float {
            return ((minute - windowStartMinutes).toFloat() / windowDurationMinutes.toFloat()) * widthPx.toFloat()
        }

        // 4. Extract Outage Intervals (Today, Tomorrow +1440, Yesterday -1440)
        val cleanQueue = queueName.replace("Черга", "").trim()
        val rawIntervals = schedule?.queues?.get(cleanQueue) ?: emptyList()

        val allIntervals = mutableListOf<ScheduleInterval>()
        for (inv in rawIntervals) {
            allIntervals.add(inv) // Today
            allIntervals.add(ScheduleInterval(inv.startMinute + 1440, inv.endMinute + 1440, inv.startStr, inv.endStr)) // Tomorrow
            allIntervals.add(ScheduleInterval(inv.startMinute - 1440, inv.endMinute - 1440, inv.startStr, inv.endStr)) // Yesterday
        }

        // 5. Draw Outage Rounded Boxes
        for (inv in allIntervals) {
            if (inv.endMinute <= windowStartMinutes || inv.startMinute >= windowEndMinutes) continue

            val x1 = toX(inv.startMinute)
            val x2 = toX(inv.endMinute)

            val left = x1.coerceAtLeast(0f)
            val right = x2.coerceAtMost(widthPx.toFloat())
            val boxWidth = right - left

            if (boxWidth >= 6f * density) {
                val cornerR = 8f * density
                val rectF = RectF(left, boxTop, right, boxBottom)
                canvas.drawRoundRect(rectF, cornerR, cornerR, boxFillPaint)
                canvas.drawRoundRect(rectF, cornerR, cornerR, boxOutlinePaint)

                // Calculate duration in hours
                val durationHours = (inv.endMinute - inv.startMinute).toDouble() / 60.0
                val durStr = String.format(Locale.US, "💡 %.1f", durationHours)

                val textBounds = Rect()
                durationTextPaint.getTextBounds(durStr, 0, durStr.length, textBounds)
                val textW = textBounds.width()

                if (boxWidth >= textW + (12f * density)) {
                    val cx = (left + right) / 2f
                    val cy = (boxTop + boxBottom) / 2f - textBounds.exactCenterY()
                    canvas.drawText(durStr, cx, cy, durationTextPaint)
                }
            }
        }

        // 6. Draw Ruler Baseline
        canvas.drawLine(0f, baselineY, widthPx.toFloat(), baselineY, baselinePaint)

        // 7. Draw Ticks & Hour Labels
        val startTick = (windowStartMinutes / 15) * 15
        val endTick = ((windowEndMinutes / 15) + 1) * 15

        for (tm in startTick..endTick step 15) {
            val tx = toX(tm)
            if (tx < -10f || tx > widthPx.toFloat() + 10f) continue

            val minuteOfHour = ((tm % 60) + 60) % 60
            if (minuteOfHour == 0) {
                // Full hour
                canvas.drawLine(tx, baselineY, tx, hourTickBottom, hourTickPaint)
                val hourOfDay = (((tm / 60) % 24) + 24) % 24
                val label = String.format(Locale.US, "%02d:00", hourOfDay)
                if (hourTextY < heightPx) {
                    canvas.drawText(label, tx, hourTextY, hourTextPaint)
                }
            } else if (minuteOfHour == 30) {
                canvas.drawLine(tx, baselineY, tx, halfHourTickBottom, halfHourTickPaint)
            } else {
                canvas.drawLine(tx, baselineY, tx, quarterTickBottom, quarterTickPaint)
            }
        }

        // 8. Draw Red Needle at Current Time
        val nowX = toX(nowMinutes)
        if (nowX in 0f..widthPx.toFloat()) {
            canvas.drawLine(nowX, boxTop, nowX, baselineY, redNeedlePaint)

            // Red pointer triangle
            val pointerPath = Path().apply {
                moveTo(nowX, baselineY - 2f * density)
                lineTo(nowX - 4f * density, baselineY + 6f * density)
                lineTo(nowX + 4f * density, baselineY + 6f * density)
                close()
            }
            canvas.drawPath(pointerPath, redPointerPaint)
        }

        return bitmap
    }
}
