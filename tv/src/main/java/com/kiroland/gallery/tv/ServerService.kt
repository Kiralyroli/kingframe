package com.kiroland.gallery.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import com.kiroland.gallery.shared.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address

/**
 * Keeps the photo receiving server running in the background and announces it
 * on the local network, so the phone can send photos any time the TV is on.
 */
class ServerService : Service() {
    private var server: GalleryServer? = null
    private var nsd: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val registration = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) = Unit
        override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
            Log.w(TAG, "NSD registration failed: $code")
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
        override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        val app = tvApp
        val started = listOf(Protocol.DEFAULT_PORT, 0).firstNotNullOfOrNull { port ->
            runCatching {
                GalleryServer(port, app) { screenSize(this) }.apply { start(SOCKET_TIMEOUT_MS, false) }
            }.onFailure { Log.w(TAG, "Port $port unavailable", it) }.getOrNull()
        }
        server = started
        if (started == null) {
            _state.value = ServerState(running = false, port = 0)
            return
        }
        _state.value = ServerState(running = true, port = started.listeningPort)

        multicastLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("tvgallery").apply { setReferenceCounted(false); acquire() }
        nsd = getSystemService(NsdManager::class.java).also { manager ->
            val info = NsdServiceInfo().apply {
                serviceName = app.prefs.tvName
                serviceType = Protocol.SERVICE_TYPE
                port = started.listeningPort
            }
            runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { nsd?.unregisterService(registration) }
        multicastLock?.release()
        server?.stop()
        _state.value = ServerState(running = false, port = 0)
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Képfogadás", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("KingFrame")
            .setContentText("Képek fogadása a telefonról")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    data class ServerState(val running: Boolean, val port: Int)

    companion object {
        private const val TAG = "ServerService"
        private const val CHANNEL = "server"
        private const val SOCKET_TIMEOUT_MS = 30_000

        private val _state = MutableStateFlow(ServerState(false, 0))
        val state: StateFlow<ServerState> = _state

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, ServerService::class.java))
            }.onFailure { Log.w(TAG, "Could not start server", it) }
        }

        /** Landscape size the slideshow is rendered at. */
        fun screenSize(context: Context): Pair<Int, Int> {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            val w = maxOf(metrics.widthPixels, metrics.heightPixels)
            val h = minOf(metrics.widthPixels, metrics.heightPixels)
            return w to h
        }

        fun localIpv4(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address }?.hostAddress
        }
    }
}
