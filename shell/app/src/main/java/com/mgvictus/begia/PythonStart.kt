package com.mgvictus.begia

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

/**
 * Python for either process - the screen's (Installer, Licence) and the
 * recorder's - started by one process at a time. After an app update
 * Chaquopy unpacks its files afresh as Python starts, clearing the old
 * ones, and two processes doing that together trip over each other: the
 * S23, 2026-10-28, opened a .begia straight after the update, the
 * installer's Python and the recorder's started at once, and the
 * recorder's failed in rmtree on a file the other had already removed
 * ("FileNotFoundError: 'Accra'"). The lock is held until begia_shell.android
 * is imported, so the first imports unpack under it too.
 */
object PythonStart {
    const val LOCK_FILE = "python-start.lock"
    /** far longer than an unpacking takes (about 2 s on the S23) */
    private const val WAIT_MS = 15_000L

    @Volatile private var shell: PyObject? = null

    /** begia_shell.android, Python started first if this process has not. */
    fun module(ctx: Context): PyObject = shell ?: ProcessLock.holding(File(ctx.filesDir, LOCK_FILE), WAIT_MS) {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
        Python.getInstance().getModule("begia_shell.android")
    }.also { shell = it }
}
