package com.jasensic.mydrive.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.jasensic.mydrive.domain.ConnectivityMonitor
import com.jasensic.mydrive.domain.DiscoveredServer
import com.jasensic.mydrive.domain.ServerDiscovery
import com.jasensic.mydrive.domain.lanScanHosts
import com.jasensic.mydrive.domain.preferredLanHost
import com.jasensic.mydrive.domain.serverFromDiscovery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {
    private val cm get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnWifi(): Boolean = lanNetwork(context) != null

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
    private val gate = Mutex()
    private val generation = AtomicInteger(0)
    private var resolveListener: NsdManager.ResolveListener? = null
    private var serviceInfoCallback: Any? = null
    private var cached: Pair<DiscoveredServer, Long>? = null

    override suspend fun find(timeoutMs: Long): DiscoveredServer? = gate.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        cached?.let { (server, at) ->
            if (now - at < CACHE_MS) return@withLock server
        }
        val found = coroutineScope {
            val nsd = async { discoverByNsd(timeoutMs) }
            val scan = async { discoverBySubnet(timeoutMs) }
            firstNotNull(nsd, scan)
        }
        if (found != null) cached = found to android.os.SystemClock.elapsedRealtime()
        found
    }

    private suspend fun discoverByNsd(timeoutMs: Long): DiscoveredServer? {
        if (!hasDiscoveryPermission()) {
            Log.i(TAG, "mDNS skipped; nearby-devices permission is not granted")
            return null
        }
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("mydrive-mdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        return try {
            withContext(Dispatchers.Main) {
                // The Wi-Fi multicast filter is not on the instant the lock is acquired.
                delay(MULTICAST_WARMUP_MS)
                val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
                var restrictToLan = true
                var attempt = 0
                var found: DiscoveredServer? = null
                while (attempt < MAX_ATTEMPTS && android.os.SystemClock.elapsedRealtime() < deadline) {
                    attempt++
                    val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                    if (remaining < 400) break
                    val slice = minOf(remaining, ATTEMPT_SLICE_MS)
                    found = withTimeoutOrNull(slice) { discoverOnce(restrictToLan) }
                    if (found != null) break
                    // The first browse is often empty, and the network-scoped API sometimes
                    // never starts. The next pass uses the process-wide discovery call.
                    restrictToLan = false
                    delay(RETRY_GAP_MS)
                }
                found
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    /**
     * mDNS from a Docker bridge never reaches the phone. The API port is published on the
     * host, so ask each address on the Wi-Fi subnet for `/v1/status`.
     */
    private suspend fun discoverBySubnet(timeoutMs: Long): DiscoveredServer? = withContext(Dispatchers.IO) {
        val lan = localLan() ?: run {
            Log.i(TAG, "subnet scan skipped; no Wi-Fi IPv4")
            return@withContext null
        }
        val hosts = lanScanHosts(lan.ip, lan.prefix).sortedBy { host -> ipv4Distance(lan.ip, host) }
        if (hosts.isEmpty()) return@withContext null
        Log.i(TAG, "scanning ${hosts.size} hosts from ${lan.ip}/${lan.prefix} port $API_PORT")
        val permits = Semaphore(SCAN_PARALLELISM)
        coroutineScope {
            val outcome = CompletableDeferred<DiscoveredServer?>()
            val jobs = hosts.map { host ->
                launch {
                    permits.withPermit {
                        if (!isActive || outcome.isCompleted) return@launch
                        if (probeMyDrive(lan.network, host, API_PORT)) {
                            Log.i(TAG, "subnet scan found $host:$API_PORT")
                            outcome.complete(DiscoveredServer(host, API_PORT, "my-drive"))
                        }
                    }
                }
            }
            launch {
                jobs.forEach { it.join() }
                outcome.complete(null)
            }
            val server = withTimeoutOrNull(timeoutMs) { outcome.await() }
            jobs.forEach { it.cancel() }
            if (server == null) Log.i(TAG, "subnet scan finished without a my-drive server")
            server
        }
    }

    private fun localLan(): LocalLan? {
        val network = lanNetwork(context) ?: return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val link = cm.getLinkProperties(network)?.linkAddresses.orEmpty().firstOrNull { address ->
            address.address is java.net.Inet4Address &&
                preferredLanHost(listOf(address.address.hostAddress.orEmpty())) != null
        } ?: return null
        val ip = link.address.hostAddress?.substringBefore('%') ?: return null
        return LocalLan(network, ip, link.prefixLength)
    }

    private fun probeMyDrive(network: Network, host: String, port: Int): Boolean {
        val url = URL("http://$host:$port/v1/status")
        val conn = network.openConnection(url) as HttpURLConnection
        conn.connectTimeout = PROBE_TIMEOUT_MS
        conn.readTimeout = PROBE_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Connection", "close")
        return try {
            conn.connect()
            if (conn.responseCode != 200) return false
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            body.contains("\"setup_required\"")
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun hasDiscoveryPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun discoverOnce(restrictToLan: Boolean): DiscoveredServer? = suspendCancellableCoroutine { cont ->
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val ticket = generation.incrementAndGet()
        val done = AtomicBoolean(false)
        fun current() = generation.get() == ticket && cont.isActive
        fun finish(server: DiscoveredServer?) {
            if (!current()) return
            if (done.compareAndSet(false, true)) cont.resume(server)
        }
        lateinit var listener: NsdManager.DiscoveryListener
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "browse started networkScoped=$restrictToLan")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "browse failed to start error=$errorCode")
                finish(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!current()) return
                Log.i(TAG, "found ${service.serviceName} port=${service.port}")
                publish(service)?.let { server ->
                    runCatching { nsd.stopServiceDiscovery(listener) }
                    finish(server)
                    return
                }
                // resolveService fails with ALREADY_ACTIVE while a browse is still running.
                runCatching { nsd.stopServiceDiscovery(listener) }
                resolve(
                    nsd,
                    service,
                    stillCurrent = { current() },
                    onResolved = { info ->
                        finish(publish(info) ?: publish(service))
                    },
                    onFailed = { finish(publish(service)) },
                )
            }
        }
        val network = if (restrictToLan) lanNetwork(context) else null
        try {
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
        } catch (err: RuntimeException) {
            Log.w(TAG, "discoverServices failed", err)
            finish(null)
        }
        cont.invokeOnCancellation {
            generation.incrementAndGet()
            resolveListener = null
            clearServiceInfoCallback(nsd)
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(
        nsd: NsdManager,
        service: NsdServiceInfo,
        stillCurrent: () -> Boolean,
        onResolved: (NsdServiceInfo) -> Unit,
        onFailed: () -> Unit,
    ) {
        val settled = AtomicBoolean(false)
        fun succeed(info: NsdServiceInfo) {
            if (!stillCurrent() || !settled.compareAndSet(false, true)) return
            resolveListener = null
            clearServiceInfoCallback(nsd)
            onResolved(info)
        }
        fun fail() {
            if (!stillCurrent() || !settled.compareAndSet(false, true)) return
            resolveListener = null
            clearServiceInfoCallback(nsd)
            onFailed()
        }
        fun giveUp() {
            if (!stillCurrent() || settled.get()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                listenForServiceUpdates(nsd, service, stillCurrent, ::succeed, ::fail)
            } else {
                fail()
            }
        }
        fun resolveAttempt(attempt: Int) {
            if (!stillCurrent() || settled.get()) return
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed error=$errorCode attempt=$attempt")
                    if (!stillCurrent() || settled.get()) return
                    if (attempt < MAX_RESOLVE_ATTEMPTS) {
                        val delayMs = 200L * (attempt + 1)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            resolveAttempt(attempt + 1)
                        }, delayMs)
                        return
                    }
                    giveUp()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (publish(serviceInfo) != null) {
                        succeed(serviceInfo)
                    } else if (attempt < MAX_RESOLVE_ATTEMPTS) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            resolveAttempt(attempt + 1)
                        }, 200L)
                    } else {
                        giveUp()
                    }
                }
            }
            resolveListener = listener
            try {
                nsd.resolveService(service, listener)
            } catch (err: RuntimeException) {
                Log.w(TAG, "resolveService failed", err)
                if (attempt < MAX_RESOLVE_ATTEMPTS && stillCurrent()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        resolveAttempt(attempt + 1)
                    }, 200L)
                } else {
                    giveUp()
                }
            }
        }
        resolveAttempt(0)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun listenForServiceUpdates(
        nsd: NsdManager,
        service: NsdServiceInfo,
        stillCurrent: () -> Boolean,
        onResolved: (NsdServiceInfo) -> Unit,
        onFailed: () -> Unit,
    ) {
        clearServiceInfoCallback(nsd)
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.w(TAG, "service info callback failed error=$errorCode")
                serviceInfoCallback = null
                onFailed()
            }
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                if (publish(serviceInfo) != null) onResolved(serviceInfo)
            }
            override fun onServiceLost() = Unit
            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        serviceInfoCallback = callback
        runCatching {
            nsd.registerServiceInfoCallback(service, ContextCompat.getMainExecutor(context), callback)
        }.onFailure {
            Log.w(TAG, "registerServiceInfoCallback failed", it)
            serviceInfoCallback = null
            if (stillCurrent()) onFailed()
        }
    }

    private fun clearServiceInfoCallback(nsd: NsdManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        clearServiceInfoCallbackApi34(nsd)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearServiceInfoCallbackApi34(nsd: NsdManager) {
        val callback = serviceInfoCallback as? NsdManager.ServiceInfoCallback ?: return
        serviceInfoCallback = null
        runCatching { nsd.unregisterServiceInfoCallback(callback) }
    }

    private fun publish(info: NsdServiceInfo): DiscoveredServer? {
        val server = serverFromDiscovery(
            name = info.serviceName.orEmpty(),
            host = hostAddress(info),
            port = info.port,
            txtUrl = txtUrl(info),
        )
        if (server != null) Log.i(TAG, "using ${server.label}")
        return server
    }

    private fun hostAddress(info: NsdServiceInfo): String? {
        val hosts = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hosts += info.hostAddresses.mapNotNull { it.hostAddress }
        }
        @Suppress("DEPRECATION")
        info.host?.hostAddress?.let { hosts += it }
        return preferredLanHost(hosts)
    }

    private fun txtUrl(info: NsdServiceInfo): String? {
        val attrs = info.attributes ?: return null
        val raw = attrs.entries.firstOrNull { entry ->
            val key = entry.key.lowercase()
            key == "url" || key == "base"
        }?.value ?: return null
        return raw.toString(Charsets.UTF_8).trim().trimEnd('/').ifBlank { null }
    }

    private companion object {
        const val TAG = "NsdServerDiscovery"
        const val SERVICE_TYPE = "_mydrive._tcp."
        const val MAX_ATTEMPTS = 3
        const val MAX_RESOLVE_ATTEMPTS = 4
        const val ATTEMPT_SLICE_MS = 3_500L
        const val RETRY_GAP_MS = 400L
        const val MULTICAST_WARMUP_MS = 300L
        const val CACHE_MS = 30_000L
        const val API_PORT = 8080
        const val SCAN_PARALLELISM = 64
        const val PROBE_TIMEOUT_MS = 500
    }
}

private data class LocalLan(val network: Network, val ip: String, val prefix: Int)

private suspend fun firstNotNull(vararg tasks: Deferred<DiscoveredServer?>): DiscoveredServer? {
    val pending = tasks.toMutableList()
    try {
        while (pending.isNotEmpty()) {
            val (done, value) = select<Pair<Deferred<DiscoveredServer?>, DiscoveredServer?>> {
                pending.forEach { task -> task.onAwait { task to it } }
            }
            pending.remove(done)
            if (value != null) return value
        }
        return null
    } finally {
        tasks.forEach { it.cancel() }
    }
}

private fun ipv4Distance(left: String, right: String): Long {
    fun pack(host: String): Long? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return parts.fold(0L) { acc, octet ->
            val value = octet.toIntOrNull() ?: return null
            (acc shl 8) or value.toLong()
        }
    }
    val a = pack(left) ?: return Long.MAX_VALUE
    val b = pack(right) ?: return Long.MAX_VALUE
    return abs(a - b)
}

@Suppress("DEPRECATION")
private fun lanNetwork(context: Context): Network? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val matches = cm.allNetworks.filter { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@filter false
        val lan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        lan && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
    return matches.firstOrNull { network ->
        val addresses = cm.getLinkProperties(network)?.linkAddresses.orEmpty().mapNotNull { it.address.hostAddress }
        preferredLanHost(addresses) != null
    } ?: matches.firstOrNull()
}
