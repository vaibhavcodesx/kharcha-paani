package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.domain.UpiLink
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.ui.theme.Spacing

/**
 * A code to scan, whichever way the money goes (owner, 2026-09-27: "the QR should be shown on both
 * sides, and also in IOU"). It opens on the code of whoever is receiving the money, and offers the
 * other person's too when both exist, so either phone can do the paying. A friend who isn't on the
 * app has no UPI ID here, so with them it's always your code: show it and they pay you.
 *
 * The "Pay in a UPI app" button appears only on the other person's code, where this phone is the
 * one sending money; on your own code it would open a payment to yourself.
 */
@Composable
fun MoneyQrSection(
    person: PersonEntity,
    myPayee: UpiPayee?,
    amount: Double,
    note: String,
    moneyComesToMe: Boolean,
    modifier: Modifier = Modifier,
) {
    val myCodeLabel = stringResource(R.string.upi_copy_your_code)
    val theirCodeLabel = stringResource(R.string.upi_copy_their_code, person.name.trim().substringBefore(' ').ifEmpty { person.name })
    val theirPayee = person.upiId?.let(UpiLink::cleanId)?.let { UpiPayee(it, person.name) }
    val firstName = person.name.trim().substringBefore(' ').ifEmpty { person.name }
    val preferMine = if (moneyComesToMe) myPayee != null else theirPayee == null
    var showMine by rememberSaveable(person.id, moneyComesToMe) { mutableStateOf(preferMine) }
    val payee = if (showMine) myPayee else theirPayee

    Column(modifier.fillMaxWidth()) {
        if (myPayee != null && theirPayee != null) {
            SegmentedControl(
                options = listOf(true, false),
                selected = showMine,
                label = { if (it) myCodeLabel else theirCodeLabel },
                onSelect = { showMine = it },
                testTagFor = { if (it) "qr_mine" else "qr_theirs" },
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
            )
        }
        when {
            payee != null && amount > 0 -> UpiPaymentCard(
                payee = payee,
                amount = amount,
                note = note,
                // Only their code is one this phone pays into.
                payingFromThisPhone = !showMine,
            )
            payee != null -> Text(
                if (showMine) stringResource(R.string.upi_copy_amount_mine) else stringResource(R.string.upi_copy_amount_theirs, firstName),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> Text(
                if (moneyComesToMe) {
                    stringResource(R.string.upi_copy_add_settings, firstName)
                } else {
                    stringResource(R.string.upi_copy_no_their_id, firstName)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("no_payee_upi"),
            )
        }
    }
}
