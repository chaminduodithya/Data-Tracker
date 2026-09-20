package com.example

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class ChartDataPoint(
    val label: String,
    val value: Long
)

@Composable
fun AnalyticsChart(
    dataPoints: List<ChartDataPoint>,
    limitBytes: Long,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) {
        animationProgress.animateTo(1f, animationSpec = tween(1000))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Usage Trends",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (dataPoints.isEmpty()) return@Canvas

                    val barWidth = 24.dp.toPx()
                    val spaceBetween = (size.width - (barWidth * dataPoints.size)) / (dataPoints.size + 1)
                    val maxVal = (dataPoints.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(limitBytes / 2)

                    dataPoints.forEachIndexed { index, point ->
                        val x = spaceBetween + index * (barWidth + spaceBetween)
                        val barHeight = (point.value.toFloat() / maxVal.toFloat() * size.height) * animationProgress.value

                        // Bar background track
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.1f),
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(barWidth / 2)
                        )

                        // Actual usage bar
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.8f),
                            topLeft = Offset(x, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                dataPoints.forEach { point ->
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
