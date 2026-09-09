package com.sarralle.begia

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

/**
 * The payload the APK shipped with, copied out of the assets so the slot
 * manager can read it as a file. Re-copied after every APK install (the
 * package's update time is the stamp), never otherwise.
 */
object Embedded {
    private const val ASSET = "payload/begia-payload.begia"

    fun ensure(ctx: Context): File? {
        val out = File(ctx.filesDir, "embedded.begia")
        val stampFile = File(ctx.filesDir, "embedded.stamp")
        val stamp = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (out.isFile && stampFile.isFile && stampFile.readText() == stamp) return out
        return try {
            ctx.assets.open(ASSET).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            stampFile.writeText(stamp)
            Log.i(Recorder.TAG, "embedded payload copied (${out.length()} bytes)")
            out
        } catch (e: FileNotFoundException) {
            Log.w(Recorder.TAG, "the APK carries no embedded payload")
            null
        }
    }
}
