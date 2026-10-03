package com.senecapp.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RegistrationScreen(state: RegistrationUiState, onCreate: (String, String, String) -> Unit,
    onResend: () -> Unit, onCheck: () -> Unit, onAnotherAccount: () -> Unit, onClose: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are kept only in memory, never saved across recreation or to disk.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.resendAt) {
        now = SystemClock.elapsedRealtime()
        while (now < state.resendAt) { delay(1_000); now = SystemClock.elapsedRealtime() }
    }
    LaunchedEffect(state.email) {
        if (state.email != null) { password = ""; confirmation = ""; showPassword = false }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = viewAccent, onPrimary = viewBackground,
        background = viewBackground, onBackground = viewForeground, surface = viewCard,
        onSurface = viewForeground, onSurfaceVariant = viewMuted)) {
        DemoView(if (state.email == null) "Create account" else "Verify your email", showSampleLabel = false) {
            if (!state.configured) ViewText("Registration is unavailable in this build. Firebase setup is required.", color = viewMuted)
            if (state.email == null) {
                ViewText("Use your university email to create a SENECApp account.", color = viewMuted)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("University email") },
                    placeholder = { Text("name@uniandes.edu.co") }, singleLine = true,
                    enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth())
                val transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") },
                    singleLine = true, enabled = !state.busy, visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = confirmation, onValueChange = { confirmation = it },
                    label = { Text("Confirm password") }, singleLine = true, enabled = !state.busy,
                    visualTransformation = transformation, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                ViewText("At least 6 characters. Your project's password policy may require more.", color = viewMuted)
                Button(enabled = state.configured && !state.busy,
                    onClick = { onCreate(email, password, confirmation) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Creating account…" else "Create account")
                }
            } else {
                ViewCard {
                    ViewText(state.email, color = viewAccent)
                    if (state.verified) {
                        ViewText("Email verified", heading = true)
                        ViewText("Your account is ready for sign-in.", color = viewMuted)
                    } else {
                        ViewText("Check your inbox", heading = true)
                        ViewText("Open the verification link, then return here and check your email status. You can also open the link on your computer.")
                        Button(onClick = onCheck, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.busy) "Please wait…" else "I've verified my email")
                        }
                        val seconds = ((state.resendAt - now).coerceAtLeast(0) + 999) / 1_000
                        TextButton(onClick = onResend, enabled = !state.busy && seconds == 0L) {
                            Text(if (seconds == 0L) "Resend email" else "Resend in ${seconds}s")
                        }
                    }
                }
                if (!state.verified) TextButton(onClick = onAnotherAccount, enabled = !state.busy) { Text("Use another email") }
            }
            state.message?.let { ViewText(it, color = viewAccent) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontFamily = viewNunito) }
            TextButton(onClick = onClose) { Text("Back to profile") }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RegistrationPreview() {
    RegistrationScreen(RegistrationUiState(configured = true), { _, _, _ -> }, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun VerificationPreview() {
    RegistrationScreen(RegistrationUiState(configured = true, email = "student@uniandes.edu.co"), { _, _, _ -> }, {}, {}, {}, {})
}
