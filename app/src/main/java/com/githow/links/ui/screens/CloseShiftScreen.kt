package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.githow.links.viewmodel.ShiftViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseShiftScreen(
    viewModel: ShiftViewModel,
    onNavigateBack: () -> Unit
) {
    val currentShift by viewModel.currentActiveShift.observeAsState()
    val shiftTransactions by viewModel.currentShiftTransactions.observeAsState(emptyList())

    var closingBalanceText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }

    val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())

    // Use CORRECT field names from Transaction entity
    val totalReceived = shiftTransactions.filter { it.transaction_type == "RECEIVED" }.sumOf { it.amount }
    val totalTransfers = shiftTransactions.filter { it.transaction_type == "SENT" }.sumOf { it.amount }
    val totalWithdrawals = shiftTransactions.filter { it.transaction_type == "WITHDRAW" }.sumOf { it.amount }
    val unassignedCount = shiftTransactions.count { it.assigned_to.isNullOrBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Close Shift") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (currentShift == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "⚠️ No Active Shift",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "There is no active shift to close.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNavigateBack) { Text("Go Back") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Shift #${currentShift?.shift_id}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Started", style = MaterialTheme.typography.labelSmall)
                        Text(dateFormat.format(Date(currentShift?.start_time ?: 0)))
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Opening Balance")
                            Text(
                                "KES ${numberFormat.format(currentShift?.open_balance ?: 0.0)}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Received:")
                            Text(
                                "KES ${numberFormat.format(totalReceived)}",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Transfers:")
                            Text("KES ${numberFormat.format(totalTransfers)}", fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Withdrawals:")
                            Text(
                                "KES ${numberFormat.format(totalWithdrawals)}",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Transactions:")
                            Text("${shiftTransactions.size}", fontWeight = FontWeight.Bold)
                        }

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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Enter Closing Balance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Enter the M-PESA account balance at shift end",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = closingBalanceText,
                            onValueChange = { closingBalanceText = it },
                            label = { Text("Closing Balance (KES)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            prefix = { Text("KES ") }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val shift = currentShift
                        val closingBalance = closingBalanceText.toDoubleOrNull()
                        if (closingBalance == null) {
                            errorMessage = "Please enter a valid closing balance"
                            showErrorDialog = true
                        } else if (unassignedCount > 0) {
                            errorMessage = "Cannot close shift with unassigned transactions"
                            showErrorDialog = true
                        } else if (shift != null) {
                            isProcessing = true
                            viewModel.closeShift(
                                shiftId = shift.shift_id,
                                closingBalance = closingBalance,
                                onSuccess = {
                                    isProcessing = false
                                    showSuccessDialog = true
                                },
                                onError = { error ->
                                    isProcessing = false
                                    errorMessage = error
                                    showErrorDialog = true
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isProcessing && closingBalanceText.isNotBlank()
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isProcessing) "Closing Shift..." else "Close Shift")
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }

                if (currentShift?.status == "CLOSED" && currentShift?.close_balance != null) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Current Closing Balance", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "KES ${numberFormat.format(currentShift!!.close_balance)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit Closing Balance")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("✓ Shift Closed") },
            text = {
                Text("Shift closed with balance of KES ${numberFormat.format(closingBalanceText.toDoubleOrNull() ?: 0.0)}")
            },
            confirmButton = {
                Button(onClick = { showSuccessDialog = false; onNavigateBack() }) { Text("OK") }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) { Text("OK") }
            }
        )
    }

    if (showEditDialog) {
        var editBalanceText by remember { mutableStateOf(currentShift?.close_balance?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Closing Balance") },
            text = {
                Column {
                    Text("Update the closing balance for this shift")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editBalanceText,
                        onValueChange = { editBalanceText = it },
                        label = { Text("New Closing Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        prefix = { Text("KES ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shift = currentShift
                        val newBalance = editBalanceText.toDoubleOrNull()
                        if (newBalance == null) {
                            errorMessage = "Please enter a valid balance"
                            showErrorDialog = true
                            showEditDialog = false
                        } else if (shift != null) {
                            viewModel.updateClosingBalance(
                                shiftId = shift.shift_id,
                                newClosingBalance = newBalance,
                                onSuccess = { showEditDialog = false },
                                onError = { error ->
                                    errorMessage = error
                                    showErrorDialog = true
                                    showEditDialog = false
                                }
                            )
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}