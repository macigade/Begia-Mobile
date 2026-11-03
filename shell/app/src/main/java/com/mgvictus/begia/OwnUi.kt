package com.sarralle.begia

import android.content.Context
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Second-screen mode draws the phone's own page over the laptop's data.
 *
 * Showing a laptop's BEGIA, the WebView loads the laptop's address and got
 * the page the laptop serves - the files its BEGIA.exe was built with. A
 * laptop is rebuilt when a laptop needs it, and the phone's layout moves on
 * with every payload: the panes chip and the name over the value were on
 * the phone's own BEGIA and not in companion mode, because the laptop's
 * exe predated them.
 *
 * So the page's own files - index.html, js/, css/, the icons and
 * fonts - are answered from the phone's installed payload (the active slot's
 * ui/, verified and signed like every payload), while everything that is
 * data - /api/..., the /ws socket - still goes to the laptop. The document
 * keeps the laptop's address, so its sign-in cookie, the socket and every
 * same-origin call work as before.
 *
 * Whichever build is newer draws the page: a laptop updated past the phone
 * serves its own (it carries the same shared ui/ with everything the phone
 * has, and more), and a phone updated past the laptop uses its own.
 */
object OwnUi {
    private val MIME = mapOf(
        "html" to "text/html", "js" to "application/javascript", "css" to "text/css",
        "svg" to "image/svg+xml", "json" to "application/json", "png" to "image/png",
        "ico" to "image/x-icon", "woff2" to "font/woff2", "woff" to "font/woff",
        "ttf" to "font/ttf", "otf" to "font/otf", "webp" to "image/webp", "txt" to "text/plain",
    )

    /** The active slot's ui/ directory, or null. */
    fun activeUiDir(ctx: Context): File? {
        val s = Recorder.slotState(ctx) ?: return null
        val build = if (s.isNull("active")) "" else s.optString("active", "")
        if (build.isEmpty()) return null
        val slot = build.replace(Regex("[^A-Za-z0-9._-]"), "_")   // payload.slot_name
        val dir = File(Recorder.slotsDir(ctx), "$slot/ui")
        return if (File(dir, "index.html").isFile) dir else null
    }

    fun activeBuild(ctx: Context): String {
        val s = Recorder.slotState(ctx) ?: return ""
        return if (s.isNull("active")) "" else s.optString("active", "")
    }

    /** The laptop's build, from its open /api/payload/info; "" when it
     *  does not say (an older laptop, or no payload in its exe). */
    fun laptopBuild(base: String): String = try {
        val c = Net.connect("$base/api/payload/info", 1500, 2500)
        c.inputStream.bufferedReader().use { JSONObject(it.readText()) }.optString("build", "")
    } catch (e: Exception) {
        ""
    }

    /** "v0.9-77-ga007fd7" -> [0, 9, 77]; a tag alone is count 0. Null when
     *  the stamp is not one git describe makes. */
    fun order(build: String): List<Int>? {
        val m = Regex("""^v(\d+)\.(\d+)(?:\.(\d+))?(?:-(\d+)-g[0-9a-f]+)?(?:-dirty)?$""").find(build.trim())
            ?: return null
        val g = m.groupValues
        return listOf(g[1].toInt(), g[2].toInt(), g[3].ifEmpty { "0" }.toInt(), g[4].ifEmpty { "0" }.toInt())
    }

    /** True when the phone's page should be drawn: its build is not older
     *  than the laptop's, or the laptop's cannot be told. */
    fun phoneDraws(phone: String, laptop: String): Boolean {
        val p = order(phone) ?: return false          // a phone build we cannot place: the laptop's page
        val l = order(laptop) ?: return true          // a laptop that does not say: the phone's
        for (i in p.indices) if (p[i] != l[i]) return p[i] > l[i]
        return true
    }

    /** The file for a request path under `dir`, or null to let the request
     *  go to the laptop. Never outside `dir`. */
    fun response(dir: File, path: String): WebResourceResponse? {
        val rel = path.trimStart('/').ifEmpty { "index.html" }
        if (rel.contains("..")) return null
        val f = File(dir, rel)
        val canon = try { f.canonicalFile } catch (e: Exception) { return null }
        if (!canon.path.startsWith(dir.canonicalPath + File.separator) || !canon.isFile) return null
        val mime = MIME[canon.extension.lowercase()] ?: "application/octet-stream"
        val enc = if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json") || mime.endsWith("svg+xml")) "utf-8" else null
        return WebResourceResponse(mime, enc, 200, "OK",
            mapOf("Cache-Control" to "no-store"), FileInputStream(canon))
    }
}
