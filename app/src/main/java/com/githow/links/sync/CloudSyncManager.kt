package com.githow.links.sync

import android.content.Context
import android.util.Log
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSyncManager"

        // TODO: Replace with your actual Google Apps Script webhook URL
        private const val WEBHOOK_URL = "https://script.google.com/macros/s/AKfycbyMiJudd8CGRrYm7_btLxj6rOycte6HbrGgAmLd6W8z6OLQ1WrVETiG2zWQU46XH_yM/exec"

        private const val TIMEOUT_SECONDS = 90L  // Increased from 30s to 90s for large shifts
        private const val MAX_RETRIES = 3
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Sync assigned transactions immediately (real-time backup)
     * This runs in the background without blocking the UI
     */
    suspend fun syncAssignedTransactions(
        shift: Shift,
        transactions: List<Transaction>
    ): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "⚡ Real-time sync: ${transactions.size} assigned transactions")

        // Build lightweight payload (only assigned transactions)
        val payload = buildAssignmentPayload(shift, transactions)

        // Try sync with single retry (fast fail for real-time)
        return@withContext try {
            sendToWebhook(payload, 1)
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Real-time sync failed (will retry at shift close): ${e.message}")
            // Don't block assignment - just log the failure
            SyncResult.Failure(
                error = "Background sync pending: ${e.message}",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Sync a closed shift with all its transactions to Google Sheets via webhook
     */
    suspend fun syncShiftToCloud(
        shift: Shift,
        transactions: List<Transaction>
    ): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting webhook sync for shift ${shift.shift_id} with ${transactions.size} transactions")

        // Build JSON payload
        val payload = buildPayload(shift, transactions)

        // Try sync with retries
        var lastError: String? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = sendToWebhook(payload, attempt + 1)
                if (result is SyncResult.Success) {
                    return@withContext result
                } else if (result is SyncResult.Failure) {
                    lastError = result.error
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
            }

            // Wait before retry (exponential backoff)
            if (attempt < MAX_RETRIES - 1) {
                val delayMs = (attempt + 1) * 1000L
                kotlinx.coroutines.delay(delayMs)
            }
        }

        // All retries failed
        Log.e(TAG, "❌ All retry attempts failed for shift ${shift.shift_id}")
        return@withContext SyncResult.Failure(
            error = lastError ?: "Failed after $MAX_RETRIES attempts",
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Build JSON payload from shift and transactions
     */
    private fun buildPayload(shift: Shift, transactions: List<Transaction>): JSONObject {
        return JSONObject().apply {
            // Add shift data
            put("shift", JSONObject().apply {
                put("shift_id", shift.shift_id)
                put("start_time", shift.start_time)
                put("end_time", shift.end_time)
                put("open_balance", shift.open_balance)
                put("close_balance", shift.close_balance)
                put("status", shift.status)
                put("total_received", shift.total_received)
                put("total_transfers", shift.total_transfers)
                put("total_withdrawals", shift.total_withdrawals)
                put("expected_total", shift.expected_total)
                put("actual_total", shift.actual_total)
                put("difference", shift.difference)
                put("shift_name", shift.shift_name ?: "")
                put("notes", shift.notes ?: "")
                put("created_at", shift.created_at)
                put("updated_at", shift.updated_at)
            })

            // Add transactions array
            put("transactions", JSONArray().apply {
                transactions.forEach { txn ->
                    put(JSONObject().apply {
                        put("id", txn.id)
                        put("mpesa_code", txn.mpesa_code)
                        put("amount", txn.amount)
                        put("sender_phone", txn.sender_phone ?: "")
                        put("sender_name", txn.sender_name ?: "")
                        put("paybill_number", txn.paybill_number ?: "")
                        put("business_name", txn.business_name ?: "")
                        put("timestamp", txn.timestamp)
                        put("date_received", txn.date_received)
                        put("time_received", txn.time_received)
                        put("account_balance", txn.account_balance)
                        put("transaction_cost", txn.transaction_cost)
                        put("transaction_type", txn.transaction_type)
                        put("shift_id", txn.shift_id ?: 0)
                        put("assigned_to", txn.assigned_to ?: "")
                        put("transaction_category", txn.transaction_category ?: "")
                        put("status", txn.status)
                        put("created_at", txn.created_at)
                    })
                }
            })

            // Add metadata
            put("metadata", JSONObject().apply {
                put("app_version", "1.0")
                put("sync_timestamp", System.currentTimeMillis())
                put("sync_type", "full") // "full" for shift close, "incremental" for real-time
                put("device_id", android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ))
            })
        }
    }

    /**
     * Build lightweight payload for real-time assignment sync
     */
    private fun buildAssignmentPayload(shift: Shift, transactions: List<Transaction>): JSONObject {
        return JSONObject().apply {
            // Minimal shift data (just identifiers)
            put("shift", JSONObject().apply {
                put("shift_id", shift.shift_id)
                put("start_time", shift.start_time)
                put("status", shift.status)
                put("updated_at", System.currentTimeMillis())
            })

            // Only the assigned transactions
            put("transactions", JSONArray().apply {
                transactions.forEach { txn ->
                    put(JSONObject().apply {
                        put("id", txn.id)
                        put("mpesa_code", txn.mpesa_code)
                        put("amount", txn.amount)
                        put("sender_phone", txn.sender_phone ?: "")
                        put("sender_name", txn.sender_name ?: "")
                        put("paybill_number", txn.paybill_number ?: "")
                        put("business_name", txn.business_name ?: "")
                        put("timestamp", txn.timestamp)
                        put("date_received", txn.date_received)
                        put("time_received", txn.time_received)
                        put("account_balance", txn.account_balance)
                        put("transaction_cost", txn.transaction_cost)
                        put("transaction_type", txn.transaction_type)
                        put("shift_id", txn.shift_id ?: 0)
                        put("assigned_to", txn.assigned_to ?: "")
                        put("transaction_category", txn.transaction_category ?: "")
                        put("status", txn.status)
                        put("created_at", txn.created_at)
                    })
                }
            })

            // Add metadata
            put("metadata", JSONObject().apply {
                put("app_version", "1.0")
                put("sync_timestamp", System.currentTimeMillis())
                put("sync_type", "incremental") // Real-time incremental sync
                put("device_id", android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ))
            })
        }
    }

    /**
     * Send payload to webhook
     */
    private fun sendToWebhook(payload: JSONObject, attemptNumber: Int): SyncResult {
        Log.d(TAG, "Sending to webhook (attempt $attemptNumber)...")

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Log.d(TAG, "✅ Webhook sync successful!")
                Log.d(TAG, "Response: $responseBody")

                // Parse response
                val responseJson = try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    // If response is not JSON, just use plain text
                    JSONObject().apply {
                        put("message", responseBody)
                    }
                }

                SyncResult.Success(
                    message = responseJson.optString("message", "Synced successfully"),
                    timestamp = System.currentTimeMillis(),
                    responseData = responseJson
                )
            } else {
                val errorMessage = "HTTP ${response.code}: ${response.message} - $responseBody"
                Log.e(TAG, "❌ Webhook sync failed: $errorMessage")

                SyncResult.Failure(
                    error = errorMessage,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during webhook sync: ${e.message}", e)
            SyncResult.Failure(
                error = e.message ?: "Network error",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Test webhook connectivity (call this to verify webhook is working)
     */
    suspend fun testWebhook(): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Testing webhook connectivity...")

        val testPayload = JSONObject().apply {
            put("test", true)
            put("timestamp", System.currentTimeMillis())
            put("message", "Connection test from LINKS app")
        }

        return@withContext try {
            sendToWebhook(testPayload, 1)
        } catch (e: Exception) {
            SyncResult.Failure(
                error = "Test failed: ${e.message}",
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

// ============ RESULT CLASSES ============

sealed class SyncResult {
    data class Success(
        val message: String,
        val timestamp: Long,
        val responseData: JSONObject? = null
    ) : SyncResult()

    data class Failure(
        val error: String,
        val timestamp: Long
    ) : SyncResult()
}