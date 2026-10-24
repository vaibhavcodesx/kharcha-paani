package com.vaibhav.kharchapaani.data.export

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The FileProvider hands out read access to whatever file_paths.xml lists, to any app a share is
 * granted to. Only three things are ever shared: CSV exports and backups (cache/exports, see
 * MainViewModel.exportCsv), the payment code sent with an ask-to-pay (cache/asks, see
 * ask/AskShare.writeQr), and the file the system camera writes a bill photo into (cache/camera,
 * see MainActivity's takeBillPhoto). Those are the only roots it may list: kept bill photos
 * (filesDir/receipts) never are.
 *
 * This reads the configuration rather than calling FileProvider.getUriForFile: under Robolectric the
 * provider resolves no roots at all, so every "can't be shared" check would pass for the wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileProviderScopeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun roots(): List<Pair<String, String?>> {
        val parser = context.resources.getXml(R.xml.file_paths)
        val roots = mutableListOf<Pair<String, String?>>()
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType == XmlResourceParser.START_TAG && parser.name != "paths") {
                roots += parser.name to parser.getAttributeValue(null, "path")
            }
        }
        return roots
    }

    @Test
    fun `only the exports, asks and camera folders in the cache can be shared`() {
        assertEquals(listOf("cache-path" to "exports/", "cache-path" to "asks/", "cache-path" to "camera/"), roots())
    }
}
