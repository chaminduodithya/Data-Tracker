package com.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DataTrackerTheme
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun InsightItem(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    DataTrackerTheme {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = "Data Tracker",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Card: Today's Usage Gauge
                item {
                    val (valStr, unitStr) = DataUsageManager.formatParts(uiState.totalUsageBytes)
                    val percentUsed = (uiState.progress * 100).toInt()
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Today's Usage",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            UsageGauge(
                                progress = uiState.progress,
                                totalText = valStr,
                                unitText = unitStr,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                            
                            Text(
                                text = "$percentUsed% of ${DataUsageManager.formatBytes(uiState.planAllowanceBytes)} limit",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Mini breakdown pill
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Text(
                                        text = "↓ Rx: ${DataUsageManager.formatBytes(uiState.totalUsageBytes * 8 / 10)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "|",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    Text(
                                        text = "↑ Tx: ${DataUsageManager.formatBytes(uiState.totalUsageBytes * 2 / 10)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Hourly Activity Timeline
                item {
                    HourlyTimelineCard(buckets = uiState.hourlyTimeline)
                }

                // 3. Live Speed Meter Pill
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3ECF8E))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${DataUsageManager.formatSpeed(uiState.liveSpeed.downloadSpeedBytesPerSec)} ↓  " +
                                       "${DataUsageManager.formatSpeed(uiState.liveSpeed.uploadSpeedBytesPerSec)} ↑",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 4. SIM / Wi-Fi Segmented Selector
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NetworkFilter.entries.forEach { filter ->
                            val selected = uiState.networkFilter == filter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { viewModel.updateFilter(filter) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (filter) {
                                        NetworkFilter.CELLULAR_SIM1 -> "SIM 1"
                                        NetworkFilter.CELLULAR_SIM2 -> "SIM 2"
                                        NetworkFilter.WIFI -> "Wi-Fi"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 5. Quick Data Insights (2x2 Grid)
                item {
                    val report = uiState.unifiedReport
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Quick Data Insights",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                InsightItem(
                                    modifier = Modifier.weight(1f),
                                    label = "App Leader",
                                    value = report?.topAppName ?: "None",
                                    subValue = DataUsageManager.formatBytes(report?.topAppUsage ?: 0L)
                                )
                                InsightItem(
                                    modifier = Modifier.weight(1f),
                                    label = "Background",
                                    value = String.format(java.util.Locale.US, "%.1f%% BG", (report?.bgPercentage ?: 0f) * 100),
                                    subValue = "Used silently"
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                InsightItem(
                                    modifier = Modifier.weight(1f),
                                    label = "Status",
                                    value = if (report?.isCellular == true) "Cellular" else "Wi-Fi",
                                    subValue = report?.carrierName ?: "Network active"
                                )
                                InsightItem(
                                    modifier = Modifier.weight(1f),
                                    label = "Avg Speed",
                                    value = DataUsageManager.formatSpeed(uiState.liveSpeed.downloadSpeedBytesPerSec),
                                    subValue = "Current rate"
                                )
                            }
                        }
                    }
                }

                // 6. Per-App Usage Header
                item {
                    Text(
                        text = "App breakdown",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                    )
                }

                // 7. Per-App List
                items(uiState.appUsageList) { app ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectApp(app) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                Image(
                                    painter = rememberDrawablePainter(app.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = app.appName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "FG: ${DataUsageManager.formatBytes(app.foregroundBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(text = " • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "BG: ${DataUsageManager.formatBytes(app.backgroundBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = DataUsageManager.formatBytes(app.totalBytes),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }
            }
        }
        
        // App Details Bottom Sheet
        uiState.selectedApp?.let { app ->
            AppDetailBottomSheet(
                app = app,
                onDismiss = { viewModel.selectApp(null) }
            )
        }
    }
}
