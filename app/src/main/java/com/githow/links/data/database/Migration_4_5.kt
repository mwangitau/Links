package com.githow.links.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 4 → 5
 *
 * Ensures both indices created by migration 3→4 are present and
 * declared in their respective entities. Uses IF NOT EXISTS so it
 * is safe whether or not a previous migration already created them.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Match RawSms entity Index("synced_to_webhook")
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_raw_sms_synced_to_webhook 
            ON raw_sms(synced_to_webhook)
        """)
        // Match Transaction entity Index("supabase_synced")
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_transactions_supabase_synced 
            ON transactions(supabase_synced)
        """)
    }
}