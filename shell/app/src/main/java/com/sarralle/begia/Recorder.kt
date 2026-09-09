package com.sarralle.begia

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * What both processes know about the recorder: where it listens, where the
 * slots are, and how to start it again.
 *
 * The recorder runs in its own process (`:recorder`, see the manifest) so
 * that starting it afresh - after an update, or after a start that never
 * became healthy - is a process kill the screen survives.
 */
object Recorder {
    const val TAG = "begia.shell"
    const val PORT = 8080
    const val DEV_PORT = 8081
    const val BASE_URL = "http://127.0.0.1:$PORT"
    private const val PROCESS_SUFFIX = ":recorder"

    fun slotsDir(ctx: Context) = File(ctx.filesDir, "slots")
    fun lastBootFile(ctx: Context) = File(ctx.filesDir, "last_boot.json")

    fun start(ctx: Context) {
        ctx.startForegroundService(Intent(ctx, RecorderService::class.java))
    }

    /** Stop the recorder and kill its process, then start it again: the next
     *  start boots the active slot - or rolls back, if the last one never
     *  became healthy (runtime/begia_shell/payload.py, Slots). */
    fun restart(ctx: Context) {
        ctx.stopService(Intent(ctx, RecorderService::class.java))
        val am = ctx.getSystemService(ActivityManager::class.java)
        val name = ctx.packageName + PROCESS_SUFFIX
        am.runningAppProcesses?.filter { it.processName == name }?.forEach {
            Log.i(TAG, "killing recorder process ${it.pid}")
            Process.killProcess(it.pid)
        }
        start(ctx)
    }

    /** slots/state.json as the slot manager wrote it, or null. */
    fun slotState(ctx: Context): JSONObject? = readJson(File(slotsDir(ctx), "state.json"))

    /** last_boot.json: what the recorder process reported at the end of its
     *  last start - the build, a rollback note, or the error. */
    fun lastBoot(ctx: Context): JSONObject? = readJson(lastBootFile(ctx))

    /** True when a failed start has an earlier build to fall back to. */
    fun canRollBack(ctx: Context): Boolean {
        val s = slotState(ctx) ?: return false
        val prev = s.optString("previous", "")
        return prev.isNotEmpty() && prev != s.optString("active", "")
    }

    private fun readJson(f: File): JSONObject? = try {
        if (f.isFile) JSONObject(f.readText()) else null
    } catch (e: Exception) {
        null
    }
}
