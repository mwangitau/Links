package com.githow.links.data.dao

import androidx.room.*
import com.githow.links.data.entity.ManualReviewQueue
import com.githow.links.data.entity.ReviewStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualReviewQueueDao {

    @Query("""
        SELECT * FROM manual_review_queue 
        WHERE review_status = 'PENDING' 
        ORDER BY received_timestamp DESC
    """)
    fun getPendingReviews(): Flow<List<ManualReviewQueue>>

    @Query("SELECT * FROM manual_review_queue WHERE raw_sms_id = :rawSmsId")
    suspend fun getByRawSmsId(rawSmsId: Long): ManualReviewQueue?

    @Query("SELECT COUNT(*) FROM manual_review_queue WHERE review_status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("""
        SELECT * FROM manual_review_queue 
        WHERE review_status = :status 
        ORDER BY received_timestamp DESC
    """)
    fun getByStatus(status: ReviewStatus): Flow<List<ManualReviewQueue>>

    @Query("""
        SELECT * FROM manual_review_queue 
        WHERE received_timestamp BETWEEN :startTime AND :endTime
        ORDER BY received_timestamp DESC
    """)
    suspend fun getByDateRange(startTime: Long, endTime: Long): List<ManualReviewQueue>

    @Query("""
        SELECT * FROM manual_review_queue 
        WHERE reviewed_by = :supervisorUsername 
        ORDER BY reviewed_timestamp DESC
    """)
    suspend fun getBySupervisor(supervisorUsername: String): List<ManualReviewQueue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ManualReviewQueue): Long

    @Update
    suspend fun update(item: ManualReviewQueue)

    @Delete
    suspend fun delete(item: ManualReviewQueue)

    @Query("""
        UPDATE manual_review_queue 
        SET review_status = :status 
        WHERE raw_sms_id = :rawSmsId
    """)
    suspend fun updateStatus(rawSmsId: Long, status: ReviewStatus)

    @Query("""
        UPDATE manual_review_queue 
        SET review_status = 'COMPLETED',
            reviewed_by = :supervisorUsername,
            reviewed_timestamp = :timestamp
        WHERE raw_sms_id = :rawSmsId
    """)
    suspend fun markAsCompleted(
        rawSmsId: Long,
        supervisorUsername: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE manual_review_queue 
        SET review_status = 'SKIPPED',
            reviewed_by = :supervisorUsername,
            reviewed_timestamp = :timestamp,
            notes = :notes
        WHERE raw_sms_id = :rawSmsId
    """)
    suspend fun markAsSkipped(
        rawSmsId: Long,
        supervisorUsername: String,
        notes: String? = null,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        DELETE FROM manual_review_queue 
        WHERE review_status IN ('COMPLETED', 'SKIPPED') 
        AND reviewed_timestamp < :beforeTimestamp
    """)
    suspend fun deleteOldReviews(beforeTimestamp: Long)

    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN review_status = 'PENDING' THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN review_status = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
            SUM(CASE WHEN review_status = 'SKIPPED' THEN 1 ELSE 0 END) as skipped
        FROM manual_review_queue
    """)
    suspend fun getStatistics(): ManualReviewStats
}

data class ManualReviewStats(
    val total: Int,
    val pending: Int,
    val completed: Int,
    val skipped: Int
)
