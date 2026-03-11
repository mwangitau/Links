package com.githow.links

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githow.links.ui.theme.LINKSTheme
import com.githow.links.ui.screens.CloseShiftScreen
import com.githow.links.ui.screens.ClosedShiftsHistoryScreen
import com.githow.links.ui.screens.HomeScreen
import com.githow.links.ui.screens.ManualReviewScreen
import com.githow.links.ui.screens.OpenShiftScreen
import com.githow.links.ui.screens.PersonManagementScreen
import com.githow.links.ui.screens.ShiftDashboardScreen
import com.githow.links.ui.screens.ShiftReportScreen         // ✅ ADDED
import com.githow.links.ui.screens.SmsScreen
import com.githow.links.ui.screens.TransactionAssignmentScreen
import com.githow.links.ui.screens.TransactionListScreen
import com.githow.links.viewmodel.*

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
    HOME,
    TRANSACTIONS,
    MANUAL_REVIEW,
    SMS,
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
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedShiftId by remember { mutableLongStateOf(0L) }

    // ViewModels
    val manualReviewViewModel: ManualReviewViewModel = viewModel()
    val transactionViewModel: TransactionViewModel = viewModel()
    val smsViewModel: SmsViewModel = viewModel()
    val shiftViewModel: ShiftViewModel = viewModel()

    Scaffold(
        bottomBar = {
            if (currentScreen in listOf(Screen.HOME, Screen.TRANSACTIONS, Screen.MANUAL_REVIEW, Screen.SMS, Screen.SETTINGS)) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, "Home") },
                        label = { Text("Home") },
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            currentScreen = Screen.HOME
                        }
                    )

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, "Transactions") },
                        label = { Text("Transactions") },
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            currentScreen = Screen.TRANSACTIONS
                        }
                    )

                    NavigationBarItem(
                        icon = {
                            val pendingCount by manualReviewViewModel.pendingCount.collectAsState()
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge { Text("$pendingCount") }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Warning, "Review")
                            }
                        },
                        label = { Text("Review") },
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            currentScreen = Screen.MANUAL_REVIEW
                        }
                    )

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Email, "SMS") },
                        label = { Text("SMS") },
                        selected = selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                            currentScreen = Screen.SMS
                        }
                    )

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings") },
                        selected = selectedTab == 4,
                        onClick = {
                            selectedTab = 4
                            currentScreen = Screen.SETTINGS
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    onNavigateToOpenShift = { currentScreen = Screen.OPEN_SHIFT },
                    onNavigateToCloseShift = { currentScreen = Screen.CLOSE_SHIFT },
                    onNavigateToShiftDashboard = { currentScreen = Screen.SHIFT_DASHBOARD },
                    onNavigateToAssignTransactions = { currentScreen = Screen.ASSIGN_TRANSACTIONS },
                    onNavigateToShiftHistory = { currentScreen = Screen.SHIFT_HISTORY }
                )

                Screen.TRANSACTIONS -> TransactionListScreen(viewModel = transactionViewModel)

                Screen.MANUAL_REVIEW -> ManualReviewScreen(
                    viewModel = manualReviewViewModel,
                    onNavigateBack = {
                        selectedTab = 0
                        currentScreen = Screen.HOME
                    }
                )

                Screen.SMS -> SmsScreen(viewModel = smsViewModel)

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

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("App settings will appear here")
    }
}
