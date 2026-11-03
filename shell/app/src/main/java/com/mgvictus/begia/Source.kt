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
    private const val COOKIE = "remote_cookie"

    fun remote(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun base(ctx: Context): String = remote(ctx) ?: Recorder.BASE_URL

    fun set(ctx: Context, url: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (url.isNullOrBlank()) remove(KEY) else putString(KEY, url.trimEnd('/'))
            if (url.isNullOrBlank()) remove(COOKIE)     // a session belongs to one laptop
        }.apply()
    }

    /**
     * The laptop's session. Since desktop ee07d6d a laptop's API refuses a
     * request from another machine unless it carries the session cookie
     * the sign-in door set; the page in the WebView has it, and the phone's
     * own callers - the watch's relay, the volume-key mark - do not. The
     * activity copies the WebView's cookie for the laptop here whenever the
     * laptop's page has loaded (the door reloads the page after a sign-in,
     * and after a sign-out), and everything that dials the laptop natively
     * sends it. Never sent to any other host, never the phone's own.
     */
    fun setCookie(ctx: Context, cookie: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (cookie.isNullOrBlank()) remove(COOKIE) else putString(COOKIE, cookie)
        }.apply()
    }

    /** The cookie to send with a request to `url`: the laptop's session when
     *  `url` is the laptop's, else nothing. */
    fun cookieFor(ctx: Context, url: String): String? {
        val r = remote(ctx) ?: return null
        if (!url.startsWith(r)) return null
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(COOKIE, null)?.takeIf { it.isNotBlank() }
    }

    /** The host to name on screen: "192.168.0.5" out of "https://192.168.0.5:8443". */
    fun host(url: String): String = try { URL(url).host } catch (e: Exception) { url }
}
