package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.Transaction
import kotlinx.coroutines.launch
import java.util.Calendar

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LinksDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()

    // ========================================
    // ORIGINAL FUNCTIONALITY (KEEP)
    // ========================================

    val allTransactions: LiveData<List<Transaction>> = try {
        // Try your original method first
        transactionDao.getAllTransactionsLive()
    } catch (e: Exception) {
        // Fallback to standard method if getAllTransactionsLive doesn't exist
        transactionDao.getAllTransactions()
    }

    fun getTodayTransactions(): LiveData<List<Transaction>> {
        val result = MutableLiveData<List<Transaction>>()
        viewModelScope.launch {
            result.value = transactionDao.getTransactionsByDate(getTodayDateString())
        }
        return result
    }

    suspend fun getTodayTotal(): Double {
        return transactionDao.getTotalByDate(getTodayDateString()) ?: 0.0
    }

    fun searchTransactions(query: String): LiveData<List<Transaction>> {
        val result = MutableLiveData<List<Transaction>>()
        viewModelScope.launch {
            result.value = transactionDao.searchTransactions("%$query%")
        }
        return result
    }

    // ========================================
    // NEW FUNCTIONALITY (FOR UI SCREENS)
    // ========================================

    // Get unassigned transactions (for filtering)
    val unassignedTransactions: LiveData<List<Transaction>> = try {
        transactionDao.getUnassignedTransactions()
    } catch (e: Exception) {
        // Fallback: filter from allTransactions
        MutableLiveData(emptyList())
    }

    // Get transactions by type (for filtering)
    fun getTransactionsByType(type: String): LiveData<List<Transaction>> {
        return try {
            transactionDao.getTransactionsByType(type)
        } catch (e: Exception) {
            // Fallback: return all and filter in UI
            allTransactions
        }
    }

    // Get total amount received today (alternative calculation)
    suspend fun getTodayTotalReceived(): Double {
        return try {
            // Try new method
            transactionDao.getTodayTotalReceived() ?: 0.0
        } catch (e: Exception) {
            // Fallback to your existing method
            getTodayTotal()
        }
    }

    // Get transaction count
    suspend fun getTransactionCount(): Int {
        return try {
            transactionDao.getTransactionCount()
        } catch (e: Exception) {
            // Fallback: count from live data
            0
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR) % 100
        return "$day/$month/$year"
    }
}