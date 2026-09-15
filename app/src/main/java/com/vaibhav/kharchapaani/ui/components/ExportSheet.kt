package com.vaibhav.kharchapaani.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.domain.CsvExport
import com.vaibhav.kharchapaani.domain.ExportRange
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class ExportScope(val label: String) { Month("A month"), Dates("Dates"), Everything("Everything") }

@Composable
fun ExportSheet(
    isOpen: Boolean,
    transactions: List<FullTransaction>,
    initialMonth: YearMonth,
    earliestMonth: YearMonth,
    onExport: (ExportRange) -> Unit,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    if (!isOpen) return
    var scope by rememberSaveable { mutableStateOf(ExportScope.Month) }
    var month by rememberSaveable { mutableStateOf(initialMonth) }
    var from by rememberSaveable { mutableStateOf(initialMonth.atDay(1)) }
    var to by rememberSaveable { mutableStateOf(minOf(initialMonth.atEndOfMonth(), today)) }
    KpModalSheet(onDismiss = onDismiss) {
        ExportContent(
            transactions = transactions,
            scope = scope,
            month = month,
            from = from,
            to = to,
            earliestMonth = earliestMonth,
            today = today,
            onScopeChange = { scope = it },
            onMonthChange = { month = it },
            onDatesChange = { newFrom, newTo -> from = newFrom; to = newTo },
            onExport = onExport,
        )
    }
}

/**
 * Picks what goes into the spreadsheet (a month, a span of dates, or everything) and says how many
 * entries that is before anything is shared.
 */
@Composable
fun ExportContent(
    transactions: List<FullTransaction>,
    scope: ExportScope,
    month: YearMonth,
    from: LocalDate,
    to: LocalDate,
    earliestMonth: YearMonth,
    today: LocalDate,
    onScopeChange: (ExportScope) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onDatesChange: (from: LocalDate, to: LocalDate) -> Unit,
    onExport: (ExportRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = when (scope) {
        ExportScope.Month -> ExportRange.Month(month)
        ExportScope.Dates -> ExportRange.Dates(from, to)
        ExportScope.Everything -> ExportRange.Everything
    }
    val count = remember(transactions, range) { CsvExport.entriesIn(transactions, range, ZoneId.systemDefault()).size }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = "Export",
                disabledLabel = "Nothing to export",
                enabled = count > 0,
                onClick = { onExport(range) },
                // Just the count: the range is on show right above the bar.
                summary = if (count == 1) "1 entry" else "$count entries",
                buttonTestTag = "export_button",
            )
        },
    ) {
        SheetTitle(title = "Export to a spreadsheet", supporting = "A CSV file that opens in Sheets, Excel or Numbers, with every entry in the range.")

        SegmentedControl(
            options = ExportScope.entries,
            selected = scope,
            label = { it.label },
            onSelect = onScopeChange,
            testTagFor = { "export_scope_${it.name}" },
            modifier = Modifier.fillMaxWidth(),
        )

        when (scope) {
            ExportScope.Month -> MonthSwitcher(
                month = month,
                earliest = earliestMonth,
                latest = YearMonth.from(today),
                onMonthChange = onMonthChange,
                modifier = Modifier.padding(top = Spacing.l),
            )
            ExportScope.Dates -> Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DateRow(
                    label = "From",
                    clickLabel = "Change start date",
                    date = from,
                    max = today,
                    position = RowPosition.First,
                    tag = "export_from",
                    // Starting after the end moves the end along, so the range never runs backwards.
                    onPick = { picked -> onDatesChange(picked, if (picked.isAfter(to)) picked else to) },
                )
                DateRow(
                    label = "To",
                    clickLabel = "Change end date",
                    date = to,
                    max = today,
                    position = RowPosition.Last,
                    tag = "export_to",
                    onPick = { picked -> onDatesChange(if (picked.isBefore(from)) picked else from, picked) },
                )
            }
            ExportScope.Everything -> Text(
                "Every entry on this phone, from the first you logged.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
    }
}

@Composable
private fun DateRow(label: String, clickLabel: String, date: LocalDate, max: LocalDate, position: RowPosition, tag: String, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .clickable(onClickLabel = clickLabel) {
                DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                    datePicker.maxDate = max.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.show()
            }
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .testTag(tag),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(SheetLabelWidth))
        Text(DayFormat.format(date), style = MaterialTheme.typography.bodyLarge, color = OffWhiteText, modifier = Modifier.weight(1f))
        Icon(KpIcons.Calendar, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp))
    }
}

private val DayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
