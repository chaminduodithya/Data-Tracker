package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DataUsageManager

// Colors based on One UI specs
val OneUIBlueGauge = Color(0xFF2C6BED)
val WarningRedGauge = Color(0xFFFF3B30)
val WarningOrangeGauge = Color(0xFFFF9500)

@Composable
fun UsageGauge(
    usedBytes: Long,
    limitBytes: Long,
    modifier: Modifier = Modifier,
    isBits: Boolean = false
) {
    val percentageRatio = if (limitBytes > 0) (usedBytes.toDouble() / limitBytes.toDouble()) else 0.0
    val percentageInt = (percentageRatio * 100).toInt()
    val isOverLimit = percentageRatio > 1.0
    val isNearLimit = percentageRatio >= 0.8 && !isOverLimit

    // Gauge arc color shift: Warning Red if >100%, One UI Blue if normal
    val activeColor = when {
        isOverLimit -> WarningRedGauge
        isNearLimit -> WarningOrangeGauge
        else -> OneUIBlueGauge
    }

    val progressSweepRatio = percentageRatio.coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progressSweepRatio,
        animationSpec = tween(durationMillis = 1000),
        label = "gauge_progress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1.2f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 18.dp.toPx()
                val diameter = size.minDimension - strokeWidthPx
                val topLeftX = (size.width - diameter) / 2
                val topLeftY = (size.height - diameter) / 2

                // Arc geometry: 240 degrees sweep starting from 150 degrees (bottom-left)
                val startAngle = 150f
                val maxSweepAngle = 240f

                // Draw background track arc
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = maxSweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    topLeft = Offset(topLeftX, topLeftY),
                    size = Size(diameter, diameter)
                )

                // Draw active progress arc
                drawArc(
                    color = activeColor,
                    startAngle = startAngle,
                    sweepAngle = maxSweepAngle * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    topLeft = Offset(topLeftX, topLeftY),
                    size = Size(diameter, diameter)
                )
            }

            // Gauge Center Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                // Render exact percentage (e.g. "103% of limit")
                Text(
                    text = "$percentageInt% of limit",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOverLimit) WarningRedGauge else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                val formattedUsed = DataUsageManager.formatBytes(usedBytes, isBits)
                val formattedLimit = DataUsageManager.formatBytes(limitBytes, isBits)

                Text(
                    text = "$formattedUsed / $formattedLimit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Badge
        val (badgeText, badgeBgColor, badgeTextColor) = when {
            isOverLimit -> {
                val excess = usedBytes - limitBytes
                Triple(
                    "+${DataUsageManager.formatBytes(excess, isBits)} over limit",
                    WarningRedGauge.copy(alpha = 0.15f),
                    WarningRedGauge
                )
            }
            isNearLimit -> {
                val remaining = limitBytes - usedBytes
                Triple(
                    "${DataUsageManager.formatBytes(remaining, isBits)} remaining (80%+ used)",
                    WarningOrangeGauge.copy(alpha = 0.15f),
                    WarningOrangeGauge
                )
            }
            else -> {
                val remaining = (limitBytes - usedBytes).coerceAtLeast(0L)
                Triple(
                    "${DataUsageManager.formatBytes(remaining, isBits)} remaining",
                    OneUIBlueGauge.copy(alpha = 0.12f),
                    OneUIBlueGauge
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(badgeBgColor)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = badgeTextColor
            )
        }
    }
}
