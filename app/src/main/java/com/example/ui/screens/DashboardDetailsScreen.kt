package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.InsightEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.PostHogViewModel

@Composable
fun DashboardDetailsScreen(
    viewModel: PostHogViewModel,
    dashboardId: Int,
    dashboardName: String,
    onBack: () -> Unit
) {
    val allInsights by viewModel.insights.collectAsStateWithLifecycle()
    val trendFilter = remember { mutableStateOf("All") }
    val sortBy = remember { mutableStateOf("NameAsc") }

    val filteredInsights = remember(allInsights, dashboardId, trendFilter.value, sortBy.value) {
        val baseList = allInsights.filter { it.dashboardId == dashboardId }
            .filter { trendFilter.value == "All" || it.trendDirection == trendFilter.value }

        when (sortBy.value) {
            "NameAsc" -> baseList.sortedBy { it.name }
            "NameDesc" -> baseList.sortedByDescending { it.name }
            "Trend" -> baseList.sortedBy {
                when (it.trendDirection) {
                    "UP" -> 0
                    "DOWN" -> 1
                    else -> 2
                }
            }
            else -> baseList
        }
    }
    val selectedInsightForFullView = remember { mutableStateOf<InsightEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    .testTag("dashboard_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dashboard Metrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = PostHogOrange,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dashboardName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
            IconButton(
                onClick = { viewModel.syncNow() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PostHogOrangeLight)
                    .testTag("dashboard_detail_sync_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = PostHogOrange)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Manual Refresh", tint = PostHogOrange)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trend:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            listOf("All" to "All", "UP" to "Up", "DOWN" to "Down").forEach { (key, display) ->
                FilterChip(
                    selected = trendFilter.value == key,
                    onClick = { trendFilter.value = key },
                    label = { Text(display, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PostHogOrange,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedCard(
                    onClick = { sortMenuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(12.dp))
                        Text(
                            text = when (sortBy.value) {
                                "NameAsc" -> "Name: A-Z"
                                "NameDesc" -> "Name: Z-A"
                                else -> "Best Trend"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Name (A to Z)") },
                        onClick = { sortBy.value = "NameAsc"; sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (Z to A)") },
                        onClick = { sortBy.value = "NameDesc"; sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Best Trends First") },
                        onClick = { sortBy.value = "Trend"; sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredInsights.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No insights match filters in this dashboard",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(filteredInsights, key = { it.id }) { insight ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedInsightForFullView.value = insight }
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .testTag("insight_card_${insight.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(0.7f)) {
                                    Text(
                                        text = insight.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    insight.description?.let { desc ->
                                        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = insight.lastValueString, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = PostHogOrange)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val trendColor = when (insight.trendDirection) { "UP" -> PostHogGreen; "DOWN" -> PostHogRed; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                                        val trendIcon = when (insight.trendDirection) { "UP" -> Icons.Default.TrendingUp; "DOWN" -> Icons.Default.TrendingDown; else -> Icons.Default.TrendingFlat }
                                        Icon(imageVector = trendIcon, contentDescription = "Trend direction", tint = trendColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = insight.trendDirection, style = MaterialTheme.typography.labelSmall, color = trendColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val seriesList = remember(insight.dataJson) { parseDataJson(insight.dataJson) }
                            val labelList = remember(insight.labelsJson) { if (insight.labelsJson.isBlank()) emptyList() else insight.labelsJson.split(",") }

                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                val displayType = insight.displayType
                                if (displayType == "ActionsBarValue" || displayType == "ActionsBar") {
                                    MetricMultiBarChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                                } else if (displayType == "ActionsLineGraph" || displayType == "ActionsLineGraphCumulative") {
                                    MetricMultiLineChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                                } else if (displayType == "ActionsPie" || displayType == "ActionsPieChart") {
                                    MetricPieChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                                } else if (displayType == "ActionsTable") {
                                    MetricTableChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                                } else {
                                    val rates = remember(seriesList) { seriesList.firstOrNull()?.data ?: emptyList() }
                                    MetricFunnelChart(stages = labelList, rates = rates, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedInsightForFullView.value?.let { selected ->
            InsightFullViewDialog(insight = selected, onDismiss = { selectedInsightForFullView.value = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightFullViewDialog(insight: InsightEntity, onDismiss: () -> Unit) {
    val seriesList = remember(insight.dataJson) { parseDataJson(insight.dataJson) }
    val labelList = remember(insight.labelsJson) { if (insight.labelsJson.isBlank()) emptyList() else insight.labelsJson.split(",") }
    val interactiveDisplayType = remember { mutableStateOf(insight.displayType) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Metric Deep Dive", style = MaterialTheme.typography.labelSmall, color = PostHogOrange, fontWeight = FontWeight.Bold)
                        Text(text = insight.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                            Text(text = "Current State Value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = insight.lastValueString, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = PostHogOrange)
                            insight.description?.let { desc -> Spacer(modifier = Modifier.height(8.dp)); Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }

                    item {
                        Column {
                            Text(text = "Projection Format Type:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ActionsLineGraph" to "Line", "ActionsBarValue" to "Bar", "ActionsPie" to "Pie", "ActionsTable" to "Table").forEach { (type, label) ->
                                    val isSelected = interactiveDisplayType.value.contains(type) ||
                                        (type == "ActionsLineGraph" && interactiveDisplayType.value.contains("Line")) ||
                                        (type == "ActionsBarValue" && interactiveDisplayType.value.contains("Bar")) ||
                                        (type == "ActionsPie" && interactiveDisplayType.value.contains("Pie"))
                                    FilterChip(selected = isSelected, onClick = { interactiveDisplayType.value = type }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PostHogOrange, selectedLabelColor = Color.White))
                                }
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(320.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                            val activeType = interactiveDisplayType.value
                            if (activeType.contains("Bar") || activeType == "ActionsBarValue" || activeType == "ActionsBar") MetricMultiBarChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                            else if (activeType.contains("Line") || activeType == "ActionsLineGraph" || activeType == "ActionsLineGraphCumulative") MetricMultiLineChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                            else if (activeType.contains("Pie") || activeType == "ActionsPie" || activeType == "ActionsPieChart") MetricPieChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                            else if (activeType == "ActionsTable") MetricTableChart(seriesList = seriesList, labels = labelList, modifier = Modifier.fillMaxSize())
                            else { val rates = remember(seriesList) { seriesList.firstOrNull()?.data ?: emptyList() }; MetricFunnelChart(stages = labelList, rates = rates, modifier = Modifier.fillMaxSize()) }
                        }
                    }

                    item {
                        Column {
                            Text(text = "Raw Dataset Coordinates Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Date Interval", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text("Value Metric", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    if (labelList.isEmpty()) { Text("No coordinate details found.", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
                                    else {
                                        labelList.forEachIndexed { idx, label ->
                                            val valueString = if (seriesList.size == 1) {
                                                seriesList.first().data.getOrNull(idx)?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "No Data"
                                            } else {
                                                val vals = seriesList.mapNotNull { it.data.getOrNull(idx) }
                                                if (vals.isNotEmpty()) { val sum = vals.sum(); if (sum % 1.0 == 0.0) sum.toInt().toString() else String.format("%.1f", sum) } else "No Data"
                                            }
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(label, style = MaterialTheme.typography.bodySmall)
                                                Text(valueString, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PostHogOrange)
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
