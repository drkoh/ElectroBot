package com.electrobot.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.electrobot.app.data.NetworkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = context.getSharedPreferences("electrobot_prefs", Context.MODE_PRIVATE)
            val queue = prefs.getString("active_queue", "3.1") ?: "3.1"
            val schedule = NetworkManager.getFallbackSchedule()
            OutageNotificationScheduler.scheduleAlarmsForToday(context, queue, schedule)
            ElectroWidgetProvider.updateAllWidgets(context)
        }
    }
}
