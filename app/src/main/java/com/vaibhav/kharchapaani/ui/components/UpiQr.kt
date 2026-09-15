package com.vaibhav.kharchapaani.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

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
        val cell = this.size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                // A hair over one cell, so anti-aliased edges don't leave white seams between modules.
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** What a friend scans to pay this account, with the ID to copy for anyone who'd rather type it. */
@Composable
fun UpiPaymentCard(payee: UpiPayee, modifier: Modifier = Modifier, amount: Double? = null, note: String? = null) {
    val link = remember(payee, amount, note) { UpiLink.build(payee.upiId, payee.name, amount, note) }
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
            text = if (amount != null && amount > 0) "Scan to pay ${Money.format(amount)}" else "Scan to pay ${payee.name}",
            style = MaterialTheme.typography.titleMedium,
            color = OffWhiteText,
            textAlign = TextAlign.Center,
        )
        Text(
            "Any UPI app: GPay, PhonePe, Paytm, BHIM",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
        QrCode(
            text = link,
            contentDescription = "UPI QR code to pay ${payee.name}" + (amount?.takeIf { it > 0 }?.let { " ${Money.format(it)}" } ?: ""),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.s)) {
            Text(payee.upiId, style = MoneyText.small, color = OffWhiteText, modifier = Modifier.testTag("upi_id_text"))
            TextButton(onClick = { clipboard.setText(AnnotatedString(payee.upiId)) }) {
                Text("Copy", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        }
    }
}
