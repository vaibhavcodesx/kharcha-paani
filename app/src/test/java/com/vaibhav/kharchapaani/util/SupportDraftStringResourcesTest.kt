package com.vaibhav.kharchapaani.util

import android.app.Activity
import android.content.Intent
import com.vaibhav.kharchapaani.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupportDraftStringResourcesTest {
    @Test fun `report draft preserves user text and the existing English template`() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertTrue(Support.reportAiAnswer(activity, "Asha's 50% question", "Answer's 25% text"))
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_SENDTO, intent.action)
            assertEquals("mailto:", intent.data.toString())
            assertArrayEquals(arrayOf(Support.EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
            assertEquals("Reporting an AI answer", intent.getStringExtra(Intent.EXTRA_SUBJECT))
            val body = intent.getStringExtra(Intent.EXTRA_TEXT)!!
            assertTrue(body.startsWith("What was wrong with it:\n\n\nThe question: Asha's 50% question\nThe answer:\nAnswer's 25% text\n\n—\n"))
            assertTrue(body.contains("Kharcha Paani ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        }
    }
}
