package com.githow.links.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githow.links.data.entity.RawSms
import com.githow.links.viewmodel.UnparsedSmsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnparsedSmsScreen(
    viewModel: UnparsedSmsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onManualEntry: (Long) -> Unit
) {
    val unparsedMessages by viewModel.unparsedMessages.collectAsState()
    val unparsedCount by viewModel.unparsedCount.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showStatistics by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    // Load data on first composition
    LaunchedEffect(Unit) {
        viewModel.loadUnparsedMessages()
        viewModel.loadStatistics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Unparsed Messages")
                        if (unparsedCount > 0) {
                            Text(
                                text = "$unparsedCount unparsed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Statistics toggle
                    IconButton(onClick = { showStatistics = !showStatistics }) {
                        Icon(
                            imageVector = if (showStatistics) Icons.Default.Close else Icons.Default.Info,
                            contentDescription = "Statistics"
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick = {
                            viewModel.loadUnparsedMessages()
                            viewModel.loadStatistics()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Statistics Panel (Collapsible)
            if (showStatistics && statistics != null) {
                UnparsedStatisticsPanel(statistics!!)
            }

            // Filter Chips
            UnparsedFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )

            // Content
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (unparsedMessages.isEmpty()) {
                UnparsedEmptyState()
            } else {
                // Filter messages based on selected filter
                val filteredMessages = when (selectedFilter) {
                    "PARSE_ERROR" -> unparsedMessages.filter { it.parse_status.name == "PARSE_ERROR" }
                    "UNPROCESSED" -> unparsedMessages.filter { it.parse_status.name == "UNPROCESSED" }
                    "MANUAL_REVIEW" -> unparsedMessages.filter { it.parse_status.name == "MANUAL_REVIEW" }
                    else -> unparsedMessages
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMessages) { rawSms ->
                        UnparsedSmsCard(
                            rawSms = rawSms,
                            onManualEntry = { onManualEntry(rawSms.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnparsedStatisticsPanel(statistics: ParseStatisticsUI) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "SMS Parsing Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                UnparsedStatItem("Total", statistics.total.toString(), Color(0xFF2196F3))
                UnparsedStatItem("Parsed", statistics.parsed_success.toString(), Color(0xFF4CAF50))
                UnparsedStatItem("Failed", statistics.parse_error.toString(), Color(0xFFF44336))
                UnparsedStatItem("Manual", statistics.manually_entered.toString(), Color(0xFFFF9800))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Success Rate Progress
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Success Rate",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${String.format("%.2f", statistics.parse_success_rate)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (statistics.parse_success_rate >= 99.0) {
                            Color(0xFF4CAF50)
                        } else {
                            Color(0xFFFF9800)
                        }
                    )
                }
                LinearProgressIndicator(
                    progress = (statistics.parse_success_rate / 100).toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = if (statistics.parse_success_rate >= 99.0) {
                        Color(0xFF4CAF50)
                    } else {
                        Color(0xFFFF9800)
                    }
                )
            }
        }
    }
}

@Composable
private fun UnparsedStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnparsedFilterChips(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == "ALL",
            onClick = { onFilterSelected("ALL") },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedFilter == "PARSE_ERROR",
            onClick = { onFilterSelected("PARSE_ERROR") },
            label = { Text("Parse Error") }
        )
        FilterChip(
            selected = selectedFilter == "UNPROCESSED",
            onClick = { onFilterSelected("UNPROCESSED") },
            label = { Text("Unprocessed") }
        )
        FilterChip(
            selected = selectedFilter == "MANUAL_REVIEW",
            onClick = { onFilterSelected("MANUAL_REVIEW") },
            label = { Text("Manual Review") }
        )
    }
}

@Composable
private fun UnparsedSmsCard(
    rawSms: RawSms,
    onManualEntry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = rawSms.parse_status.name,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (rawSms.parse_status.name) {
                            "PARSE_ERROR" -> Color(0xFFFFEBEE)
                            "UNPROCESSED" -> Color(0xFFFFF3E0)
                            "MANUAL_REVIEW" -> Color(0xFFE3F2FD)
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        labelColor = when (rawSms.parse_status.name) {
                            "PARSE_ERROR" -> Color(0xFFC62828)
                            "UNPROCESSED" -> Color(0xFFE65100)
                            "MANUAL_REVIEW" -> Color(0xFF1565C0)
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                )

                // Timestamp
                Text(
                    text = formatUnparsedTimestamp(rawSms.received_timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SMS Body
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = rawSms.message_body,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Error Message (if any)
            if (rawSms.parse_error_message != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rawSms.parse_error_message ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SMS ID
                Text(
                    text = "ID: ${rawSms.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Manual Entry Button
                Button(
                    onClick = onManualEntry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Manual Entry",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manual Entry")
                }
            }
        }
    }
}

@Composable
private fun UnparsedEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "No unparsed messages",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "All messages parsed successfully!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "No unparsed messages found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatUnparsedTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

// Data class for statistics UI
data class ParseStatisticsUI(
    val total: Int,
    val unprocessed: Int,
    val parsed_success: Int,
    val parse_error: Int,
    val manual_review: Int,
    val manually_entered: Int,
    val parse_success_rate: Double,
    val manual_entry_rate: Double
)