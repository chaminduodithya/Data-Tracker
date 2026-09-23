package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.AppUsageInfo
import com.example.DataUsageManager
import com.example.ui.theme.DownloadColor
import com.example.ui.theme.UploadColor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailBottomSheet(
    app: AppUsageInfo,
    hourlyUsage: List<Pair<Int, Long>>, // List of 24 hours (0..23 -> bytes)
    isBits: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fgBytes = app.foregroundBytes
    val bgBytes = app.backgroundBytes
    val totalBytes = app.totalBytes.coerceAtLeast(1L)

    val bgPercent = ((bgBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
    val fgPercent = (100 - bgPercent).coerceIn(0, 100)
    val isHighBgUsage = (bgBytes.toDouble() / totalBytes.toDouble()) > 0.20

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon & Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (app.icon != null) {
                    val bitmap = remember(app.icon) { app.icon.toBitmap().asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isHighBgUsage) {
                    Surface(
                        color = Color(0xFFFF3B30).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                ">20% BG",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFF3B30),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Total Consumed Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Total Consumption",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            DataUsageManager.formatBytes(app.totalBytes, isBits),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Icon(
                        Icons.Default.DataUsage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // High BG Alert Banner
            if (isHighBgUsage) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PriorityHigh,
                            contentDescription = null,
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "High background usage flag! This app consumed $bgPercent% of its total data while running in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Foreground vs Background Split
            Text(
                "Foreground vs. Background Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((fgPercent.coerceAtLeast(1)).toFloat())
                        .background(DownloadColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((bgPercent.coerceAtLeast(1)).toFloat())
                        .background(UploadColor)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(DownloadColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Foreground", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("${DataUsageManager.formatBytes(fgBytes, isBits)} ($fgPercent%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(UploadColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Background", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("${DataUsageManager.formatBytes(bgBytes, isBits)} ($bgPercent%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Hourly Usage Canvas Timeline
            Text(
                "Hourly Usage Timeline (Today)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            HourlyAppCanvasChart(hourlyUsage = hourlyUsage, isBits = isBits)

            Spacer(Modifier.height(28.dp))

            // Manage Background Data System Button
            Button(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Background Data Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HourlyAppCanvasChart(
    hourlyUsage: List<Pair<Int, Long>>,
    isBits: Boolean = false
) {
    val maxBytes = remember(hourlyUsage) {
        hourlyUsage.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    }

    var selectedHourIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (selectedHourIndex in hourlyUsage.indices) {
            val (hour, bytes) = hourlyUsage[selectedHourIndex]
            val timeLabel = String.format(Locale.US, "%02d:00 - %02d:59", hour, hour)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(timeLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(DataUsageManager.formatBytes(bytes, isBits), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            val activeBarColor = MaterialTheme.colorScheme.primary
            val dimBarColor = activeBarColor.copy(alpha = 0.35f)
            val selectedBarColor = Color(0xFFFF9500)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hourlyUsage) {
                        detectTapGestures { offset ->
                            val barWidth = size.width / 24f
                            val tappedIndex = (offset.x / barWidth).toInt().coerceIn(0, 23)
                            selectedHourIndex = tappedIndex
                        }
                    }
            ) {
                val barWidthPx = size.width / 24f
                val spacingPx = 2.dp.toPx()
                val actualBarWidth = (barWidthPx - spacingPx).coerceAtLeast(2f)

                for (i in 0..23) {
                    val bytes = hourlyUsage.getOrNull(i)?.second ?: 0L
                    val heightRatio = (bytes.toDouble() / maxBytes.toDouble()).toFloat().coerceIn(0.02f, 1.0f)
                    val barHeight = size.height * heightRatio
                    val x = i * barWidthPx + spacingPx / 2
                    val y = size.height - barHeight

                    val color = when {
                        i == selectedHourIndex -> selectedBarColor
                        bytes > 0 -> activeBarColor
                        else -> dimBarColor
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(actualBarWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("06:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("18:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("23:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
