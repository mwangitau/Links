package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.Person
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.Transaction
import kotlinx.coroutines.launch

class ShiftViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LinksDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()
    private val personDao = database.personDao()

    // Current active shift
    val currentShift: LiveData<Shift?> = transactionDao.getOpenShiftLive()

    // Closed shifts history
    val closedShifts: LiveData<List<Shift>> = transactionDao.getClosedShifts()

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

    init {
        // Load transactions when shift changes
        currentShift.observeForever { shift ->
            shift?.let {
                loadShiftTransactions(it.shift_id)
            }
        }
    }

    // ============ SHIFT OPERATIONS ============

    /**
     * Open a new shift
     * FIX #1: Sets open balance from previous shift's closing balance
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

                // Get previous closed shift for opening balance
                val allShifts = transactionDao.getAllShifts().value ?: emptyList()
                val previousShift = allShifts
                    .filter { it.status == "CLOSED" }
                    .maxByOrNull { it.end_time ?: 0 }

                val openBalance = if (previousShift != null && previousShift.close_balance != null) {
                    // Use previous shift's closing balance
                    android.util.Log.d("ShiftViewModel", "Using previous shift closing balance: ${previousShift.close_balance}")
                    previousShift.close_balance!!
                } else {
                    // First shift - use most recent transaction balance
                    val recentTransactions = transactionDao.getAllTransactions()
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
                android.util.Log.e("ShiftViewModel", "Error opening shift", e)
                _errorMessage.value = "Error opening shift: ${e.message}"
            }
        }
    }

    /**
     * Close current shift
     * FIX #4: Prevent closing if unassigned transactions exist
     */
    fun closeShift() {
        viewModelScope.launch {
            try {
                val shift = transactionDao.getOpenShift() ?: return@launch

                // FIX #4: Check for unassigned transactions
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
                val closeBalance = recentTransactions.firstOrNull()?.account_balance ?: shift.open_balance

                // Calculate totals
                val transfers = transactionDao.getTotalTransfersByShift(shift.shift_id) ?: 0.0
                val withdrawals = transactionDao.getTotalWithdrawalsByShift(shift.shift_id) ?: 0.0

                // Expected = (close + withdrawals + transfers) - open
                val expectedTotal = (closeBalance + withdrawals + transfers) - shift.open_balance

                // Actual = sum of all assignments (CSA + Debt Paid)
                val shiftTransactions = transactionDao.getAllTransactions()
                    .filter { it.shift_id == shift.shift_id && it.assigned_to != null }
                val actualTotal = shiftTransactions.sumOf { it.amount }

                val difference = expectedTotal - actualTotal

                // Update shift with all totals
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

            } catch (e: Exception) {
                android.util.Log.e("ShiftViewModel", "Error closing shift", e)
                _errorMessage.value = "Error closing shift: ${e.message}"
            }
        }
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

    // ============ TRANSACTION OPERATIONS ============

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

                // Reload transactions
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
     * FIX #3: Reassign a single transaction to different person
     */
    fun reassignTransaction(transactionId: Long, newPersonName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                transactionDao.assignTransaction(transactionId, newPersonName, newCategory)

                // Reload transactions
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

                // Reload transactions
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

    // ============ PERSON OPERATIONS ============

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

    // ============ SHIFT REPORT OPERATIONS (FIX #2) ============

    /**
     * Get detailed breakdown for a shift
     */
    suspend fun getShiftBreakdown(shiftId: Long): Map<String, Double> {
        val breakdown = mutableMapOf<String, Double>()

        try {
            val persons = personDao.getAllPersons().value ?: emptyList()

            // Get totals for each person
            persons.forEach { person ->
                val total = transactionDao.getTotalByShiftAndPerson(shiftId, person.short_name) ?: 0.0
                if (total > 0) {
                    breakdown[person.short_name] = total
                }
            }

            // Get debt paid
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