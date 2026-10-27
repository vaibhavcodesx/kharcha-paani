package com.vaibhav.kharchapaani.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.vaibhav.kharchapaani.R

/**
 * The main widget (4x2): the month's spend in the brand gradient, today's spend, the net balance,
 * and Log expense / voice / Splits. The class name stays as it is: widgets already placed on home
 * screens are bound to it.
 */
class SplitFlowWidgetProvider : KpWidgetProvider() {

    override val layoutId: Int = R.layout.widget_splitflow

    override fun RemoteViews.bind(context: Context, summary: WidgetSummary) {
        bindCard(context, summary)
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.vaibhav.kharchapaani.ACTION_QUICK_ADD"
        const val ACTION_VOICE_LOG = "com.vaibhav.kharchapaani.ACTION_VOICE_LOG"
        const val ACTION_VIEW_SPLITS = "com.vaibhav.kharchapaani.ACTION_VIEW_SPLITS"

        /** Home's hero is 48sp; a 4x2 card has room for about two-thirds of that. */
        private const val FIGURE_SP = 32f

        /** The card for [summary], exactly as the launcher gets it. Also what the tests render. */
        internal fun render(context: Context, summary: WidgetSummary): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_splitflow).apply { bindCard(context, summary) }

        private fun RemoteViews.bindCard(context: Context, summary: WidgetSummary) {
            setTextViewText(R.id.widget_net_owed_badge, summary.balance)
            setTextColor(R.id.widget_net_owed_badge, summary.balanceColor)

            setImageViewBitmap(R.id.widget_month_spent_figure, GradientFigure.render(context, summary.spent, FIGURE_SP))
            setContentDescription(R.id.widget_month_spent_figure, context.getString(R.string.widget_copy_spent_description, summary.spent))
            setTextViewText(R.id.widget_today, summary.today)
            setContentDescription(R.id.widget_today, context.getString(R.string.widget_copy_today_description, summary.today))

            setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openApp(context))
            setOnClickPendingIntent(R.id.widget_btn_add, WidgetIntents.quickAdd(context))
            setOnClickPendingIntent(R.id.widget_btn_voice, WidgetIntents.voice(context))
            setOnClickPendingIntent(R.id.widget_btn_splits, WidgetIntents.splits(context))
            // Signed out there are no splits to open; the card itself leads to sign-in.
            setViewVisibility(R.id.widget_btn_splits, if (summary.signedIn) View.VISIBLE else View.GONE)
        }

        /**
         * Redraws every placed Kharcha Paani widget, both kinds. Called after anything that changes
         * the figures (a save, a delete, a settle-up, signing in or out).
         */
        fun notifyWidgetUpdate(context: Context) {
            refresh(context, SplitFlowWidgetProvider::class.java)
            refresh(context, QuickAddWidgetProvider::class.java)
        }

        private fun refresh(context: Context, provider: Class<*>) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isEmpty()) return
                context.sendBroadcast(
                    Intent(context, provider).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            } catch (e: Exception) {
                Log.w("KpWidget", "Couldn't ask ${provider.simpleName} to refresh", e)
            }
        }
    }
}
