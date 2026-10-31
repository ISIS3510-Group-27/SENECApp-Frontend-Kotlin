package com.senecapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// Emits true/false when the default network gains or loses validated internet.
// The callback only lives while someone collects: awaitClose unregisters it, so a closed screen leaks nothing.
internal fun networkStatus(context: Context): Flow<Boolean> = callbackFlow {
    val applicationContext = context.applicationContext
    val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        trySend(true)
        awaitClose { }
        return@callbackFlow
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        override fun onLost(network: Network) { trySend(false) }
    }
    trySend(isOnline(applicationContext))
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
