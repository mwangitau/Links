package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val person_id: Long = 0,

    val name: String,              // "John Mwangi"
    val short_name: String,        // "CSA 1" or "CSA 1 (John)"
    val is_active: Boolean = true,
    val display_order: Int = 0,    // For sorting in UI
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)