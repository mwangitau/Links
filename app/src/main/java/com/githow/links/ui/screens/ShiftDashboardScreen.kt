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
    onNavigateToAssignTransactions: () -> Unit,
    onNavigateToManageCSAs: () -> Unit = {},
    onNavigateToShiftSummary: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
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
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View Shift History")
                        }
                    }
                }
            } else {
                // Active shift exists
                val shiftStatusColor = when (currentShift?.status) {
                    "FROZEN" -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = shiftStatusColor
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
                                    if (currentShift?.status == "FROZEN")
                                        "🔒 Frozen Shift #${currentShift?.shift_id}"
                                    else
                                        "Active Shift #${currentShift?.shift_id}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Started: ${dateFormat.format(Date(currentShift?.start_time ?: 0))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                currentShift?.cutoff_timestamp?.let { cutoffTime ->
                                    if (currentShift?.status == "FROZEN") {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Frozen: ${dateFormat.format(Date(cutoffTime))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (currentShift?.status == "FROZEN")
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.primary,
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
                    onClick = onNavigateToManageCSAs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage CSAs")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNavigateToShiftSummary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Shift Summary")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Shift History")
                }

                Spacer(Modifier.height(16.dp))

                // FREEZE SHIFT BUTTON (Step 1)
                if (currentShift?.status == "ACTIVE") {
                    Button(
                        onClick = {
                            viewModel.freezeShift(
                                onSuccess = {
                                    // Shift is now FROZEN - user can assign remaining transactions
                                },
                                onError = { error ->
                                    // Handle error (show toast/snackbar)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("🔒 Freeze Shift (Step 1)")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Freeze the shift to stop new transactions from being added. You can then assign remaining transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // CLOSE SHIFT BUTTON (Step 2) - Only available if FROZEN
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateToCloseShift,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = currentShift?.status == "FROZEN"
                ) {
                    Text(if (currentShift?.status == "FROZEN") "Close Shift (Step 2)" else "Close Shift")
                }

                if (currentShift?.status == "FROZEN") {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            "✓ Shift is FROZEN. Assign all transactions, then click 'Close Shift'.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else if (currentShift?.status == "ACTIVE") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "To close shift: First freeze it, then assign all transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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