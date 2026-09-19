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
import androidx.compose.foundation.shape.CircleShape
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
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.Spacing

/**
 * Who a split expense is shared with, as a dot per person in that person's colour.
 *
 * It used to read "Split between 3", which is a count and nothing else — you had to open the
 * expense to find out who. Faces answered that, but at 18dp, in full-strength colour, with dark
 * initials, overlapping, and a "+1" chip on the end, they were the brightest thing in a row whose
 * job is the merchant and the amount: the secondary detail was winning.
 *
 * Dots keep what the faces were for. A person's colour is now genuinely distinct from every other
 * person's ([PersonColours]), so three dots say how many and which three, at about a fifth of the
 * ink and with nothing competing for the eye. Who is *which* dot is a tap away, where it always was.
 */
@Composable
fun SharerFaces(
    sharers: List<PersonEntity>,
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val shown = sharers.take(MAX_DOTS)
    // Anyone the split names but this phone has no person row for (a groupmate mid-sync) still
    // counts toward the total, so the "+n" is off the split's own size, not the dots we resolved.
    val extra = count - shown.size

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
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
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(parseColor(person.avatarColor))
                    .clearAndSetSemantics {},
            )
        }
        if (extra > 0) {
            // Bare text, no container. The chip around it made a count of one look like a third
            // participant, which is the part that was called irritating.
            Text(
                "+$extra",
                style = MaterialTheme.typography.labelSmall,
                color = SubMutedWarmGray,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** Three dots and a count; a fourth dot is narrower than the "+1" it would replace. */
private const val MAX_DOTS = 3

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
