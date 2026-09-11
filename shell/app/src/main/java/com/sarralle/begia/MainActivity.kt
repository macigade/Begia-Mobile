package com.sarralle.begia

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * A full-screen WebView on the recorder served at 127.0.0.1:8080, and the
 * boot screen that stands in for it until /api/state answers. The recorder
 * itself lives in RecorderService, in its own process, and survives this
 * screen being closed.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var boot: View
    private lateinit var bootStatus: TextView
    private lateinit var bootDetail: TextView
    private lateinit var bootNote: TextView
    private lateinit var bootProgress: ProgressBar
    private lateinit var bootAction: Button

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var pageLoaded = false
    private var loading = false
    private var recording = false
    private var unansweredSince = 0L
    private var notedBoot: String? = null   // the last_boot.json "at" already shown
    private var holding = false             // the boot screen shows a note to read first
    private var polling = false
    private var splashStart = 0L            // when the payload's boot page went up (epoch ms)
    private var splashPage = false          // the WebView shows that page, not the app
    private var remote: String? = null      // second-screen mode: the laptop's BEGIA, else null
    private lateinit var bootAction2: Button
    private lateinit var second: View
    private lateinit var secondText: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { offerInstall(it) }
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // a FAT floor watches the trend: the screen stays on while BEGIA is up
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = findViewById(R.id.web)
        boot = findViewById(R.id.boot)
        bootStatus = findViewById(R.id.boot_status)
        bootDetail = findViewById(R.id.boot_detail)
        bootNote = findViewById(R.id.boot_note)
        bootProgress = findViewById(R.id.boot_progress)
        bootAction = findViewById(R.id.boot_action)
        bootAction2 = findViewById(R.id.boot_action2)
        second = findViewById(R.id.second)
        secondText = findViewById(R.id.second_text)
        findViewById<Button>(R.id.second_back).setOnClickListener { watchThisPhone() }
        remote = Source.remote(this)
        updateBanner()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true      // the payload's boot page, from the slot directory
        }
        web.addJavascriptInterface(ShellBridge(this), "BegiaShell")
        val debug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debug)   // chrome://inspect from the laptop
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                // the boot page finishing is not the app being up
                val isApp = url.startsWith(base())
                pageLoaded = isApp
                if (isApp) {
                    splashPage = false
                    if (!holding) hideBoot()
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    loading = false
                    pageLoaded = false
                }
            }

            /** A laptop's BEGIA serves HTTPS with its own CA (see Net): the
             *  one host this phone was pointed at is accepted, nothing else. */
            override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                val r = remote
                if (r != null && Source.host(error.url) == Source.host(r)) handler.proceed() else handler.cancel()
            }
        }
        askOnce()
        Recorder.start(this)
        applyLook(getSharedPreferences("shell", MODE_PRIVATE).getString("theme", "dark") ?: "dark")
        // the welcome plays while the recorder starts, not after it
        if (!showSplashPage()) showBoot(getString(R.string.boot_starting), activeBuildLine())
        handleIntent(intent)
    }

    /** The status and navigation bars wear the page's theme (the app's own
     *  meta theme-color per theme, ui/app.js THEME_META), light ones with
     *  dark icons - a dark strip over the Daylight theme was the phone's
     *  own bar, not the page. Told by the page through BegiaShell.noteLook,
     *  and from the saved preference before the page exists. */
    fun applyLook(theme: String) {
        val color = when (theme) {
            "carbon" -> 0xFF111318; "sarralle" -> 0xFF131B24; "blueprint" -> 0xFF151D31
            "amber" -> 0xFF1B1815; "daylight" -> 0xFFE2E7EC; "hmi" -> 0xFFDDE1E6
            else -> 0xFF151D25
        }.toInt()
        val light = theme == "daylight" || theme == "hmi"
        window.statusBarColor = color
        window.navigationBarColor = color
        // the window and the WebView too, so the instant before the boot page
        // paints is already the page's colour rather than a dark flash
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
        web.setBackgroundColor(color)
        androidx.core.view.WindowInsetsControllerCompat(window, web).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    override fun onStart() {
        super.onStart()
        if (!polling) {
            polling = true
            poll()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { offerInstall(it) }
    }

    // ------------------------------------------------------------- boot ----

    /** Ask /api/state every 700 ms; the answer decides between the page and the boot screen. */
    private fun poll() {
        io.execute {
            val st = probe()
            ui.post {
                onProbe(st)
                ui.postDelayed({ poll() }, 700)
            }
        }
    }

    /** The BEGIA this screen shows: this phone's recorder, or the laptop's. */
    private fun base(): String = remote ?: Recorder.BASE_URL

    private fun probe(): JSONObject? = probeAt(base())

    private fun probeAt(baseUrl: String): JSONObject? = try {
        val c = Net.connect("$baseUrl/api/state", 1500, 1500)
        c.inputStream.bufferedReader().use { r -> JSONObject(r.readText()) }
    } catch (e: Exception) {
        null
    }

    private fun onProbe(st: JSONObject?) {
        if (st != null && st.optString("app") == "begia") {
            unansweredSince = 0L
            recording = st.optBoolean("recording", false)
            showBootNoteIfNew()
            if (!pageLoaded && !loading) {
                loading = true
                // the app carries the welcome on from the boot page's start
                val q = if (splashStart > 0L) "?splash_start=$splashStart" else ""
                web.loadUrl(base() + "/" + q)
            }
            return
        }
        // not answering: booting, restarting, or dead - or a laptop out of reach
        pageLoaded = false
        val now = System.currentTimeMillis()
        if (unansweredSince == 0L) unansweredSince = now
        val r = remote
        val status = if (r != null) getString(R.string.second_connecting) else getString(R.string.boot_starting)
        val detail = if (r != null) Source.host(r) else activeBuildLine()
        if (!holding) {
            if (now - unansweredSince > (if (r != null) REMOTE_FAILED_AFTER_MS else FAILED_AFTER_MS)) showFailed()
            else if (splashPage) splashStatus(status, detail)
            else showBoot(status, detail)
        }
    }

    // ---------------------------------------------------- second screen ----

    /** Point this screen - and the watch, through Source - at a laptop's
     *  BEGIA. Refused while this phone's own recorder is in a trial: leaving
     *  that unwatched is how a recording ends without anyone noticing. */
    fun watchLaptop(url: String) {
        val target = url.trim().trimEnd('/')
        if (target.isEmpty()) return
        io.execute {
            val local = probeAt(Recorder.BASE_URL)
            if (local != null && local.optBoolean("recording", false)) {
                ui.post { tell(getString(R.string.app_name), getString(R.string.second_recording)) }
                return@execute
            }
            val there = probeAt(target)
            ui.post {
                if (there == null || there.optString("app") != "begia") {
                    tell(getString(R.string.app_name), getString(R.string.second_none, target))
                    return@post
                }
                Source.set(this, target)
                remote = target
                switchSource()
            }
        }
    }

    fun watchThisPhone() {
        Source.set(this, null)
        remote = null
        switchSource()
    }

    private fun switchSource() {
        web.stopLoading()
        pageLoaded = false
        loading = false
        holding = false
        unansweredSince = 0L
        updateBanner()
        if (!showSplashPage()) showBoot(
            if (remote != null) getString(R.string.second_connecting) else getString(R.string.boot_starting),
            if (remote != null) Source.host(remote!!) else activeBuildLine())
    }

    private fun updateBanner() {
        val r = remote
        second.visibility = if (r != null) View.VISIBLE else View.GONE
        if (r != null) secondText.text = getString(R.string.second_watching, Source.host(r))
    }

    /** The payload's own boot page - the welcome animation with a line of
     *  state under it - from the slot that is booting or active. Null for a
     *  payload that predates it, when the native screen stands in. */
    private fun bootPage(): File? {
        val build = bootingOrActive()
        if (build.isEmpty()) return null
        val slot = build.replace(Regex("[^A-Za-z0-9._-]"), "_")   // payload.slot_name
        val f = File(Recorder.slotsDir(this), "$slot/ui/boot.html")
        return if (f.isFile) f else null
    }

    /** Put the boot page up, wearing the app's last theme and text size, with
     *  the instant it went up: the app is loaded with the same instant and the
     *  animation carries on there instead of starting again. */
    private fun showSplashPage(): Boolean {
        val page = bootPage() ?: return false
        val prefs = getSharedPreferences("shell", MODE_PRIVATE)
        splashStart = System.currentTimeMillis()
        splashPage = true
        pageLoaded = false
        loading = false
        val theme = Uri.encode(prefs.getString("theme", "dark") ?: "dark")
        val scale = Uri.encode(prefs.getString("scale", "1.15") ?: "1.15")
        web.loadUrl(Uri.fromFile(page).toString() + "?theme=$theme&scale=$scale&start=$splashStart")
        boot.visibility = View.GONE
        return true
    }

    private fun splashStatus(status: String, detail: String) {
        web.evaluateJavascript(
            "window.bootStatus && bootStatus(${JSONObject.quote(status)}, ${JSONObject.quote(detail)})", null)
    }

    /** The build that is booting, else the active one. org.json's optString
     *  answers the STRING "null" for a JSON null, which is what "booting" is
     *  most of the time - so it is asked first whether the key is null. */
    private fun bootingOrActive(): String {
        val s = Recorder.slotState(this) ?: return ""
        val booting = if (s.isNull("booting")) "" else s.optString("booting", "")
        return booting.ifEmpty { if (s.isNull("active")) "" else s.optString("active", "") }
    }

    private fun activeBuildLine(): String = bootingOrActive()

    /** last_boot.json is written by the recorder process at the end of every
     *  start. A rollback note is shown once, and held until it has been read. */
    private fun showBootNoteIfNew() {
        val lb = Recorder.lastBoot(this) ?: return
        val stamp = lb.optString("at", "")
        if (stamp.isEmpty() || stamp == notedBoot) return
        notedBoot = stamp
        val note = lb.optString("rollback_note", "")
        if (note.isNotEmpty() && note != "null") {
            holding = true
            showBoot(getString(R.string.boot_rolled_back), lb.optString("build", ""))
            bootProgress.visibility = View.GONE
            bootNote.text = note
            bootNote.visibility = View.VISIBLE
            bootAction.text = getString(R.string.boot_continue)
            bootAction.visibility = View.VISIBLE
            bootAction.setOnClickListener {
                holding = false
                if (pageLoaded) hideBoot()
            }
        }
    }

    private fun showBoot(status: String, detail: String) {
        bootStatus.text = status
        bootDetail.text = detail
        bootProgress.visibility = View.VISIBLE
        bootNote.visibility = View.GONE
        bootAction.visibility = View.GONE
        bootAction2.visibility = View.GONE
        boot.visibility = View.VISIBLE
    }

    private fun showFailed() {
        val r = remote
        if (r != null) {
            // a laptop out of reach is not a failed boot: try again, or come home
            showBoot(getString(R.string.second_unreachable), r)
            bootProgress.visibility = View.GONE
            bootAction.text = getString(R.string.boot_try_again)
            bootAction.visibility = View.VISIBLE
            bootAction.setOnClickListener { unansweredSince = 0L; switchSource() }
            bootAction2.text = getString(R.string.second_back)
            bootAction2.visibility = View.VISIBLE
            bootAction2.setOnClickListener { watchThisPhone() }
            return
        }
        val err = Recorder.lastBoot(this)?.optString("error", "") ?: ""
        showBoot(getString(R.string.boot_failed), err.ifEmpty { "no answer on /api/state" })
        bootProgress.visibility = View.GONE
        bootAction.text = getString(R.string.boot_try_again)
        bootAction.visibility = View.VISIBLE
        bootAction.setOnClickListener {
            unansweredSince = 0L
            restartRecorder()
        }
    }

    private fun hideBoot() {
        boot.visibility = View.GONE
    }

    fun restartRecorder() {
        if (recording) {
            tell("A trial is recording", "Stop it first. Restarting the recorder would end it without its final flush.")
            return
        }
        pageLoaded = false
        if (!showSplashPage()) showBoot(getString(R.string.boot_starting), activeBuildLine())
        io.execute { Recorder.restart(this) }
    }

    // ---------------------------------------------------------- install ----

    fun pickPayload() = picker.launch(arrayOf("*/*"))

    /** Stage, verify and extract the file, then ask. Activation and the
     *  recorder restart happen only on Install. */
    private fun offerInstall(uri: Uri) {
        io.execute {
            try {
                val staged = Installer.stage(this, uri)
                val m = Installer.install(this, staged)
                ui.post { askToActivate(m) }
            } catch (e: Exception) {
                Log.w(Recorder.TAG, "install refused: ${e.message}")
                ui.post { tell(getString(R.string.install_refused), e.message ?: "") }
            }
        }
    }

    /** The laptop path: download, verify and extract, then the same dialog. */
    fun offerInstallFromUrl(url: String) {
        io.execute {
            try {
                val staged = Installer.download(this, url)
                val m = Installer.install(this, staged)
                ui.post { askToActivate(m) }
            } catch (e: Exception) {
                Log.w(Recorder.TAG, "install from $url refused: ${e.message}")
                ui.post { tell(getString(R.string.install_refused), e.message ?: "") }
            }
        }
    }

    /** Ask a laptop what it can offer and hand the answer to the page as an
     *  event, so the Setup card can say "0.10, and you have 0.9". */
    fun checkLaptop(url: String) {
        io.execute {
            val payload = try {
                JSONObject().put("ok", true).put("url", url).put("info", JSONObject(Installer.fetchText(url)))
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("url", url).put("error", e.message ?: "no answer")
            }
            ui.post {
                web.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('begia-laptop', {detail: $payload}))", null)
            }
        }
    }

    private fun askToActivate(m: JSONObject) {
        val version = m.optString("version")
        val build = m.optString("build")
        if (recording) {
            // installing restarts the recorder process, which would end the
            // trial without its final flush: the one thing a recorder must not do
            tell("A trial is recording", "Stop it first. Installing BEGIA $version restarts the recorder.")
            return
        }
        if (m.optBoolean("already_active")) {
            tell("BEGIA $version", "Build $build is already the version running.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.install_title, version))
            .setMessage(getString(R.string.install_body, build))
            .setPositiveButton(R.string.install_do) { _, _ ->
                io.execute {
                    try {
                        Installer.activate(this, build)
                        ui.post { restartRecorder() }
                    } catch (e: Exception) {
                        ui.post { tell(getString(R.string.install_refused), e.message ?: "") }
                    }
                }
            }
            .setNegativeButton(R.string.install_cancel, null)
            .show()
    }

    private fun tell(title: String, body: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton(R.string.ok, null).show()
    }

    // --------------------------------------------------------- hardware ----

    /** Volume-down stamps a mark while recording, so an event is logged
     *  without looking at the screen. Otherwise, and for volume-up, the
     *  phone keeps its keys. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && recording) {
            if (event.repeatCount == 0) io.execute { post("/api/trial/mark", """{"text":"mark (volume key)"}""") }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && recording) true else super.onKeyUp(keyCode, event)

    private fun post(path: String, json: String) {
        try {
            val c = Net.connect(base() + path, 2000, 4000)
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(json.toByteArray()) }
            Log.i(Recorder.TAG, "POST $path -> ${c.responseCode}")
        } catch (e: Exception) {
            Log.w(Recorder.TAG, "POST $path failed: $e")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // keep recording: leave the task rather than destroy the screen
        if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
    }

    // ----------------------------------------------------- first launch ----

    private fun askOnce() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // a recording runs with the phone in a pocket: Android must not doze the recorder
        val prefs = getSharedPreferences("shell", MODE_PRIVATE)
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("asked_battery", false)) {
            prefs.edit().putBoolean("asked_battery", true).apply()
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                Log.w(Recorder.TAG, "battery optimisation dialog: $e")
            }
        }
    }

    companion object {
        private const val FAILED_AFTER_MS = 90_000L
        private const val REMOTE_FAILED_AFTER_MS = 12_000L   // a laptop answers at once or not at all
    }
}
