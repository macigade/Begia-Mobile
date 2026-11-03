package com.sarralle.begia

/** <input accept=".xlsx,.csv,..."> as the MIME types a document picker
 *  filters by: extensions mapped, MIME types kept - the bug report's
 *  picture input asks for any image, and the picker opens on images - and
 *  any file when nothing usable is left. A CSV has many names, plain text
 *  among them. byExtension is Android's MimeTypeMap on a device; a JVM test,
 *  which has none, passes its own. */
fun pickerTypes(accept: Array<String>?,
                byExtension: (String) -> String? = {
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
                }): Array<String> {
    val out = linkedSetOf<String>()
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
            else -> byExtension(a.removePrefix("."))?.let { out.add(it) }
        }
    }
    return if (out.isEmpty()) arrayOf("*/*") else out.toTypedArray()
}
