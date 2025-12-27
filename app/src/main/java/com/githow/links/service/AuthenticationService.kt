package com.githow.links.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.githow.links.data.dao.UserDao
import com.githow.links.data.entity.User
import com.githow.links.data.entity.UserRole
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * AuthenticationService - Handles user authentication and password management
 *
 * Security:
 * - PBKDF2WithHmacSHA256 algorithm
 * - 10,000 iterations (OWASP recommended)
 * - 256-bit key length
 * - Random salt per user
 */
class AuthenticationService(
    private val userDao: UserDao,
    private val context: Context
) {

    companion object {
        private const val TAG = "AuthenticationService"
        private const val PREFS_NAME = "links_auth"
        private const val KEY_CURRENT_USER = "current_user"

        // PBKDF2 parameters
        private const val ITERATIONS = 10_000
        private const val KEY_LENGTH = 256
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================
    // AUTHENTICATION
    // ============================================

    /**
     * Authenticate supervisor with password only
     * Returns supervisor user if password is correct
     */
    suspend fun authenticateSupervisor(password: String): User? {
        try {
            val supervisor = userDao.getSupervisor()

            if (supervisor == null) {
                Log.w(TAG, "No supervisor account found")
                return null
            }

            val isValid = verifyPassword(password, supervisor.password_hash, supervisor.salt)

            if (isValid) {
                userDao.updateLastLogin(supervisor.username)
                setCurrentUser(supervisor)
                Log.d(TAG, "Supervisor authenticated successfully")
                return supervisor
            } else {
                Log.w(TAG, "Invalid supervisor password")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error authenticating supervisor: ${e.message}", e)
            return null
        }
    }

    /**
     * Authenticate user with username and password
     */
    suspend fun authenticateUser(username: String, password: String): User? {
        try {
            val user = userDao.getUserByUsername(username)

            if (user == null) {
                Log.w(TAG, "User not found: $username")
                return null
            }

            val isValid = verifyPassword(password, user.password_hash, user.salt)

            if (isValid) {
                userDao.updateLastLogin(username)
                setCurrentUser(user)
                Log.d(TAG, "User authenticated: $username")
                return user
            } else {
                Log.w(TAG, "Invalid password for user: $username")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error authenticating user: ${e.message}", e)
            return null
        }
    }

    /**
     * Verify password against hash
     */
    private fun verifyPassword(password: String, storedHash: String, salt: String): Boolean {
        val testHash = hashPassword(password, salt)
        return testHash == storedHash
    }

    // ============================================
    // PASSWORD HASHING
    // ============================================

    /**
     * Hash password using PBKDF2
     */
    fun hashPassword(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt.toByteArray(),
            ITERATIONS,
            KEY_LENGTH
        )

        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded

        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate random salt
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    // ============================================
    // USER MANAGEMENT
    // ============================================

    /**
     * Create new user
     */
    suspend fun createUser(
        username: String,
        password: String,
        fullName: String,
        role: UserRole,
        createdBy: String? = null
    ): User? {
        try {
            // Validate username
            if (username.length < 3 || username.length > 20) {
                throw IllegalArgumentException("Username must be 3-20 characters")
            }

            if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                throw IllegalArgumentException("Username can only contain letters, numbers, and underscore")
            }

            // Check if username exists
            if (userDao.usernameExists(username)) {
                throw IllegalArgumentException("Username already exists")
            }

            // Validate password
            if (password.length < 6) {
                throw IllegalArgumentException("Password must be at least 6 characters")
            }

            // Generate salt and hash password
            val salt = generateSalt()
            val passwordHash = hashPassword(password, salt)

            // Create user
            val user = User(
                username = username,
                password_hash = passwordHash,
                salt = salt,
                role = role,
                full_name = fullName,
                created_by = createdBy
            )

            userDao.insert(user)
            Log.d(TAG, "User created: $username ($role)")

            return user

        } catch (e: Exception) {
            Log.e(TAG, "Error creating user: ${e.message}", e)
            return null
        }
    }

    /**
     * Create default supervisor account
     * Called on first app launch
     */
    suspend fun createDefaultSupervisor(password: String = "admin123"): User? {
        try {
            // Check if supervisor already exists
            if (userDao.supervisorExists()) {
                Log.d(TAG, "Supervisor already exists")
                return userDao.getSupervisor()
            }

            return createUser(
                username = "supervisor",
                password = password,
                fullName = "Supervisor",
                role = UserRole.SUPERVISOR
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error creating default supervisor: ${e.message}", e)
            return null
        }
    }

    /**
     * Change user password
     */
    suspend fun changePassword(
        username: String,
        oldPassword: String,
        newPassword: String
    ): Boolean {
        try {
            val user = userDao.getUserByUsername(username)

            if (user == null) {
                Log.w(TAG, "User not found: $username")
                return false
            }

            // Verify old password
            if (!verifyPassword(oldPassword, user.password_hash, user.salt)) {
                Log.w(TAG, "Old password incorrect")
                return false
            }

            // Validate new password
            if (newPassword.length < 6) {
                throw IllegalArgumentException("New password must be at least 6 characters")
            }

            // Generate new salt and hash
            val newSalt = generateSalt()
            val newHash = hashPassword(newPassword, newSalt)

            // Update password
            userDao.changePassword(username, newHash, newSalt)
            Log.d(TAG, "Password changed for user: $username")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error changing password: ${e.message}", e)
            return false
        }
    }

    // ============================================
    // SESSION MANAGEMENT
    // ============================================

    /**
     * Get current logged-in user
     */
    fun getCurrentUser(): User? {
        val username = prefs.getString(KEY_CURRENT_USER, null) ?: return null

        // Note: This returns cached username only
        // In production, you might want to fetch from database
        return null // Implement if needed
    }

    /**
     * Set current user (save to session)
     */
    private fun setCurrentUser(user: User) {
        prefs.edit().putString(KEY_CURRENT_USER, user.username).apply()
    }

    /**
     * Logout current user
     */
    fun logout() {
        prefs.edit().remove(KEY_CURRENT_USER).apply()
        Log.d(TAG, "User logged out")
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return prefs.contains(KEY_CURRENT_USER)
    }

    /**
     * Check if current user is supervisor
     */
    suspend fun isCurrentUserSupervisor(): Boolean {
        val username = prefs.getString(KEY_CURRENT_USER, null) ?: return false
        val user = userDao.getUserByUsername(username) ?: return false
        return user.role == UserRole.SUPERVISOR
    }

    /**
     * Get current username
     */
    fun getCurrentUsername(): String? {
        return prefs.getString(KEY_CURRENT_USER, null)
    }
}