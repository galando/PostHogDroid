package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.PostHogViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationsInboxScreen(viewModel: PostHogViewModel) {
    val logs by viewModel.notifications.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "Alert History", style = MaterialTheme.typography.bodyMedium, color = PostHogOrange, fontWeight = FontWeight.Bold)
                Text(text = "Notifications Logs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }
            Row {
                IconButton(onClick = { viewModel.markNotificationsAsRead() }, modifier = Modifier.testTag("read_all_notifications_btn")) {
                    Icon(imageVector = Icons.Default.MarkChatRead, contentDescription = "Mark All Read", tint = PostHogOrange)
                }
                IconButton(onClick = { viewModel.clearNotifications() }, modifier = Modifier.testTag("clear_all_notifications_btn")) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear All Logs", tint = PostHogRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.MailOutline, contentDescription = "Inbox Empty", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "History logs are empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Any triggered threshold breaches are displayed here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    val colorAccent = if (log.type == "CRITICAL") PostHogRed else PostHogAmber
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, if (log.isRead) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else colorAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .testTag("log_card_${log.id}"),
                        colors = CardDefaults.cardColors(containerColor = if (log.isRead) MaterialTheme.colorScheme.surface else colorAccent.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = if (log.type == "CRITICAL") "CRITICAL" else "WARNING", fontSize = 12.sp, color = colorAccent, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = log.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (log.isRead) FontWeight.Bold else FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = log.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
