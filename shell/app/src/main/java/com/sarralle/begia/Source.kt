package com.sarralle.begia

import android.content.Context
import java.net.URL

/**
 * Which BEGIA this phone is looking at. Normally its own recorder on the
 * loopback; in second-screen mode a laptop's, over the plant WiFi, chosen
 * from the "This phone" card and remembered until "This phone" is pressed.
 * Everything that talks to a recorder - the WebView, the boot probe, the
 * volume-key mark, and the watch's relay - asks here for the base URL, so
 * the wrist sees whatever the phone sees.
 */
object Source {
    private const val PREFS = "shell"
    private const val KEY = "remote_url"

    fun remote(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun base(ctx: Context): String = remote(ctx) ?: Recorder.BASE_URL

    fun set(ctx: Context, url: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (url.isNullOrBlank()) remove(KEY) else putString(KEY, url.trimEnd('/'))
        }.apply()
    }

    /** The host to name on screen: "192.168.0.5" out of "https://192.168.0.5:8443". */
    fun host(url: String): String = try { URL(url).host } catch (e: Exception) { url }
}
