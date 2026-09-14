package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
