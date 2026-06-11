package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.database.DashboardEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.PostHogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardsListScreen(
    viewModel: PostHogViewModel,
    onNavigateToDetails: (Int, String) -> Unit
) {
    val dashboards by viewModel.dashboards.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val lastSyncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
    val searchQuery = remember { mutableStateOf("") }
    val showPinnedOnly = remember { mutableStateOf(false) }
    val sortBy = remember { mutableStateOf("Pinned") }

    val filtered = remember(dashboards, searchQuery.value, showPinnedOnly.value, sortBy.value) {
        val list = dashboards.filter {
            (it.name.contains(searchQuery.value, ignoreCase = true) ||
                    (it.description?.contains(searchQuery.value, ignoreCase = true) == true)) &&
                    (!showPinnedOnly.value || it.isPinned)
        }
        when (sortBy.value) {
            "NameAsc" -> list.sortedBy { it.name }
            "NameDesc" -> list.sortedByDescending { it.name }
            else -> list.sortedWith(compareByDescending<DashboardEntity> { it.isPinned }.thenBy { it.name })
        }
    }

    // Staleness helpers
    val syncStatusColor: Color
    val syncStatusText: String
    val syncDotColor: Color
    when {
        isSyncing -> {
            syncStatusColor = HogPurple
            syncStatusText = "Syncing…"
            syncDotColor = HogPurple
        }
        lastSyncError != null -> {
            syncStatusColor = HogRed
            syncStatusText = "Sync failed"
            syncDotColor = HogRed
        }
        lastSyncAt != null -> {
            val ageMs = System.currentTimeMillis() - lastSyncAt!!
            val ageMins = ageMs / 60_000
            syncStatusText = when {
                ageMins < 1 -> "Just synced"
                ageMins < 60 -> "Synced ${ageMins}m ago"
                else -> "Synced ${ageMins / 60}h ago"
            }
            syncStatusColor = if (ageMins > 60) HogAmber else HogGreen
            syncDotColor = syncStatusColor
        }
        else -> {
            syncStatusColor = MaterialTheme.colorScheme.onSurfaceVariant
            syncStatusText = "Not synced yet"
            syncDotColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { viewModel.syncNow() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_posthog_custom_logo),
                        contentDescription = "Quillboard hedgehog logo",
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Fit
                    )
                    Column {
                        Text(text = "Quillboard", style = MaterialTheme.typography.bodyMedium, color = HogPurple, fontWeight = FontWeight.Bold)
                        Text(text = "Dashboards", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(syncDotColor))
                            Text(text = syncStatusText, style = MaterialTheme.typography.labelSmall, color = syncStatusColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                IconButton(
                    onClick = { viewModel.syncNow() },
                    modifier = Modifier.clip(CircleShape).background(HogPurpleSoft).testTag("dashboard_sync_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = HogPurple)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Manual Refresh", tint = HogPurple)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery.value,
                onValueChange = { searchQuery.value = it },
                placeholder = { Text("Search dashboards…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("dashboard_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HogPurple, cursorColor = HogPurple)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = showPinnedOnly.value,
                    onClick = { showPinnedOnly.value = !showPinnedOnly.value },
                    label = { Text("Pinned Only", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        if (showPinnedOnly.value) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                        else Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(12.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HogPurple, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                )

                Spacer(modifier = Modifier.weight(1f))

                var sortMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedCard(onClick = { sortMenuExpanded = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(12.dp))
                            Text(text = when (sortBy.value) { "NameAsc" -> "Name: A-Z"; "NameDesc" -> "Name: Z-A"; else -> "Pinned First" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Pinned First") }, onClick = { sortBy.value = "Pinned"; sortMenuExpanded = false }, leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp)) })
                        DropdownMenuItem(text = { Text("Name (A to Z)") }, onClick = { sortBy.value = "NameAsc"; sortMenuExpanded = false }, leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp)) })
                        DropdownMenuItem(text = { Text("Name (Z to A)") }, onClick = { sortBy.value = "NameDesc"; sortMenuExpanded = false }, leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp)) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(imageVector = Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = if (searchQuery.value.isNotEmpty()) "No results matching keyword" else "No Cached Dashboards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = if (searchQuery.value.isNotEmpty()) "Try retyping your search" else "Tap Reload above or check Settings credentials", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered, key = { it.id }) { dashboard ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToDetails(dashboard.id, dashboard.name) }
                                .border(1.dp, if (dashboard.isPinned) HogPurple.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .testTag("dashboard_card_${dashboard.id}"),
                            colors = CardDefaults.cardColors(containerColor = if (dashboard.isPinned) HogPurpleSoft.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (dashboard.isPinned) {
                                            Icon(imageVector = Icons.Default.PushPin, contentDescription = null, tint = HogPurple, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                                        }
                                        Text(text = dashboard.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    dashboard.description?.let { desc ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "Tap to view insights", style = MaterialTheme.typography.labelSmall, color = HogPurple, fontWeight = FontWeight.Medium)
                                }
                                IconButton(onClick = { viewModel.toggleDashboardPin(dashboard.id) }, modifier = Modifier.testTag("pin_button_${dashboard.id}")) {
                                    Icon(imageVector = if (dashboard.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, contentDescription = if (dashboard.isPinned) "Unpin" else "Pin", tint = if (dashboard.isPinned) HogPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
