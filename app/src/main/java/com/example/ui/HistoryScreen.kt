package com.example.ui

import android.net.ConnectivityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DataUsageManager
import com.example.MainViewModel
import com.example.data.UnitPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedDays by remember { mutableIntStateOf(7) } // 7 or 30 days
    var selectedNetworkType by remember { mutableIntStateOf(ConnectivityManager.TYPE_MOBILE) } // Mobile vs Wi-Fi

    LaunchedEffect(selectedDays, selectedNetworkType) {
        viewModel.loadHistory(selectedNetworkType, selectedDays)
    }

    val historyData = uiState.historyList // List<Pair<Long, Long>> (timestamp -> bytes)
    val isBits = uiState.unitPreference == UnitPreference.BITS_BYTES

    // Compute maxUsageBytes across all daily items
    val maxUsageBytes = remember(historyData) {
        historyData.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    }

    // Comparison calculations
    val todayBytes = historyData.lastOrNull()?.second ?: 0L
    val yesterdayBytes = if (historyData.size >= 2) historyData[historyData.size - 2].second else 0L
    val diffPercent = if (yesterdayBytes > 0) {
        (((todayBytes.toDouble() - yesterdayBytes.toDouble()) / yesterdayBytes.toDouble()) * 100).toInt()
    } else 0

    val totalHistoryBytes = historyData.sumOf { it.second }
    val avgDailyBytes = if (historyData.isNotEmpty()) totalHistoryBytes / historyData.size else 0L
    val peakItem = historyData.maxByOrNull { it.second }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Usage History",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // --- Controls: Network & Timeframe Filters ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Filter Network & Period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedNetworkType == ConnectivityManager.TYPE_MOBILE,
                            onClick = { selectedNetworkType = ConnectivityManager.TYPE_MOBILE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(
                                text = "Mobile Data",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        SegmentedButton(
                            selected = selectedNetworkType == ConnectivityManager.TYPE_WIFI,
                            onClick = { selectedNetworkType = ConnectivityManager.TYPE_WIFI },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(
                                text = "Wi-Fi",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedDays == 7,
                            onClick = { selectedDays = 7 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(
                                text = "Last 7 Days",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        SegmentedButton(
                            selected = selectedDays == 30,
                            onClick = { selectedDays = 30 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(
                                text = "Last 30 Days",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Comparison Highlights Banner ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Comparison vs Yesterday", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isMore = diffPercent >= 0
                                Icon(
                                    if (isMore) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isMore) Color(0xFFFF3B30) else Color(0xFF00C853),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (diffPercent == 0) "Same as yesterday" else "${Math.abs(diffPercent)}% ${if (isMore) "more" else "less"} than yesterday",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMore) Color(0xFFFF3B30) else Color(0xFF00C853)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Daily Average", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DataUsageManager.formatBytes(avgDailyBytes, isBits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        Column {
                            Text("Peak Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val peakDayName = if (peakItem != null) SimpleDateFormat("EEE (MMM d)", Locale.US).format(Date(peakItem.first)) else "--"
                            val peakBytesStr = if (peakItem != null) DataUsageManager.formatBytes(peakItem.second, isBits) else "--"
                            Text("$peakDayName: $peakBytesStr", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Interactive Canvas Bar Chart ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (selectedDays == 7) "7-Day Usage Trend" else "30-Day Usage Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    InteractiveCanvasBarChart(
                        historyData = historyData,
                        maxUsageBytes = maxUsageBytes,
                        isBits = isBits
                    )
                }
            }
        }

        // --- Daily Breakdown Header ---
        item {
            Text(
                text = "Daily Breakdown Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // --- List of Daily Items ---
        items(historyData.reversed()) { (timestamp, bytes) ->
            DailyLogItem(
                timestamp = timestamp,
                bytes = bytes,
                maxUsageBytes = maxUsageBytes,
                isBits = isBits
            )
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun InteractiveCanvasBarChart(
    historyData: List<Pair<Long, Long>>,
    maxUsageBytes: Long,
    isBits: Boolean = false
) {
    if (historyData.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No history logs available yet", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Selected Bar Tooltip Banner
        if (selectedIndex in historyData.indices) {
            val (ts, bytes) = historyData[selectedIndex]
            val dateStr = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US).format(Date(ts))
            val usageStr = DataUsageManager.formatBytes(bytes, isBits)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dateStr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(usageStr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            val activeColor = MaterialTheme.colorScheme.primary
            val dimColor = activeColor.copy(alpha = 0.35f)
            val highlightColor = Color(0xFFFF9500)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(historyData) {
                        detectTapGestures { offset ->
                            val totalCount = historyData.size
                            val barWidth = size.width / totalCount
                            val tapped = (offset.x / barWidth).toInt().coerceIn(0, totalCount - 1)
                            selectedIndex = tapped
                        }
                    }
            ) {
                val count = historyData.size
                val barWidthPx = size.width / count.toFloat()
                val spacingPx = (if (count <= 7) 8.dp else 2.dp).toPx()
                val actualBarWidth = (barWidthPx - spacingPx).coerceAtLeast(2f)

                for (i in 0 until count) {
                    val bytes = historyData[i].second
                    val heightRatio = (bytes.toDouble() / maxUsageBytes.toDouble()).toFloat().coerceIn(0.02f, 1.0f)
                    val barHeight = size.height * heightRatio
                    val x = i * barWidthPx + spacingPx / 2
                    val y = size.height - barHeight

                    val color = when {
                        i == selectedIndex -> highlightColor
                        i == count - 1 -> activeColor
                        else -> dimColor
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(actualBarWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }

        // X-Axis Day Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (historyData.size <= 7) {
                for ((ts, _) in historyData) {
                    val label = SimpleDateFormat("EEE", Locale.US).format(Date(ts))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val firstLabel = SimpleDateFormat("MMM d", Locale.US).format(Date(historyData.first().first))
                val midLabel = SimpleDateFormat("MMM d", Locale.US).format(Date(historyData[historyData.size / 2].first))
                val lastLabel = SimpleDateFormat("MMM d", Locale.US).format(Date(historyData.last().first))

                Text(firstLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(midLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lastLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DailyLogItem(
    timestamp: Long,
    bytes: Long,
    maxUsageBytes: Long,
    isBits: Boolean = false
) {
    val dateStr = SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date(timestamp))
    val ratio = (bytes.toDouble() / maxUsageBytes.toDouble().coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    DataUsageManager.formatBytes(bytes, isBits),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
