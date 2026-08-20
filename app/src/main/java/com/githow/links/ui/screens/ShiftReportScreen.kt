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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.TransactionDirection
import com.githow.links.data.entity.TransactionRole
import com.githow.links.utils.ShiftPdfExporter
import com.githow.links.viewmodel.ShiftViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftReportScreen(
    shiftId: Long,
    viewModel: ShiftViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { LinksDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    val shift by database.shiftDao().getShiftByIdLive(shiftId).observeAsState()
    val shiftTransactions by database.transactionDao()
        .getTransactionsByShiftId(shiftId).observeAsState(emptyList())
    val persons by viewModel.persons.observeAsState(emptyList())

    val breakdown = remember(shiftTransactions) {
        calculateBreakdown(shiftTransactions, persons)
    }

    var isExporting by remember { mutableStateOf(false) }

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
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                shift?.let { s ->
                                    isExporting = true
                                    scope.launch {
                                        ShiftPdfExporter.exportAndShare(
                                            context = context,
                                            shift = s,
                                            transactions = shiftTransactions,
                                            stationName = com.githow.links.config.StationConfig.getStationName(context)
                                        )
                                        isExporting = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share PDF")
                        }
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
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ShiftReportHeaderCard(shift = shift!!) }
            item { BalanceSummaryCard(shift = shift!!) }

            // ── Customer Receipts (IN) ────────────────────────────────────
            item {
                Text(
                    text = "Customer Receipts (Money IN)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(breakdown.csaCollections) { item ->
                ExpandableCollectionCard(
                    csaName = item.name,
                    totalAmount = item.amount,
                    transactionCount = item.count,
                    transactions = shiftTransactions.filter {
                        it.assigned_to == item.name &&
                                it.direction == TransactionDirection.IN &&
                                it.role != TransactionRole.DUPLICATE
                    }
                )
            }

            // Till Transfer In — shown as a separate IN item if present
            if (breakdown.tillTransferIn > 0) {
                item {
                    CollectionItemCard(
                        title = "Till Transfer In",
                        amount = breakdown.tillTransferIn,
                        count = breakdown.tillTransferInCount,
                        icon = Icons.Default.CallReceived,
                        isOutflow = false
                    )
                }
            }

            // ── Transfers Out (Money OUT) ─────────────────────────────────
            if (breakdown.transfersOut > 0) {
                item {
                    Text(
                        text = "Transfers Out (Money OUT)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (breakdown.transfers > 0) {
                    item {
                        CollectionItemCard(
                            title = "Till Transfer Out",
                            amount = breakdown.transfers,
                            count = breakdown.transferCount,
                            icon = Icons.Default.Send,
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

                if (breakdown.reversals > 0) {
                    item {
                        CollectionItemCard(
                            title = "Reversals",
                            amount = breakdown.reversals,
                            count = breakdown.reversalCount,
                            icon = Icons.Default.Warning,
                            isOutflow = true
                        )
                    }
                }
            }

            // ── Duplicates excluded ───────────────────────────────────────
            if (breakdown.duplicateCount > 0) {
                item {
                    CollectionItemCard(
                        title = "Duplicates (Excluded)",
                        amount = breakdown.duplicateTotal,
                        count = breakdown.duplicateCount,
                        icon = Icons.Default.Info,
                        isOutflow = false
                    )
                }
            }

            // ── Reconciliation ────────────────────────────────────────────
            item {
                ReconciliationCard(
                    openingBalance = shift!!.open_balance,
                    closingBalance = shift!!.close_balance ?: 0.0,
                    transfersOut = shift!!.money_sent_out,
                    expectedFloat = shift!!.expected_receipts,
                    customerReceipts = shift!!.actual_receipts,
                    variance = shift!!.variance
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                    color = if (shift.variance == 0.0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (shift.variance == 0.0) "BALANCED" else "DISCREPANCY",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (shift.variance == 0.0)
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Balance Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            BalanceRow("Opening Balance", shift.open_balance, false)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BalanceRow("Closing Balance", shift.close_balance ?: shift.open_balance, false)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BalanceRow("Customer Receipts Collected", shift.actual_receipts, true)
        }
    }
}

@Composable
fun BalanceRow(label: String, amount: Double, isHighlight: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isHighlight) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = formatAmount(amount),
            style = if (isHighlight) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSecondaryContainer
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (isOutflow) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = if (isOutflow) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
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
                color = if (isOutflow) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
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

    Card(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                            contentDescription = null,
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

// ─────────────────────────────────────────────────────────────────────────────
// Reconciliation Card — updated to match new formula
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ReconciliationCard(
    openingBalance: Double,
    closingBalance: Double,
    transfersOut: Double,
    expectedFloat: Double,
    customerReceipts: Double,
    variance: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (variance == 0.0)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Reconciliation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Formula breakdown shown step by step
            BalanceRow("Closing Balance", closingBalance, false)
            BalanceRow("− Opening Balance", openingBalance, false)
            BalanceRow("+ Transfers Out (all money OUT)", transfersOut, false)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BalanceRow("= Expected Float", expectedFloat, true)

            Spacer(modifier = Modifier.height(8.dp))
            BalanceRow("Customer Receipts (all money IN)", customerReceipts, false)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Variance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatAmount(variance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (variance == 0.0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Icon(
                        if (variance == 0.0) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (variance == 0.0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            // Plain language explanation
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Formula: (Closing − Opening + Transfers Out) − Customer Receipts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Transfers Out = Till Transfer Out + Withdrawal + Reversal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Customer Receipts = Customer Receipt + Till Transfer In",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Breakdown calculation — uses direction, not transaction_type
// ─────────────────────────────────────────────────────────────────────────────
data class CSACollection(val name: String, val amount: Double, val count: Int)

data class ShiftBreakdown(
    val csaCollections: List<CSACollection>,
    val tillTransferIn: Double,
    val tillTransferInCount: Int,
    val transfers: Double,
    val transferCount: Int,
    val withdrawals: Double,
    val withdrawalCount: Int,
    val reversals: Double,
    val reversalCount: Int,
    val transfersOut: Double,  // total of all OUT
    val duplicateTotal: Double,
    val duplicateCount: Int
)

private fun calculateBreakdown(
    transactions: List<com.githow.links.data.entity.Transaction>,
    persons: List<com.githow.links.data.entity.Person>
): ShiftBreakdown {

    // CSA collections — CUSTOMER_RECEIPT assigned to a CSA
    val csaCollections = mutableListOf<CSACollection>()
    persons.forEach { person ->
        val personTxs = transactions.filter {
            it.assigned_to == person.short_name &&
                    it.role == TransactionRole.CUSTOMER_RECEIPT
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

    // Till Transfer In — direction IN but not CUSTOMER_RECEIPT
    val tillInTxs = transactions.filter { it.role == TransactionRole.TILL_TRANSFER_IN }

    // OUT roles broken down individually for the report
    val transferTxs = transactions.filter { it.role == TransactionRole.TILL_TRANSFER_OUT }
    val withdrawalTxs = transactions.filter { it.role == TransactionRole.WITHDRAWAL }
    val reversalTxs = transactions.filter { it.role == TransactionRole.REVERSAL }

    // Total OUT — all three combined
    val allOutTxs = transactions.filter { it.direction == TransactionDirection.OUT }

    // Duplicates
    val duplicateTxs = transactions.filter { it.role == TransactionRole.DUPLICATE }

    return ShiftBreakdown(
        csaCollections = csaCollections.sortedByDescending { it.amount },
        tillTransferIn = tillInTxs.sumOf { it.amount },
        tillTransferInCount = tillInTxs.size,
        transfers = transferTxs.sumOf { it.amount },
        transferCount = transferTxs.size,
        withdrawals = withdrawalTxs.sumOf { it.amount },
        withdrawalCount = withdrawalTxs.size,
        reversals = reversalTxs.sumOf { it.amount },
        reversalCount = reversalTxs.size,
        transfersOut = allOutTxs.sumOf { it.amount },
        duplicateTotal = duplicateTxs.sumOf { it.amount },
        duplicateCount = duplicateTxs.size
    )
}

private fun formatAmount(amount: Double): String = "Ksh ${String.format("%,.0f", amount)}"
private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))