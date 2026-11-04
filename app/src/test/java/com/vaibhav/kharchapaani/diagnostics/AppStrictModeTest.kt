package com.vaibhav.kharchapaani.diagnostics

import android.os.StrictMode
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppStrictModeTest {

    @Test
    fun `when not in debug mode configure does nothing`() {
        AppStrictMode.configure(isDebug = false, isRobolectric = false)
    }

    @Test
    fun `when running under Robolectric configure skips strict mode`() {
        AppStrictMode.configure(isDebug = true, isRobolectric = true)
    }

    @Test
    fun `when debug mode and not robolectric strict mode policies are applied safely`() {
        AppStrictMode.configure(isDebug = true, isRobolectric = false)
        assertNotNull(StrictMode.getThreadPolicy())
        assertNotNull(StrictMode.getVmPolicy())
    }
}
