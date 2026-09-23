package com.example.notification

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.DataUsageManager
import com.example.MainActivity

class DataTrackerNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ALERTS_CHANNEL_ID = "data_usage_alerts_channel"
        const val SPEED_CHANNEL_ID = "data_speed_notice_channel"

        const val ALERT_80_NOTIFICATION_ID = 8001
        const val ALERT_100_NOTIFICATION_ID = 10001
        const val SPEED_NOTIFICATION_ID = 12001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Data Usage Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifies when data usage reaches 80% or 100% of cap."
                enableVibration(true)
            }

            val speedChannel = NotificationChannel(
                SPEED_CHANNEL_ID,
                "Real-time Data Speed",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows real-time download and upload speeds in status bar."
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(speedChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getContentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun send80PercentAlert(usedBytes: Long, limitBytes: Long, isBits: Boolean = false) {
        if (!hasNotificationPermission()) return

        val usedStr = DataUsageManager.formatBytes(usedBytes, isBits)
        val limitStr = DataUsageManager.formatBytes(limitBytes, isBits)

        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_sys_warning)
            .setContentTitle("Data Alert: 80% Cap Reached")
            .setContentText("You have used $usedStr of your $limitStr limit.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Data consumption alert: You have reached 80% of your configured data limit ($usedStr / $limitStr). Monitor your usage to prevent overage charges.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getContentPendingIntent())
            .build()

        try {
            notificationManager.notify(ALERT_80_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun send100PercentAlert(usedBytes: Long, limitBytes: Long, isBits: Boolean = false) {
        if (!hasNotificationPermission()) return

        val usedStr = DataUsageManager.formatBytes(usedBytes, isBits)
        val limitStr = DataUsageManager.formatBytes(limitBytes, isBits)

        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_notify_error)
            .setContentTitle("CRITICAL: Data Limit Exceeded!")
            .setContentText("Limit reached! Used $usedStr of $limitStr cap.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("OVER-LIMIT WARNING: You have exceeded 100% of your data allowance ($usedStr / $limitStr). High background usage or carrier charges may apply!")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(getContentPendingIntent())
            .build()

        try {
            notificationManager.notify(ALERT_100_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun buildSpeedNotification(downSpeedBytes: Long, upSpeedBytes: Long, isBits: Boolean = false): Notification {
        val downStr = DataUsageManager.formatSpeed(downSpeedBytes, isBits)
        val upStr = DataUsageManager.formatSpeed(upSpeedBytes, isBits)

        return NotificationCompat.Builder(context, SPEED_CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_sys_download_done)
            .setContentTitle("Real-time Data Speed")
            .setContentText("↓ $downStr  •  ↑ $upStr")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(getContentPendingIntent())
            .build()
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun updateSpeedNotification(downSpeedBytes: Long, upSpeedBytes: Long, isBits: Boolean = false) {
        if (!hasNotificationPermission()) return
        val notification = buildSpeedNotification(downSpeedBytes, upSpeedBytes, isBits)
        try {
            notificationManager.notify(SPEED_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun cancelSpeedNotification() {
        notificationManager.cancel(SPEED_NOTIFICATION_ID)
    }
}
