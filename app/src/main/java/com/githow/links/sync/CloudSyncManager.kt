package com.githow.links.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.githow.links.config.StationConfig
import com.githow.links.SupabaseClient
import com.githow.links.data.entity.RawSms
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.ShiftAssignment
import com.githow.links.data.entity.Transaction
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * CloudSyncManager
 *
 * Handles all backup operations to Supabase.
 * Three backup events:
 *   1. Raw SMS    — called from SmsReceiver the moment an SMS arrives
 *   2. Assignment — called when a supervisor assigns a CSA to a transaction
 *   3. Shift close — called when a shift is closed with full financial report
 *
 * Also keeps the legacy Google Sheets webhook as secondary sync.
 */
class CloudSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSyncManager"
        // Station identity is now read from StationConfig (SharedPreferences)
        // Set via Settings screen on first install — no hardcoded values needed
        private const val APP_VERSION = "1.0"
        private const val WEBHOOK_URL =
            "https://script.google.com/macros/s/AKfycbyMiJudd8CGRrYm7_btLxj6rOycte6HbrGgAmLd6W8z6OLQ1WrVETiG2zWQU46XH_yM/exec"
        private const val TIMEOUT_SECONDS = 90L
        private const val MAX_RETRIES = 3
    }

    private val supabase = SupabaseClient.client

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val deviceId: String
        get() = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

    // ─────────────────────────────────────────────────────────────────────────
    // 1. RAW SMS BACKUP
    // Called from SmsReceiver immediately after saving to Room.
    // Happens before parsing — this is the safety net.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun backupRawSms(rawSms: RawSms): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "📤 backupRawSms called — id=${rawSms.id}")
        Log.d(TAG, "  sender=${rawSms.sender}")
        Log.d(TAG, "  code=${rawSms.mpesa_code}")
        Log.d(TAG, "  status=${rawSms.parse_status}")
        return@withContext try {
            val stationId = resolveStationId() ?: return@withContext SyncResult.Failure(
                error = "Station not configured — open Settings screen and enter station details",
                timestamp = System.currentTimeMillis()
            )
            Log.d(TAG, "  stationId=$stationId")

            val payload = buildJsonObject {
                put("station_id", JsonPrimitive(stationId))
                put("device_sms_id", JsonPrimitive(rawSms.id))
                put("sender", JsonPrimitive(rawSms.sender))
                put("message_body", JsonPrimitive(rawSms.message_body))
                put("received_timestamp", JsonPrimitive(toIso(rawSms.received_timestamp)))
                put("mpesa_code", JsonPrimitive(rawSms.mpesa_code ?: ""))
                put("extracted_amount", JsonPrimitive(rawSms.extracted_amount ?: 0.0))
                put("parse_status", JsonPrimitive(rawSms.parse_status.name))
                put("parse_error_message", JsonPrimitive(rawSms.parse_error_message ?: ""))
                put("parse_attempts", JsonPrimitive(rawSms.parse_attempts))
                put("is_duplicate", JsonPrimitive(rawSms.is_duplicate))
                put("app_version", JsonPrimitive(APP_VERSION))
            }

            supabase.from("raw_sms").upsert(payload, onConflict = "station_id,device_sms_id")

            logSyncEvent("RAW_SMS_RECEIVED", null)
            Log.d(TAG, "✅ Raw SMS backed up successfully")
            SyncResult.Success(
                message = "Raw SMS backed up",
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Raw SMS backup failed: ${e.message}", e)
            Log.e(TAG, "  Check: 1) Station configured in Settings? 2) RLS policies? 3) Internet?")
            // Caller should schedule a retry via SupabaseSyncWorker.scheduleRetry(context)
            SyncResult.Failure(
                error = e.message ?: "Unknown error",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. TRANSACTION ASSIGNMENT BACKUP
    // Called when a CSA is assigned to a transaction.
    // Upserts so it also works for re-assignments.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun backupAssignedTransaction(transaction: Transaction): SyncResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "📤 Backing up assigned transaction ${transaction.mpesa_code}")
            return@withContext try {
                val stationId = resolveStationId() ?: return@withContext SyncResult.Failure(
                    error = "Station not configured — open Settings screen",
                    timestamp = System.currentTimeMillis()
                )

                val payload = buildTransactionPayload(transaction, stationId)

                supabase.from("transactions").upsert(payload, onConflict = "station_id,mpesa_code")

                logSyncEvent("TRANSACTION_ASSIGNED", null)
                Log.d(TAG, "✅ Assignment backed up: ${transaction.mpesa_code} → ${transaction.assigned_to}")
                SyncResult.Success(
                    message = "Assignment backed up",
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Assignment backup failed: ${e.message}")
                // Caller should schedule a retry via SupabaseSyncWorker.scheduleRetry(context)
                SyncResult.Failure(
                    error = e.message ?: "Unknown error",
                    timestamp = System.currentTimeMillis()
                )
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. SHIFT CLOSE BACKUP
    // Called when a supervisor closes a shift.
    // Backs up: shift record, all transactions, CSA assignments, CSA totals.
    // Also sends to Google Sheets webhook for legacy compatibility.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun syncShiftToCloud(
        shift: Shift,
        transactions: List<Transaction>,
        assignments: List<ShiftAssignment> = emptyList()
    ): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "📤 Syncing closed shift ${shift.shift_id} (${transactions.size} transactions)")

        return@withContext try {
            val stationId = resolveStationId() ?: return@withContext SyncResult.Failure(
                error = "Station not configured — open Settings screen",
                timestamp = System.currentTimeMillis()
            )

            // ── a) Upsert shift record ────────────────────────────────────
            val shiftPayload = buildJsonObject {
                put("station_id", JsonPrimitive(stationId))
                put("device_shift_id", JsonPrimitive(shift.shift_id))
                put("shift_name", JsonPrimitive(shift.shift_name ?: ""))
                put("status", JsonPrimitive(shift.status))
                put("start_time", JsonPrimitive(toIso(shift.start_time)))
                put("end_time", JsonPrimitive(toIso(shift.end_time ?: System.currentTimeMillis())))
                put("open_balance", JsonPrimitive(shift.open_balance))
                put("close_balance", JsonPrimitive(shift.close_balance ?: 0.0))
                put("net_change", JsonPrimitive(shift.net_change))
                put("money_sent_out", JsonPrimitive(shift.money_sent_out))
                put("expected_receipts", JsonPrimitive(shift.expected_receipts))
                put("actual_receipts", JsonPrimitive(shift.actual_receipts))
                put("variance", JsonPrimitive(shift.variance))
                put("total_received", JsonPrimitive(shift.total_received))
                put("total_transfers", JsonPrimitive(shift.total_transfers))
                put("total_withdrawals", JsonPrimitive(shift.total_withdrawals))
                put("transaction_count", JsonPrimitive(transactions.size))
                put("closed_by", JsonPrimitive(shift.closed_by ?: ""))
                put("closed_at", JsonPrimitive(toIso(shift.closed_timestamp ?: System.currentTimeMillis())))
                put("closure_notes", JsonPrimitive(shift.closure_notes ?: ""))
                put("notes", JsonPrimitive(shift.notes ?: ""))
            }

            supabase.from("shifts").upsert(shiftPayload, onConflict = "station_id,device_shift_id")

            // ── b) Resolve the Supabase shift UUID ───────────────────────
            val shiftUuid = resolveShiftUuid(stationId, shift.shift_id)

            // ── c) Upsert all transactions ────────────────────────────────
            transactions.forEach { txn ->
                val txnPayload = buildTransactionPayload(txn, stationId, shiftUuid)
                supabase.from("transactions").upsert(txnPayload, onConflict = "station_id,mpesa_code")
            }

            // ── d) Upsert shift assignments (which CSAs worked) ───────────
            assignments.forEach { assignment ->
                val aPayload = buildJsonObject {
                    put("station_id", JsonPrimitive(stationId))
                    put("shift_id", JsonPrimitive(shiftUuid ?: ""))
                    put("person_name", JsonPrimitive(assignment.person_name))
                    put("role", JsonPrimitive(assignment.role ?: "CSA"))
                }
                supabase.from("shift_assignments").insert(aPayload)
            }

            // ── e) Compute and upsert per-CSA totals ─────────────────────
            val csaTotals = computeCsaTotals(transactions)
            csaTotals.forEach { (csaName, totals) ->
                val tPayload = buildJsonObject {
                    put("station_id", JsonPrimitive(stationId))
                    put("shift_id", JsonPrimitive(shiftUuid ?: ""))
                    put("csa_name", JsonPrimitive(csaName))
                    put("transaction_count", JsonPrimitive(totals.count))
                    put("total_amount", JsonPrimitive(totals.totalAmount))
                    put("total_received", JsonPrimitive(totals.totalReceived))
                    put("total_sent", JsonPrimitive(totals.totalSent))
                    put("total_transfers", JsonPrimitive(totals.totalTransfers))
                    put("total_withdrawals", JsonPrimitive(totals.totalWithdrawals))
                }
                supabase.from("shift_csa_totals").upsert(tPayload, onConflict = "shift_id,csa_name")
            }

            logSyncEvent("SHIFT_CLOSED", null)
            Log.d(TAG, "✅ Shift ${shift.shift_id} fully backed up to Supabase")

            // ── f) Also send to Google Sheets (legacy) ────────────────────
            try {
                sendToWebhook(buildLegacyPayload(shift, transactions), 1)
                Log.d(TAG, "✅ Legacy webhook sync done")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Legacy webhook failed (Supabase backup succeeded): ${e.message}")
            }

            SyncResult.Success(
                message = "Shift backed up: ${transactions.size} transactions, ${csaTotals.size} CSAs",
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Shift sync failed: ${e.message}", e)
            // Fall back to Google Sheets only
            try {
                sendToWebhook(buildLegacyPayload(shift, transactions), 1)
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Legacy fallback also failed: ${e2.message}")
            }
            SyncResult.Failure(
                error = e.message ?: "Unknown error",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy: kept for Google Sheets compatibility
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun syncAssignedTransactions(
        shift: Shift,
        transactions: List<Transaction>
    ): SyncResult = withContext(Dispatchers.IO) {
        // Back up each transaction to Supabase
        transactions.forEach { backupAssignedTransaction(it) }
        // Also push to webhook
        return@withContext try {
            sendToWebhook(buildLegacyAssignmentPayload(shift, transactions), 1)
        } catch (e: Exception) {
            SyncResult.Failure(error = e.message ?: "Error", timestamp = System.currentTimeMillis())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Resolve station UUID from StationConfig (SharedPreferences).
     *  Uses cached UUID if available, else queries Supabase by station_code.
     *  Returns null if station is not configured or not found in Supabase. */
    private suspend fun resolveStationId(): String? {
        // 1. Check station is configured on this device
        val stationCode = StationConfig.getStationCode(context)
        if (stationCode.isBlank()) {
            Log.e(TAG, "❌ Station not configured — open Settings screen and enter station details")
            return null
        }

        // 2. Use cached UUID if available — avoids a network call on every backup
        val cachedUuid = StationConfig.getCachedUuid(context)
        if (cachedUuid.isNotBlank()) {
            Log.d(TAG, "✅ Using cached station UUID for $stationCode: $cachedUuid")
            return cachedUuid
        }

        // 3. Dynamic lookup by station_code from Supabase
        Log.d(TAG, "🔍 Looking up station by code: $stationCode")
        return try {
            val result = supabase.from("stations")
                .select(Columns.list("station_id")) {
                    filter { eq("station_code", stationCode) }
                }
                .decodeSingleOrNull<StationRow>()

            if (result == null) {
                Log.e(TAG, "❌ Station '$stationCode' not found in Supabase stations table. " +
                        "Add it via Supabase Table Editor first.")
            } else {
                // Cache the UUID so we don't query again
                StationConfig.cacheUuid(context, result.station_id)
                Log.d(TAG, "✅ Station resolved and cached: ${result.station_id}")
            }
            result?.station_id
        } catch (e: Exception) {
            Log.e(TAG, "❌ resolveStationId failed: ${e.message}")
            null
        }
    }

    /** Look up the Supabase UUID for a shift by device_shift_id */
    private suspend fun resolveShiftUuid(stationId: String, deviceShiftId: Long): String? {
        return try {
            val result = supabase.from("shifts")
                .select(Columns.list("id")) {
                    filter {
                        eq("station_id", stationId)
                        eq("device_shift_id", deviceShiftId)
                    }
                }
                .decodeSingleOrNull<ShiftRow>()
            result?.id
        } catch (e: Exception) {
            Log.e(TAG, "Could not resolve shift UUID: ${e.message}")
            null
        }
    }

    private fun buildTransactionPayload(
        txn: Transaction,
        stationId: String,
        shiftUuid: String? = null
    ) = buildJsonObject {
        put("station_id", JsonPrimitive(stationId))
        put("device_txn_id", JsonPrimitive(txn.id))
        put("mpesa_code", JsonPrimitive(txn.mpesa_code))
        put("amount", JsonPrimitive(txn.amount))
        put("transaction_type", JsonPrimitive(txn.transaction_type))
        put("timestamp", JsonPrimitive(toIso(txn.timestamp)))
        put("sender_name", JsonPrimitive(txn.sender_name ?: ""))
        put("sender_phone", JsonPrimitive(txn.sender_phone ?: ""))
        put("paybill_number", JsonPrimitive(txn.paybill_number ?: ""))
        put("business_name", JsonPrimitive(txn.business_name ?: ""))
        put("account_balance", JsonPrimitive(txn.account_balance))
        put("transaction_cost", JsonPrimitive(txn.transaction_cost))
        put("is_internal_transfer", JsonPrimitive(txn.is_internal_transfer))
        put("is_hidden", JsonPrimitive(txn.is_hidden))
        put("transaction_category", JsonPrimitive(txn.transaction_category ?: ""))
        put("assigned_to", JsonPrimitive(txn.assigned_to ?: ""))
        put("assigned_at", JsonPrimitive(toIso(System.currentTimeMillis())))
        put("device_shift_id", JsonPrimitive(txn.shift_id ?: 0L))
        put("entry_source", JsonPrimitive(txn.entry_source.name))
        put("last_updated_at", JsonPrimitive(toIso(System.currentTimeMillis())))
        if (shiftUuid != null) put("shift_id", JsonPrimitive(shiftUuid))
    }

    /** Convert Long millis to ISO 8601 string in Nairobi time */
    private fun toIso(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
        return sdf.format(Date(millis))
    }

    /** Log every sync event to the sync_log table */
    private suspend fun logSyncEvent(eventType: String, recordId: String?) {
        try {
            val stationId = resolveStationId() ?: return
            val payload = buildJsonObject {
                put("station_id", JsonPrimitive(stationId))
                put("event_type", JsonPrimitive(eventType))
                put("record_id", JsonPrimitive(recordId ?: ""))
                put("device_id", JsonPrimitive(deviceId))
                put("app_version", JsonPrimitive(APP_VERSION))
                put("success", JsonPrimitive(true))
            }
            supabase.from("sync_log").insert(payload)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write sync log: ${e.message}")
        }
    }

    /** Compute per-CSA totals from a list of transactions */
    private fun computeCsaTotals(transactions: List<Transaction>): Map<String, CsaTotals> {
        val map = mutableMapOf<String, CsaTotals>()
        transactions.filter { it.assigned_to != null }.forEach { txn ->
            val csa = txn.assigned_to!!
            val current = map.getOrDefault(csa, CsaTotals())
            map[csa] = current.copy(
                count = current.count + 1,
                totalAmount = current.totalAmount + txn.amount,
                totalReceived = current.totalReceived +
                        if (txn.transaction_type == "RECEIVED") txn.amount else 0.0,
                totalSent = current.totalSent +
                        if (txn.transaction_type == "SENT") txn.amount else 0.0,
                totalTransfers = current.totalTransfers +
                        if (txn.is_internal_transfer) txn.amount else 0.0,
                totalWithdrawals = current.totalWithdrawals +
                        if (txn.transaction_type == "WITHDRAW") txn.amount else 0.0
            )
        }
        return map
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy Google Sheets webhook (kept for backward compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildLegacyPayload(shift: Shift, transactions: List<Transaction>): JSONObject {
        return JSONObject().apply {
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
                put("closed_by", shift.closed_by ?: "")
            })
            put("transactions", JSONArray().apply {
                transactions.forEach { txn ->
                    put(JSONObject().apply {
                        put("id", txn.id)
                        put("mpesa_code", txn.mpesa_code)
                        put("amount", txn.amount)
                        put("sender_name", txn.sender_name ?: "")
                        put("sender_phone", txn.sender_phone ?: "")
                        put("timestamp", txn.timestamp)
                        put("transaction_type", txn.transaction_type)
                        put("assigned_to", txn.assigned_to ?: "")
                        put("entry_source", txn.entry_source.name)
                    })
                }
            })
            put("metadata", JSONObject().apply {
                put("app_version", APP_VERSION)
                put("sync_timestamp", System.currentTimeMillis())
                put("sync_type", "full")
                put("device_id", deviceId)
            })
        }
    }

    private fun buildLegacyAssignmentPayload(
        shift: Shift,
        transactions: List<Transaction>
    ): JSONObject {
        return JSONObject().apply {
            put("shift", JSONObject().apply {
                put("shift_id", shift.shift_id)
                put("status", shift.status)
            })
            put("transactions", JSONArray().apply {
                transactions.forEach { txn ->
                    put(JSONObject().apply {
                        put("mpesa_code", txn.mpesa_code)
                        put("amount", txn.amount)
                        put("assigned_to", txn.assigned_to ?: "")
                        put("entry_source", txn.entry_source.name)
                    })
                }
            })
            put("metadata", JSONObject().apply {
                put("sync_type", "incremental")
                put("device_id", deviceId)
            })
        }
    }

    private fun sendToWebhook(payload: JSONObject, attempt: Int): SyncResult {
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(WEBHOOK_URL).post(body).build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                SyncResult.Success("Webhook OK", System.currentTimeMillis())
            } else {
                SyncResult.Failure("HTTP ${response.code}", System.currentTimeMillis())
            }
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Network error", System.currentTimeMillis())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data classes for Supabase responses
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class StationRow(val station_id: String)

@Serializable
private data class ShiftRow(val id: String)

private data class CsaTotals(
    val count: Int = 0,
    val totalAmount: Double = 0.0,
    val totalReceived: Double = 0.0,
    val totalSent: Double = 0.0,
    val totalTransfers: Double = 0.0,
    val totalWithdrawals: Double = 0.0
)

// ─────────────────────────────────────────────────────────────────────────────
// Result sealed class
// ─────────────────────────────────────────────────────────────────────────────

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