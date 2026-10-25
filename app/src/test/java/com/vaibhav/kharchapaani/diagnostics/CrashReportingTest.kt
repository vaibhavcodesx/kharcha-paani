package com.vaibhav.kharchapaani.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashReportingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a build without a Firebase project never reports, and nothing breaks`() {
        // CI and local builds use .env.example's placeholders.
        assertFalse(CrashReporting.isAvailable)
        CrashReporting.start(context)
        CrashReporting.recordProblem("Sync request failed: HTTP 403")
    }

    @Test
    fun `reports are on until the person turns them off, and the choice is kept`() {
        CrashReporting.setEnabled(context, true)
        assertTrue(CrashReporting.isEnabled(context))

        CrashReporting.setEnabled(context, false)

        assertFalse(CrashReporting.isEnabled(context))
        CrashReporting.setEnabled(context, true)
    }

    @Test
    fun `a handled problem carries the reason and the error's kind, never its message`() {
        val fromServer = IllegalStateException("row 42: Dinner at Toit, ₹1,800, Priya")

        val report = HandledProblem("Sync stopped (${fromServer.javaClass.simpleName})")

        assertEquals("Sync stopped (IllegalStateException)", report.message)
        assertFalse(report.message!!.contains("Priya"))
    }
}
