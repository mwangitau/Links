package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githow.links.data.entity.Shift
import com.githow.links.viewmodel.ShiftViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenShiftScreen(
    viewModel: ShiftViewModel,
    onNavigateBack: () -> Unit
) {
    val lastShift by viewModel.getLastClosedShift().observeAsState()
    var isProcessing by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val currentActiveShift by viewModel.currentActiveShift.observeAsState()

    val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    val openingBalance = lastShift?.close_balance ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open New Shift") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentActiveShift != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "⚠️ Active Shift Already Open",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Close the current shift before opening a new one.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Go Back")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "New Shift Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))

                        if (lastShift != null) {
                            Text("Previous Shift", style = MaterialTheme.typography.labelMedium)
                            Text("Shift #${lastShift?.shift_id}", fontWeight = FontWeight.Medium)
                            Text(
                                "Closed: ${dateFormat.format(Date(lastShift?.end_time ?: 0))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Text(
                                    "ℹ️ This is your first shift",
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        Text("Opening Balance", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "KES ${numberFormat.format(openingBalance)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Start Time", style = MaterialTheme.typography.labelMedium)
                        Text(dateFormat.format(Date()), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        isProcessing = true
                        viewModel.openNewShift(
                            shift = Shift(
                                start_time = System.currentTimeMillis(),
                                open_balance = openingBalance,
                                status = "ACTIVE"
                            ),
                            onSuccess = {
                                isProcessing = false
                                showSuccessDialog = true
                            },
                            onError = { error ->
                                isProcessing = false
                                errorMessage = error
                                showErrorDialog = true
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isProcessing) "Opening Shift..." else "Open Shift")
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("✓ Shift Opened") },
            text = { Text("New shift opened with balance of KES ${numberFormat.format(openingBalance)}") },
            confirmButton = {
                Button(onClick = { showSuccessDialog = false; onNavigateBack() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) { Text("OK") }
            }
        )
    }
}