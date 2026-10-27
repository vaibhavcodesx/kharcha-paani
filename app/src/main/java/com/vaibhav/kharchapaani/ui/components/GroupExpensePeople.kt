package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.ui.theme.LocalMyColour
import com.vaibhav.kharchapaani.ui.theme.Spacing

/** Names carry the meaning; dots repeat the person's existing avatar colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupExpensePeople(expense: FullTransaction, people: List<PersonEntity>) {
    val me = LocalMyColour.current.color
    val unknown = parseColor("")
    val you = stringResource(R.string.group_people_you)
    val former = stringResource(R.string.group_people_former)
    fun name(id: Long?) = if (id == -1L) you else people.firstOrNull { it.id == id }?.name ?: former
    fun colour(id: Long?) = if (id == -1L) me else people.firstOrNull { it.id == id }?.avatarColor?.let { parseColor(it) } ?: unknown
    val payer = expense.split?.paidByPersonId ?: if (expense.transaction.isOwnedByMe) -1L
        else people.firstOrNull { it.profileId == expense.transaction.ownerProfileId }?.id
    val prefix = "group_expense_${expense.transaction.id}"
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.group_people_paid), style = MaterialTheme.typography.bodySmall)
            MemberColourName(name(payer), colour(payer), "${prefix}_payer_$payer")
        }
        if (expense.shares.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.group_people_split), style = MaterialTheme.typography.bodySmall)
                expense.shares.distinctBy { if (it.isGuest) "guest_${it.guestPersonId}_${it.guestName}" else "person_${it.personId}" }
                    .forEachIndexed { index, share ->
                        val id = if (share.isGuest) share.guestPersonId else share.personId
                        val label = if (share.isGuest) share.guestName.orEmpty() else name(id)
                        MemberColourName(label, colour(id), "${prefix}_share_${id ?: "guest_$index"}")
                    }
            }
        }
    }
}

@Composable
private fun MemberColourName(name: String, colour: Color, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.widthIn(max = 160.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colour).testTag(tag))
        Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
