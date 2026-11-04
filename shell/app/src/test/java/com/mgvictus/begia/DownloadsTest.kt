package com.mgvictus.begia

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The page's downloads saved to the phone's Downloads (shell 3.9): under the
 * name the server gave in its Content-Disposition, and as a type that keeps
 * that name as it is.
 */
class DownloadsTest {
    private val url = "http://127.0.0.1:8080/api/signalsets/file?name=boot%20set"

    @Test
    fun theRealNameComesFromFilenameStar() {
        // GET /api/signalsets/file: a plain name for an old browser, the real one after it
        assertEquals("boot set.signalSet", Downloads.fileName(
            "attachment; filename=\"boot set.signalSet\"; filename*=UTF-8''boot%20set.signalSet", url))
        assertEquals("Zwölf + eins.signalSet", Downloads.fileName(
            "attachment; filename=\"Zw_lf + eins.signalSet\"; filename*=UTF-8''Zw%C3%B6lf%20+%20eins.signalSet", url))
    }

    @Test
    fun aPlainNameAndNone() {
        assertEquals("signals.csv", Downloads.fileName("attachment; filename=signals.csv",
            "http://127.0.0.1:8080/api/signals/export.csv"))
        assertEquals("a \"quoted\" name.csv", Downloads.fileName("attachment; filename=\"a \\\"quoted\\\" name.csv\"", url))
        assertEquals("export.csv", Downloads.fileName(null, "http://127.0.0.1:8080/api/signals/export.csv?x=1#top"))
        assertEquals("BEGIA download", Downloads.fileName("attachment", "http://127.0.0.1:8080/"))
    }

    @Test
    fun neverAPath() {
        assertEquals("x.csv", Downloads.fileName("attachment; filename=\"../../x.csv\"", url))
        assertEquals("x.csv", Downloads.fileName("attachment; filename*=UTF-8''..%2F..%5Cx.csv", url))
        assertEquals("hidden.csv", Downloads.fileName("attachment; filename=\".hidden.csv\"", url))
    }

    @Test
    fun theTypeKeepsTheName() {
        val table: (String) -> String? = { mapOf("csv" to "text/csv", "json" to "application/json")[it] }
        assertEquals("text/csv", Downloads.mimeFor("signals.csv", table))
        // told JSON, Downloads could make it "boot set.signalSet.json"
        assertEquals("application/octet-stream", Downloads.mimeFor("boot set.signalSet", table))
        assertEquals("application/octet-stream", Downloads.mimeFor("no extension", table))
    }
}
