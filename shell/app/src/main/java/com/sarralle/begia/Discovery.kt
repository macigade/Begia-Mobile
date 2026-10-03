package com.sarralle.begia

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Finding the laptop without typing its address (desktop app/discover.py).
 *
 * The phone broadcasts the six bytes "BEGIA?" to UDP port 4858 - to the
 * broadcast address of each of its own IPv4 networks and to 255.255.255.255
 * - and every BEGIA that hears it answers this socket with one JSON
 * datagram: {"begia": 1, "name", "build", "https": [...], "port",
 * "licensed", "auth": "set" | "unset"}. Replies are collected for about a
 * second; with none, the question is asked once more.
 *
 * Each answer gets two fields of the phone's own: "from", the address it
 * came from, and "best", the https address to use - the one on this
 * phone's subnet when there is one (a laptop lists every address it has:
 * the plant cable, a VM's NAT, a second WiFi), else the one it answered
 * from, else the first.
 */
object Discovery {
    const val PORT = 4858
    private val PROBE = "BEGIA?".toByteArray()

    /** This phone's IPv4 addresses with their prefix lengths. */
    @Suppress("DEPRECATION")
    fun ownV4(ctx: Context): List<Pair<Inet4Address, Int>> {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val out = ArrayList<Pair<Inet4Address, Int>>()
        for (n in cm.allNetworks) {
            val lp = cm.getLinkProperties(n) ?: continue
            for (la in lp.linkAddresses) {
                val a = la.address
                if (a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress) out.add(a to la.prefixLength)
            }
        }
        return out
    }

    private fun toInt(a: Inet4Address): Int =
        a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    private fun mask(prefix: Int): Int = if (prefix <= 0) 0 else (-1 shl (32 - prefix.coerceAtMost(32)))

    fun broadcastOf(a: Inet4Address, prefix: Int): InetAddress {
        val b = toInt(a) or mask(prefix).inv()
        return InetAddress.getByAddress(byteArrayOf((b ushr 24).toByte(), (b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte()))
    }

    fun sameSubnet(host: String, own: List<Pair<Inet4Address, Int>>): Boolean {
        val h = try { InetAddress.getByName(host) } catch (e: Exception) { return false }
        if (h !is Inet4Address) return false
        return own.any { (a, p) -> (toInt(a) and mask(p)) == (toInt(h) and mask(p)) }
    }

    /** The https address to offer for one answer (see the class note). */
    fun best(answer: JSONObject, from: String, own: List<Pair<Inet4Address, Int>>): String {
        val list = answer.optJSONArray("https") ?: JSONArray()
        val urls = (0 until list.length()).map { list.optString(it) }.filter { it.startsWith("http") }
        fun host(u: String) = try { URL(u).host } catch (e: Exception) { "" }
        return urls.firstOrNull { sameSubnet(host(it), own) }
            ?: urls.firstOrNull { host(it) == from }
            ?: urls.firstOrNull()
            ?: "https://$from:${answer.optInt("port", 8443)}"
    }

    /** Ask the network; every BEGIA that answered, as JSON objects. */
    fun find(ctx: Context, waitMs: Long = 1000L): JSONArray {
        val own = ownV4(ctx)
        val targets = LinkedHashSet<InetAddress>()
        for ((a, p) in own) targets.add(broadcastOf(a, p))
        targets.add(InetAddress.getByName("255.255.255.255"))
        val found = LinkedHashMap<String, JSONObject>()
        // Some phones hand a unicast answer to a broadcasting socket only
        // while a multicast lock is held; it costs nothing for a second.
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = try { wifi?.createMulticastLock("begia-discovery")?.apply { setReferenceCounted(false); acquire() } }
                   catch (e: Exception) { null }
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 200
                val buf = ByteArray(4096)
                for (attempt in 0 until 2) {
                    for (t in targets) {
                        try { s.send(DatagramPacket(PROBE, PROBE.size, t, PORT)) }
                        catch (e: Exception) { Log.w(Recorder.TAG, "discovery: send to $t failed: $e") }
                    }
                    val until = System.currentTimeMillis() + waitMs
                    while (System.currentTimeMillis() < until) {
                        val pkt = DatagramPacket(buf, buf.size)
                        try { s.receive(pkt) } catch (e: SocketTimeoutException) { continue }
                        val from = pkt.address?.hostAddress ?: continue
                        // this phone's own BEGIA hears the broadcast too and
                        // answers it: a phone is never a laptop to offer
                        if (pkt.address.isLoopbackAddress || own.any { it.first.hostAddress == from }) continue
                        val j = try { JSONObject(String(pkt.data, 0, pkt.length, Charsets.UTF_8)) } catch (e: Exception) { continue }
                        if (j.optInt("begia", 0) < 1) continue
                        j.put("from", from).put("best", best(j, from, own))
                        found[j.optString("name", "") + "|" + j.optString("best")] = j
                    }
                    if (found.isNotEmpty()) break
                }
            }
        } finally {
            try { lock?.release() } catch (e: Exception) { /* released already */ }
        }
        Log.i(Recorder.TAG, "discovery: ${found.size} BEGIA answered " +
              found.values.joinToString { it.optString("name") + " " + it.optString("best") })
        return JSONArray(found.values.toList())
    }
}
