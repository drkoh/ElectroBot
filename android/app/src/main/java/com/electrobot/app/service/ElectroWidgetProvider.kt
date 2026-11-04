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
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET ||
            intent.action == Intent.ACTION_TIME_TICK ||
            intent.action == Intent.ACTION_BOOT_COMPLETED
        ) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.electrobot.app.ACTION_UPDATE_WIDGET"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, ElectroWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE)
            val activeQueue = prefs.getString("active_queue", "3.1") ?: "3.1"
            val activeAddress = prefs.getString("active_address", "м. Харків, вул. Сумська, 40") ?: "м. Харків, вул. Сумська, 40"

            val schedule = NetworkManager.getFallbackSchedule()
            val status = StatusResolver.resolve(
                addressQuery = activeAddress,
                queueName = activeQueue,
                schedule = schedule,
                outages1562 = emptyList()
            )

            val views = RemoteViews(context.packageName, R.layout.widget_electrobot)

            // Queue Badge
            views.setTextViewText(R.id.widget_queue_text, "Черга $activeQueue")

            // Status Badge & Action
            if (status.isOutage) {
                if (status.isEmergency) {
                    views.setTextViewText(R.id.widget_status_badge, "🚨 АВАРІЯ")
                    views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_red)
                } else {
                    views.setTextViewText(R.id.widget_status_badge, "🔴 ЗНЕСТРУМЛЕНО")
                    views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_red)
                }
                views.setTextViewText(R.id.widget_action_text, "Очікуване увімкнення о ${status.targetTimeStr}")
            } else {
                views.setTextViewText(R.id.widget_status_badge, "🟢 СВІТЛО Є")
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_green)
                views.setTextViewText(R.id.widget_action_text, "До відключення за графіком о ${status.targetTimeStr}")
            }

            // Countdown & Charge Window
            views.setTextViewText(R.id.widget_countdown, "⏱️ ${status.countdownHms}")
            views.setTextViewText(R.id.widget_charge_window, status.chargeWindowStr)

            // Address & Updated Time
            views.setTextViewText(R.id.widget_address_text, activeAddress)
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_updated_time, "Оновл. $timeStr")

            // Click Root -> Open MainActivity
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

            // Click Refresh Button -> Broadcast ACTION_UPDATE_WIDGET
            val refreshIntent = Intent(context, ElectroWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val refreshPi = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
