package com.vaibhav.kharchapaani

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.IncomingIntents.Route
import com.vaibhav.kharchapaani.util.NotificationHelper
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which arriving intents MainActivity acts on (security audit, 2026-10-24). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IncomingIntentsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** What another app can send: MainActivity by name, with any action. */
    private fun fromAnotherApp(action: String) = Intent(action).setComponent(ComponentName(context, MainActivity::class.java))

    @Test
    fun `the app's own actions count through its own entry`() {
        assertEquals(Route.QuickAdd, IncomingIntents.route(IncomingIntents.internal(context, SplitFlowWidgetProvider.ACTION_QUICK_ADD)))
        assertEquals(Route.VoiceLog, IncomingIntents.route(IncomingIntents.internal(context, SplitFlowWidgetProvider.ACTION_VOICE_LOG)))
        assertEquals(Route.ViewSplits, IncomingIntents.route(IncomingIntents.internal(context, SplitFlowWidgetProvider.ACTION_VIEW_SPLITS)))
        assertEquals(
            Route.AnswerSettlement(42L, userId = "alice"),
            IncomingIntents.route(
                IncomingIntents.internal(context, NotificationHelper.ACTION_ANSWER_SETTLEMENT)
                    .putExtra(NotificationHelper.EXTRA_SETTLEMENT_ID, 42L)
                    .putExtra(NotificationHelper.EXTRA_USER_ID, "alice"),
            ),
        )
    }

    @Test
    fun `the same actions sent straight to MainActivity by another app are ignored`() {
        listOf(
            SplitFlowWidgetProvider.ACTION_QUICK_ADD,
            SplitFlowWidgetProvider.ACTION_VOICE_LOG,
            SplitFlowWidgetProvider.ACTION_VIEW_SPLITS,
            NotificationHelper.ACTION_ANSWER_SETTLEMENT,
        ).forEach { assertNull(it, IncomingIntents.route(fromAnotherApp(it))) }
    }

    @Test
    fun `shared text needs a text type`() {
        val text = "Paid Rs 20 to Chai Point"
        assertEquals(Route.SharedText(text), IncomingIntents.route(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)))
        // No type at all used to go straight to Gemini.
        assertNull(IncomingIntents.route(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)))
        assertNull(IncomingIntents.route(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "  ")))
    }

    @Test
    fun `a shared picture or PDF has to be a content URI`() {
        val granted = Uri.parse("content://com.whatsapp.provider/media/bill.jpg")
        assertEquals(Route.SharedImage(granted), IncomingIntents.route(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, granted)))
        val pdf = Uri.parse("content://com.android.providers.downloads.documents/document/7")
        assertEquals(Route.SharedPdf(pdf), IncomingIntents.route(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, pdf)))

        // This app's own database, by path: never.
        val ownFile = Uri.parse("file:///data/data/com.vaibhav.kharchapaani/databases/ledger.db")
        assertNull(IncomingIntents.route(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, ownFile)))
        assertNull(IncomingIntents.route(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, ownFile)))
    }

    @Test
    fun `invite links stay open to everyone`() {
        val link = "kharchapaani://join/ABCD2345"
        assertEquals(Route.Join(link), IncomingIntents.route(Intent(Intent.ACTION_VIEW, Uri.parse(link))))
    }

    @Test
    fun `the internal entry is in the manifest and not exported`() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, IncomingIntents.INTERNAL_ENTRY), PackageManager.GET_META_DATA)
        assertFalse(info.exported)
        assertEquals(MainActivity::class.java.name, info.targetActivity)
    }
}
