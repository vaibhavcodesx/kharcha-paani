package com.vaibhav.kharchapaani.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.vaibhav.kharchapaani.R

/**
 * The compact quick-add (2x1): the brand button and "Quick add". Its tap is the bar's tap: Home's
 * capture line with the keyboard up. It shows no figures, so there is nothing to load and nothing to
 * keep fresh; it only has to be drawn once and bound to the tap.
 */
class QuickAddCompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, render(context))
    }

    companion object {
        /** The widget exactly as the launcher gets it. Also what the tests render. */
        internal fun render(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_quick_add_compact).apply {
                setOnClickPendingIntent(R.id.quick_compact_root, WidgetIntents.quickAdd(context))
            }
    }
}
