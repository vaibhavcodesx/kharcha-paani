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
)
