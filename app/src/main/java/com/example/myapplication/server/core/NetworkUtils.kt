package com.example.myapplication.server.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteOrder

/**
 * Port of backend/core/serverInfo.js's getLanIpAddress(). Prefers active Wi-Fi / Ethernet
 * addresses and filters out loopback, virtual (docker/vbox/dummy/tun), and link-local IPs.
 */
object NetworkUtils {

    private const val TAG = "NetworkUtils"

    fun getLanIpAddress(context: Context): String {
        // 1. Try WifiManager IP (most reliable on Android TV / phones with Wi-Fi)
        getWifiIpAddress(context)?.let {
            Log.d(TAG, "getLanIpAddress: Using WifiManager IP -> $it")
            return it
        }

        // 2. Try ConnectivityManager active network IPv4
        getActiveNetworkIpv4(context)?.let {
            Log.d(TAG, "getLanIpAddress: Using ConnectivityManager IP -> $it")
            return it
        }

        // 3. Fallback: enumerate hardware network interfaces prioritizing wlan/eth
        getFirstNonLoopbackIpv4()?.let {
            Log.d(TAG, "getLanIpAddress: Using Interface enumeration IP -> $it")
            return it
        }

        Log.w(TAG, "getLanIpAddress: Fallback to loopback 127.0.0.1")
        return "127.0.0.1"
    }

    private fun getWifiIpAddress(context: Context): String? {
        return runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val ipInt = wm.connectionInfo?.ipAddress ?: return null
            if (ipInt == 0) return null
            val byteOrder = ByteOrder.nativeOrder()
            val bytes = if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                byteArrayOf(
                    (ipInt and 0xff).toByte(),
                    (ipInt shr 8 and 0xff).toByte(),
                    (ipInt shr 16 and 0xff).toByte(),
                    (ipInt shr 24 and 0xff).toByte()
                )
            } else {
                byteArrayOf(
                    (ipInt shr 24 and 0xff).toByte(),
                    (ipInt shr 16 and 0xff).toByte(),
                    (ipInt shr 8 and 0xff).toByte(),
                    (ipInt and 0xff).toByte()
                )
            }
            val addr = Inet4Address.getByAddress(bytes) as? Inet4Address ?: return null
            if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                addr.hostAddress
            } else null
        }.getOrNull()
    }

    private fun getActiveNetworkIpv4(context: Context): String? {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val network: Network = cm.activeNetwork ?: return null
            val linkProperties: LinkProperties = cm.getLinkProperties(network) ?: return null

            linkProperties.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private fun getFirstNonLoopbackIpv4(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            // Prioritize physical Wi-Fi (wlan, ap) and Ethernet (eth, en) interfaces
            val sortedIfaces = interfaces.sortedWith(Comparator { a, b ->
                fun rank(name: String): Int {
                    val n = name.lowercase()
                    return when {
                        n.startsWith("wlan") || n.startsWith("eth") || n.startsWith("ap") -> 0
                        n.startsWith("en") -> 1
                        else -> 2
                    }
                }
                rank(a.name).compareTo(rank(b.name))
            })

            for (iface in sortedIfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.contains("dummy") || name.contains("tun") || name.contains("tap") ||
                    name.contains("docker") || name.contains("vbox") || name.contains("p2p")
                ) continue

                for (address in iface.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        val host = address.hostAddress
                        if (host != null && host != "127.0.0.1") {
                            return host
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }
}

