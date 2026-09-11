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
            else -> return
        }
        val reply = watchState(lastChosen)
        if (!error.isNullOrEmpty()) reply.put("error", error)
        // the wrist sees what the phone sees: in second-screen mode, whose
        Source.remote(this)?.let { reply.put("source", Source.host(it)) }
        try {
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, "/begia/state/reply", reply.toString().toByteArray())
        } catch (e: Exception) {
            Log.w(Recorder.TAG, "watch reply failed: $e")
        }
    }

    /** GET /api/watch for the chosen signal, or {"up": false} when the
     *  recorder is not answering. */
    private fun watchState(chosen: String): JSONObject = try {
        val q = if (chosen.isBlank()) "" else "?signal=" + URLEncoder.encode(chosen, "UTF-8")
        val c = Net.connect("${Source.base(this)}/api/watch$q", 1500, 2500)
        c.inputStream.bufferedReader().use { JSONObject(it.readText()) }.put("up", true)
    } catch (e: Exception) {
        JSONObject().put("up", false)
    }

    /** POST to the recorder; null on success, else what it said. */
    private fun post(path: String, json: String): String? = try {
        val c = Net.connect(Source.base(this) + path, 2000, 6000)
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
    }
}
