package com.vaibhav.kharchapaani.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What both widgets share: load the figures once, then draw every placed copy.
 *
 * goAsync keeps the broadcast alive until the update is drawn. The old provider launched its work
 * and returned at once, which lets Android kill the process first, so a placed widget could sit on
 * its initial layout. The timeout keeps it inside the ten seconds a receiver is given.
 */
abstract class KpWidgetProvider : AppWidgetProvider() {

    protected abstract val layoutId: Int

    protected abstract fun RemoteViews.bind(context: Context, summary: WidgetSummary)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                val summary = withTimeoutOrNull(LOAD_TIMEOUT_MS) { WidgetData.load(context) }
                if (summary == null) {
                    Log.w(TAG, "${javaClass.simpleName}: figures didn't load in time; keeping what's shown")
                    return@launch
                }
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, layoutId).apply { bind(context, summary) }
                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                Log.w(TAG, "${javaClass.simpleName}: couldn't update", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "KpWidget"
        const val LOAD_TIMEOUT_MS = 8_000L
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}
