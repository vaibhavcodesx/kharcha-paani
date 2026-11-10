package com.vaibhav.kharchapaani.ui.itemsplit

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.itemsplit.ItemSplitDraft
import com.vaibhav.kharchapaani.data.sync.BillSplitLink
import com.vaibhav.kharchapaani.domain.SplitClaim
import com.vaibhav.kharchapaani.domain.SplitItem
import com.vaibhav.kharchapaani.domain.SplitPerson
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The split sheet as it's drawn: whose picks are showing, what each person pays, what is still
 * unpicked, and the link's three states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ItemSplitSheetTest {

    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val toggled = mutableListOf<String>()
    private val weights = mutableListOf<Pair<String, Int>>()
    private val sentLinks = mutableListOf<String>()
    private var used = 0
    private var shared = 0

    private val actions = ItemSplitActions(
        onDismiss = {},
        onSetActive = {},
        onAddPerson = { _, _ -> },
        onRemovePerson = {},
        onToggle = { toggled += it },
        onSetWeight = { id, weight -> weights += id to weight },
        onSaveItem = { _, _, _, _ -> },
        onRemoveItem = {},
        onReadItems = {},
        onShare = { shared++ },
        onStopSharing = {},
        onSendLink = { sentLinks += it },
        onUse = { used++ },
    )

    /** Toit: biryani ₹200 (Rohan's), two naan ₹100 (mine), water ₹30 nobody has picked, and 10% tax on top: ₹363. */
    private val dinner = ItemSplitDraft(
        splitId = "split",
        hostName = "Vaibhav",
        title = "Toit",
        billTotalPaise = 36_300,
        items = listOf(
            SplitItem("biryani", "Biryani", 1, 20_000),
            SplitItem("naan", "Naan", 2, 10_000),
            SplitItem("water", "Water", 1, 3_000),
        ),
        people = listOf(SplitPerson("me", "Vaibhav", isHost = true, ledgerId = -1), SplitPerson("rohan", "Rohan", ledgerId = 1)),
        claims = listOf(SplitClaim("biryani", "rohan"), SplitClaim("naan", "me")),
        activePersonId = "me",
    )

    private fun show(draft: ItemSplitDraft, state: ItemSplitUiState = ItemSplitUiState(draft = draft, isOpen = true)) {
        compose.setContent {
            SplitFlowTheme { ItemSplitContent(state = state.copy(draft = draft), draft = draft, candidates = emptyList(), actions = actions) }
        }
    }

    @Test
    fun `it shows whose picks are up, what each person pays and what is still unpicked`() {
        show(dinner)

        compose.onNodeWithTag("item_split_item_Naan").assertIsOn()
        compose.onNodeWithTag("item_split_item_Biryani").assertIsOff()
        // Items ₹330 of which ₹300 picked; ₹33 of tax follows the items: ₹10 on my ₹100, ₹20 on Rohan's ₹200.
        compose.onNodeWithTag("item_split_share_Vaibhav", useUnmergedTree = true).performScrollTo().assertTextEquals("₹110")
        compose.onNodeWithTag("item_split_share_Rohan", useUnmergedTree = true).performScrollTo().assertTextEquals("₹220")
        compose.onNodeWithTag("item_split_unpicked").performScrollTo().assertTextEquals(
            app.resources.getQuantityString(R.plurals.item_split_unpicked, 1, 1, "₹30"),
        )
        compose.onNodeWithTag("item_split_use_button").assertTextEquals(app.getString(R.string.item_split_use))
    }

    @Test
    fun `tapping an item picks it for whoever is up, and the split can be used`() {
        show(dinner)

        compose.onNodeWithTag("item_split_item_Water").performScrollTo().performClick()
        compose.onNodeWithTag("item_split_use_button").performClick()

        assertEquals(listOf("water"), toggled)
        assertEquals(1, used)
    }

    @Test
    fun `the count on an item with several goes up to how many there are`() {
        show(dinner.copy(claims = listOf(SplitClaim("naan", "me", weight = 1))))

        compose.onNodeWithText(app.getString(R.string.item_split_how_many_you)).assertExists()
        compose.onNode(androidx.compose.ui.test.hasContentDescription(app.getString(R.string.item_split_more))).performClick()
        compose.onNode(androidx.compose.ui.test.hasContentDescription(app.getString(R.string.item_split_fewer))).assertIsNotEnabled()

        assertEquals(listOf("naan" to 2), weights)
    }

    @Test
    fun `alone at the table, it asks who else was there before it can be used`() {
        show(dinner.copy(people = dinner.people.take(1), claims = emptyList()))

        compose.onNodeWithTag("item_split_use_button").assertTextEquals(app.getString(R.string.item_split_use_need_people))
        compose.onNodeWithTag("item_split_use_button").assertIsNotEnabled()
    }

    @Test
    fun `with no items yet it offers to read the bill photo or add one by hand`() {
        val empty = dinner.copy(items = emptyList(), claims = emptyList())
        show(empty, ItemSplitUiState(draft = empty, isOpen = true, canRead = true))

        compose.onNodeWithTag("item_split_read_photo").assertExists()
        compose.onNodeWithTag("item_split_add_item").assertExists()
        compose.onNodeWithTag("item_split_use_button").assertTextEquals(app.getString(R.string.item_split_use_need_items))
    }

    @Test
    fun `the link is offered, and once it's up it can be sent as the page's address`() {
        show(dinner.copy(link = BillSplitLink(id = "split", token = "0123456789abcdef0123456789abcdef")))

        compose.onNodeWithTag("item_split_share_button").assertDoesNotExist()
        compose.onNodeWithTag("item_split_send_link").performScrollTo().performClick()

        assertEquals(listOf("https://vaibhavcodesx.github.io/kharcha-paani-site/split.html#0123456789abcdef0123456789abcdef"), sentLinks)
    }

    @Test
    fun `before it's shared, the button asks for the link`() {
        show(dinner)
        compose.onNodeWithTag("item_split_share_button").performScrollTo().performClick()
        assertEquals(1, shared)
    }

    @Test
    fun `a ledger with no account is told to sign in to share`() {
        show(dinner, ItemSplitUiState(draft = dinner, isOpen = true, canShare = false))

        compose.onNodeWithTag("item_split_share_button").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.item_split_share_local)).performScrollTo().assertExists()
    }
}
