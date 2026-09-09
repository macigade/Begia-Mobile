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

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.addJavascriptInterface(ShellBridge(this), "BegiaShell")
        val debug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debug)   // chrome://inspect from the laptop
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                pageLoaded = true
                if (!holding) hideBoot()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    loading = false
                    pageLoaded = false
                }
            }
        }
        askOnce()
        Recorder.start(this)
        handleIntent(intent)
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

    private fun probe(): JSONObject? = try {
        val c = URL("${Recorder.BASE_URL}/api/state").openConnection() as HttpURLConnection
        c.connectTimeout = 1200
        c.readTimeout = 1200
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
                web.loadUrl(Recorder.BASE_URL + "/")
            }
            return
        }
        // not answering: booting, restarting, or dead
        pageLoaded = false
        val now = System.currentTimeMillis()
        if (unansweredSince == 0L) unansweredSince = now
        if (!holding) {
            if (now - unansweredSince > FAILED_AFTER_MS) showFailed()
            else showBoot(getString(R.string.boot_starting), activeBuildLine())
        }
    }

    private fun activeBuildLine(): String {
        val s = Recorder.slotState(this) ?: return ""
        return s.optString("booting", "").ifEmpty { s.optString("active", "") }
    }

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
        boot.visibility = View.VISIBLE
    }

    private fun showFailed() {
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
        pageLoaded = false
        showBoot(getString(R.string.boot_starting), activeBuildLine())
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

    private fun askToActivate(m: JSONObject) {
        val version = m.optString("version")
        val build = m.optString("build")
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
            val c = URL(Recorder.BASE_URL + path).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 2000
            c.readTimeout = 4000
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
    }
}
