package com.vaibhav.kharchapaani.data.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Google embeds [hashed] in the ID token's `nonce` claim; Supabase receives [raw], re-hashes it,
 * and rejects the token if they differ. That binds each ID token to one sign-in attempt, so a
 * leaked token can't be replayed against our project.
 */
class GoogleNonce private constructor(val raw: String) {

    val hashed: String = sha256Hex(raw)

    companion object {
        private const val RAW_BYTES = 32
        private val random = SecureRandom()

        fun generate(): GoogleNonce {
            val bytes = ByteArray(RAW_BYTES).also(random::nextBytes)
            return GoogleNonce(bytes.toHex())
        }

        internal fun fromRaw(raw: String) = GoogleNonce(raw)

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
