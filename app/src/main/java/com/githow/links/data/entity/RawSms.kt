package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "raw_sms",
    indices = [
        Index("received_timestamp"),  // Changed from received_at
        Index("mpesa_code"),
        Index("parse_status")
    ]
)
data class RawSms(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val message_body: String,
    val received_timestamp: Long,  // ✅ CHANGED from received_at
    val mpesa_code: String? = null,
    val extracted_amount: Double? = null,  // ✅ ADDED - This was missing!
    val parse_status: ParseStatus = ParseStatus.UNPROCESSED,
    val parse_error_message: String? = null,
    val parse_attempts: Int = 0,
    val transaction_id: Long? = null,
    val is_duplicate: Boolean = false,
    val created_at: Long,  // ✅ ADDED - This was missing!
    val reviewed_by: String? = null,  // ✅ ADDED - For manual review
    val synced_to_webhook: Boolean = false,
    val webhook_sync_timestamp: Long? = null,
    val webhook_sync_attempts: Int = 0,
    val webhook_sync_error: String? = null
)

enum class ParseStatus {
    UNPROCESSED,
    PARSED_SUCCESS,
    PARSE_ERROR,
    MANUAL_REVIEW,
    MANUALLY_ENTERED
}