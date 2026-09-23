package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.SamsungTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {

    companion object {
        val LAYOUT_DENSITY_KEY = stringPreferencesKey("layout_density") // "FULL", "GAUGE_SPEED", "GAUGE_ONLY"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            SamsungTheme {
                WidgetConfigScreen(
                    onSave = { selectedTarget, selectedLayout ->
                        saveAndFinish(selectedTarget, selectedLayout)
                    }
                )
            }
        }
    }

    private fun saveAndFinish(target: String, layoutDensity: String) {
        lifecycleScope.launch {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val glanceManager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)

                updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                    val mutable = prefs.toMutablePreferences()
                    mutable[DataWidgetWorker.TARGET_KEY] = target
                    mutable[LAYOUT_DENSITY_KEY] = layoutDensity
                    mutable
                }

                DataTrackerWidget().update(this@WidgetConfigActivity, glanceId)
                DataWidgetWorker.enqueueImmediate(this@WidgetConfigActivity)
            }

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    onSave: (target: String, layout: String) -> Unit
) {
    var selectedTarget by remember { mutableStateOf("SIM1") } // "SIM1", "SIM2", "WIFI"
    var selectedLayout by remember { mutableStateOf("FULL") } // "FULL", "GAUGE_SPEED", "GAUGE_ONLY"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Widget Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Configure data source target and layout density for your Home Screen widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                // --- Target Source Card ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Active Data Target",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = selectedTarget == "SIM1",
                                onClick = { selectedTarget = "SIM1" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                            ) {
                                Text("SIM 1")
                            }
                            SegmentedButton(
                                selected = selectedTarget == "SIM2",
                                onClick = { selectedTarget = "SIM2" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                            ) {
                                Text("SIM 2")
                            }
                            SegmentedButton(
                                selected = selectedTarget == "WIFI",
                                onClick = { selectedTarget = "WIFI" },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                            ) {
                                Text("Wi-Fi")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // --- Layout Density Card ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Widget Density Option",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = selectedLayout == "FULL",
                                onClick = { selectedLayout = "FULL" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                            ) {
                                Text("Gauge + Apps")
                            }
                            SegmentedButton(
                                selected = selectedLayout == "GAUGE_SPEED",
                                onClick = { selectedLayout = "GAUGE_SPEED" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                            ) {
                                Text("Gauge + Speed")
                            }
                            SegmentedButton(
                                selected = selectedLayout == "GAUGE_ONLY",
                                onClick = { selectedLayout = "GAUGE_ONLY" },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                            ) {
                                Text("Gauge Only")
                            }
                        }
                    }
                }
            }

            // --- Save Button ---
            Button(
                onClick = { onSave(selectedTarget, selectedLayout) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Apply Widget Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
