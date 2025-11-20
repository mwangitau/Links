package com.githow.links.receiver

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
        Log.d(TAG, "📱 SMS Receiver triggered! Action: ${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.w(TAG, "⚠️ Wrong action: ${intent.action}")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        Log.d(TAG, "📨 Received ${messages.size} SMS message(s)")

        // Concatenate multi-part SMS
        val mpesaMessages = mutableMapOf<String, StringBuilder>()
        val timestamps = mutableMapOf<String, Long>()

        for (smsMessage in messages) {
            val messageBody = smsMessage.messageBody ?: ""
            val sender = smsMessage.displayOriginatingAddress ?: "Unknown"
            val timestamp = smsMessage.timestampMillis

            Log.d(TAG, "📧 SMS from: $sender")

            // Check if M-PESA
            if (isMpesaSms(sender, messageBody)) {
                if (!mpesaMessages.containsKey(sender)) {
                    mpesaMessages[sender] = StringBuilder()
                    timestamps[sender] = timestamp
                }
                mpesaMessages[sender]?.append(messageBody)
            } else {
                Log.d(TAG, "⏭️ Not M-PESA SMS, skipping")
            }
        }

        // Process each M-PESA message
        mpesaMessages.forEach { (sender, messageBuilder) ->
            val completeMessage = messageBuilder.toString()
            val timestamp = timestamps[sender] ?: System.currentTimeMillis()

            Log.d(TAG, "✅ M-PESA SMS detected! Processing...")
            handleMpesaSms(context, sender, completeMessage, timestamp)
        }
    }

    private fun isMpesaSms(sender: String, body: String): Boolean {
        return sender.contains("MPESA", ignoreCase = true) ||
                body.startsWith("TK") ||
                body.startsWith("TJ") ||
                body.contains("Confirmed", ignoreCase = true)
    }

    private fun handleMpesaSms(context: Context, sender: String, messageBody: String, smsTimestamp: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = LinksDatabase.getDatabase(context)
                val rawSmsDao = database.rawSmsDao()

                // 🔥 STEP 1: CAPTURE RAW SMS FIRST (ALWAYS!)
                // Check for duplicate (same message within 5 seconds)
                val duplicate = rawSmsDao.findDuplicate(messageBody, smsTimestamp)
                if (duplicate != null) {
                    Log.w(TAG, "⚠️ Duplicate SMS detected, skipping")
                    return@launch
                }

                val rawSms = RawSms(
                    sender = sender,
                    message_body = messageBody,
                    received_at = smsTimestamp,
                    parsed = false
                )

                val rawSmsId = rawSmsDao.insert(rawSms)
                Log.d(TAG, "💾 Raw SMS saved! ID: $rawSmsId")

                // 🔥 STEP 2: NOW TRY TO PARSE (SMS is safe even if this fails)
                tryParseAndSave(context, rawSmsId, messageBody)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error capturing SMS: ${e.message}", e)
            }
        }
    }

    private suspend fun tryParseAndSave(context: Context, rawSmsId: Long, messageBody: String) {
        try {
            val database = LinksDatabase.getDatabase(context)
            val rawSmsDao = database.rawSmsDao()
            val transactionDao = database.transactionDao()

            // Try to parse
            val transaction = MpesaParser.parseTransaction(messageBody)

            if (transaction == null) {
                // Parse failed - but SMS is safe!
                Log.e(TAG, "❌ Parse failed, but SMS is saved (ID: $rawSmsId)")

                val failedSms = RawSms(
                    id = rawSmsId,
                    sender = "",
                    message_body = messageBody,
                    parsed = false,
                    error_message = "Parser returned null",
                    parse_attempts = 1
                )
                rawSmsDao.update(failedSms)

                showErrorNotification(context, "Failed to parse M-PESA SMS")
                return
            }

            Log.d(TAG, "✅ Parsed: ${transaction.mpesa_code} - Ksh${transaction.amount}")

            // Check for duplicate by M-PESA code
            val existingTxn = transactionDao.getTransactionByCode(transaction.mpesa_code)
            if (existingTxn != null) {
                Log.w(TAG, "⚠️ Duplicate transaction: ${transaction.mpesa_code}")

                val duplicateSms = RawSms(
                    id = rawSmsId,
                    sender = "",
                    message_body = messageBody,
                    parsed = true,
                    mpesa_code = transaction.mpesa_code,
                    amount = transaction.amount,
                    transaction_id = existingTxn.id,
                    is_duplicate = true
                )
                rawSmsDao.update(duplicateSms)
                return
            }

            // 🔒 Check for open shift and respect cutoff timestamp
            val openShift = transactionDao.getOpenShift()

            val finalTransaction = if (openShift != null) {
                if (openShift.cutoff_timestamp != null) {
                    if (transaction.timestamp > openShift.cutoff_timestamp) {
                        Log.w(TAG, "⚠️ SMS after cutoff - UNASSIGNED")
                        transaction.copy(shift_id = null)
                    } else {
                        Log.d(TAG, "✅ SMS before cutoff - assigning")
                        transaction.copy(shift_id = openShift.shift_id)
                    }
                } else {
                    Log.d(TAG, "📋 No cutoff - assigning")
                    transaction.copy(shift_id = openShift.shift_id)
                }
            } else {
                transaction
            }

            // Save transaction
            val txnId = transactionDao.insertTransaction(finalTransaction)

            if (txnId > 0) {
                Log.d(TAG, "💾 Transaction saved! ID: $txnId")

                // Update raw SMS with success
                val successSms = RawSms(
                    id = rawSmsId,
                    sender = "",
                    message_body = messageBody,
                    parsed = true,
                    mpesa_code = transaction.mpesa_code,
                    amount = transaction.amount,
                    transaction_id = txnId,
                    shift_id = finalTransaction.shift_id
                )
                rawSmsDao.update(successSms)

                showSuccessNotification(context, finalTransaction)
            } else {
                Log.e(TAG, "❌ Failed to save transaction")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during parse: ${e.message}", e)

            // SMS is still safe in raw_sms table!
            try {
                val rawSmsDao = LinksDatabase.getDatabase(context).rawSmsDao()
                val errorSms = RawSms(
                    id = rawSmsId,
                    sender = "",
                    message_body = messageBody,
                    parsed = false,
                    error_message = e.message,
                    parse_attempts = 1
                )
                rawSmsDao.update(errorSms)
            } catch (dbError: Exception) {
                Log.e(TAG, "❌ Failed to log error: ${dbError.message}")
            }
        }
    }

    private fun showSuccessNotification(context: Context, transaction: com.githow.links.data.entity.Transaction) {
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
    }

    private fun showErrorNotification(context: Context, message: String) {
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
    }
}