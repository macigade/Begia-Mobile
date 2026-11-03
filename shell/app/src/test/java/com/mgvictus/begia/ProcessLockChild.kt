package com.sarralle.begia

import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.system.exitProcess

/**
 * The other process of ProcessLockTest, a JVM of its own:
 *  hold LOCK MS - holds LOCK for MS ms, saying "held" beside it first;
 *  clear LOCK TREE locked|unlocked GO_MS - clears TREE the way Chaquopy
 *  clears an update's old files (rmtree: list, then delete what was
 *  listed), waiting up to GO_MS between the two for the test's "go". It
 *  exits 3 when a file it listed was already gone - the S23's
 *  "FileNotFoundError: 'Accra'".
 */
fun main(args: Array<String>) {
    val lock = File(args[1])
    val signals = lock.parentFile!!
    when (args[0]) {
        "hold" -> ProcessLock.holding(lock, 10_000) {
            File(signals, "held").writeText("")
            Thread.sleep(args[2].toLong())
        }
        "clear" -> {
            val clear = {
                val listed = File(args[2]).walkBottomUp().toList()
                File(signals, "listed").writeText("")
                val until = System.currentTimeMillis() + args[4].toLong()
                while (!File(signals, "go").exists() && System.currentTimeMillis() < until) Thread.sleep(10)
                try {
                    listed.forEach { Files.delete(it.toPath()) }
                } catch (e: NoSuchFileException) {
                    exitProcess(3)
                }
            }
            if (args[3] == "locked") ProcessLock.holding(lock, 10_000, clear) else clear()
        }
    }
    exitProcess(0)
}
