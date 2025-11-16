package com.githow.links.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.githow.links.data.entity.Transaction
import com.githow.links.viewmodel.ShiftViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftSummaryScreen(
    viewModel: ShiftViewModel,
    onNavigateBack: () -> Unit
) {
    val currentShift by viewModel.currentActiveShift.observeAsState()
    val shiftTransactions by viewModel.currentShiftTransactions.observeAsState(emptyList())

    var showEditClosingDialog by remember { mutableStateOf(false) }
    var showEditCSADialog by remember { mutableStateOf(false) }
    var selectedCSA by remember { mutableStateOf<String?>(null) }
    var showCSATransactionsDialog by remember { mutableStateOf(false) }
    var csaTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())

    // Calculate summaries
    val csaBreakdown = shiftTransactions
        .filter { !it.assigned_to.isNullOrBlank() }
        .groupBy { it.assigned_to }
        .mapValues { (_, transactions) ->
            Pair(transactions.sumOf { it.amount }, transactions.size)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shift Summary") },
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
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "⚠️ No Active Shift",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "There is no active shift to view.",
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
                // Shift Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Shift #${currentShift?.shift_id}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Started: ${dateFormat.format(Date(currentShift?.start_time ?: 0))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Balance Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Balance Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentShift?.status == "CLOSED" && currentShift?.close_balance != null) {
                                IconButton(onClick = { showEditClosingDialog = true }) {
                                    Icon(Icons.Default.Edit, "Edit Closing Balance")
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        SummaryRow("Opening Balance:", "KES ${numberFormat.format(currentShift?.open_balance ?: 0.0)}")
                        Spacer(Modifier.height(4.dp))

                        if (currentShift?.close_balance != null) {
                            SummaryRow("Closing Balance:", "KES ${numberFormat.format(currentShift?.close_balance)}")
                            Spacer(Modifier.height(4.dp))
                        }

                        if (currentShift?.close_balance != null) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))

                            val difference = (currentShift?.close_balance ?: 0.0) - (currentShift?.open_balance ?: 0.0)
                            SummaryRow(
                                "Difference:",
                                "KES ${numberFormat.format(difference)}",
                                if (difference >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // CSA Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "CSA Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        if (csaBreakdown.isEmpty()) {
                            Text(
                                "No transactions assigned yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                            )
                        } else {
                            csaBreakdown.forEach { (csaName, data) ->
                                val (amount, count) = data
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedCSA = csaName
                                            csaTransactions = shiftTransactions.filter { it.assigned_to == csaName }
                                            showCSATransactionsDialog = true
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                csaName ?: "Unknown",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "$count transaction${if (count != 1) "s" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "KES ${numberFormat.format(amount)}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    selectedCSA = csaName
                                                    showEditCSADialog = true
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    "Edit CSA Assignment",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Unassigned Transactions Warning (if any)
                val unassignedTransactions = shiftTransactions.filter { it.assigned_to.isNullOrBlank() }
                if (unassignedTransactions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "⚠️ Unassigned Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${unassignedTransactions.size} transaction${if (unassignedTransactions.size != 1) "s" else ""} not assigned to any CSA",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Total: KES ${numberFormat.format(unassignedTransactions.sumOf { it.amount })}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Detailed Review Section
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📋 Detailed Assignment Review",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        if (csaBreakdown.isEmpty()) {
                            Text(
                                "No assignments to review",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        } else {
                            // Group transactions by CSA for review
                            csaBreakdown.keys.forEach { csaName ->
                                val csaTxns = shiftTransactions.filter { it.assigned_to == csaName }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // CSA Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                csaName ?: "Unknown",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "${csaTxns.size} txns",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        HorizontalDivider()
                                        Spacer(Modifier.height(8.dp))

                                        // List all transactions for this CSA
                                        csaTxns.forEachIndexed { index, txn ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "${index + 1}. ${txn.mpesa_code}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        "${txn.transaction_type} • ${txn.sender_name ?: txn.business_name ?: "Unknown"}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                    Text(
                                                        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                                                            .format(Date(txn.timestamp)),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                    )
                                                }
                                                Text(
                                                    "KES ${numberFormat.format(txn.amount)}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            if (index < csaTxns.size - 1) {
                                                Spacer(Modifier.height(4.dp))
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                                )
                                            }
                                        }

                                        // CSA Total
                                        Spacer(Modifier.height(8.dp))
                                        HorizontalDivider()
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Total for ${csaName}:",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "KES ${numberFormat.format(csaTxns.sumOf { it.amount })}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Grand Total
                            Spacer(Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Total Assigned:",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "KES ${numberFormat.format(shiftTransactions.filter { !it.assigned_to.isNullOrBlank() }.sumOf { it.amount })}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Closing Balance Dialog
    if (showEditClosingDialog) {
        var editBalanceText by remember { mutableStateOf(currentShift?.close_balance?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showEditClosingDialog = false },
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
                            showEditClosingDialog = false
                        } else if (shift != null) {
                            viewModel.updateClosingBalance(
                                shiftId = shift.shift_id,
                                newClosingBalance = newBalance,
                                onSuccess = { showEditClosingDialog = false },
                                onError = { error ->
                                    errorMessage = error
                                    showErrorDialog = true
                                    showEditClosingDialog = false
                                }
                            )
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditClosingDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit CSA Assignment Dialog
    if (showEditCSADialog) {
        val persons by viewModel.persons.observeAsState(emptyList())
        var selectedNewCSA by remember { mutableStateOf<String?>(null) }
        var showConfirmation by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showEditCSADialog = false
                selectedNewCSA = null
            },
            title = { Text("Reassign Transactions") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        "Reassign all transactions from $selectedCSA to another CSA",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Select new CSA:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(persons.filter { it.short_name != selectedCSA }) { person ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedNewCSA = person.short_name },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedNewCSA == person.short_name)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            person.short_name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            person.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selectedNewCSA == person.short_name) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedNewCSA != null) {
                            showConfirmation = true
                        }
                    },
                    enabled = selectedNewCSA != null
                ) {
                    Text("Reassign")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showEditCSADialog = false
                        selectedNewCSA = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )

        // Confirmation Dialog
        if (showConfirmation) {
            val transactionsToReassign = shiftTransactions.filter { it.assigned_to == selectedCSA }

            AlertDialog(
                onDismissRequest = { showConfirmation = false },
                title = { Text("Confirm Reassignment") },
                text = {
                    Column {
                        Text(
                            "Are you sure you want to reassign ${transactionsToReassign.size} transaction${if (transactionsToReassign.size != 1) "s" else ""} from $selectedCSA to $selectedNewCSA?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Total Amount:",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    "KES ${numberFormat.format(transactionsToReassign.sumOf { it.amount })}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Perform reassignment
                            transactionsToReassign.forEach { txn ->
                                viewModel.reassignTransaction(
                                    transactionId = txn.id,
                                    newPersonName = selectedNewCSA!!,
                                    newCategory = "CSA"
                                )
                            }
                            showConfirmation = false
                            showEditCSADialog = false
                            selectedCSA = null
                            selectedNewCSA = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // CSA Transactions Dialog
    if (showCSATransactionsDialog) {
        AlertDialog(
            onDismissRequest = { showCSATransactionsDialog = false },
            title = { Text("$selectedCSA Transactions") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(csaTransactions) { transaction ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            transaction.mpesa_code,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "${transaction.transaction_type} • ${transaction.sender_name ?: transaction.business_name ?: "Unknown"}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        "KES ${numberFormat.format(transaction.amount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                                        .format(Date(transaction.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCSATransactionsDialog = false }) { Text("Close") }
            }
        )
    }

    // Error Dialog
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