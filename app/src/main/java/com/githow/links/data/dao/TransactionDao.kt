package com.githow.links.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.ShiftAssignment

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("SELECT * FROM transactions WHERE status = 'pending' ORDER BY timestamp DESC")
    fun getPendingTransactions(): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE shift_id = :shiftId ORDER BY timestamp DESC")
    fun getTransactionsByShift(shiftId: Long): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE mpesa_code = :mpesaCode LIMIT 1")
    suspend fun getTransactionByCode(mpesaCode: String): Transaction?

    @Query("UPDATE transactions SET assigned_to = :personName, shift_id = :shiftId WHERE id = :transactionId")
    suspend fun assignTransaction(transactionId: Long, personName: String, shiftId: Long)

    @Query("UPDATE transactions SET status = 'synced', synced_at = :syncedAt WHERE id IN (:transactionIds)")
    suspend fun markAsSynced(transactionIds: List<Long>, syncedAt: Long)

    @Query("SELECT * FROM transactions WHERE status = 'pending'")
    suspend fun getUnsyncedTransactions(): List<Transaction>

    @Insert
    suspend fun insertShift(shift: Shift): Long

    @Query("SELECT * FROM shifts WHERE status = 'open' LIMIT 1")
    suspend fun getOpenShift(): Shift?

    @Query("SELECT * FROM shifts WHERE status = 'open' LIMIT 1")
    fun getOpenShiftLive(): LiveData<Shift?>

    @Query("SELECT * FROM shifts ORDER BY start_time DESC")
    fun getAllShifts(): LiveData<List<Shift>>

    @Query("UPDATE shifts SET status = 'closed', end_time = :endTime WHERE shift_id = :shiftId")
    suspend fun closeShift(shiftId: Long, endTime: Long)

    @Insert
    suspend fun insertShiftAssignment(assignment: ShiftAssignment)

    @Query("SELECT * FROM shift_assignments WHERE shift_id = :shiftId")
    fun getShiftAssignments(shiftId: Long): LiveData<List<ShiftAssignment>>

    @Query("SELECT * FROM shift_assignments WHERE shift_id = :shiftId")
    suspend fun getShiftAssignmentsList(shiftId: Long): List<ShiftAssignment>

    @Query("""
        SELECT assigned_to, SUM(amount) as total
        FROM transactions 
        WHERE shift_id = :shiftId AND assigned_to IS NOT NULL
        GROUP BY assigned_to
    """)
    fun getShiftTotals(shiftId: Long): LiveData<List<ShiftTotal>>
}

data class ShiftTotal(
    val assigned_to: String,
    val total: Double
)