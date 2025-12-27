package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.ManualReviewQueue
import com.githow.links.service.AuthenticationService
import com.githow.links.service.ManualReviewService
import com.githow.links.ui.components.ManualEntryData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ManualReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LinksDatabase.getDatabase(application)
    private val manualReviewService = ManualReviewService(
        manualReviewDao = database.manualReviewQueueDao(),
        rawSmsDao = database.rawSmsDao(),
        transactionDao = database.transactionDao()
    )
    private val authService = AuthenticationService(
        userDao = database.userDao(),
        context = application
    )

    // ============================================
    // STATE FLOWS
    // ============================================

    val pendingReviews: StateFlow<List<ManualReviewQueue>> =
        manualReviewService.getPendingReviews()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val pendingCount: StateFlow<Int> =
        manualReviewService.getPendingCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

    val currentUser = flow {
        emit(authService.getCurrentUser())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _uiState = MutableStateFlow<ManualReviewUiState>(ManualReviewUiState.Idle)
    val uiState: StateFlow<ManualReviewUiState> = _uiState.asStateFlow()

    private val _statistics = MutableStateFlow<ManualReviewStatsUI?>(null)
    val statistics: StateFlow<ManualReviewStatsUI?> = _statistics.asStateFlow()

    // ============================================
    // PUBLIC METHODS
    // ============================================

    fun submitManualEntry(
        rawSmsId: Long,
        entryData: ManualEntryData,
        supervisorUsername: String
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = ManualReviewUiState.Submitting

                val txnId = manualReviewService.submitManualEntry(
                    rawSmsId = rawSmsId,
                    mpesaCode = entryData.mpesaCode,
                    amount = entryData.amount,
                    senderName = entryData.senderName,
                    senderPhone = entryData.senderPhone,
                    transactionType = entryData.transactionType,
                    isTransfer = entryData.isTransfer,
                    transactionTime = entryData.transactionTime,
                    paybillNumber = entryData.paybillNumber,
                    businessName = entryData.businessName,
                    supervisorUsername = supervisorUsername
                )

                _uiState.value = ManualReviewUiState.Success(
                    message = "Transaction created successfully! ID: $txnId"
                )

                loadStatistics()

            } catch (e: Exception) {
                _uiState.value = ManualReviewUiState.Error(
                    message = "Failed to submit entry: ${e.message}"
                )
            }
        }
    }

    fun skipReview(
        rawSmsId: Long,
        supervisorUsername: String,
        reason: String? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = ManualReviewUiState.Submitting

                manualReviewService.skipReview(
                    rawSmsId = rawSmsId,
                    supervisorUsername = supervisorUsername,
                    reason = reason
                )

                _uiState.value = ManualReviewUiState.Success(
                    message = "SMS skipped"
                )

                loadStatistics()

            } catch (e: Exception) {
                _uiState.value = ManualReviewUiState.Error(
                    message = "Failed to skip: ${e.message}"
                )
            }
        }
    }

    fun loadStatistics() {
        viewModelScope.launch {
            try {
                val stats = manualReviewService.getStatistics()
                _statistics.value = ManualReviewStatsUI(
                    total = stats.total,
                    pending = stats.pending,
                    completed = stats.completed,
                    skipped = stats.skipped
                )
            } catch (e: Exception) {
                // Silently fail for statistics
            }
        }
    }

    fun resetUiState() {
        _uiState.value = ManualReviewUiState.Idle
    }

    suspend fun isCurrentUserSupervisor(): Boolean {
        return authService.isCurrentUserSupervisor()
    }
}

sealed class ManualReviewUiState {
    object Idle : ManualReviewUiState()
    object Submitting : ManualReviewUiState()
    data class Success(val message: String) : ManualReviewUiState()
    data class Error(val message: String) : ManualReviewUiState()
}

// RENAMED from ManualReviewStats to ManualReviewStatsUI to avoid conflict with DAO
data class ManualReviewStatsUI(
    val total: Int,
    val pending: Int,
    val completed: Int,
    val skipped: Int
) {
    val completionRate: Double
        get() = if (total > 0) (completed.toDouble() / total) * 100 else 0.0
}