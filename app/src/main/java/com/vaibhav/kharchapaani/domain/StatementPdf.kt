package com.vaibhav.kharchapaani.domain

/**
 * Why a statement couldn't be read. One generic "couldn't read that" told the person nothing and
 * told us nothing either: an expired key, a slow network and a password-protected PDF all looked
 * the same, which is how a failing import went unnoticed for as long as it did.
 */
enum class StatementReadFailure {
    /** No Gemini key in this build. */
    NoKey,

    /** The picked file was empty, or isn't a PDF at all. */
    NotAPdf,

    /** The PDF is encrypted. Most Indian bank statements are, and Gemini can't open one. */
    PasswordProtected,

    /** Too big to send inline; the person needs to split it or export a shorter period. */
    TooLarge,

    /** The request didn't finish in time. A long statement is the one call slow enough to hit this. */
    Timeout,

    /** The network went away mid-call. */
    Offline,

    /** Gemini answered with an error: bad key, quota, rate limit, a PDF it wouldn't accept. */
    Rejected,

    /** The answer was cut off part-way, so the JSON doesn't parse. */
    Truncated,

    /** It read the document and found no transactions in it. */
    NothingFound,

    /** Anything else. */
    Unreadable,
}

/** What reading a statement produced: the parsed statement, or why there isn't one. */
sealed interface StatementReadResult {
    data class Success(val statement: com.vaibhav.kharchapaani.data.model.StatementParseResult) : StatementReadResult

    /** [detail] is for the log, never for the person. */
    data class Failed(val reason: StatementReadFailure, val detail: String? = null) : StatementReadResult
}

/**
 * The checks worth doing on the bytes before spending a round trip on them. Pure, so they're tested
 * without a network.
 */
object StatementPdf {

    /**
     * Gemini takes a document inline up to a 20 MB request. Base64 inflates by 4/3 and the prompt
     * and JSON envelope ride along, so the raw file has to stay comfortably under that.
     */
    const val MAX_BYTES: Int = 14 * 1024 * 1024

    private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)

    /** Null when the bytes are worth sending; otherwise why they aren't. */
    fun precheck(bytes: ByteArray): StatementReadFailure? = when {
        bytes.isEmpty() || !hasPdfHeader(bytes) -> StatementReadFailure.NotAPdf
        bytes.size > MAX_BYTES -> StatementReadFailure.TooLarge
        isEncrypted(bytes) -> StatementReadFailure.PasswordProtected
        else -> null
    }

    /** A PDF starts with "%PDF-", optionally after a little junk some generators leave in front. */
    fun hasPdfHeader(bytes: ByteArray): Boolean = indexOf(bytes, PDF_HEADER, limit = 1024) >= 0

    /**
     * An encrypted PDF names an /Encrypt dictionary in its trailer. Looking for the key anywhere in
     * the file is the cheap version: object streams are compressed, so a false positive would need
     * the literal bytes to survive uncompressed in page content, which is rare enough to accept —
     * and the cost of being wrong is one clear message instead of one vague one.
     */
    fun isEncrypted(bytes: ByteArray): Boolean =
        indexOf(bytes, "/Encrypt".toByteArray(Charsets.US_ASCII)) >= 0

    private fun indexOf(haystack: ByteArray, needle: ByteArray, limit: Int = haystack.size): Int {
        val last = minOf(limit, haystack.size) - needle.size
        outer@ for (i in 0..last) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
