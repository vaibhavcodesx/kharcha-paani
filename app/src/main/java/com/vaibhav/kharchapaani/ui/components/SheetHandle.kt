package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive

/**
 * The grab bar on every sheet. Sheets have no close button (swipe down or back), so this bar is the
 * only visible sign they can be dismissed, which matters most on 3-button navigation. It uses the
 * interactive outline colour (3.3:1 or more on sheet surfaces; the old bars were 1.3:1) and
 * Material's handle, which carries the "Drag handle" label and dismiss action for TalkBack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHandle() {
    BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, shape = CircleShape, color = OutlineInteractive)
}
