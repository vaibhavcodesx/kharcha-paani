package com.vaibhav.kharchapaani.domain

/**
 * Invite codes (decision 5): 8 characters from the server's 32-symbol alphabet, shared as
 * `kharchapaani://join/CODE`. Parsing is forgiving about how a code was pasted or typed; anything
 * that can't be a valid code is rejected before it reaches the server.
 */
object InviteLink {

    const val SCHEME = "kharchapaani"
    const val HOST = "join"

    // Matches the server's check constraint: no 0/O or 1/I, so a code read aloud can't be misheard.
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LENGTH = 8
    private val LINK = Regex("$SCHEME://$HOST/([A-Za-z0-9-]+)", RegexOption.IGNORE_CASE)

    fun linkFor(code: String): String = "$SCHEME://$HOST/$code"

    /** Accepts the link, the bare code, or a shared message containing the link; case, spaces and dashes are ignored. */
    fun parse(input: String): String? {
        val raw = LINK.find(input)?.groupValues?.get(1) ?: input
        val code = raw.uppercase().filterNot { it.isWhitespace() || it == '-' }
        return code.takeIf(::isValid)
    }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
