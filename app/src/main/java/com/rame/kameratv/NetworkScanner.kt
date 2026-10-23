package com.rame.kameratv

/*
 * Moje Kamere - network scanner.
 * Finds cameras of ANY manufacturer two ways:
 *   1) ONVIF WS-Discovery (multicast) - brand-agnostic, also yields the device's
 *      ONVIF service address so the exact RTSP URL can be fetched automatically.
 *   2) A TCP scan of common camera ports (RTSP and vendor ports) as a fallback
 *      for cameras that do not answer ONVIF discovery.
 * Copyright (c) 2026 Izudin Ramic (Rame).
 */

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object NetworkScanner {

    /** A discovered device. onvifXAddr is set when found via ONVIF discovery. */
    data class Device(
        val ip: String,
        var onvifXAddr: String? = null,
        var label: String? = null,
        val ports: MutableList<Int> = mutableListOf()
    )

    interface Callback {
        fun onProgress(done: Int, total: Int)
        fun onFound(dev: Device)
        fun onComplete(found: List<Device>)
    }

    // RTSP (554/8554), Tapo ONVIF (2020), Hikvision (8000), some cams (88), Dahua/XM (34567/37777)
    private val CAMERA_PORTS = intArrayOf(554, 8554, 2020, 8000, 88, 34567, 37777)

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun localSubnetPrefix(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        val dot = ip.lastIndexOf('.')
                        if (dot > 0) return ip.substring(0, dot + 1)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun localIp(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) return addr.hostAddress
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun scan(ctx: Context, cb: Callback) {
        cancelled = false
        val prefix = localSubnetPrefix()
        if (prefix == null) {
            cb.onComplete(emptyList())
            return
        }
        val devices = ConcurrentHashMap<String, Device>()

        // --- 1) ONVIF WS-Discovery (any manufacturer) ---
        try {
            for ((ip, info) in wsDiscover(ctx)) {
                if (ip == localIp()) continue
                val d = devices.getOrPut(ip) { Device(ip) }
                d.onvifXAddr = info.first
                d.label = info.second
                cb.onFound(d)
            }
        } catch (_: Exception) {
        }

        // --- 2) TCP port scan ---
        val pool = Executors.newFixedThreadPool(64)
        val total = 254
        val done = AtomicInteger(0)
        val latch = CountDownLatch(total)
        for (i in 1..254) {
            val ip = prefix + i
            pool.execute {
                if (!cancelled && ip != localIp()) {
                    val open = ArrayList<Int>()
                    for (p in CAMERA_PORTS) {
                        if (cancelled) break
                        try {
                            Socket().use { it.connect(InetSocketAddress(ip, p), 250) }
                            open.add(p)
                        } catch (_: Exception) {
                        }
                    }
                    if (open.isNotEmpty()) {
                        val d = devices.getOrPut(ip) { Device(ip) }
                        synchronized(d) { for (p in open) if (!d.ports.contains(p)) d.ports.add(p) }
                        cb.onFound(d)
                    }
                }
                cb.onProgress(done.incrementAndGet(), total)
                latch.countDown()
            }
        }

        Thread {
            try {
                latch.await()
            } catch (_: InterruptedException) {
            }
            pool.shutdownNow()
            val result = devices.values.sortedBy { ipKey(it.ip) }
            cb.onComplete(result)
        }.start()
    }

    /** Multicast ONVIF probe; returns ip -> (onvif service url, friendly label). */
    private fun wsDiscover(ctx: Context): Map<String, Pair<String?, String?>> {
        val result = HashMap<String, Pair<String?, String?>>()
        var lock: WifiManager.MulticastLock? = null
        var sock: MulticastSocket? = null
        try {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            lock = wifi?.createMulticastLock("kameratv-onvif")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            val probe = (
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" " +
                    "xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
                    "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
                    "xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\">" +
                    "<e:Header><w:MessageID>urn:uuid:" + UUID.randomUUID().toString() + "</w:MessageID>" +
                    "<w:To e:mustUnderstand=\"true\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>" +
                    "<w:Action e:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>" +
                    "</e:Header><e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"
                ).toByteArray(Charsets.UTF_8)

            sock = MulticastSocket()
            sock.soTimeout = 600
            val group = InetAddress.getByName("239.255.255.250")
            repeat(2) {
                try {
                    sock.send(DatagramPacket(probe, probe.size, group, 3702))
                } catch (_: Exception) {
                }
            }
            val buf = ByteArray(16384)
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline && !cancelled) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val ip = (pkt.address as? Inet4Address)?.hostAddress ?: continue
                    val body = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val xaddr = Regex("<[^>]*XAddrs>([^<]+)</[^>]*XAddrs>")
                        .find(body)?.groupValues?.get(1)?.trim()
                        ?.split(Regex("\\s+"))?.firstOrNull { it.startsWith("http") }
                    result[ip] = Pair(xaddr, parseScopeLabel(body))
                } catch (_: SocketTimeoutException) {
                    // keep looping until the deadline
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                sock?.close()
            } catch (_: Exception) {
            }
            try {
                lock?.release()
            } catch (_: Exception) {
            }
        }
        return result
    }

    /** Pull a friendly name/manufacturer/hardware out of the ONVIF scopes, if present. */
    private fun parseScopeLabel(body: String): String? {
        val parts = ArrayList<String>()
        Regex("onvif://www\\.onvif\\.org/name/([^<\\s]+)").find(body)?.let {
            parts.add(decodeScope(it.groupValues[1]))
        }
        Regex("onvif://www\\.onvif\\.org/hardware/([^<\\s]+)").find(body)?.let {
            parts.add(decodeScope(it.groupValues[1]))
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    private fun decodeScope(s: String): String =
        s.replace("%20", " ").replace("%2F", "/").trim()

    private fun ipKey(ip: String): Int = ip.substringAfterLast('.').toIntOrNull() ?: 0
}
