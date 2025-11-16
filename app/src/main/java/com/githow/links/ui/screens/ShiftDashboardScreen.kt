package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githow.links.viewmodel.ShiftViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDashboardScreen(
    viewModel: ShiftViewModel,
    onNavigateToOpenShift: () -> Unit,
    onNavigateToCloseShift: () -> Unit,
    onNavigateToAssignTransactions: () -> Unit
) {
    val currentShift by viewModel.currentActiveShift.observeAsState()
    val currentTransactions by viewModel.currentShiftTransactions.observeAsState(emptyList())

    val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())

    // Calculate summary stats
    val totalReceived = currentTransactions
        .filter { it.transaction_type == "RECEIVED" }
        .sumOf { it.amount }

    val totalTransfers = currentTransactions
        .filter { it.transaction_type == "SENT" }
        .sumOf { it.amount }

    val totalWithdrawals = currentTransactions
        .filter { it.transaction_type == "WITHDRAW" }
        .sumOf { it.amount }

    val unassignedCount = currentTransactions.count { it.assigned_to.isNullOrBlank() }
    val assignedTotal = currentTransactions
        .filter { !it.assigned_to.isNullOrBlank() }
        .sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shift Management") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (currentShift == null) {
                FloatingActionButton(
                    onClick = onNavigateToOpenShift,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Open Shift")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (currentShift == null) {
                // No active shift
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No Active Shift",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Open a new shift to start processing transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToOpenShift,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open New Shift")
                        }
                    }
                }
            } else {
                // Active shift exists
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Active Shift #${currentShift?.shift_id}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Started: ${dateFormat.format(Date(currentShift?.start_time ?: 0))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Opening Balance", fontWeight = FontWeight.Medium)
                            Text(
                                "KES ${numberFormat.format(currentShift?.open_balance ?: 0.0)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Transaction Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Transaction Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        SummaryRow("Total Transactions:", "${currentTransactions.size}")
                        Spacer(Modifier.height(4.dp))
                        SummaryRow(
                            "Total Received:",
                            "KES ${numberFormat.format(totalReceived)}",
                            MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        SummaryRow("Total Transfers:", "KES ${numberFormat.format(totalTransfers)}")
                        Spacer(Modifier.height(4.dp))
                        SummaryRow(
                            "Total Withdrawals:",
                            "KES ${numberFormat.format(totalWithdrawals)}",
                            MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        SummaryRow(
                            "Assigned Total:",
                            "KES ${numberFormat.format(assignedTotal)}",
                            MaterialTheme.colorScheme.tertiary
                        )

                        if (unassignedCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    "⚠️ $unassignedCount unassigned transaction(s)",
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = onNavigateToAssignTransactions,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = currentTransactions.isNotEmpty()
                ) {
                    Text("Assign Transactions")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNavigateToCloseShift,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Shift")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}