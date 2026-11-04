package com.mgvictus.begia

import org.json.JSONObject

/**
 * A signal set as a file, <name>.signalSet (desktop 166b082, PROJECT-DESIGN
 * 4 and 6): JSON whose "begia" says "signal-set". Opened from Files or
 * Downloads, or sent from the share sheet, it arrives as any JSON file does -
 * a licence too is one - and is told apart here. The page's own openSetFile
 * then imports it, asking Keep both or Replace when its name is taken, as it
 * does for a file picked on the page.
 */
object SignalSetFile {
    fun isSignalSet(text: String): Boolean =
        try { JSONObject(text).optString("begia") == "signal-set" } catch (e: Exception) { false }

    /** The script that hands it to the page: openSetFile with a File made of
     *  it, on the Signals place, where the sets are. It answers true when the
     *  page has openSetFile; a page from before the files answers false. */
    fun openScript(text: String, name: String): String =
        "(function(t,n){if(typeof openSetFile!=='function')return false;" +
        "try{if(typeof setModule==='function')setModule('signals')}catch(e){}" +
        "openSetFile(new File([t],n,{type:'application/json'}));return true})(" +
        JSONObject.quote(text) + "," + JSONObject.quote(name) + ")"
}
