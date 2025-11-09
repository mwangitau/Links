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
    val open_balance: Double,      // First transaction balance when shift opened
    val close_balance: Double? = null,  // Last transaction balance when shift closed

    // Status: "ACTIVE" or "CLOSED"
    val status: String = "ACTIVE",

    // Reconciliation fields
    val total_received: Double = 0.0,        // Sum of RECEIVED transactions
    val total_transfers: Double = 0.0,       // Sum of TRANSFER (SENT) transactions
    val total_withdrawals: Double = 0.0,     // Sum of WITHDRAWAL transactions

    // Calculated totals
    val expected_total: Double = 0.0,        // (close_balance + withdrawals) - open_balance
    val actual_total: Double = 0.0,          // Sum of all CSA assignments + debt paid
    val difference: Double = 0.0,            // expected_total - actual_total

    // Metadata
    val shift_name: String? = null,          // Optional: "Morning Shift", "9 Nov 2025"
    val notes: String? = null,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)