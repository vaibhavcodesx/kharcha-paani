package com.vaibhav.kharchapaani.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.vaibhav.kharchapaani.domain.UpiLink
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import kotlin.math.floor

/** The QR modules for [text]; the 4-module quiet zone scanners need is included. */
internal fun qrMatrix(text: String): BitMatrix =
    QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )

/** Dark modules on white, whatever the theme: scanners read inverted codes badly. */
@Composable
fun QrCode(text: String, contentDescription: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val matrix = remember(text) { qrMatrix(text) }
    Canvas(
        modifier
            .size(size)
            .clip(KpShapes.medium)
            .background(Color.White)
            .semantics { this.contentDescription = contentDescription },
    ) {
        // Whole pixels per module, centred: fractional cells drew uneven, blurry modules that cheap
        // cameras misread.
        val cell = floor(this.size.width / matrix.width)
        val inset = floor((this.size.width - cell * matrix.width) / 2)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(inset + x * cell, inset + y * cell), Size(cell, cell))
            }
        }
    }
}

/**
 * What a friend scans to pay [payee], with the ID to copy for anyone who'd rather type it.
 *
 * [payingFromThisPhone] is true only when the person holding this phone is the one sending the
 * money; only then is there a "Pay in a UPI app" button. It used to show every time, so on your
 * own code ("they paid you", or your UPI card in settings) it opened a payment to yourself.
 */
@Composable
fun UpiPaymentCard(
    payee: UpiPayee,
    modifier: Modifier = Modifier,
    amount: Double? = null,
    note: String? = null,
    payingFromThisPhone: Boolean = false,
) {
    // Two codes, because both get used: the exact one settles the debt in a tap, and the open one
    // is what you want when the amount on screen isn't the amount being handed over — a part
    // payment, or a round number. Scanning the wrong one silently pays the wrong amount, so the
    // choice is explicit rather than a long-press.
    var exact by rememberSaveable(amount) { mutableStateOf(amount != null && amount > 0) }
    val shownAmount = amount?.takeIf { exact && it > 0 }
    val link = remember(payee, shownAmount, note) {
        // Never built from an unvalidated handle: for a groupmate this string arrived from their
        // phone, and it is about to become a payment link.
        UpiLink.buildOrNull(payee.upiId, payee.name, shownAmount, note)
    } ?: return
    val clipboard = LocalClipboardManager.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l)
            .testTag("upi_payment_card"),
    ) {
        Text(
            text = if (shownAmount != null) "Scan to pay ${Money.format(shownAmount)}" else "Scan to pay ${payee.name}",
            style = MaterialTheme.typography.titleMedium,
            color = OffWhiteText,
            textAlign = TextAlign.Center,
        )
        Text(
            "Any UPI app: GPay, PhonePe, Paytm, BHIM",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
        if (amount != null && amount > 0) {
            SegmentedControl(
                options = listOf(true, false),
                selected = exact,
                label = { if (it) Money.format(amount) else "Any amount" },
                onSelect = { exact = it },
                testTagFor = { if (it) "upi_qr_exact" else "upi_qr_open" },
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
            )
        }
        QrCode(
            text = link,
            contentDescription = "UPI QR code to pay ${payee.name}" + (shownAmount?.let { " ${Money.format(it)}" } ?: ", any amount"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.s)) {
            Text(payee.upiId, style = MoneyText.small, color = OffWhiteText, modifier = Modifier.testTag("upi_id_text"))
            TextButton(onClick = { clipboard.setText(AnnotatedString(payee.upiId)) }) {
                Text("Copy", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        }
        // A payment link to tap, for the phone that is sending the money. It always carries the
        // amount being recorded, whichever code is showing: "Any amount" is for someone *scanning*
        // a different figure, and that path works everywhere. An open-amount link launched as an
        // intent is what UPI apps disagree on (the NPCI spec makes the amount editable, but apps
        // treat intent launches their own way), and the person here has already typed what they're
        // paying, so there is nothing for the UPI app to ask.
        val payAmount = amount?.takeIf { it > 0 }
        val payLink = remember(payee, payAmount, note) {
            payAmount?.let { UpiLink.buildOrNull(payee.upiId, payee.name, it, note) }
        }
        val context = LocalContext.current
        if (payingFromThisPhone && payLink != null && payAmount != null) KpButton(
            label = "Pay ${Money.format(payAmount)} in a UPI app",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payLink))
                try {
                    // A chooser rather than the default handler: most phones have several UPI apps
                    // and only one of them has the money in it.
                    context.startActivity(Intent.createChooser(intent, "Pay with"))
                } catch (e: ActivityNotFoundException) {
                    // No UPI app at all. The QR and the copyable ID above still work, so this is a
                    // dead end rather than a failure worth interrupting anyone over.
                    Log.w("UpiPaymentCard", "No app handles upi://pay on this device", e)
                }
            },
            tier = KpButtonTier.Outline,
            icon = KpIcons.Send,
            fillWidth = true,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            testTag = "upi_deep_link_button",
        )
    }
}
