package com.githow.links.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "users",
    indices = [
        Index("username", unique = true),
        Index("role")
    ]
)
data class User(
    @PrimaryKey
    val username: String,
    val password_hash: String,
    val salt: String,
    val role: UserRole,
    val full_name: String,
    val is_active: Boolean = true,
    val created_at: Long = System.currentTimeMillis(),
    val last_login: Long? = null,
    val created_by: String? = null
)

enum class UserRole {
    CSA,
    SUPERVISOR
}

fun User.isSupervisor(): Boolean = role == UserRole.SUPERVISOR

fun User.canPerformAction(action: UserAction): Boolean {
    return when (action) {
        UserAction.VIEW_SMS -> true
        UserAction.VIEW_TRANSACTIONS -> true
        UserAction.ASSIGN_TO_SELF -> true
        UserAction.VIEW_SHIFTS -> true
        UserAction.MANUAL_ENTRY -> isSupervisor()
        UserAction.CLOSE_SHIFT -> isSupervisor()
        UserAction.ASSIGN_TO_OTHERS -> isSupervisor()
        UserAction.MANAGE_USERS -> isSupervisor()
        UserAction.VIEW_MANUAL_REVIEW -> isSupervisor()
    }
}

enum class UserAction {
    VIEW_SMS,
    VIEW_TRANSACTIONS,
    ASSIGN_TO_SELF,
    VIEW_SHIFTS,
    MANUAL_ENTRY,
    CLOSE_SHIFT,
    ASSIGN_TO_OTHERS,
    MANAGE_USERS,
    VIEW_MANUAL_REVIEW
}