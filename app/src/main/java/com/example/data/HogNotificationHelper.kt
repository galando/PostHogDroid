package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.BuildConfig
import com.example.MainActivity

class HogNotificationHelper(private val context: Context) {
    private val alertChannelId = "hog_alerts"
    private val digestChannelId = "hog_digest"

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                alertChannelId,
                "Metric Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant alerts when a metric crosses its threshold"
            }

            val digestChannel = NotificationChannel(
                digestChannelId,
                "Daily Summary Digest",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily summary of active rules and alert statuses"
            }

            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(digestChannel)
            if (BuildConfig.DEBUG) Log.d("HogNotificationHelper", "Created notification channels")
        }
    }

    fun showAlertNotification(alertId: Int, alertName: String, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALERT_ID", alertId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, alertId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, alertChannelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        try {
            NotificationManagerCompat.from(context).notify(alertId, builder.build())
            if (BuildConfig.DEBUG) Log.d("HogNotificationHelper", "Alert notification sent: $alertId")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("HogNotificationHelper", "Failed to send alert notification", e)
        }
    }

    fun showDigestNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, DIGEST_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, digestChannelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)

        try {
            NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, builder.build())
            if (BuildConfig.DEBUG) Log.d("HogNotificationHelper", "Digest notification sent")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("HogNotificationHelper", "Failed to send digest notification", e)
        }
    }

    companion object {
        private const val DIGEST_NOTIFICATION_ID = 9999
    }
}
