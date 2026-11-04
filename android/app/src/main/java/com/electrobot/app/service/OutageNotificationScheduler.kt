package com.electrobot.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.electrobot.app.model.DailySchedule
import java.util.Calendar

object OutageNotificationScheduler {

    private const val TAG = "NotificationScheduler"
    private const val PREFS_NAME = "electrobot_prefs"
    const val KEY_NOTIF_ENABLED = "notif_outage_enabled"
    const val KEY_MINUTES_BEFORE = "notif_minutes_before"
    const val KEY_NOTIF_RESTORE = "notif_restore_enabled"

    fun isNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_ENABLED, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply()
    }

    fun getMinutesBefore(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MINUTES_BEFORE, 15)
    }

    fun setMinutesBefore(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MINUTES_BEFORE, minutes).apply()
    }

    fun isNotifyRestoreEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_RESTORE, true)
    }

    fun setNotifyRestoreEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIF_RESTORE, enabled).apply()
    }

    fun sendTestNotification(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeQueue = prefs.getString("active_queue", "1.1") ?: "1.1"
        OutageAlarmReceiver.showNotification(
            context = context,
            title = "⚡ Тестове сповіщення (Черга $activeQueue)",
            message = "Сповіщення налаштовано успішно. Додаток попереджатиме вас перед початком відключень для черги $activeQueue.",
            notifId = 9999
        )
    }

    fun scheduleAlarmsForToday(
        context: Context,
        queueName: String,
        schedule: DailySchedule?
    ) {
        if (!isNotificationsEnabled(context)) {
            cancelAllAlarms(context)
            return
        }
        if (schedule == null || queueName.isEmpty()) return

        val intervals = schedule.queues[queueName] ?: emptyList()
        val minutesBefore = getMinutesBefore(context)
        val notifyRestore = isNotifyRestoreEnabled(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = Calendar.getInstance()
        val curDayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Cancel existing alarm slots first
        cancelAllAlarms(context)

        intervals.forEachIndexed { index, interval ->
            // 1. Alarm before outage begins
            val outageStartMs = curDayStartMs + (interval.startMinute * 60 * 1000L)
            val alarmTimeMs = outageStartMs - (minutesBefore * 60 * 1000L)

            if (alarmTimeMs > now.timeInMillis) {
                val intent = Intent(context, OutageAlarmReceiver::class.java).apply {
                    putExtra("title", "⚡ Відключення світла за $minutesBefore хв (о ${interval.startStr})")
                    putExtra("message", "Черга $queueName: планове відключення з ${interval.startStr} до ${interval.endStr}. Перевірте заряд станції EcoFlow/Bluetti.")
                    putExtra("notif_id", 2000 + index)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2000 + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTimeMs, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTimeMs, pendingIntent)
                    }
                    Log.d(TAG, "Scheduled outage alarm for ${interval.startStr} at $alarmTimeMs")
                } catch (e: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTimeMs, pendingIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to schedule alarm: ${e.message}")
                }
            }

            // 2. Alarm before power restoration
            if (notifyRestore) {
                val outageEndMs = curDayStartMs + (interval.endMinute * 60 * 1000L)
                val restoreAlarmTimeMs = outageEndMs - (10 * 60 * 1000L) // 10 min before restore

                if (restoreAlarmTimeMs > now.timeInMillis) {
                    val intent = Intent(context, OutageAlarmReceiver::class.java).apply {
                        putExtra("title", "💡 Орієнтовне увімкнення о ${interval.endStr}")
                        putExtra("message", "Черга $queueName: через ~10 хвилин очікується подача електроенергії за графіком.")
                        putExtra("notif_id", 3000 + index)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        3000 + index,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                    )

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restoreAlarmTimeMs, pendingIntent)
                        } else {
                            alarmManager.set(AlarmManager.RTC_WAKEUP, restoreAlarmTimeMs, pendingIntent)
                        }
                    } catch (e: Exception) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restoreAlarmTimeMs, pendingIntent)
                    }
                }
            }
        }
    }

    private fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (id in 2000..2010) {
            val intent = Intent(context, OutageAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (pi != null) {
                alarmManager.cancel(pi)
            }
        }
        for (id in 3000..3010) {
            val intent = Intent(context, OutageAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (pi != null) {
                alarmManager.cancel(pi)
            }
        }
    }
}
