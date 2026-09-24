package com.vaibhav.kharchapaani.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerCancellationTest {

    @Test
    fun `a cancel faster than a person can react means nobody was shown anything`() {
        assertTrue(PickerCancellation.wasSilent(elapsedMs = 80, message = "activity is cancelled by the user."))
    }

    @Test
    fun `Google's cooldown message counts however long it took`() {
        assertTrue(
            PickerCancellation.wasSilent(
                elapsedMs = 5_000,
                message = "[16] Cannot display UI as the caller has been temporarily blocked due to too many canceled sign-in prompts.",
            )
        )
    }

    @Test
    fun `pressing Back on a picker you were looking at is a real dismissal, whatever the status code`() {
        // The exact message the 1.2.0 release build logged on an emulator, three seconds after the
        // picker appeared. Counting "[16]" as a system cancel made this say "Google didn't open the
        // account picker" to someone who had just closed it.
        assertFalse(PickerCancellation.wasSilent(elapsedMs = 3_000, message = "[16] Cancelled by user."))
    }

    @Test
    fun `a person who looked at the picker and closed it is left alone`() {
        assertFalse(PickerCancellation.wasSilent(elapsedMs = 4_000, message = "activity is cancelled by the user."))
        assertFalse(PickerCancellation.wasSilent(elapsedMs = 4_000, message = null))
    }

    @Test
    fun `Google refusing the app is recognised, and is not mistaken for either kind of cancel`() {
        // Logged verbatim by the 1.2.0 release build after an account was picked.
        val message = "[16] Account reauth failed."
        assertTrue(PickerCancellation.wasRefusedByGoogle(message))
        assertFalse(PickerCancellation.wasSilent(elapsedMs = 58_799, message = message))
        assertFalse(PickerCancellation.wasRefusedByGoogle("[16] Cancelled by user."))
    }
}
