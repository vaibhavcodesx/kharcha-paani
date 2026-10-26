package com.vaibhav.kharchapaani.reviews

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManager

/** Device-wide and deliberately conservative, including attempts Play declines to show. */
internal class ReviewEligibility(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val preferences = context.applicationContext.getSharedPreferences("play_review_preferences", Context.MODE_PRIVATE)
    init {
        if (!preferences.contains("first_seen")) preferences.edit().putLong("first_seen", clock()).apply()
    }

    @Synchronized fun claim(): Boolean {
        val now = clock()
        val first = preferences.getLong("first_seen", now)
        if (now < first || now - first < 7 * DAY) return false
        if (preferences.contains("last_attempt")) {
            val last = preferences.getLong("last_attempt", now)
            if (now < last || now - last < 180 * DAY) return false
        }
        preferences.edit().putLong("last_attempt", now).apply()
        return true
    }
    private companion object { const val DAY = 86_400_000L }
}

internal class ReviewRequester(
    private val activity: Activity,
    private val manager: ReviewManager,
    private val eligibility: ReviewEligibility,
    private val canPrompt: () -> Boolean = { true },
) {
    fun request() {
        if (activity.isFinishing || activity.isDestroyed || !canPrompt() || !eligibility.claim()) return
        runCatching {
            manager.requestReviewFlow().addOnSuccessListener { info ->
                if (!activity.isFinishing && !activity.isDestroyed && canPrompt()) {
                    runCatching { manager.launchReviewFlow(activity, info).addOnFailureListener { } }
                }
            }.addOnFailureListener { /* A Play error never becomes an app error or a retry loop. */ }
        }
    }
}
