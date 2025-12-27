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
        Index("entry_source")
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
    val synced_at: Long? = null
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