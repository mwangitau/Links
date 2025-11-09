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
import com.githow.links.data.entity.Transaction
import com.githow.links.utils.MpesaParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "mpesa_transactions"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "LINKS_SMS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📱 SMS Receiver triggered! Action: ${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.w(TAG, "⚠️ Wrong action received: ${intent.action}")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        Log.d(TAG, "📨 Received ${messages.size} SMS message(s)")

        // Concatenate all SMS parts from the same sender
        val mpesaMessages = mutableMapOf<String, StringBuilder>()

        for (smsMessage in messages) {
            val messageBody = smsMessage.messageBody
            val sender = smsMessage.displayOriginatingAddress

            Log.d(TAG, "📧 SMS part from: $sender")
            Log.d(TAG, "📄 Part content: ${messageBody.take(100)}...")

            if (sender.contains("MPESA", ignoreCase = true) ||
                messageBody.startsWith("TK") ||
                messageBody.startsWith("TJ")) {

                // Group messages by sender
                if (!mpesaMessages.containsKey(sender)) {
                    mpesaMessages[sender] = StringBuilder()
                }
                mpesaMessages[sender]?.append(messageBody)
            } else {
                Log.d(TAG, "⏭️ Not an M-PESA SMS, skipping")
            }
        }

        // Process each complete M-PESA message
        mpesaMessages.forEach { (sender, messageBuilder) ->
            val completeMessage = messageBuilder.toString()
            Log.d(TAG, "✅ M-PESA SMS detected! Processing complete message from $sender")
            Log.d(TAG, "📝 Complete message length: ${completeMessage.length} chars")
            Log.d(TAG, "📝 Complete message: ${completeMessage.take(200)}...")
            handleMpesaSms(context, completeMessage)
        }
    }

    private fun handleMpesaSms(context: Context, messageBody: String) {
        val transaction = MpesaParser.parseTransaction(messageBody)

        if (transaction == null) {
            Log.e(TAG, "❌ Failed to parse transaction!")
            Log.e(TAG, "❌ Message was: $messageBody")
            return
        }

        Log.d(TAG, "✅ Parsed: ${transaction.mpesa_code} - Ksh${transaction.amount}")
        Log.d(TAG, "📊 Balance: Ksh${transaction.account_balance}, Cost: Ksh${transaction.transaction_cost}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = LinksDatabase.getDatabase(context)
                val dao = database.transactionDao()

                // Check for duplicates
                val existing = dao.getTransactionByCode(transaction.mpesa_code)
                if (existing != null) {
                    Log.w(TAG, "⚠️ Duplicate transaction: ${transaction.mpesa_code}")
                    return@launch
                }

                // Check for open shift and create new transaction with shift_id if found
                val openShift = dao.getOpenShift()
                val finalTransaction = if (openShift != null) {
                    Log.d(TAG, "📋 Assigning to shift: ${openShift.shift_id}")
                    // Create new Transaction with shift_id
                    Transaction(
                        mpesa_code = transaction.mpesa_code,
                        amount = transaction.amount,
                        sender_phone = transaction.sender_phone,
                        sender_name = transaction.sender_name,
                        paybill_number = transaction.paybill_number,
                        business_name = transaction.business_name,
                        timestamp = transaction.timestamp,
                        date_received = transaction.date_received,
                        time_received = transaction.time_received,
                        account_balance = transaction.account_balance,
                        transaction_cost = transaction.transaction_cost,
                        transaction_type = transaction.transaction_type,
                        shift_id = openShift.shift_id,  // Assign shift
                        assigned_to = transaction.assigned_to,
                        status = transaction.status,
                        created_at = transaction.created_at,
                        synced_at = transaction.synced_at
                    )
                } else {
                    Log.d(TAG, "⚠️ No open shift found")
                    transaction
                }

                val transactionId = dao.insertTransaction(finalTransaction)

                if (transactionId > 0) {
                    Log.d(TAG, "💾 Saved to database! ID: $transactionId")
                    showNotification(context, finalTransaction)
                } else {
                    Log.e(TAG, "❌ Failed to save to database")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error handling SMS: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun showNotification(context: Context, transaction: Transaction) {
        Log.d(TAG, "🔔 Showing notification")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "M-PESA Transactions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new M-PESA transactions"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val senderInfo = transaction.sender_name ?: transaction.business_name ?: "Unknown"
        val amountText = MpesaParser.formatAmount(transaction.amount)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New M-PESA Transaction")
            .setContentText("$amountText from $senderInfo")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$amountText received from $senderInfo. Balance: Ksh${String.format("%,.2f", transaction.account_balance)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}