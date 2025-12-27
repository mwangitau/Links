package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "manual_review_queue",
    foreignKeys = [
        ForeignKey(
            entity = RawSms::class,
            parentColumns = ["id"],
            childColumns = ["raw_sms_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("raw_sms_id"),
        Index("review_status"),
        Index("received_timestamp")
    ]
)
data class ManualReviewQueue(
    @PrimaryKey
    val raw_sms_id: Long,
    val raw_message: String,
    val received_timestamp: Long,
    val extracted_code: String? = null,
    val extracted_amount: Double? = null,
    val extracted_sender: String? = null,
    val extracted_phone: String? = null,
    val manual_code: String? = null,
    val manual_amount: Double? = null,
    val manual_sender_name: String? = null,
    val manual_sender_phone: String? = null,
    val manual_transaction_time: Long? = null,
    val manual_transaction_type: TransactionType? = null,
    val manual_is_transfer: Boolean = false,
    val manual_paybill_number: String? = null,
    val manual_business_name: String? = null,
    val review_status: ReviewStatus = ReviewStatus.PENDING,
    val reviewed_by: String? = null,
    val reviewed_timestamp: Long? = null,
    val notes: String? = null,
    val created_at: Long = System.currentTimeMillis()
)

enum class ReviewStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED
}

enum class TransactionType {
    RECEIVED,
    PAYBILL,
    TILL,
    SENT,
    WITHDRAW,
    DEPOSIT,
    AIRTIME,
    BILL_PAYMENT,
    BUY_GOODS,
    REVERSAL,
    OTHER
}