package com.vaibhav.kharchapaani.voice

/** How a tap on the mic is answered. Decided once, when it's tapped. */
enum class VoiceMode {
    /** Record in the app and send the audio to Gemini: one step to a parsed entry. */
    RecordForAi,

    /** Android's speech recogniser, with the words appearing live; the text then goes the typed way. */
    LiveWords,

    /** Google's own dialog: needs no microphone permission here, and is the last resort. */
    SystemDialog;

    companion object {
        /**
         * Audio only leaves the phone once the person has accepted the AI notice, and only when the AI
         * is there and the phone is online. Without the microphone permission the system dialog is the
         * one thing that still works.
         */
        fun choose(
            micAllowed: Boolean,
            aiAvailable: Boolean,
            aiNoticeAccepted: Boolean,
            online: Boolean,
            liveWordsAvailable: Boolean,
        ): VoiceMode = when {
            !micAllowed -> SystemDialog
            aiAvailable && aiNoticeAccepted && online -> RecordForAi
            liveWordsAvailable -> LiveWords
            else -> SystemDialog
        }
    }
}

/** Whether there is a working connection right now, from the phone's own network state. */
internal fun isOnline(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
