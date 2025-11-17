package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "raw_sms",
    indices = [Index("received_at")]
)
data class RawSms(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // SMS content (ALWAYS captured first)
    val sender: String,
    val message_body: String,
    val received_at: Long = System.currentTimeMillis(),

    // Parsing status (filled later)
    val parsed: Boolean = false,
    val mpesa_code: String? = null,
    val amount: Double? = null,
    val transaction_id: Long? = null,
    val shift_id: Long? = null,

    // Error tracking
    val error_message: String? = null,
    val parse_attempts: Int = 0,

    // Flags
    val is_duplicate: Boolean = false,
    val manually_reviewed: Boolean = false
)
