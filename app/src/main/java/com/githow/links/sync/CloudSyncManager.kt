package com.githow.links.sync

import android.content.Context
import android.util.Log
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.Transaction
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class CloudSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSyncManager"

        // Your Google Apps Script webhook URL
        private const val WEBHOOK_URL = "https://script.google.com/macros/s/AKfycby9v3PQcCrKP3Xd9TlvvcM41P4x5x2e6WI2xspJOOlSdYRnju9_YZ-Jjy1Se4Fqm2jXhA/exec"

        // Retry settings
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY = 1000L // 1 second
    }

    /**
     * Sync a closed shift with all its transactions to Google Sheets
     * Matches your exact Google Sheets format
     */
    suspend fun syncShiftToCloud(
        shift: Shift,
        transactions: List<Transaction>
    ): SyncResult {
        Log.d(TAG, "Starting sync for shift ${shift.shift_id} with ${transactions.size} transactions")

        // Build the payload matching your Google Sheets columns
        val payload = buildPayload(shift, transactions)

        // Try sending with retries
        return sendWithRetry(payload)
    }

    /**
     * Build JSON payload matching your Google Sheets format
     */
    private fun buildPayload(shift: Shift, transactions: List<Transaction>): JSONObject {
        val payload = JSONObject()

        // Shift metadata
        payload.put("shift_id", shift.shift_id)
        payload.put("shift_date", formatDate(shift.start_time))
        payload.put("shift_start_time", formatTime(shift.start_time))
        payload.put("shift_end_time", formatTime(shift.end_time ?: shift.start_time))
        payload.put("opening_balance", shift.open_balance)
        payload.put("closing_balance", shift.close_balance ?: shift.open_balance)
        payload.put("total_collected", shift.actual_total)
        payload.put("difference", shift.difference)

        // Transactions array - matching your Google Sheets columns exactly
        val transactionsArray = JSONArray()
        transactions.forEach { tx ->
            val txObj = JSONObject()

            // Column 1: Timestamp
            txObj.put("timestamp", formatDateTime(tx.timestamp))

            // Column 2: Sender (MPESA for all)
            txObj.put("sender", "MPESA")

            // Column 3: Message (full SMS body)
            txObj.put("message", tx.sms_body ?: "")

            // Column 4: Amount
            txObj.put("amount", "Ksh${String.format("%,.2f", tx.amount)}")

            // Column 5: Transaction ID (M-PESA code)
            txObj.put("transaction_id", tx.mpesa_code)

            // Column 6: Name (sender name or phone)
            txObj.put("name", tx.sender_name ?: tx.sender_phone ?: "Unknown")

            // Column 7: Balance (account balance after transaction)
            txObj.put("balance", "Ksh${String.format("%,.2f", tx.account_balance)}")

            // Column 8: Type (RECEIVED, SENT, WITHDRAWN)
            txObj.put("type", tx.transaction_type)

            // Column 9: SIM (sim1 or sim2)
            txObj.put("sim", "sim1") // You can add sim_slot field to Transaction entity if needed

            // Column 10: Sent Time (time received formatted)
            txObj.put("sent_time", tx.time_received ?: formatTime(tx.timestamp))

            // Column 11: Assigned To (CSA name or empty)
            txObj.put("assigned_to", tx.assigned_to ?: "")

            // Column 12: Amount Assigned (amount if assigned, else empty)
            txObj.put("amount_assigned", if (tx.assigned_to != null) tx.amount.toInt() else "")

            // Column 13: CSA (same as assigned_to for compatibility)
            txObj.put("csa", tx.assigned_to ?: "")

            transactionsArray.put(txObj)
        }

        payload.put("transactions", transactionsArray)

        return payload
    }

    /**
     * Send data to webhook with exponential backoff retry
     */
    private suspend fun sendWithRetry(payload: JSONObject): SyncResult {
        var lastException: Exception? = null
        var retryDelay = INITIAL_RETRY_DELAY

        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Sync attempt ${attempt + 1}/$MAX_RETRIES")

                val response = sendToWebhook(payload)

                if (response.success) {
                    Log.d(TAG, "✅ Sync successful on attempt ${attempt + 1}")
                    return SyncResult.Success(
                        message = "Synced ${payload.getJSONArray("transactions").length()} transactions",
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    Log.w(TAG, "❌ Sync failed: ${response.error}")
                    lastException = Exception(response.error)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sync attempt ${attempt + 1} failed", e)
                lastException = e

                // Don't delay after last attempt
                if (attempt < MAX_RETRIES - 1) {
                    Log.d(TAG, "Retrying in ${retryDelay}ms...")
                    delay(retryDelay)
                    retryDelay *= 2 // Exponential backoff
                }
            }
        }

        // All retries failed
        val errorMessage = lastException?.message ?: "Unknown error"
        Log.e(TAG, "❌ All sync attempts failed: $errorMessage")

        return SyncResult.Failure(
            error = errorMessage,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Send JSON payload to Google Apps Script webhook
     */
    private fun sendToWebhook(payload: JSONObject): WebhookResponse {
        var connection: HttpURLConnection? = null

        try {
            val url = URL(WEBHOOK_URL)
            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 30000

            // Send payload
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            // Read response
            val responseCode = connection.responseCode
            val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

            return if (responseCode == HttpURLConnection.HTTP_OK) {
                WebhookResponse(success = true, data = responseMessage)
            } else {
                WebhookResponse(success = false, error = "HTTP $responseCode: $responseMessage")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Network error during sync", e)
            return WebhookResponse(success = false, error = e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }

    // ============ DATE/TIME FORMATTERS ============

    private fun formatDateTime(timestamp: Long): String {
        val format = SimpleDateFormat("M/d/yyyy H:mm:ss", Locale.US)
        return format.format(Date(timestamp))
    }

    private fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("M/d/yyyy", Locale.US)
        return format.format(Date(timestamp))
    }

    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("h:mm:ss a", Locale.US)
        return format.format(Date(timestamp))
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

data class WebhookResponse(
    val success: Boolean,
    val data: String? = null,
    val error: String? = null
)