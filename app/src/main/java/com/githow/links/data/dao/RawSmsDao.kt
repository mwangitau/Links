package com.githow.links.data.dao

import androidx.room.*
import com.githow.links.data.entity.RawSms
import com.githow.links.data.entity.ParseStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RawSmsDao {

    @Query("SELECT * FROM raw_sms ORDER BY received_timestamp DESC")  // ✅ CHANGED
    fun getAllRawSms(): Flow<List<RawSms>>

    @Query("SELECT * FROM raw_sms WHERE id = :id")
    suspend fun getById(id: Long): RawSms?

    @Query("SELECT * FROM raw_sms WHERE mpesa_code = :code")
    suspend fun getByMpesaCode(code: String): RawSms?

    @Query("""
        SELECT * FROM raw_sms 
        WHERE parse_status = 'PARSE_ERROR' 
        ORDER BY received_timestamp DESC
    """)  // ✅ CHANGED
    fun getUnparsedSms(): Flow<List<RawSms>>

    @Query("""
        SELECT * FROM raw_sms 
        WHERE parse_status = :status 
        ORDER BY received_timestamp DESC
    """)  // ✅ CHANGED
    fun getByParseStatus(status: ParseStatus): Flow<List<RawSms>>

    @Query("SELECT COUNT(*) FROM raw_sms WHERE parse_status = :status")
    fun getCountByStatus(status: ParseStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM raw_sms WHERE parse_status = 'PARSE_ERROR'")
    fun getParseErrorCount(): Flow<Int>

    @Query("""
        SELECT * FROM raw_sms 
        WHERE received_timestamp BETWEEN :startTime AND :endTime 
        ORDER BY received_timestamp DESC
    """)  // ✅ CHANGED
    suspend fun getByDateRange(startTime: Long, endTime: Long): List<RawSms>

    @Query("SELECT EXISTS(SELECT 1 FROM raw_sms WHERE mpesa_code = :code)")
    suspend fun existsByMpesaCode(code: String): Boolean

    @Query("""
        SELECT * FROM raw_sms 
        WHERE message_body = :messageBody 
        AND ABS(received_timestamp - :timestamp) < 5000 
        LIMIT 1
    """)  // ✅ CHANGED
    suspend fun findDuplicate(messageBody: String, timestamp: Long): RawSms?

    @Query("""
        SELECT * FROM raw_sms 
        WHERE synced_to_webhook = 0 
        AND received_timestamp > :afterTimestamp 
        ORDER BY received_timestamp ASC 
        LIMIT :limit
    """)  // ✅ CHANGED
    suspend fun getUnsyncedSms(
        afterTimestamp: Long = 0,
        limit: Int = 50
    ): List<RawSms>

    @Query("""
        SELECT * FROM raw_sms 
        WHERE synced_to_webhook = 0 
        AND webhook_sync_attempts > 0 
        ORDER BY received_timestamp ASC
    """)  // ✅ CHANGED
    suspend fun getFailedSyncs(): List<RawSms>

    @Query("""
        UPDATE raw_sms 
        SET synced_to_webhook = 1, 
            webhook_sync_timestamp = :timestamp 
        WHERE id = :id
    """)
    suspend fun markAsSynced(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE raw_sms 
        SET webhook_sync_attempts = webhook_sync_attempts + 1,
            webhook_sync_error = :error
        WHERE id = :id
    """)
    suspend fun incrementSyncAttempts(id: Long, error: String?)

    @Query("UPDATE raw_sms SET parse_status = :status WHERE id = :id")
    suspend fun updateParseStatus(id: Long, status: ParseStatus)

    @Query("""
        UPDATE raw_sms 
        SET parse_status = :status,
            parse_error_message = :errorMessage,
            parse_attempts = parse_attempts + 1
        WHERE id = :id
    """)
    suspend fun updateParseStatusWithError(
        id: Long,
        status: ParseStatus,
        errorMessage: String?
    )

    @Query("""
        UPDATE raw_sms 
        SET transaction_id = :transactionId,
            parse_status = 'PARSED_SUCCESS'
        WHERE id = :id
    """)
    suspend fun linkToTransaction(id: Long, transactionId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rawSms: RawSms): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rawSmsList: List<RawSms>): List<Long>

    @Update
    suspend fun update(rawSms: RawSms)

    @Delete
    suspend fun delete(rawSms: RawSms)

    @Query("""
        DELETE FROM raw_sms 
        WHERE received_timestamp < :beforeTimestamp 
        AND parse_status IN ('PARSED_SUCCESS', 'MANUALLY_ENTERED')
    """)  // ✅ CHANGED
    suspend fun deleteOldParsedSms(beforeTimestamp: Long)

    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN parse_status = 'UNPROCESSED' THEN 1 ELSE 0 END) as unprocessed,
            SUM(CASE WHEN parse_status = 'PARSED_SUCCESS' THEN 1 ELSE 0 END) as parsed_success,
            SUM(CASE WHEN parse_status = 'PARSE_ERROR' THEN 1 ELSE 0 END) as parse_error,
            SUM(CASE WHEN parse_status = 'MANUAL_REVIEW' THEN 1 ELSE 0 END) as manual_review,
            SUM(CASE WHEN parse_status = 'MANUALLY_ENTERED' THEN 1 ELSE 0 END) as manually_entered
        FROM raw_sms
    """)
    suspend fun getParseStatistics(): ParseStatistics
}

data class ParseStatistics(
    val total: Int,
    val unprocessed: Int,
    val parsed_success: Int,
    val parse_error: Int,
    val manual_review: Int,
    val manually_entered: Int
) {
    val parse_success_rate: Double
        get() = if (total > 0) (parsed_success.toDouble() / total) * 100 else 0.0

    val manual_entry_rate: Double
        get() = if (total > 0) (manually_entered.toDouble() / total) * 100 else 0.0
}