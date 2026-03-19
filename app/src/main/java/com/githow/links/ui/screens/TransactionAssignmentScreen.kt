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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githow.links.data.entity.Person
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.TransactionDirection
import com.githow.links.data.entity.TransactionRole
import com.githow.links.viewmodel.ShiftViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Role display helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun TransactionRole.displayName(): String = when (this) {
    TransactionRole.CUSTOMER_RECEIPT  -> "Customer Receipt"
    TransactionRole.TILL_TRANSFER_IN  -> "Till Transfer In"
    TransactionRole.WITHDRAWAL        -> "Withdrawal"
    TransactionRole.REVERSAL          -> "Reversal"
    TransactionRole.TILL_TRANSFER_OUT -> "Till Transfer Out"
    TransactionRole.DUPLICATE         -> "Duplicate"
    TransactionRole.UNASSIGNED        -> "Unassigned"
}

private fun TransactionRole.directionLabel(): String = when (this) {
    TransactionRole.CUSTOMER_RECEIPT,
    TransactionRole.TILL_TRANSFER_IN  -> "IN +"
    TransactionRole.WITHDRAWAL,
    TransactionRole.REVERSAL,
    TransactionRole.TILL_TRANSFER_OUT -> "OUT −"
    TransactionRole.DUPLICATE         -> "EXCLUDED"
    TransactionRole.UNASSIGNED        -> "PENDING"
}

private fun TransactionRole.directionColor(
    primary: Color,
    error: Color,
    outline: Color
): Color = when (this) {
    TransactionRole.CUSTOMER_RECEIPT,
    TransactionRole.TILL_TRANSFER_IN  -> primary
    TransactionRole.WITHDRAWAL,
    TransactionRole.REVERSAL,
    TransactionRole.TILL_TRANSFER_OUT -> error
    else                               -> outline
}

// Roles that require a CSA to be selected
private fun TransactionRole.requiresCsa(): Boolean = when (this) {
    TransactionRole.CUSTOMER_RECEIPT  -> true
    TransactionRole.TILL_TRANSFER_IN  -> true
    TransactionRole.WITHDRAWAL        -> true
    TransactionRole.REVERSAL          -> true
    TransactionRole.TILL_TRANSFER_OUT -> true
    TransactionRole.DUPLICATE         -> false
    TransactionRole.UNASSIGNED        -> false
}

// Assignable roles — UNASSIGNED is not a valid target
private val ASSIGNABLE_ROLES = listOf(
    TransactionRole.CUSTOMER_RECEIPT,
    TransactionRole.TILL_TRANSFER_IN,
    TransactionRole.WITHDRAWAL,
    TransactionRole.REVERSAL,
    TransactionRole.TILL_TRANSFER_OUT,
    TransactionRole.DUPLICATE
)

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
    var filterType by remember { mutableStateOf("unassigned") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshTransactions() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
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
                unassignedCount = unassignedTransactions.size,
                onFilterChange = {
                    filterType = it
                    selectedTransactions = setOf()
                }
            )

            // Transaction List
            val displayTransactions = when (filterType) {
                "unassigned" -> unassignedTransactions
                "assigned"   -> assignedTransactions
                else         -> unassignedTransactions + assignedTransactions
            }

            if (displayTransactions.isEmpty()) {
                EmptyTransactionsMessage(filterType)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayTransactions, key = { it.id }) { transaction ->
                        AssignableTransactionCard(
                            transaction = transaction,
                            isSelected = selectedTransactions.contains(transaction.id),
                            onToggleSelection = {
                                // Only unassigned transactions can be batch-selected
                                if (transaction.role == TransactionRole.UNASSIGNED) {
                                    selectedTransactions = if (selectedTransactions.contains(transaction.id)) {
                                        selectedTransactions - transaction.id
                                    } else {
                                        selectedTransactions + transaction.id
                                    }
                                }
                            },
                            onEdit = {
                                transactionToEdit = transaction
                                showEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Batch Assignment Dialog
    if (showAssignDialog) {
        AssignmentDialog(
            persons = persons,
            transactionCount = selectedTransactions.size,
            onDismiss = {
                showAssignDialog = false
            },
            onConfirm = { role, personName ->
                viewModel.assignTransactions(
                    transactionIds = selectedTransactions.toList(),
                    personName = personName ?: "",
                    role = role
                )
                selectedTransactions = setOf()
                showAssignDialog = false
            }
        )
    }

    // Edit Single Transaction Dialog
    if (showEditDialog && transactionToEdit != null) {
        EditAssignmentDialog(
            transaction = transactionToEdit!!,
            persons = persons,
            onDismiss = {
                showEditDialog = false
                transactionToEdit = null
            },
            onConfirm = { role, personName ->
                viewModel.assignTransactions(
                    transactionIds = listOf(transactionToEdit!!.id),
                    personName = personName ?: "",
                    role = role
                )
                showEditDialog = false
                transactionToEdit = null
            },
            onUnassign = {
                viewModel.unassignTransaction(transactionToEdit!!.id)
                showEditDialog = false
                transactionToEdit = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary Card
// ─────────────────────────────────────────────────────────────────────────────
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
            containerColor = if (totalUnassigned > 0)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
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
                    color = if (totalUnassigned > 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                Text(text = "Unassigned", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalAssigned",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Assigned", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatAmount(unassignedAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (totalUnassigned > 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(text = "Pending", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (totalUnassigned > 0) {
            Text(
                text = "⚠ Assign all transactions before closing shift",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter Chips
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChips(
    selected: String,
    unassignedCount: Int,
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
            label = { Text("Unassigned ($unassignedCount)") }
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

// ─────────────────────────────────────────────────────────────────────────────
// Transaction Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AssignableTransactionCard(
    transaction: Transaction,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onEdit: () -> Unit
) {
    val isUnassigned = transaction.role == TransactionRole.UNASSIGNED

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected   -> MaterialTheme.colorScheme.primaryContainer
                isUnassigned -> MaterialTheme.colorScheme.surface
                else         -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox (only for unassigned)
            if (isUnassigned) {
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

                // Role + CSA badge
                if (!isUnassigned) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Role badge
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = transaction.role.displayName(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        // CSA badge
                        if (!transaction.assigned_to.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = transaction.assigned_to,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Amount + direction + edit
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAmount(transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = transaction.role.directionLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = transaction.role.directionColor(
                        primary = MaterialTheme.colorScheme.primary,
                        error = MaterialTheme.colorScheme.error,
                        outline = MaterialTheme.colorScheme.outline
                    )
                )
                if (!isUnassigned) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Batch Assignment Dialog — pick role first, then CSA if needed
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AssignmentDialog(
    persons: List<Person>,
    transactionCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (role: TransactionRole, personName: String?) -> Unit
) {
    var selectedRole by remember { mutableStateOf<TransactionRole?>(null) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }

    val canConfirm = selectedRole != null &&
            (selectedRole!!.requiresCsa() == false || !selectedPerson.isNullOrBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign $transactionCount Transaction(s)") },
        text = {
            Column {
                // ── Step 1: Role ─────────────────────────────────────────────
                Text(
                    "1. Select role:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ASSIGNABLE_ROLES.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedRole == role,
                                onClick = {
                                    selectedRole = role
                                    if (!role.requiresCsa()) selectedPerson = null
                                }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedRole == role,
                                onClick = {
                                    selectedRole = role
                                    if (!role.requiresCsa()) selectedPerson = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(role.displayName())
                        }
                        Text(
                            text = role.directionLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (role.directionLabel().startsWith("IN"))
                                MaterialTheme.colorScheme.primary
                            else if (role.directionLabel().startsWith("OUT"))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // ── Step 2: CSA (only if role needs one) ─────────────────────
                if (selectedRole?.requiresCsa() == true) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "2. Assign to CSA:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    persons.filter { it.is_active }.forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedPerson == person.short_name,
                                    onClick = { selectedPerson = person.short_name }
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPerson == person.short_name,
                                onClick = { selectedPerson = person.short_name }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(person.short_name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedRole!!, selectedPerson) },
                enabled = canConfirm
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit Assignment Dialog — same as batch but pre-filled
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EditAssignmentDialog(
    transaction: Transaction,
    persons: List<Person>,
    onDismiss: () -> Unit,
    onConfirm: (role: TransactionRole, personName: String?) -> Unit,
    onUnassign: () -> Unit
) {
    var selectedRole by remember {
        mutableStateOf<TransactionRole?>(
            if (transaction.role == TransactionRole.UNASSIGNED) null else transaction.role
        )
    }
    var selectedPerson by remember { mutableStateOf(transaction.assigned_to) }

    val canConfirm = selectedRole != null &&
            (selectedRole!!.requiresCsa() == false || !selectedPerson.isNullOrBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Assignment") },
        text = {
            Column {
                // Transaction info card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
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

                // Role picker
                Text(
                    "Role:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(ASSIGNABLE_ROLES) { role ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedRole == role,
                                    onClick = {
                                        selectedRole = role
                                        if (!role.requiresCsa()) selectedPerson = null
                                    }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedRole == role,
                                    onClick = {
                                        selectedRole = role
                                        if (!role.requiresCsa()) selectedPerson = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(role.displayName(), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = role.directionLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (role.directionLabel().startsWith("IN"))
                                    MaterialTheme.colorScheme.primary
                                else if (role.directionLabel().startsWith("OUT"))
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // CSA picker
                if (selectedRole?.requiresCsa() == true) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "CSA:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    persons.filter { it.is_active }.forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedPerson == person.short_name,
                                    onClick = { selectedPerson = person.short_name }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPerson == person.short_name,
                                onClick = { selectedPerson = person.short_name }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(person.short_name)
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
                    onClick = { onConfirm(selectedRole!!, selectedPerson) },
                    enabled = canConfirm
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NoActiveShiftMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("No Active Shift", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Open a shift first to assign transactions", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyTransactionsMessage(filterType: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = when (filterType) {
                "unassigned" -> "✅ All transactions assigned"
                "assigned"   -> "No assigned transactions yet"
                else         -> "No transactions in this shift"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

private fun formatAmount(amount: Double): String {
    return "Ksh ${String.format("%,.0f", amount)}"
}