package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLinkTest {

    @Test
    fun `a link round-trips to its code`() {
        assertEquals("kharchapaani://join/AB2CD3EF", InviteLink.linkFor("AB2CD3EF"))
        assertEquals("AB2CD3EF", InviteLink.parse(InviteLink.linkFor("AB2CD3EF")))
    }

    @Test
    fun `a code typed or pasted loosely is accepted`() {
        assertEquals("AB2CD3EF", InviteLink.parse("  ab2c-d3ef "))
        assertEquals("AB2CD3EF", InviteLink.parse("AB2C D3EF"))
        assertEquals("AB2CD3EF", InviteLink.parse("Join my Goa group on Kharcha Paani: KHARCHAPAANI://join/ab2cd3ef"))
        assertEquals("AB2CD3EF", InviteLink.parse("kharchapaani://join/AB2CD3EF see you there!"))
    }

    @Test
    fun `anything that can't be a server code is rejected`() {
        assertNull(InviteLink.parse(""))
        assertNull(InviteLink.parse("AB2CD3E"))
        assertNull(InviteLink.parse("AB2CD3EFG"))
        // 0, O, 1 and I are outside the alphabet.
        assertNull(InviteLink.parse("AB0CD3EF"))
        assertNull(InviteLink.parse("ABOCD3EF"))
        assertNull(InviteLink.parse("AB1CD3EF"))
        assertNull(InviteLink.parse("ABICD3EF"))
        assertNull(InviteLink.parse("https://example.com/join/AB2CD3EF?x=1"))
    }

    @Test
    fun `malformed schemes, hosts, and paths are rejected`() {
        val malformedLinks = listOf(
            "http://join/AB2CD3EF",
            "https://join/AB2CD3EF",
            "ftp://join/AB2CD3EF",
            "custom://join/AB2CD3EF",
            "kharchapaani://invite/AB2CD3EF",
            "kharchapaani://groups/AB2CD3EF",
            "kharchapaani://otherhost/AB2CD3EF",
            "kharchapaani://join",
            "kharchapaani://join/",
            "kharchapaani://join///",
            "kharchapaani://join/   ",
            "kharchapaani:/join/AB2CD3EF",
            "kharchapaani:///join/AB2CD3EF",
        )
        for (link in malformedLinks) {
            assertNull("Accepted malformed link: $link", InviteLink.parse(link))
        }
    }

    @Test
    fun `malformed codes with disallowed characters and punctuation are rejected`() {
        val disallowedInputs = listOf(
            // Ambiguous characters in various positions
            "0B2CD3EF", "AB2CD3E0", "ab0cd3ef",
            "OB2CD3EF", "AB2CD3EO", "abOcd3ef",
            "1B2CD3EF", "AB2CD3E1", "ab1cd3ef",
            "IB2CD3EF", "AB2CD3EI", "abIcd3ef",
            "lB2CD3EF", "AB2CD3El", "ab3lcd4e",
            // Punctuation and symbols
            "AB2C.3EF", "AB2C_3EF", "AB2C/3EF", "AB2C?3EF",
            "AB2C#3EF", "AB2C@3EF", "AB2C&3EF", "AB2C=3EF",
            "AB2C+3EF", "AB2C!3EF", "AB2C$3EF", "AB2C%3EF",
            "AB2C:3EF", "AB2C;3EF", "AB2C,3EF", "AB2C*3EF",
            // Control characters, null bytes, unicode, and emojis
            "AB2C\u00003E", "AB2C\u00073E",
            "AB2C🏖️3E", "AB2C🍕3E",
            "AB2C₹D3E", "AB2Cराम3E",
            // Whitespace and dash only inputs
            "   ", "\t\n", "--", "- - - -", "--------",
        )
        for (input in disallowedInputs) {
            assertNull("Accepted disallowed input: $input", InviteLink.parse(input))
        }
    }

    @Test
    fun `length boundary violations are strictly rejected`() {
        // Under-length (1 to 7)
        listOf("A", "AB", "ABC", "ABCD", "ABCDE", "ABCDEF", "ABCDEFG").forEach {
            assertNull("Accepted under-length code: $it", InviteLink.parse(it))
        }

        // Over-length (9 and beyond)
        listOf("ABCDEFGH2", "ABCDEFGH23", "AB2CD3EFAB2CD3EF", "A".repeat(100)).forEach {
            assertNull("Accepted over-length code: $it", InviteLink.parse(it))
        }
    }

    @Test
    fun `injection attempts and path traversals are rejected`() {
        val injections = listOf(
            "' OR '1'='1",
            "'; DROP TABLE groups; --",
            "\" OR 1=1 --",
            "<script>alert(1)</script>",
            "<svg onload=alert(1)>",
            "javascript:void(0)",
            "../../../../etc/passwd",
            "..\\..\\windows\\system32",
        )
        for (payload in injections) {
            assertNull("Accepted injection payload: $payload", InviteLink.parse(payload))
        }
    }

    @Test
    fun `isValid strictly validates 8-character codes over exact 32 symbol alphabet`() {
        val validAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        assertEquals(32, validAlphabet.length)

        // Every character in the alphabet repeated 8 times must be valid
        for (ch in validAlphabet) {
            val code = ch.toString().repeat(8)
            assertTrue("Expected valid code: $code", InviteLink.isValid(code))
        }

        // Mixed valid characters
        assertTrue(InviteLink.isValid("AB2CD3EF"))
        assertTrue(InviteLink.isValid("23456789"))
        assertTrue(InviteLink.isValid("WXYZ2345"))

        // Exact alphabet exclusions
        assertFalse(InviteLink.isValid("AB0CD3EF")) // 0
        assertFalse(InviteLink.isValid("ABOCD3EF")) // O
        assertFalse(InviteLink.isValid("AB1CD3EF")) // 1
        assertFalse(InviteLink.isValid("ABICD3EF")) // I
        assertFalse(InviteLink.isValid("ab2cd3ef")) // lowercase not accepted by raw isValid
        assertFalse(InviteLink.isValid("AB-CD3EF")) // dashes not accepted by raw isValid
        assertFalse(InviteLink.isValid("AB CD3EF")) // spaces not accepted by raw isValid
    }

}
