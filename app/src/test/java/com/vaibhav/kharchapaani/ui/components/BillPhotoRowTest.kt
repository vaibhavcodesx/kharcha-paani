package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.receipts.BillPhoto
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File

/** The add sheet's bill photo row, in each of its states. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillPhotoRowTest {

    @get:Rule
    val compose = createComposeRule()

    private var added: BillPhotoSource? = null
    private var removed = 0

    private fun show(photo: BillPhoto) = compose.setContent {
        SplitFlowTheme { BillPhotoRow(photo = photo, onAdd = { added = it }, onRemove = { removed++ }) }
    }

    @Test
    fun `with no photo it offers Add, then camera or gallery`() {
        show(BillPhoto.None)

        compose.onNodeWithText("optional", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("bill_photo_add").performClick()
        compose.onNodeWithText("Choose from gallery").performClick()

        assertEquals(BillPhotoSource.Gallery, added)
    }

    @Test
    fun `while a photo is written it says so`() {
        show(BillPhoto.Processing)

        compose.onNodeWithText("Saving photo…").assertIsDisplayed()
    }

    @Test
    fun `with a photo it shows it, says where it is, and can remove it`() {
        show(BillPhoto.Kept(File("bill.jpg"), staged = null))

        compose.onNodeWithTag("bill_photo_thumbnail").assertIsDisplayed()
        compose.onNodeWithText("Kept with this expense").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove bill photo").performClick()

        assertEquals(1, removed)
    }

    @Test
    fun `tapping the photo opens it full screen, with Replace and Remove`() {
        show(BillPhoto.Kept(File("bill.jpg"), staged = null))

        compose.onNodeWithTag("bill_photo_thumbnail").performClick()
        compose.onNodeWithTag("bill_photo_viewer").assertIsDisplayed()
        compose.onNodeWithText("Choose another").performClick()

        assertEquals(BillPhotoSource.Gallery, added)
    }

    @Test
    fun `a photo whose file is gone says so and can be removed`() {
        show(BillPhoto.Missing)

        compose.onNodeWithText("Photo not on this phone").assertIsDisplayed()
        compose.onNodeWithTag("bill_photo_forget").performClick()

        assertTrue(removed == 1)
    }
    @Test
    fun `a photo only in the cloud offers Load, and shows it loading`() {
        var loads = 0
        var photo by androidx.compose.runtime.mutableStateOf<BillPhoto>(BillPhoto.InCloud())
        compose.setContent {
            SplitFlowTheme { BillPhotoRow(photo = photo, onAdd = {}, onRemove = {}, onLoad = { loads++ }) }
        }
        compose.onNodeWithText("Bill photo saved in the cloud").assertIsDisplayed()
        compose.onNodeWithTag("bill_photo_load").performClick()
        compose.runOnIdle { assertEquals(1, loads) }
        photo = BillPhoto.InCloud(loading = true)
        compose.onNodeWithText("Loading the photo…").assertIsDisplayed()
    }
}