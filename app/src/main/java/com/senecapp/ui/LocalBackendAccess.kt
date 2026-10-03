package com.senecapp.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.senecapp.BuildConfig

private const val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
internal fun LocalBackendAccess(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val host = Uri.parse(BuildConfig.API_BASE_URL).host.orEmpty()
    val localHost = host == "localhost" || host == "::1" || host.startsWith("127.") ||
        host.startsWith("10.") || host.startsWith("192.168.") ||
        (host.startsWith("172.") && host.split('.').getOrNull(1)?.toIntOrNull() in 16..31)
    val required = Build.VERSION.SDK_INT >= 37 && localHost
    fun allowed() = !required || context.checkSelfPermission(localNetworkPermission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    // Recheck after returning from Settings or after a revoked permission.
    DisposableEffect(context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = allowed()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    if (granted) {
        content()
    } else {
        DemoView("Local network access", showSampleLabel = false) {
            ViewText("Allow nearby devices permission to load organizations and events from the local server.")
            TextButton(onClick = { launcher.launch(localNetworkPermission) }) {
                Text("Allow local network", color = viewAccent)
            }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")))
            }) { Text("Open app permissions", color = viewAccent) }
        }
    }
}
