package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true)
    val shift_id: Long = 0,

    val shift_name: String,
    val start_time: Long,
    val end_time: Long? = null,
    val status: String = "open",
    val created_at: Long = System.currentTimeMillis(),
    val synced_at: Long? = null
)