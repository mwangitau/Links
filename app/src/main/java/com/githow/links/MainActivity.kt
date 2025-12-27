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
import androidx.compose.ui.unit.dp  // ✅ ADDED
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githow.links.ui.theme.LINKSTheme  // ✅ CHANGED from LinksTheme
import com.githow.links.ui.screens.ManualReviewScreen  // ✅ ADDED
import com.githow.links.viewmodel.ManualReviewViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LINKS"
    }

    // Permission launcher
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

        // Check and request permissions
        checkPermissions()

        setContent {
            LINKSTheme {  // ✅ CHANGED from LinksTheme
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

        // SMS permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_SMS)
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
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

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val manualReviewViewModel: ManualReviewViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar {
                // Home Tab
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )

                // Transactions Tab
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, "Transactions") },
                    label = { Text("Transactions") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )

                // Manual Review Tab (NEW!)
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
                    onClick = { selectedTab = 2 }
                )

                // SMS Tab
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Email, "SMS") },
                    label = { Text("SMS") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )

                // Settings Tab
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> TransactionsScreen()
                2 -> ManualReviewScreen(
                    viewModel = manualReviewViewModel,
                    onNavigateBack = { selectedTab = 0 }
                )
                3 -> SmsScreen()
                4 -> SettingsScreen()
            }
        }
    }
}

// Placeholder screens - Replace these with your actual screens!

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "LINKS - M-PESA Manager",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Welcome to LINKS v3.0!", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("New Feature: Manual Review System")
                Text("• Review unparsed M-PESA SMS")
                Text("• Supervisor password protection")
                Text("• Manual transaction entry")
            }
        }
    }
}

@Composable
fun TransactionsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Transactions",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Your transaction list will appear here")
        // TODO: Add your TransactionViewModel and list
    }
}

@Composable
fun SmsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "SMS History",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Your SMS history will appear here")
        // TODO: Add your SMS list
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
        // TODO: Add your settings options
    }
}