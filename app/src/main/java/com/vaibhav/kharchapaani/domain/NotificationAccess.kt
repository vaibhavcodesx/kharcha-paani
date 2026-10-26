package com.vaibhav.kharchapaani.domain

/**
 * Whether the app can show notifications, and if not, what would fix it (notifications plan N1).
 *
 * Android asks at most twice: after a second "Don't allow" the dialog stops appearing and only the
 * system settings page can turn them on. [shouldShowRationale] is true only between the first and the
 * second refusal, so the stored count of asks tells "never asked" from "refused for good". Asking
 * once more than needed only costs a dialog that doesn't appear; the count then sends the next tap to
 * settings.
 */
enum class NotificationAccess {
    /** They'll show. */
    Allowed,

    /** Android will still show its dialog: ask, after the app's own explanation. */
    CanAsk,

    /** Refused for good, or turned off in system settings: only the settings page can fix it. */
    OpenSettings;

    companion object {
        /** Android 13 (API 33) is where notifications became a runtime permission. */
        const val RUNTIME_PERMISSION_SDK = 33

        /** How many times Android shows its dialog before refusing for good. */
        const val MAX_SYSTEM_ASKS = 2

        fun of(sdk: Int, enabledForApp: Boolean, granted: Boolean, timesAsked: Int, shouldShowRationale: Boolean): NotificationAccess = when {
            enabledForApp -> Allowed
            // Before 13 there's nothing to ask for: they were turned off in settings.
            sdk < RUNTIME_PERMISSION_SDK -> OpenSettings
            // Granted but still off: turned off in settings after allowing.
            granted -> OpenSettings
            // Once: refused, or the dialog closed without a choice, which doesn't count against it.
            timesAsked < MAX_SYSTEM_ASKS -> CanAsk
            shouldShowRationale -> CanAsk
            else -> OpenSettings
        }
    }
}

/**
 * The moments the app offers to turn notifications on by itself, each right after the person did
 * something a notification would follow up on. Never on a first launch, before they've seen the app.
 */
enum class NotificationMoment { FirstOwnEntry, JoinedGroup, CreatedGroup, RecordedPayment }

object NotificationOffer {
    /** The app offers by itself at most this often, whatever the moments; settings can always ask. */
    const val MAX_OFFERS = 2

    /** Whether to show the explanation sheet at a moment: only when asking can still work. */
    fun shouldOffer(access: NotificationAccess, offersShown: Int): Boolean =
        access == NotificationAccess.CanAsk && offersShown < MAX_OFFERS
}

/**
 * Quiet hours: payment reminders found during them wait until they end, so nothing buzzes at 2 AM
 * because a groupmate settled up late. Minutes since midnight; a window may run past midnight
 * (22:30 to 08:00, the default).
 */
data class QuietHours(val enabled: Boolean = true, val startMinute: Int = DEFAULT_START, val endMinute: Int = DEFAULT_END) {

    fun isQuietAt(minuteOfDay: Int): Boolean {
        if (!enabled || startMinute == endMinute) return false
        return if (startMinute < endMinute) minuteOfDay in startMinute until endMinute
        else minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    companion object {
        const val DEFAULT_START = 22 * 60 + 30
        const val DEFAULT_END = 8 * 60
    }
}

/** What the person chose on the Notifications screen. Everything starts on. */
data class NotificationPrefs(
    val paymentsToConfirm: Boolean = true,
    val quietHours: QuietHours = QuietHours(),
)
