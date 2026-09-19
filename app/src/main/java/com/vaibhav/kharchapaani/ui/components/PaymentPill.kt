package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.domain.PaymentKind
import com.vaibhav.kharchapaani.domain.PaymentMethods
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray

/**
 * How a transaction was paid for, as a small outlined tag rather than a word in the subtitle.
 *
 * Nearly every row in this list said "PopUPI" in the same grey as the time beside it, so the
 * subtitle was the same sentence on every line and the one row that differed took reading to find.
 * The icon does that work without reading; the label is there for the cases the icon cannot tell
 * apart, like a debit card from a credit card.
 *
 * Outlined, not filled — it sits beside an amount, and a filled chip next to a number reads as a
 * second number.
 */
@Composable
fun PaymentPill(method: String, modifier: Modifier = Modifier) {
    if (method.isBlank()) return
    val kind = remember(method) { PaymentMethods.kindOf(method) }
    val label = remember(method) { PaymentMethods.shortLabel(method) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(KpShapes.small)
            .border(1.dp, SubMutedWarmGray.copy(alpha = 0.35f), KpShapes.small)
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .semantics { contentDescription = "Paid by $method" },
    ) {
        kind.icon?.let {
            Icon(it, contentDescription = null, tint = SubMutedWarmGray, modifier = Modifier.size(11.dp))
        }
        Text(
            text = label,
            style = EyebrowText.copy(fontSize = 9.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
            color = SubMutedWarmGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (kind.icon != null) 3.dp else 0.dp),
        )
    }
}

/** Null for [PaymentKind.Other]: a made-up method has no glyph that would mean anything. */
private val PaymentKind.icon: ImageVector?
    get() = when (this) {
        PaymentKind.Upi -> KpIcons.Upi
        PaymentKind.Card -> KpIcons.Card
        PaymentKind.Cash -> KpIcons.Cash
        PaymentKind.Bank -> KpIcons.Bank
        PaymentKind.Other -> null
    }
