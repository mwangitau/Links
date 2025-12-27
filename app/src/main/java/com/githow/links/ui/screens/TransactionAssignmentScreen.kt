package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githow.links.data.entity.Transaction
import com.githow.links.viewmodel.ShiftViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionAssignmentScreen(
    viewModel: ShiftViewModel,
    onNavigateBack: () -> Unit
) {
    val currentShift by viewModel.currentShift.observeAsState()
    val unassignedTransactions by viewModel.unassignedTransactions.observeAsState(emptyList())
    val assignedTransactions by viewModel.assignedTransactions.observeAsState(emptyList())
    val persons by viewModel.persons.observeAsState(emptyList())

    var selectedTransactions by remember { mutableStateOf(setOf<Long>()) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }
    var filterType by remember { mutableStateOf("unassigned") }  // unassigned, assigned, all

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                // REMOVED: Refresh button since refreshTransactions() doesn't exist
                // The LiveData observables will automatically update when data changes
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (selectedTransactions.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAssignDialog = true },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text("Assign ${selectedTransactions.size}") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentShift == null) {
                NoActiveShiftMessage()
                return@Column
            }

            // Summary Card
            AssignmentSummaryCard(
                totalUnassigned = unassignedTransactions.size,
                totalAssigned = assignedTransactions.size,
                unassignedAmount = unassignedTransactions.sumOf { it.amount }
            )

            // Filter Chips
            FilterChips(
                selected = filterType,
                onFilterChange = {
                    filterType = it
                    selectedTransactions = setOf()  // Clear selection on filter change
                }
            )

            // Transaction List
            val displayTransactions = when (filterType) {
                "unassigned" -> unassignedTransactions
                "assigned" -> assignedTransactions
                else -> unassignedTransactions + assignedTransactions
            }

            if (displayTransactions.isEmpty()) {
                EmptyTransactionsMessage(filterType)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayTransactions) { transaction ->
                        AssignableTransactionCard(
                            transaction = transaction,
                            isSelected = selectedTransactions.contains(transaction.id),
                            onToggleSelection = {
                                selectedTransactions = if (selectedTransactions.contains(transaction.id)) {
                                    selectedTransactions - transaction.id
                                } else {
                                    selectedTransactions + transaction.id
                                }
                            },
                            onEdit = {
                                transactionToEdit = transaction
                                selectedPerson = transaction.assigned_to
                                showEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Assignment Dialog
    if (showAssignDialog) {
        AssignmentDialog(
            persons = persons,
            selectedPerson = selectedPerson,
            onPersonSelected = { selectedPerson = it },
            onDismiss = {
                showAssignDialog = false
                selectedPerson = null
            },
            onConfirm = {
                if (selectedPerson != null) {
                    viewModel.assignTransactions(
                        transactionIds = selectedTransactions.toList(),
                        personName = selectedPerson!!,
                        category = if (selectedPerson == "Neutral") "NEUTRAL" else "CSA"
                    )
                    selectedTransactions = setOf()
                    showAssignDialog = false
                    selectedPerson = null
                }
            }
        )
    }

    // Edit Dialog
    if (showEditDialog && transactionToEdit != null) {
        EditAssignmentDialog(
            transaction = transactionToEdit!!,
            persons = persons,
            currentAssignment = selectedPerson,
            onPersonSelected = { selectedPerson = it },
            onDismiss = {
                showEditDialog = false
                transactionToEdit = null
                selectedPerson = null
            },
            onConfirm = {
                if (selectedPerson != null) {
                    viewModel.reassignTransaction(
                        transactionId = transactionToEdit!!.id,
                        newPersonName = selectedPerson!!,
                        newCategory = if (selectedPerson == "Neutral") "NEUTRAL" else "CSA"
                    )
                    showEditDialog = false
                    transactionToEdit = null
                    selectedPerson = null
                }
            },
            onUnassign = {
                viewModel.unassignTransaction(transactionToEdit!!.id)
                showEditDialog = false
                transactionToEdit = null
                selectedPerson = null
            }
        )
    }
}

@Composable
fun AssignmentSummaryCard(
    totalUnassigned: Int,
    totalAssigned: Int,
    unassignedAmount: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalUnassigned",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Unassigned",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalAssigned",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Assigned",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatAmount(unassignedAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChips(
    selected: String,
    onFilterChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == "unassigned",
            onClick = { onFilterChange("unassigned") },
            label = { Text("Unassigned") }
        )
        FilterChip(
            selected = selected == "assigned",
            onClick = { onFilterChange("assigned") },
            label = { Text("Assigned") }
        )
        FilterChip(
            selected = selected == "all",
            onClick = { onFilterChange("all") },
            label = { Text("All") }
        )
    }
}

@Composable
fun AssignableTransactionCard(
    transaction: Transaction,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection Checkbox
            if (transaction.assigned_to == null) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() }
                )
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Assigned",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Transaction Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.sender_name ?: transaction.business_name ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (transaction.sender_phone != null) {
                    Text(
                        text = transaction.sender_phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = "${transaction.time_received} • ${transaction.mpesa_code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Show assignment if assigned
                if (transaction.assigned_to != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Assigned to: ${transaction.assigned_to}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAmount(transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Edit button for assigned transactions
                if (transaction.assigned_to != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssignmentDialog(
    persons: List<com.githow.links.data.entity.Person>,
    selectedPerson: String?,
    onPersonSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign To") },
        text = {
            Column {
                Text(
                    "Select a person or category:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn {
                    // Persons (CSAs only)
                    items(persons.filter { it.is_active }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedPerson == person.short_name,
                                    onClick = { onPersonSelected(person.short_name) }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPerson == person.short_name,
                                onClick = { onPersonSelected(person.short_name) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(person.short_name)
                        }
                    }

                    // Neutral option
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedPerson == "Neutral",
                                    onClick = { onPersonSelected("Neutral") }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPerson == "Neutral",
                                onClick = { onPersonSelected("Neutral") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Neutral")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedPerson != null
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditAssignmentDialog(
    transaction: Transaction,
    persons: List<com.githow.links.data.entity.Person>,
    currentAssignment: String?,
    onPersonSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onUnassign: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Assignment") },
        text = {
            Column {
                // Transaction info
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = transaction.sender_name ?: transaction.business_name ?: "Unknown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatAmount(transaction.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = transaction.mpesa_code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    "Currently assigned to: ${transaction.assigned_to}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    "Reassign to:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(modifier = Modifier.height(200.dp)) {
                    // Persons (CSAs only)
                    items(persons.filter { it.is_active }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currentAssignment == person.short_name,
                                    onClick = { onPersonSelected(person.short_name) }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentAssignment == person.short_name,
                                onClick = { onPersonSelected(person.short_name) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(person.short_name)
                        }
                    }

                    // Neutral option
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currentAssignment == "Neutral",
                                    onClick = { onPersonSelected("Neutral") }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentAssignment == "Neutral",
                                onClick = { onPersonSelected("Neutral") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Neutral")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUnassign,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Unassign")
                }
                Button(
                    onClick = onConfirm,
                    enabled = currentAssignment != null && currentAssignment != transaction.assigned_to
                ) {
                    Text("Reassign")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NoActiveShiftMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Active Shift",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Open a shift first to assign transactions",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun EmptyTransactionsMessage(filterType: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (filterType) {
                "unassigned" -> "No unassigned transactions"
                "assigned" -> "No assigned transactions yet"
                else -> "No transactions in this shift"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

private fun formatAmount(amount: Double): String {
    return "Ksh ${String.format("%,.0f", amount)}"
}