package com.vaibhav.kharchapaani.ui.itemsplit

import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.itemsplit.ItemSplitDraft
import com.vaibhav.kharchapaani.data.itemsplit.ItemSplitStore
import com.vaibhav.kharchapaani.data.repository.BillItemLine
import com.vaibhav.kharchapaani.data.repository.BillItemsReadResult
import com.vaibhav.kharchapaani.data.sync.BillSplitEndedException
import com.vaibhav.kharchapaani.data.sync.BillSplitLink
import com.vaibhav.kharchapaani.data.sync.BillSplitRefusedException
import com.vaibhav.kharchapaani.data.sync.BillSplitRemote
import com.vaibhav.kharchapaani.data.sync.RemoteBillClaim
import com.vaibhav.kharchapaani.data.sync.RemoteBillItem
import com.vaibhav.kharchapaani.data.sync.RemoteBillPerson
import com.vaibhav.kharchapaani.data.sync.RemoteBillSplit
import com.vaibhav.kharchapaani.domain.AiFailure
import com.vaibhav.kharchapaani.domain.ItemSplit
import com.vaibhav.kharchapaani.domain.ItemSplitMath
import com.vaibhav.kharchapaani.domain.SplitItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Split by items, from the first tap to the amounts handed back, against a table that behaves like
 * the server (supabase/migrations/20261110000000_bill_item_splits.sql): the phone's own picks, a
 * friend's picks arriving from the link, a dropped connection, a link that ran out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemSplitControllerTest {

    /** The server's side of one shared bill, with friends who join and pick from the link. */
    private class FakeTable : BillSplitRemote {
        var split: RemoteBillSplit? = null
        var offline = false
        var refuse: String? = null
        val closed = mutableListOf<String>()
        var created = 0
        private var version = 0L
        private var joined = 0

        private fun reach() {
            if (offline) throw IOException("offline")
            refuse?.let { throw BillSplitRefusedException(it) }
        }

        private fun change(transform: (RemoteBillSplit) -> RemoteBillSplit): RemoteBillSplit {
            val current = split ?: throw BillSplitEndedException()
            version++
            return transform(current).copy(version = version).also { split = it }
        }

        override suspend fun create(id: String, title: String, hostName: String, split: ItemSplit): BillSplitLink {
            reach()
            created++
            version++
            this.split = RemoteBillSplit(
                id = id, title = title, hostName = hostName, totalPaise = split.totalPaise, version = version,
                items = split.items.map { RemoteBillItem(it.id, it.name, it.quantity, it.amountPaise) },
                people = split.people.map { RemoteBillPerson(it.id, it.name, it.isHost) },
                claims = split.claims.map { RemoteBillClaim(it.itemId, it.personId, it.weight) },
            )
            return BillSplitLink(id = id, token = "token-$created")
        }

        override suspend fun view(token: String): RemoteBillSplit? {
            reach()
            return split
        }

        override suspend fun updateBill(id: String, title: String, totalPaise: Long, items: List<SplitItem>): RemoteBillSplit {
            reach()
            return change { current ->
                current.copy(
                    title = title, totalPaise = totalPaise,
                    items = items.map { RemoteBillItem(it.id, it.name, it.quantity, it.amountPaise) },
                    claims = current.claims.filter { claim -> items.any { it.id == claim.itemId } },
                )
            }
        }

        override suspend fun addPerson(token: String, personId: String, name: String): Pair<String, RemoteBillSplit> {
            reach()
            val existing = (split ?: throw BillSplitEndedException()).people.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (existing != null) return existing.id to split!!
            return personId to change { it.copy(people = it.people + RemoteBillPerson(personId, name)) }
        }

        override suspend fun removePerson(id: String, personId: String): RemoteBillSplit {
            reach()
            return change { current ->
                current.copy(people = current.people.filterNot { it.id == personId }, claims = current.claims.filterNot { it.personId == personId })
            }
        }

        override suspend fun claim(token: String, personId: String, itemId: String, weight: Int): RemoteBillSplit {
            reach()
            // As the server does: a pick for someone who isn't at the table is refused.
            if (split?.people?.none { it.id == personId } == true) throw BillSplitRefusedException("bad_request")
            return change { current ->
                val others = current.claims.filterNot { it.itemId == itemId && it.personId == personId }
                current.copy(claims = if (weight > 0) others + RemoteBillClaim(itemId, personId, weight) else others)
            }
        }

        override suspend fun close(id: String) {
            closed += id
            if (split?.id == id) split = null
        }

        /** Someone opens the link in their browser and types their name. */
        fun friendJoins(name: String): String {
            val id = "web-${++joined}"
            change { it.copy(people = it.people + RemoteBillPerson(id, name)) }
            return id
        }

        fun friendPicks(personId: String, itemId: String, weight: Int = 1) {
            change { it.copy(claims = it.claims.filterNot { c -> c.itemId == itemId && c.personId == personId } + RemoteBillClaim(itemId, personId, weight)) }
        }

        fun friendIsDone(personId: String) {
            change { it.copy(people = it.people.map { p -> if (p.id == personId) p.copy(done = true) else p }) }
        }
    }

    private class FakeStore(var saved: ItemSplitDraft? = null) : ItemSplitStore {
        var cleared = 0
        override fun load(): ItemSplitDraft? = saved
        override fun save(draft: ItemSplitDraft) {
            saved = draft
        }
        override fun clear() {
            cleared++
            saved = null
        }
    }

    private val messages = mutableListOf<String>()
    private var clock = 1_000_000L
    private var ids = 0

    private fun TestScope.controller(table: FakeTable? = FakeTable(), store: FakeStore = FakeStore()) = ItemSplitController(
        scope = backgroundScope,
        remote = table,
        store = store,
        showMessage = { messages += it },
        resolveText = { id, _ -> id.toString() },
        failureText = { it.name },
        io = StandardTestDispatcher(testScheduler),
        newId = { "id-${++ids}" },
        now = { clock },
    ).also { settle() }

    /** Lets everything due now (and for the next [millis]) run, the watch loop included. */
    private fun TestScope.settle(millis: Long = 0) {
        if (millis > 0) advanceTimeBy(millis)
        runCurrent()
    }

    private fun said(resource: Int) = resource.toString() in messages

    private val dinner = ItemSplitSeed(hostName = "Vaibhav", title = "Toit", totalPaise = 33_000, friends = listOf(7L to "Rohan"))

    private fun ItemSplitController.draft(): ItemSplitDraft = checkNotNull(state.value.draft) { "no split in progress" }
    private fun ItemSplitController.item(name: String): String = draft().items.first { it.name == name }.id
    private fun ItemSplitController.person(name: String): String = draft().people.first { it.name == name }.id

    /** Biryani ₹200 and water ₹100, on a bill of ₹330. */
    private fun ItemSplitController.withTheBill() {
        saveItem(null, "Biryani", 1, 20_000)
        saveItem(null, "Water", 2, 10_000)
    }

    // ------------------------------------------------------------------------------------------
    // On this phone alone
    // ------------------------------------------------------------------------------------------

    @Test
    fun `a new split starts with me and the friends ticked on the entry`() = runTest {
        val controller = controller()
        controller.open(dinner, read = null)
        settle()

        val draft = controller.draft()
        assertTrue(controller.state.value.isOpen)
        assertEquals(listOf("Vaibhav", "Rohan"), draft.people.map { it.name })
        assertEquals(listOf(-1L, 7L), draft.people.map { it.ledgerId })
        assertTrue("I'm the one being picked for first", draft.people.first { it.id == draft.activePersonId }.isHost)
        assertEquals("the entry's amount is the bill's total", 33_000L, draft.totalPaise)
        assertFalse("nothing is shared until asked", draft.isShared)
    }

    @Test
    fun `picks add up to the bill to the paisa, and survive the sheet closing`() = runTest {
        val store = FakeStore()
        val controller = controller(store = store)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.setActive(controller.person("Rohan"))
        controller.toggle(controller.item("Biryani"))
        // The water went round the table: nobody ticks it.

        controller.close()
        settle(1_000)
        assertFalse(controller.state.value.isOpen)
        assertEquals("the draft is kept for coming back to", controller.draft(), store.saved)

        controller.open(dinner, read = null)
        settle()
        val amounts = ItemSplitMath.finalAmounts(controller.draft().split)
        // Water 50/50; extras of ₹30 follow the items: 5/30 and 25/30.
        assertEquals(5_000L + 500L, amounts.getValue(controller.person("Vaibhav")))
        assertEquals(25_000L + 2_500L, amounts.getValue(controller.person("Rohan")))
        assertEquals(33_000L, amounts.values.sum())
    }

    @Test
    fun `tapping an item again takes it back, and a quantity can be shared unevenly`() = runTest {
        val controller = controller()
        controller.open(dinner, read = null)
        controller.withTheBill()
        val water = controller.item("Water")

        controller.toggle(water)
        controller.setWeight(water, 5)
        assertEquals("no more parts than there are of it", 2, controller.draft().claims.single().weight)
        controller.toggle(water)
        assertTrue(controller.draft().claims.isEmpty())

        controller.toggle(controller.item("Biryani"))
        controller.removeItem(controller.item("Biryani"))
        assertTrue("a removed item takes its picks with it", controller.draft().claims.isEmpty())
    }

    @Test
    fun `reading the bill fills the items, and the total only when the entry has none`() = runTest {
        val read: suspend () -> BillItemsReadResult = {
            BillItemsReadResult.Read(listOf(BillItemLine("Paneer Tikka", 1, 420.0), BillItemLine("Butter Naan", 3, 180.0)), total = 661.5)
        }

        val withAmount = controller()
        withAmount.open(dinner, read)
        settle()
        assertEquals(listOf("Paneer Tikka", "Butter Naan"), withAmount.draft().items.map { it.name })
        assertEquals(listOf(42_000L, 18_000L), withAmount.draft().items.map { it.amountPaise })
        assertEquals(3, withAmount.draft().items[1].quantity)
        assertEquals("the amount typed on the entry wins", 33_000L, withAmount.draft().totalPaise)
        assertFalse(withAmount.state.value.reading)

        val withoutAmount = controller()
        withoutAmount.open(dinner.copy(totalPaise = 0), read)
        settle()
        assertEquals("with none typed, the bill's own total is used", 66_150L, withoutAmount.draft().totalPaise)
    }

    @Test
    fun `a bill that can't be read says why and is left to be typed`() = runTest {
        val controller = controller()
        controller.open(dinner) { BillItemsReadResult.Failed(AiFailure.Offline) }
        settle()

        assertEquals(listOf(AiFailure.Offline.name), messages)
        assertTrue(controller.draft().items.isEmpty())
        assertTrue("it can be tried again", controller.state.value.canRead)

        messages.clear()
        val empty = controller()
        empty.open(dinner) { BillItemsReadResult.Read(emptyList(), total = 330.0) }
        settle()
        assertTrue(said(R.string.item_split_read_none))
    }

    @Test
    fun `without an account it still splits, and says why it can't share`() = runTest {
        val controller = controller(table = null)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.toggle(controller.item("Biryani"))
        controller.share()
        settle()

        assertFalse(controller.state.value.canShare)
        assertTrue(said(R.string.item_split_share_needs_account))
        assertEquals(1, controller.draft().claims.size)
    }

    // ------------------------------------------------------------------------------------------
    // Shared with the table
    // ------------------------------------------------------------------------------------------

    @Test
    fun `sharing sends the bill as it stands, and a friend's picks from the link arrive`() = runTest {
        val table = FakeTable()
        val controller = controller(table)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.toggle(controller.item("Water"))
        controller.share()
        settle()

        assertEquals("token-1", controller.draft().link?.token)
        assertEquals(listOf("Biryani", "Water"), table.split!!.items.map { it.name })
        assertEquals("my pick went up with the bill", 1, table.split!!.claims.size)
        assertEquals(33_000L, table.split!!.totalPaise)

        val priya = table.friendJoins("Priya")
        table.friendPicks(priya, controller.item("Biryani"))
        table.friendIsDone(priya)
        settle(2_500)

        val draft = controller.draft()
        assertEquals(listOf("Vaibhav", "Rohan", "Priya"), draft.people.map { it.name })
        assertTrue(draft.people.first { it.id == priya }.done)
        assertNull("she isn't anyone in this phone's ledger yet", draft.people.first { it.id == priya }.ledgerId)
        assertEquals("Rohan is still the friend he was", 7L, draft.people.first { it.name == "Rohan" }.ledgerId)
        assertTrue(draft.claims.any { it.personId == priya && it.itemId == controller.item("Biryani") })
        assertTrue("nothing to complain about", messages.isEmpty())
    }

    @Test
    fun `my own pick reaches the table, and one that never arrived is put back`() = runTest {
        val table = FakeTable()
        val controller = controller(table)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.share()
        settle()
        val biryani = controller.item("Biryani")
        val water = controller.item("Water")

        controller.toggle(biryani)
        assertEquals("shown at once", 1, controller.draft().claims.size)
        settle()
        assertEquals("and kept by the server", listOf(biryani), table.split!!.claims.map { it.itemId })

        table.offline = true
        controller.toggle(water)
        settle()
        assertTrue(said(R.string.item_split_sync_failed))

        table.offline = false
        settle(2_500)
        assertEquals("the pick the table never got is gone again", listOf(biryani), controller.draft().claims.map { it.itemId })
    }

    @Test
    fun `correcting the bill while it's shared reaches the table and keeps the picks`() = runTest {
        val table = FakeTable()
        val controller = controller(table)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.share()
        settle()
        val priya = table.friendJoins("Priya")
        table.friendPicks(priya, controller.item("Biryani"))
        settle(2_500)

        controller.saveItem(controller.item("Biryani"), "Chicken biryani", 1, 24_000)
        settle()

        assertEquals("Chicken biryani", table.split!!.items.first().name)
        assertEquals(24_000L, table.split!!.items.first().amountPaise)
        assertEquals("Priya's pick is still hers", 1, controller.draft().claims.count { it.personId == priya })
    }

    @Test
    fun `a name added here that a friend already joined as is the same person`() = runTest {
        val table = FakeTable()
        val controller = controller(table)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.share()
        settle()
        val priya = table.friendJoins("Priya")
        // Before this phone has heard about her:
        controller.addPerson("priya")
        controller.toggle(controller.item("Water"))
        settle()

        val draft = controller.draft()
        assertEquals("one Priya, the one from the link", listOf(priya), draft.people.filter { it.name.equals("Priya", ignoreCase = true) }.map { it.id })
        assertEquals(priya, draft.activePersonId)
        assertEquals(3, table.split!!.people.size)
        assertEquals("and the pick made in between is hers on the server", listOf(priya), table.split!!.claims.map { it.personId })
        assertTrue("with nothing to complain about", messages.isEmpty())
    }

    @Test
    fun `a full table is said, and the person isn't kept`() = runTest {
        val table = FakeTable()
        val controller = controller(table)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.share()
        settle()

        table.refuse = "too_many_people"
        controller.addPerson("Kabir")
        settle()
        table.refuse = null
        settle(2_500)

        assertTrue(said(R.string.item_split_table_full))
        assertEquals(listOf("Vaibhav", "Rohan"), controller.draft().people.map { it.name })
    }

    @Test
    fun `when the link runs out the picks stay, and sharing again makes a new link`() = runTest {
        val table = FakeTable()
        val controller = controller(table)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.share()
        settle()
        val first = controller.draft().splitId
        val priya = table.friendJoins("Priya")
        table.friendPicks(priya, controller.item("Biryani"))
        settle(2_500)

        table.split = null // its day is up
        settle(2_500)

        assertTrue(said(R.string.item_split_link_ended))
        assertFalse(controller.draft().isShared)
        assertTrue("what Priya picked is still here", controller.draft().claims.any { it.personId == priya })

        controller.share()
        settle()
        assertEquals("token-2", controller.draft().link?.token)
        assertNotEquals("a new split on the server, not the dead one", first, controller.draft().splitId)
        assertEquals("with everyone and their picks", 3, table.split!!.people.size)
    }

    @Test
    fun `stopping sharing takes the link down and keeps everything on the phone`() = runTest {
        val table = FakeTable()
        val controller = controller(table)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.share()
        settle()
        val shared = controller.draft().splitId

        controller.stopSharing()
        settle()

        assertEquals(listOf(shared), table.closed)
        assertFalse(controller.draft().isShared)
        assertEquals(2, controller.draft().items.size)
    }

    @Test
    fun `throwing the entry away takes the link down and forgets the draft`() = runTest {
        val table = FakeTable()
        val store = FakeStore()
        val controller = controller(table, store)
        controller.open(dinner, read = null)
        controller.withTheBill()
        controller.share()
        settle(1_000)
        val shared = controller.draft().splitId
        assertNotNull(store.saved)

        controller.discard()
        settle(1_000)

        assertNull(controller.state.value.draft)
        assertFalse(controller.state.value.isOpen)
        assertNull(store.saved)
        assertEquals(listOf(shared), table.closed)
    }

    // ------------------------------------------------------------------------------------------
    // After the app was closed
    // ------------------------------------------------------------------------------------------

    @Test
    fun `a split left open is offered back, still shared`() = runTest {
        val table = FakeTable()
        val first = controller(table, FakeStore())
        first.open(dinner, read = null)
        first.withTheBill()
        first.share()
        settle(1_000)
        val left = first.draft()

        // The app is closed and opened again an hour later.
        clock += 60 * 60 * 1000L
        val again = controller(table, FakeStore(saved = left))

        assertEquals(left, again.state.value.draft)
        assertFalse("it waits to be opened", again.state.value.isOpen)

        val priya = table.friendJoins("Priya")
        again.open(dinner, read = null)
        settle()
        assertTrue("and catches up with the table when it is", again.draft().people.any { it.id == priya })
    }

    @Test
    fun `a split older than a day is cleared, and its link taken down`() = runTest {
        val table = FakeTable()
        val old = ItemSplitDraft(splitId = "old-split", hostName = "Vaibhav", startedAt = clock, link = BillSplitLink("old-split", "old-token"))
        clock += ItemSplitDraft.MAX_AGE_MILLIS + 1
        val store = FakeStore(saved = old)

        val controller = controller(table, store)

        assertNull(controller.state.value.draft)
        assertNull(store.saved)
        assertEquals(listOf("old-split"), table.closed)
    }
}
