package com.githow.links.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.githow.links.data.entity.Shift

@Dao
interface ShiftDao {

    @Insert
    suspend fun insertShift(shift: Shift): Long

    @Update
    suspend fun updateShift(shift: Shift)

    // Get active or frozen shift (LiveData)
    @Query("SELECT * FROM shifts WHERE status IN ('ACTIVE', 'FROZEN') LIMIT 1")
    fun getActiveShift(): LiveData<Shift?>

    // Get active or frozen shift (Direct/Suspend)
    @Query("SELECT * FROM shifts WHERE status IN ('ACTIVE', 'FROZEN') LIMIT 1")
    suspend fun getActiveShiftDirect(): Shift?

    @Query("SELECT * FROM shifts WHERE status = 'CLOSED' ORDER BY end_time DESC LIMIT 1")
    fun getLastClosedShift(): LiveData<Shift?>

    @Query("SELECT * FROM shifts WHERE shift_id = :shiftId")
    fun getShiftByIdLive(shiftId: Long): LiveData<Shift?>

    @Query("SELECT * FROM shifts WHERE shift_id = :shiftId")
    suspend fun getShiftByIdDirect(shiftId: Long): Shift?

    @Query("SELECT * FROM shifts ORDER BY start_time DESC")
    fun getAllShifts(): LiveData<List<Shift>>

    @Query("SELECT * FROM shifts WHERE status = 'CLOSED' ORDER BY end_time DESC")
    fun getClosedShifts(): LiveData<List<Shift>>

    @Delete
    suspend fun deleteShift(shift: Shift)

    @Query("SELECT COUNT(*) FROM shifts")
    suspend fun getShiftCount(): Int

    // ============================================
    // NEW: Close Shift with Reconciliation
    // ============================================

    /**
     * Close shift with new reconciliation formula:
     * Expected Receipts = (Closing Balance - Opening Balance) + Money Sent Out
     * Variance = Expected Receipts - Actual Receipts
     */
    @Query("""
        UPDATE shifts 
        SET status = 'CLOSED',
            end_time = :endTime,
            close_balance = :closeBalance,
            cutoff_timestamp = :cutoffTimestamp,
            net_change = :netChange,
            money_sent_out = :moneySentOut,
            expected_receipts = :expectedReceipts,
            actual_receipts = :actualReceipts,
            variance = :variance,
            updated_at = :updatedAt
        WHERE shift_id = :shiftId
    """)
    suspend fun closeShiftWithReconciliation(
        shiftId: Long,
        endTime: Long,
        closeBalance: Double,
        cutoffTimestamp: Long,
        netChange: Double,
        moneySentOut: Double,
        expectedReceipts: Double,
        actualReceipts: Double,
        variance: Double,
        updatedAt: Long
    )

    // ============================================
    // NEW: Update Closing Balance and Reconciliation
    // ============================================

    /**
     * Update closing balance and recalculate reconciliation for an already closed shift
     * This is used when editing the closing balance after a shift has been closed
     */
    @Query("""
        UPDATE shifts 
        SET close_balance = :closeBalance,
            net_change = :netChange,
            expected_receipts = :expectedReceipts,
            variance = :variance,
            updated_at = :updatedAt
        WHERE shift_id = :shiftId
    """)
    suspend fun updateClosingBalanceAndReconciliation(
        shiftId: Long,
        closeBalance: Double,
        netChange: Double,
        expectedReceipts: Double,
        variance: Double,
        updatedAt: Long
    )
}