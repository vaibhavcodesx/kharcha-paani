package com.vaibhav.kharchapaani.ui.components

import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * One expense or income in the Home timeline. The day it belongs to is its section header, so the
 * row only shows the time. Tap to edit; swipe right to edit, left to delete, or use the same two
 * actions from TalkBack's actions menu. Read-only rows (a groupmate's, or in an archived group)
 * offer neither.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItemCard(
    fullTx: FullTransaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    position: RowPosition = RowPosition.Single,
    people: List<PersonEntity> = emptyList(),
    visits: Int = 0,
    hasBillPhoto: Boolean = false,
) {
    val editLabel = stringResource(R.string.timeline_row_edit)
    val deleteLabel = stringResource(R.string.timeline_row_delete)
    val tx = fullTx.transaction
    val isIncome = tx.type == "income"
    val isSplit = fullTx.split != null
    val time = remember(tx.date) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(tx.date)) }
    // "Split between 3" told you a count and nothing else; the faces say who, at a glance, which
    // is what anyone actually wants from a shared expense in a list (backlog §5, Image 13).
    // The payment method has left this string and become its own pill: as plain text it was the
    // same grey word on nearly every row, which is reading load for no information.
    val detail = time
    val sharers = remember(fullTx, people) {
        if (!isSplit) emptyList() else fullTx.shares.mapNotNull { share -> people.firstOrNull { it.id == share.personId } }
    }

    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDelete by rememberUpdatedState(onDelete)
    // Deliberate swipes only. The default commits after 56dp, or after any quick flick however
    // short, so a sideways twitch while scrolling deleted rows. Now the finger has to carry the row
    // nearly half its width, fast or slow. The state isn't saved: a row Undo brings back must arrive
    // settled, not still swiped away, which would delete it again.
    val density = LocalDensity.current
    var rowWidth by remember { mutableFloatStateOf(0f) }
    val dismissState = remember(density) {
        lateinit var state: SwipeToDismissBoxState
        state = SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            confirmValueChange = { target ->
                target == SwipeToDismissBoxValue.Settled || SwipeCommit.isFarEnough(state.requireOffset(), rowWidth)
            },
            positionalThreshold = { distance -> distance * SwipeCommit.FRACTION },
        )
        state
    }
    // Act once the row has settled: confirmValueChange can run more than once per swipe, which
    // deleted the same expense twice.
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> currentOnDelete()
            SwipeToDismissBoxValue.StartToEnd -> {
                currentOnEdit()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    // A row the ledger won't delete must not swipe away as if it had been deleted.
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isReadOnly,
        enableDismissFromEndToStart = !isReadOnly,
        modifier = modifier.clip(position.shape).onSizeChanged { rowWidth = it.width.toFloat() },
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeReveal(WarmAmberPrimary, CharcoalBg, KpIcons.Write, editLabel, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> SwipeReveal(DeleteRed, OffWhiteText, KpIcons.Delete, deleteLabel, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(CharcoalCard)
                .clickable(onClickLabel = editLabel, onClick = onEdit)
                .semantics {
                    if (!isReadOnly) {
                        customActions = listOf(
                            CustomAccessibilityAction(editLabel) { currentOnEdit(); true },
                            CustomAccessibilityAction(deleteLabel) { currentOnDelete(); true },
                        )
                    }
                }
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .testTag("transaction_item_${tx.id}"),
        ) {
            CategoryBadge(tx.category)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.merchant.ifBlank { tx.category },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (visits >= 2) VisitChip(visits)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PaymentPill(method = tx.paymentMethod, modifier = Modifier.padding(start = Spacing.s))
                    if (hasBillPhoto) {
                        Icon(
                            Icons.Outlined.ReceiptLong,
                            contentDescription = stringResource(R.string.timeline_row_bill),
                            tint = MutedWarmGray,
                            modifier = Modifier.padding(start = Spacing.s).size(14.dp),
                        )
                    }
                    if (isSplit) {
                        SharerFaces(
                            sharers = sharers,
                            count = fullTx.shares.size,
                            modifier = Modifier.padding(start = Spacing.s),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val shown = if (isSplit) fullTx.myPersonalShare else tx.amount
                // A split someone else paid in full leaves my share at zero, and "-₹0" is not a
                // number anyone means: it reads as a negative nothing. No sign, and the muted
                // colour the settled rows already use, so the row says "this cost me nothing".
                val nothingOwed = shown < ROUNDS_TO_ZERO
                Text(
                    text = when {
                        nothingOwed -> Money.format(0.0)
                        isIncome -> Money.format(shown, showPlus = true)
                        else -> "-${Money.format(shown)}"
                    },
                    style = MoneyText.row,
                    color = when {
                        nothingOwed -> MutedWarmGray
                        isIncome -> ChartreuseSecondary
                        else -> OffWhiteText
                    },
                )
                if (isSplit) {
                    Text(text = stringResource(R.string.timeline_row_of, Money.format(tx.amount)), style = MoneyText.small)
                }
            }
        }
    }
}

@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    val color = CategoryHelper.getCategoryColor(category)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(KpShapes.small)
            .background(color.copy(alpha = 0.14f)),
    ) {
        Icon(CategoryHelper.getCategoryIcon(category), contentDescription = category, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwipeReveal(background: Color, content: Color, icon: ImageVector, label: String, alignment: Alignment) {
    Box(
        contentAlignment = alignment,
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = Spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

// 4.8:1 behind OffWhite label text.
private val DeleteRed = Color(0xFFC62F2F)

/** How far a row must be dragged before letting go deletes or edits it. */
internal object SwipeCommit {
    const val FRACTION = 0.45f

    fun isFarEnough(offsetPx: Float, rowWidthPx: Float): Boolean = rowWidthPx > 0f && abs(offsetPx) >= rowWidthPx * FRACTION
}

/** Below half a paisa nothing changed hands, whatever the arithmetic says. */
private const val ROUNDS_TO_ZERO = 0.005
