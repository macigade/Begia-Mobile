package com.sarralle.begia

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * One holder at a time, across the app's processes and within each: a lock
 * on a file (the kernel drops it if its process dies, so a killed recorder
 * leaves none behind) and, since a JVM may hold a file's lock only once, a
 * monitor for the threads of this one. Plain JVM, so ProcessLockTest runs
 * it in two real processes.
 */
object ProcessLock {
    private val inProcess = Any()

    /** [body] while holding [file]'s lock. After [waitMs] without it, [body]
     *  runs anyway: a lock that cannot be had must not keep BEGIA from
     *  starting, and running unlocked is only how it always ran. */
    fun <T> holding(file: File, waitMs: Long, body: () -> T): T = synchronized(inProcess) {
        RandomAccessFile(file, "rw").use { raf ->
            val lock = acquire(raf.channel, waitMs)
            try {
                body()
            } finally {
                lock?.release()
            }
        }
    }

    private fun acquire(channel: FileChannel, waitMs: Long): FileLock? {
        val until = System.currentTimeMillis() + waitMs
        while (true) {
            channel.tryLock()?.let { return it }
            if (System.currentTimeMillis() >= until) return null
            Thread.sleep(25)
        }
    }
}
