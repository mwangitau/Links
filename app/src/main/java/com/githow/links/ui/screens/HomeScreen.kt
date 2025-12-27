package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githow.links.viewmodel.ShiftViewModel
import com.githow.links.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onNavigateToOpenShift: () -> Unit = {},
    onNavigateToCloseShift: () -> Unit = {},
    onNavigateToShiftDashboard: () -> Unit = {},
    onNavigateToAssignTransactions: () -> Unit = {},
    onNavigateToShiftHistory: () -> Unit = {}
) {
    val shiftViewModel: ShiftViewModel = viewModel()
    val transactionViewModel: TransactionViewModel = viewModel()

    val currentShift by shiftViewModel.currentActiveShift.observeAsState()
    val allTransactions by transactionViewModel.allTransactions.observeAsState(emptyList())

    // Calculate today's stats
    val todayTransactions = remember(allTransactions) {
        allTransactions.filter { it.date_received == getTodayDateString() }
    }
    val todayTotal = remember(todayTransactions) {
        todayTransactions.filter { it.transaction_type == "RECEIVED" }.sumOf { it.amount }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "LINKS",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "M-PESA Management System",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Current Shift Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (currentShift != null)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Current Shift",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (currentShift != null) {
                            Text(
                                text = "Shift #${currentShift!!.shift_id} - ${currentShift!!.status}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Started: ${formatTime(currentShift!!.start_time)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "No active shift",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Icon(
                        if (currentShift != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (currentShift != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Today's Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Today's Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Transaction Count
                    Column {
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${todayTransactions.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Total Received
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Received",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Ksh ${String.format("%,.0f", todayTotal)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shift Management Section
        Text(
            text = "Shift Management",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Shift Action Buttons
        if (currentShift == null) {
            // No active shift - Show Open Shift button
            ShiftActionCard(
                title = "Open New Shift",
                description = "Start a new shift to begin accepting transactions",
                icon = Icons.Default.PlayArrow,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = onNavigateToOpenShift
            )
        } else {
            // Active shift - Show shift options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShiftActionCard(
                    title = "Shift Dashboard",
                    description = "View current shift details and statistics",
                    icon = Icons.Default.Dashboard,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onNavigateToShiftDashboard
                )

                ShiftActionCard(
                    title = "Assign Transactions",
                    description = "Assign transactions to CSAs",
                    icon = Icons.Default.Assignment,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onNavigateToAssignTransactions
                )

                ShiftActionCard(
                    title = "Close Shift",
                    description = "End current shift and reconcile",
                    icon = Icons.Default.Stop,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    onClick = onNavigateToCloseShift
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Shift History Button
        ShiftActionCard(
            title = "Shift History",
            description = "View past closed shifts",
            icon = Icons.Default.History,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onClick = onNavigateToShiftHistory
        )

        Spacer(modifier = Modifier.height(24.dp))

        // App Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LINKS v3.0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Features:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text("• Automatic M-PESA SMS parsing", style = MaterialTheme.typography.bodySmall)
                Text("• Shift-based transaction management", style = MaterialTheme.typography.bodySmall)
                Text("• CSA assignment and tracking", style = MaterialTheme.typography.bodySmall)
                Text("• Manual review for failed parses", style = MaterialTheme.typography.bodySmall)
                Text("• Cloud sync with Google Sheets", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ShiftActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun getTodayDateString(): String {
    val calendar = Calendar.getInstance()
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1
    val year = calendar.get(Calendar.YEAR) % 100
    return "$day/$month/$year"
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
