package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AppWidgetProvider for Daily Mobile Data Tracker Home Screen Widget.
 */
class DailyDataAppWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all active widgets
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DailyDataAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            updateWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scope.launch {
            val manager = DataUsageManager(context)
            val hasPermission = manager.hasUsageAccessPermission()
            val usage = if (hasPermission) {
                manager.getDailyMobileDataUsage()
            } else {
                DailyMobileDataUsage(
                    startTimeMillis = manager.getStartOfDayMidnightMillis(),
                    endTimeMillis = System.currentTimeMillis()
                )
            }

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_daily_data)

                // 1. PendingIntent to open MainActivity when widget body is tapped
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

                // 2. PendingIntent to refresh widget on sync button tap
                val refreshIntent = Intent(context, DailyDataAppWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    widgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

                // 3. Populate Data
                if (!hasPermission) {
                    views.setTextViewText(R.id.widget_total_value, "Grant")
                    views.setTextViewText(R.id.widget_total_unit, "Access")
                    views.setTextViewText(R.id.widget_status_badge, "Needs Permission")
                    views.setTextViewText(R.id.widget_download_text, "--")
                    views.setTextViewText(R.id.widget_upload_text, "--")
                    views.setTextViewText(R.id.widget_hotspot_text, "--")
                    views.setProgressBar(R.id.widget_progress_bar, 100, 0, false)
                    views.setTextViewText(R.id.widget_updated_time, "Tap to open app & grant access")
                } else {
                    val (totalVal, totalUnit) = DataUsageManager.formatParts(usage.totalBytes)
                    views.setTextViewText(R.id.widget_total_value, totalVal)
                    views.setTextViewText(R.id.widget_total_unit, totalUnit)

                    val statusText = if (usage.isCellularConnected) {
                        if (!usage.carrierName.isNullOrBlank()) usage.carrierName else "Cellular"
                    } else {
                        "Today"
                    }
                    views.setTextViewText(R.id.widget_status_badge, statusText)

                    views.setTextViewText(
                        R.id.widget_download_text,
                        DataUsageManager.formatBytes(usage.rxBytes)
                    )
                    views.setTextViewText(
                        R.id.widget_upload_text,
                        DataUsageManager.formatBytes(usage.txBytes)
                    )
                    views.setTextViewText(
                        R.id.widget_hotspot_text,
                        DataUsageManager.formatBytes(usage.tetheringTotalBytes)
                    )

                    // Default 2GB target progress bar
                    val dailyTargetBytes = 2L * 1024L * 1024L * 1024L
                    val progressPercent = ((usage.totalBytes.toDouble() / dailyTargetBytes.toDouble()) * 100)
                        .toInt().coerceIn(0, 100)
                    views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)

                    val updatedTime = DataUsageManager.formatTime(System.currentTimeMillis())
                    views.setTextViewText(
                        R.id.widget_updated_time,
                        "From 12:00 AM • Updated $updatedTime"
                    )
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.ACTION_REFRESH_DATA_WIDGET"

        /**
         * Utility to trigger a widget refresh from anywhere in the application (e.g. on manual refresh in MainActivity).
         */
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(context, DailyDataAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }
}
