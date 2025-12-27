package com.githow.links.data.dao

import androidx.room.*
import com.githow.links.data.entity.User
import com.githow.links.data.entity.UserRole
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username AND is_active = 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE role = 'SUPERVISOR' AND is_active = 1 LIMIT 1")
    suspend fun getSupervisor(): User?

    @Query("SELECT * FROM users WHERE role = 'SUPERVISOR' AND is_active = 1")
    fun getAllSupervisors(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE role = 'CSA' AND is_active = 1")
    fun getAllCSAs(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE is_active = 1 ORDER BY full_name ASC")
    fun getAllActiveUsers(): Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY created_at DESC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :username)")
    suspend fun usernameExists(username: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE role = 'SUPERVISOR' AND is_active = 1)")
    suspend fun supervisorExists(): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("UPDATE users SET is_active = 0 WHERE username = :username")
    suspend fun deactivateUser(username: String)

    @Query("UPDATE users SET is_active = 1 WHERE username = :username")
    suspend fun reactivateUser(username: String)

    @Query("UPDATE users SET last_login = :timestamp WHERE username = :username")
    suspend fun updateLastLogin(username: String, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE users 
        SET password_hash = :newPasswordHash, 
            salt = :newSalt 
        WHERE username = :username
    """)
    suspend fun changePassword(username: String, newPasswordHash: String, newSalt: String)

    @Query("UPDATE users SET role = :newRole WHERE username = :username")
    suspend fun changeUserRole(username: String, newRole: UserRole)

    @Query("SELECT COUNT(*) FROM users WHERE role = :role AND is_active = 1")
    suspend fun getUserCountByRole(role: UserRole): Int
}