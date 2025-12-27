package com.githow.links.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.Person
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.Transaction
import com.githow.links.sync.CloudSyncManager
import com.githow.links.sync.SyncResult
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShiftViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LinksDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()
    private val personDao = database.personDao()
    private val shiftDao = database.shiftDao()

    // Cloud sync manager
    private val cloudSyncManager = CloudSyncManager(application)

    // Current active shift
    val currentShift: LiveData<Shift?> = transactionDao.getOpenShiftLive()

    // Alternative current active shift (for new screens)
    val currentActiveShift: LiveData<Shift?> = shiftDao.getActiveShift()

    // Current shift transactions (for new screens)
    val currentShiftTransactions: LiveData<List<Transaction>> = transactionDao.getCurrentShiftTransactions()

    // Closed shifts history
    val closedShifts: LiveData<List<Shift>> = transactionDao.getClosedShifts()

    // All shifts (for history screen)
    val allShifts: LiveData<List<Shift>> = shiftDao.getAllShifts()

    // People (CSA list)
    val persons: LiveData<List<Person>> = personDao.getAllActivePersons()

    // Transactions for current shift
    private val _unassignedTransactions = MutableLiveData<List<Transaction>>()
    val unassignedTransactions: LiveData<List<Transaction>> = _unassignedTransactions

    private val _assignedTransactions = MutableLiveData<List<Transaction>>()
    val assignedTransactions: LiveData<List<Transaction>> = _assignedTransactions

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Cloud sync status
    private val _syncStatus = MutableLiveData<String?>()
    val syncStatus: LiveData<String?> = _syncStatus

    private val TAG = "ShiftViewModel"

    init {
        // Load transactions when shift changes
        currentShift.observeForever { shift ->
            shift?.let {
                loadShiftTransactions(it.shift_id)
            }
        }
    }

    // ============ NEW METHODS FOR OPEN/CLOSE SHIFT SCREENS ============

    /**
     * Get the last closed shift (for opening new shift with its closing balance)
     */
    fun getLastClosedShift(): LiveData<Shift?> {
        return shiftDao.getLastClosedShift()
    }

    /**
     * Open a new shift (FOR NEW OPEN SHIFT SCREEN)
     */
    fun openNewShift(
        shift: Shift,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val activeShift = shiftDao.getActiveShiftDirect()
                    if (activeShift != null) {
                        withContext(Dispatchers.Main) {
                            onError("There is already an active shift. Please close it first.")
                        }
                        return@withContext
                    }

                    val shiftId = shiftDao.insertShift(shift)
                    assignUnassignedTransactionsToShift(shiftId)
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Failed to open shift: ${e.message}")
                }
            }
        }
    }

    /**
     * Freeze the current active shift (Step 1 of Two-Step Close Process)
     */
    fun freezeShift(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val activeShift = shiftDao.getActiveShiftDirect()

                    if (activeShift == null) {
                        withContext(Dispatchers.Main) {
                            onError("No active shift to freeze")
                        }
                        return@withContext
                    }

                    if (activeShift.status == "FROZEN") {
                        withContext(Dispatchers.Main) {
                            onError("Shift is already frozen")
                        }
                        return@withContext
                    }

                    val cutoffTime = System.currentTimeMillis()

                    val frozenShift = activeShift.copy(
                        status = "FROZEN",
                        cutoff_timestamp = cutoffTime,
                        updated_at = System.currentTimeMillis()
                    )

                    shiftDao.updateShift(frozenShift)

                    Log.d("SHIFT_FREEZE", "✅ Shift #${activeShift.shift_id} FROZEN successfully")
                    Log.d("SHIFT_FREEZE", "   Cutoff Timestamp: $cutoffTime")
                    Log.d("SHIFT_FREEZE", "   Any SMS after this will NOT be assigned to this shift")
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {
                Log.e("SHIFT_FREEZE", "❌ Error freezing shift: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError("Failed to freeze shift: ${e.message}")
                }
            }
        }
    }

    /**
     * Close shift with NEW RECONCILIATION FORMULA
     * Formula: Expected Receipts = (Closing Balance - Opening Balance) + Money Sent Out
     */
    fun closeShift(
        shiftId: Long,
        closingBalance: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val shift = shiftDao.getShiftByIdDirect(shiftId)
                if (shift == null) {
                    onError("Shift not found")
                    return@launch
                }

                // Get all transactions in this shift
                val shiftTransactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)

                // Find the transaction that shows this exact closing balance
                val closingBalanceTransaction = shiftTransactions
                    .filter { transaction ->
                        abs(transaction.account_balance - closingBalance) < 0.01
                    }
                    .maxByOrNull { transaction -> transaction.timestamp }

                val cutoffTime = if (closingBalanceTransaction != null) {
                    val cutoff = closingBalanceTransaction.timestamp + 1000
                    Log.d("SHIFT_CLOSE", "✅ Found balance match: ${closingBalanceTransaction.mpesa_code}")
                    Log.d("SHIFT_CLOSE", "    Balance: Ksh ${closingBalanceTransaction.account_balance}")
                    Log.d("SHIFT_CLOSE", "    Timestamp: ${closingBalanceTransaction.timestamp}")
                    Log.d("SHIFT_CLOSE", "    Cutoff set to: $cutoff (+1 second)")
                    cutoff
                } else {
                    val lastTxn = shiftTransactions.maxByOrNull { transaction -> transaction.timestamp }
                    val cutoff = lastTxn?.timestamp?.plus(1000) ?: System.currentTimeMillis()
                    Log.w("SHIFT_CLOSE", "⚠️ No exact balance match for Ksh $closingBalance")
                    cutoff
                }

                Log.d("SHIFT_CLOSE", "🔒 Final cutoff timestamp: $cutoffTime")

                // ============================================
                // NEW RECONCILIATION CALCULATION
                // ============================================

                // 1. Net change in account
                val netChange = closingBalance - shift.open_balance

                // 2. Money sent out (only SENT transactions - transfers)
                val moneySentOut = shiftTransactions
                    .filter { it.transaction_type == "SENT" }
                    .sumOf { abs(it.amount) }  // Use absolute value

                // 3. Expected customer receipts
                val expectedReceipts = netChange + moneySentOut

                // 4. Actual recorded receipts (only RECEIVED transactions)
                val actualReceipts = shiftTransactions
                    .filter { it.transaction_type == "RECEIVED" }
                    .sumOf { it.amount }

                // 5. Calculate variance
                val variance = expectedReceipts - actualReceipts

                // ============================================
                // DETAILED LOGGING
                // ============================================

                Log.d("SHIFT_CLOSE", "✅ Shift #$shiftId closed successfully")
                Log.d("SHIFT_CLOSE", "    Opening Balance: Ksh ${shift.open_balance}")
                Log.d("SHIFT_CLOSE", "    Closing Balance: Ksh $closingBalance")
                Log.d("SHIFT_CLOSE", "    Net Change: Ksh $netChange")
                Log.d("SHIFT_CLOSE", "    Money Sent Out: Ksh $moneySentOut")
                Log.d("SHIFT_CLOSE", "    Expected Receipts: Ksh $expectedReceipts")
                Log.d("SHIFT_CLOSE", "    Actual Receipts: Ksh $actualReceipts")
                Log.d("SHIFT_CLOSE", "    Variance: Ksh $variance")

                // Transaction breakdown
                Log.d("SHIFT_CLOSE", "📊 Transaction Breakdown:")
                val breakdown = shiftTransactions.groupBy { it.transaction_type }
                breakdown.forEach { (type, txns) ->
                    Log.d("SHIFT_CLOSE", "    $type: ${txns.size} transactions, Ksh ${txns.sumOf { it.amount }}")
                }

                // ============================================
                // UPDATE DATABASE WITH NEW METHOD
                // ============================================

                shiftDao.closeShiftWithReconciliation(
                    shiftId = shiftId,
                    endTime = System.currentTimeMillis(),
                    closeBalance = closingBalance,
                    cutoffTimestamp = cutoffTime,
                    netChange = netChange,
                    moneySentOut = moneySentOut,
                    expectedReceipts = expectedReceipts,
                    actualReceipts = actualReceipts,
                    variance = variance,
                    updatedAt = System.currentTimeMillis()
                )

                // ============================================
                // CLOUD SYNC
                // ============================================

                Log.d(TAG, "🔄 Starting cloud sync for shift #$shiftId...")
                _syncStatus.value = "Syncing to cloud..."

                // Get updated shift for sync
                val updatedShift = shiftDao.getShiftByIdDirect(shiftId)
                if (updatedShift != null) {
                    val syncResult = cloudSyncManager.syncShiftToCloud(
                        shift = updatedShift,
                        transactions = shiftTransactions
                    )

                    when (syncResult) {
                        is SyncResult.Success -> {
                            Log.d(TAG, "☁️ ${syncResult.message}")
                            _syncStatus.value = "✅ Synced to Google Sheets"
                        }
                        is SyncResult.Failure -> {
                            Log.e(TAG, "☁️ Sync failed: ${syncResult.error}")
                            _syncStatus.value = "⚠️ Sync failed: ${syncResult.error}"
                        }
                    }
                }

                onSuccess()

            } catch (e: Exception) {
                Log.e("SHIFT_CLOSE", "❌ Error closing shift: ${e.message}", e)
                e.printStackTrace()
                onError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Update closing balance for an already closed shift
     */
    fun updateClosingBalance(
        shiftId: Long,
        newClosingBalance: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val shift = shiftDao.getShiftByIdDirect(shiftId)
                    if (shift == null) {
                        withContext(Dispatchers.Main) {
                            onError("Shift not found")
                        }
                        return@withContext
                    }

                    if (shift.status != "CLOSED") {
                        withContext(Dispatchers.Main) {
                            onError("Can only update closing balance for closed shifts")
                        }
                        return@withContext
                    }

                    // Get all transactions in this shift
                    val shiftTransactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)

                    // Recalculate reconciliation with new closing balance
                    val netChange = newClosingBalance - shift.open_balance
                    val moneySentOut = shiftTransactions
                        .filter { it.transaction_type == "SENT" }
                        .sumOf { abs(it.amount) }
                    val expectedReceipts = netChange + moneySentOut
                    val actualReceipts = shiftTransactions
                        .filter { it.transaction_type == "RECEIVED" }
                        .sumOf { it.amount }
                    val variance = expectedReceipts - actualReceipts

                    Log.d(TAG, "📝 Updating closing balance for Shift #$shiftId")
                    Log.d(TAG, "    Old Closing Balance: Ksh ${shift.close_balance}")
                    Log.d(TAG, "    New Closing Balance: Ksh $newClosingBalance")
                    Log.d(TAG, "    New Net Change: Ksh $netChange")
                    Log.d(TAG, "    New Expected Receipts: Ksh $expectedReceipts")
                    Log.d(TAG, "    New Variance: Ksh $variance")

                    // Update the shift with new values
                    shiftDao.updateClosingBalanceAndReconciliation(
                        shiftId = shiftId,
                        closeBalance = newClosingBalance,
                        netChange = netChange,
                        expectedReceipts = expectedReceipts,
                        variance = variance,
                        updatedAt = System.currentTimeMillis()
                    )

                    Log.d(TAG, "✅ Closing balance updated successfully")
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating closing balance: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Failed to update closing balance")
                }
            }
        }
    }

    // ============ HELPER METHODS ============

    private suspend fun assignUnassignedTransactionsToShift(shiftId: Long) {
        transactionDao.getAllTransactions()
            .filter { it.shift_id == null }
            .forEach { transaction ->
                val updated = transaction.copy(shift_id = shiftId)
                transactionDao.updateTransaction(updated)
            }
    }

    private fun loadShiftTransactions(shiftId: Long) {
        viewModelScope.launch {
            try {
                val transactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)

                val unassigned = transactions.filter { it.assigned_to.isNullOrBlank() }
                val assigned = transactions.filter { !it.assigned_to.isNullOrBlank() }

                _unassignedTransactions.value = unassigned
                _assignedTransactions.value = assigned

            } catch (e: Exception) {
                Log.e(TAG, "Error loading transactions", e)
                _errorMessage.value = "Error loading transactions: ${e.message}"
            }
        }
    }

    // ============ ASSIGNMENT METHODS ============

    fun assignTransactions(transactionIds: List<Long>, personName: String, category: String) {
        viewModelScope.launch {
            try {
                transactionIds.forEach { id ->
                    transactionDao.assignTransaction(id, personName, category)
                }

                Log.d(TAG, "✅ Assigned ${transactionIds.size} transactions to $personName")

                // Reload UI transactions
                currentShift.value?.let { shift ->
                    loadShiftTransactions(shift.shift_id)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error assigning transactions", e)
                _errorMessage.value = "Error assigning transactions: ${e.message}"
            }
        }
    }

    fun reassignTransaction(transactionId: Long, newPersonName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                transactionDao.assignTransaction(transactionId, newPersonName, newCategory)

                currentShift.value?.let { shift ->
                    loadShiftTransactions(shift.shift_id)
                }

                Log.d(TAG, "✅ Reassigned transaction $transactionId to $newPersonName")
            } catch (e: Exception) {
                Log.e(TAG, "Error reassigning transaction", e)
                _errorMessage.value = "Error reassigning: ${e.message}"
            }
        }
    }

    fun unassignTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                transactionDao.unassignTransaction(transactionId)

                currentShift.value?.let { shift ->
                    loadShiftTransactions(shift.shift_id)
                }

                Log.d(TAG, "✅ Unassigned transaction $transactionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error unassigning transaction", e)
                _errorMessage.value = "Error unassigning: ${e.message}"
            }
        }
    }

    // ============ PERSON MANAGEMENT ============

    fun addPerson(shortName: String, fullName: String) {
        viewModelScope.launch {
            try {
                val displayName = if (fullName.isNotEmpty())
                    "$shortName ($fullName)"
                else
                    shortName

                val person = Person(
                    name = fullName,
                    short_name = displayName,
                    is_active = true,
                    display_order = (personDao.getActivePersonCount()) + 1
                )

                personDao.insertPerson(person)
                Log.d(TAG, "✅ Added person: $displayName")

            } catch (e: Exception) {
                Log.e(TAG, "Error adding person", e)
                _errorMessage.value = "Error adding person: ${e.message}"
            }
        }
    }

    fun updatePerson(personId: Long, shortName: String, fullName: String) {
        viewModelScope.launch {
            try {
                val person = personDao.getPersonById(personId) ?: return@launch

                val displayName = if (fullName.isNotEmpty())
                    "$shortName ($fullName)"
                else
                    shortName

                val updated = person.copy(
                    name = fullName,
                    short_name = displayName,
                    updated_at = System.currentTimeMillis()
                )

                personDao.updatePerson(updated)
                Log.d(TAG, "✅ Updated person: $displayName")

            } catch (e: Exception) {
                Log.e(TAG, "Error updating person", e)
                _errorMessage.value = "Error updating person: ${e.message}"
            }
        }
    }

    fun togglePersonActive(personId: Long) {
        viewModelScope.launch {
            try {
                val person = personDao.getPersonById(personId) ?: return@launch

                if (person.is_active) {
                    personDao.deactivatePerson(personId)
                    Log.d(TAG, "Deactivated person: ${person.short_name}")
                } else {
                    personDao.reactivatePerson(personId)
                    Log.d(TAG, "Reactivated person: ${person.short_name}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error toggling person", e)
            }
        }
    }

    // ============ REPORTING ============

    suspend fun getShiftBreakdown(shiftId: Long): Map<String, Double> {
        val breakdown = mutableMapOf<String, Double>()

        try {
            val persons = personDao.getAllPersons().value ?: emptyList()

            persons.forEach { person ->
                val total = transactionDao.getTotalByShiftAndPerson(shiftId, person.short_name) ?: 0.0
                if (total > 0) {
                    breakdown[person.short_name] = total
                }
            }

            val internalTransfers = transactionDao.getTotalByShiftAndCategory(shiftId, "NEUTRAL") ?: 0.0
            if (internalTransfers != 0.0) {
                breakdown["Neutral Transactions"] = internalTransfers
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting shift breakdown", e)
        }

        return breakdown
    }
}