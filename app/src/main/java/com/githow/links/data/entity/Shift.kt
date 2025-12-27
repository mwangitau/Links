package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true)
    val shift_id: Long = 0,
    val start_time: Long,
    val end_time: Long? = null,
    val open_balance: Double,
    val close_balance: Double? = null,
    val cutoff_timestamp: Long? = null,
    val status: String = "ACTIVE",
    val net_change: Double = 0.0,
    val money_sent_out: Double = 0.0,
    val expected_receipts: Double = 0.0,
    val actual_receipts: Double = 0.0,
    val variance: Double = 0.0,
    val total_received: Double = 0.0,
    val total_transfers: Double = 0.0,
    val total_withdrawals: Double = 0.0,
    val expected_total: Double = 0.0,
    val actual_total: Double = 0.0,
    val difference: Double = 0.0,
    val closed_by: String? = null,
    val closed_timestamp: Long? = null,
    val closure_notes: String? = null,
    val shift_name: String? = null,
    val notes: String? = null,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)

fun Shift.isActive(): Boolean = status == "ACTIVE"
fun Shift.isClosed(): Boolean = status == "CLOSED"
fun Shift.canBeClosed(): Boolean = status == "ACTIVE" || status == "FROZEN"

fun Shift.getDurationHours(): Double? {
    val endTime = end_time ?: return null
    val durationMillis = endTime - start_time
    return durationMillis / (1000.0 * 60 * 60)
}