package com.jasensic.mydrive.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.jasensic.mydrive.domain.SyncScheduler
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.shouldStartWifiSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wakes when Wi-Fi appears or disappears, including if the process was not running.
 * Joining a new Wi-Fi network starts a sync; staying on the same one is left to the hourly job.
 */
class WifiSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entry = EntryPointAccessors.fromApplication(app, WifiSyncEntryPoint::class.java)
                val loggedIn = entry.state().session() != null
                val networkId = wifiNetworkId(app, intent)
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val previous = prefs.getString(KEY_NETWORK, null)
                if (networkId == null) {
                    if (previous != null) prefs.edit().remove(KEY_NETWORK).apply()
                } else if (networkId != previous) {
                    prefs.edit().putString(KEY_NETWORK, networkId).apply()
                }
                if (shouldStartWifiSync(previous, networkId, loggedIn)) {
                    entry.scheduler().enqueueBackground()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val PREFS = "mydrive_wifi_sync"
        const val KEY_NETWORK = "network_id"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WifiSyncEntryPoint {
    fun scheduler(): SyncScheduler
    fun state(): SyncStateRepository
}

private fun wifiNetworkId(context: Context, intent: Intent?): String? {
    val network = intent?.wifiNetworkExtra() ?: return null
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val caps = cm.getNetworkCapabilities(network) ?: return null
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        network.networkHandle.toString()
    } else {
        network.toString()
    }
}

private fun Intent.wifiNetworkExtra(): Network? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)
    }
