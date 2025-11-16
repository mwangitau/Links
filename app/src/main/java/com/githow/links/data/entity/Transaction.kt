package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Shift::class,
            parentColumns = ["shift_id"],
            childColumns = ["shift_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("shift_id"), Index("mpesa_code")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Basic transaction info
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

    // Transaction type: RECEIVED, SENT, WITHDRAW
    val transaction_type: String,

    // NEW: Shift management fields
    val shift_id: Long? = null,
    val assigned_to: String? = null,  // "CSA 1 (John)", "Debt Paid", "Transfer", etc.

    // NEW: Transaction category for reconciliation
    val transaction_category: String? = null,  // "CSA", "DEBT_PAID", "TRANSFER", "WITHDRAWAL"

    // NEW: For hiding duplicate internal transfers
    val is_hidden: Boolean = false,  // true for internal "received" SMS
    val is_internal_transfer: Boolean = false,  // true if from own paybill

    // Status and sync
    val status: String = "pending",  // "pending", "assigned", "reconciled"
    val created_at: Long = System.currentTimeMillis(),
    val synced_at: Long? = null
)