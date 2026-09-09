package com.sarralle.begia

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * `window.BegiaShell` inside the WebView: what the shared UI may ask of the
 * phone. The UI knows it is running inside the shell by the object's
 * existence; on a laptop browser it is simply absent.
 */
class ShellBridge(private val activity: MainActivity) {
    /** Shell version, active and installed payloads, the last boot's report. */
    @JavascriptInterface
    fun info(): String = try {
        Installer.info(activity)
    } catch (e: Exception) {
        JSONObject().put("error", e.message ?: "unknown").toString()
    }

    @JavascriptInterface
    fun apk(): String = JSONObject()
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("debug", BuildConfig.DEBUG)
        .toString()

    /** The system file picker for a .begia; the install dialog follows. */
    @JavascriptInterface
    fun pickPayload() {
        activity.runOnUiThread { activity.pickPayload() }
    }

    @JavascriptInterface
    fun restartRecorder() {
        activity.runOnUiThread { activity.restartRecorder() }
    }
}
