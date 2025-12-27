package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true)
    val shift_id: Long = 0,

    // Shift timing
    val start_time: Long,
    val end_time: Long? = null,

    // Balance tracking
    val open_balance: Double,
    val close_balance: Double? = null,
    val cutoff_timestamp: Long? = null,

    // Status: "ACTIVE", "FROZEN", or "CLOSED"
    val status: String = "ACTIVE",

    // ============================================
    // RECONCILIATION FIELDS (NEW FORMULA)
    // ============================================

    // 1. Net change in account
    val net_change: Double = 0.0,              // close_balance - open_balance

    // 2. Money sent out (transfers, withdrawals)
    val money_sent_out: Double = 0.0,          // Sum of SENT transactions

    // 3. Expected customer receipts
    val expected_receipts: Double = 0.0,       // net_change + money_sent_out

    // 4. Actual recorded receipts
    val actual_receipts: Double = 0.0,         // Sum of RECEIVED transactions

    // 5. Variance (what we're looking for)
    val variance: Double = 0.0,                // expected_receipts - actual_receipts

    // ============================================
    // OLD FIELDS (Kept for backward compatibility)
    // ============================================
    val total_received: Double = 0.0,
    val total_transfers: Double = 0.0,
    val total_withdrawals: Double = 0.0,
    val expected_total: Double = 0.0,
    val actual_total: Double = 0.0,
    val difference: Double = 0.0,

    // Metadata
    val shift_name: String? = null,
    val notes: String? = null,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)