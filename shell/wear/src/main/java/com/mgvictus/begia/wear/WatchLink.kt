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

/** One ticked signal, as the picker lists it. */
data class Sig(val id: String, val name: String, val unit: String, val value: String, val bool: Boolean,
               val text: Boolean = false)   // a STRING tag: its value is words, sent on change

/** The input the I/O check saw move last (GET /api/watch "iocheck.last"). */
data class IoLast(val nodeId: String, val name: String, val address: String, val value: String,
                  val status: String, val count: Int, val atMs: Long, val member: String)

/** The I/O check on the phone: its counts and the input that moved last. */
data class IoState(val running: Boolean, val name: String, val total: Int, val ok: Int, val nok: Int,
                   val waiting: Int, val changed: Int, val skipped: Int, val last: IoLast?)

/** What the wrist knows about the recorder, as of the last reply. */
data class WatchState(
    val linked: Boolean = false,      // a phone with BEGIA is reachable over Bluetooth
    val up: Boolean = false,          // and its recorder answered
    val recording: Boolean = false,
    val trial: String = "",
    val startedMs: Long = 0L,
    val marks: Int = 0,
    val signal: String = "",          // the one shown: the watch's choice, else the first analog
    val signalId: String = "",
    val value: String = "",
    val unit: String = "",
    val signals: List<Sig> = emptyList(),
    val source: String = "",          // the laptop the phone is showing, in second-screen mode
    val error: String = "",
    val nowMs: Long = 0L,             // the phone's clock at the reply, for the elapsed time
    val receivedAt: Long = 0L,        // this watch's clock at the reply
    // No new sample of the shown signal for a while: the link to the PLC is
    // down, and the last value is not a reading any more. The phone shows a
    // dash then (desktop: readings are dashes while the link is down); so
    // does the wrist, rather than a number minutes old that looks live.
    val stale: Boolean = false,
    // "iocheck" while the phone's page shows the I/O check: the wrist follows
    val mode: String = "",
    val io: IoState? = null,
)

/**
 * The watch's end of the Data Layer. Every few seconds while the screen is
 * on it asks the phone's relay (shell, WearRelayService) for the state -
 * naming the signal it wants to see - and an action is a message the same
 * way; the reply to either is the new state. Request and reply rather than
 * a synced data item: a remote wants an answer to the thing it just did,
 * not a mirror that catches up eventually.
 */
class WatchLink private constructor(private val ctx: Context) : MessageClient.OnMessageReceivedListener {
    val state = mutableStateOf(WatchState())
    val justMarked = mutableStateOf(false)
    /** The node id the wrist chose to watch; remembered across launches. */
    val chosen = mutableStateOf(ctx.getSharedPreferences("watch", Context.MODE_PRIVATE).getString("signal", "") ?: "")

    private var scope: CoroutineScope? = null
    private var poller: Job? = null
    private var phoneNode: String? = null
    // The shown signal's sample stamp and when (on this watch's clock) it
    // last moved. The stamp is the sample's own time - on OPC UA the PLC's
    // clock - so it is only ever compared with itself, never with a clock.
    private var lastSig = ""
    private var lastAt = 0L
    private var lastMoved = 0L
    // when the poll that is still unanswered went out; 0 once answered
    @Volatile private var askedAt = 0L

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        Wearable.getMessageClient(ctx).addListener(this)
        poller = s.launch {
            while (true) {
                // One question at a time: a new one only when the last was
                // answered, or has waited long enough to be given up on - at
                // four a second a slow relay (a laptop over the WiFi, in
                // second-screen mode) would otherwise have a queue of them.
                val now = System.currentTimeMillis()
                if (askedAt != 0L && now - askedAt > REPLY_WAIT_MS && state.value.up) {
                    // no answer: the phone's recorder or the laptop behind it
                    // is gone, and what is on the wrist - "via ...", the last
                    // reading - is not true any more
                    state.value = state.value.copy(up = false, source = "", stale = true)
                }
                if (askedAt == 0L || now - askedAt > REPLY_WAIT_MS) {
                    askedAt = now
                    ask("/begia/state", chosen.value)
                }
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

    /** The I/O check's verdict on an input, from the wrist: "ok" or "nok". */
    fun ioMark(nodeId: String, verdict: String) =
        act("/begia/iomark", JSONObject().put("node_id", nodeId).put("verdict", verdict).toString())

    /** Counts the inputs seen to move since the app opened: the I/O page
     *  buzzes on each. */
    val ioMoves = androidx.compose.runtime.mutableIntStateOf(0)

    /** Show this signal on the Live page from now on. */
    fun choose(id: String) {
        Log.i(TAG, "chose '$id'")
        chosen.value = id
        ctx.getSharedPreferences("watch", Context.MODE_PRIVATE).edit().putString("signal", id).apply()
        act("/begia/state", id)
    }

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
        askedAt = 0L
        val j = try { JSONObject(String(event.data)) } catch (e: Exception) { JSONObject() }
        // The phone says which signal this answer is for. An answer to any
        // other than the one picked now - a question sent before the pick, or
        // one from a stale sender - is not shown: seen on the plant laptop,
        // the Live page flipped back to the old signal within 200 ms of a pick.
        val asked = if (j.has("asked")) j.optString("asked", "") else null
        val want = chosen.value
        if (asked != null && want.isNotEmpty() && asked != want) return
        val wasMarks = state.value.marks
        val list = ArrayList<Sig>()
        val arr = j.optJSONArray("signals")
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(Sig(o.optString("id"), o.optString("name"), o.optString("unit"),
                         o.optString("value"), o.optBoolean("bool", false), o.optBoolean("text", false)))
        }
        val sigId = j.optString("signal_id", "")
        if (sigId != state.value.signalId) Log.i(TAG, "showing '$sigId' (asked '${chosen.value}')")
        val at = j.optLong("at_ms", 0L)
        val here = System.currentTimeMillis()
        if (sigId != lastSig || at != lastAt || lastMoved == 0L) {
            lastSig = sigId; lastAt = at; lastMoved = here
        }
        val next = WatchState(
            // a text is sent on change only: one that has not changed is not
            // stale, so its stamp standing still says nothing
            stale = !j.optBoolean("text", false) && (at == 0L || here - lastMoved > STALE_MS),
            linked = true,
            up = j.optBoolean("up", false),
            recording = j.optBoolean("recording", false),
            trial = j.optString("trial", ""),
            startedMs = j.optLong("started_ms", 0L),
            marks = j.optInt("marks", 0),
            signal = j.optString("signal", ""),
            signalId = j.optString("signal_id", ""),
            value = j.optString("value", ""),
            unit = j.optString("unit", ""),
            signals = list,
            source = j.optString("source", ""),
            error = j.optString("error", ""),
            nowMs = j.optLong("now_ms", 0L),
            receivedAt = System.currentTimeMillis(),
            mode = j.optString("mode", ""),
            io = parseIo(j.optJSONObject("iocheck")),
        )
        val wasIoAt = state.value.io?.last?.atMs
        state.value = next
        if (next.recording && next.marks > wasMarks && wasMarks >= 0) {
            justMarked.value = true
            scope?.launch { delay(1500); justMarked.value = false }
        }
        // an input moved: the I/O page buzzes, as the phone does
        val ioAt = next.io?.last?.atMs
        if (ioAt != null && wasIoAt != null && ioAt != wasIoAt) ioMoves.intValue += 1
    }

    private fun parseIo(o: JSONObject?): IoState? {
        if (o == null) return null
        val sm = o.optJSONObject("summary") ?: JSONObject()
        val l = o.optJSONObject("last")
        return IoState(
            running = o.optBoolean("running", false), name = o.optString("name", ""),
            total = sm.optInt("total", 0), ok = sm.optInt("ok", 0), nok = sm.optInt("nok", 0),
            waiting = sm.optInt("waiting", 0), changed = sm.optInt("changed", 0), skipped = sm.optInt("skipped", 0),
            last = l?.let {
                IoLast(it.optString("node_id"), it.optString("name"), it.optString("address"),
                       it.optString("value"), it.optString("status"), it.optInt("count", 0),
                       it.optLong("at_ms", 0L), it.optString("member"))
            },
        )
    }

    companion object {
        /** One link per watch app: every screen instance Android keeps shares
         *  the same choice and the same poller. (A link per activity let a
         *  second instance keep asking for the signal it was created with.) */
        @Volatile private var one: WatchLink? = null
        fun of(ctx: Context): WatchLink =
            one ?: synchronized(this) { one ?: WatchLink(ctx.applicationContext).also { one = it } }

        const val TAG = "begia.watch"
        const val CAPABILITY = "begia_phone"
        // Four times a second while the watch app is open: every 2 s the
        // reading visibly trailed the phone ("the input comes after 1-2 s").
        // A message over Bluetooth and a loopback GET are each a few tens of
        // milliseconds; the app polls only while its screen is on.
        const val POLL_MS = 250L
        // a question with no answer after this is given up and asked again
        const val REPLY_WAIT_MS = 2000L
        // no new sample for this long: the phone dashes a reading after 3 s
        // without one, and the wrist sees the stamp stop within a poll
        const val STALE_MS = 3000L
    }
}
