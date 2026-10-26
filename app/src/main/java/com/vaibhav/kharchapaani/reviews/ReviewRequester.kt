package com.vaibhav.kharchapaani.reviews

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManager

/** Contracts for test-first review eligibility and the Play boundary. */
internal class ReviewEligibility(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    fun claim(): Boolean = false
}

internal class ReviewRequester(
    private val activity: Activity,
    private val manager: ReviewManager,
    private val eligibility: ReviewEligibility,
    private val canPrompt: () -> Boolean = { true },
) {
    fun request() {}
}
