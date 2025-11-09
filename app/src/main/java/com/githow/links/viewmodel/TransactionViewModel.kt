package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.Transaction
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LinksDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()

    // LiveData that automatically updates when database changes
    val allTransactions: LiveData<List<Transaction>> = transactionDao.getAllTransactionsLive()

    // Get transactions for today
    fun getTodayTransactions(): LiveData<List<Transaction>> {
        return transactionDao.getTransactionsByDate(getTodayDateString())
    }

    // Get total amount received today
    suspend fun getTodayTotal(): Double {
        return transactionDao.getTotalByDate(getTodayDateString()) ?: 0.0
    }

    // Search transactions
    fun searchTransactions(query: String): LiveData<List<Transaction>> {
        return transactionDao.searchTransactions("%$query%")
    }

    // Get today's date in format "d/M/yy" (e.g., "9/11/25")
    private fun getTodayDateString(): String {
        val calendar = java.util.Calendar.getInstance()
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val year = calendar.get(java.util.Calendar.YEAR) % 100
        return "$day/$month/$year"
    }
}