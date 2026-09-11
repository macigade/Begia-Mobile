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

    /** Ask a laptop's BEGIA what payload it offers; the answer arrives as a
     *  `begia-laptop` event on window with {ok, url, info | error}. */
    @JavascriptInterface
    fun checkLaptop(infoUrl: String) {
        activity.checkLaptop(infoUrl)
    }

    /** Download a payload from a URL and offer to install it. */
    @JavascriptInterface
    fun installFromUrl(payloadUrl: String) {
        activity.offerInstallFromUrl(payloadUrl)
    }

    /** What the page looks like - theme and text size - so the boot page,
     *  which is shown before the page exists and cannot read its storage,
     *  wears the same next time. */
    @JavascriptInterface
    fun noteLook(theme: String, scale: String) {
        activity.getSharedPreferences("shell", android.content.Context.MODE_PRIVATE)
            .edit().putString("theme", theme).putString("scale", scale).apply()
        activity.runOnUiThread { activity.applyLook(theme) }
    }
}
