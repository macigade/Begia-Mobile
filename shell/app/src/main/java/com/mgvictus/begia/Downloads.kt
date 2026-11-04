package com.mgvictus.begia

import java.net.URLDecoder

/**
 * What the page downloads - a CSV export, a .signalSet - saved to the phone's
 * Downloads (MainActivity.saveDownload). A WebView drops a download that no
 * one takes, so before shell 3.9 none of them reached a phone. The part a
 * JVM test can run: the name the server gave the file, and the type it is
 * kept under.
 */
object Downloads {
    private val STAR = Regex("""filename\*\s*=\s*([\w-]*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
    private val PLAIN = Regex("""filename\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^;]+))""", RegexOption.IGNORE_CASE)

    /** Content-Disposition's filename* (RFC 5987: the real name, whatever its
     *  letters) or its filename, else the address's last part - never a path. */
    fun fileName(disposition: String?, url: String): String {
        val cd = disposition.orEmpty()
        val star = STAR.find(cd)?.let { m ->
            try {
                URLDecoder.decode(m.groupValues[2].trim().replace("+", "%2B"), m.groupValues[1].ifBlank { "UTF-8" })
            } catch (e: Exception) {
                null
            }
        }
        val plain = PLAIN.find(cd)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2].trim() }.replace("\\\"", "\"") }
        val last = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val name = (star ?: plain ?: last).substringAfterLast('/').substringAfterLast('\\')
            .filter { it >= ' ' }.trim().trimStart('.')
        return name.ifEmpty { "BEGIA download" }
    }

    /** The type it is kept under: its extension's, else a plain byte stream.
     *  Downloads may give a file the extension of the type it is told, and
     *  the server says JSON for a .signalSet: it would come out ".signalSet.json". */
    fun mimeFor(name: String, byExtension: (String) -> String? = {
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
    }): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return (if (ext.isEmpty()) null else byExtension(ext)) ?: "application/octet-stream"
    }
}
