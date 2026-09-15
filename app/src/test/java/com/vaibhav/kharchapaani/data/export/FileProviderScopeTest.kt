package com.vaibhav.kharchapaani.data.export

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The app's FileProvider hands out read access to whatever its paths cover. Only CSV exports are ever
 * shared, so nothing else in the app's storage may be reachable through it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileProviderScopeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val authority = "${context.packageName}.fileprovider"

    private fun fileIn(dir: File, name: String) = File(dir, name).apply {
        parentFile?.mkdirs()
        writeText("x")
    }

    private fun assertNotShareable(file: File) {
        assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, authority, file) }
    }

    @Test
    fun `an export can be shared`() {
        val uri = FileProvider.getUriForFile(context, authority, fileIn(File(context.cacheDir, "exports"), "KharchaPaani_all.csv"))

        assertEquals(authority, uri.authority)
        assertEquals("KharchaPaani_all.csv", uri.lastPathSegment)
    }

    @Test
    fun `app files can't be shared`() {
        assertNotShareable(fileIn(context.filesDir, "session.json"))
    }

    @Test
    fun `other cache files can't be shared`() {
        assertNotShareable(fileIn(context.cacheDir, "image_cache/receipt.jpg"))
    }

    @Test
    fun `external storage can't be shared`() {
        val external = context.getExternalFilesDir(null) ?: return
        assertNotShareable(fileIn(external, "anything.txt"))
    }

    @Test
    fun `the ledger database can't be shared`() {
        assertNotShareable(fileIn(context.getDatabasePath("x").parentFile!!, "ledger.db"))
    }
}
