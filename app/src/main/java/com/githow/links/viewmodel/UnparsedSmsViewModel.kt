package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.RawSms
import com.githow.links.service.ManualReviewService
import com.githow.links.ui.screens.ParseStatisticsUI
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class UnparsedSmsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LinksDatabase.getDatabase(application)
    private val rawSmsDao = database.rawSmsDao()
    private val manualReviewService = ManualReviewService(
        manualReviewDao = database.manualReviewQueueDao(),
        rawSmsDao = rawSmsDao,
        transactionDao = database.transactionDao()
    )

    // ============================================
    // STATE FLOWS
    // ============================================

    val unparsedMessages: StateFlow<List<RawSms>> =
        rawSmsDao.getUnparsedSms()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val unparsedCount: StateFlow<Int> =
        unparsedMessages.map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

    private val _statistics = MutableStateFlow<ParseStatisticsUI?>(null)
    val statistics: StateFlow<ParseStatisticsUI?> = _statistics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ============================================
    // PUBLIC METHODS
    // ============================================

    fun loadUnparsedMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            kotlinx.coroutines.delay(300)
            _isLoading.value = false
        }
    }

    fun loadStatistics() {
        viewModelScope.launch {
            try {
                val stats = rawSmsDao.getParseStatistics()
                _statistics.value = ParseStatisticsUI(
                    total = stats.total,
                    unprocessed = stats.unprocessed,
                    parsed_success = stats.parsed_success,
                    parse_error = stats.parse_error,
                    manual_review = stats.manual_review,
                    manually_entered = stats.manually_entered,
                    parse_success_rate = stats.parse_success_rate,
                    manual_entry_rate = stats.manual_entry_rate
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Ensure a PARSE_ERROR SMS is in the manual_review_queue.
     * Safe to call multiple times — insert uses REPLACE conflict strategy.
     */
    fun ensureInReviewQueue(rawSms: RawSms) {
        viewModelScope.launch {
            try {
                val partial = manualReviewService.extractPartialData(rawSms.message_body)
                manualReviewService.addToReviewQueue(
                    rawSmsId = rawSms.id,
                    rawMessage = rawSms.message_body,
                    timestamp = rawSms.received_timestamp,
                    extractedCode = partial.code,
                    extractedAmount = partial.amount,
                    extractedSender = partial.senderName,
                    extractedPhone = partial.senderPhone
                )
            } catch (e: Exception) {
                // Already queued or DB error — safe to ignore
            }
        }
    }

    fun getMessagesByStatus(status: String): Flow<List<RawSms>> {
        return unparsedMessages.map { messages ->
            messages.filter { it.parse_status.name == status }
        }
    }

    fun refresh() {
        loadUnparsedMessages()
        loadStatistics()
    }
}