package com.example.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.*
import com.example.R
import com.example.data.UnitPreference
import com.example.ui.components.UsageGauge
import com.example.ui.theme.DownloadColor
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TetheringColor
import com.example.ui.theme.UploadColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: History, 2: Settings

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionsAndLoad()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.checkPermissionsAndLoad()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Data Tracker Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when (selectedTab) {
                                0 -> "Data Overview"
                                1 -> "Usage History"
                                else -> "Configuration"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Overview") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> OverviewTabContent(
                    viewModel = viewModel,
                    onGrantAccess = {
                        try {
                            usageAccessLauncher.launch(viewModel.getUsageAccessSettingsIntent())
                        } catch (_: Exception) {
                            usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                )
                1 -> HistoryScreen(viewModel = viewModel)
                2 -> SettingsScreen(viewModel = viewModel)
            }

            // Deep App Detail Bottom Sheet
            uiState.selectedApp?.let { selectedApp ->
                AppDetailBottomSheet(
                    app = selectedApp,
                    hourlyUsage = uiState.selectedAppHourlyUsage,
                    isBits = uiState.unitPreference == UnitPreference.BITS_BYTES,
                    onDismiss = { viewModel.selectAppForDetail(null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewTabContent(
    viewModel: MainViewModel,
    onGrantAccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isBits = uiState.unitPreference == UnitPreference.BITS_BYTES

    val maxAppUsageBytes = remember(uiState.filteredAppUsageList) {
        uiState.filteredAppUsageList.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Permission Card ---
        if (!uiState.hasUsageAccess) {
            item {
                PermissionCard(
                    title = "Usage Access Required",
                    description = "To track daily mobile data and individual app usage, grant PACKAGE_USAGE_STATS permission.",
                    buttonText = "Grant Usage Permission",
                    onClick = onGrantAccess
                )
            }
        }

        // --- Network & Period Selector Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Mobile vs Wi-Fi Selector
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.selectedNetworkType == ConnectivityManager.TYPE_MOBILE,
                            onClick = { viewModel.setNetworkType(ConnectivityManager.TYPE_MOBILE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = uiState.selectedNetworkType == ConnectivityManager.TYPE_MOBILE)
                            }
                        ) {
                            Text(
                                text = "Mobile",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        SegmentedButton(
                            selected = uiState.selectedNetworkType == ConnectivityManager.TYPE_WIFI,
                            onClick = { viewModel.setNetworkType(ConnectivityManager.TYPE_WIFI) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = uiState.selectedNetworkType == ConnectivityManager.TYPE_WIFI)
                            }
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

                    // Time Period Filter Tabs: Today, This Week, This Month, Custom
                    ScrollableTabRow(
                        selectedTabIndex = uiState.selectedPeriod.ordinal,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        TimePeriodFilter.entries.forEach { period ->
                            Tab(
                                selected = uiState.selectedPeriod == period,
                                onClick = { viewModel.setTimePeriodFilter(period) },
                                text = {
                                    Text(
                                        when (period) {
                                            TimePeriodFilter.TODAY -> "Today"
                                            TimePeriodFilter.THIS_WEEK -> "This Week"
                                            TimePeriodFilter.THIS_MONTH -> "This Month"
                                            TimePeriodFilter.CUSTOM_RANGE -> "Custom"
                                        },
                                        fontWeight = if (uiState.selectedPeriod == period) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Usage Gauge Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    UsageGauge(
                        usedBytes = uiState.dataUsage.totalBytes,
                        limitBytes = uiState.activeLimit,
                        isBits = isBits,
                        rolloverMessage = uiState.rolloverMessage
                    )
                }
            }
        }

        // --- Smart Forecast Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Smart Forecast & Pace",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiState.paceEndString,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = uiState.remainingTodayString,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // --- Download / Upload Detailed Cards ---
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Download",
                    value = DataUsageManager.formatBytes(uiState.dataUsage.rxBytes, isBits),
                    icon = Icons.Default.ArrowDownward,
                    color = DownloadColor
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Upload",
                    value = DataUsageManager.formatBytes(uiState.dataUsage.txBytes, isBits),
                    icon = Icons.Default.ArrowUpward,
                    color = UploadColor
                )
            }
        }

        // --- Hotspot Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = TetheringColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Hotspot / Tethering", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            DataUsageManager.formatBytes(uiState.dataUsage.tetheringTotalBytes, isBits),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // --- Per-App Consumption Section Header & Search/Sort Bar ---
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Per-App Consumption",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // CSV Export Button
                    IconButton(onClick = {
                        val csv = viewModel.exportCsvData()
                        Toast.makeText(context, "Exported 30-day logs (${csv.lines().size} rows)", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = uiState.appSearchQuery,
                    onValueChange = { viewModel.setAppSearchQuery(it) },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.appSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setAppSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                // Sort Options
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sort by:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(
                        selected = uiState.appSortOption == AppSortOption.TOTAL_DATA,
                        onClick = { viewModel.setAppSortOption(AppSortOption.TOTAL_DATA) },
                        label = { Text("Total Data") }
                    )
                    FilterChip(
                        selected = uiState.appSortOption == AppSortOption.BACKGROUND_DATA,
                        onClick = { viewModel.setAppSortOption(AppSortOption.BACKGROUND_DATA) },
                        label = { Text("Background") }
                    )
                    FilterChip(
                        selected = uiState.appSortOption == AppSortOption.APP_NAME,
                        onClick = { viewModel.setAppSortOption(AppSortOption.APP_NAME) },
                        label = { Text("Name") }
                    )
                }
            }
        }

        // --- App Usage List Items ---
        items(uiState.filteredAppUsageList) { app ->
            AppUsageItemCard(
                app = app,
                maxUsageBytes = maxAppUsageBytes,
                isBits = isBits,
                onClick = { viewModel.selectAppForDetail(app) }
            )
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AppUsageItemCard(
    app: AppUsageInfo,
    maxUsageBytes: Long,
    isBits: Boolean = false,
    onClick: () -> Unit
) {
    val totalBytes = app.totalBytes.coerceAtLeast(0L)
    val progressRatio = (totalBytes.toDouble() / maxUsageBytes.toDouble().coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val bgRatio = app.backgroundBytes.toDouble() / totalBytes.toDouble().coerceAtLeast(1.0)
    val isHighBgUsage = bgRatio > 0.20

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )

                        if (isHighBgUsage) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF3B30).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "High BG",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF3B30),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        "FG: ${DataUsageManager.formatBytes(app.foregroundBytes, isBits)}  •  BG: ${DataUsageManager.formatBytes(app.backgroundBytes, isBits)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        DataUsageManager.formatBytes(app.totalBytes, isBits),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun OneUICard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    OneUICard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    OneUICard {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}
