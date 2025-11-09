package com.githow.links.utils

import android.util.Log
import com.githow.links.data.entity.Transaction
import java.text.SimpleDateFormat
import java.util.*

object MpesaParser {

    private const val TAG = "MPESA_PARSER"

    // Your paybill numbers - add yours here
    private val INTERNAL_PAYBILLS = listOf("5176352", "5176338")  // Add your paybill numbers

    fun parseTransaction(messageBody: String): Transaction? {
        try {
            Log.d(TAG, "📝 Parsing: ${messageBody.take(150)}")

            if (!messageBody.startsWith("TK") && !messageBody.startsWith("TJ") && !messageBody.contains("Confirmed")) {
                Log.e(TAG, "❌ Not a valid M-PESA message")
                return null
            }

            // Extract M-PESA code
            val codeRegex = """^([A-Z]{2}[A-Z0-9]{8,10})""".toRegex()
            val mpesaCode = codeRegex.find(messageBody)?.groupValues?.get(1) ?: run {
                Log.e(TAG, "❌ No M-PESA code found")
                return null
            }

            // Determine transaction type
            val transactionType = when {
                messageBody.contains("Sent to", ignoreCase = true) -> "SENT"
                messageBody.contains("received from", ignoreCase = true) -> "RECEIVED"
                messageBody.contains("withdrawn", ignoreCase = true) -> "WITHDRAW"
                else -> "RECEIVED"
            }

            // Extract amount
            val amountRegex = """Ksh([\d,]+\.?\d*)""".toRegex()
            val amountMatch = amountRegex.find(messageBody) ?: run {
                Log.e(TAG, "❌ No amount found")
                return null
            }
            val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: run {
                Log.e(TAG, "❌ Invalid amount")
                return null
            }

            // Extract date/time
            val dateRegex = """(\d{1,2}/\d{1,2}/\d{2})""".toRegex()
            val dateReceived = dateRegex.find(messageBody)?.groupValues?.get(1) ?: run {
                Log.e(TAG, "❌ No date found")
                return null
            }

            val timeRegex = """at (\d{1,2}:\d{2} [AP]M)""".toRegex()
            val timeReceived = timeRegex.find(messageBody)?.groupValues?.get(1) ?: run {
                Log.e(TAG, "❌ No time found")
                return null
            }

            // Extract balance
            val balanceRegex = """(?:New )?(?:Merchant )?Account [Bb]alance is Ksh([\d,]+\.?\d*)""".toRegex()
            val balanceMatch = balanceRegex.find(messageBody)
            val accountBalance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            // Extract cost
            val costRegex = """Transaction cost,? Ksh([\d,]+\.?\d*)""".toRegex()
            val costMatch = costRegex.find(messageBody)
            val transactionCost = costMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            var senderPhone: String? = null
            var senderName: String? = null
            var paybillNumber: String? = null
            var businessName: String? = null

            // NEW: Check if this is an internal transfer
            var isInternalTransfer = false
            var isHidden = false

            if (transactionType == "RECEIVED") {
                // Check for paybill/till format first
                val paybillRegex = """from\s+(-\s*)?(\d{6,7})\s*-\s*([^.]+)""".toRegex()
                val paybillMatch = paybillRegex.find(messageBody)

                if (paybillMatch != null) {
                    paybillNumber = paybillMatch.groupValues[2].trim()
                    businessName = paybillMatch.groupValues[3].trim()

                    // Check if this is from your own paybill
                    if (INTERNAL_PAYBILLS.contains(paybillNumber)) {
                        isInternalTransfer = true
                        isHidden = true  // Hide the "received" half of internal transfer
                        Log.d(TAG, "🔄 Internal transfer detected from $paybillNumber - will be hidden")
                    }

                    Log.d(TAG, "📊 Till/Paybill: $paybillNumber - $businessName")
                } else {
                    // Personal format
                    val personalRegex = """from (254\d{9})\s+([^.]+)""".toRegex()
                    val personalMatch = personalRegex.find(messageBody)

                    if (personalMatch != null) {
                        senderPhone = personalMatch.groupValues[1].trim()
                        senderName = personalMatch.groupValues[2].trim().removeSuffix(".")
                        Log.d(TAG, "👤 Personal: $senderPhone - $senderName")
                    }
                }
            } else if (transactionType == "SENT") {
                // Extract recipient for SENT transactions
                val sentToRegex = """Sent to (\d{6,7})\s*-\s*([^.]+)""".toRegex()
                val sentMatch = sentToRegex.find(messageBody)

                if (sentMatch != null) {
                    paybillNumber = sentMatch.groupValues[1].trim()
                    businessName = sentMatch.groupValues[2].trim()
                }
            }

            val timestamp = convertToTimestamp(dateReceived, timeReceived)

            // Determine transaction category
            val transactionCategory = when {
                transactionType == "SENT" -> "TRANSFER"
                transactionType == "WITHDRAW" -> "WITHDRAWAL"
                isInternalTransfer -> "INTERNAL_TRANSFER"
                else -> null  // Will be assigned later (CSA, DEBT_PAID, etc.)
            }

            Log.d(TAG, "✅ Parsed successfully: $mpesaCode - Ksh$amount - $transactionType" +
                    if (isHidden) " (HIDDEN)" else "")

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
                transaction_category = transactionCategory,
                is_hidden = isHidden,
                is_internal_transfer = isInternalTransfer,
                status = "pending"
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Parsing error: ${e.message}", e)
            e.printStackTrace()
            return null
        }
    }

    private fun convertToTimestamp(date: String, time: String): Long {
        return try {
            val dateTimeString = "$date $time"
            val format = SimpleDateFormat("d/M/yy h:mm a", Locale.US)
            format.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
            format.parse(dateTimeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing timestamp: ${e.message}")
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