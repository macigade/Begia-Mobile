package com.sarralle.begia.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.delay

/**
 * The wrist end of BEGIA: a remote for the phone, not a recorder. One
 * screen - what the recorder is doing, one signal's reading, and the
 * buttons that matter with a phone in a pocket: Mark first of all.
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

@Composable
fun BegiaWatch(link: WatchLink, buzz: () -> Unit) {
    val s by link.state
    val marked by link.justMarked
    var confirmStop by remember { mutableStateOf(false) }
    // the elapsed time ticks locally between replies, from the phone's clock
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(1000) } }
    if (!s.recording) confirmStop = false

    MaterialTheme(colors = BegiaColors) {
        Scaffold(timeText = { TimeText() }, vignette = { Vignette(VignettePosition.TopAndBottom) }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // --- the state line ---------------------------------------
                when {
                    !s.linked -> Text(stringOf(R.string.no_phone), color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    !s.up -> Text(stringOf(R.string.no_recorder), color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    s.recording -> {
                        val elapsed = if (s.startedMs > 0 && s.nowMs > 0)
                            (s.nowMs - s.startedMs + (tick - s.receivedAt).coerceAtLeast(0)) / 1000 else 0L
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("●", color = Rec, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("REC  " + hms(elapsed), color = Rec, fontFamily = Mono,
                                 fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (s.trial.isNotEmpty()) {
                            Text(s.trial, color = Muted, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                        }
                        Text("${s.marks} " + stringOf(R.string.marks), color = Muted, fontSize = 11.sp)
                    }
                    else -> Text("BEGIA · " + stringOf(R.string.idle), color = Muted, fontSize = 13.sp)
                }

                // --- one reading ------------------------------------------
                if (s.up && s.signal.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(s.value.ifEmpty { "—" }, color = Ink, fontFamily = Mono,
                             fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        if (s.unit.isNotEmpty()) {
                            Spacer(Modifier.width(5.dp))
                            Text(s.unit, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                    Text(s.signal.substringAfterLast('.'), color = Muted, fontSize = 11.sp,
                         maxLines = 1, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(12.dp))

                // --- the buttons ------------------------------------------
                when {
                    !s.up -> Unit
                    confirmStop -> {
                        Text(stringOf(R.string.stop_ask), color = Ink, fontSize = 13.sp)
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
                    s.recording -> {
                        // Mark is the big one: the reason this app exists
                        Button(
                            onClick = { buzz(); link.mark() },
                            colors = ButtonDefaults.buttonColors(backgroundColor = if (marked) Ink else Accent, contentColor = Bg),
                            modifier = Modifier.fillMaxWidth(0.82f).height(52.dp),
                        ) {
                            Text(if (marked) stringOf(R.string.marked) else stringOf(R.string.mark),
                                 fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { confirmStop = true },
                               colors = ButtonDefaults.secondaryButtonColors(),
                               modifier = Modifier.size(40.dp)) { Text("■", color = Rec, fontSize = 13.sp) }
                    }
                    else -> Button(
                        onClick = { link.startTrial() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Rec, contentColor = Ink),
                        modifier = Modifier.fillMaxWidth(0.7f).height(46.dp),
                    ) { Text("● " + stringOf(R.string.start), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                }

                if (s.error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(s.error, color = Rec, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun stringOf(id: Int): String = androidx.compose.ui.res.stringResource(id)

private fun hms(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
