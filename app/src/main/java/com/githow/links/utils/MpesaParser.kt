package com.githow.links.utils

import android.util.Log
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.TransactionRole
import com.githow.links.data.entity.transactionTypeToRole
import com.githow.links.data.entity.withRole
import java.text.SimpleDateFormat
import java.util.*


object MpesaParser {

    private const val TAG = "MPESA_PARSER"

    fun parseTransaction(messageBody: String): Transaction? {
        try {
            val cleanedMessage = sanitizeInput(messageBody)

            Log.d(TAG, "📝 Parsing: ${cleanedMessage.take(200)}")

            if (!isValidMpesaMessage(cleanedMessage)) {
                Log.e(TAG, "❌ Not a valid M-PESA message")
                return tryTruncatedParsing(messageBody)
            }

            val mpesaCode = extractTransactionCode(cleanedMessage) ?: run {
                Log.e(TAG, "❌ No M-PESA code found")
                return tryTruncatedParsing(messageBody)
            }

            val transactionType = determineTransactionType(cleanedMessage)
            Log.d(TAG, "📊 Transaction type: $transactionType")

            val rawAmount = extractAmount(cleanedMessage, transactionType) ?: run {
                Log.e(TAG, "❌ No amount found in: $cleanedMessage")
                return tryTruncatedParsing(messageBody)
            }

            val amount = rawAmount

            val originalTransactionCode = if (transactionType == "REVERSAL") {
                extractReversalTransactionCode(cleanedMessage)
            } else {
                null
            }

            val dateReceived = extractDate(cleanedMessage) ?: run {
                Log.e(TAG, "❌ No date found")
                return tryTruncatedParsing(messageBody)
            }

            val timeReceived = extractTime(cleanedMessage) ?: run {
                Log.e(TAG, "❌ No time found")
                return tryTruncatedParsing(messageBody)
            }

            val accountBalance = extractBalance(cleanedMessage)
            if (accountBalance == 0.0) {
                Log.w(TAG, "⚠️ No balance found or balance is 0")
            }

            val transactionCost = extractTransactionCost(cleanedMessage)
            val senderInfo = extractSenderInfo(cleanedMessage, transactionType)

            // FIX: Every transaction arrives as UNASSIGNED so the manager
            // can review and assign the correct role manually on the assignment screen.
            val initialRole = TransactionRole.UNASSIGNED

            val timestamp = convertToTimestamp(dateReceived, timeReceived)

            Log.d(TAG, "✅ Parsed: $mpesaCode Ksh$amount $transactionType → role=UNASSIGNED (pending manager review)")

            if (transactionType == "REVERSAL") {
                Log.w(TAG, "⚠️ REVERSAL: original=${originalTransactionCode ?: "Unknown"}")
            }

            val transaction = Transaction(
                mpesa_code = mpesaCode,
                amount = amount,
                sender_phone = senderInfo.senderPhone,
                sender_name = senderInfo.senderName,
                paybill_number = senderInfo.paybillNumber,
                business_name = senderInfo.businessName,
                timestamp = timestamp,
                date_received = dateReceived,
                time_received = timeReceived,
                account_balance = accountBalance,
                transaction_cost = transactionCost,
                sms_body = messageBody,
                transaction_type = transactionType,
                assigned_to = null,
                transaction_category = null,
                is_hidden = false,
                is_internal_transfer = false,
                status = "pending"
            )

            return transaction.withRole(initialRole)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Parsing error: ${e.message}", e)
            e.printStackTrace()
            Log.w(TAG, "🔄 Attempting truncated message recovery...")
            return tryTruncatedParsing(messageBody)
        }
    }

    private fun isValidMpesaMessage(message: String): Boolean {
        if (message.contains("reversal", ignoreCase = true)) {
            return message.contains("Ksh", ignoreCase = true)
        }

        val hasValidCode = message.matches("""^[QRSTU][A-L][A-Z0-9]{8}.*""".toRegex(RegexOption.IGNORE_CASE)) ||
                message.startsWith("TEST", ignoreCase = true)

        return hasValidCode &&
                message.contains("Confirmed", ignoreCase = true) &&
                message.contains("Ksh", ignoreCase = true) &&
                (message.contains("received from", ignoreCase = true) ||
                        message.contains("Sent to", ignoreCase = true) ||
                        message.contains("withdrawn", ignoreCase = true) ||
                        message.contains("deposited", ignoreCase = true) ||
                        message.contains("airtime for", ignoreCase = true) ||
                        message.contains("paid to", ignoreCase = true))
    }

    private fun extractTransactionCode(message: String): String? {
        val codeRegex = """^([QRSTU][A-L][A-Z0-9]{8}|TEST\d+)""".toRegex(RegexOption.IGNORE_CASE)
        return codeRegex.find(message)?.groupValues?.get(1)
    }

    private fun determineTransactionType(message: String): String {
        return when {
            message.contains("reversal of", ignoreCase = true) -> "REVERSAL"
            message.contains("reversed", ignoreCase = true) -> "REVERSAL"
            message.contains("Sent to", ignoreCase = true) -> "SENT"
            message.contains("withdrawn", ignoreCase = true) -> "WITHDRAW"
            message.contains("Withdraw", ignoreCase = true) -> "WITHDRAW"
            message.contains("deposited", ignoreCase = true) -> "DEPOSIT"
            message.contains("Give", ignoreCase = true) &&
                    message.contains("cash to", ignoreCase = true) -> "DEPOSIT"
            message.contains("airtime for", ignoreCase = true) -> "AIRTIME"
            message.contains("paid to", ignoreCase = true) &&
                    message.contains("for account", ignoreCase = true) -> "BILL_PAYMENT"
            message.contains("paid to", ignoreCase = true) -> "BUY_GOODS"
            message.contains("received from", ignoreCase = true) -> "RECEIVED"
            else -> "RECEIVED"
        }
    }

    private fun extractAmount(message: String, transactionType: String): Double? {
        val patterns = when (transactionType) {
            "REVERSAL" -> listOf(
                """reversal of\s+[A-Z0-9]+\s+of\s+Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE),
                """reversed\s+Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE),
                """Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE)
            )
            "RECEIVED" -> listOf(
                """[AP]M\s*Ksh\s*([\d,]+\.?\d*)\s*received""".toRegex(RegexOption.IGNORE_CASE),
                """Ksh\s*([\d,]+\.?\d*)\s*received""".toRegex(RegexOption.IGNORE_CASE)
            )
            "SENT" -> listOf(
                """Ksh\s*([\d,]+\.?\d*)\s+Sent\s+to""".toRegex(RegexOption.IGNORE_CASE),
                """Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE)
            )
            "DEPOSIT" -> listOf(
                """deposited\s+Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE),
                """Give\s+Ksh\s*([\d,]+\.?\d*)\s+cash""".toRegex(RegexOption.IGNORE_CASE)
            )
            "AIRTIME" -> listOf(
                """Ksh\s*([\d,]+\.?\d*)\s+airtime""".toRegex(RegexOption.IGNORE_CASE)
            )
            "BILL_PAYMENT", "BUY_GOODS" -> listOf(
                """Ksh\s*([\d,]+\.?\d*)\s+paid\s+to""".toRegex(RegexOption.IGNORE_CASE)
            )
            else -> listOf(
                """Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE)
            )
        }

        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                return amountStr.toDoubleOrNull()
            }
        }

        return null
    }

    private fun extractReversalTransactionCode(message: String): String? {
        val reversalRegex = """reversal of\s+([A-Z]{2}[A-Z0-9]{8,10})""".toRegex(RegexOption.IGNORE_CASE)
        return reversalRegex.find(message)?.groupValues?.get(1)
    }

    private fun extractDate(message: String): String? {
        val dateRegex = """[.\s]on\s+(\d{1,2}/\d{1,2}/\d{2,4})""".toRegex(RegexOption.IGNORE_CASE)
        return dateRegex.find(message)?.groupValues?.get(1)
    }

    private fun extractTime(message: String): String? {
        val timeRegex = """at\s+(\d{1,2}:\d{1,2})\s*([AP]M)""".toRegex(RegexOption.IGNORE_CASE)
        val match = timeRegex.find(message)
        return if (match != null) "${match.groupValues[1]} ${match.groupValues[2]}" else null
    }

    private fun extractBalance(message: String): Double {
        val balanceRegex = """(?:New\s+)?(?:Merchant\s+)?Account\s+[Bb]alance\s+is\s+Ksh\s*([\d,]+\.?\d*)""".toRegex()
        val match = balanceRegex.find(message)
        return match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
    }

    private fun extractTransactionCost(message: String): Double {
        val costRegex = """Transaction\s+cost,?\s+Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE)
        val match = costRegex.find(message)
        return match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
    }

    private fun extractSenderInfo(message: String, transactionType: String): SenderInfo {
        if (transactionType == "REVERSAL") return extractReversalInfo(message)
        if (transactionType == "SENT") return extractSentToInfo(message)
        if (transactionType == "DEPOSIT") return extractDepositInfo(message)
        if (transactionType == "WITHDRAW") return extractWithdrawalInfo(message)
        if (transactionType == "AIRTIME") return extractAirtimeInfo(message)
        if (transactionType == "BILL_PAYMENT" || transactionType == "BUY_GOODS") return extractPaymentInfo(message)

        extractKopoPopo(message)?.let { return it }
        extractBonga(message)?.let { return it }
        extractB2C(message)?.let { return it }
        extractPesaPal(message)?.let { return it }
        extractCoopToTill(message)?.let { return it }
        extractBankToTill(message)?.let { return it }
        extractMerchant(message)?.let { return it }
        extractPaybill(message)?.let { return it }
        extractPersonal(message)?.let { return it }

        Log.w(TAG, "⚠️ No sender info extracted from: ${message.take(100)}")
        return SenderInfo()
    }

    private fun extractKopoPopo(message: String): SenderInfo? {
        val regex = """from\s+(\d{6})-([^.]+)""".toRegex()
        val match = regex.find(message) ?: return null
        val paybillNumber = match.groupValues[1].trim()
        var businessName = match.groupValues[2].trim()
            .replace(Regex("""-\d+$"""), "")
            .take(100)
        return SenderInfo(paybillNumber = paybillNumber, businessName = businessName)
    }

    private fun extractBonga(message: String): SenderInfo? {
        val regex = """from\s+(\d+)-Bonga\s+Everywhere\s+Services[^!]+!([^!\n]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message) ?: return null
        return SenderInfo(
            paybillNumber = match.groupValues[1].trim(),
            businessName = "Bonga: ${match.groupValues[2].trim().take(80)}"
        )
    }

    private fun extractB2C(message: String): SenderInfo? {
        val regex = """from\s+(\d+)-([^:]+B2C[^:]*):([^.]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message) ?: return null
        return SenderInfo(
            paybillNumber = match.groupValues[1].trim(),
            businessName = "${match.groupValues[2].trim()}: ${match.groupValues[3].trim()}".take(100)
        )
    }

    private fun extractPesaPal(message: String): SenderInfo? {
        val regex = """from\s+(\d+)-PesaPal[^:]*:(.+?)(?:\.|New)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message) ?: return null
        return SenderInfo(
            paybillNumber = match.groupValues[1].trim(),
            businessName = "PesaPal: ${match.groupValues[2].trim()}".take(100)
        )
    }

    private fun extractCoopToTill(message: String): SenderInfo? {
        val regex = """from\s+(\d+)-CO-OP\s+TO\s+TILL:?([^:]*?)(?:::|\.|\s*New)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message) ?: return null
        val customerInfo = match.groupValues[2].trim()
        return SenderInfo(
            paybillNumber = match.groupValues[1].trim(),
            businessName = if (customerInfo.isNotEmpty()) "CO-OP: $customerInfo" else "CO-OP TO TILL"
        )
    }

    private fun extractBankToTill(message: String): SenderInfo? {
        val regex = """from\s+(\d+)-([^:]+?):(.+?)(?:\.|New)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message) ?: return null
        val bankName = match.groupValues[2].trim()
        if (bankName.contains("B2C", ignoreCase = true) ||
            bankName.contains("Bonga", ignoreCase = true) ||
            bankName.contains("PesaPal", ignoreCase = true)) return null
        return SenderInfo(
            paybillNumber = match.groupValues[1].trim(),
            businessName = "$bankName: ${match.groupValues[3].trim()}".take(100)
        )
    }

    private fun extractMerchant(message: String): SenderInfo? {
        val regex = """from\s+(254\d{9})\s+(\d+)\s*-\s*([^.]+)""".toRegex()
        val match = regex.find(message) ?: return null
        return SenderInfo(
            senderPhone = match.groupValues[1].trim(),
            senderName = "${match.groupValues[2].trim()} - ${match.groupValues[3].trim()}".take(100),
            businessName = match.groupValues[3].trim().take(100)
        )
    }

    private fun extractPaybill(message: String): SenderInfo? {
        val regex = """from\s+(?:-\s*)?(\d{6,7})\s*-\s*([^.]+)""".toRegex()
        val match = regex.find(message) ?: return null
        return SenderInfo(
            paybillNumber = match.groupValues[1].trim(),
            businessName = match.groupValues[2].trim()
                .removeSuffix(".")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(100)
        )
    }

    private fun extractPersonal(message: String): SenderInfo? {
        val regex = """from\s+(254\d{9})\s+([^.]+)""".toRegex()
        val match = regex.find(message) ?: return null
        val cleanName = match.groupValues[2].trim()
            .removeSuffix(".")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""New\s+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""Transaction\s+.*""", RegexOption.IGNORE_CASE), "")
            .trim()
        return SenderInfo(senderPhone = match.groupValues[1].trim(), senderName = cleanName)
    }

    private fun extractSentToInfo(message: String): SenderInfo {
        val regex = """Sent to\s+(\d{6,7})\s*-\s*([^.]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message)
        return if (match != null) {
            SenderInfo(
                paybillNumber = match.groupValues[1].trim(),
                businessName = match.groupValues[2].trim()
                    .removeSuffix(".")
                    .replace(Regex("""New\s+.*""", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .take(100)
            )
        } else SenderInfo()
    }

    private fun extractReversalInfo(message: String): SenderInfo {
        val patterns = listOf(
            """(?:from|to)\s+(254\d{9})""".toRegex(RegexOption.IGNORE_CASE),
            """(254\d{9})""".toRegex()
        )
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                return SenderInfo(
                    senderPhone = match.groupValues[1].trim(),
                    senderName = "REVERSAL - Customer took money back"
                )
            }
        }
        return SenderInfo(senderName = "REVERSAL - Customer took money back")
    }

    private fun extractDepositInfo(message: String): SenderInfo {
        val regex = """(?:at|from)\s+([^0-9]+?)\s*(?:Agent\s+)?(254\d{9}|07\d{8})""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message)
        return if (match != null) {
            SenderInfo(
                senderPhone = match.groupValues[2].trim(),
                senderName = "Deposit via ${match.groupValues[1].trim()}"
            )
        } else SenderInfo(senderName = "Cash Deposit")
    }

    private fun extractWithdrawalInfo(message: String): SenderInfo {
        if (message.contains("ATM", ignoreCase = true)) return SenderInfo(senderName = "ATM Withdrawal")
        val regex = """(?:from|at)\s+([^0-9]+?)\s*(254\d{9}|07\d{8})?""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message)
        return if (match != null) {
            SenderInfo(
                senderPhone = match.groupValues.getOrNull(2)?.trim(),
                senderName = "Withdrawal via ${match.groupValues[1].trim()}"
            )
        } else SenderInfo(senderName = "Cash Withdrawal")
    }

    private fun extractAirtimeInfo(message: String): SenderInfo {
        val regex = """airtime for\s+(254\d{9}|07\d{8})""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message)
        return if (match != null) {
            SenderInfo(senderPhone = match.groupValues[1].trim(), senderName = "Airtime Purchase")
        } else SenderInfo(senderName = "Airtime Purchase")
    }

    private fun extractPaymentInfo(message: String): SenderInfo {
        val patterns = listOf(
            """paid to\s+([^f]+?)\s+for account\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE),
            """paid to\s+([^.]+?)\.?(?:\s+on|\s+New|$)""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                val businessName = match.groupValues[1].trim()
                val accountNumber = match.groupValues.getOrNull(2)?.trim()
                val fullName = if (!accountNumber.isNullOrBlank()) "$businessName (Acc: $accountNumber)" else businessName
                return SenderInfo(businessName = fullName.take(100))
            }
        }
        return SenderInfo(senderName = "Bill Payment")
    }

    private data class SenderInfo(
        val senderPhone: String? = null,
        val senderName: String? = null,
        val paybillNumber: String? = null,
        val businessName: String? = null
    )

    private fun sanitizeInput(message: String): String {
        return message
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[,\\.]+\\s*$"), "")
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
    }

    private fun tryTruncatedParsing(message: String): Transaction? {
        try {
            Log.w(TAG, "🔄 Attempting truncated message recovery...")
            val cleaned = sanitizeInput(message)

            val mpesaCode = extractTransactionCode(cleaned) ?: run {
                Log.e(TAG, "❌ Truncated recovery: No code found")
                return null
            }
            val dateReceived = extractDate(cleaned) ?: run {
                Log.e(TAG, "❌ Truncated recovery: No date found")
                return null
            }
            val timeReceived = extractTime(cleaned) ?: run {
                Log.e(TAG, "❌ Truncated recovery: No time found")
                return null
            }
            val amountRegex = """Ksh\s*([\d,]+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE)
            val amount = amountRegex.find(cleaned)
                ?.groupValues?.get(1)
                ?.replace(",", "")
                ?.toDoubleOrNull() ?: run {
                Log.e(TAG, "❌ Truncated recovery: No amount found")
                return null
            }

            val phoneRegex = """(254\d{9})""".toRegex()
            val phone = phoneRegex.find(cleaned)?.groupValues?.get(1)

            val nameRegex = """from\s+\d+\s+([^.]+)""".toRegex(RegexOption.IGNORE_CASE)
            val senderName = nameRegex.find(cleaned)
                ?.groupValues?.get(1)
                ?.trim()
                ?.replace(Regex("[,\\s]+$"), "")

            val timestamp = convertToTimestamp(dateReceived, timeReceived)

            Log.w(TAG, "⚠️ Truncated recovery: $mpesaCode Ksh$amount — UNASSIGNED pending review")

            val truncatedTxn = Transaction(
                mpesa_code = mpesaCode,
                amount = amount,
                sender_phone = phone,
                sender_name = senderName ?: "INCOMPLETE DATA - NEEDS REVIEW",
                paybill_number = null,
                business_name = null,
                timestamp = timestamp,
                date_received = dateReceived,
                time_received = timeReceived,
                account_balance = 0.0,
                transaction_cost = 0.0,
                sms_body = message,
                transaction_type = "RECEIVED",
                assigned_to = null,
                transaction_category = null,
                is_hidden = false,
                is_internal_transfer = false,
                status = "needs_review"
            )
            // Truncated messages always UNASSIGNED — manager must review
            return truncatedTxn.withRole(TransactionRole.UNASSIGNED)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Truncated parsing also failed: ${e.message}")
            return null
        }
    }

    private fun convertToTimestamp(date: String, time: String): Long {
        return try {
            val format = SimpleDateFormat("d/M/yy h:mm a", Locale.US)
            format.timeZone = TimeZone.getTimeZone("Africa/Nairobi")
            format.parse("$date $time")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing timestamp: ${e.message}")
            System.currentTimeMillis()
        }
    }

    fun formatAmount(amount: Double): String = "Ksh ${String.format("%,.0f", amount)}"

    fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US)
        return format.format(Date(timestamp))
    }

    fun getYearFromCode(mpesaCode: String): Int? {
        if (mpesaCode.isEmpty()) return null
        return when (mpesaCode[0].uppercaseChar()) {
            'Q' -> 2022; 'R' -> 2023; 'S' -> 2024; 'T' -> 2025
            'U' -> 2026; 'V' -> 2027; 'W' -> 2028; 'X' -> 2029
            'Y' -> 2030; 'Z' -> 2031; else -> null
        }
    }

    fun getMonthFromCode(mpesaCode: String): Int? {
        if (mpesaCode.length < 2) return null
        return when (mpesaCode[1].uppercaseChar()) {
            'A' -> 1; 'B' -> 2; 'C' -> 3; 'D' -> 4
            'E' -> 5; 'F' -> 6; 'G' -> 7; 'H' -> 8
            'I' -> 9; 'J' -> 10; 'K' -> 11; 'L' -> 12
            else -> null
        }
    }

    fun getTransactionPeriod(mpesaCode: String): String? {
        val year = getYearFromCode(mpesaCode) ?: return null
        val month = getMonthFromCode(mpesaCode) ?: return null
        val monthName = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )[month - 1]
        return "$monthName $year"
    }
}