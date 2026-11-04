package com.vaibhav.kharchapaani.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a tap on the mic does, decided once. Audio never leaves the phone without consent. */
class VoiceModeTest {

    private fun choose(
        mic: Boolean = true, ai: Boolean = true, accepted: Boolean = true, online: Boolean = true, live: Boolean = true,
    ) = VoiceMode.choose(micAllowed = mic, aiAvailable = ai, aiNoticeAccepted = accepted, online = online, liveWordsAvailable = live)

    @Test
    fun `with the AI accepted and a connection, the audio is recorded for Gemini`() {
        assertEquals(VoiceMode.RecordForAi, choose())
    }

    @Test
    fun `audio never goes to the AI before its notice is accepted`() {
        assertEquals(VoiceMode.LiveWords, choose(accepted = false))
    }

    @Test
    fun `offline, or with no AI in the build, the live words are the answer`() {
        assertEquals(VoiceMode.LiveWords, choose(online = false))
        assertEquals(VoiceMode.LiveWords, choose(ai = false))
    }

    @Test
    fun `with no recogniser on the phone the system dialog is the last resort`() {
        assertEquals(VoiceMode.SystemDialog, choose(online = false, live = false))
        assertEquals(VoiceMode.SystemDialog, choose(ai = false, live = false))
    }

    @Test
    fun `without the microphone permission only the system dialog works, whatever else is true`() {
        assertEquals(VoiceMode.SystemDialog, choose(mic = false))
        assertEquals(VoiceMode.SystemDialog, choose(mic = false, live = false, online = false))
    }
}
