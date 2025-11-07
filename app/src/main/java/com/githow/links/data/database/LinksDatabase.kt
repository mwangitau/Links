package com.githow.links.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.githow.links.data.dao.TransactionDao
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.ShiftAssignment

@Database(
    entities = [Transaction::class, Shift::class, ShiftAssignment::class],
    version = 1,
    exportSchema = false
)
abstract class LinksDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}