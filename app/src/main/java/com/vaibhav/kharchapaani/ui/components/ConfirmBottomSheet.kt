package com.vaibhav.kharchapaani.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.ui.theme.numericFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBottomSheet(
    isOpen: Boolean,
    isParsing: Boolean,
    isEditing: Boolean,
    type: String,
    amount: String,
    merchant: String,
    category: String,
    paymentMethod: String,
    note: String,
    date: Long,
    groupId: Long?,
    availableGroups: List<GroupEntity>,
    isSplit: Boolean,
    splitMode: SplitMode,
    paidByPersonId: Long,
    shares: List<ShareInput>,
    onTypeChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onPaymentMethodChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onGroupIdChange: (Long?) -> Unit,
    onIsSplitChange: (Boolean) -> Unit,
    onSplitModeChange: (SplitMode) -> Unit,
    onPaidByPersonIdChange: (Long) -> Unit,
    onTogglePersonShare: (Long) -> Unit,
    onExactAmountChange: (Long, Double) -> Unit,
    onPercentageChange: (Long, Double) -> Unit,
    onOpenAddFriend: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val safeDismiss = {
        focusManager.clearFocus()
        onDismiss()
    }
    val safeSave = {
        focusManager.clearFocus()
        onSave()
    }

    val sdfDateTime = remember { SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault()) }
    val formattedDateText = remember(date) {
        val now = Calendar.getInstance()
        val txCal = Calendar.getInstance().apply { timeInMillis = date }
        val isToday = now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        if (isToday) {
            "Today at ${timeFormat.format(Date(date))}"
        } else {
            sdfDateTime.format(Date(date))
        }
    }

    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = safeDismiss,
            sheetState = sheetState,
            containerColor = CharcoalSurface,
            contentColor = OffWhiteText,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(CharcoalBorder)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Transaction" else "Confirm Transaction",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText
                        )
                    )
                    IconButton(onClick = safeDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MutedWarmGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isParsing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = WarmAmberPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Gemini is parsing transaction...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MutedWarmGray)
                            )
                        }
                    }
                } else {
                    // Type Toggle (Expense vs Income)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CharcoalElevated)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (type == "expense") WarmAmberPrimary else Color.Transparent)
                                .clickable { onTypeChange("expense") }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = if (type == "expense") CharcoalBg else MutedWarmGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Expense",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = if (type == "expense") CharcoalBg else MutedWarmGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (type == "income") ChartreuseSecondary else Color.Transparent)
                                .clickable { onTypeChange("income") }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = if (type == "income") CharcoalBg else MutedWarmGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Income",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = if (type == "income") CharcoalBg else MutedWarmGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big Amount Input Field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CharcoalElevated)
                            .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "₹",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = if (type == "income") ChartreuseSecondary else WarmAmberPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = numericFontFamily
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = amount,
                                onValueChange = onAmountChange,
                                placeholder = {
                                    Text(
                                        "0",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            color = SubMutedWarmGray,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = numericFontFamily
                                        )
                                    )
                                },
                                textStyle = MaterialTheme.typography.displayMedium.copy(
                                    color = OffWhiteText,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                    fontFamily = numericFontFamily
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    cursorColor = WarmAmberPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_amount_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // DATE & TIME SELECTOR (Default to Current, Editable On Demand)
                    Text(
                        "Date & Time",
                        style = MaterialTheme.typography.titleSmall.copy(color = MutedWarmGray)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CharcoalElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val currentCal = Calendar.getInstance().apply { timeInMillis = date }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        currentCal.set(Calendar.YEAR, year)
                                        currentCal.set(Calendar.MONTH, month)
                                        currentCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                currentCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                currentCal.set(Calendar.MINUTE, minute)
                                                onDateChange(currentCal.timeInMillis)
                                            },
                                            currentCal.get(Calendar.HOUR_OF_DAY),
                                            currentCal.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    },
                                    currentCal.get(Calendar.YEAR),
                                    currentCal.get(Calendar.MONTH),
                                    currentCal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .testTag("transaction_date_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = WarmAmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formattedDateText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = OffWhiteText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = WarmAmberPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Fast Date Presets
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CharcoalElevated,
                            modifier = Modifier.clickable { onDateChange(System.currentTimeMillis()) }
                        ) {
                            Text(
                                "⚡ Now",
                                style = MaterialTheme.typography.labelSmall.copy(color = ChartreuseSecondary),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CharcoalElevated,
                            modifier = Modifier.clickable {
                                onDateChange(System.currentTimeMillis() - (2 * 60 * 60 * 1000L))
                            }
                        ) {
                            Text(
                                "2 hrs ago",
                                style = MaterialTheme.typography.labelSmall.copy(color = MutedWarmGray),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CharcoalElevated,
                            modifier = Modifier.clickable {
                                onDateChange(System.currentTimeMillis() - (24 * 60 * 60 * 1000L))
                            }
                        ) {
                            Text(
                                "Yesterday",
                                style = MaterialTheme.typography.labelSmall.copy(color = MutedWarmGray),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Merchant / Description
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = onMerchantChange,
                        label = { Text("Merchant / Description", color = MutedWarmGray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_merchant_input"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OffWhiteText),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmAmberPrimary,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalElevated,
                            unfocusedContainerColor = CharcoalElevated,
                            cursorColor = WarmAmberPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Selector
                    Text(
                        "Category",
                        style = MaterialTheme.typography.titleSmall.copy(color = MutedWarmGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryHelper.allCategories.forEach { cat ->
                            val isSelected = category == cat
                            val catColor = CategoryHelper.getCategoryColor(cat)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) catColor.copy(alpha = 0.25f) else CharcoalElevated,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, catColor) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                modifier = Modifier.clickable { onCategoryChange(cat) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = CategoryHelper.getCategoryIcon(cat),
                                        contentDescription = null,
                                        tint = if (isSelected) catColor else MutedWarmGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (isSelected) OffWhiteText else MutedWarmGray,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Payment Method Selector
                    Text(
                        "Payment Method",
                        style = MaterialTheme.typography.titleSmall.copy(color = MutedWarmGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryHelper.allPaymentMethods.forEach { method ->
                            val isSelected = paymentMethod == method
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) WarmAmberPrimary.copy(alpha = 0.2f) else CharcoalElevated,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, WarmAmberPrimary) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                modifier = Modifier.clickable { onPaymentMethodChange(method) }
                            ) {
                                Text(
                                    text = method,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSelected) WarmAmberPrimary else MutedWarmGray,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Trip / Group Selector (if available)
                    if (availableGroups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Trip / Group (Optional)",
                            style = MaterialTheme.typography.titleSmall.copy(color = MutedWarmGray)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isNoneSelected = groupId == null
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isNoneSelected) WarmAmberPrimary.copy(alpha = 0.2f) else CharcoalElevated,
                                border = if (isNoneSelected) androidx.compose.foundation.BorderStroke(1.dp, WarmAmberPrimary) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                modifier = Modifier.clickable { onGroupIdChange(null) }
                            ) {
                                Text(
                                    text = "General / None",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isNoneSelected) WarmAmberPrimary else MutedWarmGray,
                                        fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            availableGroups.forEach { grp ->
                                val isGroupSelected = groupId == grp.id
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isGroupSelected) WarmAmberPrimary.copy(alpha = 0.2f) else CharcoalElevated,
                                    border = if (isGroupSelected) androidx.compose.foundation.BorderStroke(1.dp, WarmAmberPrimary) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                    modifier = Modifier.clickable { onGroupIdChange(grp.id) }
                                ) {
                                    Text(
                                        text = "${grp.emoji} ${grp.name}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (isGroupSelected) WarmAmberPrimary else MutedWarmGray,
                                            fontWeight = if (isGroupSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "For Me" vs "Split" Flow Selector
                    if (type == "expense") {
                        Text(
                            "Who is this for?",
                            style = MaterialTheme.typography.titleSmall.copy(color = MutedWarmGray)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CharcoalElevated)
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (!isSplit) WarmAmberPrimary else Color.Transparent)
                                    .clickable { onIsSplitChange(false) }
                                    .padding(vertical = 10.dp)
                                    .testTag("for_me_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (!isSplit) CharcoalBg else MutedWarmGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "For Me Only",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (!isSplit) CharcoalBg else MutedWarmGray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSplit) WarmAmberPrimary else Color.Transparent)
                                    .clickable { onIsSplitChange(true) }
                                    .padding(vertical = 10.dp)
                                    .testTag("split_with_friends_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = if (isSplit) CharcoalBg else MutedWarmGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Split with Friends",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (isSplit) CharcoalBg else MutedWarmGray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // Split Configuration Details
                        AnimatedVisibility(visible = isSplit) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CharcoalElevated)
                                    .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                // Paid By Row
                                Text(
                                    "Paid By",
                                    style = MaterialTheme.typography.labelMedium.copy(color = MutedWarmGray)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isMePaid = paidByPersonId == -1L
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isMePaid) WarmAmberPrimary.copy(alpha = 0.2f) else CharcoalSurface,
                                        border = if (isMePaid) androidx.compose.foundation.BorderStroke(1.dp, WarmAmberPrimary) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                        modifier = Modifier.clickable { onPaidByPersonIdChange(-1L) }
                                    ) {
                                        Text(
                                            "You",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = if (isMePaid) WarmAmberPrimary else OffWhiteText,
                                                fontWeight = if (isMePaid) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    shares.filter { it.personId != -1L }.forEach { share ->
                                        val isThisPerson = paidByPersonId == share.personId
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isThisPerson) WarmAmberPrimary.copy(alpha = 0.2f) else CharcoalSurface,
                                            border = if (isThisPerson) androidx.compose.foundation.BorderStroke(1.dp, WarmAmberPrimary) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                            modifier = Modifier.clickable { onPaidByPersonIdChange(share.personId) }
                                        ) {
                                            Text(
                                                share.personName,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = if (isThisPerson) WarmAmberPrimary else OffWhiteText,
                                                    fontWeight = if (isThisPerson) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Split Mode Tabs
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SplitMode.values().forEach { mode ->
                                        val isSelected = splitMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) WarmAmberPrimary else CharcoalSurface,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSplitModeChange(mode) }
                                        ) {
                                            Text(
                                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (isSelected) CharcoalBg else MutedWarmGray,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                ),
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Friends in this Split
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Split Between",
                                        style = MaterialTheme.typography.labelMedium.copy(color = MutedWarmGray)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onOpenAddFriend() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = WarmAmberPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Add Friend",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = WarmAmberPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Shares List
                                shares.forEach { share ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable { onTogglePersonShare(share.personId) }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(if (share.isSelected) WarmAmberPrimary else CharcoalSurface)
                                                    .border(1.dp, CharcoalBorder, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (share.isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = CharcoalBg,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = share.personName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = if (share.isSelected) OffWhiteText else SubMutedWarmGray,
                                                    fontWeight = if (share.isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            )
                                        }

                                        if (share.isSelected) {
                                            when (splitMode) {
                                                SplitMode.EQUAL -> {
                                                    Text(
                                                        text = "₹${String.format(Locale.US, "%.0f", share.amount)}",
                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                            color = WarmAmberPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = numericFontFamily
                                                        )
                                                    )
                                                }
                                                SplitMode.EXACT -> {
                                                    OutlinedTextField(
                                                        value = if (share.amount > 0) String.format(Locale.US, "%.0f", share.amount) else "",
                                                        onValueChange = {
                                                            val exactVal = it.toDoubleOrNull() ?: 0.0
                                                            onExactAmountChange(share.personId, exactVal)
                                                        },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.labelMedium.copy(
                                                            color = OffWhiteText,
                                                            textAlign = TextAlign.End,
                                                            fontFamily = numericFontFamily
                                                        ),
                                                        prefix = { Text("₹", color = WarmAmberPrimary) },
                                                        modifier = Modifier.width(100.dp),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedContainerColor = CharcoalSurface,
                                                            unfocusedContainerColor = CharcoalSurface,
                                                            focusedBorderColor = WarmAmberPrimary,
                                                            unfocusedBorderColor = CharcoalBorder
                                                        )
                                                    )
                                                }
                                                SplitMode.PERCENTAGE -> {
                                                    OutlinedTextField(
                                                        value = if (share.percentage > 0) String.format(Locale.US, "%.0f", share.percentage) else "",
                                                        onValueChange = {
                                                            val pctVal = it.toDoubleOrNull() ?: 0.0
                                                            onPercentageChange(share.personId, pctVal)
                                                        },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.labelMedium.copy(
                                                            color = OffWhiteText,
                                                            textAlign = TextAlign.End,
                                                            fontFamily = numericFontFamily
                                                        ),
                                                        suffix = { Text("%", color = WarmAmberPrimary) },
                                                        modifier = Modifier.width(85.dp),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedContainerColor = CharcoalSurface,
                                                            unfocusedContainerColor = CharcoalSurface,
                                                            focusedBorderColor = WarmAmberPrimary,
                                                            unfocusedBorderColor = CharcoalBorder
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val totalVal = amount.toDoubleOrNull() ?: 0.0
                                if (splitMode == SplitMode.EXACT) {
                                    val sumShares = shares.filter { it.isSelected }.sumOf { it.amount }
                                    val diff = totalVal - sumShares
                                    if (abs(diff) > 0.01) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (diff > 0) "₹${String.format(Locale.US, "%.2f", diff)} remaining to allocate" else "₹${String.format(Locale.US, "%.2f", -diff)} over total amount",
                                            style = MaterialTheme.typography.labelSmall.copy(color = WarmAmberPrimary)
                                        )
                                    }
                                } else if (splitMode == SplitMode.PERCENTAGE) {
                                    val sumPct = shares.filter { it.isSelected }.sumOf { it.percentage }
                                    if (abs(100.0 - sumPct) > 0.1) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Total percentage: ${String.format(Locale.US, "%.1f", sumPct)}% (must equal 100%)",
                                            style = MaterialTheme.typography.labelSmall.copy(color = WarmAmberPrimary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Note Field
                    OutlinedTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        label = { Text("Note / Memo (Optional)", color = MutedWarmGray) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = OffWhiteText),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmAmberPrimary,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalElevated,
                            unfocusedContainerColor = CharcoalElevated,
                            cursorColor = WarmAmberPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Save Button
                    val isValidAmount = (amount.toDoubleOrNull() ?: 0.0) > 0
                    Button(
                        onClick = safeSave,
                        enabled = isValidAmount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("confirm_save_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "income") ChartreuseSecondary else WarmAmberPrimary,
                            contentColor = CharcoalBg,
                            disabledContainerColor = CharcoalElevated,
                            disabledContentColor = SubMutedWarmGray
                        )
                    ) {
                        Text(
                            text = if (isEditing) "Update Transaction" else "Save Transaction",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
