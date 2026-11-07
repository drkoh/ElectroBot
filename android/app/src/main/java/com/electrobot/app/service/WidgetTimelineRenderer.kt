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
        targetWidthDp: Int = 340,
        targetHeightDp: Int = 72,
        nowCal: Calendar = Calendar.getInstance()
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        val widthPx = (targetWidthDp * density).toInt().coerceAtLeast(600)
        val heightPx = (targetHeightDp * density).toInt().coerceAtLeast(140)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Paints
        val boxOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF9F0A.toInt() // Sleek amber border
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x24FF9F0A.toInt() // Subtle translucent amber fill
            style = Paint.Style.FILL
        }

        val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4A4D5C.toInt() // Subtle ruler baseline
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }

        val hourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8E92A4.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }

        val halfHourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF65697B.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }

        val quarterTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF444755.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
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
            color = 0xFFFF453A.toInt() // Crisp Apple red needle
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
        }

        val redPointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF453A.toInt()
            style = Paint.Style.FILL
        }

        // 2. Proportional Geometry (leaves ample breathing room at bottom)
        val boxTop = 8f * density
        val boxBottom = 38f * density
        val baselineY = 48f * density
        val hourTickTop = baselineY - 4f * density
        val hourTickBottom = baselineY + 6f * density
        val halfTickTop = baselineY - 2f * density
        val halfTickBottom = baselineY + 3.5f * density
        val quarterTickTop = baselineY - 1f * density
        val quarterTickBottom = baselineY + 2f * density
        val hourTextY = baselineY + 16f * density

        // 3. Time Window: 6.5 hours (390 min), 45 min in the past
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val windowStartMinutes = nowMinutes - 45
        val windowDurationMinutes = 390
        val windowEndMinutes = windowStartMinutes + windowDurationMinutes

        fun toX(minute: Int): Float {
            return ((minute - windowStartMinutes).toFloat() / windowDurationMinutes.toFloat()) * widthPx.toFloat()
        }

        // 4. Extract Outage Intervals (Today, Tomorrow, Yesterday)
        val cleanQueue = queueName.replace("Черга", "").trim()
        val rawIntervals = schedule?.queues?.get(cleanQueue) ?: emptyList()

        val allIntervals = mutableListOf<ScheduleInterval>()
        for (inv in rawIntervals) {
            allIntervals.add(inv) // Today
            allIntervals.add(ScheduleInterval(inv.startMinute + 1440, inv.endMinute + 1440, inv.startStr, inv.endStr)) // Tomorrow
            allIntervals.add(ScheduleInterval(inv.startMinute - 1440, inv.endMinute - 1440, inv.startStr, inv.endStr)) // Yesterday
        }

        // 5. Draw Outage Rounded Boxes with boundary edge clipping
        val cornerR = 7f * density
        for (inv in allIntervals) {
            if (inv.endMinute <= windowStartMinutes || inv.startMinute >= windowEndMinutes) continue

            val x1 = toX(inv.startMinute)
            val x2 = toX(inv.endMinute)

            val left = x1.coerceAtLeast(0f)
            val right = x2.coerceAtMost(widthPx.toFloat())
            val boxWidth = right - left

            if (boxWidth >= 4f * density) {
                val isLeftClipped = (x1 < 0f)
                val isRightClipped = (x2 > widthPx.toFloat())

                // Only round corners on unclipped boundaries!
                val radii = floatArrayOf(
                    if (isLeftClipped) 0f else cornerR, if (isLeftClipped) 0f else cornerR, // Top-Left
                    if (isRightClipped) 0f else cornerR, if (isRightClipped) 0f else cornerR, // Top-Right
                    if (isRightClipped) 0f else cornerR, if (isRightClipped) 0f else cornerR, // Bottom-Right
                    if (isLeftClipped) 0f else cornerR, if (isLeftClipped) 0f else cornerR  // Bottom-Left
                )

                val path = Path()
                path.addRoundRect(RectF(left, boxTop, right, boxBottom), radii, Path.Direction.CW)
                canvas.drawPath(path, boxFillPaint)
                canvas.drawPath(path, boxOutlinePaint)

                // Calculate duration in hours
                val durationHours = (inv.endMinute - inv.startMinute).toDouble() / 60.0
                val durStr = String.format(Locale.US, "💡 %.1f", durationHours)

                val textBounds = Rect()
                durationTextPaint.getTextBounds(durStr, 0, durStr.length, textBounds)
                val textW = textBounds.width()

                if (boxWidth >= textW + (10f * density)) {
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
                // Full hour tick (crosses baseline)
                canvas.drawLine(tx, hourTickTop, tx, hourTickBottom, hourTickPaint)
                val hourOfDay = (((tm / 60) % 24) + 24) % 24
                val label = String.format(Locale.US, "%02d:00", hourOfDay)
                if (hourTextY < heightPx) {
                    canvas.drawText(label, tx, hourTextY, hourTextPaint)
                }
            } else if (minuteOfHour == 30) {
                canvas.drawLine(tx, halfTickTop, tx, halfTickBottom, halfHourTickPaint)
            } else {
                canvas.drawLine(tx, quarterTickTop, tx, quarterTickBottom, quarterTickPaint)
            }
        }

        // 8. Draw Red Needle at Current Time
        val nowX = toX(nowMinutes)
        if (nowX in 0f..widthPx.toFloat()) {
            canvas.drawLine(nowX, boxTop - 2f * density, nowX, baselineY, redNeedlePaint)

            // Red pointer triangle
            val pointerPath = Path().apply {
                moveTo(nowX, baselineY - 1f * density)
                lineTo(nowX - 3.5f * density, baselineY + 6f * density)
                lineTo(nowX + 3.5f * density, baselineY + 6f * density)
                close()
            }
            canvas.drawPath(pointerPath, redPointerPaint)
        }

        return bitmap
    }
}
