package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

// ─────────────────────────────────────────────────────────────────────────────
// Transaction Role — what role does this transaction play in reconciliation?
// Set by the manager on the assignment screen (default = UNASSIGNED on capture)
// ─────────────────────────────────────────────────────────────────────────────
enum class TransactionRole {
    CUSTOMER_RECEIPT,    // Money IN  — customer paid for fuel/services (→ CSA float)
    TILL_TRANSFER_IN,    // Money IN  — received from another till/paybill (→ CSA float)
    WITHDRAWAL,          // Money OUT — owner/manager withdrew cash
    REVERSAL,            // Money OUT — Safaricom reversed a transaction
    TILL_TRANSFER_OUT,   // Money OUT — sent to HQ or another paybill
    DUPLICATE,           // EXCLUDED  — second leg of a till-to-till transfer
    UNASSIGNED           // DEFAULT   — not yet reviewed; blocks shift close
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction Direction — auto-derived from role, never set manually
// ─────────────────────────────────────────────────────────────────────────────
enum class TransactionDirection {
    IN,   // CUSTOMER_RECEIPT, TILL_TRANSFER_IN
    OUT,  // WITHDRAWAL, REVERSAL, TILL_TRANSFER_OUT
    NONE  // DUPLICATE, UNASSIGNED
}

// ─────────────────────────────────────────────────────────────────────────────
// Derive direction from role — single source of truth
// ─────────────────────────────────────────────────────────────────────────────
fun TransactionRole.toDirection(): TransactionDirection = when (this) {
    TransactionRole.CUSTOMER_RECEIPT  -> TransactionDirection.IN
    TransactionRole.TILL_TRANSFER_IN  -> TransactionDirection.IN
    TransactionRole.WITHDRAWAL        -> TransactionDirection.OUT
    TransactionRole.REVERSAL          -> TransactionDirection.OUT
    TransactionRole.TILL_TRANSFER_OUT -> TransactionDirection.OUT
    TransactionRole.DUPLICATE         -> TransactionDirection.NONE
    TransactionRole.UNASSIGNED        -> TransactionDirection.NONE
}

// ─────────────────────────────────────────────────────────────────────────────
// Whether a role is included in reconciliation totals
// ─────────────────────────────────────────────────────────────────────────────
fun TransactionRole.includedInReconciliation(): Boolean = when (this) {
    TransactionRole.DUPLICATE  -> false
    TransactionRole.UNASSIGNED -> false
    else                       -> true
}

// Whether a role requires a CSA to be selected
fun TransactionRole.requiresCsa(): Boolean = when (this) {
    TransactionRole.CUSTOMER_RECEIPT  -> true
    TransactionRole.TILL_TRANSFER_IN  -> true
    TransactionRole.WITHDRAWAL        -> true
    TransactionRole.REVERSAL          -> true
    TransactionRole.TILL_TRANSFER_OUT -> true
    TransactionRole.DUPLICATE         -> false
    TransactionRole.UNASSIGNED        -> false
}

// ─────────────────────────────────────────────────────────────────────────────
// Parser helper — derive initial role from transaction_type on SMS capture
// Manager can override this later on the assignment screen
// ─────────────────────────────────────────────────────────────────────────────
fun transactionTypeToRole(transactionType: String): TransactionRole = when (transactionType) {
    "RECEIVED"     -> TransactionRole.CUSTOMER_RECEIPT
    "PAYBILL"      -> TransactionRole.CUSTOMER_RECEIPT
    "TILL"         -> TransactionRole.CUSTOMER_RECEIPT
    "BUY_GOODS"    -> TransactionRole.CUSTOMER_RECEIPT
    "DEPOSIT"      -> TransactionRole.CUSTOMER_RECEIPT
    "SENT"         -> TransactionRole.TILL_TRANSFER_OUT
    "WITHDRAW"     -> TransactionRole.WITHDRAWAL
    "AIRTIME"      -> TransactionRole.WITHDRAWAL
    "BILL_PAYMENT" -> TransactionRole.WITHDRAWAL
    "REVERSAL"     -> TransactionRole.REVERSAL
    else           -> TransactionRole.UNASSIGNED
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Shift::class,
            parentColumns = ["shift_id"],
            childColumns = ["shift_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RawSms::class,
            parentColumns = ["id"],
            childColumns = ["raw_sms_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("shift_id"),
        Index("mpesa_code", unique = true),
        Index("raw_sms_id"),
        Index("entry_source"),
        Index("supabase_synced"),
        Index("role"),        // Added in migration 5->6
        Index("direction")    // Added in migration 5->6
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val raw_sms_id: Long? = null,
    val mpesa_code: String,
    val amount: Double,
    val sender_phone: String?,
    val sender_name: String?,
    val paybill_number: String?,
    val business_name: String?,
    val timestamp: Long,
    val date_received: String,
    val time_received: String,
    val account_balance: Double,
    val transaction_cost: Double,
    val sms_body: String? = null,
    val transaction_type: String,
    val shift_id: Long? = null,
    val assigned_to: String? = null,
    val transaction_category: String? = null,
    val is_hidden: Boolean = false,
    val is_internal_transfer: Boolean = false,
    val entry_source: EntrySource = EntrySource.AUTO_PARSED,
    val status: String = "pending",
    val created_at: Long = System.currentTimeMillis(),
    val synced_at: Long? = null,
    val supabase_synced: Boolean = false,
    val supabase_sync_attempts: Int = 0,
    val supabase_sync_error: String? = null,

    // v6 fields
    val role: TransactionRole = TransactionRole.UNASSIGNED,
    val direction: TransactionDirection = TransactionDirection.NONE,
    val included_in_reconciliation: Boolean = false,
    val last_modified_at: Long = System.currentTimeMillis()
)

enum class EntrySource {
    AUTO_PARSED,
    MANUAL_SUPERVISOR
}

fun Transaction.isManuallyEntered(): Boolean {
    return entry_source == EntrySource.MANUAL_SUPERVISOR
}

fun Transaction.getEntrySourceDisplay(): String {
    return when (entry_source) {
        EntrySource.AUTO_PARSED -> "Auto"
        EntrySource.MANUAL_SUPERVISOR -> "Manual"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Apply a role and auto-derive direction + reconciliation flag in one call
// Use this everywhere instead of setting the three fields manually
// ─────────────────────────────────────────────────────────────────────────────
fun Transaction.withRole(newRole: TransactionRole): Transaction = copy(
    role = newRole,
    direction = newRole.toDirection(),
    included_in_reconciliation = newRole.includedInReconciliation(),
    last_modified_at = System.currentTimeMillis()
)