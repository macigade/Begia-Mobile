package com.mgvictus.begia

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A .signalSet opened with BEGIA arrives as any JSON file does - a licence
 * too is one - and is told apart by its "begia"; then it goes to the page's
 * openSetFile in a script (MainActivity.deliverSignalSet).
 */
class SignalSetFileTest {
    private val set = """{"begia": "signal-set", "v": 1, "name": "Press line", "made": "2026-11-04T10:00:00",
        "build": "v0.9-574-g166b082", "signals": [{"node_id": "ns=3;s=\"DB_Axis_1\".\"Press_A_bar\""}],
        "groups": [], "charts": {}, "plc": "sim"}"""

    @Test
    fun aSignalSetIsToldFromALicence() {
        assertTrue(SignalSetFile.isSignalSet(set))
        assertFalse(SignalSetFile.isSignalSet("""{"licence": {"id": "x"}, "key_id": "43fb53eea16a0945", "alg": "ed25519", "sig": "00"}"""))
        assertFalse(SignalSetFile.isSignalSet("""{"begia": "licence"}"""))
        assertFalse(SignalSetFile.isSignalSet("[1, 2]"))
        assertFalse(SignalSetFile.isSignalSet("{not json"))
    }

    @Test
    fun theScriptCarriesTheFileWhole() {
        val name = "Press \"line\" </script>.signalSet"
        val js = SignalSetFile.openScript(set, name)
        // one line, the text and the name as string literals the page reads back
        assertFalse(js.contains('\n'))
        assertTrue(js.contains(JSONObject.quote(set)))
        assertTrue(js.contains(JSONObject.quote(name)))
        assertFalse(js.contains("</script>"))
        assertTrue(js.startsWith("(function(t,n){if(typeof openSetFile!=='function')return false;"))
    }
}
