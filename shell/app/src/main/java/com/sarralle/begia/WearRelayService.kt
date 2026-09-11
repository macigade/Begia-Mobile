package com.sarralle.begia

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
 * Paths: /begia/state (ask), /begia/mark (data = the mark's text),
 * /begia/start, /begia/stop. The reply goes back as /begia/state/reply.
 * Declared for the watch to find through the begia_phone capability
 * (res/values/wear.xml).
 */
class WearRelayService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val data = String(event.data)
        val error: String? = when (event.path) {
            "/begia/state" -> null
            "/begia/mark" -> post("/api/trial/mark",
                JSONObject().put("text", data.ifBlank { "mark (watch)" }).toString())
            "/begia/start" -> post("/api/trial/start",
                JSONObject().put("name", data.ifBlank { "watch " + stamp() }).put("pretrigger_s", 0).toString())
            "/begia/stop" -> post("/api/trial/stop", "{}")
            else -> return
        }
        val reply = watchState()
        if (!error.isNullOrEmpty()) reply.put("error", error)
        try {
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, "/begia/state/reply", reply.toString().toByteArray())
        } catch (e: Exception) {
            Log.w(Recorder.TAG, "watch reply failed: $e")
        }
    }

    /** GET /api/watch, or {"up": false} when the recorder is not answering. */
    private fun watchState(): JSONObject = try {
        val c = URL("${Recorder.BASE_URL}/api/watch").openConnection() as HttpURLConnection
        c.connectTimeout = 1500
        c.readTimeout = 2500
        c.inputStream.bufferedReader().use { JSONObject(it.readText()) }.put("up", true)
    } catch (e: Exception) {
        JSONObject().put("up", false)
    }

    /** POST to the recorder; null on success, else what it said. */
    private fun post(path: String, json: String): String? = try {
        val c = URL(Recorder.BASE_URL + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 2000
        c.readTimeout = 6000
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
}
