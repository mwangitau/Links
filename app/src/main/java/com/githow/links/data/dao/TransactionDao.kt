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

    // ============ GET TRANSACTIONS (Various queries) ============

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsLive(): LiveData<List<Transaction>>

    /**
     * Get all transactions for the current active shift (LiveData)
     */
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN shifts s ON t.shift_id = s.shift_id
        WHERE s.status = 'ACTIVE'
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
     * Get unassigned transactions for current shift
     */
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN shifts s ON t.shift_id = s.shift_id
        WHERE s.status = 'ACTIVE' 
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

    // ============ SHIFT OPERATIONS ============

    @Insert
    suspend fun insertShift(shift: Shift): Long

    @Update
    suspend fun updateShift(shift: Shift)

    @Query("SELECT * FROM shifts WHERE shift_id = :id")
    suspend fun getShiftById(id: Long): Shift?

    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getOpenShift(): Shift?

    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' LIMIT 1")
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
}