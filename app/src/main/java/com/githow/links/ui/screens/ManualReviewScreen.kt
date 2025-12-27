package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githow.links.ui.components.*
import com.githow.links.viewmodel.ManualReviewViewModel
import com.githow.links.viewmodel.ManualReviewUiState
import com.githow.links.viewmodel.ManualReviewStatsUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualReviewScreen(
    viewModel: ManualReviewViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val pendingReviews by viewModel.pendingReviews.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val statistics by viewModel.statistics.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<Pair<Long, ManualEntryData>?>(null) }
    var showStatistics by remember { mutableStateOf(false) }

    // Load statistics on first composition
    LaunchedEffect(Unit) {
        viewModel.loadStatistics()
    }

    // Handle UI state changes
    LaunchedEffect(uiState) {
        when (uiState) {
            is ManualReviewUiState.Success -> {
                kotlinx.coroutines.delay(2000)
                viewModel.resetUiState()
            }
            is ManualReviewUiState.Error -> {
                kotlinx.coroutines.delay(3000)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Manual Review")
                        if (pendingCount > 0) {
                            Text(
                                text = "$pendingCount pending",
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
                    // Statistics button - Using Info icon (guaranteed to exist)
                    IconButton(onClick = { showStatistics = !showStatistics }) {
                        Icon(Icons.Default.Info, "Statistics")
                    }

                    // Refresh button
                    IconButton(onClick = { viewModel.loadStatistics() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Statistics card (if shown)
                if (showStatistics && statistics != null) {
                    StatisticsCard(
                        statistics = statistics!!,
                        onDismiss = { showStatistics = false },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Status message
                when (uiState) {
                    is ManualReviewUiState.Success -> {
                        SuccessMessage(
                            message = (uiState as ManualReviewUiState.Success).message,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    is ManualReviewUiState.Error -> {
                        ErrorMessage(
                            message = (uiState as ManualReviewUiState.Error).message,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    else -> {}
                }

                // Main content
                if (pendingReviews.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = pendingReviews,
                            key = { it.raw_sms_id }
                        ) { item ->
                            ManualEntryCard(
                                item = item,
                                onSubmit = { data ->
                                    selectedItem = Pair(item.raw_sms_id, data)
                                    showPasswordDialog = true
                                },
                                onSkip = {
                                    currentUser?.let { user ->
                                        viewModel.skipReview(
                                            rawSmsId = item.raw_sms_id,
                                            supervisorUsername = user.username,
                                            reason = "Skipped by supervisor"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Loading overlay
            if (uiState is ManualReviewUiState.Submitting) {
                LoadingOverlay()
            }
        }
    }

    // Password dialog
    if (showPasswordDialog && selectedItem != null) {
        SupervisorPasswordDialog(
            title = "Confirm Manual Entry",
            message = "Enter supervisor password to create this transaction",
            onAuthenticated = { supervisor ->
                val (rawSmsId, data) = selectedItem!!
                viewModel.submitManualEntry(
                    rawSmsId = rawSmsId,
                    entryData = data,
                    supervisorUsername = supervisor.username
                )
                showPasswordDialog = false
                selectedItem = null
            },
            onDismiss = {
                showPasswordDialog = false
                selectedItem = null
            }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "All Caught Up!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No unparsed SMS to review",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatisticsCard(
    statistics: ManualReviewStatsUI,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manual Review Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            Divider()

            StatRow("Total Reviews", statistics.total.toString())
            StatRow("Pending", statistics.pending.toString(), Color(0xFFFF9800))
            StatRow("Completed", statistics.completed.toString(), Color(0xFF4CAF50))
            StatRow("Skipped", statistics.skipped.toString(), Color(0xFF9E9E9E))

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Completion Rate", fontWeight = FontWeight.Medium)
                Text(
                    text = "${String.format("%.1f", statistics.completionRate)}%",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    color: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            text = value,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SuccessMessage(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, color = Color(0xFF4CAF50))
        }
    }
}

@Composable
private fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Processing...")
            }
        }
    }
}