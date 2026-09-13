package com.vaibhav.kharchapaani.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNonceTest {

    @Test
    fun `hashed is the lowercase hex SHA-256 of raw`() {
        // FIPS 180-2 test vector for "abc".
        val nonce = GoogleNonce.fromRaw("abc")

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", nonce.hashed)
    }

    @Test
    fun `generated raw nonce is 32 random bytes as lowercase hex`() {
        val nonce = GoogleNonce.generate()

        assertTrue(nonce.raw, nonce.raw.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `each sign-in attempt gets a different nonce`() {
        assertNotEquals(GoogleNonce.generate().raw, GoogleNonce.generate().raw)
    }

    @Test
    fun `Google receives the hash and never the raw value`() {
        val nonce = GoogleNonce.generate()

        assertNotEquals(nonce.raw, nonce.hashed)
        assertEquals(GoogleNonce.fromRaw(nonce.raw).hashed, nonce.hashed)
    }
}
