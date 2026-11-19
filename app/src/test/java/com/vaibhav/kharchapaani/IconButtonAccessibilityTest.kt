package com.vaibhav.kharchapaani

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.vaibhav.kharchapaani.data.receipts.BillPhoto
import com.vaibhav.kharchapaani.ui.components.BillPhotoRow
import com.vaibhav.kharchapaani.ui.home.CaptureAction
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.shell.AppTab
import com.vaibhav.kharchapaani.ui.shell.KpDock
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IconButtonAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `spoken labels exist for icon-only buttons on dock, capture action, and photo removal`() {
        compose.setContent {
            SplitFlowTheme {
                KpDock(
                    currentTab = AppTab.Activity,
                    onTabSelected = {},
                    onAdd = {},
                )

                CaptureAction(
                    icon = KpIcons.Mic,
                    label = "Say it",
                    filled = false,
                    onClick = {},
                )

                BillPhotoRow(
                    photo = BillPhoto.Kept(File("receipt.jpg"), staged = null),
                    onAdd = {},
                    onRemove = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Add expense").assertIsDisplayed()
        compose.onNodeWithContentDescription("Say it").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove bill photo").assertIsDisplayed()
    }
}
