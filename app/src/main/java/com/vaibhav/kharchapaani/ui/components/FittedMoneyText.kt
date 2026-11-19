package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Keep amounts on one line and shrink only if they exceed their available width. */
@Composable
internal fun FittedMoneyText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = style.copy(lineHeight = TextUnit.Unspecified),
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
    )
}
