package com.githow.links.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.githow.links.R
import com.githow.links.data.database.LinksDatabase
import com.githow.links.utils.MpesaParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "mpesa_transactions"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (smsMessage in messages) {
            val messageBody = smsMessage.messageBody
            val sender = smsMessage.displayOriginatingAddress

            if (sender.contains("MPESA", ignoreCase = true) ||
                messageBody.startsWith("TJ")) {

                handleMpesaSms(context, messageBody)
            }
        }
    }

    private fun handleMpesaSms(context: Context, messageBody: String) {
        val transaction = MpesaParser.parseTransaction(messageBody) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = LinksDatabase.getDatabase(context)
                val dao = database.transactionDao()

                val existing = dao.getTransactionByCode(transaction.mpesa_code)
                if (existing != null) {
                    return@launch
                }

                val openShift = dao.getOpenShift()
                val transactionWithShift = if (openShift != null) {
                    transaction.copy(shift_id = openShift.shift_id)
                } else {
                    transaction
                }

                val transactionId = dao.insertTransaction(transactionWithShift)

                if (transactionId > 0) {
                    showNotification(context, transaction)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showNotification(context: Context, transaction: com.githow.links.data.entity.Transaction) {
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New M-PESA Transaction")
            .setContentText("${MpesaParser.formatAmount(transaction.amount)} from ${transaction.sender_name ?: transaction.business_name ?: "Unknown"}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${MpesaParser.formatAmount(transaction.amount)} received from ${transaction.sender_name ?: transaction.business_name ?: "Unknown"}. Tap to assign."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}