package com.vaibhav.kharchapaani.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vaibhav.kharchapaani.IncomingIntents

/**
 * The taps both widgets offer. Each opens MainActivity, through the app's non-exported entry
 * (IncomingIntents.INTERNAL_ENTRY), with one of the actions it routes, so a signed-out tap still
 * lands on sign-in and then does the thing.
 */
internal object WidgetIntents {

    fun openApp(context: Context): PendingIntent = activity(context, REQUEST_OPEN, action = null)

    fun quickAdd(context: Context): PendingIntent =
        activity(context, REQUEST_ADD, SplitFlowWidgetProvider.ACTION_QUICK_ADD)

    fun voice(context: Context): PendingIntent =
        activity(context, REQUEST_VOICE, SplitFlowWidgetProvider.ACTION_VOICE_LOG)

    fun splits(context: Context): PendingIntent =
        activity(context, REQUEST_SPLITS, SplitFlowWidgetProvider.ACTION_VIEW_SPLITS)

    private fun activity(context: Context, requestCode: Int, action: String?): PendingIntent {
        val intent = IncomingIntents.internal(context, action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // Distinct per action, or PendingIntent would hand every button the same (first) intent.
    private const val REQUEST_OPEN = 0
    private const val REQUEST_ADD = 1
    private const val REQUEST_VOICE = 2
    private const val REQUEST_SPLITS = 3
}
