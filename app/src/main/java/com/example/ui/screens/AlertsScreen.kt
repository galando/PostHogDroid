package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.PostHogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(viewModel: PostHogViewModel) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Automated Trackers", style = MaterialTheme.typography.bodyMedium, color = HogPurple, fontWeight = FontWeight.Bold)
        Text(text = "Metric Thresholds", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Configure on-device limit alarms for each metric. Rules are evaluated locally — no backend required. Two modes: fixed threshold or % change from last data point.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp)
        )

        if (insights.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(imageVector = Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "No PostHog Metrics Loaded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Connect a live PostHog dashboard or switch to Demo Mode in Settings to load metrics.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(insights, key = { it.id }) { insight ->
                    val matchingAlert = alerts.find { it.insightId == insight.id }
                    val isEnabled = matchingAlert != null && matchingAlert.isActive
                    val isTriggered = matchingAlert != null && matchingAlert.isActive && matchingAlert.isTriggered
                    val isPctMode = matchingAlert?.alertType == "PCT_CHANGE"

                    val parsedCurrentVal = remember(insight.lastValueString) {
                        insight.lastValueString.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                    }
                    val initialThresholdText = remember(matchingAlert) {
                        if (matchingAlert != null) matchingAlert.threshold.toString()
                        else (Math.round((if (parsedCurrentVal > 0) parsedCurrentVal * 1.1 else 100.0) * 10.0) / 10.0).toString()
                    }
                    val initialPctText = remember(matchingAlert) {
                        matchingAlert?.pctChangeThreshold?.toString() ?: "20.0"
                    }

                    var thresholdInputString by remember(insight.id, initialThresholdText) { mutableStateOf(initialThresholdText) }
                    var pctInputString by remember(insight.id, initialPctText) { mutableStateOf(initialPctText) }

                    val cardBorderColor = if (isTriggered) HogRed else if (isEnabled) HogGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    val cardBg = if (isTriggered) HogRed.copy(alpha = 0.04f) else if (isEnabled) HogGreen.copy(alpha = 0.02f) else MaterialTheme.colorScheme.surface

                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, cardBorderColor, RoundedCornerShape(16.dp)).testTag("metric_alert_card_${insight.id}"),
                        colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Title row + toggle
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(0.7f)) {
                                    Text(text = insight.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = "Current: ${insight.lastValueString}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = HogPurple)
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        val threshVal = thresholdInputString.toDoubleOrNull() ?: (if (parsedCurrentVal > 0) parsedCurrentVal * 1.1 else 100.0)
                                        viewModel.saveAlertThreshold(insightId = insight.id, insightName = insight.name, threshold = threshVal, isActive = checked)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = HogPurple),
                                    modifier = Modifier.testTag("alert_toggle_${insight.id}")
                                )
                            }

                            if (isEnabled && matchingAlert != null) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                // Alert mode selector: Threshold vs % Change
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Mode:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FilterChip(
                                        selected = !isPctMode,
                                        onClick = { viewModel.setAlertType(matchingAlert.id, "THRESHOLD", matchingAlert.pctChangeThreshold) },
                                        label = { Text("Threshold", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HogPurple, selectedLabelColor = Color.White),
                                        modifier = Modifier.height(26.dp)
                                    )
                                    FilterChip(
                                        selected = isPctMode,
                                        onClick = { viewModel.setAlertType(matchingAlert.id, "PCT_CHANGE", pctInputString.toDoubleOrNull() ?: 20.0) },
                                        label = { Text("% Change", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HogMagenta, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    // Input field
                                    Column(modifier = Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (isPctMode) {
                                            Text(text = "Trigger when change ≥", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            OutlinedTextField(
                                                value = pctInputString,
                                                onValueChange = { input ->
                                                    pctInputString = input
                                                    val pct = input.toDoubleOrNull()
                                                    if (pct != null && pct > 0.0) {
                                                        viewModel.setAlertType(matchingAlert.id, "PCT_CHANGE", pct)
                                                    }
                                                },
                                                label = { Text("% change") },
                                                suffix = { Text("%") },
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HogMagenta, unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), focusedLabelColor = HogMagenta),
                                                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pct_input_${insight.id}"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                                            )
                                        } else {
                                            Text(text = "Trigger Above Threshold", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            OutlinedTextField(
                                                value = thresholdInputString,
                                                onValueChange = { input ->
                                                    thresholdInputString = input
                                                    val v = input.toDoubleOrNull()
                                                    if (v != null && v >= 0.0) {
                                                        viewModel.saveAlertThreshold(insightId = insight.id, insightName = insight.name, threshold = v, isActive = true)
                                                    }
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HogPurple, unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), focusedLabelColor = HogPurple),
                                                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("threshold_input_${insight.id}"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Status + actions
                                    Column(modifier = Modifier.weight(0.45f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(
                                            if (matchingAlert.isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                                            else if (isTriggered) HogRed.copy(alpha = 0.12f)
                                            else HogGreen.copy(alpha = 0.12f)
                                        ).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                text = if (matchingAlert.isMuted) "MUTED" else if (isTriggered) "BREACHED" else "OK",
                                                color = if (matchingAlert.isMuted) MaterialTheme.colorScheme.onSurfaceVariant else if (isTriggered) HogRed else HogGreen,
                                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { viewModel.triggerAlertSimulation(matchingAlert.id) }, colors = ButtonDefaults.textButtonColors(contentColor = HogPurple), modifier = Modifier.height(32.dp).padding(horizontal = 4.dp).testTag("test_alert_btn_${insight.id}")) {
                                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                            }
                                            IconButton(onClick = { viewModel.toggleMuteAlert(matchingAlert.id) }, modifier = Modifier.size(32.dp).testTag("mute_alert_btn_${insight.id}")) {
                                                Icon(imageVector = if (matchingAlert.isMuted) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive, contentDescription = "Mute", tint = if (matchingAlert.isMuted) MaterialTheme.colorScheme.onSurfaceVariant else HogPurple, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
