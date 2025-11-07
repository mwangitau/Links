package com.githow.links.utils

import com.githow.links.data.entity.Transaction
import java.text.SimpleDateFormat
import java.util.*

object MpesaParser {

    fun parseTransaction(messageBody: String): Transaction? {
        try {
            if (!messageBody.startsWith("TJ") && !messageBody.contains("Confirmed")) {
                return null
            }

            val codeRegex = """^([A-Z0-9]{10})""".toRegex()
            val mpesaCode = codeRegex.find(messageBody)?.groupValues?.get(1) ?: return null

            val transactionType = when {
                messageBody.contains("received from", ignoreCase = true) -> "RECEIVED"
                messageBody.contains("Sent to", ignoreCase = true) -> "SENT"
                else -> return null
            }

            val amountRegex = """Ksh([\d,]+\.?\d*)""".toRegex()
            val amountMatch = amountRegex.find(messageBody) ?: return null
            val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

            val dateRegex = """(\d{2}/\d{2}/\d{2})""".toRegex()
            val dateReceived = dateRegex.find(messageBody)?.groupValues?.get(1) ?: return null

            val timeRegex = """at (\d{1,2}:\d{2} [AP]M)""".toRegex()
            val timeReceived = timeRegex.find(messageBody)?.groupValues?.get(1) ?: return null

            val balanceRegex = """Account [Bb]alance is Ksh([\d,]+\.?\d*)""".toRegex()
            val balanceMatch = balanceRegex.find(messageBody)
            val accountBalance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            val costRegex = """Transaction cost,? Ksh([\d,]+\.?\d*)""".toRegex()
            val costMatch = costRegex.find(messageBody)
            val transactionCost = costMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            var senderPhone: String? = null
            var senderName: String? = null
            var paybillNumber: String? = null
            var businessName: String? = null

            if (transactionType == "RECEIVED") {
                val paybillRegex = """from\s+(-?\s*)?(\d{6,7})\s*-\s*([^.]+)""".toRegex()
                val paybillMatch = paybillRegex.find(messageBody)

                if (paybillMatch != null) {
                    paybillNumber = paybillMatch.groupValues[2].trim()
                    businessName = paybillMatch.groupValues[3].trim()
                } else {
                    val personalRegex = """from (254\d{9})\s+([^.]+)""".toRegex()
                    val personalMatch = personalRegex.find(messageBody)

                    if (personalMatch != null) {
                        senderPhone = personalMatch.groupValues[1].trim()
                        senderName = personalMatch.groupValues[2].trim().removeSuffix(".")
                    }
                }
            } else if (transactionType == "SENT") {
                val sentToRegex = """Sent to (\d{6,7})\s*-\s*([^.]+)""".toRegex()
                val sentMatch = sentToRegex.find(messageBody)

                if (sentMatch != null) {
                    paybillNumber = sentMatch.groupValues[1].trim()
                    businessName = sentMatch.groupValues[2].trim()
                }
            }

            val timestamp = convertToTimestamp(dateReceived, timeReceived)

            return Transaction(
                mpesa_code = mpesaCode,
                amount = amount,
                sender_phone = senderPhone,
                sender_name = senderName,
                paybill_number = paybillNumber,
                business_name = businessName,
                timestamp = timestamp,
                date_received = dateReceived,
                time_received = timeReceived,
                account_balance = accountBalance,
                transaction_cost = transactionCost,
                transaction_type = transactionType,
                status = "pending"
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun convertToTimestamp(date: String, time: String): Long {
        return try {
            val dateTimeString = "$date $time"
            val format = SimpleDateFormat("dd/MM/yy h:mm a", Locale.US)
            format.parse(dateTimeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun formatAmount(amount: Double): String {
        return "Ksh ${String.format("%,.0f", amount)}"
    }

    fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US)
        return format.format(Date(timestamp))
    }
}