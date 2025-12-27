package com.githow.links.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {

        // 1. UPDATE raw_sms TABLE
        database.execSQL("""
            ALTER TABLE raw_sms 
            ADD COLUMN parse_status TEXT NOT NULL DEFAULT 'UNPROCESSED'
        """)

        database.execSQL("""
            ALTER TABLE raw_sms 
            ADD COLUMN parse_error_message TEXT
        """)

        database.execSQL("""
            ALTER TABLE raw_sms 
            ADD COLUMN synced_to_webhook INTEGER NOT NULL DEFAULT 0
        """)

        database.execSQL("""
            ALTER TABLE raw_sms 
            ADD COLUMN webhook_sync_timestamp INTEGER
        """)

        database.execSQL("""
            ALTER TABLE raw_sms 
            ADD COLUMN webhook_sync_attempts INTEGER NOT NULL DEFAULT 0
        """)

        database.execSQL("""
            ALTER TABLE raw_sms 
            ADD COLUMN webhook_sync_error TEXT
        """)

        database.execSQL("""
            UPDATE raw_sms 
            SET parse_status = CASE 
                WHEN parsed = 1 THEN 'PARSED_SUCCESS'
                WHEN error_message IS NOT NULL THEN 'PARSE_ERROR'
                ELSE 'UNPROCESSED'
            END
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_raw_sms_parse_status 
            ON raw_sms(parse_status)
        """)

        // 2. CREATE manual_review_queue TABLE
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS manual_review_queue (
                raw_sms_id INTEGER PRIMARY KEY NOT NULL,
                raw_message TEXT NOT NULL,
                received_timestamp INTEGER NOT NULL,
                extracted_code TEXT,
                extracted_amount REAL,
                extracted_sender TEXT,
                extracted_phone TEXT,
                manual_code TEXT,
                manual_amount REAL,
                manual_sender_name TEXT,
                manual_sender_phone TEXT,
                manual_transaction_time INTEGER,
                manual_transaction_type TEXT,
                manual_is_transfer INTEGER NOT NULL DEFAULT 0,
                manual_paybill_number TEXT,
                manual_business_name TEXT,
                review_status TEXT NOT NULL DEFAULT 'PENDING',
                reviewed_by TEXT,
                reviewed_timestamp INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                FOREIGN KEY(raw_sms_id) REFERENCES raw_sms(id) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_manual_review_queue_raw_sms_id 
            ON manual_review_queue(raw_sms_id)
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_manual_review_queue_review_status 
            ON manual_review_queue(review_status)
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_manual_review_queue_received_timestamp 
            ON manual_review_queue(received_timestamp)
        """)

        // 3. CREATE users TABLE
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS users (
                username TEXT PRIMARY KEY NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                role TEXT NOT NULL,
                full_name TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                last_login INTEGER,
                created_by TEXT
            )
        """)

        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_users_username 
            ON users(username)
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_users_role 
            ON users(role)
        """)

        // 4. UPDATE transactions TABLE
        database.execSQL("""
            ALTER TABLE transactions 
            ADD COLUMN entry_source TEXT NOT NULL DEFAULT 'AUTO_PARSED'
        """)

        database.execSQL("""
            ALTER TABLE transactions 
            ADD COLUMN raw_sms_id INTEGER
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_transactions_raw_sms_id 
            ON transactions(raw_sms_id)
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_transactions_entry_source 
            ON transactions(entry_source)
        """)

        database.execSQL("""
            UPDATE transactions 
            SET raw_sms_id = (
                SELECT id FROM raw_sms 
                WHERE raw_sms.transaction_id = transactions.id 
                LIMIT 1
            )
            WHERE EXISTS (
                SELECT 1 FROM raw_sms 
                WHERE raw_sms.transaction_id = transactions.id
            )
        """)

        // 5. UPDATE shifts TABLE
        database.execSQL("""
            ALTER TABLE shifts 
            ADD COLUMN closed_by TEXT
        """)

        database.execSQL("""
            ALTER TABLE shifts 
            ADD COLUMN closed_timestamp INTEGER
        """)

        database.execSQL("""
            ALTER TABLE shifts 
            ADD COLUMN closure_notes TEXT
        """)

        // 6. MIGRATE manually_reviewed FLAG
        database.execSQL("""
            INSERT INTO manual_review_queue (
                raw_sms_id,
                raw_message,
                received_timestamp,
                review_status
            )
            SELECT 
                id,
                message_body,
                received_at,
                'COMPLETED'
            FROM raw_sms
            WHERE manually_reviewed = 1
        """)

        database.execSQL("""
            UPDATE raw_sms 
            SET parse_status = 'MANUALLY_ENTERED'
            WHERE manually_reviewed = 1
        """)

        // 7. POPULATE manual review queue
        database.execSQL("""
            INSERT OR IGNORE INTO manual_review_queue (
                raw_sms_id,
                raw_message,
                received_timestamp,
                extracted_code,
                review_status
            )
            SELECT 
                id,
                message_body,
                received_at,
                mpesa_code,
                'PENDING'
            FROM raw_sms
            WHERE parse_status = 'PARSE_ERROR'
            AND id NOT IN (SELECT raw_sms_id FROM manual_review_queue)
        """)
    }
}