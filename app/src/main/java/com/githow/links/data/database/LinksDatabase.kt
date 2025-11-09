package com.githow.links.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.githow.links.data.dao.PersonDao
import com.githow.links.data.dao.TransactionDao
import com.githow.links.data.entity.Person
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.ShiftAssignment
import com.githow.links.data.entity.Transaction

@Database(
    entities = [
        Transaction::class,
        Shift::class,
        ShiftAssignment::class,
        Person::class  // NEW
    ],
    version = 2,  // Increment version
    exportSchema = false
)
abstract class LinksDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao  // NEW

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
                    .fallbackToDestructiveMigration()  // For development - recreates DB on version change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}