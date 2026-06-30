package com.githow.links.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.FileProvider
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.TransactionDirection
import com.githow.links.data.entity.TransactionRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * ShiftPdfExporter
 *
 * Generates a printable PDF shift report containing:
 *   - Shift header (date, time, station, status)
 *   - Per-CSA breakdown (who collected how much + their individual transactions)
 *   - Reconciliation summary (opening/closing balance, expected float, variance)
 *
 * Uses Android's built-in PdfDocument API — no external library needed.
 * Output is shared via Android's share sheet (WhatsApp, Email, Drive, etc.)
 */
object ShiftPdfExporter {

    private const val TAG = "ShiftPdfExporter"

    // ── Page dimensions (A4 at 72dpi) ────────────────────────────────────────
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN)

    // ── Colours ───────────────────────────────────────────────────────────────
    private val COLOR_PRIMARY = Color.rgb(0, 105, 62)       // Shell green
    private val COLOR_ERROR = Color.rgb(176, 0, 32)
    private val COLOR_SURFACE = Color.rgb(245, 245, 245)
    private val COLOR_TEXT_PRIMARY = Color.rgb(20, 20, 20)
    private val COLOR_TEXT_SECONDARY = Color.rgb(100, 100, 100)
    private val COLOR_DIVIDER = Color.rgb(220, 220, 220)
    private val COLOR_WHITE = Color.WHITE

    // ── Paint objects ─────────────────────────────────────────────────────────
    private fun titlePaint() = Paint().apply {
        color = COLOR_WHITE
        textSize = 20f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private fun headingPaint() = Paint().apply {
        color = COLOR_TEXT_PRIMARY
        textSize = 13f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private fun bodyPaint() = Paint().apply {
        color = COLOR_TEXT_PRIMARY
        textSize = 11f
        isAntiAlias = true
    }

    private fun smallPaint() = Paint().apply {
        color = COLOR_TEXT_SECONDARY
        textSize = 9f
        isAntiAlias = true
    }

    private fun amountPaint(color: Int = COLOR_PRIMARY) = Paint().apply {
        this.color = color
        textSize = 11f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    private fun fillPaint(color: Int) = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private fun dividerPaint() = Paint().apply {
        color = COLOR_DIVIDER
        strokeWidth = 0.5f
        isAntiAlias = true
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Generate the PDF and launch Android share sheet.
     * Must be called from a coroutine (runs file I/O on Dispatchers.IO).
     */
    suspend fun exportAndShare(
        context: Context,
        shift: Shift,
        transactions: List<Transaction>,
        stationName: String = "LINKS Station"
    ) = withContext(Dispatchers.IO) {
        try {
            val pdf = buildPdf(shift, transactions, stationName)
            val file = savePdf(context, pdf, shift)
            pdf.close()
            withContext(Dispatchers.Main) {
                sharePdf(context, file, shift)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ PDF export failed: ${e.message}", e)
        }
    }

    // ── PDF construction ──────────────────────────────────────────────────────

    private fun buildPdf(
        shift: Shift,
        transactions: List<Transaction>,
        stationName: String
    ): PdfDocument {
        val pdf = PdfDocument()

        // Organise data
        val csaGroups = transactions
            .filter {
                it.role == TransactionRole.CUSTOMER_RECEIPT &&
                        !it.assigned_to.isNullOrBlank()
            }
            .groupBy { it.assigned_to!! }
            .toSortedMap()

        val tillTransferIn = transactions.filter { it.role == TransactionRole.TILL_TRANSFER_IN }
        val tillTransferOut = transactions.filter { it.role == TransactionRole.TILL_TRANSFER_OUT }
        val withdrawals = transactions.filter { it.role == TransactionRole.WITHDRAWAL }
        val reversals = transactions.filter { it.role == TransactionRole.REVERSAL }
        val duplicates = transactions.filter { it.role == TransactionRole.DUPLICATE }

        // ── Page manager ──────────────────────────────────────────────────────
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            pdf.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            page = pdf.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        fun checkPageBreak(needed: Float = 60f) {
            if (y + needed > PAGE_HEIGHT - MARGIN) newPage()
        }

        // ── Header banner ─────────────────────────────────────────────────────
        canvas.drawRect(MARGIN - 10, y, PAGE_WIDTH - MARGIN + 10, y + 70f, fillPaint(COLOR_PRIMARY))
        canvas.drawText("LINKS — Shift Report", MARGIN, y + 26f, titlePaint())
        canvas.drawText(stationName, MARGIN, y + 48f, Paint().apply {
            color = Color.rgb(200, 240, 220)
            textSize = 12f
            isAntiAlias = true
        })
        val statusText = if ((shift.variance ?: 0.0) == 0.0) "✓ BALANCED" else "⚠ DISCREPANCY"
        val statusColor = if ((shift.variance ?: 0.0) == 0.0) Color.rgb(150, 255, 180) else Color.rgb(255, 180, 180)
        val statusPaint = Paint().apply {
            color = statusColor
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(statusText, PAGE_WIDTH - MARGIN + 10, y + 48f, statusPaint)
        y += 80f

        // ── Shift info row ────────────────────────────────────────────────────
        val dateStr = formatDate(shift.start_time)
        val timeStr = "${formatTime(shift.start_time)} – ${formatTime(shift.end_time ?: shift.start_time)}"
        canvas.drawText("Date: $dateStr", MARGIN, y, bodyPaint())
        canvas.drawText("Time: $timeStr", MARGIN + CONTENT_WIDTH / 2, y, bodyPaint())
        y += 16f
        canvas.drawText("Shift: ${shift.shift_name ?: "Shift #${shift.shift_id}"}", MARGIN, y, bodyPaint())
        if (shift.closed_by != null) {
            canvas.drawText("Closed by: ${shift.closed_by}", MARGIN + CONTENT_WIDTH / 2, y, bodyPaint())
        }
        y += 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint())
        y += 16f

        // ── Section: Per-CSA breakdown ────────────────────────────────────────
        canvas.drawText("CUSTOMER RECEIPTS — PER CSA", MARGIN, y, headingPaint())
        y += 18f

        csaGroups.forEach { (csaName, csaTxns) ->
            checkPageBreak(50f)

            // CSA header row
            canvas.drawRect(MARGIN, y - 14f, PAGE_WIDTH - MARGIN, y + 4f, fillPaint(COLOR_SURFACE))
            canvas.drawText(csaName, MARGIN + 6f, y, headingPaint())
            val csaTotal = csaTxns.sumOf { it.amount }
            canvas.drawText(
                formatAmount(csaTotal),
                PAGE_WIDTH - MARGIN,
                y,
                amountPaint(COLOR_PRIMARY)
            )
            canvas.drawText(
                "${csaTxns.size} transaction${if (csaTxns.size != 1) "s" else ""}",
                MARGIN + 6f, y + 14f, smallPaint()
            )
            y += 26f

            // Individual transactions under this CSA
            csaTxns.sortedBy { it.timestamp }.forEach { txn ->
                checkPageBreak(20f)
                val code = txn.mpesa_code
                val name = (txn.sender_name ?: txn.business_name ?: "Unknown").take(30)
                val time = formatTime(txn.timestamp)
                canvas.drawText("  $code", MARGIN + 8f, y, smallPaint())
                canvas.drawText(name, MARGIN + 90f, y, smallPaint())
                canvas.drawText(time, MARGIN + 300f, y, smallPaint())
                canvas.drawText(formatAmount(txn.amount), PAGE_WIDTH - MARGIN, y, amountPaint())
                y += 14f
            }

            // CSA subtotal line
            canvas.drawLine(MARGIN + 8f, y, PAGE_WIDTH - MARGIN, y, dividerPaint())
            y += 4f
            canvas.drawText("Subtotal — $csaName", MARGIN + 8f, y + 10f, smallPaint())
            canvas.drawText(formatAmount(csaTotal), PAGE_WIDTH - MARGIN, y + 10f, amountPaint())
            y += 22f
        }

        // Till Transfer In (if any)
        if (tillTransferIn.isNotEmpty()) {
            checkPageBreak(30f)
            canvas.drawRect(MARGIN, y - 14f, PAGE_WIDTH - MARGIN, y + 4f, fillPaint(COLOR_SURFACE))
            canvas.drawText("Till Transfer In", MARGIN + 6f, y, headingPaint())
            canvas.drawText(
                formatAmount(tillTransferIn.sumOf { it.amount }),
                PAGE_WIDTH - MARGIN, y, amountPaint(COLOR_PRIMARY)
            )
            y += 22f
        }

        // ── Section: Transfers Out ────────────────────────────────────────────
        val hasOutflows = tillTransferOut.isNotEmpty() || withdrawals.isNotEmpty() || reversals.isNotEmpty()
        if (hasOutflows) {
            checkPageBreak(40f)
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint())
            y += 16f
            canvas.drawText("TRANSFERS OUT (MONEY OUT)", MARGIN, y, headingPaint())
            y += 18f

            listOf(
                Triple("Till Transfer Out", tillTransferOut, COLOR_ERROR),
                Triple("Withdrawals", withdrawals, COLOR_ERROR),
                Triple("Reversals", reversals, COLOR_ERROR)
            ).forEach { (label, txns, color) ->
                if (txns.isNotEmpty()) {
                    checkPageBreak(20f)
                    canvas.drawText(label, MARGIN + 6f, y, bodyPaint())
                    canvas.drawText(
                        "${txns.size} txn${if (txns.size != 1) "s" else ""}  ${formatAmount(txns.sumOf { it.amount })}",
                        PAGE_WIDTH - MARGIN, y, amountPaint(color)
                    )
                    y += 16f
                }
            }
        }

        // Duplicates excluded
        if (duplicates.isNotEmpty()) {
            checkPageBreak(20f)
            canvas.drawText(
                "Duplicates excluded: ${duplicates.size} (${formatAmount(duplicates.sumOf { it.amount })})",
                MARGIN, y, smallPaint()
            )
            y += 16f
        }

        // ── Section: Reconciliation ───────────────────────────────────────────
        checkPageBreak(140f)
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint())
        y += 16f

        val isBalanced = (shift.variance ?: 0.0) == 0.0
        val reconBg = if (isBalanced) Color.rgb(220, 245, 230) else Color.rgb(255, 235, 235)
        canvas.drawRect(MARGIN, y - 4f, PAGE_WIDTH - MARGIN, y + 115f, fillPaint(reconBg))

        canvas.drawText("RECONCILIATION SUMMARY", MARGIN + 8f, y + 12f, headingPaint())
        y += 28f

        fun reconRow(label: String, amount: Double, bold: Boolean = false) {
            val p = if (bold) headingPaint() else bodyPaint()
            canvas.drawText(label, MARGIN + 8f, y, p)
            canvas.drawText(formatAmount(amount), PAGE_WIDTH - MARGIN - 8f, y, amountPaint())
            y += 16f
        }

        reconRow("Opening Balance", shift.open_balance)
        reconRow("Closing Balance", shift.close_balance ?: 0.0)
        reconRow("Transfers Out (all money OUT)", shift.money_sent_out)
        canvas.drawLine(MARGIN + 8f, y, PAGE_WIDTH - MARGIN - 8f, y, dividerPaint())
        y += 10f
        reconRow("Expected Float", shift.expected_receipts, bold = true)
        reconRow("Customer Receipts (all IN)", shift.actual_receipts)
        canvas.drawLine(MARGIN + 8f, y, PAGE_WIDTH - MARGIN - 8f, y, dividerPaint())
        y += 10f

        // Variance — large and coloured
        val varianceColor = if (isBalanced) COLOR_PRIMARY else COLOR_ERROR
        canvas.drawText("VARIANCE", MARGIN + 8f, y, headingPaint())
        canvas.drawText(
            formatAmount(shift.variance ?: 0.0),
            PAGE_WIDTH - MARGIN - 8f,
            y,
            amountPaint(varianceColor).apply { textSize = 14f }
        )
        y += 20f

        canvas.drawText(
            "Formula: (Closing − Opening + Transfers Out) − Customer Receipts",
            MARGIN + 8f, y, smallPaint()
        )

        // ── Footer ────────────────────────────────────────────────────────────
        val footerY = PAGE_HEIGHT - 20f
        canvas.drawLine(MARGIN, footerY - 8f, PAGE_WIDTH - MARGIN, footerY - 8f, dividerPaint())
        canvas.drawText(
            "Generated by LINKS • ${formatDate(System.currentTimeMillis())} ${formatTime(System.currentTimeMillis())}",
            MARGIN, footerY, smallPaint()
        )
        val pagesPaint = Paint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 9f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("Page $pageNum", PAGE_WIDTH - MARGIN, footerY, pagesPaint)

        pdf.finishPage(page)
        return pdf
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    private fun savePdf(context: Context, pdf: PdfDocument, shift: Shift): File {
        val dir = File(context.cacheDir, "shift_reports").apply { mkdirs() }
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(shift.start_time))
        val file = File(dir, "ShiftReport_${shift.shift_id}_$date.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        Log.d(TAG, "✅ PDF saved: ${file.absolutePath}")
        return file
    }

    private fun sharePdf(context: Context, file: File, shift: Shift) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val date = formatDate(shift.start_time)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Shift Report — $date")
            putExtra(Intent.EXTRA_TEXT, "LINKS Shift Report for $date. See attached PDF.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Shift Report"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatAmount(amount: Double) = "Ksh ${String.format("%,.0f", amount)}"
    private fun formatDate(ts: Long) =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))
    private fun formatTime(ts: Long) =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
}