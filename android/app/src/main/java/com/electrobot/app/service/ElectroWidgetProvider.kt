package com.electrobot.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.electrobot.app.MainActivity
import com.electrobot.app.R
import com.electrobot.app.data.NetworkManager
import com.electrobot.app.data.StatusResolver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ElectroWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        WidgetUpdateScheduler.scheduleNextUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateScheduler.cancelUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_UPDATE_WIDGET ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            updateAllWidgets(context)
            WidgetUpdateScheduler.scheduleNextUpdate(context)
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.electrobot.app.ACTION_UPDATE_WIDGET"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, ElectroWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds.isNotEmpty()) {
                    for (widgetId in allWidgetIds) {
                        updateAppWidget(context, appWidgetManager, widgetId)
                    }
                    WidgetUpdateScheduler.scheduleNextUpdate(context)
                }
            } catch (e: Exception) {
                android.util.Log.e("ElectroWidgetProvider", "Error updating widgets: ${e.message}")
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE)
            val rawQueue = prefs.getString("active_queue", "2.2") ?: "2.2"
            val cleanQueue = rawQueue.replace("Черга", "").trim().ifEmpty { "2.2" }
            val activeAddress = prefs.getString("active_address", "вул. Дудинської, 9") ?: "вул. Дудинської, 9"

            val schedule = NetworkManager.loadScheduleCache(context) ?: NetworkManager.getFallbackSchedule()
            val nowCal = Calendar.getInstance()
            val status = StatusResolver.resolve(
                addressQuery = activeAddress,
                queueName = cleanQueue,
                schedule = schedule,
                outages1562 = emptyList()
            )

            val views = RemoteViews(context.packageName, R.layout.widget_electrobot)

            // 1. Top-Left: Queue Header & Timestamp
            views.setTextViewText(R.id.widget_queue_text, "#$cleanQueue")
            views.setTextViewText(
                R.id.widget_bulb_icon,
                if (status.isOutage) " 💡̸" else " 💡"
            )

            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_timestamp, "Станом на $timeStr")

            // 2. Top-Right: Status & Time Remaining
            if (status.isOutage) {
                views.setTextViewText(
                    R.id.widget_status_text,
                    if (status.isEmergency) "Аварія" else "Вимкнено"
                )
                if (status.isEmergency) {
                    views.setTextViewText(R.id.widget_timer_text, "Графік аварійний")
                } else if (status.targetSecondsLeft > 0) {
                    val mins = (status.targetSecondsLeft + 59) / 60
                    val h = mins / 60
                    val m = mins % 60
                    val durStr = if (h > 0 && m > 0) "${h}год ${m}хв" else if (h > 0) "${h}год" else "${m}хв"
                    views.setTextViewText(R.id.widget_timer_text, "Відновиться через ~$durStr")
                } else {
                    views.setTextViewText(R.id.widget_timer_text, "Очікується увімкнення")
                }
            } else {
                views.setTextViewText(R.id.widget_status_text, "Увімкнено")
                if (status.targetSecondsLeft > 0) {
                    val mins = (status.targetSecondsLeft + 59) / 60
                    val h = mins / 60
                    val m = mins % 60
                    val durStr = if (h > 0 && m > 0) "${h}год ${m}хв" else if (h > 0) "${h}год" else "${m}хв"
                    views.setTextViewText(R.id.widget_timer_text, "Вимкнеться через ~$durStr")
                } else {
                    views.setTextViewText(R.id.widget_timer_text, "Без обмежень до кінця доби")
                }
            }

            // 3. Bottom: Render Timeline Bitmap
            try {
                val timelineBmp = WidgetTimelineRenderer.renderTimeline(
                    context = context,
                    schedule = schedule,
                    queueName = cleanQueue,
                    targetWidthDp = 340,
                    targetHeightDp = 72,
                    nowCal = nowCal
                )
                views.setImageViewBitmap(R.id.widget_timeline_image, timelineBmp)
            } catch (e: Exception) {
                android.util.Log.e("ElectroWidgetProvider", "Error rendering timeline bitmap: ${e.message}")
            }

            // 4. Click Root -> Open MainActivity and trigger immediate refresh
            val rootIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val rootPi = PendingIntent.getActivity(
                context,
                0,
                rootIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_root, rootPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
