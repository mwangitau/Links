package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githow.links.viewmodel.ShiftViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftReportScreen(
    shiftId: Long,
    viewModel: ShiftViewModel,
    onNavigateBack: () -> Unit
) {
    val shift by viewModel.getShiftByIdLive(shiftId).observeAsState()
    val shiftTransactions by viewModel.getShiftTransactions(shiftId).observeAsState(emptyList())
    val persons by viewModel.persons.observeAsState(emptyList())

    // Calculate breakdown
    val breakdown = remember(shiftTransactions) {
        calculateBreakdown(shiftTransactions, persons)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shift Report") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Share/Export */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (shift == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                ShiftReportHeaderCard(shift = shift!!)
            }

            // Balance Summary
            item {
                BalanceSummaryCard(shift = shift!!)
            }

            // Collections Breakdown
            item {
                Text(
                    text = "Collections Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // CSA Collections with expandable details
            items(breakdown.csaCollections) { item ->
                ExpandableCollectionCard(
                    csaName = item.name,
                    totalAmount = item.amount,
                    transactionCount = item.count,
                    transactions = shiftTransactions.filter {
                        it.assigned_to == item.name &&
                                it.transaction_type == "RECEIVED" &&
                                it.transaction_category != "DEBT_PAID"
                    }
                )
            }

            // Debt Paid
            if (breakdown.debtPaid > 0) {
                item {
                    CollectionItemCard(
                        title = "Debt Paid",
                        amount = breakdown.debtPaid,
                        count = breakdown.debtCount,
                        icon = Icons.Default.Star
                    )
                }
            }

            // Transfers/Withdrawals
            if (breakdown.transfers > 0 || breakdown.withdrawals > 0) {
                item {
                    Text(
                        text = "Money Movements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (breakdown.transfers > 0) {
                    item {
                        CollectionItemCard(
                            title = "Transfers",
                            amount = breakdown.transfers,
                            count = breakdown.transferCount,
                            icon = Icons.Default.Call,
                            isOutflow = true
                        )
                    }
                }

                if (breakdown.withdrawals > 0) {
                    item {
                        CollectionItemCard(
                            title = "Withdrawals",
                            amount = breakdown.withdrawals,
                            count = breakdown.withdrawalCount,
                            icon = Icons.Default.Delete,
                            isOutflow = true
                        )
                    }
                }
            }

            // Reconciliation Card
            item {
                ReconciliationCard(
                    expected = shift!!.expected_total,
                    actual = shift!!.actual_total,
                    difference = shift!!.difference
                )
            }
        }
    }
}

@Composable
fun ShiftReportHeaderCard(shift: com.githow.links.data.entity.Shift) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                Column {
                    Text(
                        text = formatDate(shift.start_time),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${formatTime(shift.start_time)} - ${formatTime(shift.end_time ?: shift.start_time)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    color = if (shift.difference == 0.0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (shift.difference == 0.0) "BALANCED" else "DISCREPANCY",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (shift.difference == 0.0)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}

@Composable
fun BalanceSummaryCard(shift: com.githow.links.data.entity.Shift) {
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
                text = "Balance Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Opening Balance
            BalanceRow(
                label = "Opening Balance",
                amount = shift.open_balance,
                isHighlight = false
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Closing Balance
            BalanceRow(
                label = "Closing Balance",
                amount = shift.close_balance ?: shift.open_balance,
                isHighlight = false
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Total Collected
            BalanceRow(
                label = "Total Collected",
                amount = shift.actual_total,
                isHighlight = true
            )
        }
    }
}

@Composable
fun BalanceRow(
    label: String,
    amount: Double,
    isHighlight: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isHighlight)
                MaterialTheme.typography.titleSmall
            else
                MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = formatAmount(amount),
            style = if (isHighlight)
                MaterialTheme.typography.titleMedium
            else
                MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun CollectionItemCard(
    title: String,
    amount: Double,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isOutflow: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                Surface(
                    color = if (isOutflow)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = if (isOutflow)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$count transaction${if (count != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                text = formatAmount(amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOutflow)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ExpandableCollectionCard(
    csaName: String,
    totalAmount: Double,
    transactionCount: Int,
    transactions: List<com.githow.links.data.entity.Transaction>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header - CSA Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = csaName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$transactionCount transaction${if (transactionCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatAmount(totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Expanded - Individual Transactions
            if (expanded && transactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Individual Transactions",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Sort transactions by timestamp
                transactions.sortedBy { it.timestamp }.forEach { transaction ->
                    TransactionDetailRow(transaction = transaction)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun TransactionDetailRow(transaction: com.githow.links.data.entity.Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.mpesa_code,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = transaction.sender_name ?: transaction.business_name ?: "Unknown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = transaction.time_received,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatAmount(transaction.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // Verification badge
            if (transaction.status == "assigned" || transaction.status == "reconciled") {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Verified",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReconciliationCard(
    expected: Double,
    actual: Double,
    difference: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (difference == 0.0)
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
            Text(
                text = "Reconciliation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            BalanceRow("Expected Total", expected, false)
            Spacer(modifier = Modifier.height(8.dp))
            BalanceRow("Actual Total", actual, false)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Difference",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatAmount(difference),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (difference == 0.0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    if (difference == 0.0) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Balanced",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Discrepancy",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// Data classes for breakdown
data class CSACollection(
    val name: String,
    val amount: Double,
    val count: Int
)

data class ShiftBreakdown(
    val csaCollections: List<CSACollection>,
    val debtPaid: Double,
    val debtCount: Int,
    val transfers: Double,
    val transferCount: Int,
    val withdrawals: Double,
    val withdrawalCount: Int
)

// Helper function to calculate breakdown
private fun calculateBreakdown(
    transactions: List<com.githow.links.data.entity.Transaction>,
    persons: List<com.githow.links.data.entity.Person>
): ShiftBreakdown {
    val csaCollections = mutableListOf<CSACollection>()

    // Only include RECEIVED transactions that are assigned to CSAs (collections)
    persons.forEach { person ->
        val personTxs = transactions.filter {
            it.assigned_to == person.short_name &&
                    it.transaction_type == "RECEIVED" &&
                    it.transaction_category != "DEBT_PAID"  // Exclude debt from CSA totals
        }
        if (personTxs.isNotEmpty()) {
            csaCollections.add(
                CSACollection(
                    name = person.short_name,
                    amount = personTxs.sumOf { it.amount },
                    count = personTxs.size
                )
            )
        }
    }

    // Debt paid - RECEIVED transactions with DEBT_PAID category
    val debtTxs = transactions.filter {
        it.transaction_type == "RECEIVED" &&
                it.transaction_category == "DEBT_PAID"
    }
    val debtPaid = debtTxs.sumOf { it.amount }
    val debtCount = debtTxs.size

    // Transfers - Money sent to other M-PESA accounts (outflows)
    val transferTxs = transactions.filter {
        it.transaction_type == "SENT" &&
                it.transaction_category == "TRANSFER"
    }
    val transfers = transferTxs.sumOf { it.amount }
    val transferCount = transferTxs.size

    // Withdrawals - Money taken from agent (outflows)
    val withdrawalTxs = transactions.filter {
        it.transaction_type == "WITHDRAWN" ||
                it.transaction_category == "WITHDRAWAL"
    }
    val withdrawals = withdrawalTxs.sumOf { it.amount }
    val withdrawalCount = withdrawalTxs.size

    return ShiftBreakdown(
        csaCollections = csaCollections.sortedByDescending { it.amount },
        debtPaid = debtPaid,
        debtCount = debtCount,
        transfers = transfers,
        transferCount = transferCount,
        withdrawals = withdrawals,
        withdrawalCount = withdrawalCount
    )
}

// Helper functions
private fun formatAmount(amount: Double): String {
    return "Ksh ${String.format("%,.0f", amount)}"
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    return format.format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}
