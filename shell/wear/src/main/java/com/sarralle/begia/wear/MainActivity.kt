package com.sarralle.begia.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The wrist end of BEGIA: a remote for the phone, not a recorder. Three
 * pages a swipe apart - Live (the reading and Mark), Signals (the picker,
 * turned with the bezel), Trial (Start and Stop) - and the phone does the
 * rest.
 */
class MainActivity : ComponentActivity() {
    private lateinit var link: WatchLink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = WatchLink(this)
        setContent { BegiaWatch(link, ::buzz) }
    }

    override fun onResume() { super.onResume(); link.start() }
    override fun onPause() { super.onPause(); link.stop() }

    /** A mark is felt, not looked at: the wrist is where the confirmation lands. */
    private fun buzz() {
        val v = (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        v.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

// The app's own palette (Slate & Teal): the background, the accent, the
// recording red. Nothing else on a screen this size.
private val Bg = Color(0xFF0E141A)
private val Panel = Color(0xFF16222C)
private val Ink = Color(0xFFEAF1F7)
private val Muted = Color(0xFF8FA1B0)
private val Accent = Color(0xFF3FB6C0)
private val Rec = Color(0xFFE5533F)
private val Mono = FontFamily.Monospace

private val BegiaColors = Colors(
    primary = Accent, primaryVariant = Accent, secondary = Accent,
    background = Bg, surface = Panel, error = Rec,
    onPrimary = Bg, onSecondary = Bg, onBackground = Ink, onSurface = Ink, onError = Ink,
)

private const val PAGE_LIVE = 0
private const val PAGE_SIGNALS = 1
private const val PAGE_TRIAL = 2
private const val PAGES = 3

@Composable
fun BegiaWatch(link: WatchLink, buzz: () -> Unit) {
    val s by link.state
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()

    MaterialTheme(colors = BegiaColors) {
        Scaffold(timeText = { TimeText() }, vignette = { Vignette(VignettePosition.TopAndBottom) }) {
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        PAGE_LIVE -> LivePage(link, s, buzz)
                        PAGE_SIGNALS -> SignalsPage(link, s) {
                            scope.launch { pager.animateScrollToPage(PAGE_LIVE) }
                        }
                        else -> TrialPage(link, s)
                    }
                }
                // three dots: where you are, a swipe from where you are not
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(PAGES) { i ->
                        Box(Modifier.size(if (i == pager.currentPage) 6.dp else 4.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) Ink else Muted.copy(alpha = 0.5f)))
                    }
                }
            }
        }
    }
}

/** What the recorder is doing, in a line - and whose it is, when the phone
 *  is showing a laptop's BEGIA rather than its own. */
@Composable
private fun StateLine(s: WatchState) {
    if (s.linked && s.up && s.source.isNotEmpty()) {
        Text("via " + s.source, color = Accent, fontFamily = Mono, fontSize = 10.sp, maxLines = 1,
             overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 2.dp))
    }
    when {
        !s.linked -> Text(stringResource(R.string.no_phone), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        !s.up -> Text(stringResource(R.string.no_recorder), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        s.recording -> {
            var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(1000) } }
            val elapsed = if (s.startedMs > 0 && s.nowMs > 0)
                (s.nowMs - s.startedMs + (tick - s.receivedAt).coerceAtLeast(0)) / 1000 else 0L
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = Rec, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("REC  " + hms(elapsed), color = Rec, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        else -> Text("BEGIA · " + stringResource(R.string.idle), color = Muted, fontSize = 12.sp)
    }
}

// --- page 1: the reading, and Mark ---------------------------------------
@Composable
private fun LivePage(link: WatchLink, s: WatchState, buzz: () -> Unit) {
    val marked by link.justMarked
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateLine(s)
        if (s.up && s.signal.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.value.ifEmpty { "—" }, color = Ink, fontFamily = Mono, fontWeight = FontWeight.Bold,
                     fontSize = if (s.value.length > 6) 26.sp else 32.sp, maxLines = 1)
                if (s.unit.isNotEmpty()) {
                    Spacer(Modifier.width(5.dp))
                    Text(s.unit, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            // the full name, as the phone shows it: the block tells the signals apart
            Text(s.signal, color = Muted, fontSize = 11.sp, maxLines = 2,
                 overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (s.up && s.recording) {
            Spacer(Modifier.height(12.dp))
            // Mark is the big one: the reason this app exists
            Button(
                onClick = { buzz(); link.mark() },
                colors = ButtonDefaults.buttonColors(backgroundColor = if (marked) Ink else Accent, contentColor = Bg),
                modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
            ) {
                Text(if (marked) stringResource(R.string.marked) else stringResource(R.string.mark),
                     fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("${s.marks} " + stringResource(R.string.marks), color = Muted, fontSize = 11.sp,
                 modifier = Modifier.padding(top = 4.dp))
        }
        if (s.error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(s.error, color = Rec, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

// --- page 2: the picker, turned with the bezel ---------------------------
// A tree of the dotted names, the way a PLC's blocks nest: a block is a row
// that opens on a tap to show what is in it, a member deeper still opens
// the same way, and a signal is a row that chooses. One branch open at a
// time at every level - `open` is the one path that is expanded, so tapping
// a sibling closes the other by replacing it.
private class TreeNode {
    val kids = LinkedHashMap<String, TreeNode>()
    var sig: Sig? = null
    var count = 0
}

private sealed class TreeRow {
    data class Folder(val path: List<String>, val name: String, val count: Int, val open: Boolean) : TreeRow()
    data class Leaf(val path: List<String>, val sig: Sig) : TreeRow()
}

private fun treeOf(sigs: List<Sig>): TreeNode {
    val root = TreeNode()
    for (sig in sigs.sortedBy { it.name }) {
        var node = root
        node.count++
        for (seg in sig.name.split('.')) {
            node = node.kids.getOrPut(seg) { TreeNode() }
            node.count++
        }
        node.sig = sig
    }
    return root
}

private fun flatten(node: TreeNode, path: List<String>, open: List<String>, out: MutableList<TreeRow>) {
    for ((name, kid) in node.kids) {
        if (kid.kids.isEmpty() && kid.sig != null) {
            out.add(TreeRow.Leaf(path, kid.sig!!))
        } else {
            val isOpen = open.size > path.size && open.subList(0, path.size) == path && open[path.size] == name
            out.add(TreeRow.Folder(path, name, kid.count, isOpen))
            if (isOpen) flatten(kid, path + name, open, out)
        }
    }
}

@Composable
private fun SignalsPage(link: WatchLink, s: WatchState, onPicked: () -> Unit) {
    val chosen by link.chosen
    val listState = rememberScalingLazyListState()
    var open by remember { mutableStateOf<List<String>>(emptyList()) }
    // the first time the list arrives, the branch holding the chosen signal is open
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(s.signals.isNotEmpty()) {
        if (!seeded && s.signals.isNotEmpty()) {
            seeded = true
            val id = chosen.ifEmpty { s.signalId }
            val name = s.signals.firstOrNull { it.id == id }?.name ?: ""
            if (name.contains('.')) open = name.split('.').dropLast(1)
        }
    }
    if (!s.up || s.signals.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (s.up) stringResource(R.string.no_signals) else stringResource(R.string.no_recorder),
                 color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }
    val rows = remember(s.signals, open) {
        ArrayList<TreeRow>().also { flatten(treeOf(s.signals), emptyList(), open, it) }
    }
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 34.dp, start = 8.dp, end = 8.dp),
        rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState),
    ) {
        item {
            Text(stringResource(R.string.pick_signal), color = Muted, fontSize = 11.sp,
                 modifier = Modifier.padding(bottom = 2.dp))
        }
        items(rows.size, key = { i ->
            when (val r = rows[i]) {
                is TreeRow.Folder -> "f:" + (r.path + r.name).joinToString(".")
                is TreeRow.Leaf -> "s:" + r.sig.id
            }
        }) { i ->
            when (val r = rows[i]) {
                is TreeRow.Folder -> Chip(
                    onClick = { open = if (r.open) r.path else r.path + r.name },
                    colors = if (r.open) ChipDefaults.secondaryChipColors(backgroundColor = Panel, contentColor = Accent)
                             else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(start = (r.path.size * 10).dp),
                    label = {
                        Text((if (r.open) "▾ " else "▸ ") + r.name, fontSize = 13.sp, maxLines = 1,
                             overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    },
                    secondaryLabel = { Text("${r.count}", fontFamily = Mono, fontSize = 11.sp) },
                )
                is TreeRow.Leaf -> {
                    val on = r.sig.id == chosen || (chosen.isEmpty() && r.sig.id == s.signalId)
                    Chip(
                        onClick = { link.choose(r.sig.id); onPicked() },
                        colors = if (on) ChipDefaults.primaryChipColors(backgroundColor = Accent, contentColor = Bg)
                                 else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(start = (r.path.size * 10).dp),
                        label = {
                            Text(r.sig.name.substringAfterLast('.'), fontSize = 13.sp, maxLines = 2,
                                 overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        },
                        secondaryLabel = {
                            Text((r.sig.value.ifEmpty { "—" }) + if (r.sig.unit.isNotEmpty()) " ${r.sig.unit}" else "",
                                 fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
                        },
                    )
                }
            }
        }
    }
}

// --- page 3: the trial -----------------------------------------------------
@Composable
private fun TrialPage(link: WatchLink, s: WatchState) {
    var confirmStop by remember { mutableStateOf(false) }
    if (!s.recording) confirmStop = false
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateLine(s)
        if (s.recording && s.trial.isNotEmpty()) {
            Text(s.trial, color = Ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                 textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Text("${s.marks} " + stringResource(R.string.marks), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        when {
            !s.up -> Unit
            confirmStop -> {
                Text(stringResource(R.string.stop_ask), color = Ink, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { confirmStop = false; link.stopTrial() },
                           colors = ButtonDefaults.buttonColors(backgroundColor = Rec, contentColor = Ink),
                           modifier = Modifier.size(52.dp)) { Text("■", fontSize = 16.sp) }
                    Button(onClick = { confirmStop = false },
                           colors = ButtonDefaults.secondaryButtonColors(),
                           modifier = Modifier.size(52.dp)) { Text("↩", fontSize = 16.sp) }
                }
            }
            s.recording -> Button(
                onClick = { confirmStop = true },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.fillMaxWidth(0.7f).height(46.dp),
            ) { Text("■ " + stringResource(R.string.stop), color = Rec, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            else -> Button(
                onClick = { link.startTrial() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Rec, contentColor = Ink),
                modifier = Modifier.fillMaxWidth(0.7f).height(46.dp),
            ) { Text("● " + stringResource(R.string.start), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        if (s.error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(s.error, color = Rec, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

private fun hms(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
