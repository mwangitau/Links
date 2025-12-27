package com.githow.links.service

import android.util.Log
import com.githow.links.data.dao.ManualReviewQueueDao
import com.githow.links.data.dao.RawSmsDao
import com.githow.links.data.dao.TransactionDao
import com.githow.links.data.entity.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

/**
 * ManualReviewService - Handles unparsed SMS manual entry workflow
 *
 * Flow:
 * 1. SMS fails to parse → Added to manual review queue
 * 2. Supervisor opens ManualReviewScreen → Sees unparsed SMS
 * 3. Supervisor fills form → Enters password
 * 4. Service creates transaction → Updates status
 */
class ManualReviewService(
    private val manualReviewDao: ManualReviewQueueDao,
    private val rawSmsDao: RawSmsDao,
    private val transactionDao: TransactionDao
) {

    companion object {
        private const val TAG = "ManualReviewService"
    }

    // ============================================
    // QUERY OPERATIONS
    // ============================================

    /**
     * Get all pending reviews
     */
    fun getPendingReviews(): Flow<List<ManualReviewQueue>> {
        return manualReviewDao.getPendingReviews()
    }

    /**
     * Get pending review count (for badge)
     */
    fun getPendingCount(): Flow<Int> {
        return manualReviewDao.getPendingCount()
    }

    /**
     * Get review by raw SMS ID
     */
    suspend fun getReviewItem(rawSmsId: Long): ManualReviewQueue? {
        return manualReviewDao.getByRawSmsId(rawSmsId)
    }

    // ============================================
    // ADD TO REVIEW QUEUE
    // ============================================

    /**
     * Add SMS to manual review queue
     * Called when SMS parsing fails
     */
    suspend fun addToReviewQueue(
        rawSmsId: Long,
        rawMessage: String,
        timestamp: Long,
        extractedCode: String? = null,
        extractedAmount: Double? = null,
        extractedSender: String? = null,
        extractedPhone: String? = null
    ): Long {
        try {
            Log.d(TAG, "📝 Adding to review queue: SMS ID $rawSmsId")

            // Create review item
            val reviewItem = ManualReviewQueue(
                raw_sms_id = rawSmsId,
                raw_message = rawMessage,
                received_timestamp = timestamp,
                extracted_code = extractedCode,
                extracted_amount = extractedAmount,
                extracted_sender = extractedSender,
                extracted_phone = extractedPhone,
                review_status = ReviewStatus.PENDING
            )

            // Insert into queue
            val id = manualReviewDao.insert(reviewItem)

            // Update raw SMS status
            rawSmsDao.updateParseStatus(rawSmsId, ParseStatus.MANUAL_REVIEW)

            Log.d(TAG, "✅ Added to review queue successfully")
            return id

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding to review queue: ${e.message}", e)
            throw e
        }
    }

    // ============================================
    // SUBMIT MANUAL ENTRY
    // ============================================

    /**
     * Submit manual entry and create transaction
     * Called when supervisor completes the form and authenticates
     */
    suspend fun submitManualEntry(
        rawSmsId: Long,
        mpesaCode: String,
        amount: Double,
        senderName: String?,
        senderPhone: String?,
        transactionType: TransactionType,
        isTransfer: Boolean,
        transactionTime: Long,
        paybillNumber: String? = null,
        businessName: String? = null,
        supervisorUsername: String,
        notes: String? = null
    ): Long {
        try {
            Log.d(TAG, "📝 Submitting manual entry for SMS ID $rawSmsId")
            Log.d(TAG, "Code: $mpesaCode, Amount: $amount, Type: $transactionType")

            // 1. Get the review item
            val reviewItem = manualReviewDao.getByRawSmsId(rawSmsId)
                ?: throw IllegalStateException("Review item not found for SMS ID $rawSmsId")

            // 2. Check if transaction with this code already exists
            val existingTxn = transactionDao.getTransactionByCode(mpesaCode)
            if (existingTxn != null) {
                throw IllegalStateException("Transaction with code $mpesaCode already exists")
            }

            // 3. Create transaction from manual entry
            val transaction = createTransactionFromManualEntry(
                rawSmsId = rawSmsId,
                mpesaCode = mpesaCode,
                amount = amount,
                senderName = senderName,
                senderPhone = senderPhone,
                transactionType = transactionType,
                isTransfer = isTransfer,
                transactionTime = transactionTime,
                paybillNumber = paybillNumber,
                businessName = businessName
            )

            // 4. Insert transaction
            val txnId = transactionDao.insertTransaction(transaction)

            if (txnId <= 0) {
                throw IllegalStateException("Failed to insert transaction")
            }

            Log.d(TAG, "✅ Transaction created with ID: $txnId")

            // 5. Update review queue
            val updatedReview = reviewItem.copy(
                review_status = ReviewStatus.COMPLETED,
                reviewed_by = supervisorUsername,
                reviewed_timestamp = System.currentTimeMillis(),
                manual_code = mpesaCode,
                manual_amount = amount,
                manual_sender_name = senderName,
                manual_sender_phone = senderPhone,
                manual_transaction_time = transactionTime,
                manual_transaction_type = transactionType,
                manual_is_transfer = isTransfer,
                manual_paybill_number = paybillNumber,
                manual_business_name = businessName,
                notes = notes
            )

            manualReviewDao.update(updatedReview)

            // 6. Update raw SMS status and link to transaction
            rawSmsDao.linkToTransaction(rawSmsId, txnId)
            rawSmsDao.updateParseStatus(rawSmsId, ParseStatus.MANUALLY_ENTERED)

            Log.d(TAG, "✅ Manual entry completed successfully")

            return txnId

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error submitting manual entry: ${e.message}", e)
            throw e
        }
    }

    /**
     * Create Transaction entity from manual entry data
     */
    private fun createTransactionFromManualEntry(
        rawSmsId: Long,
        mpesaCode: String,
        amount: Double,
        senderName: String?,
        senderPhone: String?,
        transactionType: TransactionType,
        isTransfer: Boolean,
        transactionTime: Long,
        paybillNumber: String?,
        businessName: String?
    ): Transaction {

        // Format date and time
        val dateReceived = formatDate(transactionTime)
        val timeReceived = formatTime(transactionTime)

        // Determine transaction category
        val category = when {
            isTransfer -> "INTERNAL_TRANSFER"
            transactionType == TransactionType.SENT -> "TRANSFER"
            transactionType == TransactionType.WITHDRAW -> "WITHDRAWAL"
            else -> "CSA"  // Default to CSA for received payments
        }

        // Map TransactionType enum to string
        val typeString = mapTransactionType(transactionType)

        return Transaction(
            raw_sms_id = rawSmsId,
            mpesa_code = mpesaCode,
            amount = amount,
            sender_name = senderName,
            sender_phone = senderPhone,
            paybill_number = paybillNumber,
            business_name = businessName,
            timestamp = transactionTime,
            date_received = dateReceived,
            time_received = timeReceived,
            account_balance = 0.0,  // Not available from manual entry
            transaction_cost = 0.0,  // Not available from manual entry
            sms_body = null,  // Original SMS is in raw_sms table
            transaction_type = typeString,
            transaction_category = category,
            is_internal_transfer = isTransfer,
            is_hidden = false,  // Manual entries are always visible
            entry_source = EntrySource.MANUAL_SUPERVISOR,
            status = "pending"  // Will be assigned to shift later
        )
    }

    // ============================================
    // SKIP REVIEW
    // ============================================

    /**
     * Skip review (mark as not needed)
     * Called when supervisor decides SMS is not relevant
     */
    suspend fun skipReview(
        rawSmsId: Long,
        supervisorUsername: String,
        reason: String? = null
    ) {
        try {
            Log.d(TAG, "⏭️ Skipping review for SMS ID $rawSmsId")

            // Update review queue
            manualReviewDao.markAsSkipped(
                rawSmsId = rawSmsId,
                supervisorUsername = supervisorUsername,
                notes = reason
            )

            // Update raw SMS status
            rawSmsDao.updateParseStatus(rawSmsId, ParseStatus.PARSE_ERROR)

            Log.d(TAG, "✅ Review skipped")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error skipping review: ${e.message}", e)
            throw e
        }
    }

    // ============================================
    // STATISTICS
    // ============================================

    /**
     * Get manual review statistics
     */
    suspend fun getStatistics(): com.githow.links.data.dao.ManualReviewStats {
        return manualReviewDao.getStatistics()
    }
    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Map TransactionType enum to string
     */
    private fun mapTransactionType(type: TransactionType): String {
        return when (type) {
            TransactionType.RECEIVED -> "RECEIVED"
            TransactionType.PAYBILL -> "PAYBILL"
            TransactionType.TILL -> "TILL"
            TransactionType.SENT -> "SENT"
            TransactionType.WITHDRAW -> "WITHDRAW"
            TransactionType.DEPOSIT -> "DEPOSIT"
            TransactionType.AIRTIME -> "AIRTIME"
            TransactionType.BILL_PAYMENT -> "BILL_PAYMENT"
            TransactionType.BUY_GOODS -> "BUY_GOODS"
            TransactionType.REVERSAL -> "REVERSAL"
            TransactionType.OTHER -> "OTHER"
        }
    }

    /**
     * Format date for transaction
     */
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("d/M/yy", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
        return sdf.format(Date(timestamp))
    }

    /**
     * Format time for transaction
     */
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
        return sdf.format(Date(timestamp))
    }

    /**
     * Extract partial data from failed SMS
     * Called when adding to review queue to pre-fill form
     */
    suspend fun extractPartialData(messageBody: String): PartialExtraction {
        // Try to extract M-PESA code
        val codePattern = """\\b[A-Z][A-Z0-9]{9}\\b""".toRegex()
        val code = codePattern.find(messageBody)?.value

        // Try to extract amount
        val amountPattern = """Ksh([\\d,]+\\.?\\d*)""".toRegex(RegexOption.IGNORE_CASE)
        val amountMatch = amountPattern.find(messageBody)
        val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        // Try to extract phone number
        val phonePattern = """(254\\d{9}|07\\d{8})""".toRegex()
        val phone = phonePattern.find(messageBody)?.value

        // Try to extract sender name (between "from" and phone/amount)
        val senderPattern = """from\\s+([A-Z\\s]+?)(?:\\s+254|\\s+07|\\s+on|\\.)""".toRegex(RegexOption.IGNORE_CASE)
        val sender = senderPattern.find(messageBody)?.groupValues?.get(1)?.trim()

        return PartialExtraction(
            code = code,
            amount = amount,
            senderName = sender,
            senderPhone = phone
        )
    }
}

/**
 * Data class for partial extraction results
 */
data class PartialExtraction(
    val code: String?,
    val amount: Double?,
    val senderName: String?,
    val senderPhone: String?
)
