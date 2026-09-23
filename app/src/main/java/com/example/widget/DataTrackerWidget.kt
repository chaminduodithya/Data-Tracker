package com.example.widget

import android.R
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.DataUsageManager
import com.example.MainActivity

class DataTrackerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        private val SMALL_2X1 = DpSize(120.dp, 60.dp)
        private val EXPANDED_4X2 = DpSize(240.dp, 110.dp)

        val BlueColor = Color(0xFF2C6BED)
        val RedColor = Color(0xFFFF3B30)
        val OrangeColor = Color(0xFFFF9500)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL_2X1, EXPANDED_4X2))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val usedBytes = prefs[DataWidgetWorker.USED_BYTES_KEY] ?: 0L
            val limitBytes = prefs[DataWidgetWorker.LIMIT_BYTES_KEY] ?: 2147483648L
            val displayLabel = prefs[DataWidgetWorker.DISPLAY_LABEL_KEY] ?: "SIM 1 • Mobile"
            val layoutStyle = prefs[DataWidgetWorker.LAYOUT_STYLE_KEY] ?: "FULL"

            val top1Name = prefs[DataWidgetWorker.TOP_APP_1_NAME] ?: ""
            val top1Bytes = prefs[DataWidgetWorker.TOP_APP_1_BYTES] ?: 0L
            val top2Name = prefs[DataWidgetWorker.TOP_APP_2_NAME] ?: ""
            val top2Bytes = prefs[DataWidgetWorker.TOP_APP_2_BYTES] ?: 0L
            val top3Name = prefs[DataWidgetWorker.TOP_APP_3_NAME] ?: ""
            val top3Bytes = prefs[DataWidgetWorker.TOP_APP_3_BYTES] ?: 0L

            val size = LocalSize.current
            val isExpanded = size.width >= 200.dp && layoutStyle != "GAUGE_ONLY"

            WidgetRoot(
                context = context,
                usedBytes = usedBytes,
                limitBytes = limitBytes,
                displayLabel = displayLabel,
                layoutStyle = layoutStyle,
                top1Name = top1Name,
                top1Bytes = top1Bytes,
                top2Name = top2Name,
                top2Bytes = top2Bytes,
                top3Name = top3Name,
                top3Bytes = top3Bytes,
                isExpanded = isExpanded
            )
        }
    }

    @Composable
    private fun WidgetRoot(
        context: Context,
        usedBytes: Long,
        limitBytes: Long,
        displayLabel: String,
        layoutStyle: String,
        top1Name: String,
        top1Bytes: Long,
        top2Name: String,
        top2Bytes: Long,
        top3Name: String,
        top3Bytes: Long,
        isExpanded: Boolean
    ) {
        val ratio = if (limitBytes > 0) (usedBytes.toDouble() / limitBytes.toDouble()) else 0.0
        val percentageInt = (ratio * 100).toInt()
        val isOverLimit = ratio >= 1.0
        val isNearLimit = ratio >= 0.8 && !isOverLimit

        val accentColor = when {
            isOverLimit -> RedColor
            isNearLimit -> OrangeColor
            else -> BlueColor
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(8.dp)
                .background(ColorProvider(Color(0xFF1C1C1E)))
                .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header Row: Target Label & Percentage Badge & Refresh Button
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayLabel,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF8E8E93)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    // Percentage Badge
                    Box(
                        modifier = GlanceModifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .background(ColorProvider(accentColor.copy(alpha = 0.25f)))
                    ) {
                        Text(
                            text = "$percentageInt%",
                            style = TextStyle(
                                color = ColorProvider(accentColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(GlanceModifier.width(6.dp))

                    // Manual Refresh Button
                    Image(
                        provider = ImageProvider(R.drawable.ic_popup_sync),
                        contentDescription = "Refresh Widget",
                        modifier = GlanceModifier
                            .size(16.dp)
                            .clickable(actionRunCallback<RefreshActionCallback>())
                    )
                }

                Spacer(GlanceModifier.height(4.dp))

                // Main Usage Text
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = DataUsageManager.formatBytes(usedBytes),
                        style = TextStyle(
                            color = ColorProvider(if (isOverLimit) RedColor else Color.White),
                            fontSize = if (isExpanded) 20.sp else 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = " / ${DataUsageManager.formatBytes(limitBytes)}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFAEAEB2)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                Spacer(GlanceModifier.height(6.dp))

                // Progress Gauge Bar
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(ColorProvider(Color.White.copy(alpha = 0.15f)))
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .background(ColorProvider(accentColor)),
                        content = {}
                    )
                }

                // Expanded Layout Section (Only shown if isExpanded is true and layoutStyle is NOT "GAUGE_ONLY")
                if (isExpanded && layoutStyle != "GAUGE_ONLY") {
                    Spacer(GlanceModifier.height(10.dp))
                    Text(
                        text = "Top Consuming Apps Today:",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF8E8E93)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.height(4.dp))

                    if (top1Name.isNotBlank()) {
                        WidgetTopAppRow(name = top1Name, bytes = top1Bytes)
                    }
                    if (top2Name.isNotBlank()) {
                        WidgetTopAppRow(name = top2Name, bytes = top2Bytes)
                    }
                    if (top3Name.isNotBlank()) {
                        WidgetTopAppRow(name = top3Name, bytes = top3Bytes)
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetTopAppRow(name: String, bytes: Long) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "• $name",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = DataUsageManager.formatBytes(bytes),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF359AFF)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        DataWidgetWorker.enqueueImmediate(context)
    }
}
