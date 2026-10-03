package com.senecapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun AuthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = viewAccent, onPrimary = viewBackground,
        background = viewBackground, onBackground = viewForeground, surface = viewCard,
        onSurface = viewForeground, onSurfaceVariant = Color(0xFFBAC3D8), error = Color(0xFFFFB4AB)), content = content)
}

@Composable
internal fun SignInScreen(state: SessionUiState, onSignIn: (String, String) -> Unit,
    onResetPassword: (String) -> Unit, onCreateAccount: () -> Unit, onClearMessage: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var recovering by rememberSaveable { mutableStateOf(false) }
    fun leaveRecovery() { recovering = false; onClearMessage() }
    BackHandler(enabled = recovering) { if (!state.busy) leaveRecovery() }
    AuthTheme {
        Box(Modifier.imePadding()) {
            DemoView(if (recovering) "Reset password" else "Sign in", showSampleLabel = false) {
                ViewText(if (recovering) "Enter your university email to request a password reset link."
                    else "Find your community at Uniandes.")
                if (!state.configured) ViewText("Sign-in is unavailable. Firebase setup is required for this build.")
                OutlinedTextField(value = email, onValueChange = { email = it }, singleLine = true,
                    label = { Text("University email") }, placeholder = { Text("name@uniandes.edu.co") },
                    enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth())
                if (!recovering) {
                    OutlinedTextField(value = password, onValueChange = { password = it }, singleLine = true,
                        label = { Text("Password") }, enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } },
                        modifier = Modifier.fillMaxWidth())
                }
                Button(enabled = state.configured && !state.busy, modifier = Modifier.fillMaxWidth(),
                    onClick = { if (recovering) onResetPassword(email) else onSignIn(email, password) }) {
                    Text(if (state.busy) "Please wait…" else if (recovering) "Send reset email" else "Sign in")
                }
                state.message?.let { ViewText(it, color = viewAccent) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontFamily = viewNunito) }
                if (recovering) {
                    TextButton(onClick = ::leaveRecovery, enabled = !state.busy) { Text("Back to sign in") }
                } else {
                    TextButton(onClick = { password = ""; recovering = true; onClearMessage() }, enabled = !state.busy) { Text("Forgot password?") }
                    OutlinedButton(onClick = onCreateAccount, enabled = state.configured && !state.busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Create account") }
                }
            }
        }
    }
}
