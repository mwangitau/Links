package com.githow.links.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githow.links.data.entity.ManualReviewQueue
import com.githow.links.data.entity.TransactionType
import java.text.SimpleDateFormat
import java.util.*

// ── helpers ──────────────────────────────────────────────────────────────────

/** Parse the date and time that M-PESA embeds in the SMS body.
 *  Looks for "on D/M/YY at H:MM AM/PM" and converts to a Long timestamp.
 *  Returns null when nothing is found so the form falls back to received_timestamp. */
private fun extractSmsTransactionTime(smsBody: String): Long? {
    return try {
        val dateRegex = """[.\s]on\s+(\d{1,2}/\d{1,2}/\d{2,4})""".toRegex(RegexOption.IGNORE_CASE)
        val timeRegex = """at\s+(\d{1,2}:\d{1,2})\s*([AP]M)""".toRegex(RegexOption.IGNORE_CASE)
        val date = dateRegex.find(smsBody)?.groupValues?.get(1) ?: return null
        val timeMatch = timeRegex.find(smsBody) ?: return null
        val time = "${timeMatch.groupValues[1]} ${timeMatch.groupValues[2]}"
        val sdf = SimpleDateFormat("d/M/yy h:mm a", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
        sdf.parse("$date $time")?.time
    } catch (e: Exception) {
        null
    }
}

/** Format a Long timestamp to a human-readable date string for display. */
private fun formatTimestampForDisplay(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
    return sdf.format(Date(timestamp))
}

/** Format a Long timestamp to HH:MM for display. */
private fun formatTimeForDisplay(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
    return sdf.format(Date(timestamp))
}

/** Combine a date string "dd MMM yyyy" and time string "HH:mm" into a Long timestamp. */
private fun combineToTimestamp(dateStr: String, timeStr: String): Long? {
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
        sdf.parse("$dateStr $timeStr")?.time
    } catch (e: Exception) {
        null
    }
}

/**
 * ManualEntryCard - Expandable card for manual M-PESA entry
 *
 * Shows:
 * 1. Original unparsed SMS (red background)
 * 2. Auto-extracted fields (if any)
 * 3. Expandable full entry form
 * 4. Submit button (triggers password dialog)
 */
@Composable
fun ManualEntryCard(
    item: ManualReviewQueue,
    onSubmit: (ManualEntryData) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // ── Determine best starting timestamp ──────────────────────────────────
    // Prefer the real M-PESA transaction time embedded in the SMS text.
    // Fall back to the time the SMS arrived on the phone.
    val bestTimestamp = remember(item.raw_message) {
        extractSmsTransactionTime(item.raw_message) ?: item.received_timestamp
    }

    // Form state
    var mpesaCode by remember { mutableStateOf(item.extracted_code ?: "") }
    var amount by remember { mutableStateOf(item.extracted_amount?.toString() ?: "") }
    var senderName by remember { mutableStateOf(item.extracted_sender ?: "") }
    var senderPhone by remember { mutableStateOf(item.extracted_phone ?: "") }
    var transactionType by remember { mutableStateOf(TransactionType.RECEIVED) }
    var isTransfer by remember { mutableStateOf(false) }
    var paybillNumber by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }

    // Editable date and time — initialised from the SMS-embedded timestamp
    var txnDateDisplay by remember { mutableStateOf(formatTimestampForDisplay(bestTimestamp)) }
    var txnTimeDisplay by remember { mutableStateOf(formatTimeForDisplay(bestTimestamp)) }
    var txnDateError by remember { mutableStateOf(false) }
    var txnTimeError by remember { mutableStateOf(false) }

    // Resolved timestamp — recomputes whenever the supervisor edits the date or time fields
    val resolvedTimestamp = combineToTimestamp(txnDateDisplay, txnTimeDisplay) ?: bestTimestamp

    // Validation
    val isFormValid = mpesaCode.isNotBlank() &&
            mpesaCode.length == 10 &&
            amount.toDoubleOrNull() != null &&
            (amount.toDoubleOrNull() ?: 0.0) > 0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Parse Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "UNPARSED SMS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = formatDateTime(item.received_timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Divider()

            // Original SMS (always visible, red text)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            ) {
                Text(
                    text = item.raw_message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            // Auto-extracted fields preview (if any)
            if (item.extracted_code != null || item.extracted_amount != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item.extracted_code?.let { code ->
                        InfoChip(
                            label = "Code",
                            value = code,
                            icon = Icons.Default.Info
                        )
                    }

                    item.extracted_amount?.let { amt ->
                        InfoChip(
                            label = "Amount",
                            value = "KSh ${String.format("%,.0f", amt)}",
                            icon = Icons.Default.Info
                        )
                    }
                }
            }

            Divider()

            // Expand/Collapse button
            Button(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (expanded) "Hide Entry Form" else "Enter Transaction Details")
            }

            // Expandable manual entry form
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Divider()

                    Text(
                        text = "MANUAL TRANSACTION ENTRY",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    // M-PESA Code
                    OutlinedTextField(
                        value = mpesaCode,
                        onValueChange = {
                            if (it.length <= 10) {
                                mpesaCode = it.uppercase()
                            }
                        },
                        label = { Text("M-PESA Code *") },
                        placeholder = { Text("e.g., SRH1234567") },
                        supportingText = {
                            Text(
                                if (item.extracted_code != null) {
                                    "Auto-detected: ${item.extracted_code}"
                                } else {
                                    "Required - 10 characters"
                                }
                            )
                        },
                        isError = mpesaCode.isNotBlank() && mpesaCode.length != 10,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Amount
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (KSh) *") },
                        placeholder = { Text("e.g., 1500.00") },
                        supportingText = {
                            Text(
                                if (item.extracted_amount != null) {
                                    "Auto-detected: ${item.extracted_amount}"
                                } else {
                                    "Required"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = amount.isNotBlank() && amount.toDoubleOrNull() == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sender Name
                    OutlinedTextField(
                        value = senderName,
                        onValueChange = { senderName = it },
                        label = { Text("Sender Name") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sender Phone
                    OutlinedTextField(
                        value = senderPhone,
                        onValueChange = { senderPhone = it },
                        label = { Text("Sender Phone") },
                        placeholder = { Text("254... or 07...") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ── Transaction Date & Time ───────────────────────────────
                    Text(
                        text = "Transaction Date & Time",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // Info chip — shows where the time was sourced from
                    val timeSource = if (extractSmsTransactionTime(item.raw_message) != null)
                        "Auto-read from SMS text" else "Defaulted to SMS arrival time — please verify"
                    val timeSourceColor = if (extractSmsTransactionTime(item.raw_message) != null)
                        Color(0xFF4CAF50) else Color(0xFFFF9800)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = timeSourceColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = timeSource,
                            style = MaterialTheme.typography.bodySmall,
                            color = timeSourceColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date field — format: dd MMM yyyy e.g. "04 Jan 2026"
                        OutlinedTextField(
                            value = txnDateDisplay,
                            onValueChange = {
                                txnDateDisplay = it
                                txnDateError = combineToTimestamp(it, txnTimeDisplay) == null
                            },
                            label = { Text("Date") },
                            placeholder = { Text("04 Jan 2026") },
                            isError = txnDateError,
                            supportingText = {
                                if (txnDateError) Text("Use: dd MMM yyyy")
                                else Text("dd MMM yyyy")
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            }
                        )

                        // Time field — format: HH:mm e.g. "14:35"
                        OutlinedTextField(
                            value = txnTimeDisplay,
                            onValueChange = {
                                txnTimeDisplay = it
                                txnTimeError = combineToTimestamp(txnDateDisplay, it) == null
                            },
                            label = { Text("Time") },
                            placeholder = { Text("14:35") },
                            isError = txnTimeError,
                            supportingText = {
                                if (txnTimeError) Text("Use: HH:mm")
                                else Text("24-hr HH:mm")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                    // ─────────────────────────────────────────────────────────
                    Text(
                        text = "Transaction Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = transactionType == TransactionType.RECEIVED,
                            onClick = { transactionType = TransactionType.RECEIVED },
                            label = { Text("Received") }
                        )
                        FilterChip(
                            selected = transactionType == TransactionType.PAYBILL,
                            onClick = { transactionType = TransactionType.PAYBILL },
                            label = { Text("Paybill") }
                        )
                        FilterChip(
                            selected = transactionType == TransactionType.TILL,
                            onClick = { transactionType = TransactionType.TILL },
                            label = { Text("Till") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = transactionType == TransactionType.SENT,
                            onClick = { transactionType = TransactionType.SENT },
                            label = { Text("Sent") }
                        )
                        FilterChip(
                            selected = transactionType == TransactionType.WITHDRAW,
                            onClick = { transactionType = TransactionType.WITHDRAW },
                            label = { Text("Withdraw") }
                        )
                        FilterChip(
                            selected = transactionType == TransactionType.DEPOSIT,
                            onClick = { transactionType = TransactionType.DEPOSIT },
                            label = { Text("Deposit") }
                        )
                    }

                    // Transfer flag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isTransfer,
                            onCheckedChange = { isTransfer = it }
                        )
                        Text("Mark as Internal Transfer (Sent SMS)")
                    }

                    // Paybill/Business fields (conditional)
                    if (transactionType == TransactionType.PAYBILL ||
                        transactionType == TransactionType.TILL) {

                        OutlinedTextField(
                            value = paybillNumber,
                            onValueChange = { paybillNumber = it },
                            label = { Text("Paybill/Till Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Business Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Divider()

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = onSkip
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Skip This SMS")
                        }

                        Button(
                            onClick = {
                                val data = ManualEntryData(
                                    mpesaCode = mpesaCode,
                                    amount = amount.toDouble(),
                                    senderName = senderName.ifBlank { null },
                                    senderPhone = senderPhone.ifBlank { null },
                                    transactionType = transactionType,
                                    isTransfer = isTransfer,
                                    transactionTime = resolvedTimestamp,
                                    paybillNumber = paybillNumber.ifBlank { null },
                                    businessName = businessName.ifBlank { null }
                                )
                                onSubmit(data)
                            },
                            enabled = isFormValid && !txnDateError && !txnTimeError
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Submit Entry")
                        }
                    }

                    if (!isFormValid || txnDateError || txnTimeError) {
                        Text(
                            text = when {
                                txnDateError || txnTimeError -> "⚠️ Fix the date/time fields (dd MMM yyyy  HH:mm)"
                                else -> "⚠️ M-PESA Code (10 chars) and Amount are required"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Info chip for auto-extracted fields
 */
@Composable
private fun InfoChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF4CAF50)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = Color(0xFF4CAF50).copy(alpha = 0.7f)
                )
                Text(
                    text = value,
                    fontSize = 11.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Data class for manual entry
 */
data class ManualEntryData(
    val mpesaCode: String,
    val amount: Double,
    val senderName: String?,
    val senderPhone: String?,
    val transactionType: TransactionType,
    val isTransfer: Boolean,
    val transactionTime: Long,
    val paybillNumber: String? = null,
    val businessName: String? = null
)

/**
 * Helper to format date/time
 */
private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US)
    return sdf.format(Date(timestamp))
}