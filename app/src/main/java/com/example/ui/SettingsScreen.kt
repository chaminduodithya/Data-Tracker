package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.data.AppThemePreference
import com.example.data.UnitPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Local states for inputs (values in GB for easy editing)
    var sim1DailyGb by remember(uiState.sim1DailyLimit) {
        mutableStateOf((uiState.sim1DailyLimit / (1024.0 * 1024.0 * 1024.0)).toString())
    }
    var sim1MonthlyGb by remember(uiState.sim1MonthlyLimit) {
        mutableStateOf((uiState.sim1MonthlyLimit / (1024.0 * 1024.0 * 1024.0)).toString())
    }
    var sim2DailyGb by remember(uiState.sim2DailyLimit) {
        mutableStateOf((uiState.sim2DailyLimit / (1024.0 * 1024.0 * 1024.0)).toString())
    }
    var sim2MonthlyGb by remember(uiState.sim2MonthlyLimit) {
        mutableStateOf((uiState.sim2MonthlyLimit / (1024.0 * 1024.0 * 1024.0)).toString())
    }
    var wifiDailyGb by remember(uiState.wifiDailyLimit) {
        mutableStateOf((uiState.wifiDailyLimit / (1024.0 * 1024.0 * 1024.0)).toString())
    }
    var wifiMonthlyGb by remember(uiState.wifiMonthlyLimit) {
        mutableStateOf((uiState.wifiMonthlyLimit / (1024.0 * 1024.0 * 1024.0)).toString())
    }

    var anchorDay by remember(uiState.anchorDay) { mutableFloatStateOf(uiState.anchorDay.toFloat()) }
    var selectedUnit by remember(uiState.unitPreference) { mutableStateOf(uiState.unitPreference) }
    var selectedTheme by remember(uiState.themePreference) { mutableStateOf(uiState.themePreference) }
    var notificationsEnabled by remember(uiState.notificationsEnabled) { mutableStateOf(uiState.notificationsEnabled) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // --- SIM 1 Limits Card ---
        item {
            SettingsCard(title = "SIM 1 Data Limits", icon = Icons.Default.SimCard) {
                LimitInputField(
                    label = "Daily Cap (GB)",
                    value = sim1DailyGb,
                    onValueChange = { sim1DailyGb = it }
                )
                Spacer(Modifier.height(12.dp))
                LimitInputField(
                    label = "Monthly Cap (GB)",
                    value = sim1MonthlyGb,
                    onValueChange = { sim1MonthlyGb = it }
                )
            }
        }

        // --- SIM 2 Limits Card ---
        item {
            SettingsCard(title = "SIM 2 Data Limits", icon = Icons.Default.SimCard) {
                LimitInputField(
                    label = "Daily Cap (GB)",
                    value = sim2DailyGb,
                    onValueChange = { sim2DailyGb = it }
                )
                Spacer(Modifier.height(12.dp))
                LimitInputField(
                    label = "Monthly Cap (GB)",
                    value = sim2MonthlyGb,
                    onValueChange = { sim2MonthlyGb = it }
                )
            }
        }

        // --- Wi-Fi Limits Card ---
        item {
            SettingsCard(title = "Wi-Fi Data Limits", icon = Icons.Default.Wifi) {
                LimitInputField(
                    label = "Daily Cap (GB)",
                    value = wifiDailyGb,
                    onValueChange = { wifiDailyGb = it }
                )
                Spacer(Modifier.height(12.dp))
                LimitInputField(
                    label = "Monthly Cap (GB)",
                    value = wifiMonthlyGb,
                    onValueChange = { wifiMonthlyGb = it }
                )
            }
        }

        // --- Billing Cycle Reset Day Card ---
        item {
            SettingsCard(title = "Billing Cycle Reset Day", icon = Icons.Default.DateRange) {
                Text(
                    text = "Monthly Reset Day: ${anchorDay.toInt()}${getDaySuffix(anchorDay.toInt())} of each month",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = anchorDay,
                    onValueChange = { anchorDay = it },
                    valueRange = 1f..31f,
                    steps = 29
                )
                Text(
                    text = "Data totals for 'This Month' will accumulate starting from day ${anchorDay.toInt()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Display Preferences Card ---
        item {
            SettingsCard(title = "Display & Units", icon = Icons.Default.Tune) {
                Text(
                    text = "Unit Display Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedUnit == UnitPreference.MB_GB,
                        onClick = { selectedUnit = UnitPreference.MB_GB },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Bytes (MB / GB)")
                    }
                    SegmentedButton(
                        selected = selectedUnit == UnitPreference.BITS_BYTES,
                        onClick = { selectedUnit = UnitPreference.BITS_BYTES },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Bits (Mb / Gb)")
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTheme == AppThemePreference.SYSTEM,
                        onClick = { selectedTheme = AppThemePreference.SYSTEM },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text("System")
                    }
                    SegmentedButton(
                        selected = selectedTheme == AppThemePreference.LIGHT,
                        onClick = { selectedTheme = AppThemePreference.LIGHT },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text("Light")
                    }
                    SegmentedButton(
                        selected = selectedTheme == AppThemePreference.DARK,
                        onClick = { selectedTheme = AppThemePreference.DARK },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("Dark")
                    }
                }
            }
        }

        // --- Notifications Card ---
        item {
            SettingsCard(title = "Notifications & Usage Alerts", icon = Icons.Default.Notifications) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Usage Alerts (80% & 100%)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Receive notifications when active limits reach 80% or 100%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }
            }
        }

        // --- Save Settings Button ---
        item {
            Button(
                onClick = {
                    val s1d = (sim1DailyGb.toDoubleOrNull() ?: 2.0) * 1024 * 1024 * 1024
                    val s1m = (sim1MonthlyGb.toDoubleOrNull() ?: 30.0) * 1024 * 1024 * 1024
                    val s2d = (sim2DailyGb.toDoubleOrNull() ?: 2.0) * 1024 * 1024 * 1024
                    val s2m = (sim2MonthlyGb.toDoubleOrNull() ?: 30.0) * 1024 * 1024 * 1024
                    val wfd = (wifiDailyGb.toDoubleOrNull() ?: 10.0) * 1024 * 1024 * 1024
                    val wfm = (wifiMonthlyGb.toDoubleOrNull() ?: 100.0) * 1024 * 1024 * 1024

                    viewModel.saveSettings(
                        sim1Daily = s1d.toLong(),
                        sim1Monthly = s1m.toLong(),
                        sim2Daily = s2d.toLong(),
                        sim2Monthly = s2m.toLong(),
                        wifiDaily = wfd.toLong(),
                        wifiMonthly = wfm.toLong(),
                        anchorDay = anchorDay.toInt(),
                        unitPref = selectedUnit,
                        themePref = selectedTheme,
                        notifications = notificationsEnabled
                    )

                    Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Configuration", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun LimitInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun getDaySuffix(day: Int): String {
    if (day in 11..13) return "th"
    return when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}
