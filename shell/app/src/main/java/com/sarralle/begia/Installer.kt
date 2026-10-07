package com.sarralle.begia

import android.content.Context
import android.net.Uri
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File

/**
 * The screen's own Python, for verifying and installing payloads. It never
 * imports the service. The slot manager it calls is the module the recorder
 * process boots from, so both read and write one slots/state.json.
 */
object Installer {
    class Refused(message: String) : Exception(message)

    internal fun py(ctx: Context): PyObject {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
        return Python.getInstance().getModule("begia_shell.android")
    }

    /** A PayloadError's text is written for the screen: keep just that. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: PyException) {
        val msg = e.message ?: "unreadable payload"
        throw Refused(if (msg.startsWith("PayloadError: ")) msg.removePrefix("PayloadError: ") else msg)
    }

    /** Copy whatever the intent or the picker handed us into our own cache. */
    fun stage(ctx: Context, uri: Uri): File {
        val f = File(ctx.cacheDir, "incoming.begia")
        val input = ctx.contentResolver.openInputStream(uri) ?: throw Refused("could not read $uri")
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }

    /**
     * Fetch a payload from a laptop's BEGIA (GET /api/payload) into our cache.
     *
     * The laptop serves HTTPS under its own local CA, which this phone has
     * no way to trust, so the certificate is not checked for THIS download:
     * the payload's own manifest hashes catch corruption, and a payload
     * carries no secret. That leaves a forged laptop on the plant WiFi as
     * the one open door - the decision taken for now, with payload signing
     * as the answer when a site's IT asks for it.
     */
    fun download(ctx: Context, url: String, code: String = ""): File {
        val f = File(ctx.cacheDir, "incoming.begia")
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        // The laptop shares its payload only for a while and, since 70e6c81,
        // only to whoever presents the six-digit code shown on its own
        // screen. A header, not the URL: the URL is logged, the code is not.
        if (code.isNotBlank()) c.setRequestProperty("X-Begia-Pair", code.filter { it.isDigit() })
        if (c is javax.net.ssl.HttpsURLConnection) {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val ssl = javax.net.ssl.SSLContext.getInstance("TLS")
            ssl.init(null, trustAll, java.security.SecureRandom())
            c.sslSocketFactory = ssl.socketFactory
            c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        c.connectTimeout = 5000
        c.readTimeout = 30000
        if (c.responseCode != 200) {
            val body = try { c.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { null }
            // the server's own words when it gives them ({"detail": "..."}):
            // "wrong pairing code", "sharing is closed; open it again on the
            // laptop" - the operator has to walk back to the laptop, and the
            // text says so
            val why = try { body?.let { JSONObject(it).optString("detail", "") }?.takeIf { it.isNotBlank() } }
                      catch (e: Exception) { null } ?: body?.take(160)
            throw Refused("the laptop answered ${c.responseCode}" + (why?.let { ": $it" } ?: ""))
        }
        c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }

    /** GET a small JSON document the same way (the laptop's /api/payload/info). */
    fun fetchText(url: String): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        if (c is javax.net.ssl.HttpsURLConnection) {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val ssl = javax.net.ssl.SSLContext.getInstance("TLS")
            ssl.init(null, trustAll, java.security.SecureRandom())
            c.sslSocketFactory = ssl.socketFactory
            c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        c.connectTimeout = 5000
        c.readTimeout = 10000
        if (c.responseCode != 200) throw Refused("the laptop answered ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** A laptop connection that accepts the laptop's own CA, as download()
     *  and fetchText() do - the box (IBA-CODE docs/LICENSING-DESIGN.md
     *  flow 4) carries only what verifies under the APK's keys anyway. */
    private fun open(url: String, readMs: Int): java.net.HttpURLConnection {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        if (c is javax.net.ssl.HttpsURLConnection) {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val ssl = javax.net.ssl.SSLContext.getInstance("TLS")
            ssl.init(null, trustAll, java.security.SecureRandom())
            c.sslSocketFactory = ssl.socketFactory
            c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        c.connectTimeout = 5000
        c.readTimeout = readMs
        return c
    }

    /** POST a JSON body to a laptop; its status code (no throw on 4xx). */
    fun postText(url: String, json: String): Int {
        val c = open(url, 10000)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(json.toByteArray()) }
        return c.responseCode
    }

    /** GET a small document from a laptop: (status, body). */
    fun getText(url: String): Pair<Int, String> {
        val c = open(url, 10000)
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return code to body
    }

    /** Verify and extract into a slot. Nothing is activated yet. */
    fun install(ctx: Context, zip: File): JSONObject = guarded {
        JSONObject(py(ctx).callAttr("install", zip.path, ctx.filesDir.path).toString())
    }

    /** Make a build the one that boots next. The caller restarts the recorder. */
    fun activate(ctx: Context, build: String) {
        guarded { py(ctx).callAttr("activate", build, ctx.filesDir.path) }
    }

    fun info(ctx: Context): String =
        guarded { py(ctx).callAttr("info", ctx.filesDir.path, Licence.deviceId(ctx)).toString() }

    /** Whether an unsigned payload is refused from now on (policy.json). */
    fun setRequireSigned(ctx: Context, on: Boolean): String =
        guarded { py(ctx).callAttr("set_policy", ctx.filesDir.path, on).toString() }
}
