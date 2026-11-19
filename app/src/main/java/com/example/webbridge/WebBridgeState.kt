package com.example.webbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

data class WebBridgeStatus(
    val isRunning: Boolean = false,
    val hostAddress: String? = null,
    val port: Int = NephoWebBridgeServer.DEFAULT_PORT,
    val pairingPin: String = "",
    val connectedClients: Int = 0,
    val wifiAvailable: Boolean = true,
    val lastError: String? = null
) {
    val localUrl: String?
        get() = hostAddress?.let { "http://$it:$port" }

    val formattedPin: String
        get() = if (pairingPin.length == 6) {
            "${pairingPin.substring(0, 3)} ${pairingPin.substring(3)}"
        } else {
            pairingPin
        }
}

object WebBridgeController {
    private val _status = MutableStateFlow(WebBridgeStatus())
    val status: StateFlow<WebBridgeStatus> = _status.asStateFlow()

    internal fun updateStatus(transform: (WebBridgeStatus) -> WebBridgeStatus) {
        _status.value = transform(_status.value)
    }

    internal fun setStatus(newStatus: WebBridgeStatus) {
        _status.value = newStatus
    }

    fun isOnWifiOrEthernet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Resolves the device's best local LAN IPv4 address, prioritizing Wi-Fi/Ethernet interfaces
     * (wlan*, eth*, en*) with RFC1918 site-local addresses.
     */
    fun resolveLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { iface ->
                    runCatching { iface.isUp && !iface.isLoopback && !iface.isVirtual }.getOrDefault(false)
                }

            val candidates = interfaces.flatMap { iface ->
                val name = iface.name.lowercase()
                val priority = when {
                    name.startsWith("wlan") || name.startsWith("wifi") -> 0
                    name.startsWith("eth") || name.startsWith("en") -> 1
                    name.startsWith("ap") || name.startsWith("swlan") -> 2
                    else -> 3
                }
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { addr ->
                        val siteLocalRank = if (addr.isSiteLocalAddress) 0 else 1
                        Triple(priority, siteLocalRank, addr.hostAddress)
                    }
            }

            candidates
                .sortedWith(compareBy<Triple<Int, Int, String?>> { it.first }.thenBy { it.second })
                .firstOrNull { !it.third.isNullOrBlank() }
                ?.third
        }.getOrNull()
    }
}
