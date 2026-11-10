package com.vaibhav.kharchapaani.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Where the privacy policy, terms and account deletion page live on the web (plan T1.2, O6). Built
 * from docs/legal by scripts/legal_site.py and published as described in site/README.md. Change
 * [BASE] here, and only here, if they move.
 */
object LegalLinks {

    const val BASE = "https://vaibhavcodesx.github.io/kharcha-paani-site/"
    const val PRIVACY_POLICY = BASE + "privacy.html"
    const val TERMS = BASE + "terms.html"
    const val DELETE_ACCOUNT = BASE + "delete-account.html"

    /** The page friends pick their items on (site/split.html; docs/designs/split-by-items.md). */
    const val SPLIT = BASE + "split.html"

    /**
     * A bill split's link. The key rides after "#", which a browser keeps to itself: it never
     * reaches the server that hosts the page, or its logs.
     */
    fun splitLink(token: String): String = "$SPLIT#$token"

    /** Opens [url] in the browser; false when the phone has none. */
    fun open(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
