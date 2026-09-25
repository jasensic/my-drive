package com.jasensic.mydrive.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.jasensic.mydrive.domain.ConnectivityMonitor
import com.jasensic.mydrive.domain.DiscoveredServer
import com.jasensic.mydrive.domain.ServerDiscovery
import com.jasensic.mydrive.domain.serverFromDiscovery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {
    private val cm get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnWifi(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    override suspend fun awaitWifi() = suspendCancellableCoroutine { cont ->
        if (isOnWifi()) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.unregisterNetworkCallback(this)
                if (cont.isActive) cont.resume(Unit)
            }
        }
        cm.registerNetworkCallback(request, cb)
        cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
}

@Singleton
class NsdServerDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServerDiscovery {
    override suspend fun find(timeoutMs: Long): DiscoveredServer? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("mydrive-mdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        return try {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs) {
                    discoverOnce()
                }
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    private suspend fun discoverOnce(): DiscoveredServer? = suspendCancellableCoroutine { cont ->
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val done = AtomicBoolean(false)
        fun finish(server: DiscoveredServer?) {
            if (done.compareAndSet(false, true) && cont.isActive) cont.resume(server)
        }
        lateinit var listener: NsdManager.DiscoveryListener
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                val immediate = serverFromDiscovery(
                    name = service.serviceName.orEmpty(),
                    host = hostAddress(service),
                    port = service.port,
                    txtUrl = txtUrl(service),
                )
                if (immediate?.advertisedUrl != null) {
                    runCatching { nsd.stopServiceDiscovery(listener) }
                    finish(immediate)
                    return
                }
                resolve(nsd, service, onResolved = { info ->
                    val server = serverFromDiscovery(
                        name = info.serviceName.orEmpty().ifBlank { service.serviceName.orEmpty() },
                        host = hostAddress(info) ?: hostAddress(service),
                        port = info.port.takeIf { it > 0 } ?: service.port,
                        txtUrl = txtUrl(info) ?: txtUrl(service),
                    )
                    if (server != null) {
                        runCatching { nsd.stopServiceDiscovery(listener) }
                        finish(server)
                    }
                }, onFailed = {
                    val fallback = serverFromDiscovery(
                        name = service.serviceName.orEmpty(),
                        host = hostAddress(service),
                        port = service.port,
                        txtUrl = txtUrl(service),
                    )
                    if (fallback != null) {
                        runCatching { nsd.stopServiceDiscovery(listener) }
                        finish(fallback)
                    }
                })
            }
        }
        val network = wifiNetwork()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && network != null) {
            nsd.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                network,
                ContextCompat.getMainExecutor(context),
                listener,
            )
        } else {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        cont.invokeOnCancellation {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private fun wifiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val networks = cm.allNetworks
        return networks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(
        nsd: NsdManager,
        service: NsdServiceInfo,
        onResolved: (NsdServiceInfo) -> Unit,
        onFailed: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = onFailed()
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                    onResolved(serviceInfo)
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            nsd.registerServiceInfoCallback(service, ContextCompat.getMainExecutor(context), callback)
            return
        }
        val retried = AtomicBoolean(false)
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && retried.compareAndSet(false, true)) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        nsd.resolveService(service, this)
                    }, 250)
                    return
                }
                onFailed()
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) = onResolved(serviceInfo)
        }
        nsd.resolveService(service, listener)
    }

    private fun hostAddress(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val addresses = info.hostAddresses
            val preferred = addresses.firstOrNull { it is java.net.Inet4Address } ?: addresses.firstOrNull()
            preferred?.hostAddress?.let { return it }
        }
        @Suppress("DEPRECATION")
        return info.host?.hostAddress
    }

    private fun txtUrl(info: NsdServiceInfo): String? {
        val attrs = info.attributes ?: return null
        val raw = attrs["url"] ?: attrs["base"] ?: return null
        return raw.toString(Charsets.UTF_8).trim().trimEnd('/').ifBlank { null }
    }

    private companion object {
        const val SERVICE_TYPE = "_mydrive._tcp."
    }
}
