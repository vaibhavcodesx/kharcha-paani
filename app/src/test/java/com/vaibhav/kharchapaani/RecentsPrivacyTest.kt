package com.vaibhav.kharchapaani

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivity

/** Observe the Activity API boundary while executing MainActivity's real onCreate. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [RecentsPrivacyTest.RecentsShadow::class])
class RecentsPrivacyTest {
    @Implements(Activity::class)
    class RecentsShadow : ShadowActivity() {
        var screenshotsEnabled = true

        @Implementation(minSdk = 33)
        protected fun setRecentsScreenshotEnabled(enabled: Boolean) {
            screenshotsEnabled = enabled
        }
    }

    @Test
    @Config(sdk = [33, 36])
    fun `launch disables Recents snapshots without preventing manual screenshots`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            assertFalse("MainActivity must disable Recents snapshots", Shadow.extract<RecentsShadow>(activity).screenshotsEnabled)
            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        } finally {
            controller.destroy()
        }
    }

    @Test
    @Config(sdk = [32])
    fun `older Android launches without calling the newer Recents API`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            assertEquals(0, controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        } finally {
            controller.destroy()
        }
    }
}
