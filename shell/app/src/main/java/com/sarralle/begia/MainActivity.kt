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
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    // second-screen mode: the laptop's BEGIA, else null; read on the WebView's
    // own thread too (shouldInterceptRequest)
    @Volatile private var remote: String? = null
    private lateinit var bootAction2: Button
    private lateinit var second: View
    private lateinit var secondText: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { offerInstall(it) }
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // the licence (IBA-CODE docs/LICENSING-DESIGN.md 3, flows 3 and 5): the
    // QR the Licence Manager shows, and a licence file - which goes through
    // the same first-byte sniff as anything else opened here
    private val scanner = registerForActivityResult(ScanContract()) { r ->
        val text = r.contents
        val detail = when {
            text != null -> JSONObject().put("ok", true).put("text", text)
            r.originalIntent?.hasExtra("MISSING_CAMERA_PERMISSION") == true ->
                JSONObject().put("ok", false).put("error", "the camera was not allowed - open the licence file, or paste it")
            else -> JSONObject().put("ok", false).put("error", "cancelled")
        }
        dispatch("begia-scan", detail)
    }
    private val licencePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { offerInstall(it) }
    }
    private var licenceCheckedDay = ""      // onResume: companion mode re-checked once a day

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
        // a remembered laptop only for a licensed phone (LICENSING-DESIGN 4.2,
        // check 2): one whose licence lapsed comes up on its own page, which
        // shows the door, and the strip never shows
        remote = Source.remote(this)?.let { r ->
            if (Licence.ok(this)) r else {
                Source.set(this, null)
                Toast.makeText(this, R.string.second_dropped, Toast.LENGTH_LONG).show()
                null
            }
        }
        licenceCheckedDay = today()
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
                    // second-screen mode: the laptop's page carries the
                    // session its sign-in door set (the door reloads the
                    // page after a sign-in, and a sign-out); the relay and
                    // the volume key dial the laptop with the same one
                    remote?.let { r ->
                        Source.setCookie(this@MainActivity,
                            try { CookieManager.getInstance().getCookie(r) } catch (e: Exception) { null })
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    loading = false
                    pageLoaded = false
                }
            }

            /** Second-screen mode: the page's own files from this phone's
             *  payload when it is the newer build (OwnUi), the data from the
             *  laptop. Runs on the WebView's own thread; the first request
             *  for a laptop asks it for its build, once. */
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                val r = remote ?: return null
                if (request.method != "GET") return null
                if (!request.url.toString().startsWith(r)) return null
                val path = request.url.path ?: "/"
                if (path == "/ws" || path == "/api" || path.startsWith("/api/")) return null
                val dir = ownUiFor(r) ?: return null
                return OwnUi.response(dir, path)
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
     *  meta theme-color per theme, THEME_META in the desktop's
     *  ui/js/100-theme.js), light ones with
     *  dark icons - a dark strip over the Daylight theme was the phone's
     *  own bar, not the page. Told by the page through BegiaShell.noteLook,
     *  and from the saved preference before the page exists. */
    fun applyLook(theme: String) {
        val color = when (theme) {
            // "victus" is the house palette since desktop d8edb47; "sarralle"
            // is its old id, still in a preference saved before the rename
            "carbon" -> 0xFF111318; "victus", "sarralle" -> 0xFF131B24; "blueprint" -> 0xFF151D31
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

    /** Companion mode re-checked once a day and after any licence install
     *  (LICENSING-DESIGN 4.2, check 4): an expiry past its grace drops the
     *  laptop at the next resume, not the next restart. */
    override fun onResume() {
        super.onResume()
        val day = today()
        if (remote == null || (day == licenceCheckedDay && !Licence.changed)) return
        licenceCheckedDay = day
        Licence.changed = false
        io.execute {
            val ok = Licence.status(this, fresh = true).optBoolean("ok", false)
            if (!ok) ui.post {
                watchThisPhone()
                tell(getString(R.string.app_name), getString(R.string.second_dropped))
            }
        }
    }

    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())

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

    // Which page second-screen mode draws for a laptop (OwnUi): decided once
    // per laptop and phone build, on the WebView's thread at the first request.
    @Volatile private var ownUiLaptop: String? = null
    @Volatile private var ownUiPhoneBuild: String? = null
    @Volatile private var ownUiDir: File? = null

    private fun ownUiFor(laptop: String): File? {
        val phoneBuild = OwnUi.activeBuild(this)
        if (laptop != ownUiLaptop || phoneBuild != ownUiPhoneBuild) {
            val lb = OwnUi.laptopBuild(laptop)
            val draws = OwnUi.phoneDraws(phoneBuild, lb)
            ownUiDir = if (draws) OwnUi.activeUiDir(this) else null
            Log.i(Recorder.TAG, "second screen: phone $phoneBuild, laptop ${lb.ifEmpty { "?" }} - " +
                  (if (ownUiDir != null) "the phone's page" else "the laptop's page"))
            ownUiLaptop = laptop
            ownUiPhoneBuild = phoneBuild
        }
        return ownUiDir
    }

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
        // this phone's own address is not a laptop: the page would be the
        // one already on screen, with a strip claiming it is someone else's
        val host = Source.host(target)
        if (host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "0.0.0.0") {
            tell(getString(R.string.app_name), getString(R.string.second_self))
            return
        }
        io.execute {
            // no licence, no laptop (LICENSING-DESIGN 4.2, check 1): nothing
            // loads from it, and the laptop never learns the phone tried
            if (!Licence.status(this, fresh = true).optBoolean("ok", false)) {
                ui.post { tell(getString(R.string.app_name), getString(R.string.second_unlicensed)) }
                return@execute
            }
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
        // the same guard on every way into a laptop (LICENSING-DESIGN 4.2,
        // check 3) - "Try again" on an unreachable laptop comes through here
        if (remote != null && !Licence.ok(this)) {
            Source.set(this, null)
            remote = null
            tell(getString(R.string.app_name), getString(R.string.second_unlicensed))
        }
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
        // no page, no door: the device code and the licence state are said
        // here, natively, so a phone can always be read and licensed
        // (LICENSING-DESIGN 4.4). Rewritten with the same text by each probe.
        val lic = Licence.status(this)
        bootNote.text = getString(R.string.licence_line, lic.optString("code", "?"),
            if (lic.optBoolean("ok", false)) getString(R.string.licence_installed, lic.optString("customer"))
            else lic.optString("why", ""))
        bootNote.visibility = View.VISIBLE
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
     *  recorder restart happen only on Install. A licence arrives by the
     *  same doors (mail, Drive, a picker), so the first byte decides: "{" a
     *  licence, anything else the payload installer as before. */
    private fun offerInstall(uri: Uri) {
        io.execute {
            try {
                val staged = Installer.stage(this, uri)
                val head = staged.inputStream().use { s -> ByteArray(64).let { b -> b.copyOf(s.read(b).coerceAtLeast(0)) } }
                if (head.firstOrNull { it.toInt().toChar() !in " \t\r\n﻿" }?.toInt()?.toChar() == '{' ||
                    (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte())) {
                    offerLicence(staged.readText(Charsets.UTF_8).removePrefix("﻿"))
                    return@execute
                }
                val m = Installer.install(this, staged)
                ui.post { askToActivate(m) }
            } catch (e: Exception) {
                Log.w(Recorder.TAG, "install refused: ${e.message}")
                ui.post { tell(getString(R.string.install_refused), e.message ?: "") }
            }
        }
    }

    /** The laptop path: download (with the laptop's pairing code, when it
     *  asks for one), verify and extract, then the same dialog. */
    fun offerInstallFromUrl(url: String, code: String = "") {
        io.execute {
            try {
                val staged = Installer.download(this, url, code)
                val m = Installer.install(this, staged)
                ui.post { askToActivate(m) }
            } catch (e: Exception) {
                Log.w(Recorder.TAG, "install from $url refused: ${e.message}")
                ui.post { tell(getString(R.string.install_refused), e.message ?: "") }
            }
        }
    }

    /** Ask the WiFi which BEGIA are on it (Discovery) and hand what answered
     *  to the page as a `begia-found` event: {ok, laptops: [...] | error}. */
    fun findLaptops() {
        io.execute {
            val payload = try {
                JSONObject().put("ok", true).put("laptops", Discovery.find(this))
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message ?: e.toString())
            }
            ui.post {
                web.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('begia-found', {detail: $payload}))", null)
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

    // ---------------------------------------------------------- licence ----
    // IBA-CODE docs/LICENSING-DESIGN.md 3 and 4. The shell judges a licence
    // for this phone before offering it (licence.py, the APK's keys), and the
    // phone's own service installs it - POST /api/licence on the loopback,
    // which verifies again and writes data/licence.json. The shell never
    // writes that file.

    private fun dispatch(event: String, detail: JSONObject) {
        ui.post {
            web.evaluateJavascript("window.dispatchEvent(new CustomEvent('$event', {detail: $detail}))", null)
        }
    }

    /** The licence QR (flow 3): the decoded text comes back to the page as a
     *  `begia-scan` event {ok, text | error}; the page installs it. */
    fun scanLicence() {
        ui.post {
            scanner.launch(ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.licence_scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false))
        }
    }

    /** "Open a licence file..." (flow 5): the page's file input is dead in a
     *  WebView without a chooser, so the system picker, then the sniff. */
    fun pickLicence() {
        ui.post { licencePicker.launch(arrayOf("application/json", "*/*")) }
    }

    /** Judge a licence (or a pack) for this phone, and offer it. Off the UI thread. */
    private fun offerLicence(text: String) {
        val v = try {
            JSONObject(Installer.py(this).callAttr("licence_offer", filesDir.path,
                Licence.deviceId(this), text).toString())
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("why", e.message ?: "unreadable")
        }
        ui.post {
            if (!v.optBoolean("ok", false)) {
                val why = v.optString("why")
                tell(getString(R.string.licence_refused),
                     if (why.startsWith("this is not a licence") || why.startsWith("this is not a BEGIA licence"))
                         getString(R.string.licence_not_licence) else why)
                return@post
            }
            val until = if (v.isNull("expires") || v.optString("expires").isEmpty()) getString(R.string.licence_no_expiry)
                        else getString(R.string.licence_until, v.optString("expires"))
            AlertDialog.Builder(this)
                .setTitle(R.string.licence_title)
                .setMessage(getString(R.string.licence_body, v.optString("customer"), v.optString("code"), until))
                .setPositiveButton(R.string.licence_do) { _, _ -> io.execute { installLicence(v.optString("text")) } }
                .setNegativeButton(R.string.install_cancel, null)
                .show()
        }
    }

    /** POST the licence to this phone's own service. Off the UI thread. */
    private fun installLicence(text: String) {
        val (code, answer) = postLocal("/api/licence", JSONObject().put("text", text).toString())
        Licence.invalidate()
        ui.post {
            if (code in 200..299) {
                Toast.makeText(this, getString(R.string.licence_installed,
                    answer.optString("customer").ifEmpty { "this phone" }), Toast.LENGTH_LONG).show()
                // the page asks again: the door gives way to the app
                if (remote == null) { pageLoaded = false; web.reload() }
            } else if (code == 404) {
                tell(getString(R.string.licence_refused), getString(R.string.licence_predates))
            } else {
                tell(getString(R.string.licence_refused),
                     answer.optString("detail").ifEmpty { answer.optString("error", "the service answered $code") })
            }
        }
    }

    /** POST JSON to this phone's own recorder (never the laptop's): (status, body). */
    private fun postLocal(path: String, json: String): Pair<Int, JSONObject> = try {
        val c = Net.connect(Recorder.BASE_URL + path, 2000, 10000)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(json.toByteArray()) }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        code to (try { JSONObject(body) } catch (e: Exception) { JSONObject() })
    } catch (e: Exception) {
        0 to JSONObject().put("error", "this phone's recorder is not answering ($e)")
    }

    /** GET text from this phone's own recorder: (status, body). */
    private fun getLocal(path: String): Pair<Int, String> = try {
        val c = Net.connect(Recorder.BASE_URL + path, 2000, 10000)
        val code = c.responseCode
        code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
    } catch (e: Exception) {
        0 to ""
    }

    /** "Send a licence request..." (flow 5): this phone's request document,
     *  from its own service, through the share sheet - Bluetooth to the
     *  owner's laptop needs no network at all. */
    fun shareRequest() {
        io.execute {
            val (code, text) = getLocal("/api/licence/request")
            val doc = try { JSONObject(text) } catch (e: Exception) { null }
            val mine = Licence.code(this)
            // a payload that predates licences answers with a code of its own
            // making, which no licence for this phone can match
            if (code != 200 || doc == null || doc.optString("machine").replace("-", "") != mine.replace("-", "")) {
                ui.post { tell(getString(R.string.licence_share_request), getString(R.string.licence_predates)) }
                return@execute
            }
            share("licence-request-$mine.json", text, getString(R.string.licence_share_request))
        }
    }

    /** "Send a usage report..." (LICENSING-DESIGN 5.3 c): the service's
     *  report, through the share sheet. */
    fun shareUsage() {
        io.execute {
            val (code, text) = getLocal("/api/licence/usage")
            if (code != 200) {
                ui.post { tell(getString(R.string.licence_share_usage), getString(R.string.licence_predates)) }
                return@execute
            }
            val day = today().replace("-", "")
            share("usage-report-${Licence.code(this)}-$day.json", text, getString(R.string.licence_share_usage))
        }
    }

    private fun share(name: String, text: String, title: String) {
        val dir = File(cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name)
        f.writeText(text)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ui.post { startActivity(Intent.createChooser(send, title)) }
    }

    /** "Get it from the laptop" (flow 4): leave this phone's request in the
     *  laptop's box and take its licence if one is waiting. The answer goes
     *  to the page as a `begia-box` event {ok, licence | why}; a licence
     *  found is offered at once. */
    fun getLicenceFromLaptop(base: String) {
        val laptop = base.trim().trimEnd('/')
        io.execute {
            val detail = try {
                val (rc, request) = getLocal("/api/licence/request")
                if (rc != 200) throw Installer.Refused(getString(R.string.licence_predates))
                val mine = Licence.code(this)
                val (posted, postBody) = Installer.postText("$laptop/api/licence/box/requests",
                    JSONObject().put("text", request).toString())
                // a laptop whose BEGIA predates the box: no such route (404),
                // a static mount that takes only GET (405), or its sign-in
                // gate, which the box routes are open past from Phase 1 (401)
                if (posted == 404 || posted == 405 || posted == 401)
                    throw Installer.Refused(getString(R.string.licence_box_old))
                // any other refusal is the laptop's to word: the box is full
                // (507), the file too big (413), not a request (400), the box
                // off (403), another copy holds the folder (423)
                if (posted !in 200..299) throw Installer.Refused(Installer.said(posted, postBody))
                val (gc, lic) = Installer.getText("$laptop/api/licence/box/licences/$mine")
                when (gc) {
                    200 -> {
                        val doc = try { JSONObject(lic) } catch (e: Exception) { JSONObject() }
                        if (doc.has("begia_licence_refusal")) {
                            JSONObject().put("ok", false).put("why", doc.optString("why"))
                        } else {
                            offerLicence(lic)
                            JSONObject().put("ok", true).put("licence", true)
                        }
                    }
                    404 -> JSONObject().put("ok", false).put("why", getString(R.string.licence_box_none))
                    else -> JSONObject().put("ok", false).put("why", Installer.said(gc, lic))
                }
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("why", e.message ?: e.toString())
            }
            dispatch("begia-box", detail)
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
            val c = Net.connect(base() + path, 2000, 4000, Source.cookieFor(this, base() + path))
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
