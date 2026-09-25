package com.kiroland.gallery.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.kiroland.gallery.shared.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

data class FoundTv(val name: String, val host: String, val port: Int)

/** Finds KingFrame TVs on the Wi-Fi via NSD (mDNS). */
object TvDiscovery {

    @Suppress("DEPRECATION")
    fun discover(context: Context): Flow<List<FoundTv>> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("tvgallery-discovery").apply { setReferenceCounted(false); acquire() }
        val found = linkedMapOf<String, FoundTv>()
        // NsdManager only resolves one service at a time.
        val resolveLock = Mutex()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                launch {
                    val list = resolveLock.withLock {
                        val resolved = resolve(nsd, info) ?: return@launch
                        val host = resolved.host?.hostAddress ?: return@launch
                        found[resolved.serviceName] = FoundTv(resolved.serviceName, host, resolved.port)
                        found.values.toList()
                    }
                    trySend(list)
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                launch {
                    resolveLock.withLock { found.remove(info.serviceName) }
                    trySend(found.values.toList())
                }
            }
        }
        trySend(emptyList())
        nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            lock.release()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(serviceInfo)
                }
            })
        }
}
