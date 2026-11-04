package com.electrobot.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.electrobot.app.MainActivity
import com.electrobot.app.R

class OutageAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "⚡ Попередження про відключення світла"
        val message = intent.getStringExtra("message") ?: "За 15 хвилин розпочнеться заплановане відключення."
        val notifId = intent.getIntExtra("notif_id", 1001)
        showNotification(context, title, message, notifId)
    }

    companion object {
        fun showNotification(context: Context, title: String, message: String, notifId: Int = 1001) {
            android.util.Log.i("OutageAlarmReceiver", ">>> showNotification CALLED: notifId=$notifId, title=$title, message=$message")
            val channelId = "electrobot_outage_channel"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Графік відключень світла",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Термінові сповіщення про зміну черг та аварійні відключення 1562"
                    enableVibration(true)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_outage)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(notifId, notif)
        }
    }
}
