package com.githow.links.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.githow.links.data.dao.PersonDao
import com.githow.links.data.dao.ShiftDao
import com.githow.links.data.dao.TransactionDao
import com.githow.links.data.entity.Person
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.ShiftAssignment
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.RawSms
import com.githow.links.data.dao.RawSmsDao

@Database(
    entities = [
        Transaction::class,
        Shift::class,
        Person::class,
        ShiftAssignment::class,
        RawSms::class
    ],
    version = 5,  // ← UPDATED: New reconciliation fields
    exportSchema = false
)
abstract class LinksDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao
    abstract fun shiftDao(): ShiftDao
    abstract fun rawSmsDao(): RawSmsDao

    companion object {
        @Volatile
        private var INSTANCE: LinksDatabase? = null

        fun getDatabase(context: Context): LinksDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LinksDatabase::class.java,
                    "links_database"
                )
                    .fallbackToDestructiveMigration()  // Recreates DB on version change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}