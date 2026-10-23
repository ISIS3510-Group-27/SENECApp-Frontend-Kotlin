package com.senecapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AuthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = viewPrimary, onPrimary = Color.White,
            secondary = viewAccent, onSecondary = viewBackground,
            secondaryContainer = viewAccent, onSecondaryContainer = viewBackground,
            background = viewBackground, onBackground = viewForeground, surface = viewCard,
            onSurface = viewForeground, surfaceVariant = viewSecondary, onSurfaceVariant = viewMuted,
            outline = Color.White.copy(alpha = .08f), error = Color(0xFFFFB4AB)),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = viewNunito, fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = TextStyle(fontFamily = viewNunito, fontSize = 13.sp, lineHeight = 19.sp),
            bodySmall = TextStyle(fontFamily = viewNunito, fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontFamily = viewNunito, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            labelMedium = TextStyle(fontFamily = viewNunito, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontFamily = viewBricolage, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
            titleMedium = TextStyle(fontFamily = viewBricolage, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}

@Composable
internal fun SignInScreen(state: SessionUiState, onSignIn: (String, String) -> Unit,
    onResetPassword: (String) -> Unit, onCreateAccount: () -> Unit, onClearMessage: () -> Unit,
    onMicrosoft: () -> Unit = {}) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var recovering by rememberSaveable { mutableStateOf(false) }
    fun leaveRecovery() { recovering = false; onClearMessage() }
    BackHandler(enabled = recovering) { if (!state.busy) leaveRecovery() }
    AuthTheme {
        AuthPage(if (recovering) "Reset password" else "Welcome back",
            if (recovering) "Enter your university email to request a password reset link."
            else "Sign in with your university account to find your people on campus.") {
            if (!state.configured) InfoNotice("Sign-in is unavailable. Firebase setup is required for this build.", error = true)
            BrandField(value = email, onValueChange = { email = it }, label = "University email",
                placeholder = "you@uniandes.edu.co", enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            if (!recovering) {
                BrandField(value = password, onValueChange = { password = it }, label = "Password",
                    enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show", color = viewMuted)
                    } })
            }
            state.message?.let { InfoNotice(it) }
            state.error?.let { InfoNotice(it, error = true) }
            BrandButton(if (recovering) "Send reset email" else "Sign in", enabled = state.configured,
                busy = state.busy, onClick = { if (recovering) onResetPassword(email) else onSignIn(email, password) })
            if (recovering) {
                TextButton(onClick = ::leaveRecovery, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Back to sign in", color = viewAccent)
                }
            } else {
                BrandButton("Continue with Microsoft", onMicrosoft, enabled = state.configured && !state.busy, secondary = true)
                TextButton(onClick = { password = ""; recovering = true; onClearMessage() },
                    enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Forgot password?", color = viewAccent)
                }
                TextButton(onClick = onCreateAccount, enabled = state.configured && !state.busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("New to SENECApp? Create an account", color = viewAccent)
                }
            }
        }
    }
}
