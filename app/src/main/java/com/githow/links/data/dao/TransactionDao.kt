package com.githow.links.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.Shift

@Dao
interface TransactionDao {

    // ============ TRANSACTION OPERATIONS ============

    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE mpesa_code = :code LIMIT 1")
    suspend fun getTransactionByCode(code: String): Transaction?

    @Query("SELECT COUNT(*) FROM transactions WHERE mpesa_code = :mpesaCode")
    suspend fun transactionExists(mpesaCode: String): Int

    @Query("SELECT * FROM transactions WHERE timestamp <= :timestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTransactionBefore(timestamp: Long): Transaction?

    // ============ GET TRANSACTIONS (Various queries) ============

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsLive(): LiveData<List<Transaction>>

    /**
     * Get all transactions for the current active or frozen shift (LiveData)
     */
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN shifts s ON t.shift_id = s.shift_id
        WHERE s.status IN ('ACTIVE', 'FROZEN')
        ORDER BY t.timestamp DESC
    """)
    fun getCurrentShiftTransactions(): LiveData<List<Transaction>>

    /**
     * Get all transactions for a specific shift (LiveData)
     */
    @Query("SELECT * FROM transactions WHERE shift_id = :shiftId ORDER BY timestamp DESC")
    fun getTransactionsByShiftId(shiftId: Long): LiveData<List<Transaction>>

    /**
     * Get all transactions for a specific shift (Direct - for suspend functions)
     */
    @Query("SELECT * FROM transactions WHERE shift_id = :shiftId ORDER BY timestamp DESC")
    suspend fun getTransactionsByShiftIdDirect(shiftId: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE date_received = :date ORDER BY timestamp DESC")
    suspend fun getTransactionsByDate(date: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE sender_name LIKE '%' || :query || '%' OR mpesa_code LIKE '%' || :query || '%'")
    suspend fun searchTransactions(query: String): List<Transaction>

    /**
     * Get unassigned transactions for current shift (ACTIVE or FROZEN)
     */
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN shifts s ON t.shift_id = s.shift_id
        WHERE s.status IN ('ACTIVE', 'FROZEN')
        AND (t.assigned_to IS NULL OR t.assigned_to = '')
        ORDER BY t.timestamp DESC
    """)
    fun getUnassignedTransactions(): LiveData<List<Transaction>>

    /**
     * Get transactions assigned to a specific person
     */
    @Query("SELECT * FROM transactions WHERE assigned_to = :personName ORDER BY timestamp DESC")
    fun getTransactionsByPerson(personName: String): LiveData<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    // ============ ASSIGNMENT OPERATIONS ============

    @Query("UPDATE transactions SET assigned_to = :personName, transaction_category = :category WHERE id = :transactionId")
    suspend fun assignTransaction(transactionId: Long, personName: String, category: String)

    @Query("UPDATE transactions SET assigned_to = NULL, transaction_category = NULL WHERE id = :transactionId")
    suspend fun unassignTransaction(transactionId: Long)

    // ── v6: Role-based assignment ─────────────────────────────────────────────

    /**
     * Assign a transaction: sets CSA, role, direction, and reconciliation flag
     * This is the main assignment call — use this instead of assignTransaction()
     */
    @Query("""
        UPDATE transactions
        SET assigned_to = :personName,
            role = :role,
            direction = :direction,
            included_in_reconciliation = :includedInReconciliation,
            last_modified_at = :modifiedAt,
            status = 'assigned'
        WHERE id = :transactionId
    """)
    suspend fun assignTransactionWithRole(
        transactionId: Long,
        personName: String?,
        role: String,
        direction: String,
        includedInReconciliation: Boolean,
        modifiedAt: Long = System.currentTimeMillis()
    )

    /**
     * Count UNASSIGNED transactions for a shift — used to gate shift close
     */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE shift_id = :shiftId
        AND role = 'UNASSIGNED'
    """)
    suspend fun countUnassigned(shiftId: Long): Int

    /**
     * Sum of UNASSIGNED amounts for a shift — shown in the blocking dialog
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE shift_id = :shiftId
        AND role = 'UNASSIGNED'
    """)
    suspend fun sumUnassigned(shiftId: Long): Double

    /**
     * Money Out for a shift = sum of all OUT direction transactions
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE shift_id = :shiftId
        AND direction = 'OUT'
    """)
    suspend fun getMoneyOut(shiftId: Long): Double

    /**
     * Grand Total for a shift = sum of all assigned IN transactions (excluding DUPLICATE)
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE shift_id = :shiftId
        AND direction = 'IN'
        AND assigned_to IS NOT NULL
        AND role != 'DUPLICATE'
    """)
    suspend fun getGrandTotal(shiftId: Long): Double

    /**
     * Per-CSA total for a shift
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE shift_id = :shiftId
        AND direction = 'IN'
        AND assigned_to = :personName
        AND role != 'DUPLICATE'
    """)
    suspend fun getCsaTotal(shiftId: Long, personName: String): Double

    /**
     * Get all UNASSIGNED transactions for a shift (for the blocking dialog list)
     */
    @Query("""
        SELECT * FROM transactions
        WHERE shift_id = :shiftId
        AND role = 'UNASSIGNED'
        ORDER BY timestamp ASC
    """)
    fun getUnassignedByShift(shiftId: Long): androidx.lifecycle.LiveData<List<Transaction>>

    // ============ SHIFT OPERATIONS ============

    @Insert
    suspend fun insertShift(shift: Shift): Long

    @Update
    suspend fun updateShift(shift: Shift)

    @Query("SELECT * FROM shifts WHERE shift_id = :id")
    suspend fun getShiftById(id: Long): Shift?

    // Get open shift (ACTIVE or FROZEN) - for SMS receiver to assign transactions
    @Query("SELECT * FROM shifts WHERE status IN ('ACTIVE', 'FROZEN') LIMIT 1")
    suspend fun getOpenShift(): Shift?

    // Get open shift (ACTIVE or FROZEN) - LiveData version
    @Query("SELECT * FROM shifts WHERE status IN ('ACTIVE', 'FROZEN') LIMIT 1")
    fun getOpenShiftLive(): LiveData<Shift?>

    @Query("SELECT * FROM shifts WHERE status = 'CLOSED' ORDER BY end_time DESC")
    fun getClosedShifts(): LiveData<List<Shift>>

    // ============ CALCULATIONS & TOTALS ============

    @Query("SELECT SUM(amount) FROM transactions WHERE date_received = :date AND transaction_type = 'RECEIVED'")
    suspend fun getTotalByDate(date: String): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE shift_id = :shiftId AND transaction_type = 'SENT'")
    suspend fun getTotalTransfersByShift(shiftId: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE shift_id = :shiftId AND transaction_type = 'WITHDRAW'")
    suspend fun getTotalWithdrawalsByShift(shiftId: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE shift_id = :shiftId AND assigned_to = :personName")
    suspend fun getTotalByShiftAndPerson(shiftId: Long, personName: String): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE shift_id = :shiftId AND transaction_category = :category")
    suspend fun getTotalByShiftAndCategory(shiftId: Long, category: String): Double?

    // ============ SUPABASE SYNC ============

    @Query("SELECT * FROM transactions WHERE supabase_synced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedTransactions(limit: Int = 100): List<Transaction>

    @Query("SELECT COUNT(*) FROM transactions WHERE supabase_synced = 0")
    suspend fun getUnsyncedTransactionCount(): Int

    @Query("""
        UPDATE transactions 
        SET supabase_synced = 1,
            supabase_sync_attempts = supabase_sync_attempts + 1,
            supabase_sync_error = NULL,
            synced_at = :timestamp
        WHERE id = :id
    """)
    suspend fun markTransactionSynced(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE transactions 
        SET supabase_sync_attempts = supabase_sync_attempts + 1,
            supabase_sync_error = :error
        WHERE id = :id
    """)
    suspend fun markTransactionSyncFailed(id: Long, error: String)
}