package com.vaibhav.kharchapaani.diagnostics

import android.os.StrictMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        val originalThread = StrictMode.getThreadPolicy()
        val originalVm = StrictMode.getVmPolicy()
        val sentinelThread = StrictMode.ThreadPolicy.Builder().permitAll().build()
        val sentinelVm = StrictMode.VmPolicy.Builder().build()
        try {
            StrictMode.setThreadPolicy(sentinelThread)
            StrictMode.setVmPolicy(sentinelVm)

            AppStrictMode.configure(isDebug = false, isRobolectric = false)

            assertEquals(sentinelThread.toString(), StrictMode.getThreadPolicy().toString())
            assertEquals(sentinelVm.toString(), StrictMode.getVmPolicy().toString())
        } finally {
            StrictMode.setThreadPolicy(originalThread)
            StrictMode.setVmPolicy(originalVm)
        }
    }

    @Test
    fun `when running under Robolectric configure skips strict mode`() {
        val originalThread = StrictMode.getThreadPolicy()
        val originalVm = StrictMode.getVmPolicy()
        val sentinelThread = StrictMode.ThreadPolicy.Builder().permitAll().build()
        val sentinelVm = StrictMode.VmPolicy.Builder().build()
        try {
            StrictMode.setThreadPolicy(sentinelThread)
            StrictMode.setVmPolicy(sentinelVm)

            AppStrictMode.configure(isDebug = true, isRobolectric = true)

            assertEquals(sentinelThread.toString(), StrictMode.getThreadPolicy().toString())
            assertEquals(sentinelVm.toString(), StrictMode.getVmPolicy().toString())
        } finally {
            StrictMode.setThreadPolicy(originalThread)
            StrictMode.setVmPolicy(originalVm)
        }
    }

    @Test
    fun `when debug mode and not robolectric strict mode policies are applied safely`() {
        val originalThread = StrictMode.getThreadPolicy()
        val originalVm = StrictMode.getVmPolicy()
        val initialThread = StrictMode.ThreadPolicy.Builder().permitAll().build()
        val initialVm = StrictMode.VmPolicy.Builder().build()
        try {
            StrictMode.setThreadPolicy(initialThread)
            StrictMode.setVmPolicy(initialVm)

            AppStrictMode.configure(isDebug = true, isRobolectric = false)

            val currentThread = StrictMode.getThreadPolicy()
            val currentVm = StrictMode.getVmPolicy()
            assertNotNull(currentThread)
            assertNotNull(currentVm)
            assertNotEquals("Thread policy should be updated from permitAll by enableDefaults", initialThread.toString(), currentThread.toString())
            assertNotEquals("VM policy should be updated from empty by enableDefaults", initialVm.toString(), currentVm.toString())
        } finally {
            StrictMode.setThreadPolicy(originalThread)
            StrictMode.setVmPolicy(originalVm)
        }
    }
}
