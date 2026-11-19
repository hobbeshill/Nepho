package com.example.webbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PulseRepository

class NephoWebBridgeService : Service() {

    private var server: NephoWebBridgeServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBridgeAndSelf()
                return START_NOT_STICKY
            }
            ACTION_ROTATE_PIN -> {
                val activeServer = server
                if (activeServer != null && activeServer.isRunning) {
                    val newPin = activeServer.rotatePin()
                    WebBridgeController.updateStatus { current ->
                        current.copy(pairingPin = newPin, connectedClients = 0)
                    }
                    postOrUpdateForegroundNotification()
                }
                return START_STICKY
            }
            else -> {
                startBridgeIfNeeded()
                return START_STICKY
            }
        }
    }

    private fun startBridgeIfNeeded() {
        val currentServer = server
        if (currentServer != null && currentServer.isRunning) {
            val wifiAvailable = WebBridgeController.isOnWifiOrEthernet(this)
            val host = WebBridgeController.resolveLocalIpv4Address()
            WebBridgeController.updateStatus { current ->
                current.copy(
                    hostAddress = host,
                    wifiAvailable = wifiAvailable
                )
            }
            postOrUpdateForegroundNotification()
            return
        }

        ensureNotificationChannel()
        val repository = PulseRepository.getInstance(applicationContext)
        val cachedHtml by lazy { loadCompanionHtml() }

        val newServer = NephoWebBridgeServer(
            repository = repository,
            htmlProvider = { cachedHtml },
            requestedPort = NephoWebBridgeServer.DEFAULT_PORT,
            onClientCountChanged = { count ->
                WebBridgeController.updateStatus { it.copy(connectedClients = count) }
            }
        )

        try {
            val port = newServer.start()
            server = newServer
            val wifiAvailable = WebBridgeController.isOnWifiOrEthernet(this)
            val host = WebBridgeController.resolveLocalIpv4Address()
            WebBridgeController.setStatus(
                WebBridgeStatus(
                    isRunning = true,
                    hostAddress = host,
                    port = port,
                    pairingPin = newServer.pairingPin,
                    connectedClients = 0,
                    wifiAvailable = wifiAvailable,
                    lastError = null
                )
            )
            postOrUpdateForegroundNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Nepho Web Bridge server", e)
            WebBridgeController.setStatus(
                WebBridgeStatus(
                    isRunning = false,
                    wifiAvailable = WebBridgeController.isOnWifiOrEthernet(this),
                    lastError = e.message ?: "Could not start local web server."
                )
            )
            stopSelf()
        }
    }

    private fun postOrUpdateForegroundNotification() {
        val notification = buildForegroundNotification(WebBridgeController.status.value)
        try {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        } catch (e: Exception) {
            Log.w(TAG, "Foreground promotion fallback", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(status: WebBridgeStatus): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            REQ_OPEN_APP,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NephoWebBridgeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQ_STOP_BRIDGE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = status.localUrl?.let { url ->
            getString(R.string.web_bridge_notif_body, url, status.formattedPin)
        } ?: getString(R.string.web_bridge_no_wifi_warning)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.web_bridge_notif_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .addAction(
                0,
                getString(R.string.web_bridge_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.web_bridge_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.web_bridge_channel_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun loadCompanionHtml(): String {
        return runCatching {
            assets.open(ASSET_COMPANION_HTML).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse {
            "<!doctype html><html><body><h1>Nepho Web Companion</h1><p>Companion asset missing.</p></body></html>"
        }
    }

    private fun stopBridgeAndSelf() {
        server?.stop()
        server = null
        WebBridgeController.setStatus(
            WebBridgeStatus(
                isRunning = false,
                wifiAvailable = WebBridgeController.isOnWifiOrEthernet(this)
            )
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        WebBridgeController.setStatus(
            WebBridgeStatus(
                isRunning = false,
                wifiAvailable = WebBridgeController.isOnWifiOrEthernet(this)
            )
        )
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NephoWebBridgeService"
        private const val CHANNEL_ID = "nepho_web_bridge"
        private const val NOTIFICATION_ID = 44_901
        private const val REQ_OPEN_APP = 8401
        private const val REQ_STOP_BRIDGE = 8402
        private const val ASSET_COMPANION_HTML = "web_companion.html"

        const val ACTION_START = "com.example.webbridge.ACTION_START"
        const val ACTION_STOP = "com.example.webbridge.ACTION_STOP"
        const val ACTION_ROTATE_PIN = "com.example.webbridge.ACTION_ROTATE_PIN"

        fun startBridge(context: Context) {
            val intent = Intent(context, NephoWebBridgeService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopBridge(context: Context) {
            val intent = Intent(context, NephoWebBridgeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun rotatePin(context: Context) {
            val intent = Intent(context, NephoWebBridgeService::class.java).apply {
                action = ACTION_ROTATE_PIN
            }
            context.startService(intent)
        }
    }
}
