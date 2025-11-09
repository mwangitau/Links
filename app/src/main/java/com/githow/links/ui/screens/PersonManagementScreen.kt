package com.githow.links.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githow.links.viewmodel.ShiftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonManagementScreen(
    viewModel: ShiftViewModel,
    onNavigateBack: () -> Unit
) {
    val persons by viewModel.persons.observeAsState(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<com.githow.links.data.entity.Person?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage People") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Person")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add people who collect M-PESA payments (CSA 1, CSA 2, etc.)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // People List
            if (persons.isEmpty()) {
                EmptyPeopleMessage()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(persons) { person ->
                        PersonCard(
                            person = person,
                            onEdit = { editingPerson = person },
                            onToggleActive = { viewModel.togglePersonActive(person.person_id) }
                        )
                    }
                }
            }
        }
    }

    // Add Person Dialog
    if (showAddDialog) {
        AddEditPersonDialog(
            person = null,
            onDismiss = { showAddDialog = false },
            onSave = { shortName, fullName ->
                viewModel.addPerson(shortName, fullName)
                showAddDialog = false
            }
        )
    }

    // Edit Person Dialog
    if (editingPerson != null) {
        AddEditPersonDialog(
            person = editingPerson,
            onDismiss = { editingPerson = null },
            onSave = { shortName, fullName ->
                viewModel.updatePerson(editingPerson!!.person_id, shortName, fullName)
                editingPerson = null
            }
        )
    }
}

@Composable
fun PersonCard(
    person: com.githow.links.data.entity.Person,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (person.is_active)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.short_name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (person.is_active)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (person.name.isNotEmpty() && person.name != person.short_name) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (!person.is_active) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Inactive",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Edit Button
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }

            // Toggle Active/Inactive
            Switch(
                checked = person.is_active,
                onCheckedChange = { onToggleActive() }
            )
        }
    }
}

@Composable
fun AddEditPersonDialog(
    person: com.githow.links.data.entity.Person?,
    onDismiss: () -> Unit,
    onSave: (shortName: String, fullName: String) -> Unit
) {
    var shortName by remember { mutableStateOf(person?.short_name ?: "") }
    var fullName by remember { mutableStateOf(person?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "Add Person" else "Edit Person") },
        text = {
            Column {
                Text(
                    text = "Enter person details:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = shortName,
                    onValueChange = { shortName = it },
                    label = { Text("Short Name (e.g., CSA 1)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name (optional)") },
                    placeholder = { Text("e.g., John Mwangi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Display as: ${if (fullName.isNotEmpty()) "$shortName ($fullName)" else shortName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(shortName, fullName) },
                enabled = shortName.isNotBlank()
            ) {
                Text(if (person == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyPeopleMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No people added yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to add your first person",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}