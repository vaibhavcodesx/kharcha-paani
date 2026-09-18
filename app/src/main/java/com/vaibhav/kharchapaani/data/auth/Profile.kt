package com.vaibhav.kharchapaani.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of `public.profiles`, created by the `on_auth_user_created` trigger on first sign-in. */
@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    /** Shown instead of initials; groupmates see it too. Null means initials. */
    @SerialName("avatar_emoji") val avatarEmoji: String? = null,
    /**
     * Where groupmates can pay this account. Readable only by this account and people who share a
     * group with it (profiles_select_self_or_groupmate), which is exactly the audience for a
     * payment handle — a UPI ID is not secret, but it is not public either.
     */
    @SerialName("upi_id") val upiId: String? = null,
)
