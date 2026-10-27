package com.vaibhav.kharchapaani.ask

import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.vaibhav.kharchapaani.MainActivity
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.components.qrBitmap
import java.io.File
import java.io.IOException

/**
 * Hands an ask to the share sheet (docs/designs/ask-to-pay.md): the message as text, and your payment
 * code as a picture when you have a UPI ID. WhatsApp doesn't make upi:// links tappable, so the code
 * travels as an image; the friend scans it, or picks it from the gallery in their UPI app.
 */
object AskShare {

    const val EXTRA_PERSON_ID = "com.vaibhav.kharchapaani.ask.PERSON_ID"
    const val EXTRA_AMOUNT = "com.vaibhav.kharchapaani.ask.AMOUNT"
    const val EXTRA_USER_ID = "com.vaibhav.kharchapaani.ask.USER_ID"

    private const val TAG = "AskShare"

    /**
     * Writes the code for [upiLink] to `cache/asks/ask-<personId>.png` and returns its content URI, or
     * null if it couldn't be written (the ask then goes as text alone). One file per friend,
     * overwritten each time, so the cache doesn't grow with every ask.
     */
    fun writeQr(context: Context, personId: Long, upiLink: String): Uri? = try {
        val dir = File(context.cacheDir, "asks").apply { mkdirs() }
        val file = File(dir, "ask-$personId.png")
        file.outputStream().use { qrBitmap(upiLink).compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't write the payment code; asking with text only", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Payment code isn't shareable; asking with text only", e)
        null
    }

    /** The share itself: the code as an image with [message] as its caption, or [message] alone. */
    fun sendIntent(context: Context, message: String, qr: Uri?): Intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, message)
        if (qr != null) {
            // An image type, not */*: messaging apps keep EXTRA_TEXT as the caption only for images.
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, qr)
            clipData = ClipData.newRawUri(context.getString(R.string.ask_payment_code_label), qr)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }

    /**
     * The share sheet for [send]. Picking an app tells [AskSentReceiver], which logs the ask; closing
     * the sheet logs nothing (eng review D5). Neither can see whether the message was then sent.
     *
     * This app is left out of its own sheet: it takes shared images (bills to scan), so it was listed,
     * and picking it logged an ask that never reached the friend.
     */
    fun chooser(context: Context, send: Intent, personId: Long, amount: Double, userId: String, title: String): Intent =
        Intent.createChooser(send, title, pickedCallback(context, personId, amount, userId).intentSender).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
        }

    /** What the share sheet calls when an app is picked: [AskSentReceiver], with this ask's details. */
    internal fun pickedCallback(context: Context, personId: Long, amount: Double, userId: String): PendingIntent {
        val picked = Intent(context, AskSentReceiver::class.java).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_PERSON_ID, personId)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_USER_ID, userId)
        }
        // Mutable so the system can add which app was picked; it's explicit, so nothing else can
        // fill it in. (Android 12+ requires saying so; before that every PendingIntent was mutable.)
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            personId.hashCode(),
            picked,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }
}
