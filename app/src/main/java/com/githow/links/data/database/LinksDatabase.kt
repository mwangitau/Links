package com.githow.links.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.githow.links.data.dao.*
import com.githow.links.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * LinksDatabase - Main database for LINKS app
 *
 * V3.0 Changes:
 * - Added ManualReviewQueue table
 * - Added User table
 * - Updated RawSms with new fields
 * - Updated Transaction with entry_source
 * - Updated Shift with supervisor closure fields
 *
 * Database version: 3
 */
@Database(
    entities = [
        RawSms::class,
        Transaction::class,
        Shift::class,
        Person::class,
        ShiftAssignment::class,
        ManualReviewQueue::class,  // NEW in v3.0
        User::class                 // NEW in v3.0
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LinksDatabase : RoomDatabase() {

    // ============================================
    // DAOs
    // ============================================

    abstract fun rawSmsDao(): RawSmsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun shiftDao(): ShiftDao
    abstract fun personDao(): PersonDao
    abstract fun manualReviewQueueDao(): ManualReviewQueueDao  // NEW in v3.0
    abstract fun userDao(): UserDao                            // NEW in v3.0

    companion object {
        @Volatile
        private var INSTANCE: LinksDatabase? = null

        private const val DATABASE_NAME = "links_database"

        /**
         * Get database instance (singleton)
         */
        fun getDatabase(context: Context): LinksDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LinksDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback(context))
                    .addMigrations(MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * For testing: create in-memory database
         */
        fun createInMemoryDatabase(context: Context): LinksDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                LinksDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }

    /**
     * Database callback for initialization
     */
    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            // On first creation, seed the database
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    seedDatabase(database, context)
                }
            }
        }

        /**
         * Seed database with initial data
         */
        private suspend fun seedDatabase(database: LinksDatabase, context: Context) {
            try {
                // Create default supervisor account
                // Password: "admin123" (user should change on first login)
                val authService = com.githow.links.service.AuthenticationService(
                    database.userDao(),
                    context
                )

                // This will only create if doesn't exist
                authService.createDefaultSupervisor("admin123")

                android.util.Log.d("LinksDatabase", "✅ Default supervisor created")

            } catch (e: Exception) {
                android.util.Log.e("LinksDatabase", "❌ Error seeding database: ${e.message}", e)
            }
        }
    }
}

/**
 * Type converters for Room
 */
class Converters {
    // Add any type converters here if needed
    // For example: Date to Long, Enum to String, etc.

    @androidx.room.TypeConverter
    fun fromParseStatus(value: ParseStatus): String {
        return value.name
    }

    @androidx.room.TypeConverter
    fun toParseStatus(value: String): ParseStatus {
        return ParseStatus.valueOf(value)
    }

    @androidx.room.TypeConverter
    fun fromReviewStatus(value: ReviewStatus): String {
        return value.name
    }

    @androidx.room.TypeConverter
    fun toReviewStatus(value: String): ReviewStatus {
        return ReviewStatus.valueOf(value)
    }

    @androidx.room.TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @androidx.room.TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @androidx.room.TypeConverter
    fun fromUserRole(value: UserRole): String {
        return value.name
    }

    @androidx.room.TypeConverter
    fun toUserRole(value: String): UserRole {
        return UserRole.valueOf(value)
    }

    @androidx.room.TypeConverter
    fun fromEntrySource(value: EntrySource): String {
        return value.name
    }

    @androidx.room.TypeConverter
    fun toEntrySource(value: String): EntrySource {
        return EntrySource.valueOf(value)
    }
}