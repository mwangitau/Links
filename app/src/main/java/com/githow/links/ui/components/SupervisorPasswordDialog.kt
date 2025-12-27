package com.githow.links.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.githow.links.data.database.LinksDatabase
import com.githow.links.service.AuthenticationService
import kotlinx.coroutines.launch

/**
 * SupervisorPasswordDialog - Password authentication for supervisor actions
 *
 * Usage:
 * ```
 * var showDialog by remember { mutableStateOf(false) }
 *
 * if (showDialog) {
 *     SupervisorPasswordDialog(
 *         title = "Close Shift",
 *         message = "Enter supervisor password to close this shift",
 *         onAuthenticated = { supervisor ->
 *             // Password correct, proceed with action
 *             viewModel.closeShift(closedBy = supervisor.username)
 *             showDialog = false
 *         },
 *         onDismiss = {
 *             showDialog = false
 *         }
 *     )
 * }
 * ```
 */
@Composable
fun SupervisorPasswordDialog(
    title: String = "Supervisor Authentication Required",
    message: String = "Enter supervisor password to continue",
    onAuthenticated: (com.githow.links.data.entity.User) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Request focus on password field when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isAuthenticating) {
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = "Authentication",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null  // Clear error when user types
                    },
                    label = { Text("Supervisor Password") },
                    placeholder = { Text("Enter password") },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible }
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.Visibility
                                } else {
                                    Icons.Filled.VisibilityOff
                                },
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                }
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (password.isNotBlank() && !isAuthenticating) {
                                scope.launch {
                                    authenticateSupervisor(
                                        context = context,
                                        password = password,
                                        onSuccess = onAuthenticated,
                                        onError = { errorMsg ->
                                            error = errorMsg
                                            password = ""
                                        },
                                        setAuthenticating = { isAuthenticating = it }
                                    )
                                }
                            }
                        }
                    ),
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    enabled = !isAuthenticating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                if (isAuthenticating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        authenticateSupervisor(
                            context = context,
                            password = password,
                            onSuccess = onAuthenticated,
                            onError = { errorMsg ->
                                error = errorMsg
                                password = ""
                            },
                            setAuthenticating = { isAuthenticating = it }
                        )
                    }
                },
                enabled = password.isNotBlank() && !isAuthenticating
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAuthenticating
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Authenticate supervisor with password
 */
private suspend fun authenticateSupervisor(
    context: android.content.Context,
    password: String,
    onSuccess: (com.githow.links.data.entity.User) -> Unit,
    onError: (String) -> Unit,
    setAuthenticating: (Boolean) -> Unit
) {
    setAuthenticating(true)

    try {
        val database = LinksDatabase.getDatabase(context)
        val authService = AuthenticationService(
            userDao = database.userDao(),
            context = context
        )

        val supervisor = authService.authenticateSupervisor(password)

        if (supervisor != null) {
            // Authentication successful
            onSuccess(supervisor)
        } else {
            // Authentication failed
            onError("Invalid password. Please try again.")
        }
    } catch (e: Exception) {
        onError("Authentication error: ${e.message}")
    } finally {
        setAuthenticating(false)
    }
}

/**
 * Simplified version - just returns true/false
 */
@Composable
fun SupervisorPasswordDialogSimple(
    title: String = "Supervisor Authentication Required",
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit
) {
    SupervisorPasswordDialog(
        title = title,
        onAuthenticated = { _ ->
            onAuthenticated()
        },
        onDismiss = onDismiss
    )
}