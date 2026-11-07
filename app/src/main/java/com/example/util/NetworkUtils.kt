package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.regex.Pattern

/**
 * Utility functions for network interface inspection and IPv4 validation.
 */
object NetworkUtils {

    private val IPV4_PATTERN: Pattern = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    /**
     * Validates whether a given string is a valid IPv4 address in quad-dotted notation.
     */
    fun isValidIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return false
        return IPV4_PATTERN.matcher(trimmed).matches()
    }

    /**
     * Converts what the user typed into a plain ASCII IPv4 string.
     * Arabic-Indic digits become 0-9, and comma / Arabic comma / Arabic decimal separator
     * become '.', so an Arabic keyboard can still produce a valid address. Spaces are removed.
     */
    fun normalizeIpInput(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                '٫', '،', ',', '。', '．' -> sb.append('.')
                ' ' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Returns the Wi-Fi network this phone is connected to, even if that Wi-Fi has no internet.
     * Binding the UDP socket to it stops Android from routing LAN packets over mobile data.
     * Returns null on the phone that hosts the hotspot (it has no Wi-Fi client network).
     */
    fun findWifiNetwork(context: Context): Network? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                null
            } else {
                @Suppress("DEPRECATION")
                val networks = cm.allNetworks
                networks.firstOrNull { network ->
                    val caps = cm.getNetworkCapabilities(network)
                    caps != null &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates whether a port is within the safe unprivileged user port range (1024..65535).
     */
    fun isValidPort(port: Int): Boolean {
        return port in 1024..65535
    }

    /**
     * Inspects active network interfaces and extracts valid IPv4 addresses,
     * prioritizing Wi-Fi and Wi-Fi Hotspot / Tethering interfaces (e.g., wlan0, ap0, swlan, rndis).
     *
     * Returns a list of Pair(InterfaceName, IPv4Address).
     */
    fun getLocalIpv4Addresses(): List<Pair<String, String>> {
        val addresses = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                // Must be up and not a loopback interface
                if (!intf.isUp || intf.isLoopback) continue

                val inetAddresses = Collections.list(intf.inetAddresses)
                for (addr in inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        // Skip local link dummy address 127.* or 0.*
                        if (host.startsWith("127.") || host == "0.0.0.0") continue
                        addresses.add(Pair(intf.name, host))
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback empty list on unexpected network access error
        }

        // Sort priority: Hotspot (ap), Wi-Fi (wlan), Tethering (rndis, swlan), Ethernet (eth), others
        return addresses.sortedWith(
            compareBy<Pair<String, String>> { (name, _) ->
                when {
                    name.startsWith("ap", ignoreCase = true) -> 0
                    name.startsWith("wlan", ignoreCase = true) -> 1
                    name.startsWith("swlan", ignoreCase = true) -> 2
                    name.startsWith("rndis", ignoreCase = true) -> 3
                    name.startsWith("eth", ignoreCase = true) -> 4
                    else -> 5
                }
            }
        )
    }

    /**
     * Returns the primary detected IPv4 address for display, or null if offline.
     */
    fun getPreferredLocalIpv4(): String? {
        val list = getLocalIpv4Addresses()
        return list.firstOrNull()?.second
    }
}
