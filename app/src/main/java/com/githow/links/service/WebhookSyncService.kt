package com.githow.links.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.githow.links.data.dao.RawSmsDao
import com.githow.links.data.entity.RawSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * WebhookSyncService - Syncs raw SMS to cloud webhook
 */
class WebhookSyncService(
    private val rawSmsDao: RawSmsDao,
    private val context: Context
) {

    companion object {
        private const val TAG = "WebhookSync"
        private const val BATCH_SIZE = 20
        private const val MAX_RETRIES = 3
        private const val TIMEOUT_SECONDS = 30L
        private const val DEFAULT_WEBHOOK_URL = "https://your-webhook-url.com/api/sms"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun syncPendingSms(webhookUrl: String = DEFAULT_WEBHOOK_URL): Int {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    Log.w(TAG, "⚠️ No network available")
                    return@withContext 0
                }

                Log.d(TAG, "🔄 Starting webhook sync...")

                var totalSynced = 0
                var hasMore = true

                while (hasMore) {
                    val unsyncedBatch = rawSmsDao.getUnsyncedSms(limit = BATCH_SIZE)

                    if (unsyncedBatch.isEmpty()) {
                        hasMore = false
                        break
                    }

                    for (sms in unsyncedBatch) {
                        val success = syncSingleSms(sms, webhookUrl)
                        if (success) totalSynced++
                    }

                    if (unsyncedBatch.size < BATCH_SIZE) {
                        hasMore = false
                    }
                }

                Log.d(TAG, "✅ Synced $totalSynced SMS")
                totalSynced

            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync error: ${e.message}", e)
                0
            }
        }
    }

    suspend fun syncSingleSmsNow(smsId: Long, webhookUrl: String = DEFAULT_WEBHOOK_URL): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sms = rawSmsDao.getById(smsId) ?: return@withContext false

                if (sms.synced_to_webhook) {
                    return@withContext true
                }

                syncSingleSms(sms, webhookUrl)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing SMS $smsId: ${e.message}", e)
                false
            }
        }
    }

    suspend fun retryFailedSyncs(webhookUrl: String = DEFAULT_WEBHOOK_URL): Int {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    return@withContext 0
                }

                val failedSyncs = rawSmsDao.getFailedSyncs()
                var successCount = 0

                for (sms in failedSyncs) {
                    if (sms.webhook_sync_attempts >= MAX_RETRIES) {
                        continue
                    }

                    val success = syncSingleSms(sms, webhookUrl)
                    if (success) successCount++
                }

                successCount

            } catch (e: Exception) {
                Log.e(TAG, "❌ Retry error: ${e.message}", e)
                0
            }
        }
    }

    private suspend fun syncSingleSms(sms: RawSms, webhookUrl: String): Boolean {
        return try {
            val json = buildJsonPayload(sms)

            val requestBody = json.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-App-Version", "3.0")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                rawSmsDao.markAsSynced(sms.id)
                true
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                rawSmsDao.incrementSyncAttempts(sms.id, errorMsg)
                false
            }

        } catch (e: IOException) {
            val errorMsg = "Network error: ${e.message}"
            rawSmsDao.incrementSyncAttempts(sms.id, errorMsg)
            false

        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            rawSmsDao.incrementSyncAttempts(sms.id, errorMsg)
            false
        }
    }

    private fun buildJsonPayload(sms: RawSms): JSONObject {
        return JSONObject().apply {
            put("id", sms.id)
            put("sender", sms.sender)
            put("message", sms.message_body)
            put("received_timestamp", sms.received_timestamp)  // ✅ CHANGED THIS LINE
            put("mpesa_code", sms.mpesa_code)
            put("parse_status", sms.parse_status.name)
            put("is_duplicate", sms.is_duplicate)
            put("transaction_id", sms.transaction_id)
            put("parse_attempts", sms.parse_attempts)

            if (sms.parse_error_message != null) {
                put("parse_error", sms.parse_error_message)
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}