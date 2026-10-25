package com.vaibhav.kharchapaani.ui.shell

import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app is dark whatever the phone's theme, so the bars' icons must be light. With the phone in
 * light mode (most phones), the clock and battery came out dark on the near-black app: invisible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "notnight")
class SystemBarsTest {

    @Test
    fun `on a phone in light mode the bar icons stay light on the dark app`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        activity.applySystemBars()

        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse("status bar icons are dark on the dark app", bars.isAppearanceLightStatusBars)
        assertFalse("navigation bar icons are dark on the dark app", bars.isAppearanceLightNavigationBars)
    }
}
