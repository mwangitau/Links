package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.RawSms
import com.githow.links.ui.screens.ParseStatisticsUI
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class UnparsedSmsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LinksDatabase.getDatabase(application)
    private val rawSmsDao = database.rawSmsDao()

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
                    parsed_success = stats.parsed_success,           // ✅ FIXED: Use underscores
                    parse_error = stats.parse_error,                 // ✅ FIXED: Use underscores
                    manual_review = stats.manual_review,             // ✅ FIXED: Use underscores
                    manually_entered = stats.manually_entered,       // ✅ FIXED: Use underscores
                    parse_success_rate = stats.parse_success_rate,   // ✅ FIXED: Use underscores
                    manual_entry_rate = stats.manual_entry_rate      // ✅ FIXED: Use underscores
                )
            } catch (e: Exception) {
                e.printStackTrace()
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