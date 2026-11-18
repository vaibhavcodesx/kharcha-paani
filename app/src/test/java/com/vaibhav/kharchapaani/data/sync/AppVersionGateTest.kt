package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppVersionGateTest {

    @Test
    fun `no minimum means every version may sync`() {
        assertTrue(AppVersionGate.syncAllowed(installedVersionCode = 1, minVersionCode = 0))
        assertTrue(AppVersionGate.syncAllowed(installedVersionCode = 8, minVersionCode = -1))
    }

    @Test
    fun `a version below the minimum may not sync, at or above may`() {
        assertFalse(AppVersionGate.syncAllowed(installedVersionCode = 7, minVersionCode = 8))
        assertTrue(AppVersionGate.syncAllowed(installedVersionCode = 8, minVersionCode = 8))
        assertTrue(AppVersionGate.syncAllowed(installedVersionCode = 9, minVersionCode = 8))
    }

    @Test
    fun `nothing cached means no update is required`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_version_gate", Context.MODE_PRIVATE).edit().clear().commit()

        assertEquals(0, AppVersionGate.cachedMinVersion(context))
        assertFalse(AppVersionGate.updateRequired(context, installedVersionCode = 1))
    }

    @Test
    fun `a cached minimum above the installed version requires an update`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_version_gate", Context.MODE_PRIVATE).edit()
            .putInt("min_version_code", 9).commit()

        assertTrue(AppVersionGate.updateRequired(context, installedVersionCode = 8))
        assertFalse(AppVersionGate.updateRequired(context, installedVersionCode = 9))
    }

    @Test
    fun `the server row decodes, and missing numbers mean no minimum`() {
        val json = Json { ignoreUnknownKeys = true }
        val row = json.decodeFromString<RemoteAppConfig>(
            """{"id":true,"min_version_code":8,"recommended_version_code":9,"updated_at":"2026-11-19T00:00:00Z"}"""
        )
        assertEquals(RemoteAppConfig(minVersionCode = 8, recommendedVersionCode = 9), row)
        assertEquals(RemoteAppConfig(), json.decodeFromString<RemoteAppConfig>("{}"))
    }
}
