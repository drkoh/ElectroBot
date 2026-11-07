package com.electrobot.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object WidgetUpdateScheduler {

    private const val ALARM_INTERVAL_MILLIS = 60_000L // 1 minute interval

    fun scheduleNextUpdate(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ElectroWidgetProvider::class.java).apply {
            action = ElectroWidgetProvider.ACTION_UPDATE_WIDGET
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getBroadcast(context, 9001, intent, flags)

        val triggerAt = System.currentTimeMillis() + ALARM_INTERVAL_MILLIS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // In case exact alarm permission is restricted, fallback to inexact
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelUpdates(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ElectroWidgetProvider::class.java).apply {
            action = ElectroWidgetProvider.ACTION_UPDATE_WIDGET
        }
        val flags = PendingIntent.FLAG_NO_CREATE or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getBroadcast(context, 9001, intent, flags)
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }
}
