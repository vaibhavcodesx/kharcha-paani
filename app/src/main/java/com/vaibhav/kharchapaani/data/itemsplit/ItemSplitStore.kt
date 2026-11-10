package com.vaibhav.kharchapaani.data.itemsplit

import android.content.Context
import android.util.Log
import com.vaibhav.kharchapaani.data.sync.BillSplitLink
import com.vaibhav.kharchapaani.domain.ItemSplit
import com.vaibhav.kharchapaani.domain.SplitClaim
import com.vaibhav.kharchapaani.domain.SplitItem
import com.vaibhav.kharchapaani.domain.SplitPerson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A bill being split by items, as this phone holds it (docs/designs/split-by-items.md). It is the
 * whole truth until it's shared; after that the server holds the picks and this follows it.
 */
@Serializable
data class ItemSplitDraft(
    /** The split's id on the server while it's shared. A fresh one each time sharing starts. */
    val splitId: String,
    val hostName: String,
    /** The place, from the entry it belongs to. */
    val title: String = "",
    /** The amount on the entry, or 0 when none is typed yet: the items' sum stands in. */
    val billTotalPaise: Long = 0,
    val items: List<SplitItem> = emptyList(),
    val people: List<SplitPerson> = emptyList(),
    val claims: List<SplitClaim> = emptyList(),
    /** Whose picks a tap on an item changes. */
    val activePersonId: String = "",
    /** Set while friends can pick from the link. */
    val link: BillSplitLink? = null,
    val startedAt: Long = 0,
) {
    val itemsPaise: Long get() = items.sumOf { it.amountPaise }
    val totalPaise: Long get() = if (billTotalPaise > 0) billTotalPaise else itemsPaise
    val split: ItemSplit get() = ItemSplit(totalPaise, items, people, claims)
    val host: SplitPerson? get() = people.firstOrNull { it.isHost }
    val isShared: Boolean get() = link != null

    companion object {
        /** A link works for a day; a draft older than that isn't worth offering back either. */
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}

/** Where the draft waits between the sheet closing (or the app being closed) and its expense saving. */
interface ItemSplitStore {
    fun load(): ItemSplitDraft?
    fun save(draft: ItemSplitDraft)
    fun clear()
}

/**
 * One small file per ledger in the cache folder: it is a day's draft, so losing it to a storage
 * clean-up costs little, and [com.vaibhav.kharchapaani.ui.session.AccountLeftovers] removes the
 * folder when the phone moves to another account.
 */
class FileItemSplitStore(private val file: File) : ItemSplitStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): ItemSplitDraft? = try {
        if (file.exists()) json.decodeFromString<ItemSplitDraft>(file.readText()) else null
    } catch (e: Exception) {
        // A half-written or older-format file: start clean rather than fail every launch.
        Log.w(TAG, "Dropping an unreadable split draft", e)
        file.delete()
        null
    }

    override fun save(draft: ItemSplitDraft) {
        try {
            file.parentFile?.mkdirs()
            val staged = File(file.parentFile, file.name + ".tmp")
            staged.writeText(json.encodeToString(ItemSplitDraft.serializer(), draft))
            if (!staged.renameTo(file)) {
                file.delete()
                staged.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't keep the split draft", e)
        }
    }

    override fun clear() {
        file.delete()
    }

    companion object {
        private const val TAG = "ItemSplitStore"
        const val DIR = "item-split"

        /** [ledgerName] is the ledger's own file name, so each account and the local ledger stay apart. */
        fun forLedger(context: Context, ledgerName: String): FileItemSplitStore =
            FileItemSplitStore(File(File(context.cacheDir, DIR), ledgerName.removeSuffix(".db") + ".json"))
    }
}
