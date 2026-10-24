package com.sarralle.begia

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

/**
 * A laptop's answer to "BEGIA?" as desktop main sends it since 74eaf0d
 * (app/routes/hosting.py, _discovery_answer): two more fields, "id" (this
 * copy, 16 hex) and "plc" (which PLC it is on, 12-hex fingerprints). Each
 * test takes the answer through the steps Discovery.find takes a datagram:
 * JSONObject of the text, begia >= 1, then "from" and "best" of its own. A
 * field the shell does not know is carried along, never a reason to drop
 * the answer.
 */
class DiscoveryAnswerTest {
    private val own = listOf(InetAddress.getByName("192.168.6.104") as Inet4Address to 24)

    private fun answer(https: List<String>, plc: List<String>) =
        """{"begia":1,"device":"pc","name":"LAPTOP","build":"v0.9-426-g05008bd",""" +
        """"https":[${https.joinToString(",") { "\"$it\"" }}],"port":8443,"licensed":true,""" +
        """"auth":"set","id":"3f9a1c0b7d2e4a65","plc":[${plc.joinToString(",") { "\"$it\"" }}],""" +
        """"later":{"what":"a field no shell knows yet","n":[1,2,3]}}"""

    /** find()'s steps for one datagram, from the text on */
    private fun take(text: String, from: String): JSONObject? {
        val j = try { JSONObject(text) } catch (e: Exception) { return null }
        if (j.optInt("begia", 0) < 1) return null
        return j.put("from", from).put("best", Discovery.best(j, from, own))
    }

    @Test
    fun anAnswerWithIdAndPlcIsTaken() {
        val text = answer(listOf("https://192.168.70.135:8443", "https://192.168.6.108:8443"),
                          listOf("a1b2c3d4e5f6", "0f1e2d3c4b5a"))
        val j = take(text, "192.168.6.108")!!
        assertEquals("https://192.168.6.108:8443", j.getString("best"))    // this phone's subnet
        assertEquals("3f9a1c0b7d2e4a65", j.getString("id"))
        assertEquals(2, j.getJSONArray("plc").length())
        assertTrue(j.has("later"))
    }

    @Test
    fun aPlcNotConnectedIsAnEmptyList() {
        val j = take(answer(listOf("https://192.168.6.108:8443"), emptyList()), "192.168.6.108")!!
        assertEquals(0, j.getJSONArray("plc").length())
    }

    @Test
    fun whatIsNotAnAnswerIsStillDropped() {
        assertEquals(null, take("BEGIA?", "192.168.6.108"))
        assertEquals(null, take("""{"begia":0,"id":"x","plc":[]}""", "192.168.6.108"))
    }

    @Test
    fun evenABusyLaptopsAnswerFitsTheDatagramFindReads() {
        // find() reads an answer into 4096 bytes and a longer one would not
        // parse: a laptop with twelve addresses, each one also a PLC key
        // (the simulator on its loopback), stays far inside that
        val hosts = (1..12).map { "10.$it.200.17" }
        val text = answer(hosts.map { "https://$it:8443" }, hosts.indices.map { "%012x".format(it * 0x1234567L) })
        assertTrue(text.toByteArray(Charsets.UTF_8).size < 1500)
        assertTrue(take(text, "10.1.200.17") != null)
    }
}
