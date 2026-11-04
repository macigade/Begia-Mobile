package com.mgvictus.begia

/** <input accept=".xlsx,.csv,..."> as the MIME types a document picker
 *  filters by: extensions mapped, MIME types kept - the bug report's
 *  picture input asks for any image, and the picker opens on images - and
 *  any file when nothing usable is left. A CSV has many names, plain text
 *  among them. An extension Android has no type for (.begia, .signalSet)
 *  is kept as a plain byte stream, which a picker filtering by the other
 *  types would hide: with one in the list, any file may be picked - the
 *  signal sets' ".signalSet,application/json" was JSON only before shell
 *  3.9. byExtension is Android's MimeTypeMap on a device; a JVM test,
 *  which has none, passes its own. */
fun pickerTypes(accept: Array<String>?,
                byExtension: (String) -> String? = {
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
                }): Array<String> {
    val out = linkedSetOf<String>()
    var unknown = false
    for (raw in accept.orEmpty().flatMap { it.split(',') }) {
        val a = raw.trim().lowercase()
        when {
            a.isEmpty() -> {}
            a.contains('/') -> out.add(a)
            a == ".csv" -> out.addAll(listOf("text/csv", "text/comma-separated-values", "application/csv",
                                             "application/vnd.ms-excel", "text/plain"))
            a == ".txt" -> out.add("text/plain")
            a == ".json" -> out.add("application/json")
            a == ".xlsx" -> out.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            a == ".xls" -> out.add("application/vnd.ms-excel")
            else -> byExtension(a.removePrefix(".")).let { if (it != null) out.add(it) else unknown = true }
        }
    }
    if (unknown) out.addAll(listOf("application/octet-stream", "*/*"))
    return if (out.isEmpty()) arrayOf("*/*") else out.toTypedArray()
}
