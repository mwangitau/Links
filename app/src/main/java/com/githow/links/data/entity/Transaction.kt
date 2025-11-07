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
    val transaction_type: String,

    val shift_id: Long? = null,
    val assigned_to: String? = null,
    val status: String = "pending",
    val created_at: Long = System.currentTimeMillis(),
    val synced_at: Long? = null
)