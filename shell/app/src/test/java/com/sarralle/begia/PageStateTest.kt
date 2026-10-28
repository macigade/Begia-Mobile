package com.sarralle.begia

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Back on the shell (PageState.back, MainActivity.onBackPressed) with the
 * tiles (desktop a0d9c36): BEGIA's page goes back a place at a time to the
 * home, and only the home asks whether to quit or to keep running.
 */
class PageStateTest {
    private val base = "http://127.0.0.1:8765"
    private val page = "$base/?splash_start=1761638400000"
    private fun back(url: String, canGoBack: Boolean, module: String) =
        PageState.back(url, base, canGoBack, module)

    @Test
    fun backWalksThePlacesBackToTheHome() {
        assertEquals(PageState.Back.HISTORY, back("$page#/io/inputs", true, "ioinputs"))
        assertEquals(PageState.Back.HISTORY, back("$page#/live", true, "monitor"))
        // a laptop's page in second-screen mode, the same
        val laptop = "https://192.168.1.20:8443"
        assertEquals(PageState.Back.HISTORY, PageState.back("$laptop/#/trials", laptop, true, "review"))
    }

    @Test
    fun theHomeAsksToLeaveWhateverIsBehindIt() {
        assertEquals(PageState.Back.LEAVE, back("$page#/", true, "home"))
        assertEquals(PageState.Back.LEAVE, back(page, false, "home"))
    }

    @Test
    fun aPlaceThePageWasLoadedOnGoesHome() {
        // a reload keeps the address and starts the history there
        assertEquals(PageState.Back.HOME, back("$page#/trials", false, "review"))
        // so does a page from before the tiles: it has no goHome() and
        // answers false, and Back asks to leave as it always did
        assertEquals(PageState.Back.HOME, back(page, false, "monitor"))
    }

    @Test
    fun aWebPageGoesBackToBegiaAndTheBootPageNever() {
        assertEquals(PageState.Back.HISTORY, back("https://www.example.com/help", true, "options"))
        assertEquals(PageState.Back.LEAVE, back("https://www.example.com/help", false, "options"))
        assertEquals(PageState.Back.LEAVE, back("file:///data/user/0/app/files/slots/x/ui/boot.html?theme=dark", true, "home"))
    }

    @Test
    fun theWatchTakesTheHomeForAnywhereButTheIoCheck() {
        try {
            PageState.module = "home"
            assertEquals("", PageState.watchMode())
            PageState.module = "ioinputs"
            assertEquals("iocheck", PageState.watchMode())
        } finally {
            PageState.module = ""
        }
    }
}
