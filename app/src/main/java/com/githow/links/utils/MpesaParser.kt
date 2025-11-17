package com.githow.links.utils

import android.util.Log
import com.githow.links.data.entity.Transaction
import java.text.SimpleDateFormat
import java.util.*

object MpesaParser {

    private const val TAG = "MPESA_PARSER"

    // Your paybill numbers - add yours here
    private val INTERNAL_PAYBILLS = listOf("5176352", "5176338")

    fun parseTransaction(messageBody: String): Transaction? {
        try {
            Log.d(TAG, "📝 Parsing: ${messageBody.take(200)}")

            // ✅ IMPROVED: More flexible M-PESA detection
            val isMpesa = messageBody.startsWith("TK") ||
                    messageBody.startsWith("TJ") ||
                    messageBody.contains("Confirmed", ignoreCase = true) ||
                    (messageBody.contains("M-PESA", ignoreCase = true) && messageBody.contains("Ksh"))

            if (!isMpesa) {
                Log.e(TAG, "❌ Not a valid M-PESA message")
                return null
            }

            // ✅ Extract M-PESA code - More flexible
            val codeRegex = """^([A-Z]{2}[A-Z0-9]{8,10})""".toRegex()
            val mpesaCode = codeRegex.find(messageBody)?.groupValues?.get(1) ?: run {
                Log.e(TAG, "❌ No M-PESA code found")
                return null
            }

            // ✅ Determine transaction type
            val transactionType = when {
                messageBody.contains("Sent to", ignoreCase = true) -> "SENT"
                messageBody.contains("received from", ignoreCase = true) -> "RECEIVED"
                messageBody.contains("withdrawn", ignoreCase = true) -> "WITHDRAW"
                else -> "RECEIVED"  // Default
            }

            // ✅ IMPROVED: Extract amount - More precise to avoid getting balance
            // Look for amount BEFORE "received from" or after "Confirmed"
            val amountRegex = if (transactionType == "RECEIVED") {
                // For RECEIVED: Amount comes after PM/AM and before "received"
                """[AP]M\s*Ksh([\d,]+\.?\d*)\s+received""".toRegex()
            } else {
                // For others: Standard format
                """Ksh([\d,]+\.?\d*)""".toRegex()
            }

            val amountMatch = amountRegex.find(messageBody) ?: run {
                Log.e(TAG, "❌ No amount found")
                Log.e(TAG, "Message: $messageBody")
                return null
            }
            val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: run {
                Log.e(TAG, "❌ Invalid amount: ${amountMatch.groupValues[1]}")
                return null
            }

            // ✅ IMPROVED: Extract date - Handle "on DD/MM/YY"
            val dateRegex = """on\s+(\d{1,2}/\d{1,2}/\d{2})""".toRegex()
            val dateReceived = dateRegex.find(messageBody)?.groupValues?.get(1) ?: run {
                Log.e(TAG, "❌ No date found")
                return null
            }

            // ✅ IMPROVED: Extract time - Handle with or without space before Ksh
            val timeRegex = """at\s+(\d{1,2}:\d{2}\s+[AP]M)""".toRegex()
            val timeReceived = timeRegex.find(messageBody)?.groupValues?.get(1) ?: run {
                Log.e(TAG, "❌ No time found")
                return null
            }

            // ✅ IMPROVED: Extract balance - Handle ALL variations
            // Handles: "New Account balance", "New Merchant Account Balance", "Account Balance"
            val balanceRegex = """(?:New\s+)?(?:Merchant\s+)?Account\s+[Bb]alance\s+is\s+Ksh([\d,]+\.?\d*)""".toRegex()
            val balanceMatch = balanceRegex.find(messageBody)
            val accountBalance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            if (accountBalance == 0.0) {
                Log.w(TAG, "⚠️ No balance found or balance is 0")
            }

            // ✅ IMPROVED: Extract transaction cost - Handle when missing
            val costRegex = """Transaction\s+cost,?\s+Ksh([\d,]+\.?\d*)""".toRegex()
            val costMatch = costRegex.find(messageBody)
            val transactionCost = costMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            var senderPhone: String? = null
            var senderName: String? = null
            var paybillNumber: String? = null
            var businessName: String? = null

            // Check if this is an internal transfer
            var isInternalTransfer = false
            var isHidden = false

            if (transactionType == "RECEIVED") {
                // ✅ IMPROVED: Try multiple sender format patterns

                // Pattern 1: KopoPopo format: "721444-KOPOKOPO MERCHANT PAYMENTS:BUSINESS-REF"
                val kopopoRegex = """from\s+(\d{6})-([^.]+)""".toRegex()
                val kopopoMatch = kopopoRegex.find(messageBody)

                if (kopopoMatch != null) {
                    paybillNumber = kopopoMatch.groupValues[1].trim()
                    businessName = kopopoMatch.groupValues[2].trim()
                        .replace(Regex("""-\d+$"""), "")  // Remove trailing reference numbers
                        .take(100)  // Limit length

                    Log.d(TAG, "📊 KopoPopo: $paybillNumber - $businessName")
                }
                // Pattern 2: Business/Merchant format: "254XXXXXXXXX 6703985 - BUSINESS NAME"
                else {
                    val merchantRegex = """from\s+(254\d{9})\s+(\d+)\s*-\s*([^.]+)""".toRegex()
                    val merchantMatch = merchantRegex.find(messageBody)

                    if (merchantMatch != null) {
                        senderPhone = merchantMatch.groupValues[1].trim()
                        val accountNumber = merchantMatch.groupValues[2].trim()
                        businessName = merchantMatch.groupValues[3].trim()
                        senderName = "$accountNumber - $businessName"

                        Log.d(TAG, "📊 Merchant: $senderPhone - $senderName")
                    }
                    // Pattern 3: Standard paybill/till: "from - 5176352 - BUSINESS NAME" or "from 5176352 - BUSINESS"
                    else {
                        val paybillRegex = """from\s+(?:-\s*)?(\d{6,7})\s*-\s*([^.]+)""".toRegex()
                        val paybillMatch = paybillRegex.find(messageBody)

                        if (paybillMatch != null) {
                            paybillNumber = paybillMatch.groupValues[1].trim()
                            businessName = paybillMatch.groupValues[2].trim()

                            // Check if this is from your own paybill
                            if (INTERNAL_PAYBILLS.contains(paybillNumber)) {
                                isInternalTransfer = true
                                isHidden = true
                                Log.d(TAG, "🔄 Internal transfer detected from $paybillNumber - will be hidden")
                            }

                            Log.d(TAG, "📊 Paybill: $paybillNumber - $businessName")
                        }
                        // Pattern 4: Personal payment: "from 254XXXXXXXXX NAME"
                        else {
                            val personalRegex = """from\s+(254\d{9})\s+([^.]+)""".toRegex()
                            val personalMatch = personalRegex.find(messageBody)

                            if (personalMatch != null) {
                                senderPhone = personalMatch.groupValues[1].trim()
                                senderName = personalMatch.groupValues[2].trim()
                                    .removeSuffix(".")
                                    .replace(Regex("""\s+"""), " ")  // Normalize spaces
                                    .trim()
                                Log.d(TAG, "👤 Personal: $senderPhone - $senderName")
                            } else {
                                Log.w(TAG, "⚠️ No sender info extracted")
                            }
                        }
                    }
                }
            } else if (transactionType == "SENT") {
                // Extract recipient for SENT transactions
                val sentToRegex = """Sent to\s+(\d{6,7})\s*-\s*([^.]+)""".toRegex()
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
                sms_body = messageBody,  // Store original SMS for debugging
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