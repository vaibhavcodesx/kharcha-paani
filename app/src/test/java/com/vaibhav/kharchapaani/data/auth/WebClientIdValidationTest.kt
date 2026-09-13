package com.vaibhav.kharchapaani.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebClientIdValidationTest {

    @Test
    fun `real web client id is usable`() {
        assertTrue(AuthRepository.isUsableWebClientId("1234567890-abc123def456.apps.googleusercontent.com"))
    }

    @Test
    fun `env example placeholder is not usable`() {
        assertFalse(AuthRepository.isUsableWebClientId("YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"))
    }

    @Test
    fun `blank or malformed values are not usable`() {
        assertFalse(AuthRepository.isUsableWebClientId(""))
        assertFalse(AuthRepository.isUsableWebClientId("not-a-client-id"))
    }
}
