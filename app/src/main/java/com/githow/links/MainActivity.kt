package com.githow.links

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.githow.links.ui.screens.*
import com.githow.links.ui.theme.LINKSTheme
import com.githow.links.viewmodel.ShiftViewModel
import com.githow.links.viewmodel.TransactionViewModel

class MainActivity : ComponentActivity() {

    private val transactionViewModel: TransactionViewModel by viewModels()
    private val shiftViewModel: ShiftViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("LINKS", "✅ All permissions granted!")
            Toast.makeText(this, "LINKS is ready!", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("LINKS", "❌ Some permissions denied!")
            Toast.makeText(this, "SMS permissions required!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestSmsPermissions()

        setContent {
            LINKSTheme {
                LINKSApp(
                    transactionViewModel = transactionViewModel,
                    shiftViewModel = shiftViewModel
                )
            }
        }
    }

    private fun requestSmsPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsNeeded = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            Log.d("LINKS", "🔐 Requesting permissions")
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            Log.d("LINKS", "✅ All permissions already granted!")
        }
    }
}

@Composable
fun LINKSApp(
    transactionViewModel: TransactionViewModel,
    shiftViewModel: ShiftViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf("main") }
    var selectedShiftId by remember { mutableStateOf<Long?>(null) }

    // Handle sub-screens
    when (currentScreen) {
        "assignment" -> {
            TransactionAssignmentScreen(
                viewModel = shiftViewModel,
                onNavigateBack = { currentScreen = "main" }
            )
            return
        }
        "persons" -> {
            PersonManagementScreen(
                viewModel = shiftViewModel,
                onNavigateBack = { currentScreen = "main" }
            )
            return
        }
        "shift_summary" -> {
            ShiftSummaryScreen(
                viewModel = shiftViewModel,
                onNavigateBack = { currentScreen = "main" }
            )
            return
        }
        "shift_history" -> {
            ClosedShiftsHistoryScreen(
                viewModel = shiftViewModel,
                onNavigateBack = { currentScreen = "main" },
                onViewShiftDetails = { shiftId ->
                    selectedShiftId = shiftId
                    currentScreen = "shift_report"
                }
            )
            return
        }
        "shift_report" -> {
            selectedShiftId?.let { shiftId ->
                ShiftReportScreen(
                    shiftId = shiftId,
                    viewModel = shiftViewModel,
                    onNavigateBack = {
                        currentScreen = "shift_history"
                        selectedShiftId = null
                    }
                )
            }
            return
        }
        "open_shift" -> {
            OpenShiftScreen(
                viewModel = shiftViewModel,
                onNavigateBack = { currentScreen = "main" }
            )
            return
        }
        "close_shift" -> {
            CloseShiftScreen(
                viewModel = shiftViewModel,
                onNavigateBack = { currentScreen = "main" }
            )
            return
        }
    }

    // Main screens with bottom navigation
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Transactions") },
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        currentScreen = "main"
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("Shifts") },
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        currentScreen = "main"
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> TransactionListScreen(viewModel = transactionViewModel)
                1 -> ShiftDashboardScreen(
                    viewModel = shiftViewModel,
                    onNavigateToOpenShift = { currentScreen = "open_shift" },
                    onNavigateToCloseShift = { currentScreen = "close_shift" },
                    onNavigateToAssignTransactions = { currentScreen = "assignment" },
                    onNavigateToManageCSAs = { currentScreen = "persons" },
                    onNavigateToShiftSummary = { currentScreen = "shift_summary" },
                    onNavigateToHistory = { currentScreen = "shift_history" }
                )
            }
        }
    }
}