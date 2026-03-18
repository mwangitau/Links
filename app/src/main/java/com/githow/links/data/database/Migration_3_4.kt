package com.githow.links.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 3 → 4
 *
 * Adds supabase_synced tracking to transactions table.
 * Enables the offline retry queue — transactions that fail to sync
 * are flagged and retried automatically when network returns.
 *
 * NOTE: raw_sms already has synced_to_webhook from migration 2→3.
 * We reuse that existing column — no new columns needed on raw_sms.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {

        // Add supabase sync tracking to transactions
        database.execSQL("""
            ALTER TABLE transactions 
            ADD COLUMN supabase_synced INTEGER NOT NULL DEFAULT 0
        """)

        database.execSQL("""
            ALTER TABLE transactions 
            ADD COLUMN supabase_sync_attempts INTEGER NOT NULL DEFAULT 0
        """)

        database.execSQL("""
            ALTER TABLE transactions 
            ADD COLUMN supabase_sync_error TEXT
        """)

        // Index for fast unsynced transactions query only
        // DO NOT add index_raw_sms_synced_to_webhook here —
        // it is not declared on the RawSms entity and causes a Room schema mismatch
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_transactions_supabase_synced 
            ON transactions(supabase_synced)
        """)
    }
}