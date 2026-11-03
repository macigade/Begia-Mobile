package com.mgvictus.begia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * PythonStart's lock (ProcessLock) between real processes. The S23's
 * failure on 2026-10-28 - the recorder's Python start clearing an update's
 * old files while the screen's Python cleared them too - is reproduced with
 * plain files (Chaquopy itself does not run on the JVM), then shown gone
 * with the lock.
 */
class ProcessLockTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun other(vararg args: String): Process {
        // the child needs this module's classes, the tests' and Kotlin's
        val cp = listOf(ProcessLock::class.java, ProcessLockTest::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct().joinToString(File.pathSeparator)
        return ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
            "-cp", cp, "com.mgvictus.begia.ProcessLockChildKt", *args)
            .redirectErrorStream(true).redirectOutput(tmp.newFile()).start()
    }

    private fun await(f: File) {
        val until = System.currentTimeMillis() + 30_000
        while (!f.exists()) {
            check(System.currentTimeMillis() < until) { "no \"${f.name}\" from the other process" }
            Thread.sleep(10)
        }
    }

    /** The other process lists an update's old tree to clear it; this one
     *  clears the same tree right then, as the screen's Python did. */
    private fun clearTogether(locked: Boolean): Int {
        val dir = tmp.newFolder()
        val tree = File(dir, "AssetFinder")
        File(tree, "requirements/tzdata/zoneinfo/Africa").apply { mkdirs() }.let { africa ->
            listOf("Abidjan", "Accra", "Addis_Ababa").forEach { File(africa, it).writeText("TZif") }
        }
        val lock = File(dir, "python-start.lock")
        // unlocked, the other waits for our "go" (long enough to never miss
        // it); locked, the "go" cannot come before it is done, so it waits briefly
        val child = other("clear", lock.path, tree.path, if (locked) "locked" else "unlocked",
            if (locked) "1500" else "20000")
        await(File(dir, "listed"))
        val ours = { tree.deleteRecursively() }
        if (locked) ProcessLock.holding(lock, 10_000, ours) else ours()
        File(dir, "go").writeText("")
        assertTrue("the other process hung", child.waitFor(30, TimeUnit.SECONDS))
        return child.exitValue()
    }

    @Test
    fun twoProcessesClearingOneTreeTripOverEachOther() {
        // the S23: a file the recorder had listed was gone when it came to delete it
        assertEquals(3, clearTogether(locked = false))
    }

    @Test
    fun withTheLockTheSecondWaitsItsTurn() {
        assertEquals(0, clearTogether(locked = true))
    }

    @Test
    fun threadsOfOneProcessTakeTurns() {
        // a JVM may hold a file's lock only once: without the monitor the
        // second thread would get OverlappingFileLockException
        val lock = File(tmp.root, "python-start.lock")
        var inside = 0
        var most = 0
        val threads = (1..4).map {
            Thread {
                repeat(5) {
                    ProcessLock.holding(lock, 10_000) {
                        inside++
                        most = maxOf(most, inside)
                        Thread.sleep(5)
                        inside--
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1, most)
    }

    @Test
    fun aLockNeverFreedStillLetsBegiaStart() {
        val lock = File(tmp.root, "python-start.lock")
        val child = other("hold", lock.path, "8000")
        try {
            await(File(tmp.root, "held"))
            val t0 = System.currentTimeMillis()
            val ran = ProcessLock.holding(lock, 400) { true }
            val waited = System.currentTimeMillis() - t0
            assertTrue(ran)
            assertTrue("waited $waited ms", waited in 400..5_000)
        } finally {
            child.destroy()
            child.waitFor(10, TimeUnit.SECONDS)
        }
    }
}
