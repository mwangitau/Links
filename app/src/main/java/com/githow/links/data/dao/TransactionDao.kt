package com.githow.links.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.Shift

@Dao
interface TransactionDao {

    // ============ BASIC OPERATIONS ============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    // ============ GENERAL QUERIES ============

    // Get all transactions (visible only - excluding hidden)
    @Query("SELECT * FROM transactions WHERE is_hidden = 0 ORDER BY timestamp DESC")
    fun getAllTransactionsLive(): LiveData<List<Transaction>>

    // Get all transactions including hidden
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactions(): List<Transaction>

    // Get transaction by M-PESA code
    @Query("SELECT * FROM transactions WHERE mpesa_code = :code LIMIT 1")
    suspend fun getTransactionByCode(code: String): Transaction?

    // Search transactions (visible only)
    @Query("""
        SELECT * FROM transactions 
        WHERE is_hidden = 0 
          AND (sender_name LIKE :query 
           OR sender_phone LIKE :query 
           OR business_name LIKE :query
           OR mpesa_code LIKE :query)
        ORDER BY timestamp DESC
    """)
    fun searchTransactions(query: String): LiveData<List<Transaction>>

    // Get transactions by date
    @Query("SELECT * FROM transactions WHERE is_hidden = 0 AND date_received = :date ORDER BY timestamp DESC")
    fun getTransactionsByDate(date: String): LiveData<List<Transaction>>

    // Get total by date
    @Query("SELECT SUM(amount) FROM transactions WHERE is_hidden = 0 AND date_received = :date AND transaction_type = 'RECEIVED'")
    suspend fun getTotalByDate(date: String): Double?

    // ============ SHIFT-SPECIFIC QUERIES ============

    // Get transactions for a specific shift (visible only)
    @Query("SELECT * FROM transactions WHERE is_hidden = 0 AND shift_id = :shiftId ORDER BY timestamp DESC")
    fun getTransactionsByShift(shiftId: Long): LiveData<List<Transaction>>

    // Get unassigned transactions in current shift (no person assigned)
    @Query("""
        SELECT * FROM transactions 
        WHERE is_hidden = 0 
          AND shift_id = :shiftId 
          AND (assigned_to IS NULL OR assigned_to = '')
          AND transaction_type = 'RECEIVED'
        ORDER BY timestamp DESC
    """)
    fun getUnassignedTransactionsByShift(shiftId: Long): LiveData<List<Transaction>>

    // Get assigned transactions for a person in a shift
    @Query("""
        SELECT * FROM transactions 
        WHERE is_hidden = 0 
          AND shift_id = :shiftId 
          AND assigned_to = :personName
        ORDER BY timestamp DESC
    """)
    fun getTransactionsByShiftAndPerson(shiftId: Long, personName: String): LiveData<List<Transaction>>

    // Get total amount assigned to a person in a shift
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE is_hidden = 0 
          AND shift_id = :shiftId 
          AND assigned_to = :personName
    """)
    suspend fun getTotalByShiftAndPerson(shiftId: Long, personName: String): Double?

    // Get transactions by category in a shift
    @Query("""
        SELECT * FROM transactions 
        WHERE is_hidden = 0 
          AND shift_id = :shiftId 
          AND transaction_category = :category
        ORDER BY timestamp DESC
    """)
    fun getTransactionsByShiftAndCategory(shiftId: Long, category: String): LiveData<List<Transaction>>

    // Get total by category in a shift
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE is_hidden = 0 
          AND shift_id = :shiftId 
          AND transaction_category = :category
    """)
    suspend fun getTotalByShiftAndCategory(shiftId: Long, category: String): Double?

    // Get all TRANSFER transactions in a shift (for reconciliation)
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE is_hidden = 0 
          AND shift_id = :shiftId 
          AND (transaction_type = 'SENT' OR transaction_category = 'TRANSFER')
    """)
    suspend fun getTotalTransfersByShift(shiftId: Long): Double?

    // Get all WITHDRAWAL transactions in a shift
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE is_hidden = 0 
          AND shift_id = :shiftId 
          AND transaction_category = 'WITHDRAWAL'
    """)
    suspend fun getTotalWithdrawalsByShift(shiftId: Long): Double?

    // Assign transaction to person
    @Query("""
        UPDATE transactions 
        SET assigned_to = :personName, 
            transaction_category = :category,
            status = 'assigned'
        WHERE id = :transactionId
    """)
    suspend fun assignTransaction(transactionId: Long, personName: String, category: String)

    // Unassign transaction
    @Query("""
        UPDATE transactions 
        SET assigned_to = NULL, 
            transaction_category = NULL,
            status = 'pending'
        WHERE id = :transactionId
    """)
    suspend fun unassignTransaction(transactionId: Long)

    // ============ SHIFT MANAGEMENT ============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: Shift): Long

    @Update
    suspend fun updateShift(shift: Shift)

    // Get currently active shift
    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getOpenShift(): Shift?

    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' LIMIT 1")
    fun getOpenShiftLive(): LiveData<Shift?>

    // Get all shifts
    @Query("SELECT * FROM shifts ORDER BY start_time DESC")
    fun getAllShifts(): LiveData<List<Shift>>

    // Get shift by ID
    @Query("SELECT * FROM shifts WHERE shift_id = :id")
    suspend fun getShiftById(id: Long): Shift?

    @Query("SELECT * FROM shifts WHERE shift_id = :id")
    fun getShiftByIdLive(id: Long): LiveData<Shift?>

    // Get closed shifts
    @Query("SELECT * FROM shifts WHERE status = 'CLOSED' ORDER BY start_time DESC")
    fun getClosedShifts(): LiveData<List<Shift>>

    // Close shift
    @Query("""
        UPDATE shifts 
        SET status = 'CLOSED',
            end_time = :endTime,
            close_balance = :closeBalance,
            updated_at = :timestamp
        WHERE shift_id = :shiftId
    """)
    suspend fun closeShift(shiftId: Long, endTime: Long, closeBalance: Double, timestamp: Long = System.currentTimeMillis())

    // ============ STATISTICS ============

    @Query("SELECT COUNT(*) FROM transactions WHERE is_hidden = 0")
    suspend fun getTransactionCount(): Int

    @Query("SELECT SUM(amount) FROM transactions WHERE is_hidden = 0 AND transaction_type = 'RECEIVED'")
    suspend fun getTotalReceived(): Double?
}