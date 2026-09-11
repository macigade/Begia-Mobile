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

/** What the recorder is doing, in a line. */
@Composable
private fun StateLine(s: WatchState) {
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
            Text(s.signal.substringAfterLast('.'), color = Muted, fontSize = 11.sp, maxLines = 1,
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
@Composable
private fun SignalsPage(link: WatchLink, s: WatchState, onPicked: () -> Unit) {
    val chosen by link.chosen
    val listState = rememberScalingLazyListState()
    if (!s.up || s.signals.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (s.up) stringResource(R.string.no_signals) else stringResource(R.string.no_recorder),
                 color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 34.dp, start = 10.dp, end = 10.dp),
        rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState),
    ) {
        item {
            Text(stringResource(R.string.pick_signal), color = Muted, fontSize = 11.sp,
                 modifier = Modifier.padding(bottom = 4.dp))
        }
        items(s.signals, key = { it.id }) { sig ->
            val on = sig.id == chosen || (chosen.isEmpty() && sig.id == s.signalId)
            Chip(
                onClick = { link.choose(sig.id); onPicked() },
                colors = if (on) ChipDefaults.primaryChipColors(backgroundColor = Accent, contentColor = Bg)
                         else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(sig.name.substringAfterLast('.'), fontSize = 13.sp, maxLines = 1,
                         overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                },
                secondaryLabel = {
                    Text((sig.value.ifEmpty { "—" }) + if (sig.unit.isNotEmpty()) " ${sig.unit}" else "",
                         fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
                },
            )
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
