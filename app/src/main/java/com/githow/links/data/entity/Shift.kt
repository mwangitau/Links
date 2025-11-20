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
    val cutoff_timestamp: Long? = null,  // ← NEW FIELD

    // Status: "ACTIVE" or "CLOSED"
    val status: String = "ACTIVE",

    // Reconciliation fields
    val total_received: Double = 0.0,
    val total_transfers: Double = 0.0,
    val total_withdrawals: Double = 0.0,

    // Calculated totals
    val expected_total: Double = 0.0,
    val actual_total: Double = 0.0,
    val difference: Double = 0.0,

    // Metadata
    val shift_name: String? = null,
    val notes: String? = null,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)