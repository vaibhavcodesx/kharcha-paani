package com.vaibhav.kharchapaani

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import com.vaibhav.kharchapaani.domain.InviteLink
import com.vaibhav.kharchapaani.util.NotificationHelper
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider

/**
 * What an intent arriving at MainActivity asks for, and whether to believe it.
 *
 * MainActivity is exported (it's the launcher and the share target), so any app can send it any
 * intent. It used to honour the widgets' and notifications' own actions from anyone, and to send
 * shared text with no type, and streams of any scheme, to the parsers: Gemini calls on someone
 * else's say-so (security audit, 2026-10-24). Now:
 * - the app's own actions count only when they come in through [INTERNAL_ENTRY], a non-exported
 *   alias of MainActivity that only this app can start;
 * - shared text needs a `text/` type;
 * - a shared picture or PDF has to be an external `content://` URI, never our own FileProvider.
 */
object IncomingIntents {

    /** The alias in AndroidManifest.xml. Widgets and notifications open the app through it. */
    const val INTERNAL_ENTRY = "com.vaibhav.kharchapaani.InternalEntry"

    /** An intent for this app's own [action], through [INTERNAL_ENTRY]. */
    fun internal(context: Context, action: String?): Intent = Intent().setClassName(context, INTERNAL_ENTRY).setAction(action)

    sealed interface Route {
        data class Join(val link: String) : Route
        data class SharedImage(val uri: Uri) : Route
        data class SharedPdf(val uri: Uri) : Route
        data class SharedText(val text: String) : Route
        data object QuickAdd : Route
        data object VoiceLog : Route
        data object ViewSplits : Route
        /** [userId] is whose ledger [settlementId] is in; null from notifications posted before 1.5. */
        data class AnswerSettlement(val settlementId: Long?, val userId: String?) : Route
    }

    fun route(intent: Intent): Route? {
        val action = intent.action
        if (action == Intent.ACTION_VIEW && intent.data?.scheme == InviteLink.SCHEME) {
            // Public on purpose: invites arrive from WhatsApp and browsers. Nothing is joined until
            // the person confirms the preview.
            return Route.Join(intent.dataString.orEmpty())
        }
        if (action == Intent.ACTION_SEND) {
            val type = intent.type ?: return null
            return when {
                type.startsWith("image/") -> stream(intent)?.let(Route::SharedImage)
                type == "application/pdf" -> stream(intent)?.let(Route::SharedPdf)
                type.startsWith("text/") -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(Route::SharedText)
                else -> null
            }
        }
        if (intent.component?.className != INTERNAL_ENTRY) return null
        return when (action) {
            SplitFlowWidgetProvider.ACTION_QUICK_ADD -> Route.QuickAdd
            SplitFlowWidgetProvider.ACTION_VOICE_LOG -> Route.VoiceLog
            SplitFlowWidgetProvider.ACTION_VIEW_SPLITS -> Route.ViewSplits
            NotificationHelper.ACTION_ANSWER_SETTLEMENT ->
                Route.AnswerSettlement(
                    settlementId = intent.getLongExtra(NotificationHelper.EXTRA_SETTLEMENT_ID, -1L).takeIf { it > 0 },
                    userId = intent.getStringExtra(NotificationHelper.EXTRA_USER_ID),
                )
            else -> null
        }
    }

    private fun stream(intent: Intent): Uri? {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
        }
        // Another app may grant its own content, but cannot ask us to read our private files.
        // Android can prefix an authority with a user id (e.g. 0@provider); compare the provider
        // itself so that spelling cannot bypass the self-FileProvider check.
        return uri?.takeIf {
            val authority = it.authority?.substringAfterLast('@')
            it.scheme == ContentResolver.SCHEME_CONTENT && !authority.isNullOrBlank() &&
                !authority.equals("${BuildConfig.APPLICATION_ID}.fileprovider", ignoreCase = true)
        }
    }
}
