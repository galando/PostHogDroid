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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = "Automated Trackers", style = MaterialTheme.typography.bodyMedium, color = PostHogOrange, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Metric Thresholds", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = "Configure direct on-device limit alarms for each metric. Since PostHog keys are read-only, our app processes these rules locally in system RAM—evaluating metrics in the background and firing direct native notification alerts when a limit is exceeded. 100% on-device, no database mutations.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp)
        )

        if (insights.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(imageVector = Icons.Default.CloudQueue, contentDescription = "No insights", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "No PostHog Metrics Loaded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Connect a live PostHog dashboard or switch to Demo Mode in 'Settings' to automatically populate your active metrics.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(insights, key = { it.id }) { insight ->
                    val matchingAlert = alerts.find { it.insightId == insight.id }
                    val isEnabled = matchingAlert != null && matchingAlert.isActive
                    val isTriggered = matchingAlert != null && matchingAlert.isActive && matchingAlert.isTriggered

                    val parsedCurrentVal = remember(insight.lastValueString) {
                        insight.lastValueString.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                    }

                    val initialThresholdText = remember(matchingAlert) {
                        if (matchingAlert != null) matchingAlert.threshold.toString()
                        else { val defaultVal = if (parsedCurrentVal > 0) parsedCurrentVal * 1.1 else 100.0; (Math.round(defaultVal * 10.0) / 10.0).toString() }
                    }

                    var thresholdInputString by remember(insight.id, initialThresholdText) { mutableStateOf(initialThresholdText) }

                    val cardBorderColor = if (isTriggered) PostHogRed else if (isEnabled) PostHogGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    val cardBg = if (isTriggered) PostHogRed.copy(alpha = 0.04f) else if (isEnabled) PostHogGreen.copy(alpha = 0.02f) else MaterialTheme.colorScheme.surface

                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, cardBorderColor, RoundedCornerShape(16.dp)).testTag("metric_alert_card_${insight.id}"),
                        colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(0.7f)) {
                                    Text(text = insight.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = "Current Value: ${insight.lastValueString}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = PostHogOrange)
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        val threshVal = thresholdInputString.toDoubleOrNull() ?: (if (parsedCurrentVal > 0) parsedCurrentVal * 1.1 else 100.0)
                                        viewModel.saveAlertThreshold(insightId = insight.id, insightName = insight.name, threshold = threshVal, isActive = checked)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PostHogOrange),
                                    modifier = Modifier.testTag("alert_toggle_${insight.id}")
                                )
                            }

                            if (isEnabled && matchingAlert != null) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = "Trigger Above Threshold", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        OutlinedTextField(
                                            value = thresholdInputString,
                                            onValueChange = { input ->
                                                thresholdInputString = input
                                                val parsedDouble = input.toDoubleOrNull()
                                                if (parsedDouble != null && parsedDouble >= 0.0) {
                                                    viewModel.saveAlertThreshold(insightId = insight.id, insightName = insight.name, threshold = parsedDouble, isActive = true)
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PostHogOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), focusedLabelColor = PostHogOrange),
                                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("threshold_input_${insight.id}"),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(0.45f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (matchingAlert.isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f) else if (isTriggered) PostHogRed.copy(alpha = 0.12f) else PostHogGreen.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                text = if (matchingAlert.isMuted) "MUTED" else if (isTriggered) "BREACHED" else "MONITORING",
                                                color = if (matchingAlert.isMuted) MaterialTheme.colorScheme.onSurfaceVariant else if (isTriggered) PostHogRed else PostHogGreen,
                                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { viewModel.triggerAlertSimulation(matchingAlert.id) }, colors = ButtonDefaults.textButtonColors(contentColor = PostHogOrange), modifier = Modifier.height(32.dp).padding(horizontal = 4.dp).testTag("test_alert_btn_${insight.id}")) {
                                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("Test alert", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                            }
                                            IconButton(onClick = { viewModel.toggleMuteAlert(matchingAlert.id) }, modifier = Modifier.size(32.dp).testTag("mute_alert_btn_${insight.id}")) {
                                                Icon(imageVector = if (matchingAlert.isMuted) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive, contentDescription = "Mute notifications", tint = if (matchingAlert.isMuted) MaterialTheme.colorScheme.onSurfaceVariant else PostHogOrange, modifier = Modifier.size(18.dp))
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
