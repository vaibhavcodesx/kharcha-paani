package com.vaibhav.kharchapaani.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.vaibhav.kharchapaani.R

/**
 * The quick-add bar (4x1): Home's "What did you spend on?" on the home screen.
 *
 * Why this one: logging is what people open the app to do most, and it's the step the daily
 * reminder asks for. A bar that looks like the capture field is one tap to a new expense, and
 * today's spend beside it shows the log landed. Balances already have the 4x2 card.
 */
class QuickAddWidgetProvider : KpWidgetProvider() {

    override val layoutId: Int = R.layout.widget_quick_add

    override fun RemoteViews.bind(context: Context, summary: WidgetSummary) {
        bindBar(context, summary)
    }

    companion object {
        /** The bar for [summary], exactly as the launcher gets it. Also what the tests render. */
        internal fun render(context: Context, summary: WidgetSummary): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_quick_add).apply { bindBar(context, summary) }

        private fun RemoteViews.bindBar(context: Context, summary: WidgetSummary) {
            setTextViewText(R.id.quick_hint, summary.hint)
            setTextViewText(R.id.quick_today, summary.today)
            setContentDescription(R.id.quick_today_block, context.getString(R.string.widget_copy_today_description, summary.today))
            // Signed out, today means nothing; the bar leads to sign-in and says so.
            setViewVisibility(R.id.quick_today_block, if (summary.signedIn) View.VISIBLE else View.GONE)

            setOnClickPendingIntent(R.id.quick_root, WidgetIntents.quickAdd(context))
            setOnClickPendingIntent(R.id.quick_btn_voice, WidgetIntents.voice(context))
        }
    }
}
