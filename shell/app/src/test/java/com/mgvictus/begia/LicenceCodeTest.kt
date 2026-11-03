package com.sarralle.begia

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The device code the shell computes natively must be the Licence Manager's
 * code_of() to the character, or no licence issued for this phone matches.
 * The expected values are begialic/format.py's (IBA-LICENCE-CODE), for the
 * same identities; runtime/begia_shell/licence.py is checked against the same
 * reference in tests/test_shell_licence.py.
 */
class LicenceCodeTest {
    @Test
    fun theCodeIsTheManagers() {
        assertEquals("5P6N-LSMS-VA4Q-S72U-YRTX", Licence.codeOf("and:0123456789abcdef"))
        assertEquals("TE3O-MMHB-MMMQ-GKLV-6PET", Licence.codeOf("and:ffffffffffffffff"))
        assertEquals("LP55-JNAD-VKAK-QEFA-OB44", Licence.codeOf("and:5a3c9e1b7d2f4a60"))
    }

    @Test
    fun noIdentityNoCode() {
        // a phone that will not say its ANDROID_ID has no code, rather than
        // the code of an empty "and:" every such phone would share
        assertEquals("", Licence.codeOf(""))
    }
}
