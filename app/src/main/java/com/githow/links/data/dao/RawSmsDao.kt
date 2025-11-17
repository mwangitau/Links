package com.githow.links.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.githow.links.data.entity.RawSms

@Dao
interface RawSmsDao {

    @Insert
    suspend fun insert(rawSms: RawSms): Long

    @Update
    suspend fun update(rawSms: RawSms)

    @Query("SELECT * FROM raw_sms ORDER BY received_at DESC LIMIT 100")
    fun getRecent(): LiveData<List<RawSms>>

    @Query("SELECT * FROM raw_sms WHERE parsed = 0 ORDER BY received_at DESC")
    fun getUnparsed(): LiveData<List<RawSms>>

    @Query("SELECT * FROM raw_sms WHERE is_duplicate = 1 ORDER BY received_at DESC")
    fun getDuplicates(): LiveData<List<RawSms>>

    @Query("SELECT COUNT(*) FROM raw_sms")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM raw_sms WHERE parsed = 1")
    suspend fun getParsedCount(): Int

    @Query("SELECT COUNT(*) FROM raw_sms WHERE parsed = 0")
    suspend fun getFailedCount(): Int

    @Query("""
        SELECT * FROM raw_sms 
        WHERE message_body = :messageBody 
        AND ABS(received_at - :timestamp) < 5000
        LIMIT 1
    """)
    suspend fun findDuplicate(messageBody: String, timestamp: Long): RawSms?

    @Query("DELETE FROM raw_sms WHERE received_at < :cutoffTime")
    suspend fun deleteOldSms(cutoffTime: Long): Int
}