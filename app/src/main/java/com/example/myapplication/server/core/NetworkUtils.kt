package com.example.myapplication.server.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Port of backend/core/serverInfo.js's getLanIpAddress(). Prefers the active network's link
 * addresses (works for both Wi-Fi and Ethernet on a TV box) and falls back to enumerating
 * every interface, mirroring Node's os.networkInterfaces() behavior.
 */
object NetworkUtils {

    fun getLanIpAddress(context: Context): String {
        getActiveNetworkIpv4(context)?.let { return it }
        getFirstNonLoopbackIpv4()?.let { return it }
        return "127.0.0.1"
    }

    private fun getActiveNetworkIpv4(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network: Network = cm.activeNetwork ?: return null
        val linkProperties: LinkProperties = cm.getLinkProperties(network) ?: return null

        return linkProperties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    private fun getFirstNonLoopbackIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (address in iface.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress
                }
            }
        }
        return null
    }
}
