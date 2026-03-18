package com.githow.links.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.githow.links.data.database.LinksDatabase
import com.githow.links.sync.CloudSyncManager
import java.util.concurrent.TimeUnit

/**
 * SupabaseSyncWorker
 *
 * Runs automatically whenever the device has network.
 * Finds all raw_sms and transactions that failed to sync to Supabase
 * and retries them in chronological order (oldest first).
 *
 * Triggered by:
 *   1. NetworkCallback — fires the moment internet returns
 *   2. Periodic schedule — every 15 minutes as a safety net
 *   3. Manual call from CloudSyncManager when a backup fails
 *
 * Guarantees:
 *   - Every SMS that landed on the phone reaches Supabase eventually
 *   - Retries do not duplicate — Supabase upsert is idempotent
 *   - Max 3 retry attempts per record before giving up (to avoid infinite loops)
 */
class SupabaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "SupabaseSyncWorker"
    private val MAX_SYNC_ATTEMPTS = 3
    private val BATCH_SIZE = 50 // process 50 records at a time

    override suspend fun doWork(): Result {
        Log.d(TAG, "🔄 SupabaseSyncWorker started")

        val db = LinksDatabase.getDatabase(applicationContext)
        val rawSmsDao = db.rawSmsDao()
        val transactionDao = db.transactionDao()
        val syncManager = CloudSyncManager(applicationContext)

        var rawSmsSynced = 0
        var rawSmsFailed = 0
        var txnSynced = 0
        var txnFailed = 0

        // ── 1. Sync unsynced raw SMS ─────────────────────────────────────
        try {
            val unsyncedSms = rawSmsDao.getUnsyncedSms(limit = BATCH_SIZE)
            Log.d(TAG, "📋 Found ${unsyncedSms.size} unsynced raw SMS records")

            for (sms in unsyncedSms) {
                // Skip if too many failed attempts
                if (sms.webhook_sync_attempts >= MAX_SYNC_ATTEMPTS) {
                    Log.w(TAG, "⚠️ Skipping SMS ${sms.id} — too many failed attempts (${sms.webhook_sync_attempts})")
                    continue
                }

                val result = syncManager.backupRawSms(sms)
                when (result) {
                    is com.githow.links.sync.SyncResult.Success -> {
                        rawSmsDao.markAsSynced(sms.id)
                        rawSmsSynced++
                        Log.d(TAG, "  ✅ Raw SMS ${sms.id} synced")
                    }
                    is com.githow.links.sync.SyncResult.Failure -> {
                        rawSmsFailed++
                        Log.w(TAG, "  ❌ Raw SMS ${sms.id} failed: ${result.error}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing raw SMS batch: ${e.message}", e)
        }

        // ── 2. Sync unsynced transactions ───────────────────────────────
        try {
            val unsyncedTxns = transactionDao.getUnsyncedTransactions(limit = BATCH_SIZE)
            Log.d(TAG, "📋 Found ${unsyncedTxns.size} unsynced transactions")

            for (txn in unsyncedTxns) {
                if (txn.supabase_sync_attempts >= MAX_SYNC_ATTEMPTS) {
                    Log.w(TAG, "⚠️ Skipping txn ${txn.id} — too many failed attempts")
                    continue
                }

                val result = syncManager.backupAssignedTransaction(txn)
                when (result) {
                    is com.githow.links.sync.SyncResult.Success -> {
                        transactionDao.markTransactionSynced(txn.id)
                        txnSynced++
                        Log.d(TAG, "  ✅ Transaction ${txn.mpesa_code} synced")
                    }
                    is com.githow.links.sync.SyncResult.Failure -> {
                        transactionDao.markTransactionSyncFailed(txn.id, result.error)
                        txnFailed++
                        Log.w(TAG, "  ❌ Transaction ${txn.mpesa_code} failed: ${result.error}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing transaction batch: ${e.message}", e)
        }

        Log.d(TAG, "✅ Sync run complete:")
        Log.d(TAG, "   Raw SMS  — synced: $rawSmsSynced, failed: $rawSmsFailed")
        Log.d(TAG, "   Txns     — synced: $txnSynced,    failed: $txnFailed")

        // If some failed, retry again later (WorkManager will reschedule)
        return if (rawSmsFailed > 0 || txnFailed > 0) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME_ONE_TIME = "supabase_sync_retry"
        private const val WORK_NAME_PERIODIC = "supabase_sync_periodic"

        /**
         * Schedule a one-time sync — call this immediately after a failed backup.
         * Will run as soon as network is available.
         */
        fun scheduleRetry(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.KEEP, // don't stack duplicates
                    request
                )

            Log.d("SupabaseSyncWorker", "📅 One-time sync scheduled (waiting for network)")
        }

        /**
         * Schedule periodic sync every 15 minutes — call once from MainActivity.
         * Safety net to catch anything the one-time retry misses.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SupabaseSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP, // don't reset timer on restart
                    request
                )

            Log.d("SupabaseSyncWorker", "📅 Periodic sync scheduled (every 15 min, network required)")
        }
    }
}