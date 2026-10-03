package com.sarralle.begia

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The phone's end of the watch companion. The watch cannot reach the
 * recorder (it listens on the phone's loopback), so its messages arrive
 * here over the Data Layer and become calls to the recorder's API on
 * 127.0.0.1:8080; every one of them is answered with the state the watch
 * shows (GET /api/watch), so the wrist sees what it just did.
 *
 * Paths: /begia/state (ask; data = the node id the watch chose to show,
 * or empty), /begia/mark (data = the mark's text), /begia/start,
 * /begia/stop. The reply goes back as /begia/state/reply. Declared for the
 * watch to find through the begia_phone capability (res/values/wear.xml).
 */
class WearRelayService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val data = String(event.data)
        val error: String? = when (event.path) {
            "/begia/state" -> { lastChosen = data; null }
            "/begia/mark" -> post("/api/trial/mark",
                JSONObject().put("text", data.ifBlank { "mark (watch)" }).toString())
            "/begia/start" -> post("/api/trial/start",
                JSONObject().put("name", data.ifBlank { "watch " + stamp() }).put("pretrigger_s", 0).toString())
            "/begia/stop" -> post("/api/trial/stop", "{}")
            // the I/O check's OK / not OK from the wrist: data is
            // {"node_id", "verdict"} for POST /api/iocheck/mark
            "/begia/iomark" -> try {
                val j = JSONObject(data)
                post("/api/iocheck/mark", JSONObject().put("node_id", j.optString("node_id"))
                    .put("verdict", j.optString("verdict", "ok")).toString())
            } catch (e: Exception) { "not a mark" }
            else -> return
        }
        val reply = watchState(lastChosen)
        // which signal this answer is for: the watch shows an answer only for
        // the signal picked now, so a stale question cannot overwrite a pick
        reply.put("asked", lastChosen)
        // the page's module: the watch follows the phone into the I/O check
        reply.put("mode", PageState.watchMode())
        if (!error.isNullOrEmpty()) reply.put("error", error)
        // the wrist sees what the phone sees: in second-screen mode, whose
        Source.remote(this)?.let { reply.put("source", Source.host(it)) }
        val bytes = fit(reply).toString().toByteArray()
        // One line when the wrist asks for a different signal: what it asked
        // for, what the recorder shows, how big the answer is and from where.
        // (Four questions a second otherwise say nothing.)
        if (event.path == "/begia/state" && data != loggedChosen) {
            loggedChosen = data
            Log.i(Recorder.TAG, "watch: asked for '${data.ifEmpty { "(default)" }}', showing '" +
                  reply.optString("signal_id", "") + "', ${bytes.size} bytes from ${Source.base(this)}")
        }
        try {
            // sendMessage fails later, on its Task - a try around the call
            // alone never saw a reply the Data Layer refused
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, "/begia/state/reply", bytes)
                .addOnFailureListener { e -> Log.w(Recorder.TAG, "watch reply refused (${bytes.size} bytes): $e") }
        } catch (e: Exception) {
            Log.w(Recorder.TAG, "watch reply failed: $e")
        }
    }

    /**
     * The answer the watch needs, and small enough to be carried. The
     * recorder lists every ticked signal with its reading, for the wrist's
     * picker; a laptop with hundreds of them (second-screen mode) made an
     * answer the Data Layer will not carry in one message, and a refused
     * reply leaves the watch showing what it had. Each row keeps what the
     * picker uses (id, name, unit, value, digital or not); past MAX_REPLY the
     * list keeps the shown signal and as many others as fit, and says so.
     */
    private fun fit(reply: JSONObject): JSONObject {
        val rows = reply.optJSONArray("signals") ?: return reply
        val lean = org.json.JSONArray()
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            lean.put(JSONObject().put("id", r.optString("id")).put("name", r.optString("name"))
                .put("unit", r.optString("unit")).put("value", r.optString("value"))
                .put("bool", r.optBoolean("bool", false)).put("text", r.optBoolean("text", false)))
        }
        reply.put("signals", lean)
        if (reply.toString().length <= MAX_REPLY) return reply
        val shownId = reply.optString("signal_id", "")
        val kept = org.json.JSONArray()
        var size = reply.toString().length - lean.toString().length
        for (i in 0 until lean.length()) {
            val r = lean.getJSONObject(i)
            val cost = r.toString().length + 1
            if (r.optString("id") == shownId || size + cost <= MAX_REPLY - 2000) { kept.put(r); size += cost }
        }
        reply.put("signals", kept).put("signals_cut", lean.length() - kept.length())
        return reply
    }

    /** GET /api/watch for the chosen signal, or {"up": false} when the
     *  recorder is not answering. */
    private fun watchState(chosen: String): JSONObject = try {
        val q = if (chosen.isBlank()) "" else "?signal=" + URLEncoder.encode(chosen, "UTF-8")
        val url = "${Source.base(this)}/api/watch$q"
        val c = Net.connect(url, 1500, 2500, Source.cookieFor(this, url))
        if (c.responseCode == 401) {
            // a laptop's API asks a stranger to sign in (desktop ee07d6d):
            // the phone's page has the door, the wrist can only say so
            JSONObject().put("up", false).put("error", "sign in to the laptop on the phone first")
        } else {
            c.inputStream.bufferedReader().use { JSONObject(it.readText()) }.put("up", true)
        }
    } catch (e: Exception) {
        JSONObject().put("up", false)
    }

    /** POST to the recorder; null on success, else what it said. */
    private fun post(path: String, json: String): String? = try {
        val url = Source.base(this) + path
        val c = Net.connect(url, 2000, 6000, Source.cookieFor(this, url))
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(json.toByteArray()) }
        val code = c.responseCode
        Log.i(Recorder.TAG, "watch: POST $path -> $code")
        if (code < 300) null else {
            val body = try { c.errorStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (e: Exception) { "" }
            val detail = try { JSONObject(body).optString("detail", "") } catch (e: Exception) { "" }
            detail.ifEmpty { "the recorder refused ($code)" }
        }
    } catch (e: Exception) {
        Log.w(Recorder.TAG, "watch: POST $path failed: $e")
        "the recorder is not answering"
    }

    private fun stamp(): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())

    companion object {
        /** The signal the watch last asked to see, so the reply to a mark or
         *  a stop shows the same one rather than the default. */
        @Volatile private var lastChosen: String = ""
        @Volatile private var loggedChosen: String? = null
        /** Well under what one Data Layer message carries. */
        private const val MAX_REPLY = 60_000
    }
}
