package com.sarralle.begia

/**
 * What the page on this phone is showing, as far as other parts of the app
 * need to know: the module (markModule calls BegiaShell.noteModule). The
 * watch's relay reads it to follow the phone into the I/O check - in
 * second-screen mode too, since the page is still this phone's.
 */
object PageState {
    @Volatile var module: String = ""

    /** The watch's mode for the page's module: "iocheck" in the I/O check
     *  (its check tab or its inputs tab), else "". */
    fun watchMode(): String = if (module == "iocheck" || module == "ioinputs") "iocheck" else ""
}
