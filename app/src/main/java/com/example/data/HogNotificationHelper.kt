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
    private val channelId = "hog_alerts"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PostHog Metric Alerts"
            val descriptionText = "Instant notifications triggered when insights cross threshold limits"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            if (BuildConfig.DEBUG) Log.d("HogNotificationHelper", "Created Alert notification channel")
        }
    }

    fun showAlertNotification(alertId: Int, alertName: String, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALERT_ID", alertId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Use standard accessible notification icon from android resources
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(alertId, builder.build())
            if (BuildConfig.DEBUG) Log.d("HogNotificationHelper", "Dispatched alert notification: $alertId - $title")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("HogNotificationHelper", "Failed to present notification", e)
        }
    }
}
