package com.example

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class DataTrackerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = DataUsageManager(context)
        val hasPermission = manager.hasUsageAccessPermission()
        val report = if (hasPermission) {
            manager.getUnifiedUsageReport(NetworkFilter.CELLULAR_SIM1)
        } else {
            null
        }

        val totalBytes = report?.totalBytes ?: 0L
        val limitBytes = 6L * 1024L * 1024L * 1024L // Updated to 6.0 GB daily cap
        val progress = (totalBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f)
        
        // Sample live speeds
        val speeds = manager.getLiveSpeed()
        val speedStr = "${DataUsageManager.formatSpeed(speeds.downloadSpeedBytesPerSec)} ↓  " +
                       "${DataUsageManager.formatSpeed(speeds.uploadSpeedBytesPerSec)} ↑"

        provideContent {
            WidgetLayout(
                speedText = speedStr,
                totalText = DataUsageManager.formatBytes(totalBytes),
                progress = progress,
                hasPermission = hasPermission,
                report = report
            )
        }
    }
}

@Composable
private fun WidgetLayout(
    speedText: String,
    totalText: String,
    progress: Float,
    hasPermission: Boolean,
    report: DataUsageReport?
) {
    // One UI translucent background style
    val bgColor = ColorProvider(Color(0xD21E1E1E))
    val textColor = ColorProvider(Color.White)
    val subTextColor = ColorProvider(Color.LightGray)
    val accentColor = ColorProvider(Color(0xFF4A90E2))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Data Monitor",
                        style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                    val detailText = if (!hasPermission) {
                        "Grant Access Permission"
                    } else if (report != null) {
                        "Today: $totalText • ${report.topAppName}"
                    } else {
                        "Today: $totalText"
                    }
                    Text(
                        text = detailText,
                        style = TextStyle(color = subTextColor, fontSize = 12.sp)
                    )
                }
                
                Text(
                    text = speedText,
                    style = TextStyle(color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = accentColor,
                backgroundColor = ColorProvider(Color.DarkGray)
            )
        }
    }
}

class DataTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DataTrackerWidget()
}
