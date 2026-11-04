package com.mgvictus.begia

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the shell's file chooser (MainActivity.PageChrome.onShowFileChooser)
 * asks Android's document picker for, from an <input type="file">'s accept.
 * The bug report dialog's picture input (BUG-REPORTS-CONTRACT, 2026-10-27)
 * accepts any image: the picker opens on images, one or - with
 * "multiple" - several (OpenMultipleDocuments), and the WebView hands the
 * page what was picked.
 */
class PickerTypesTest {
    private val none: (String) -> String? = { null }

    @Test
    fun theBugReportsPicturesAreImages() {
        assertArrayEquals(arrayOf("image/*"), pickerTypes(arrayOf("image/*"), none))
        assertArrayEquals(arrayOf("image/*"), pickerTypes(arrayOf(" IMAGE/* "), none))
        assertArrayEquals(arrayOf("image/png", "image/jpeg"), pickerTypes(arrayOf("image/png,image/jpeg"), none))
    }

    @Test
    fun anExtensionIsAskedOfTheMimeTable() {
        val table: (String) -> String? = { if (it == "png") "image/png" else null }
        assertArrayEquals(arrayOf("image/png"), pickerTypes(arrayOf(".png"), table))
        // one Android has no type for is a plain byte stream, and any file may be picked
        assertArrayEquals(arrayOf("application/octet-stream", "*/*"), pickerTypes(arrayOf(".nothing"), table))
    }

    @Test
    fun aSignalSetFileCanBePicked() {
        // js/070's <input accept=".signalSet,application/json">: JSON alone hid
        // every .signalSet, kept as a byte stream by the phone (before shell 3.9)
        assertArrayEquals(arrayOf("application/json", "application/octet-stream", "*/*"),
                          pickerTypes(arrayOf(".signalSet,application/json"), none))
    }

    @Test
    fun theOldAcceptsStayAsTheyWere() {
        assertTrue(pickerTypes(arrayOf(".csv"), none).contains("text/csv"))
        assertArrayEquals(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                          pickerTypes(arrayOf(".xlsx"), none))
        assertArrayEquals(arrayOf("*/*"), pickerTypes(null, none))
        assertArrayEquals(arrayOf("*/*"), pickerTypes(arrayOf(""), none))
    }
}
