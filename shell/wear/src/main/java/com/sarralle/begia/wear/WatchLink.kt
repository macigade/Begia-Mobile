package com.sarralle.begia.wear

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What the wrist knows about the recorder, as of the last reply. */
data class WatchState(
    val linked: Boolean = false,      // a phone with BEGIA is reachable over Bluetooth
    val up: Boolean = false,          // and its recorder answered
    val recording: Boolean = false,
    val trial: String = "",
    val startedMs: Long = 0L,
    val marks: Int = 0,
    val signal: String = "",
    val value: String = "",
    val unit: String = "",
    val error: String = "",
    val nowMs: Long = 0L,             // the phone's clock at the reply, for the elapsed time
    val receivedAt: Long = 0L,        // this watch's clock at the reply
)

/**
 * The watch's end of the Data Layer. Every few seconds while the screen is
 * on it asks the phone's relay (shell, WearRelayService) for the state, and
 * an action is a message the same way; the reply to either is the new state.
 * Request and reply rather than a synced data item: a remote wants an answer
 * to the thing it just did, not a mirror that catches up eventually.
 */
class WatchLink(private val ctx: Context) : MessageClient.OnMessageReceivedListener {
    val state = mutableStateOf(WatchState())
    val justMarked = mutableStateOf(false)

    private var scope: CoroutineScope? = null
    private var poller: Job? = null
    private var phoneNode: String? = null

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        Wearable.getMessageClient(ctx).addListener(this)
        poller = s.launch {
            while (true) {
                ask("/begia/state")
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        Wearable.getMessageClient(ctx).removeListener(this)
        poller?.cancel()
        scope?.cancel()
        scope = null
        poller = null
    }

    fun mark() = act("/begia/mark", "mark (watch)")
    fun startTrial() = act("/begia/start", "")
    fun stopTrial() = act("/begia/stop", "")

    private fun act(path: String, body: String) {
        scope?.launch { ask(path, body) }
    }

    /** Find the phone (the shell declares the begia_phone capability) and send. */
    private fun ask(path: String, body: String = "") {
        try {
            val node = phoneNode ?: findPhone()
            if (node == null) {
                state.value = state.value.copy(linked = false, up = false, error = "")
                return
            }
            phoneNode = node
            Tasks.await(Wearable.getMessageClient(ctx).sendMessage(node, path, body.toByteArray()),
                        4, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "$path: $e")
            phoneNode = null
            state.value = state.value.copy(linked = false, up = false)
        }
    }

    private fun findPhone(): String? = try {
        val info = Tasks.await(
            Wearable.getCapabilityClient(ctx).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE),
            4, TimeUnit.SECONDS)
        info.nodes.firstOrNull { it.isNearby }?.id ?: info.nodes.firstOrNull()?.id
    } catch (e: Exception) {
        Log.w(TAG, "capability: $e")
        null
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/begia/state/reply") return
        val j = try { JSONObject(String(event.data)) } catch (e: Exception) { JSONObject() }
        val wasMarks = state.value.marks
        val next = WatchState(
            linked = true,
            up = j.optBoolean("up", false),
            recording = j.optBoolean("recording", false),
            trial = j.optString("trial", ""),
            startedMs = j.optLong("started_ms", 0L),
            marks = j.optInt("marks", 0),
            signal = j.optString("signal", ""),
            value = j.optString("value", ""),
            unit = j.optString("unit", ""),
            error = j.optString("error", ""),
            nowMs = j.optLong("now_ms", 0L),
            receivedAt = System.currentTimeMillis(),
        )
        state.value = next
        if (next.recording && next.marks > wasMarks && wasMarks >= 0) {
            justMarked.value = true
            scope?.launch { delay(1500); justMarked.value = false }
        }
    }

    companion object {
        const val TAG = "begia.watch"
        const val CAPABILITY = "begia_phone"
        const val POLL_MS = 2000L
    }
}
