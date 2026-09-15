package com.jasensic.mydrive.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.jasensic.mydrive.domain.ConnectivityMonitor
import com.jasensic.mydrive.domain.DiscoveredServer
import com.jasensic.mydrive.domain.ServerDiscovery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {
    private val cm get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnWifi(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
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
        cont.invokeOnCancellation { cm.unregisterNetworkCallback(cb) }
    }
}

@Singleton
class NsdServerDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServerDiscovery {
    override suspend fun find(timeoutMs: Long): DiscoveredServer? = suspendCancellableCoroutine { cont ->
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("mydrive")) return
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress ?: return
                        if (cont.isActive) {
                            nsd.stopServiceDiscovery(this@listener)
                            cont.resume(DiscoveredServer(host, serviceInfo.port, serviceInfo.serviceName))
                        }
                    }
                })
            }
        }
        nsd.discoverServices("_mydrive._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        cont.invokeOnCancellation {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
