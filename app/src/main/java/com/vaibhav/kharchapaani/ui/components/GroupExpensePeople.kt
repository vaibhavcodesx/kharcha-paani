package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.LocalMyColour
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray

/**
 * Who paid and who shares a group expense, as dots only: a ringed dot for the payer, a hairline,
 * then a filled dot per person it was split with (the colour each person has everywhere else).
 * The names this used to repeat on every row are in the spoken description, and the legend above
 * the list ([GroupExpenseLegend]) says what ring and dot mean once.
 *
 * Colour never carries the meaning alone: the whole cluster reads as "Paid by Aaru. Split with You,
 * Aaru, Pruthva." to a screen reader.
 */
@Composable
internal fun GroupExpensePeople(expense: FullTransaction, people: List<PersonEntity>, modifier: Modifier = Modifier) {
    val me = LocalMyColour.current.color
    val unknown = parseColor("")
    val you = stringResource(R.string.group_people_you)
    val former = stringResource(R.string.group_people_former)
    fun name(id: Long?) = if (id == -1L) you else people.firstOrNull { it.id == id }?.name ?: former
    fun colour(id: Long?) = if (id == -1L) me else people.firstOrNull { it.id == id }?.avatarColor?.let { parseColor(it) } ?: unknown
    val payer = expense.split?.paidByPersonId ?: if (expense.transaction.isOwnedByMe) -1L
        else people.firstOrNull { it.profileId == expense.transaction.ownerProfileId }?.id
    val prefix = "group_expense_${expense.transaction.id}"

    class Sharer(val label: String, val colour: Color, val tag: String)
    val sharers = expense.shares
        .distinctBy { if (it.isGuest) "guest_${it.guestPersonId}_${it.guestName}" else "person_${it.personId}" }
        .mapIndexed { index, share ->
            val id = if (share.isGuest) share.guestPersonId else share.personId
            val label = if (share.isGuest) share.guestName.orEmpty() else name(id)
            Sharer(label, colour(id), "${prefix}_share_${id ?: "guest_$index"}")
        }
    val shown = sharers.take(MAX_SHARER_DOTS)
    val extra = sharers.size - shown.size

    val separator = stringResource(R.string.timeline_row_separator)
    val description = if (sharers.isEmpty()) stringResource(R.string.group_people_description_paid, name(payer))
        else stringResource(R.string.group_people_description, name(payer), sharers.joinToString(separator) { it.label })

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        // Paid: the dot inside a ring. The dot itself is the payer's colour, so it reads as theirs
        // wherever it appears; the ring is what says "paid".
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(12.dp).border(1.5.dp, colour(payer), CircleShape),
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(colour(payer)).testTag("${prefix}_payer_$payer"))
        }
        if (shown.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(10.dp).background(CharcoalBorder))
        }
        shown.forEach { Box(Modifier.size(8.dp).clip(CircleShape).background(it.colour).testTag(it.tag)) }
        if (extra > 0) {
            Text(
                "+$extra",
                style = MaterialTheme.typography.labelSmall,
                color = SubMutedWarmGray,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** Ring and dot, named once, for the top of a group's list. */
@Composable
internal fun GroupExpenseLegend(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.clearAndSetSemantics {},
    ) {
        Box(Modifier.size(12.dp).border(1.5.dp, SubMutedWarmGray, CircleShape))
        Text(stringResource(R.string.group_legend_paid), style = MaterialTheme.typography.labelSmall, color = SubMutedWarmGray)
        Box(Modifier.padding(start = Spacing.s).size(8.dp).clip(CircleShape).background(SubMutedWarmGray))
        Text(stringResource(R.string.group_legend_split), style = MaterialTheme.typography.labelSmall, color = SubMutedWarmGray)
    }
}

/** Four dots and a count: more than that and the row is a crowd, which "+N" says better. */
private const val MAX_SHARER_DOTS = 4
