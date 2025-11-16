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

    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveShift(): LiveData<Shift?>

    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' LIMIT 1")
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
}