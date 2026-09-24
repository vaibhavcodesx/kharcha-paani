package com.vaibhav.kharchapaani.data.auth

/**
 * Tells a person closing Google's account picker apart from Google closing it for them.
 *
 * Credential Manager reports both as `GetCredentialCancellationException`. After someone dismisses
 * the bottom-sheet picker a few times, Google puts the app on a cooldown and every later request
 * comes straight back "cancelled" without drawing anything. The app treated that as the user
 * changing their mind and stayed quiet, so after signing out the button simply did nothing: no
 * picker, no message, no request ever reaching the server.
 *
 * A human cannot see a sheet and dismiss it in under [HUMAN_FLOOR_MS], and Google's cooldown says
 * so in its message, so either signal means nobody was asked.
 */
internal object PickerCancellation {

    const val HUMAN_FLOOR_MS = 1_200L

    private val systemMarkers = listOf("temporarily blocked", "too many canceled", "[16]", "reauth")

    fun wasSilent(elapsedMs: Long, message: String?): Boolean {
        if (elapsedMs < HUMAN_FLOOR_MS) return true
        val text = message?.lowercase() ?: return false
        return systemMarkers.any { it in text }
    }
}
