package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "shift_assignments",
    foreignKeys = [
        ForeignKey(
            entity = Shift::class,
            parentColumns = ["shift_id"],
            childColumns = ["shift_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("shift_id")]
)
data class ShiftAssignment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val shift_id: Long,
    val person_name: String,
    val role: String? = null,
    val created_at: Long = System.currentTimeMillis()
)