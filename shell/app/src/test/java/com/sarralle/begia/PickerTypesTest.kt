package com.sarralle.begia

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
        assertArrayEquals(arrayOf("*/*"), pickerTypes(arrayOf(".nothing"), table))
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
