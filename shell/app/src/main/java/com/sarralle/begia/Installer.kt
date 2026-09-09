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

    private fun py(ctx: Context): PyObject {
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

    /** Verify and extract into a slot. Nothing is activated yet. */
    fun install(ctx: Context, zip: File): JSONObject = guarded {
        JSONObject(py(ctx).callAttr("install", zip.path, ctx.filesDir.path).toString())
    }

    /** Make a build the one that boots next. The caller restarts the recorder. */
    fun activate(ctx: Context, build: String) {
        guarded { py(ctx).callAttr("activate", build, ctx.filesDir.path) }
    }

    fun info(ctx: Context): String = guarded { py(ctx).callAttr("info", ctx.filesDir.path).toString() }
}
