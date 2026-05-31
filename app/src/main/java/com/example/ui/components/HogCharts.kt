package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Representation of rich multi-series dashboard charts in PostHog
data class ChartSeries(
    val name: String,
    val data: List<Double>
)

// Helper parser to support both legacy metrics, mock data and real multi-series payload formats smoothly
fun parseDataJson(dataJson: String): List<ChartSeries> {
    if (dataJson.isBlank()) return emptyList()
    
    // Check if payload contains multi-series pattern (separated by | or containing :)
    if (dataJson.contains("|") || dataJson.contains(":")) {
        return dataJson.split("|").mapNotNull { part ->
            val colonIndex = part.indexOf(":")
            if (colonIndex != -1) {
                val name = part.substring(0, colonIndex)
                val valsStr = part.substring(colonIndex + 1)
                val vals = valsStr.split(",").mapNotNull { it.toDoubleOrNull() }
                ChartSeries(name, vals)
            } else {
                val vals = part.split(",").mapNotNull { it.toDoubleOrNull() }
                ChartSeries("Metric", vals)
            }
        }.filter { it.data.isNotEmpty() }
    } else {
        // Simple comma separated list representing single series
        val vals = dataJson.split(",").mapNotNull { it.toDoubleOrNull() }
        return listOf(ChartSeries("Metric", vals))
    }
}

// PostHog Theme Color Generator
fun getSeriesColor(index: Int): Color {
    val palette = listOf(
        PostHogOrange,
        ChartBlue,
        ChartTeal,
        ChartPurple,
        ChartPink,
        ChartIndigo,
        PostHogGreen,
        PostHogAmber
    )
    return palette[index % palette.size]
}

@Composable
fun InteractiveLegendRow(
    seriesList: List<ChartSeries>,
    visibleSeries: Map<String, Boolean>,
    onToggleSeries: (String) -> Unit
) {
    if (seriesList.size <= 1 && seriesList.firstOrNull()?.name == "Metric") return
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        seriesList.forEachIndexed { idx, series ->
            val color = getSeriesColor(idx)
            val isVisible = visibleSeries[series.name] ?: true
            
            Surface(
                onClick = { onToggleSeries(series.name) },
                shape = RoundedCornerShape(20.dp),
                color = if (isVisible) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    1.dp, 
                    if (isVisible) color.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier.padding(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isVisible) color else Color.Gray.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = series.name,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MetricBarChart(
    dataPoints: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    // Retain legacy compatibility signature by embedding dummy series structure
    MetricMultiBarChart(
        seriesList = listOf(ChartSeries("Metric", dataPoints)),
        labels = labels,
        modifier = modifier
    )
}

@Composable
fun MetricMultiBarChart(
    seriesList: List<ChartSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (seriesList.isEmpty() || labels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No Chart Data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var visibleSeries = remember(seriesList) {
        mutableStateMapOf<String, Boolean>().apply {
            seriesList.forEach { put(it.name, true) }
        }
    }

    val activeSeries = seriesList.filter { visibleSeries[it.name] ?: true }
    
    Column(modifier = modifier.fillMaxSize()) {
        InteractiveLegendRow(
            seriesList = seriesList,
            visibleSeries = visibleSeries,
            onToggleSeries = { name -> visibleSeries[name] = !(visibleSeries[name] ?: true) }
        )

        if (activeSeries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Select a metric above to display",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return
        }

        // Parse maximum value for grid heights
        val maxVal = activeSeries.flatMap { it.data }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            labels.forEachIndexed { labelIdx, label ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Grouped bar charts
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        activeSeries.forEachIndexed { seriesIdx, series ->
                            val value = series.data.getOrNull(labelIdx) ?: 0.0
                            val pct = (value / maxVal).toFloat().coerceIn(0.05f, 1f)
                            val color = getSeriesColor(seriesList.indexOf(series))
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(pct)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(color, color.copy(alpha = 0.5f))
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Represent aggregated totals on top
                    val combinedHoverTotal = activeSeries.sumOf { it.data.getOrNull(labelIdx) ?: 0.0 }
                    Text(
                        text = if (combinedHoverTotal >= 1000) String.format("%.1fK", combinedHoverTotal / 1000.0) else String.format("%.0f", combinedHoverTotal),
                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = label.take(5),
                        style = TextStyle(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MetricLineChart(
    dataPoints: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    MetricMultiLineChart(
        seriesList = listOf(ChartSeries("Metric", dataPoints)),
        labels = labels,
        modifier = modifier
    )
}

@Composable
fun MetricMultiLineChart(
    seriesList: List<ChartSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (seriesList.isEmpty() || labels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No Chart Data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var visibleSeries = remember(seriesList) {
        mutableStateMapOf<String, Boolean>().apply {
            seriesList.forEach { put(it.name, true) }
        }
    }
    
    val activeSeries = seriesList.filter { visibleSeries[it.name] ?: true }
    var activeHoverIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        InteractiveLegendRow(
            seriesList = seriesList,
            visibleSeries = visibleSeries,
            onToggleSeries = { name -> visibleSeries[name] = !(visibleSeries[name] ?: true) }
        )

        if (activeSeries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Select a metric above to display",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return
        }

        val maxVal = activeSeries.flatMap { it.data }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val minVal = activeSeries.flatMap { it.data }.minOrNull()?.coerceAtMost(maxVal - 1.0) ?: 0.0
        val range = (maxVal - minVal).coerceAtLeast(1.0)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // High-fidelity rich interactive Canvas with grid lines and crosshairs!
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(labels.size) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val spacing = size.width / (labels.size - 1).coerceAtLeast(1)
                                activeHoverIndex = (offset.x / spacing).roundToInt().coerceIn(0, labels.size - 1)
                            },
                            onDragEnd = { activeHoverIndex = null },
                            onDragCancel = { activeHoverIndex = null },
                            onDrag = { change, _ ->
                                val spacing = size.width / (labels.size - 1).coerceAtLeast(1)
                                activeHoverIndex = (change.position.x / spacing).roundToInt().coerceIn(0, labels.size - 1)
                            }
                        )
                    }
                    .pointerInput(labels.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                val spacing = size.width / (labels.size - 1).coerceAtLeast(1)
                                activeHoverIndex = (offset.x / spacing).roundToInt().coerceIn(0, labels.size - 1)
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                val padY = 24.dp.toPx()
                val graphHeight = height - (padY * 2)
                val spacing = width / (labels.size - 1).coerceAtLeast(1)

                // 1. Draw gridlines
                val gridLineCount = 3
                for (i in 0..gridLineCount) {
                    val y = padY + (graphHeight * i / gridLineCount)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 2. Draw vertical hover line if dynamic hover tooltip is active
                activeHoverIndex?.let { hIdx ->
                    val hoverX = hIdx * spacing
                    drawLine(
                        color = PostHogOrange.copy(alpha = 0.4f),
                        start = Offset(hoverX, padY),
                        end = Offset(hoverX, height - padY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // 3. Draw each active series path
                activeSeries.forEachIndexed { seriesIdx, series ->
                    val seriesColor = getSeriesColor(seriesList.indexOf(series))
                    val points = series.data.mapIndexed { index, value ->
                        val x = index * spacing
                        val y = padY + graphHeight - (((value - minVal) / range) * graphHeight).toFloat()
                        Offset(x, y)
                    }

                    if (points.isNotEmpty()) {
                        val strokePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }

                        // Gradient filled path below the curves
                        val fillPath = Path().apply {
                            addPath(strokePath)
                            lineTo(points.last().x, height - padY)
                            lineTo(points.first().x, height - padY)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(seriesColor.copy(alpha = 0.15f), Color.Transparent),
                                startY = points.map { it.y }.minOrNull() ?: 0f,
                                endY = height - padY
                            )
                        )

                        drawPath(
                            path = strokePath,
                            color = seriesColor,
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        // Highlight dots
                        points.forEachIndexed { index, point ->
                            val isHovered = index == activeHoverIndex
                            drawCircle(
                                color = Color.White,
                                radius = if (isHovered) 6.dp.toPx() else 4.dp.toPx(),
                                center = point
                            )
                            drawCircle(
                                color = seriesColor,
                                radius = if (isHovered) 4.dp.toPx() else 2.5.dp.toPx(),
                                center = point
                            )
                        }
                    }
                }
            }

            // 4. Elegant Interactive Hover Tooltip card
            activeHoverIndex?.let { hoverIdx ->
                val dateLabel = labels.getOrNull(hoverIdx) ?: ""
                
                Box(
                    modifier = Modifier
                        .align(if (hoverIdx > labels.size / 2) Alignment.TopStart else Alignment.TopEnd)
                        .padding(12.dp)
                        .widthIn(max = 200.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = dateLabel,
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        activeSeries.forEachIndexed { idx, series ->
                            val color = getSeriesColor(seriesList.indexOf(series))
                            val valAtHover = series.data.getOrNull(hoverIdx) ?: 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = series.name,
                                        style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (valAtHover >= 1000) String.format("%,.1fK", valAtHover / 1000.0) else String.format("%,.1f", valAtHover),
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Horizontal timeline scale
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEachIndexed { index, label ->
                if (index == 0 || index == labels.size - 1 || labels.size <= 5 || index % (labels.size / 3).coerceAtLeast(1) == 0) {
                    Text(
                        text = label,
                        style = TextStyle(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MetricFunnelChart(
    stages: List<String>,
    rates: List<Double>,
    modifier: Modifier = Modifier
) {
    if (rates.isEmpty() || stages.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No Chart Data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stages.forEachIndexed { index, stage ->
            val rate = rates.getOrNull(index) ?: 0.0
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stage indicator step
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(PostHogOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = PostHogOrange)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stage,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(110.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                
                // Funnel progress conversions bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Gray baseline indicator track
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    )
                    
                    // Progressive fill (rate as percentage)
                    val progressFraction = (rate / 100.0).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(PostHogOrange, PostHogOrange.copy(alpha = 0.60f))
                                )
                            )
                    )
                    
                    Text(
                        text = String.format("%.1f%%", rate),
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricPieChart(
    seriesList: List<ChartSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (seriesList.isEmpty() || labels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No Chart Data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // Pie chart usually aggregates metrics by labels across a single series, or list of series totals
    val pieSections = remember(seriesList, labels) {
        if (seriesList.size > 1) {
            seriesList.mapIndexed { idx, series ->
                val total = series.data.sum()
                PieSection(series.name, total, getSeriesColor(idx))
            }
        } else {
            val series = seriesList.first()
            labels.mapIndexed { idx, label ->
                val valAtIdx = series.data.getOrNull(idx) ?: 0.0
                PieSection(label, valAtIdx, getSeriesColor(idx))
            }
        }
    }

    val grandTotal = remember(pieSections) { pieSections.sumOf { it.value } }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .weight(1.1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                val innerRadius = radius * 0.65f
                val center = Offset(size.width / 2, size.height / 2)
                
                var startAngle = -90f
                
                pieSections.forEach { section ->
                    val sweepAngle = if (grandTotal > 0.0) {
                        (section.value / grandTotal * 360f).toFloat()
                    } else {
                        0f
                    }
                    
                    if (sweepAngle > 0f) {
                        drawArc(
                            color = section.color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = (radius - innerRadius))
                        )
                        startAngle += sweepAngle
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Total",
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (grandTotal >= 1000) String.format("%.1fK", grandTotal / 1000.0) else String.format("%.0f", grandTotal),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            items(pieSections.take(5)) { section ->
                val pct = if (grandTotal > 0.0) (section.value / grandTotal) * 100.0 else 0.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(section.color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = section.name,
                            style = TextStyle(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = String.format("%.1f%%", pct),
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

data class PieSection(
    val name: String,
    val value: Double,
    val color: Color
)

@Composable
fun MetricTableChart(
    seriesList: List<ChartSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (seriesList.isEmpty() || labels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No Chart Data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.02f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Breakdown / Metric Event",
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.weight(1.5f)
            )
            Text(
                "Aggregated",
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End),
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(seriesList.flatMap { series ->
                // Render list representing metric results across events
                labels.mapIndexed { idx, label ->
                    val value = series.data.getOrNull(idx) ?: 0.0
                    val name = if (series.name == "Metric" || seriesList.size == 1) label else "${series.name} ($label)"
                    TableItem(name, value)
                }
            }.sortedByDescending { it.value }.take(10)) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = if (item.value >= 1000) String.format("%,.1fK", item.value / 1000.0) else String.format("%,.1f", item.value),
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = PostHogOrange),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            }
        }
    }
}

data class TableItem(
    val label: String,
    val value: Double
)
