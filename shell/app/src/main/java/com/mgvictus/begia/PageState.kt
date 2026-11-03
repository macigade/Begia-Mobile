package com.sarralle.begia

/**
 * What the page on this phone is showing, as far as other parts of the app
 * need to know: the module (markModule calls BegiaShell.noteModule). The
 * watch's relay reads it to follow the phone into the I/O check - in
 * second-screen mode too, since the page is still this phone's - and Back
 * reads it to know the tiles' home, "home" (desktop a0d9c36), which the
 * watch takes as anywhere but the I/O check.
 */
object PageState {
    @Volatile var module: String = ""

    /** The watch's mode for the page's module: "iocheck" in the I/O check
     *  (its check tab or its inputs tab), else "". */
    fun watchMode(): String = if (module == "iocheck" || module == "ioinputs") "iocheck" else ""

    /** What Back does (MainActivity.onBackPressed). */
    enum class Back {
        /** the WebView's goBack(): the place before, or BEGIA from a web page reached from it */
        HISTORY,
        /** the page's goHome() where it has the tiles, else LEAVE */
        HOME,
        /** askLeave(): quit, or keep running behind the other apps */
        LEAVE,
    }

    /** Back as an Android app's: it leaves from the first screen only. The
     *  tiles (desktop a0d9c36) give each place of BEGIA's page an address -
     *  #/live, #/io/inputs, #/ the home - and every move pushes one, so Back
     *  walks back the way the user came, a place at a time, and on the home
     *  asks whether to leave, whatever is behind it. The WebView's history
     *  holds only the page's own moves (cleared as the page loads), so
     *  canGoBack() there is a place to go back to; a page reloaded on a
     *  place has none and goes home. A page from before the tiles has
     *  neither, and asks at once, as it always did; so does the boot page. */
    fun back(url: String, base: String, canGoBack: Boolean, module: String): Back = when {
        !url.startsWith(base) -> if (url.startsWith("http") && canGoBack) Back.HISTORY else Back.LEAVE
        module == "home" -> Back.LEAVE
        canGoBack -> Back.HISTORY
        else -> Back.HOME
    }
}
