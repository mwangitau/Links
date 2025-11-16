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

    val allTransactions: LiveData<List<Transaction>> = transactionDao.getAllTransactionsLive()

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

    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR) % 100
        return "$day/$month/$year"
    }
}