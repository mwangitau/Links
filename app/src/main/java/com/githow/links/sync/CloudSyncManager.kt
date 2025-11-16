package com.githow.links.sync

import android.content.Context
import android.util.Log
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.Transaction
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class CloudSyncManager(private val context: Context) {

    private val db = Firebase.firestore

    companion object {
        private const val TAG = "CloudSyncManager"
        private const val SHIFTS_COLLECTION = "shifts"
        private const val TRANSACTIONS_COLLECTION = "transactions"
    }

    /**
     * Sync a closed shift with all its transactions to Firestore
     */
    suspend fun syncShiftToCloud(
        shift: Shift,
        transactions: List<Transaction>
    ): SyncResult {
        Log.d(TAG, "Starting Firestore sync for shift ${shift.shift_id} with ${transactions.size} transactions")

        val shiftDocRef = db.collection(SHIFTS_COLLECTION).document(shift.shift_id.toString())

        return try {
            // Use a batch write to save the shift and all its transactions in one atomic operation
            db.runBatch { batch ->
                // 1. Save the Shift document
                batch.set(shiftDocRef, shift)

                // 2. Save each Transaction in a sub-collection
                val transactionsCollection = shiftDocRef.collection(TRANSACTIONS_COLLECTION)
                transactions.forEach { transaction ->
                    // Use the M-PESA code as the document ID for easy lookup and to prevent duplicates
                    val transactionDoc = transactionsCollection.document(transaction.mpesa_code)
                    batch.set(transactionDoc, transaction)
                }
            }.await() // Wait for the batch write to complete

            Log.d(TAG, "✅ Firestore sync successful for shift ${shift.shift_id}")
            SyncResult.Success(
                message = "Synced shift and ${transactions.size} transactions to Firestore",
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firestore sync failed for shift ${shift.shift_id}", e)
            SyncResult.Failure(
                error = e.message ?: "An unknown error occurred during Firestore sync",
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

// ============ RESULT CLASSES ============

sealed class SyncResult {
    data class Success(
        val message: String,
        val timestamp: Long
    ) : SyncResult()

    data class Failure(
        val error: String,
        val timestamp: Long
    ) : SyncResult()
}