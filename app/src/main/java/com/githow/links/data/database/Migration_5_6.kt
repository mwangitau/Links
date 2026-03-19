package com.githow.links.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 5 -> 6
 *
 * Adds role-based transaction classification system:
 *   - role       : manager-assigned role (UNASSIGNED by default)
 *   - direction  : auto-derived from role (IN / OUT / NONE)
 *   - included_in_reconciliation : auto-derived from role
 *   - last_modified_at : timestamp of last assignment change
 *
 * Also adds frozen_at to shifts table to record when a shift
 * was time-locked (FROZEN status).
 *
 * Backfill logic:
 *   RECEIVED, DEPOSIT        -> CUSTOMER_RECEIPT / IN
 *   SENT                     -> TILL_TRANSFER_OUT / OUT
 *   WITHDRAW, AIRTIME,
 *   BILL_PAYMENT             -> WITHDRAWAL / OUT
 *   REVERSAL                 -> REVERSAL / OUT
 *   OTHER                    -> UNASSIGNED / NONE
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {

        // ── 1. transactions: add new columns ─────────────────────────────────
        database.execSQL(
            "ALTER TABLE transactions ADD COLUMN role TEXT NOT NULL DEFAULT 'UNASSIGNED'"
        )
        database.execSQL(
            "ALTER TABLE transactions ADD COLUMN direction TEXT NOT NULL DEFAULT 'NONE'"
        )
        database.execSQL(
            "ALTER TABLE transactions ADD COLUMN included_in_reconciliation INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE transactions ADD COLUMN last_modified_at INTEGER NOT NULL DEFAULT 0"
        )

        // ── 2. transactions: indexes ──────────────────────────────────────────
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transactions_role ON transactions(role)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transactions_direction ON transactions(direction)"
        )

        // ── 3. transactions: backfill role + direction from transaction_type ──
        // CUSTOMER_RECEIPT / IN
        database.execSQL("""
            UPDATE transactions
            SET role = 'CUSTOMER_RECEIPT',
                direction = 'IN',
                included_in_reconciliation = 1
            WHERE transaction_type IN ('RECEIVED','PAYBILL','TILL','BUY_GOODS','DEPOSIT')
        """)

        // TILL_TRANSFER_OUT / OUT
        database.execSQL("""
            UPDATE transactions
            SET role = 'TILL_TRANSFER_OUT',
                direction = 'OUT',
                included_in_reconciliation = 1
            WHERE transaction_type = 'SENT'
        """)

        // WITHDRAWAL / OUT
        database.execSQL("""
            UPDATE transactions
            SET role = 'WITHDRAWAL',
                direction = 'OUT',
                included_in_reconciliation = 1
            WHERE transaction_type IN ('WITHDRAW','AIRTIME','BILL_PAYMENT')
        """)

        // REVERSAL / OUT
        database.execSQL("""
            UPDATE transactions
            SET role = 'REVERSAL',
                direction = 'OUT',
                included_in_reconciliation = 1
            WHERE transaction_type = 'REVERSAL'
        """)

        // OTHER stays UNASSIGNED / NONE (already the default)

        // ── 4. shifts: add frozen_at column ──────────────────────────────────
        database.execSQL(
            "ALTER TABLE shifts ADD COLUMN frozen_at INTEGER DEFAULT NULL"
        )
    }
}