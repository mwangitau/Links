package com.githow.links

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githow.links.service.SmsForegroundService
import com.githow.links.config.StationConfig
import com.githow.links.worker.SupabaseSyncWorker
import com.githow.links.ui.theme.LINKSTheme
import com.githow.links.ui.screens.CloseShiftScreen
import com.githow.links.ui.screens.ClosedShiftsHistoryScreen
import com.githow.links.ui.screens.HomeScreen
import com.githow.links.ui.screens.PinScreen
import com.githow.links.ui.screens.ManualReviewScreen
import com.githow.links.ui.screens.OpenShiftScreen
import com.githow.links.ui.screens.PersonManagementScreen
import com.githow.links.ui.screens.ShiftDashboardScreen
import com.githow.links.ui.screens.ShiftReportScreen
import com.githow.links.ui.screens.SmsScreen
import com.githow.links.ui.screens.SupervisorSetupScreen
import com.githow.links.ui.screens.TransactionAssignmentScreen
import com.githow.links.ui.screens.TransactionListScreen
import com.githow.links.ui.screens.UnparsedSmsScreen
import com.githow.links.data.database.LinksDatabase
import com.githow.links.sync.CloudSyncManager
import com.githow.links.sync.SyncResult
import com.githow.links.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LINKS"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "✅ All permissions granted!")
        } else {
            Log.w(TAG, "⚠️ Some permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        SmsForegroundService.start(this)
        SupabaseSyncWorker.schedulePeriodicSync(this)
        requestBatteryOptimisationExemption()

        setContent {
            LINKSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    private fun requestBatteryOptimisationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "Requesting battery optimisation exemption")
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        Log.w(TAG, "Could not open battery optimisation settings")
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            Log.d(TAG, "✅ All permissions already granted!")
        }
    }
}

enum class Screen {
    LOGIN,
    SETUP_SUPERVISOR,
    HOME,
    TRANSACTIONS,
    MANUAL_REVIEW,
    SMS,
    UNPARSED_SMS,
    SETTINGS,
    OPEN_SHIFT,
    CLOSE_SHIFT,
    SHIFT_DASHBOARD,
    ASSIGN_TRANSACTIONS,
    SHIFT_HISTORY,
    SHIFT_DETAILS,
    MANAGE_PERSONS
}

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.LOGIN) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedShiftId by remember { mutableLongStateOf(0L) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val manualReviewViewModel: ManualReviewViewModel = viewModel()
    val transactionViewModel: TransactionViewModel = viewModel()
    val smsViewModel: SmsViewModel = viewModel()
    val shiftViewModel: ShiftViewModel = viewModel()
    val unparsedSmsViewModel: UnparsedSmsViewModel = viewModel()

    Scaffold(
        bottomBar = {
            if (currentScreen in listOf(
                    Screen.HOME, Screen.TRANSACTIONS, Screen.MANUAL_REVIEW,
                    Screen.SMS, Screen.SETTINGS
                ) && currentScreen != Screen.LOGIN
            ) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, "Home") },
                        label = { Text("Home") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; currentScreen = Screen.HOME }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, "Transactions") },
                        label = { Text("Transactions") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; currentScreen = Screen.TRANSACTIONS }
                    )
                    NavigationBarItem(
                        icon = {
                            val pendingCount by manualReviewViewModel.pendingCount.collectAsState()
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) Badge { Text("$pendingCount") }
                                }
                            ) {
                                Icon(Icons.Default.Warning, "Review")
                            }
                        },
                        label = { Text("Review") },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; currentScreen = Screen.MANUAL_REVIEW }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Email, "SMS") },
                        label = { Text("SMS") },
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3; currentScreen = Screen.SMS }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings") },
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4; currentScreen = Screen.SETTINGS }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.LOGIN -> PinScreen(
                    onAuthenticated = {
                        scope.launch {
                            val supervisorExists = withContext(Dispatchers.IO) {
                                LinksDatabase.getDatabase(context).userDao().supervisorExists()
                            }
                            currentScreen = if (supervisorExists) Screen.HOME else Screen.SETUP_SUPERVISOR
                        }
                    }
                )
                Screen.SETUP_SUPERVISOR -> SupervisorSetupScreen(
                    onSetupComplete = { currentScreen = Screen.HOME }
                )
                Screen.HOME -> HomeScreen(
                    onNavigateToOpenShift = { currentScreen = Screen.OPEN_SHIFT },
                    onNavigateToCloseShift = { currentScreen = Screen.CLOSE_SHIFT },
                    onNavigateToShiftDashboard = { currentScreen = Screen.SHIFT_DASHBOARD },
                    onNavigateToAssignTransactions = { currentScreen = Screen.ASSIGN_TRANSACTIONS },
                    onNavigateToShiftHistory = { currentScreen = Screen.SHIFT_HISTORY },
                    onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                )
                Screen.TRANSACTIONS -> TransactionListScreen(viewModel = transactionViewModel)
                Screen.MANUAL_REVIEW -> ManualReviewScreen(
                    viewModel = manualReviewViewModel,
                    onNavigateBack = { selectedTab = 0; currentScreen = Screen.HOME }
                )
                Screen.SMS -> SmsScreen(
                    viewModel = smsViewModel,
                    onNavigateToUnparsed = { currentScreen = Screen.UNPARSED_SMS }
                )
                Screen.UNPARSED_SMS -> UnparsedSmsScreen(
                    viewModel = unparsedSmsViewModel,
                    onNavigateBack = { currentScreen = Screen.SMS },
                    onManualEntry = {
                        selectedTab = 2
                        currentScreen = Screen.MANUAL_REVIEW
                    }
                )
                Screen.SETTINGS -> SettingsScreen()
                Screen.OPEN_SHIFT -> OpenShiftScreen(
                    viewModel = shiftViewModel,
                    onNavigateBack = { currentScreen = Screen.HOME }
                )
                Screen.CLOSE_SHIFT -> CloseShiftScreen(
                    viewModel = shiftViewModel,
                    onNavigateBack = { currentScreen = Screen.HOME }
                )
                Screen.SHIFT_DASHBOARD -> ShiftDashboardScreen(
                    viewModel = shiftViewModel,
                    onNavigateToOpenShift = { currentScreen = Screen.OPEN_SHIFT },
                    onNavigateToCloseShift = { currentScreen = Screen.CLOSE_SHIFT },
                    onNavigateToAssignTransactions = { currentScreen = Screen.ASSIGN_TRANSACTIONS },
                    onNavigateToManageCSAs = { currentScreen = Screen.MANAGE_PERSONS },
                    onNavigateToShiftSummary = { currentScreen = Screen.HOME },
                    onNavigateToHistory = { currentScreen = Screen.SHIFT_HISTORY }
                )
                Screen.ASSIGN_TRANSACTIONS -> TransactionAssignmentScreen(
                    viewModel = shiftViewModel,
                    onNavigateBack = { currentScreen = Screen.SHIFT_DASHBOARD }
                )
                Screen.SHIFT_HISTORY -> ClosedShiftsHistoryScreen(
                    viewModel = shiftViewModel,
                    onNavigateBack = { currentScreen = Screen.HOME },
                    onViewShiftDetails = { shiftId ->
                        selectedShiftId = shiftId
                        currentScreen = Screen.SHIFT_DETAILS
                    }
                )
                Screen.SHIFT_DETAILS -> ShiftReportScreen(
                    shiftId = selectedShiftId,
                    viewModel = shiftViewModel,
                    onNavigateBack = { currentScreen = Screen.SHIFT_HISTORY }
                )
                Screen.MANAGE_PERSONS -> PersonManagementScreen(
                    viewModel = shiftViewModel,
                    onNavigateBack = { currentScreen = Screen.SHIFT_DASHBOARD }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var stationCode by remember { mutableStateOf(StationConfig.getStationCode(context)) }
    var stationName by remember { mutableStateOf(StationConfig.getStationName(context)) }
    var tillNumber by remember { mutableStateOf(StationConfig.getTillNumber(context)) }
    var paybillNumber by remember { mutableStateOf(StationConfig.getPaybillNumber(context)) }

    var showSaved by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var cachedUuid by remember { mutableStateOf(StationConfig.getCachedUuid(context)) }

    val isConfigured = StationConfig.isConfigured(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            // ── Station Status Card ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConfigured)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isConfigured) "✅ Station Configured" else "⚠️ Station Not Configured",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    if (isConfigured) {
                        Text(
                            StationConfig.toDisplayString(context),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Fill in station details below before syncing.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Station Identity Fields ──────────────────────────────────────
            Text(
                "Station Identity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Each phone must have a unique station code matching a row in Supabase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = stationCode,
                onValueChange = { stationCode = it.uppercase() },
                label = { Text("Station Code *") },
                placeholder = { Text("e.g. MANGU, GATAKA") },
                supportingText = { Text("Must match station_code in Supabase stations table") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = stationName,
                onValueChange = { stationName = it },
                label = { Text("Station Name *") },
                placeholder = { Text("e.g. TotalEnergies Gataka Road") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = tillNumber,
                onValueChange = { tillNumber = it },
                label = { Text("M-PESA Till Number") },
                placeholder = { Text("e.g. 3424425") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = paybillNumber,
                onValueChange = { paybillNumber = it },
                label = { Text("M-PESA Paybill Number") },
                placeholder = { Text("e.g. 3424425") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (stationCode.isBlank() || stationName.isBlank()) {
                        showError = true
                        showSaved = false
                    } else {
                        StationConfig.save(
                            context = context,
                            stationCode = stationCode,
                            stationName = stationName,
                            tillNumber = tillNumber,
                            paybillNumber = paybillNumber
                        )
                        cachedUuid = StationConfig.getCachedUuid(context)
                        showSaved = true
                        showError = false
                        syncMessage = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Save Station Config")
            }

            if (showSaved) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        "✅ Saved — Station: ${StationConfig.getStationCode(context)}",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (showError) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "❌ Station Code and Station Name are required.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ── Supabase Sync Section ────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "Supabase Sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            // UUID status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (cachedUuid.isNotBlank())
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        if (cachedUuid.isNotBlank()) "✅ UUID Resolved" else "⚠️ UUID Not Resolved",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (cachedUuid.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (cachedUuid.isNotBlank()) cachedUuid
                        else "Save station config then tap Sync Now — UUID will be resolved from Supabase automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "The station_code must match a row in the Supabase stations table.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // ── Sync Now Button — runs directly, bypasses WorkManager ────────
            // WorkManager network constraints are unreliable on Unisoc/SPRD ROMs
            Button(
                onClick = {
                    isSyncing = true
                    syncMessage = null
                    scope.launch {
                        try {
                            val db = LinksDatabase.getDatabase(context)
                            val rawSmsDao = db.rawSmsDao()
                            val transactionDao = db.transactionDao()
                            val syncManager = CloudSyncManager(context)

                            // Fetch unsynced records
                            val unsyncedSms = withContext(Dispatchers.IO) {
                                rawSmsDao.getUnsyncedSms(limit = 50)
                            }
                            val unsyncedTxns = withContext(Dispatchers.IO) {
                                transactionDao.getUnsyncedTransactions(limit = 100)
                            }

                            var smsSynced = 0
                            var txnSynced = 0
                            var failed = 0

                            // Push unsynced raw SMS
                            withContext(Dispatchers.IO) {
                                for (sms in unsyncedSms) {
                                    when (val result = syncManager.backupRawSms(sms)) {
                                        is SyncResult.Success -> {
                                            rawSmsDao.markAsSynced(sms.id)
                                            smsSynced++
                                        }
                                        is SyncResult.Failure -> {
                                            Log.e("SYNC", "SMS ${sms.id} failed: ${result.error}")
                                            failed++
                                        }
                                    }
                                }
                            }

                            // Push unsynced transactions
                            withContext(Dispatchers.IO) {
                                for (txn in unsyncedTxns) {
                                    when (val result = syncManager.backupAssignedTransaction(txn)) {
                                        is SyncResult.Success -> {
                                            transactionDao.markTransactionSynced(txn.id)
                                            txnSynced++
                                        }
                                        is SyncResult.Failure -> {
                                            Log.e("SYNC", "Txn ${txn.mpesa_code} failed: ${result.error}")
                                            failed++
                                        }
                                    }
                                }
                            }

                            // Refresh UUID display — will now show resolved UUID
                            // if this was the first successful sync
                            cachedUuid = StationConfig.getCachedUuid(context)

                            syncMessage = when {
                                failed > 0 && smsSynced == 0 && txnSynced == 0 ->
                                    "❌ Sync failed — check internet and station config. Is station code EXACTLY '$stationCode' in Supabase?"
                                failed > 0 ->
                                    "⚠️ Partial — SMS: $smsSynced ✅, Transactions: $txnSynced ✅, Failed: $failed ❌"
                                smsSynced == 0 && txnSynced == 0 ->
                                    "✅ Nothing to sync — all records already up to date"
                                else ->
                                    "✅ Sync complete — SMS: $smsSynced, Transactions: $txnSynced pushed to Supabase"
                            }

                        } catch (e: Exception) {
                            Log.e("SYNC", "Direct sync error: ${e.message}", e)
                            syncMessage = "❌ Sync error: ${e.message}"
                        } finally {
                            isSyncing = false
                        }
                    }
                },
                enabled = isConfigured && !isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isSyncing) "Syncing..." else "☁️ Sync Now")
            }

            // Sync result message
            syncMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            message.startsWith("✅") -> MaterialTheme.colorScheme.primaryContainer
                            message.startsWith("⚠️") -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}