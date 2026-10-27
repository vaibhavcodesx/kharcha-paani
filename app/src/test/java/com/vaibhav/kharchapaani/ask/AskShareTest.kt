package com.vaibhav.kharchapaani.ask

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.vaibhav.kharchapaani.MainActivity
import com.vaibhav.kharchapaani.domain.UpiLink
import com.vaibhav.kharchapaani.ui.components.qrBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import androidx.core.content.FileProvider
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AskShareTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val link = UpiLink.build("lal@okaxis", "Lal", 340.0, "Kharcha Paani hisaab")

    private fun decode(bitmap: android.graphics.Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun `the shared code scans back to the payment link for the asked amount`() {
        val bitmap = qrBitmap(link)

        assertEquals(link, decode(bitmap))
        // Dark on white, quiet zone included: the corner is white.
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(0, 0))
    }

    @Test
    fun `the code is written once per friend and shared through our file provider`() {
        // Surfaces a provider misconfiguration as the failure itself, rather than as a null URI.
        // (FileProvider matches roots with '/', so this only passes where paths use it: CI, not Windows.)
        val probe = File(context.cacheDir, "asks/probe.png").apply { parentFile?.mkdirs(); writeText("x") }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", probe)

        val first = AskShare.writeQr(context, personId = 7, upiLink = link)
        val again = AskShare.writeQr(context, personId = 7, upiLink = link)

        assertNotNull(first)
        assertEquals("${context.packageName}.fileprovider", first!!.authority)
        assertEquals("one file per friend, overwritten", first, again)
        context.contentResolver.openInputStream(first)!!.use { stream ->
            assertEquals(link, decode(BitmapFactory.decodeStream(stream)))
        }
    }

    @Test
    fun `with a code, the share is an image with the message as its caption`() {
        val uri = Uri.parse("content://${context.packageName}.fileprovider/asks/ask-7.png")

        val send = AskShare.sendIntent(context, "Hey Aaru", uri)

        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertEquals("Payment code", send.clipData!!.description.label.toString())
        assertEquals("Hey Aaru", send.getStringExtra(Intent.EXTRA_TEXT))
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `without a code, the share is the message alone`() {
        val send = AskShare.sendIntent(context, "Hey Aaru", null)

        assertEquals("text/plain", send.type)
        @Suppress("DEPRECATION")
        assertNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    @Test
    fun `picking an app is reported to our receiver with this ask's details`() {
        val callback = AskShare.pickedCallback(context, personId = 7, amount = 340.0, userId = "u")

        val saved = shadowOf(callback).savedIntent
        assertTrue(shadowOf(callback).isBroadcast)
        assertEquals(AskSentReceiver::class.java.name, saved.component?.className)
        assertEquals(7L, saved.getLongExtra(AskShare.EXTRA_PERSON_ID, 0))
        assertEquals(340.0, saved.getDoubleExtra(AskShare.EXTRA_AMOUNT, 0.0), 0.0)
        assertEquals("u", saved.getStringExtra(AskShare.EXTRA_USER_ID))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Mutable, or Android can't add which app was picked.
            assertTrue(shadowOf(callback).flags and android.app.PendingIntent.FLAG_MUTABLE != 0)
        }
        val chooser = AskShare.chooser(context, AskShare.sendIntent(context, "Hey", null), personId = 7, amount = 340.0, userId = "u", title = "Ask Aaru to pay")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    }

    @Test
    fun `the app isn't offered as somewhere to send its own ask`() {
        // It takes shared images (bills to scan), so the sheet listed it, and picking it logged an
        // ask that never reached the friend. Found on the release build's device check.
        val chooser = AskShare.chooser(context, AskShare.sendIntent(context, "Hey", null), personId = 7, amount = 340.0, userId = "u", title = "Ask Aaru to pay")

        @Suppress("DEPRECATION")
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)?.map { it as ComponentName }
        assertEquals(listOf(ComponentName(context, MainActivity::class.java)), excluded)
    }
}
