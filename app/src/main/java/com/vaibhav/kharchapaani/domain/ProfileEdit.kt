package com.vaibhav.kharchapaani.domain

/** What someone can set on their profile: a name groupmates see, and an emoji instead of a photo. */
object ProfileEdit {

    const val MAX_NAME_LENGTH = 40

    /**
     * Friendly faces and things, picked to read at 30dp in a member stack. Null means initials. All are
     * Emoji 3.0 or older, so they draw on Android 7 (minSdk 24) without a downloaded emoji font; 🧋 and
     * 🥳 didn't.
     */
    val avatarEmoji: List<String> = listOf(
        "😎", "🤓", "😜", "😇", "🤠", "🙃", "😺", "🐶",
        "🦊", "🐼", "🐯", "🦁", "🐸", "🐵", "🦄", "🐙",
        "🌻", "🌈", "🔥", "⚡", "🍕", "🍩", "☕", "🍉",
        "⚽", "🏏", "🎸", "🎮", "✈️", "🏍️", "🚀", "🌍",
    )

    /** The name as it will be saved (spaces tidied), or null when it can't be saved. */
    fun cleanName(raw: String): String? {
        val tidy = raw.trim().replace(Regex("\\s+"), " ")
        return tidy.takeIf { it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_NAME_LENGTH }
    }

    /** Only the offered emoji are saved; anything else falls back to initials. */
    fun cleanEmoji(emoji: String?): String? = emoji?.takeIf { it in avatarEmoji }

    fun hasChanges(savedName: String?, savedEmoji: String?, name: String, emoji: String?): Boolean =
        cleanName(name) != savedName?.let(::cleanName) || cleanEmoji(emoji) != savedEmoji
}
