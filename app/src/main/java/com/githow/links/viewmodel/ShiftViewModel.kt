package com.githow.links.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.data.entity.Person
import com.githow.links.data.entity.Shift
import com.githow.links.data.entity.ShiftAssignment
import com.githow.links.data.entity.TransactionRole
import com.githow.links.data.entity.TransactionDirection
import com.githow.links.data.entity.Transaction
import com.githow.links.data.entity.includedInReconciliation
import com.githow.links.data.entity.toDirection
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
    private val rawSmsDao = database.rawSmsDao()

    val unparsedSmsCount: LiveData<Int> = rawSmsDao.getUnparsedCount().asLiveData()

    private val cloudSyncManager = CloudSyncManager(application)

    val currentShift: LiveData<Shift?> = transactionDao.getOpenShiftLive()
    val currentActiveShift: LiveData<Shift?> = shiftDao.getActiveShift()
    val currentShiftTransactions: LiveData<List<Transaction>> = transactionDao.getCurrentShiftTransactions()
    val closedShifts: LiveData<List<Shift>> = transactionDao.getClosedShifts()
    val allShifts: LiveData<List<Shift>> = shiftDao.getAllShifts()
    val persons: LiveData<List<Person>> = personDao.getAllActivePersons()

    private val _unassignedTransactions = MutableLiveData<List<Transaction>>()
    val unassignedTransactions: LiveData<List<Transaction>> = _unassignedTransactions

    private val _assignedTransactions = MutableLiveData<List<Transaction>>()
    val assignedTransactions: LiveData<List<Transaction>> = _assignedTransactions

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _syncStatus = MutableLiveData<String?>()
    val syncStatus: LiveData<String?> = _syncStatus

    // ── Double-assignment guard ───────────────────────────────────────────────
    // Prevents a second tap from triggering a duplicate assignment while the
    // first one is still being written to Room. The UI observes this and
    // disables the Assign button + FAB while true.
    private val _isAssigning = MutableLiveData<Boolean>(false)
    val isAssigning: LiveData<Boolean> = _isAssigning

    private val TAG = "ShiftViewModel"

    init {
        currentShift.observeForever { shift ->
            shift?.let { loadShiftTransactions(it.shift_id) }
        }
    }

    // ============ OPEN / FREEZE / CLOSE ============

    fun getLastClosedShift(): LiveData<Shift?> = shiftDao.getLastClosedShift()

    fun openNewShift(shift: Shift, onSuccess: () -> Unit, onError: (String) -> Unit) {
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
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError("Failed to open shift: ${e.message}") }
            }
        }
    }

    fun freezeShift(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val activeShift = shiftDao.getActiveShiftDirect()
                    if (activeShift == null) {
                        withContext(Dispatchers.Main) { onError("No active shift to freeze") }
                        return@withContext
                    }
                    if (activeShift.status == "FROZEN") {
                        withContext(Dispatchers.Main) { onError("Shift is already frozen") }
                        return@withContext
                    }
                    val cutoffTime = System.currentTimeMillis()
                    shiftDao.updateShift(
                        activeShift.copy(
                            status = "FROZEN",
                            cutoff_timestamp = cutoffTime,
                            updated_at = System.currentTimeMillis()
                        )
                    )
                    Log.d("SHIFT_FREEZE", "✅ Shift #${activeShift.shift_id} FROZEN at $cutoffTime")
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                Log.e("SHIFT_FREEZE", "❌ Error freezing shift: ${e.message}", e)
                withContext(Dispatchers.Main) { onError("Failed to freeze shift: ${e.message}") }
            }
        }
    }

    /**
     * Close shift with ROLE-BASED reconciliation formula:
     *
     * Transfers Out     = SUM of all transactions where direction == OUT
     *                     (TILL_TRANSFER_OUT + WITHDRAWAL + REVERSAL)
     *
     * Customer Receipts = SUM of all transactions where direction == IN
     *                     (CUSTOMER_RECEIPT + TILL_TRANSFER_IN)
     *
     * Expected Float    = Closing Balance − Opening Balance + Transfers Out
     * Variance          = Expected Float − Customer Receipts
     *
     * DUPLICATE and UNASSIGNED are excluded from both sides entirely.
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
                if (shift == null) { onError("Shift not found"); return@launch }

                val shiftTransactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)

                // Money OUT of the float — every OUT direction transaction
                val transfersOut = shiftTransactions
                    .filter { it.direction == TransactionDirection.OUT }
                    .sumOf { it.amount }

                // Money IN to the float — every IN direction transaction
                val customerReceipts = shiftTransactions
                    .filter { it.direction == TransactionDirection.IN }
                    .sumOf { it.amount }

                // Core formula
                val netChange = closingBalance - shift.open_balance
                val expectedFloat = netChange + transfersOut
                val variance = expectedFloat - customerReceipts

                Log.d("SHIFT_CLOSE", "════════════════════════════════════")
                Log.d("SHIFT_CLOSE", "Opening Balance  : Ksh ${shift.open_balance}")
                Log.d("SHIFT_CLOSE", "Closing Balance  : Ksh $closingBalance")
                Log.d("SHIFT_CLOSE", "Net Change       : Ksh $netChange")
                Log.d("SHIFT_CLOSE", "Transfers Out    : Ksh $transfersOut")
                Log.d("SHIFT_CLOSE", "Expected Float   : Ksh $expectedFloat")
                Log.d("SHIFT_CLOSE", "Customer Receipts: Ksh $customerReceipts")
                Log.d("SHIFT_CLOSE", "Variance         : Ksh $variance")
                Log.d("SHIFT_CLOSE", "════════════════════════════════════")

                val byRole = shiftTransactions.groupBy { it.role }
                byRole.forEach { (role, txns) ->
                    Log.d("SHIFT_CLOSE", "  $role: ${txns.size} txns, Ksh ${txns.sumOf { it.amount }}")
                }

                val closingBalanceTransaction = shiftTransactions
                    .filter { abs(it.account_balance - closingBalance) < 0.01 }
                    .maxByOrNull { it.timestamp }

                val cutoffTime = closingBalanceTransaction?.timestamp?.plus(1000)
                    ?: (shiftTransactions.maxByOrNull { it.timestamp }?.timestamp?.plus(1000)
                        ?: System.currentTimeMillis())

                shiftDao.closeShiftWithReconciliation(
                    shiftId = shiftId,
                    endTime = System.currentTimeMillis(),
                    closeBalance = closingBalance,
                    cutoffTimestamp = cutoffTime,
                    netChange = netChange,
                    moneySentOut = transfersOut,
                    expectedReceipts = expectedFloat,
                    actualReceipts = customerReceipts,
                    variance = variance,
                    updatedAt = System.currentTimeMillis()
                )

                _syncStatus.value = "Syncing to cloud..."
                val updatedShift = shiftDao.getShiftByIdDirect(shiftId)
                if (updatedShift != null) {
                    val derivedAssignments = shiftTransactions
                        .filter { !it.assigned_to.isNullOrBlank() && it.assigned_to != "Neutral" }
                        .groupBy { it.assigned_to!! }
                        .map { (personName, _) ->
                            ShiftAssignment(shift_id = shiftId, person_name = personName, role = "CSA")
                        }

                    when (val syncResult = cloudSyncManager.syncShiftToCloud(
                        updatedShift, shiftTransactions, derivedAssignments
                    )) {
                        is SyncResult.Success -> _syncStatus.value = "✅ Synced to cloud"
                        is SyncResult.Failure -> _syncStatus.value = "⚠️ Sync failed: ${syncResult.error}"
                    }
                }

                onSuccess()

            } catch (e: Exception) {
                Log.e("SHIFT_CLOSE", "❌ Error closing shift: ${e.message}", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

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
                        withContext(Dispatchers.Main) { onError("Shift not found") }
                        return@withContext
                    }
                    if (shift.status != "CLOSED") {
                        withContext(Dispatchers.Main) {
                            onError("Can only update closing balance for closed shifts")
                        }
                        return@withContext
                    }

                    val shiftTransactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)

                    val transfersOut = shiftTransactions
                        .filter { it.direction == TransactionDirection.OUT }
                        .sumOf { it.amount }

                    val customerReceipts = shiftTransactions
                        .filter { it.direction == TransactionDirection.IN }
                        .sumOf { it.amount }

                    val netChange = newClosingBalance - shift.open_balance
                    val expectedFloat = netChange + transfersOut
                    val variance = expectedFloat - customerReceipts

                    shiftDao.updateClosingBalanceAndReconciliation(
                        shiftId = shiftId,
                        closeBalance = newClosingBalance,
                        netChange = netChange,
                        expectedReceipts = expectedFloat,
                        variance = variance,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating closing balance: ${e.message}", e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Failed to update closing balance") }
            }
        }
    }

    // ============ TRANSACTION LOADING ============

    fun refreshTransactions() {
        currentShift.value?.let { loadShiftTransactions(it.shift_id) }
    }

    private fun loadShiftTransactions(shiftId: Long) {
        viewModelScope.launch {
            try {
                val transactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)
                _unassignedTransactions.value = transactions.filter {
                    it.role == TransactionRole.UNASSIGNED
                }
                _assignedTransactions.value = transactions.filter {
                    it.role != TransactionRole.UNASSIGNED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading transactions", e)
                _errorMessage.value = "Error loading transactions: ${e.message}"
            }
        }
    }

    private suspend fun assignUnassignedTransactionsToShift(shiftId: Long) {
        transactionDao.getAllTransactions()
            .filter { it.shift_id == null }
            .forEach { transactionDao.updateTransaction(it.copy(shift_id = shiftId)) }
    }

    // ============ ASSIGNMENT ============

    /**
     * Assign transactions with double-assignment guard.
     * If an assignment is already in progress, the second call is silently
     * dropped — the UI button is also disabled while _isAssigning == true
     * so this is a belt-and-suspenders safety net.
     */
    fun assignTransactions(transactionIds: List<Long>, personName: String, role: TransactionRole) {
        if (_isAssigning.value == true) {
            Log.w(TAG, "⚠️ Assignment already in progress — ignoring duplicate tap")
            return
        }
        _isAssigning.value = true

        viewModelScope.launch {
            try {
                transactionIds.forEach { id ->
                    transactionDao.assignTransactionWithRole(
                        transactionId = id,
                        personName = personName.ifBlank { null },
                        role = role.name,
                        direction = role.toDirection().name,
                        includedInReconciliation = role.includedInReconciliation(),
                        modifiedAt = System.currentTimeMillis()
                    )
                }

                Log.d(TAG, "✅ Assigned ${transactionIds.size} transactions — role=$role, csa=${personName.ifBlank { "none" }}")

                transactionIds.forEach { id ->
                    val txn = transactionDao.getTransactionById(id)
                    if (txn != null) cloudSyncManager.backupAssignedTransaction(txn)
                }

                currentShift.value?.let { loadShiftTransactions(it.shift_id) }

            } catch (e: Exception) {
                Log.e(TAG, "Error assigning transactions", e)
                _errorMessage.value = "Error assigning transactions: ${e.message}"
            } finally {
                // Always release the lock — even if something went wrong
                _isAssigning.value = false
            }
        }
    }

    fun assignTransactions(transactionIds: List<Long>, personName: String, category: String) {
        val role = when (category) {
            "CSA"         -> TransactionRole.CUSTOMER_RECEIPT
            "TRANSFER_IN" -> TransactionRole.TILL_TRANSFER_IN
            "NEUTRAL"     -> TransactionRole.WITHDRAWAL
            "DUPLICATE"   -> TransactionRole.DUPLICATE
            else          -> TransactionRole.CUSTOMER_RECEIPT
        }
        assignTransactions(transactionIds, personName, role)
    }

    fun bulkAssignAll(
        shiftId: Long,
        personName: String,
        onProgress: (assigned: Int, total: Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isAssigning.value == true) {
            Log.w(TAG, "⚠️ Assignment already in progress — ignoring bulk assign tap")
            return
        }
        _isAssigning.value = true

        viewModelScope.launch {
            try {
                val allTransactions = transactionDao.getTransactionsByShiftIdDirect(shiftId)
                val unassigned = allTransactions.filter { it.role == TransactionRole.UNASSIGNED }
                val total = unassigned.size
                var assigned = 0

                Log.d(TAG, "🔄 Bulk assigning $total transactions to $personName")

                unassigned.chunked(100).forEach { batch ->
                    batch.forEach { txn ->
                        transactionDao.assignTransactionWithRole(
                            transactionId = txn.id,
                            personName = personName,
                            role = TransactionRole.CUSTOMER_RECEIPT.name,
                            direction = TransactionDirection.IN.name,
                            includedInReconciliation = true,
                            modifiedAt = System.currentTimeMillis()
                        )
                        assigned++
                    }
                    onProgress(assigned, total)
                }

                Log.d(TAG, "✅ Bulk assign complete: $assigned transactions to $personName")
                loadShiftTransactions(shiftId)
                onComplete()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Bulk assign failed: ${e.message}", e)
                onError(e.message ?: "Unknown error")
            } finally {
                _isAssigning.value = false
            }
        }
    }

    fun reassignTransaction(transactionId: Long, newPersonName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                val role = when (newCategory) {
                    "TRANSFER_IN" -> TransactionRole.TILL_TRANSFER_IN
                    "NEUTRAL"     -> TransactionRole.WITHDRAWAL
                    "DUPLICATE"   -> TransactionRole.DUPLICATE
                    else          -> TransactionRole.CUSTOMER_RECEIPT
                }
                transactionDao.assignTransactionWithRole(
                    transactionId = transactionId,
                    personName = newPersonName.ifBlank { null },
                    role = role.name,
                    direction = role.toDirection().name,
                    includedInReconciliation = role.includedInReconciliation(),
                    modifiedAt = System.currentTimeMillis()
                )

                val txn = transactionDao.getTransactionById(transactionId)
                if (txn != null) cloudSyncManager.backupAssignedTransaction(txn)

                currentShift.value?.let { loadShiftTransactions(it.shift_id) }

                Log.d(TAG, "✅ Reassigned transaction $transactionId to $newPersonName ($role)")
            } catch (e: Exception) {
                Log.e(TAG, "Error reassigning transaction", e)
                _errorMessage.value = "Error reassigning: ${e.message}"
            }
        }
    }

    fun unassignTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                transactionDao.assignTransactionWithRole(
                    transactionId = transactionId,
                    personName = null,
                    role = TransactionRole.UNASSIGNED.name,
                    direction = TransactionDirection.NONE.name,
                    includedInReconciliation = false,
                    modifiedAt = System.currentTimeMillis()
                )
                currentShift.value?.let { loadShiftTransactions(it.shift_id) }
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
                val displayName = if (fullName.isNotEmpty()) "$shortName ($fullName)" else shortName
                val person = Person(
                    name = fullName,
                    short_name = displayName,
                    is_active = true,
                    display_order = personDao.getActivePersonCount() + 1
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
                val displayName = if (fullName.isNotEmpty()) "$shortName ($fullName)" else shortName
                personDao.updatePerson(
                    person.copy(
                        name = fullName,
                        short_name = displayName,
                        updated_at = System.currentTimeMillis()
                    )
                )
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
                if (person.is_active) personDao.deactivatePerson(personId)
                else personDao.reactivatePerson(personId)
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
                if (total > 0) breakdown[person.short_name] = total
            }
            val internalTransfers = transactionDao.getTotalByShiftAndCategory(shiftId, "NEUTRAL") ?: 0.0
            if (internalTransfers != 0.0) breakdown["Neutral Transactions"] = internalTransfers
        } catch (e: Exception) {
            Log.e(TAG, "Error getting shift breakdown", e)
        }
        return breakdown
    }
}