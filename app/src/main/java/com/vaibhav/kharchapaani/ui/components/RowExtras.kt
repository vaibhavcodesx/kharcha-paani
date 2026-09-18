package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.Spacing

/**
 * Who a split expense is shared with, as faces on the row itself.
 *
 * It used to read "Split between 3", which is a count and nothing else — you had to open the
 * expense to find out who. Borrowed from §5's Image 13, where the avatars sit on the transaction
 * row and answer "who" without a tap.
 */
@Composable
fun SharerFaces(
    sharers: List<PersonEntity>,
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val shown = sharers.take(3)
    // Anyone the split names but this phone has no person row for (a groupmate mid-sync) still
    // counts toward the total, so the "+n" is off the split's own size, not the faces we resolved.
    val extra = count - shown.size

    Row(
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics {
            contentDescription = if (shown.isEmpty()) {
                "Split between $count"
            } else {
                "Split with " + shown.joinToString(", ") { it.name } + if (extra > 0) " and $extra more" else ""
            }
        },
    ) {
        shown.forEach { person ->
            PersonAvatar(
                name = person.name.trim().substringBefore(' '),
                colorHex = person.avatarColor,
                emoji = person.avatarEmoji,
                size = 18.dp,
                ringColor = CharcoalCard,
            )
        }
        if (extra > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp)
                    .clip(KpShapes.small)
                    .background(CharcoalElevated)
                    .clearAndSetSemantics {},
            ) {
                Text("+$extra", style = MaterialTheme.typography.labelSmall, color = MutedWarmGray)
            }
        }
    }
}

/**
 * How often this merchant has come up. Borrowed from §5's Image 10's "3 visits" chip: a cheap
 * addition that turns a row into a small piece of insight ("this is the third Rapido this month").
 *
 * Only shown from two upward — "1 visit" is noise on every row in the list.
 */
@Composable
fun VisitChip(visits: Int, modifier: Modifier = Modifier) {
    if (visits < 2) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = Spacing.s)
            .clip(KpShapes.small)
            .background(CharcoalElevated)
            .padding(horizontal = Spacing.xs, vertical = 1.dp)
            .semantics { contentDescription = "$visits this month" },
    ) {
        Icon(KpIcons.Insights, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(10.dp))
        Text(
            text = "${visits}×",
            style = MaterialTheme.typography.labelSmall,
            color = MutedWarmGray,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
