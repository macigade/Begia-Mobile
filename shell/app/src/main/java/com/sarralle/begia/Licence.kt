package com.sarralle.begia

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest

/**
 * This phone's licence, as the shell sees it (IBA-CODE
 * docs/LICENSING-DESIGN.md 2.3 and 4.2).
 *
 * The identity is ANDROID_ID, which Android scopes to the APK's signing key
 * (shell/signing.gradle): a licensable payload is told it as
 * BEGIA_DEVICE_ID = "and:<id>" (RecorderService -> boot.prepare), and what
 * anyone sees or sends is only its device code - the Licence Manager's
 * code_of(): sha256, base32, five groups of four.
 *
 * The verdict is runtime/begia_shell/licence.py's status() over
 * <filesDir>/data/licence.json, through the screen process's Python, with
 * the APK's own keys (trust.LICENCE_KEYS): no payload can vouch for itself.
 * Cached 30 s, and dropped after any install.
 */
object Licence {
    private const val CACHE_MS = 30_000L

    @Volatile private var cached: JSONObject? = null
    @Volatile private var cachedAt = 0L
    /** Set by an install; onResume re-checks companion mode when it sees it. */
    @Volatile var changed = false

    /** "and:<ANDROID_ID>", or "" when the phone will not say. Never shown. */
    @SuppressLint("HardwareIds")
    fun deviceId(ctx: Context): String {
        val id = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
        return if (id.isNullOrBlank()) "" else "and:$id"
    }

    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** The device code: what the door shows and the Licence Manager is given. */
    fun code(ctx: Context): String = codeOf(deviceId(ctx))

    /** begialic/format.py code_of(): base32 of sha256, first 20 characters,
     *  in groups of four. "" for no identity. */
    fun codeOf(guid: String): String {
        if (guid.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(guid.toByteArray(Charsets.UTF_8))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in digest) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5 && out.length < 20) {
                out.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            if (out.length >= 20) break
        }
        return out.chunked(4).joinToString("-")
    }

    /** licence.status() for this phone: {ok, why, code, present, customer,
     *  expires, days_left, id, ...}. Cached 30 s; never throws - a Python
     *  that cannot start is a phone without a licence the shell can see. */
    fun status(ctx: Context, fresh: Boolean = false): JSONObject {
        val now = System.currentTimeMillis()
        val c = cached
        if (!fresh && c != null && now - cachedAt < CACHE_MS) return c
        val s = try {
            JSONObject(Installer.py(ctx).callAttr("licence_status", ctx.filesDir.path, deviceId(ctx)).toString())
        } catch (e: Exception) {
            Log.w(Recorder.TAG, "licence status: $e")
            JSONObject().put("ok", false).put("why", "the licence could not be checked (${e.message})")
                .put("code", code(ctx)).put("present", false)
        }
        cached = s
        cachedAt = now
        return s
    }

    fun ok(ctx: Context): Boolean = status(ctx).optBoolean("ok", false)

    /** After a licence install (or removal): the next ok() reads the file. */
    fun invalidate() {
        cached = null
        changed = true
    }
}
