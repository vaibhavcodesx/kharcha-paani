package com.vaibhav.kharchapaani.ui.format

import androidx.annotation.StringRes
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.domain.AiPrompt
import com.vaibhav.kharchapaani.domain.AiFailure

// Display text is separate from the instruction/voice strings sent to the AI.

@get:StringRes
val AiPrompt.labelResource: Int
    get() = when (this) {
        AiPrompt.MonthInReview -> R.string.ai_display_labelresource_monthinreview
        AiPrompt.Overspending -> R.string.ai_display_labelresource_overspending
        AiPrompt.WhatToChange -> R.string.ai_display_labelresource_whattochange
        AiPrompt.TripReview -> R.string.ai_display_labelresource_tripreview
    }

@get:StringRes
val AiPrompt.eyebrowResource: Int
    get() = when (this) {
        AiPrompt.MonthInReview -> R.string.ai_display_eyebrowresource_monthinreview
        AiPrompt.Overspending -> R.string.ai_display_eyebrowresource_overspending
        AiPrompt.WhatToChange -> R.string.ai_display_eyebrowresource_whattochange
        AiPrompt.TripReview -> R.string.ai_display_eyebrowresource_tripreview
    }

@get:StringRes
val AiFailure.answerMessageResource: Int
    get() = when (this) {
        AiFailure.NoKey -> R.string.ai_display_answermessageresource_nokey
        AiFailure.Offline -> R.string.ai_display_answermessageresource_offline
        AiFailure.Timeout -> R.string.ai_display_answermessageresource_timeout
        AiFailure.RateLimited -> R.string.ai_display_answermessageresource_ratelimited
        AiFailure.KeyRefused -> R.string.ai_display_answermessageresource_keyrefused
        AiFailure.ServerError -> R.string.ai_display_answermessageresource_servererror
        AiFailure.Blocked -> R.string.ai_display_answermessageresource_blocked
        AiFailure.Truncated -> R.string.ai_display_answermessageresource_truncated
        AiFailure.Unreadable -> R.string.ai_display_answermessageresource_unreadable
        AiFailure.DailyLimit -> R.string.ai_display_answermessageresource_dailylimit
        AiFailure.NeedsSignIn -> R.string.ai_display_answermessageresource_needssignin
    }
