package com.rame.kameratv

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object NetworkScanner {

    interface Callback {
        fun onProgress(done: Int, total: Int)
        fun onFound(ip: String)
        fun onComplete(found: List<String>)
    }

    @Volatile
    private var cancelled = false

    fun localSubnetPrefix(): String? {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in ifaces) {
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

    fun cancel() {
        cancelled = true
    }

    fun scan(port: Int = 554, timeoutMs: Int = 350, cb: Callback) {
        cancelled = false
        val prefix = localSubnetPrefix()
        if (prefix == null) {
            cb.onComplete(emptyList())
            return
        }
        val pool = Executors.newFixedThreadPool(48)
        val found = Collections.synchronizedList(ArrayList<String>())
        val done = AtomicInteger(0)
        val total = 254
        val latch = CountDownLatch(total)

        for (i in 1..254) {
            val ip = prefix + i
            pool.execute {
                if (!cancelled) {
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(ip, port), timeoutMs)
                            found.add(ip)
                            cb.onFound(ip)
                        }
                    } catch (_: Exception) {
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
            val result = ArrayList(found)
            result.sortBy { ipKey(it) }
            cb.onComplete(result)
        }.start()
    }

    private fun ipKey(ip: String): Int {
        val last = ip.substringAfterLast('.').toIntOrNull() ?: 0
        return last
    }
}
