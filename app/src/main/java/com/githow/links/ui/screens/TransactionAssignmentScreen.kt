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
import com.githow.links.data.entity.requiresCsa
import com.githow.links.viewmodel.ShiftViewModel

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

    // ── Double-assignment guard ───────────────────────────────────────────────
    val isAssigning by viewModel.isAssigning.observeAsState(false)

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
                    onClick = {
                        // Guard — do not open dialog if assignment is in progress
                        if (!isAssigning) showAssignDialog = true
                    },
                    icon = {
                        if (isAssigning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    text = {
                        Text(
                            if (isAssigning) "Assigning..."
                            else "Assign ${selectedTransactions.size}"
                        )
                    }
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

            // Assignment in progress banner
            if (isAssigning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Saving assignment — please wait...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            AssignmentSummaryCard(
                totalUnassigned = unassignedTransactions.size +
                        assignedTransactions.count {
                            it.role.requiresCsa() && it.assigned_to.isNullOrBlank()
                        },
                totalAssigned = assignedTransactions.count {
                    !it.assigned_to.isNullOrBlank() || !it.role.requiresCsa()
                },
                unassignedAmount = unassignedTransactions.sumOf { it.amount } +
                        assignedTransactions.filter {
                            it.role.requiresCsa() && it.assigned_to.isNullOrBlank()
                        }.sumOf { it.amount }
            )

            FilterChips(
                selected = filterType,
                unassignedCount = unassignedTransactions.size,
                onFilterChange = {
                    filterType = it
                    selectedTransactions = setOf()
                }
            )

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
                        val isUnassigned = transaction.role == TransactionRole.UNASSIGNED
                        val missingCsa = transaction.role.requiresCsa() &&
                                transaction.assigned_to.isNullOrBlank()
                        val needsAttention = isUnassigned || missingCsa

                        AssignableTransactionCard(
                            transaction = transaction,
                            isSelected = selectedTransactions.contains(transaction.id),
                            isAssigning = isAssigning,
                            onToggleSelection = {
                                // Block selection while assignment is in progress
                                if (!isAssigning && needsAttention) {
                                    selectedTransactions = if (selectedTransactions.contains(transaction.id)) {
                                        selectedTransactions - transaction.id
                                    } else {
                                        selectedTransactions + transaction.id
                                    }
                                }
                            },
                            onEdit = {
                                if (!isAssigning) {
                                    transactionToEdit = transaction
                                    showEditDialog = true
                                }
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
            isAssigning = isAssigning,
            onDismiss = { showAssignDialog = false },
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

@Composable
fun AssignableTransactionCard(
    transaction: Transaction,
    isSelected: Boolean,
    isAssigning: Boolean,
    onToggleSelection: () -> Unit,
    onEdit: () -> Unit
) {
    val isUnassigned = transaction.role == TransactionRole.UNASSIGNED
    val missingCsa = transaction.role.requiresCsa() && transaction.assigned_to.isNullOrBlank()
    val needsAttention = isUnassigned || missingCsa

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAssigning && isSelected -> MaterialTheme.colorScheme.secondaryContainer
                isSelected               -> MaterialTheme.colorScheme.primaryContainer
                needsAttention           -> MaterialTheme.colorScheme.surface
                else                     -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (needsAttention) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    enabled = !isAssigning  // disabled while assigning
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

                if (!isUnassigned) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp),
                        enabled = !isAssigning  // disabled while assigning
                    ) {
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

private sealed class AssignOption {
    data class Csa(val name: String) : AssignOption()
    data class Role(val role: TransactionRole) : AssignOption()
}

@Composable
fun AssignmentDialog(
    persons: List<Person>,
    transactionCount: Int,
    isAssigning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (role: TransactionRole, personName: String?) -> Unit
) {
    var selected by remember { mutableStateOf<AssignOption?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isAssigning) onDismiss() },
        title = { Text("Assign $transactionCount Transaction(s)") },
        text = {
            LazyColumn {
                item {
                    Text(
                        "CSA",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(persons.filter { it.is_active }) { person ->
                    val opt = AssignOption.Csa(person.short_name)
                    FlatOptionRow(
                        label = person.short_name,
                        sublabel = "IN +",
                        sublabelColor = MaterialTheme.colorScheme.primary,
                        selected = selected == opt,
                        enabled = !isAssigning,
                        onClick = { selected = opt }
                    )
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Other",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(
                    listOf(
                        TransactionRole.TILL_TRANSFER_OUT,
                        TransactionRole.TILL_TRANSFER_IN,
                        TransactionRole.WITHDRAWAL,
                        TransactionRole.REVERSAL,
                        TransactionRole.DUPLICATE
                    )
                ) { role ->
                    val opt = AssignOption.Role(role)
                    FlatOptionRow(
                        label = role.displayName(),
                        sublabel = role.directionLabel(),
                        sublabelColor = if (role.directionLabel().startsWith("IN"))
                            MaterialTheme.colorScheme.primary
                        else if (role.directionLabel().startsWith("OUT"))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.outline,
                        selected = selected == opt,
                        enabled = !isAssigning,
                        onClick = { selected = opt }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (val s = selected) {
                        is AssignOption.Csa  -> onConfirm(TransactionRole.CUSTOMER_RECEIPT, s.name)
                        is AssignOption.Role -> onConfirm(s.role, null)
                        null -> {}
                    }
                },
                // Disabled while assigning or nothing selected
                enabled = selected != null && !isAssigning
            ) {
                if (isAssigning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isAssigning) "Saving..." else "Assign")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAssigning
            ) { Text("Cancel") }
        }
    )
}

@Composable
fun EditAssignmentDialog(
    transaction: Transaction,
    persons: List<Person>,
    onDismiss: () -> Unit,
    onConfirm: (role: TransactionRole, personName: String?) -> Unit,
    onUnassign: () -> Unit
) {
    var selected by remember { mutableStateOf<AssignOption?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Assignment") },
        text = {
            Column {
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

                Text(
                    "CSA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                persons.filter { it.is_active }.forEach { person ->
                    val opt = AssignOption.Csa(person.short_name)
                    FlatOptionRow(
                        label = person.short_name,
                        sublabel = "IN +",
                        sublabelColor = MaterialTheme.colorScheme.primary,
                        selected = selected == opt,
                        enabled = true,
                        onClick = { selected = opt }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Other",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                listOf(
                    TransactionRole.TILL_TRANSFER_OUT,
                    TransactionRole.TILL_TRANSFER_IN,
                    TransactionRole.WITHDRAWAL,
                    TransactionRole.REVERSAL,
                    TransactionRole.DUPLICATE
                ).forEach { role ->
                    val opt = AssignOption.Role(role)
                    FlatOptionRow(
                        label = role.displayName(),
                        sublabel = role.directionLabel(),
                        sublabelColor = if (role.directionLabel().startsWith("IN"))
                            MaterialTheme.colorScheme.primary
                        else if (role.directionLabel().startsWith("OUT"))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.outline,
                        selected = selected == opt,
                        enabled = true,
                        onClick = { selected = opt }
                    )
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
                ) { Text("Unassign") }
                Button(
                    onClick = {
                        when (val s = selected) {
                            is AssignOption.Csa  -> onConfirm(TransactionRole.CUSTOMER_RECEIPT, s.name)
                            is AssignOption.Role -> onConfirm(s.role, null)
                            null -> {}
                        }
                    },
                    enabled = selected != null
                ) { Text("Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FlatOptionRow(
    label: String,
    sublabel: String,
    sublabelColor: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) sublabelColor else sublabelColor.copy(alpha = 0.4f)
        )
    }
}

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

private fun formatAmount(amount: Double): String =
    "Ksh ${String.format("%,.0f", amount)}"