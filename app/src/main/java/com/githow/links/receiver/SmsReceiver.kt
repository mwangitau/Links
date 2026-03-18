package com.githow.links.receiver

import com.githow.links.worker.SupabaseSyncWorker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.githow.links.R
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.RawSms
import com.githow.links.data.entity.ParseStatus
import com.githow.links.service.ManualReviewService
import com.githow.links.utils.MpesaParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "mpesa_transactions"
        private const val CHANNEL_ID_ERRORS = "mpesa_errors"
        private const val TAG = "LINKS_SMS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.e(TAG, "═══════════════════════════════════════")
        Log.e(TAG, "📱 SMS RECEIVER TRIGGERED!")
        Log.e(TAG, "═══════════════════════════════════════")
        Log.e(TAG, "Time: ${System.currentTimeMillis()}")
        Log.e(TAG, "Action: ${intent.action}")
        Log.e(TAG, "Package: ${context.packageName}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.e(TAG, "❌ Wrong action: ${intent.action}")
            return
        }

        // goAsync() tells Android: "I'm not done yet — don't recycle this receiver"
        // Without this, Android can kill the BroadcastReceiver before the coroutine
        // finishes saving to the database, causing silent SMS loss
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                Log.e(TAG, "📨 Total messages: ${messages.size}")

                val mpesaMessages = mutableMapOf<String, StringBuilder>()
                val timestamps = mutableMapOf<String, Long>()

                messages.forEachIndexed { index, smsMessage ->
                    val messageBody = smsMessage.messageBody ?: ""
                    val sender = smsMessage.displayOriginatingAddress ?: "Unknown"
                    val timestamp = smsMessage.timestampMillis

                    Log.e(TAG, "─────────────────────────────────────")
                    Log.e(TAG, "Message #${index + 1}:")
                    Log.e(TAG, "From: $sender")
                    Log.e(TAG, "Body Preview: ${messageBody.take(100)}")

                    val isMpesa = isMpesaSms(sender, messageBody)
                    Log.e(TAG, "Is M-PESA? $isMpesa")

                    if (isMpesa) {
                        if (!mpesaMessages.containsKey(sender)) {
                            mpesaMessages[sender] = StringBuilder()
                            timestamps[sender] = timestamp
                        }
                        mpesaMessages[sender]?.append(messageBody)
                    }
                }

                Log.e(TAG, "📊 Total M-PESA messages to process: ${mpesaMessages.size}")

                mpesaMessages.forEach { (sender, messageBuilder) ->
                    val completeMessage = messageBuilder.toString()
                    val timestamp = timestamps[sender] ?: System.currentTimeMillis()
                    handleMpesaSms(context, sender, completeMessage, timestamp)
                }

                Log.e(TAG, "✅ SMS Receiver processing complete!")

            } catch (e: Exception) {
                Log.e(TAG, "❌ CRITICAL ERROR in onReceive: ${e.message}", e)
                e.printStackTrace()
            } finally {
                // CRITICAL: always call finish() so Android knows we're done
                pendingResult.finish()
            }
        }
    }

    private fun isMpesaSms(sender: String, body: String): Boolean {
        // Sender check — most reliable signal
        if (sender.contains("MPESA", ignoreCase = true)) return true

        // M-PESA transaction code at start of message
        // Year codes: Q=2022 R=2023 S=2024 T=2025 U=2026 V=2027 ...
        // Month codes: A-L (Jan-Dec)
        if (body.matches("""^[QRSTUV][A-L][A-Z0-9]{8}.*""".toRegex(RegexOption.DOT_MATCHES_ALL))) return true

        // Confirmed keyword — fallback for unusual formats
        if (body.contains("Confirmed", ignoreCase = true) &&
            body.contains("Ksh", ignoreCase = true)) return true

        return false
    }

    private fun handleMpesaSms(context: Context, sender: String, messageBody: String, smsTimestamp: Long) {
        Log.e(TAG, "🔧 handleMpesaSms called")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "💾 Getting database instance...")
                val database = LinksDatabase.getDatabase(context)
                val rawSmsDao = database.rawSmsDao()
                Log.e(TAG, "✅ Database instance obtained")

                // Check for duplicate
                Log.e(TAG, "🔍 Checking for duplicates...")
                val duplicate = rawSmsDao.findDuplicate(messageBody, smsTimestamp)
                if (duplicate != null) {
                    Log.e(TAG, "⚠️ Duplicate SMS detected (ID: ${duplicate.id}), skipping")
                    return@launch
                }
                Log.e(TAG, "✅ No duplicate found")

                // Create raw SMS entry
                Log.e(TAG, "📝 Creating RawSms entry...")
                val rawSms = RawSms(
                    sender = sender,
                    message_body = messageBody,
                    received_timestamp = smsTimestamp,
                    parse_status = ParseStatus.UNPROCESSED,
                    created_at = System.currentTimeMillis()
                )

                Log.e(TAG, "💾 Inserting RawSms into database...")
                val rawSmsId = rawSmsDao.insert(rawSms)
                Log.e(TAG, "✅✅✅ RAW SMS SAVED! ID: $rawSmsId ✅✅✅")

                // ── Supabase backup — happens immediately after Room save ──
                // The raw SMS is now safe in the cloud before we even attempt parsing
                try {
                    val savedRawSms = rawSmsDao.getById(rawSmsId)
                    if (savedRawSms != null) {
                        val syncManager = com.githow.links.sync.CloudSyncManager(context)
                        val result = syncManager.backupRawSms(savedRawSms)
                        when (result) {
                            is com.githow.links.sync.SyncResult.Success ->
                                Log.d(TAG, "☁️ Raw SMS backed up to Supabase")
                            is com.githow.links.sync.SyncResult.Failure -> {
                                Log.w(TAG, "⚠️ Supabase backup failed — scheduling retry: \${result.error}")
                                SupabaseSyncWorker.scheduleRetry(context)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Supabase backup failed (SMS still saved locally): \${e.message}")
                    SupabaseSyncWorker.scheduleRetry(context)
                }

                // Now try to parse
                Log.e(TAG, "🔍 Attempting to parse SMS...")
                tryParseAndSave(context, rawSmsId, sender, messageBody, smsTimestamp)

            } catch (e: Exception) {
                Log.e(TAG, "❌ CRITICAL ERROR in handleMpesaSms: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    private suspend fun tryParseAndSave(
        context: Context,
        rawSmsId: Long,
        sender: String,
        messageBody: String,
        smsTimestamp: Long
    ) {
        try {
            Log.e(TAG, "🔧 tryParseAndSave called for RawSms ID: $rawSmsId")

            val database = LinksDatabase.getDatabase(context)
            val rawSmsDao = database.rawSmsDao()
            val transactionDao = database.transactionDao()

            // Try to parse
            Log.e(TAG, "🔍 Calling MpesaParser.parseTransaction...")
            val transaction = MpesaParser.parseTransaction(messageBody)

            if (transaction == null) {
                Log.e(TAG, "❌ Parser returned NULL")
                Log.e(TAG, "📋 Updating RawSms as PARSE_ERROR...")

                val failedSms = RawSms(
                    id = rawSmsId,
                    sender = sender,
                    message_body = messageBody,
                    received_timestamp = smsTimestamp,
                    parse_status = ParseStatus.PARSE_ERROR,
                    parse_error_message = "Parser returned null",
                    parse_attempts = 1,
                    created_at = System.currentTimeMillis()
                )
                rawSmsDao.update(failedSms)
                Log.e(TAG, "✅ RawSms updated with PARSE_ERROR status")

                // ── ADD TO MANUAL REVIEW QUEUE ──────────────────────────────
                // So supervisor can see it in ManualReviewScreen and enter it manually
                try {
                    val manualReviewService = ManualReviewService(
                        manualReviewDao = database.manualReviewQueueDao(),
                        rawSmsDao = rawSmsDao,
                        transactionDao = database.transactionDao()
                    )
                    // Try to extract whatever partial data we can to pre-fill the form
                    val partial = manualReviewService.extractPartialData(messageBody)
                    manualReviewService.addToReviewQueue(
                        rawSmsId = rawSmsId,
                        rawMessage = messageBody,
                        timestamp = smsTimestamp,
                        extractedCode = partial.code,
                        extractedAmount = partial.amount,
                        extractedSender = partial.senderName,
                        extractedPhone = partial.senderPhone
                    )
                    Log.e(TAG, "✅ Added to manual review queue")
                } catch (queueError: Exception) {
                    Log.e(TAG, "⚠️ Failed to add to review queue: ${queueError.message}")
                    // Non-fatal: raw SMS is already saved with PARSE_ERROR status
                }
                // ────────────────────────────────────────────────────────────

                showErrorNotification(context, "Failed to parse M-PESA SMS")
                return
            }

            Log.e(TAG, "✅ PARSE SUCCESS!")
            Log.e(TAG, "  - M-PESA Code: ${transaction.mpesa_code}")
            Log.e(TAG, "  - Amount: ${transaction.amount}")
            Log.e(TAG, "  - Type: ${transaction.transaction_type}")

            // Check for duplicate by M-PESA code
            Log.e(TAG, "🔍 Checking for duplicate M-PESA code...")
            val existingTxn = transactionDao.getTransactionByCode(transaction.mpesa_code)
            if (existingTxn != null) {
                Log.e(TAG, "⚠️ Duplicate transaction code: ${transaction.mpesa_code}")

                val duplicateSms = RawSms(
                    id = rawSmsId,
                    sender = sender,
                    message_body = messageBody,
                    received_timestamp = smsTimestamp,
                    parse_status = ParseStatus.PARSED_SUCCESS,
                    mpesa_code = transaction.mpesa_code,
                    extracted_amount = transaction.amount,
                    transaction_id = existingTxn.id,
                    is_duplicate = true,
                    created_at = System.currentTimeMillis()
                )
                rawSmsDao.update(duplicateSms)
                Log.e(TAG, "✅ Marked as duplicate")
                return
            }

            // Check for open shift
            Log.e(TAG, "🔍 Checking for open shift...")
            val openShift = transactionDao.getOpenShift()

            val finalTransaction = if (openShift != null) {
                Log.e(TAG, "✅ Open shift found: ${openShift.shift_id}")
                if (openShift.cutoff_timestamp != null) {
                    if (transaction.timestamp > openShift.cutoff_timestamp) {
                        Log.e(TAG, "⚠️ SMS after cutoff - UNASSIGNED")
                        transaction.copy(shift_id = null)
                    } else {
                        Log.e(TAG, "✅ SMS before cutoff - assigning to shift")
                        transaction.copy(shift_id = openShift.shift_id)
                    }
                } else {
                    Log.e(TAG, "📋 No cutoff - assigning to shift")
                    transaction.copy(shift_id = openShift.shift_id)
                }
            } else {
                Log.e(TAG, "⚠️ No open shift found")
                transaction
            }

            // Save transaction
            Log.e(TAG, "💾 Inserting transaction into database...")
            val txnId = transactionDao.insertTransaction(finalTransaction)

            if (txnId > 0) {
                Log.e(TAG, "✅✅✅ TRANSACTION SAVED! ID: $txnId ✅✅✅")

                val successSms = RawSms(
                    id = rawSmsId,
                    sender = sender,
                    message_body = messageBody,
                    received_timestamp = smsTimestamp,
                    parse_status = ParseStatus.PARSED_SUCCESS,
                    mpesa_code = transaction.mpesa_code,
                    extracted_amount = transaction.amount,
                    transaction_id = txnId,
                    created_at = System.currentTimeMillis()
                )
                rawSmsDao.update(successSms)
                Log.e(TAG, "✅ RawSms updated with SUCCESS status")

                showSuccessNotification(context, finalTransaction)
            } else {
                Log.e(TAG, "❌ Failed to save transaction (insertTransaction returned $txnId)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION in tryParseAndSave: ${e.message}", e)
            e.printStackTrace()

            try {
                val rawSmsDao = LinksDatabase.getDatabase(context).rawSmsDao()
                val database = LinksDatabase.getDatabase(context)

                val errorSms = RawSms(
                    id = rawSmsId,
                    sender = sender,
                    message_body = messageBody,
                    received_timestamp = smsTimestamp,
                    parse_status = ParseStatus.PARSE_ERROR,
                    parse_error_message = e.message,
                    parse_attempts = 1,
                    created_at = System.currentTimeMillis()
                )
                rawSmsDao.update(errorSms)
                Log.e(TAG, "✅ Error logged to database")

                // Add to manual review queue so supervisor can handle it
                try {
                    val manualReviewService = ManualReviewService(
                        manualReviewDao = database.manualReviewQueueDao(),
                        rawSmsDao = rawSmsDao,
                        transactionDao = database.transactionDao()
                    )
                    val partial = manualReviewService.extractPartialData(messageBody)
                    manualReviewService.addToReviewQueue(
                        rawSmsId = rawSmsId,
                        rawMessage = messageBody,
                        timestamp = smsTimestamp,
                        extractedCode = partial.code,
                        extractedAmount = partial.amount,
                        extractedSender = partial.senderName,
                        extractedPhone = partial.senderPhone
                    )
                    Log.e(TAG, "✅ Exception path: added to manual review queue")
                } catch (queueError: Exception) {
                    Log.e(TAG, "⚠️ Failed to add to review queue (exception path): ${queueError.message}")
                }
            } catch (dbError: Exception) {
                Log.e(TAG, "❌ Failed to log error to DB: ${dbError.message}")
            }
        }
    }

    private fun showSuccessNotification(context: Context, transaction: com.githow.links.data.entity.Transaction) {
        try {
            Log.e(TAG, "🔔 Showing success notification...")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "M-PESA Transactions",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val senderInfo = transaction.sender_name ?: transaction.business_name ?: "Unknown"
            val amountText = "Ksh ${String.format("%,.0f", transaction.amount)}"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New M-PESA Transaction")
                .setContentText("$amountText from $senderInfo")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(1001, notification)
            Log.e(TAG, "✅ Notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show notification: ${e.message}", e)
        }
    }

    private fun showErrorNotification(context: Context, message: String) {
        try {
            Log.e(TAG, "⚠️ Showing error notification: $message")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_ERRORS,
                    "M-PESA Errors",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_ERRORS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("M-PESA Parse Error")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1002, notification)
            Log.e(TAG, "✅ Error notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show error notification: ${e.message}", e)
        }
    }
}