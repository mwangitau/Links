package com.githow.links.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

// ── Constants ─────────────────────────────────────────────────────────────────
private const val PREFS_NAME = "links_pin"
private const val KEY_PIN_HASH = "pin_hash"
private const val PIN_LENGTH = 4

// ── PIN hashing ───────────────────────────────────────────────────────────────
private fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(pin.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private fun isPinSet(prefs: SharedPreferences) = prefs.contains(KEY_PIN_HASH)

private fun verifyPin(prefs: SharedPreferences, pin: String): Boolean {
    val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
    return stored == hashPin(pin)
}

private fun savePin(prefs: SharedPreferences, pin: String) {
    prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
}

// ── Main Screen ───────────────────────────────────────────────────────────────
@Composable
fun PinScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val pinSet = remember { isPinSet(prefs) }

    if (pinSet) {
        PinEntryScreen(prefs = prefs, onAuthenticated = onAuthenticated)
    } else {
        PinSetupScreen(prefs = prefs, onAuthenticated = onAuthenticated)
    }
}

// ── Setup Screen (first launch) ───────────────────────────────────────────────
@Composable
private fun PinSetupScreen(
    prefs: SharedPreferences,
    onAuthenticated: () -> Unit
) {
    var step by remember { mutableStateOf(0) } // 0 = create, 1 = confirm
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }

    val title = if (step == 0) "Create PIN" else "Confirm PIN"
    val subtitle = if (step == 0) "Set a 4-digit PIN to secure the app" else "Enter your PIN again to confirm"

    PinLayout(
        title = title,
        subtitle = subtitle,
        pin = currentPin,
        errorMessage = errorMessage,
        shake = shake,
        onShakeEnd = { shake = false },
        onDigit = { digit ->
            if (currentPin.length < PIN_LENGTH) {
                currentPin += digit
                errorMessage = ""
                if (currentPin.length == PIN_LENGTH) {
                    if (step == 0) {
                        firstPin = currentPin
                        currentPin = ""
                        step = 1
                    } else {
                        if (currentPin == firstPin) {
                            savePin(prefs, currentPin)
                            onAuthenticated()
                        } else {
                            shake = true
                            errorMessage = "PINs do not match. Try again."
                            currentPin = ""
                            firstPin = ""
                            step = 0
                        }
                    }
                }
            }
        },
        onBackspace = {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                errorMessage = ""
            }
        }
    )
}

// ── Entry Screen (subsequent launches) ────────────────────────────────────────
@Composable
private fun PinEntryScreen(
    prefs: SharedPreferences,
    onAuthenticated: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var shake by remember { mutableStateOf(false) }

    PinLayout(
        title = "Welcome Back",
        subtitle = "Enter your PIN to continue",
        pin = currentPin,
        errorMessage = errorMessage,
        shake = shake,
        onShakeEnd = { shake = false },
        onDigit = { digit ->
            if (currentPin.length < PIN_LENGTH) {
                currentPin += digit
                errorMessage = ""
                if (currentPin.length == PIN_LENGTH) {
                    if (verifyPin(prefs, currentPin)) {
                        onAuthenticated()
                    } else {
                        attempts++
                        shake = true
                        errorMessage = if (attempts >= 3)
                            "Incorrect PIN ($attempts attempts)"
                        else
                            "Incorrect PIN. Try again."
                        currentPin = ""
                    }
                }
            }
        },
        onBackspace = {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                errorMessage = ""
            }
        }
    )
}

// ── Shared Layout ─────────────────────────────────────────────────────────────
@Composable
private fun PinLayout(
    title: String,
    subtitle: String,
    pin: String,
    errorMessage: String,
    shake: Boolean,
    onShakeEnd: () -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val shakeOffset by animateFloatAsState(
        targetValue = if (shake) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        finishedListener = { onShakeEnd() },
        label = "shake"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0A),
                        Color(0xFF1A1A2E),
                        Color(0xFF0A0A0A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shell logo placeholder - station name
                Text(
                    text = "LINKS",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD700),
                    letterSpacing = 8.sp
                )
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
            }

            // ── PIN Dots ──────────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = (shakeOffset * 8).dp)
            ) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < pin.length
                    val isActive = index == pin.length

                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    filled -> Color(0xFFFFD700)
                                    isActive -> Color(0x44FFD700)
                                    else -> Color(0x22FFFFFF)
                                }
                            )
                            .border(
                                width = if (isActive) 1.dp else 0.dp,
                                color = if (isActive) Color(0xFFFFD700) else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }

            // ── Error Message ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = errorMessage.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF4444),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // ── Keypad ────────────────────────────────────────────────────────
            PinKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace
            )
        }
    }
}

// ── Keypad ────────────────────────────────────────────────────────────────────
@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    when {
                        key.isEmpty() -> Spacer(modifier = Modifier.size(72.dp))
                        key == "⌫" -> PinKeyButton(
                            content = {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = Color(0xFFAAAAAA),
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            onClick = onBackspace,
                            isSpecial = true
                        )
                        else -> PinKeyButton(
                            content = {
                                Text(
                                    text = key,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            },
                            onClick = { onDigit(key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    content: @Composable () -> Unit,
    onClick: () -> Unit,
    isSpecial: Boolean = false
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    Surface(
        onClick = {
            pressed = true
            onClick()
        },
        modifier = Modifier
            .size(72.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        color = if (isSpecial) Color(0xFF1A1A2E) else Color(0xFF1E1E1E),
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}
