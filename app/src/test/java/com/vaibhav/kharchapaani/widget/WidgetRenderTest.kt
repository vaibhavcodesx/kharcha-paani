package com.vaibhav.kharchapaani.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Both widgets, built by their providers and applied the way a launcher applies them. This is
 * what catches a layout a launcher would refuse (a view class RemoteViews doesn't allow, a bad
 * attribute) and a bind that points at the wrong id — none of which a unit test of the text sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun applied(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun `the card shows the month, today and the balance, with every button`() {
        val card = applied(SplitFlowWidgetProvider.render(context, WidgetSummary.of(context, 18420.25, 0.0, -415.5, todaySpent = 120.0)))

        assertEquals("You owe ₹415.50", card.text(R.id.widget_net_owed_badge))
        assertEquals(WidgetSummary.MONEY_OUT, card.findViewById<TextView>(R.id.widget_net_owed_badge).currentTextColor)
        assertEquals("₹120", card.text(R.id.widget_today))
        val figure = card.findViewById<ImageView>(R.id.widget_month_spent_figure)
        assertNotNull("the gradient figure is drawn", figure.drawable)
        assertEquals("Spent this month: ₹18,420.25", figure.contentDescription.toString())
        listOf(R.id.widget_btn_add, R.id.widget_btn_voice, R.id.widget_btn_splits).forEach {
            assertEquals(View.VISIBLE, card.findViewById<View>(it).visibility)
        }
    }

    @Test
    fun `signed out, the card shows no figures and no splits to open`() {
        val card = applied(SplitFlowWidgetProvider.render(context, WidgetSummary.signedOut(context)))

        assertEquals("Sign in", card.text(R.id.widget_net_owed_badge))
        assertEquals("—", card.text(R.id.widget_today))
        assertEquals(View.GONE, card.findViewById<View>(R.id.widget_btn_splits).visibility)
        assertEquals(View.VISIBLE, card.findViewById<View>(R.id.widget_btn_add).visibility)
    }

    @Test
    fun `the bar asks what you spent on and shows today`() {
        val bar = applied(QuickAddWidgetProvider.render(context, WidgetSummary.of(context, 0.0, 0.0, 0.0, todaySpent = 89.0)))

        assertEquals("What did you spend on?", bar.text(R.id.quick_hint))
        assertEquals("₹89", bar.text(R.id.quick_today))
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.quick_today_block).visibility)
    }

    @Test
    fun `signed out, the bar says to sign in and hides today`() {
        val bar = applied(QuickAddWidgetProvider.render(context, WidgetSummary.signedOut(context)))

        assertEquals("Sign in to start logging", bar.text(R.id.quick_hint))
        assertEquals(View.GONE, bar.findViewById<View>(R.id.quick_today_block).visibility)
    }
}
