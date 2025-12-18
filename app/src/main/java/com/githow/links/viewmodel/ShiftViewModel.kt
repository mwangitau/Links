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
    private val shiftDao = database.shiftDao()  // ADDED: New ShiftDao

    // Cloud sync manager
    private val cloudSyncManager = CloudSyncManager(application)

    // Current active shift
    val currentShift: LiveData<Shift?> = transactionDao.getOpenShiftLive()

    // ADDED: Alternative current active shift (for new screens)
    val currentActiveShift: LiveData<Shift?> = shiftDao.getActiveShift()

    // ADDED: Current shift transactions (for new screens)
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
     * This version is simpler and works with the new UI
     */
    fun openNewShift(
        shift: Shift,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Check if there's already an active shift
                    val activeShift = shiftDao.getActiveShiftDirect()
                    if (activeShift != null) {
                        withContext(Dispatchers.Main) {
                            onError("There is already an active shift. Please close it first.")
                        }
                        return@withContext
                    }

                    // Insert the new shift
                    val shiftId = shiftDao.insertShift(shift)

                    // Assign all unassigned transactions to this shift
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
     * Close the current shift with manual closing balance (FOR NEW CLOSE SHIFT SCREEN)
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

                // 🔒 OPTION 2: Use Account Balance Timestamp (Most Accurate)
                // Find the transaction that shows this exact closing balance
                val closingBalanceTransaction = shiftTransactions
                    .filter { transaction ->
                        // Use small tolerance for floating point comparison
                        abs(transaction.account_balance - closingBalance) < 0.01
                    }
                    .maxByOrNull { transaction -> transaction.timestamp }  // Get the latest one if multiple matches

                val cutoffTime = if (closingBalanceTransaction != null) {
                    // Found exact balance match - use its timestamp + 1 second
                    val cutoff = closingBalanceTransaction.timestamp + 1000
                    Log.d("SHIFT_CLOSE", "✅ Found balance match: ${closingBalanceTransaction.mpesa_code}")
                    Log.d("SHIFT_CLOSE", "   Balance: Ksh ${closingBalanceTransaction.account_balance}")
                    Log.d("SHIFT_CLOSE", "   Timestamp: ${closingBalanceTransaction.timestamp}")
                    Log.d("SHIFT_CLOSE", "   Cutoff set to: $cutoff (+1 second)")
                    cutoff
                } else {
                    // No exact match - use last transaction + 1 second as fallback
                    val lastTxn = shiftTransactions.maxByOrNull { transaction -> transaction.timestamp }
                    val cutoff = lastTxn?.timestamp?.plus(1000) ?: System.currentTimeMillis()
                    Log.w("SHIFT_CLOSE", "⚠️ No exact balance match for Ksh $closingBalance")
                    Log.w("SHIFT_CLOSE", "   Closest balances:")
                    shiftTransactions.sortedByDescending { transaction -> transaction.timestamp }.take(3).forEach { transaction ->
                        Log.w("SHIFT_CLOSE", "     - Ksh ${transaction.account_balance} at ${transaction.timestamp}")
                    }
                    Log.w("SHIFT_CLOSE", "   Using last transaction + 1 sec as fallback: $cutoff")
                    cutoff
                }

                Log.d("SHIFT_CLOSE", "🔒 Final cutoff timestamp: $cutoffTime")

                // Calculate totals
                val totalReceived = shiftTransactions
                    .filter { transaction -> transaction.transaction_type == "RECEIVED" }
                    .sumOf { transaction -> transaction.amount }

                val totalTransfers = shiftTransactions
                    .filter { transaction -> transaction.transaction_type == "SENT" }
                    .sumOf { transaction -> transaction.amount }

                val totalWithdrawals = shiftTransactions
                    .filter { transaction -> transaction.transaction_type == "WITHDRAW" }
                    .sumOf { transaction -> transaction.amount }

                // Expected total: (closing - opening) + withdrawals
                val expectedTotal = (closingBalance - shift.open_balance) + totalWithdrawals

                // Actual total: sum of all assignments
                val actualTotal = shiftTransactions
                    .filter { transaction -> !transaction.assigned_to.isNullOrBlank() }
                    .sumOf { transaction -> transaction.amount }

                val difference = expectedTotal - actualTotal

                // Update shift with all calculated values
                val updatedShift = shift.copy(
                    status = "CLOSED",
                    close_balance = closingBalance,
                    end_time = System.currentTimeMillis(),
                    cutoff_timestamp = cutoffTime,  // ← Set the smart cutoff
                    total_received = totalReceived,
                    total_transfers = totalTransfers,
                    total_withdrawals = totalWithdrawals,
                    expected_total = expectedTotal,
                    actual_total = actualTotal,
                    difference = difference,
                    updated_at = System.currentTimeMillis()
                )

                shiftDao.updateShift(updatedShift)

                Log.d("SHIFT_CLOSE", "✅ Shift #$shiftId closed successfully")
                Log.d("SHIFT_CLOSE", "   Opening Balance: Ksh ${shift.open_balance}")
                Log.d("SHIFT_CLOSE", "   Closing Balance: Ksh $closingBalance")
                Log.d("SHIFT_CLOSE", "   Expected Total: Ksh $expectedTotal")
                Log.d("SHIFT_CLOSE", "   Actual Total: Ksh $actualTotal")
                Log.d("SHIFT_CLOSE", "   Difference: Ksh $difference")

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
                            onError("Can only edit closing balance for closed shifts")
                        }
                        return@withContext
                    }

                    // Recalculate with new closing balance
                    val expectedTotal = (newClosingBalance + shift.total_withdrawals) - shift.open_balance
                    val difference = expectedTotal - shift.actual_total

                    val updatedShift = shift.copy(
                        close_balance = newClosingBalance,
                        expected_total = expectedTotal,
                        difference = difference,
                        updated_at = System.currentTimeMillis()
                    )

                    shiftDao.updateShift(updatedShift)

                    // Get all transactions for this shift to sync
                    val transactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)

                    // Cloud sync
                    withContext(Dispatchers.Main) {
                        _syncStatus.value = "Syncing updated shift to cloud..."
                    }

                    val syncResult = cloudSyncManager.syncShiftToCloud(
                        shift = updatedShift,
                        transactions = transactions
                    )

                    withContext(Dispatchers.Main) {
                        when (syncResult) {
                            is SyncResult.Success -> {
                                android.util.Log.d("ShiftViewModel", "☁️ Update Sync: ${syncResult.message}")
                                _syncStatus.value = "✅ Shift update synced"
                            }
                            is SyncResult.Failure -> {
                                android.util.Log.e("ShiftViewModel", "☁️ Update Sync failed: ${syncResult.error}")
                                _syncStatus.value = "⚠️ Update sync failed: ${syncResult.error}"
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Failed to update closing balance: ${e.message}")
                }
            }
        }
    }

    // ============ EXISTING METHODS BELOW (KEEP ALL OF THESE) ============

    /**
     * Get a specific shift by ID as LiveData
     * Used by: ShiftReportScreen
     */
    fun getShiftByIdLive(shiftId: Long): LiveData<Shift?> {
        val result = MutableLiveData<Shift?>()
        viewModelScope.launch {
            try {
                result.value = transactionDao.getShiftById(shiftId)
            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error getting shift by ID", e)
                result.value = null
            }
        }
        return result
    }

    /**
     * Get all transactions for a specific shift as LiveData
     * Used by: ShiftReportScreen
     */
    fun getShiftTransactions(shiftId: Long): LiveData<List<Transaction>> {
        val result = MutableLiveData<List<Transaction>>()
        viewModelScope.launch {
            try {
                // Get all transactions for this shift
                val allTransactions = transactionDao.getAllTransactions()
                val shiftTransactions = allTransactions.filter {
                    it.shift_id == shiftId && !it.is_hidden
                }
                result.value = shiftTransactions
            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error getting shift transactions", e)
                result.value = emptyList()
            }
        }
        return result
    }

    /**
     * Open a shift (ORIGINAL METHOD - KEEP FOR COMPATIBILITY)
     */
    fun openShift() {
        viewModelScope.launch {
            try {
                // Check if there's already an active shift
                val existingShift = transactionDao.getOpenShift()
                if (existingShift != null) {
                    android.util.Log.w("ShiftViewModel", "Shift already open")
                    _errorMessage.value = "A shift is already open. Close it first."
                    return@launch
                }

                // Get ALL shifts and find the most recent CLOSED shift
                val allShifts = transactionDao.getAllTransactions()
                    .mapNotNull { it.shift_id }
                    .distinct()
                    .mapNotNull { transactionDao.getShiftById(it) }
                    .filter { it.status == "CLOSED" }
                    .sortedByDescending { it.end_time ?: 0 }

                val previousShift = allShifts.firstOrNull()

                val openBalance = if (previousShift != null && previousShift.close_balance != null) {
                    android.util.Log.d("ShiftViewModel", "✅ Using previous shift closing balance: ${previousShift.close_balance}")
                    previousShift.close_balance!!
                } else {
                    val recentTransactions = transactionDao.getAllTransactions()
                        .sortedByDescending { it.timestamp }
                    val balance = recentTransactions.firstOrNull()?.account_balance ?: 0.0
                    android.util.Log.d("ShiftViewModel", "First shift - using recent transaction balance: $balance")
                    balance
                }

                // Create new shift
                val shift = Shift(
                    start_time = System.currentTimeMillis(),
                    open_balance = openBalance,
                    status = "ACTIVE"
                )

                val shiftId = transactionDao.insertShift(shift)
                android.util.Log.d("ShiftViewModel", "✅ Shift opened with ID: $shiftId, Opening Balance: $openBalance")

                // Assign all unassigned transactions to this shift
                assignUnassignedTransactionsToShift(shiftId)

            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Er" +
                        "ror opening shift", e)
                _errorMessage.value = "Error opening shift: ${e.message}"
            }
        }
    }

    /**
     * Close current shift (ORIGINAL METHOD - MODIFIED TO USE NEW DAO)
     */
    fun closeShift() {
        viewModelScope.launch {
            try {
                val shift = transactionDao.getOpenShift() ?: return@launch

                // Check for unassigned transactions
                val unassigned = transactionDao.getAllTransactions()
                    .filter { it.shift_id == shift.shift_id &&
                            it.assigned_to == null &&
                            !it.is_hidden &&
                            it.transaction_type == "RECEIVED" }

                if (unassigned.isNotEmpty()) {
                    android.util.Log.w("ShiftViewModel", "Cannot close shift: ${unassigned.size} unassigned transactions")
                    _errorMessage.value = "Cannot close shift! You have ${unassigned.size} unassigned transaction(s). Please assign all transactions first."
                    return@launch
                }

                // Get most recent transaction for closing balance
                val recentTransactions = transactionDao.getAllTransactions()
                    .sortedByDescending { it.timestamp }
                val closeBalance = recentTransactions.firstOrNull()?.account_balance ?: shift.open_balance

                // Calculate totals
                val transfers = transactionDao.getTotalTransfersByShift(shift.shift_id) ?: 0.0
                val withdrawals = transactionDao.getTotalWithdrawalsByShift(shift.shift_id) ?: 0.0

                val expectedTotal = (closeBalance + withdrawals + transfers) - shift.open_balance

                val shiftTransactions = transactionDao.getAllTransactions()
                    .filter { it.shift_id == shift.shift_id && it.assigned_to != null }
                val actualTotal = shiftTransactions.sumOf { it.amount }

                val difference = expectedTotal - actualTotal

                // Update shift
                val updatedShift = shift.copy(
                    end_time = System.currentTimeMillis(),
                    close_balance = closeBalance,
                    status = "CLOSED",
                    total_transfers = transfers,
                    total_withdrawals = withdrawals,
                    expected_total = expectedTotal,
                    actual_total = actualTotal,
                    difference = difference,
                    updated_at = System.currentTimeMillis()
                )

                transactionDao.updateShift(updatedShift)
                android.util.Log.d("ShiftViewModel", "✅ Shift closed. Expected: $expectedTotal, Actual: $actualTotal, Diff: $difference")

                // Cloud sync
                _syncStatus.value = "Syncing to cloud..."

                val syncResult = cloudSyncManager.syncShiftToCloud(
                    shift = updatedShift,
                    transactions = shiftTransactions
                )

                when (syncResult) {
                    is SyncResult.Success -> {
                        android.util.Log.d("ShiftViewModel", "☁️ ${syncResult.message}")
                        _syncStatus.value = "✅ Synced to Google Sheets"
                    }
                    is SyncResult.Failure -> {
                        android.util.Log.e("ShiftViewModel", "☁️ Sync failed: ${syncResult.error}")
                        _syncStatus.value = "⚠️ Sync failed: ${syncResult.error}"
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error closing shift", e)
                _errorMessage.value = "Error closing shift: ${e.message}"
            }
        }
    }

    // Clear sync status
    fun clearSyncStatus() {
        _syncStatus.value = null
    }

    // Clear error message
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Assign all current unassigned transactions to a shift
     */
    private suspend fun assignUnassignedTransactionsToShift(shiftId: Long) {
        try {
            val unassigned = transactionDao.getAllTransactions()
                .filter { it.shift_id == null && !it.is_hidden }

            unassigned.forEach { transaction ->
                val updated = transaction.copy(shift_id = shiftId)
                transactionDao.updateTransaction(updated)
            }

            android.util.Log.d("ShiftViewModel", "✅ Assigned ${unassigned.size} transactions to shift")
        } catch (e: Exception) {
            android.util.Log.e("ShiftViewModel", "Error assigning transactions to shift", e)
        }
    }

    /**
     * Load transactions for a shift
     */
    private fun loadShiftTransactions(shiftId: Long) {
        viewModelScope.launch {
            try {
                val all = transactionDao.getAllTransactions()
                    .filter { it.shift_id == shiftId && !it.is_hidden && it.transaction_type == "RECEIVED" }

                _unassignedTransactions.value = all.filter { it.assigned_to == null }
                _assignedTransactions.value = all.filter { it.assigned_to != null }

            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error loading shift transactions", e)
            }
        }
    }

    /**
     * Assign multiple transactions to a person
     */
    fun assignTransactions(transactionIds: List<Long>, personName: String, category: String) {
        viewModelScope.launch {
            try {
                transactionIds.forEach { id ->
                    transactionDao.assignTransaction(id, personName, category)
                }

                currentShift.value?.let { shift ->
                    loadShiftTransactions(shift.shift_id)
                }

                android.util.Log.d("ShiftViewModel", "✅ Assigned ${transactionIds.size} transactions to $personName")
            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error assigning transactions", e)
                _errorMessage.value = "Error assigning transactions: ${e.message}"
            }
        }
    }

    /**
     * Reassign a single transaction to different person
     */
    fun reassignTransaction(transactionId: Long, newPersonName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                transactionDao.assignTransaction(transactionId, newPersonName, newCategory)

                currentShift.value?.let { shift ->
                    loadShiftTransactions(shift.shift_id)
                }

                android.util.Log.d("ShiftViewModel", "✅ Reassigned transaction $transactionId to $newPersonName")
            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error reassigning transaction", e)
                _errorMessage.value = "Error reassigning: ${e.message}"
            }
        }
    }

    /**
     * Unassign a transaction
     */
    fun unassignTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                transactionDao.unassignTransaction(transactionId)

                currentShift.value?.let { shift ->
                    loadShiftTransactions(shift.shift_id)
                }

                android.util.Log.d("ShiftViewModel", "✅ Unassigned transaction $transactionId")
            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error unassigning transaction", e)
                _errorMessage.value = "Error unassigning: ${e.message}"
            }
        }
    }

    /**
     * Add a new person
     */
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
                android.util.Log.d("ShiftViewModel", "✅ Added person: $displayName")

            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error adding person", e)
                _errorMessage.value = "Error adding person: ${e.message}"
            }
        }
    }

    /**
     * Update a person
     */
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
                android.util.Log.d("ShiftViewModel", "✅ Updated person: $displayName")

            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error updating person", e)
                _errorMessage.value = "Error updating person: ${e.message}"
            }
        }
    }

    /**
     * Toggle person active/inactive status
     */
    fun togglePersonActive(personId: Long) {
        viewModelScope.launch {
            try {
                val person = personDao.getPersonById(personId) ?: return@launch

                if (person.is_active) {
                    personDao.deactivatePerson(personId)
                    android.util.Log.d("ShiftViewModel", "Deactivated person: ${person.short_name}")
                } else {
                    personDao.reactivatePerson(personId)
                    android.util.Log.d("ShiftViewModel", "Reactivated person: ${person.short_name}")
                }

            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error toggling person", e)
            }
        }
    }

    /**
     * Get detailed breakdown for a shift
     */
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

            val debtPaid = transactionDao.getTotalByShiftAndCategory(shiftId, "DEBT_PAID") ?: 0.0
            if (debtPaid > 0) {
                breakdown["Debt Paid"] = debtPaid
            }

        } catch (e: Exception) {
            android.util.Log.e("ShiftViewModel", "Error getting shift breakdown", e)
        }

        return breakdown
    }
}
