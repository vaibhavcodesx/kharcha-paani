package com.vaibhav.kharchapaani.ui.itemsplit

import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.itemsplit.ItemSplitDraft
import com.vaibhav.kharchapaani.data.itemsplit.ItemSplitStore
import com.vaibhav.kharchapaani.data.repository.BillItemsReadResult
import com.vaibhav.kharchapaani.data.sync.BillSplitEndedException
import com.vaibhav.kharchapaani.data.sync.BillSplitLink
import com.vaibhav.kharchapaani.data.sync.BillSplitRefusedException
import com.vaibhav.kharchapaani.data.sync.BillSplitRemote
import com.vaibhav.kharchapaani.data.sync.RemoteBillSplit
import com.vaibhav.kharchapaani.domain.AiFailure
import com.vaibhav.kharchapaani.domain.ItemSplitMath
import com.vaibhav.kharchapaani.domain.SplitClaim
import com.vaibhav.kharchapaani.domain.SplitItem
import com.vaibhav.kharchapaani.domain.SplitPerson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class ItemSplitUiState(
    /** The bill being split, or null when there isn't one. */
    val draft: ItemSplitDraft? = null,
    val isOpen: Boolean = false,
    /** The bill's photo is being read for its items. */
    val reading: Boolean = false,
    /** There's a bill photo that could be read (again). */
    val canRead: Boolean = false,
    /** The link is being made: the bill holds still until it is. */
    val startingShare: Boolean = false,
    /** False on a ledger with no account, which has nobody to share as. */
    val canShare: Boolean = true,
)

/** What the entry sheet knows when "Split by items" is tapped. */
data class ItemSplitSeed(
    val hostName: String,
    val title: String,
    val totalPaise: Long,
    /** Friends already ticked on the entry: ledger id and name. */
    val friends: List<Pair<Long, String>> = emptyList(),
)

/**
 * A bill split by who had what (docs/designs/split-by-items.md). The phone holds the whole split
 * and works with no connection; "Share with the table" puts it behind a link, and from then on
 * every pick goes to the server and everyone's picks come back from it.
 *
 * While shared: a change shows at once, is sent, and the server's reply settles it. People and
 * picks follow the server (friends are changing them too). The bill itself, its items and total,
 * is the host's alone, so this phone's copy wins and is sent again if the server's ever differs.
 *
 * Runs on [scope]'s dispatcher (the main one in the app), so its state needs no locking. Kept free
 * of Android types so the flow can be tested with fakes.
 */
class ItemSplitController(
    private val scope: CoroutineScope,
    private val remote: BillSplitRemote?,
    private val store: ItemSplitStore,
    private val showMessage: (String) -> Unit,
    private val resolveText: (Int, Array<out Any>) -> String,
    private val failureText: (AiFailure) -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
    private val pollMillis: Long = 2_500,
) {
    private fun text(@androidx.annotation.StringRes id: Int, vararg args: Any): String = resolveText(id, args)

    private val _state = MutableStateFlow(ItemSplitUiState(canShare = remote != null))
    val state: StateFlow<ItemSplitUiState> = _state.asStateFlow()

    private val draft: ItemSplitDraft? get() = _state.value.draft
    private var reader: (suspend () -> BillItemsReadResult)? = null
    private var readJob: Job? = null
    private var pollJob: Job? = null
    private var saveJob: Job? = null

    /** Changes sent and not yet answered: while any are, a server snapshot is older than the screen. */
    private var pending = 0
    private var lastVersion = 0L

    /** Times the bill was sent again because the server's copy differed, so a refusal can't loop. */
    private var billResends = 0
    private val sending = Mutex()

    /**
     * Ids this phone made up for people the server already knew under another id (the same name,
     * joined from the link a moment earlier). A pick made in between is sent under the server's.
     */
    private val serverIds = HashMap<String, String>()

    // A split left open when the app was closed: offered back for the day its link lasts.
    private val restored: Job = scope.launch {
        val saved = withContext(io) { store.load() } ?: return@launch
        if (now() - saved.startedAt > ItemSplitDraft.MAX_AGE_MILLIS) {
            withContext(io) { store.clear() }
            saved.link?.let { closeQuietly(it.id) }
        } else if (draft == null) {
            _state.update { it.copy(draft = saved) }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Opening and closing
    // ------------------------------------------------------------------------------------------

    /**
     * Opens the split for the entry on the sheet: a new one from [seed], or the one in progress
     * brought up to date with the entry's place and amount. [read] reads the entry's bill photo,
     * or is null when there's no photo (or no AI) to read it with.
     */
    fun open(seed: ItemSplitSeed, read: (suspend () -> BillItemsReadResult)?) {
        reader = read
        val current = draft
        if (current == null) {
            val host = SplitPerson(id = newId(), name = seed.hostName, isHost = true, ledgerId = ME)
            val friends = seed.friends.map { (ledgerId, name) -> SplitPerson(id = newId(), name = name, ledgerId = ledgerId) }
            put(
                ItemSplitDraft(
                    splitId = newId(),
                    hostName = seed.hostName,
                    title = seed.title,
                    billTotalPaise = seed.totalPaise,
                    people = listOf(host) + friends,
                    activePersonId = host.id,
                    startedAt = now(),
                ),
            )
        } else {
            val total = if (seed.totalPaise > 0) seed.totalPaise else current.billTotalPaise
            if (current.title != seed.title || current.billTotalPaise != total) {
                put(current.copy(title = seed.title, billTotalPaise = total))
                billResends = 0
                pushBill()
            }
        }
        _state.update { it.copy(isOpen = true, canRead = read != null) }
        if (draft?.items.isNullOrEmpty() && read != null) readItems()
        watch()
    }

    /** The sheet closed. The split stays, to be used or come back to. */
    fun close() {
        pollJob?.cancel()
        _state.update { it.copy(isOpen = false) }
    }

    /** The entry was saved or thrown away: the split goes, and its link with it. */
    fun discard() {
        val gone = draft
        readJob?.cancel()
        pollJob?.cancel()
        saveJob?.cancel()
        reader = null
        _state.update { it.copy(draft = null, isOpen = false, reading = false, canRead = false, startingShare = false) }
        scope.launch {
            restored.join()
            withContext(io) { store.clear() }
        }
        gone?.link?.let { closeQuietly(it.id) }
    }

    // ------------------------------------------------------------------------------------------
    // The bill
    // ------------------------------------------------------------------------------------------

    /** Reads the bill photo's lines. They become the items only while nobody has picked anything. */
    fun readItems() {
        val read = reader ?: return
        if (_state.value.reading || holding()) return
        _state.update { it.copy(reading = true) }
        readJob = scope.launch {
            try {
                when (val result = read()) {
                    is BillItemsReadResult.Failed -> showMessage(failureText(result.reason))
                    is BillItemsReadResult.Read -> {
                        val current = draft
                        if (result.items.isEmpty()) {
                            showMessage(text(R.string.item_split_read_none))
                        } else if (current != null && current.claims.isEmpty()) {
                            val items = result.items.map {
                                SplitItem(id = newId(), name = it.name, quantity = it.quantity, amountPaise = ItemSplitMath.paise(it.amount))
                            }
                            // The entry's amount wins; the bill's own total fills in when there's none.
                            val readTotal = result.total
                            val total = if (current.billTotalPaise > 0 || readTotal == null) current.billTotalPaise else ItemSplitMath.paise(readTotal)
                            put(current.copy(items = items, billTotalPaise = total))
                            billResends = 0
                            pushBill()
                        }
                    }
                }
            } finally {
                _state.update { it.copy(reading = false) }
            }
        }
    }

    /** Adds a line, or changes the one with [id]. */
    fun saveItem(id: String?, name: String, quantity: Int, amountPaise: Long) {
        val clean = name.trim().take(MAX_ITEM_NAME)
        val current = draft ?: return
        if (holding() || clean.isEmpty() || amountPaise < 0) return
        if (id == null && current.items.size >= MAX_ITEMS) {
            showMessage(text(R.string.item_split_too_many_items))
            return
        }
        val item = SplitItem(id = id ?: newId(), name = clean, quantity = quantity.coerceIn(1, MAX_QUANTITY), amountPaise = amountPaise)
        put(current.copy(items = if (id == null) current.items + item else current.items.map { if (it.id == id) item else it }))
        billResends = 0
        pushBill()
    }

    fun removeItem(id: String) {
        val current = draft ?: return
        if (holding()) return
        put(current.copy(items = current.items.filterNot { it.id == id }, claims = current.claims.filterNot { it.itemId == id }))
        billResends = 0
        pushBill()
    }

    // ------------------------------------------------------------------------------------------
    // The table
    // ------------------------------------------------------------------------------------------

    /** Adds someone and makes them the one being picked for. A name already there is just selected. */
    fun addPerson(name: String, ledgerId: Long? = null) {
        val clean = name.trim().replace(SPACES, " ").take(MAX_NAME)
        val current = draft ?: return
        if (holding() || clean.isEmpty()) return
        val existing = current.people.firstOrNull { (ledgerId != null && it.ledgerId == ledgerId) || it.name.equals(clean, ignoreCase = true) }
        if (existing != null) {
            // Typed as a name before, now picked as a friend: remember which friend.
            val people = if (ledgerId != null && existing.ledgerId == null) {
                current.people.map { if (it.id == existing.id) it.copy(ledgerId = ledgerId) else it }
            } else {
                current.people
            }
            put(current.copy(people = people, activePersonId = existing.id))
            return
        }
        if (current.people.size >= MAX_PEOPLE) {
            showMessage(text(R.string.item_split_table_full))
            return
        }
        val person = SplitPerson(id = newId(), name = clean, ledgerId = ledgerId)
        put(current.copy(people = current.people + person, activePersonId = person.id))
        push { remote, link ->
            val (serverId, split) = remote.addPerson(link.token, person.id, person.name)
            // Someone typed that name on the page a moment ago: they're the same person.
            val latest = draft
            if (serverId != person.id && latest != null) {
                serverIds[person.id] = serverId
                put(
                    latest.copy(
                        people = latest.people.map { if (it.id == person.id) it.copy(id = serverId) else it }.distinctBy { it.id },
                        claims = latest.claims.map { if (it.personId == person.id) it.copy(personId = serverId) else it },
                        activePersonId = if (latest.activePersonId == person.id) serverId else latest.activePersonId,
                    ),
                )
            }
            split
        }
    }

    fun removePerson(id: String) {
        val current = draft ?: return
        if (holding() || current.people.none { it.id == id && !it.isHost }) return
        put(
            current.copy(
                people = current.people.filterNot { it.id == id },
                claims = current.claims.filterNot { it.personId == id },
                activePersonId = if (current.activePersonId == id) current.host?.id.orEmpty() else current.activePersonId,
            ),
        )
        push { remote, link -> remote.removePerson(link.id, serverIds[id] ?: id) }
    }

    fun setActive(personId: String) {
        val current = draft ?: return
        if (current.people.any { it.id == personId }) put(current.copy(activePersonId = personId))
    }

    /** Ticks or unticks [itemId] for whoever is being picked for. */
    fun toggle(itemId: String) {
        val current = draft ?: return
        val has = current.claims.any { it.itemId == itemId && it.personId == current.activePersonId && it.weight > 0 }
        setWeight(itemId, if (has) 0 else 1)
    }

    /** How many parts of [itemId] the active person had; 0 takes the pick back. */
    fun setWeight(itemId: String, weight: Int) {
        val current = draft ?: return
        val personId = current.activePersonId
        val item = current.items.firstOrNull { it.id == itemId } ?: return
        if (holding() || current.people.none { it.id == personId }) return
        val parts = weight.coerceIn(0, minOf(maxOf(item.quantity, 1), MAX_WEIGHT))
        val others = current.claims.filterNot { it.itemId == itemId && it.personId == personId }
        put(current.copy(claims = if (parts > 0) others + SplitClaim(itemId, personId, parts) else others))
        // The id is looked up when it's sent: by then an earlier "add this person" has been answered.
        push { remote, link -> remote.claim(link.token, serverIds[personId] ?: personId, itemId, parts) }
    }

    // ------------------------------------------------------------------------------------------
    // Sharing
    // ------------------------------------------------------------------------------------------

    /** Puts the bill behind a link friends can open without the app. */
    fun share() {
        val current = draft ?: return
        val server = remote
        if (server == null) {
            showMessage(text(R.string.item_split_share_needs_account))
            return
        }
        if (current.link != null || _state.value.startingShare) return
        if (current.items.isEmpty()) {
            showMessage(text(R.string.item_split_share_needs_items))
            return
        }
        _state.update { it.copy(startingShare = true) }
        scope.launch {
            try {
                val link = server.create(current.splitId, current.title, current.hostName, current.split)
                lastVersion = 0
                billResends = 0
                draft?.let { put(it.copy(link = link)) }
                watch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillSplitRefusedException) {
                showMessage(text(if (e.reason == "too_many_splits") R.string.item_split_share_too_many else R.string.item_split_share_failed))
            } catch (e: Exception) {
                showMessage(text(R.string.item_split_share_failed))
            } finally {
                _state.update { it.copy(startingShare = false) }
            }
        }
    }

    /** Takes the link down. Everyone's picks so far stay on this phone. */
    fun stopSharing() {
        val link = draft?.link ?: return
        pollJob?.cancel()
        unshare()
        closeQuietly(link.id)
    }

    /** The link is gone (finished elsewhere, or its day is up): carry on with what's here. */
    private fun unshare() {
        val current = draft ?: return
        // A new id, so sharing again makes a new link rather than asking for the dead one.
        put(current.copy(link = null, splitId = newId(), people = current.people.map { it.copy(done = false) }))
        lastVersion = 0
    }

    private fun closeQuietly(splitId: String) {
        val server = remote ?: return
        scope.launch {
            try {
                server.close(splitId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // It stops working by itself within a day.
            }
        }
    }

    /** While the sheet is up and the bill is shared, what friends pick arrives every few seconds. */
    private fun watch() {
        pollJob?.cancel()
        val server = remote ?: return
        if (draft?.link == null || !_state.value.isOpen) return
        pollJob = scope.launch {
            while (isActive) {
                val link = draft?.link ?: break
                try {
                    val split = server.view(link.token)
                    if (split == null) {
                        ended()
                        break
                    }
                    take(split)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline for a moment: the next round tries again, quietly.
                }
                delay(pollMillis)
            }
        }
    }

    private fun ended() {
        if (draft?.link == null) return
        unshare()
        showMessage(text(R.string.item_split_link_ended))
    }

    /** Sends one change, in order, and settles on the server's answer. */
    private fun push(send: suspend (BillSplitRemote, BillSplitLink) -> RemoteBillSplit) {
        val server = remote ?: return
        val link = draft?.link ?: return
        pending++
        scope.launch {
            var failed = false
            try {
                val reply = try {
                    sending.withLock { send(server, link) }
                } finally {
                    pending--
                }
                take(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillSplitEndedException) {
                ended()
            } catch (e: BillSplitRefusedException) {
                failed = true
                showMessage(text(if (e.reason == "too_many_people") R.string.item_split_table_full else R.string.item_split_sync_failed))
            } catch (e: Exception) {
                failed = true
                showMessage(text(R.string.item_split_sync_failed))
            }
            // What the server kept is the truth: a change that didn't arrive is put back.
            if (failed) refresh()
        }
    }

    private fun pushBill() {
        push { server, link ->
            val current = draft ?: throw BillSplitEndedException()
            server.updateBill(link.id, current.title, current.totalPaise, current.items)
        }
    }

    private suspend fun refresh() {
        val server = remote ?: return
        val link = draft?.link ?: return
        try {
            val split = server.view(link.token)
            if (split == null) ended() else take(split, force = true, resendBill = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Still unreachable: the watch loop catches up when the connection is back.
        }
    }

    /** The server's picture of the split. Ignored while a change of ours is still on its way. */
    private fun take(split: RemoteBillSplit, force: Boolean = false, resendBill: Boolean = true) {
        val current = draft ?: return
        if (current.link?.id != split.id || pending > 0) return
        if (!force && split.version < lastVersion) return
        lastVersion = split.version
        val known = current.people.associateBy { it.id }
        val people = split.people.map {
            SplitPerson(id = it.id, name = it.name, isHost = it.isHost, done = it.done, ledgerId = if (it.isHost) ME else known[it.id]?.ledgerId)
        }
        put(
            current.copy(
                people = people,
                claims = split.claims.map { SplitClaim(it.itemId, it.personId, it.weight) },
                activePersonId = current.activePersonId.takeIf { id -> people.any { it.id == id } }
                    ?: people.firstOrNull { it.isHost }?.id.orEmpty(),
            ),
        )
        // The bill is this phone's to say: if the server's copy has drifted, send ours again.
        val theirs = split.items.map { SplitItem(it.id, it.name, it.quantity, it.amountPaise) }
        if (theirs == current.items && split.totalPaise == current.totalPaise) {
            billResends = 0
        } else if (resendBill && billResends < MAX_BILL_RESENDS) {
            billResends++
            pushBill()
        }
    }

    // ------------------------------------------------------------------------------------------

    /** True while a change shouldn't be taken: the link is being made from the bill as it stands. */
    private fun holding(): Boolean = _state.value.startingShare

    private fun put(after: ItemSplitDraft) {
        if (after == draft) return
        _state.update { it.copy(draft = after) }
        // Kept a moment after the last change rather than on every tap.
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MILLIS)
            withContext(io) { store.save(after) }
        }
    }

    companion object {
        const val ME = -1L
        const val MAX_ITEMS = 100
        const val MAX_PEOPLE = 30
        const val MAX_NAME = 40
        const val MAX_ITEM_NAME = 80
        const val MAX_QUANTITY = 99
        const val MAX_WEIGHT = 20
        private const val SAVE_DELAY_MILLIS = 400L
        private const val MAX_BILL_RESENDS = 2
        private val SPACES = Regex("\\s+")
    }
}
