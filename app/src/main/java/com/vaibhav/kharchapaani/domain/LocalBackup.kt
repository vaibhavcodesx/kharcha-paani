package com.vaibhav.kharchapaani.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A backup of everything the server will never hold.
 *
 * ## What that turned out to mean
 *
 * The first version of this file backed up "rows involving a friend with no account". That was too
 * narrow, and the narrowness was invisible until a real export came back with an empty `splits`
 * array next to a friend who was owed money. Reading the sync engine rather than reasoning about
 * it gives the actual rule, and it is much wider:
 *
 * - `SyncDao.pendingPersonalTransactions` selects `... AND groupId IS NULL AND isSplit = 0`.
 *   **Every split expense is excluded from upload** — the whole transaction, not just its shares,
 *   and regardless of who it was split with.
 * - `GroupSyncEngine.buildGroupExpense` returns null unless the transaction's group exists and
 *   every participant has a profile, so a local-only group's expenses never upload either.
 * - The same class returns null for a settlement with no `groupId`, so **a settlement reaches
 *   groupmates only inside a shared group** — a settlement between two accounts outside one is
 *   local as well. (One with a friend who has no account goes up privately since 1.3.0; below.)
 *
 * So the predicate is not "does a local friend appear in it" but "would the sync engine ever
 * upload it". [isUnsyncable] is that predicate, kept next to the format it decides the contents of.
 *
 * ## Since 1.3.0 it over-includes, on purpose
 *
 * Local-friend sync (docs/local-friend-sync-v2.md) uploads splits and IOUs with friends who have no
 * account, made from 1.3.0 on, to `local_splits` and `local_settlements`. The three rules above
 * still describe the shared-group path, and this file still carries every split and every
 * settlement outside a shared group, so those newer records are in the backup *and* on the server.
 * That is safe because [com.vaibhav.kharchapaani.data.repository.LocalBackupRepository.restore] is
 * additive and recognises them: transactions by `remoteId`, settlements by who, when and the amount
 * to the paisa. `LocalBackupOverCloudRestoreTest` restores a backup over the same records pulled from
 * the cloud and asserts the balance doesn't move. Narrowing the file to `LOCAL_ONLY` rows would
 * make it smaller, at the cost of a backup that can't stand in for the cloud.
 *
 * ## What is still true
 *
 * A normal app update keeps all of this — verified on 2026-09-19 by installing v1.0.1 over the
 * published v1.0.0 and watching a local friend, a subscription and a custom category come through
 * with `firstInstallTime` unchanged. This file is for uninstalling, a new phone, and a change of
 * signing key. It is insurance, not routine.
 *
 * ## Why names, not ids
 *
 * Row ids are `autoGenerate` and mean nothing outside the database that issued them. People are
 * written out by name and referred to by name everywhere else, so a restore resolves them to
 * whatever ids the target database ends up using. `null` means "you", which the ledger stores as
 * `personId = -1` and which is stable across installs in a way a generated id is not.
 */
@Serializable
data class LocalBackup(
    /**
     * 1: the first format. Its `splits` array was always empty because of the bug above; a v1 file
     * still restores, it simply has little in it.
     *
     * 2: transactions carry their own split, their payer, their type and their group.
     */
    val version: Int = FORMAT_VERSION,
    @SerialName("exported_at") val exportedAt: Long,
    @SerialName("app_version") val appVersion: String,
    val people: List<BackupPerson> = emptyList(),
    val groups: List<BackupGroup> = emptyList(),
    val subscriptions: List<BackupSubscription> = emptyList(),
    val settlements: List<BackupSettlement> = emptyList(),
    /** Every expense the server would never receive, with its split inline. */
    val transactions: List<BackupTransaction> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
) {

    val isEmpty: Boolean
        get() = people.isEmpty() && groups.isEmpty() && subscriptions.isEmpty() &&
            settlements.isEmpty() && transactions.isEmpty() && settings.isEmpty

    /** What the UI reports, in the order a person would check it. */
    fun summary(): String = listOfNotNull(
        people.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "friend" else "friends"}" },
        groups.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "group" else "groups"}" },
        transactions.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "expense" else "expenses"}" },
        settlements.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "settlement" else "settlements"}" },
        subscriptions.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "subscription" else "subscriptions"}" },
    ).joinToString(", ").ifEmpty { "settings only" }

    companion object {
        const val FORMAT_VERSION = 2

        /** Pretty-printed on purpose: a backup you can open and read is one you can check. */
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(backup: LocalBackup): String = json.encodeToString(backup)

        /**
         * Reads a backup file, or says why it could not.
         *
         * A file chosen by hand from a picker is the one entirely arbitrary input here, so every
         * failure is a message rather than a crash.
         */
        fun decode(text: String): BackupReadResult = try {
            val backup = json.decodeFromString<LocalBackup>(text)
            if (backup.version > FORMAT_VERSION) BackupReadResult.TooNew(backup.version)
            else BackupReadResult.Ok(backup)
        } catch (e: Exception) {
            BackupReadResult.Unreadable
        }

        fun fileName(exportedAt: Long): String =
            "kharcha-paani-backup-${java.time.Instant.ofEpochMilli(exportedAt).toString().take(10)}.json"

        /**
         * Whether a transaction is one the sync engine would never upload, and so one this file
         * has to carry.
         *
         * Mirrors `SyncDao.pendingPersonalTransactions` and `GroupSyncEngine.buildGroupExpense`.
         * Deliberately errs towards including: a row backed up twice is de-duplicated on restore,
         * a row left out is gone.
         *
         * @param inSharedGroup null when the transaction belongs to no group.
         */
        fun isUnsyncable(isSplit: Boolean, inSharedGroup: Boolean?): Boolean = when {
            // Excluded by `isSplit = 0`, whoever it was split with. Since 1.3.0 a new personal split
            // with friends who have no account goes up through `local_splits` instead; it is kept
            // here anyway (see "Since 1.3.0 it over-includes" above).
            isSplit -> true
            // No group: an ordinary personal expense, which does upload.
            inSharedGroup == null -> false
            // A local-only group's expenses have nowhere on the server to go.
            else -> !inSharedGroup
        }
    }
}

sealed interface BackupReadResult {
    data class Ok(val backup: LocalBackup) : BackupReadResult

    /** Written by a newer version of the app than this one. */
    data class TooNew(val version: Int) : BackupReadResult

    /** Not a backup file at all, or damaged. */
    data object Unreadable : BackupReadResult
}

/** A friend who has no account. [name] is the key everything else refers to them by. */
@Serializable
data class BackupPerson(
    val name: String,
    @SerialName("avatar_color") val avatarColor: String,
    @SerialName("avatar_emoji") val avatarEmoji: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

/** A group that never left this phone, with its members named rather than numbered. */
@Serializable
data class BackupGroup(
    val name: String,
    val emoji: String,
    val description: String = "",
    @SerialName("color_hex") val colorHex: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("archived_at") val archivedAt: Long? = null,
    /** Local friends' names. "You" is always a member and is not listed. */
    @SerialName("member_names") val memberNames: List<String> = emptyList(),
)

@Serializable
data class BackupSubscription(
    val name: String,
    val amount: Double,
    val category: String,
    @SerialName("payment_method") val paymentMethod: String,
    val cycle: String,
    @SerialName("start_date") val startDate: Long,
    @SerialName("charged_through") val chargedThrough: Long? = null,
    @SerialName("cancelled_at") val cancelledAt: Long? = null,
    @SerialName("created_at") val createdAt: Long,
)

/**
 * Money that changed hands outside a shared group, in either direction, IOU or settlement.
 *
 * [fromName] and [toName] are null for "you". Direction is the whole meaning of the row, so it is
 * carried literally rather than normalised into an amount with a sign.
 */
@Serializable
data class BackupSettlement(
    @SerialName("from_name") val fromName: String? = null,
    @SerialName("to_name") val toName: String? = null,
    val amount: Double,
    val date: Long,
    val note: String = "",
    @SerialName("is_iou") val isIou: Boolean = false,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

/**
 * An expense the server would never receive, carrying its split.
 *
 * [remoteId] is kept so a restore onto a phone that has already synced can recognise the same row
 * rather than adding a second copy of it.
 */
@Serializable
data class BackupTransaction(
    @SerialName("remote_id") val remoteId: String? = null,
    /** "expense" or "income". */
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    @SerialName("payment_method") val paymentMethod: String,
    val date: Long,
    val note: String = "",
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("created_at") val createdAt: Long,
    val split: BackupSplit? = null,
)

/**
 * How one expense was divided.
 *
 * [paidByName] is the field whose absence would corrupt a ledger quietly: an expense the friend
 * paid and an expense you paid are the same rows with the payer swapped, and restoring the second
 * as the first flips who owes whom without failing.
 */
@Serializable
data class BackupSplit(
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("split_type") val splitType: String,
    @SerialName("paid_by_name") val paidByName: String? = null,
    val shares: List<BackupShare> = emptyList(),
)

@Serializable
data class BackupShare(
    val name: String? = null,
    val amount: Double,
    val percentage: Double? = null,
)

/** The choices that live in DataStore rather than in the database. */
@Serializable
data class BackupSettings(
    @SerialName("custom_categories") val customCategories: List<String> = emptyList(),
    @SerialName("hidden_categories") val hiddenCategories: List<String> = emptyList(),
    @SerialName("custom_income_categories") val customIncomeCategories: List<String> = emptyList(),
    @SerialName("custom_payment_methods") val customPaymentMethods: List<String> = emptyList(),
    @SerialName("hidden_payment_methods") val hiddenPaymentMethods: List<String> = emptyList(),
    @SerialName("ask_before_discarding") val askBeforeDiscarding: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = customCategories.isEmpty() && hiddenCategories.isEmpty() &&
            customIncomeCategories.isEmpty() && customPaymentMethods.isEmpty() &&
            hiddenPaymentMethods.isEmpty() && askBeforeDiscarding == null
}

/** What a restore did, for the message afterwards. */
data class RestoreReport(
    val peopleAdded: Int = 0,
    val peopleSkipped: Int = 0,
    val groupsAdded: Int = 0,
    val groupsSkipped: Int = 0,
    val subscriptionsAdded: Int = 0,
    val subscriptionsSkipped: Int = 0,
    val settlementsAdded: Int = 0,
    val settlementsSkipped: Int = 0,
    val transactionsAdded: Int = 0,
    val transactionsSkipped: Int = 0,
    val settingsRestored: Boolean = false,
) {
    val added: Int get() = peopleAdded + groupsAdded + subscriptionsAdded + settlementsAdded + transactionsAdded
    val skipped: Int get() = peopleSkipped + groupsSkipped + subscriptionsSkipped + settlementsSkipped + transactionsSkipped

    /**
     * Says what was skipped as well as what was added. A restore run twice should be visibly a
     * no-op the second time, not a success that leaves you unsure whether it duplicated the lot.
     */
    fun message(): String = when {
        added == 0 && skipped == 0 && settingsRestored -> "Settings restored. Nothing else in that backup was missing."
        added == 0 && skipped > 0 -> "Everything in that backup was already here. Nothing changed."
        added == 0 -> "Nothing to restore from that backup."
        skipped == 0 -> "Restored $added ${if (added == 1) "item" else "items"}."
        else -> "Restored $added ${if (added == 1) "item" else "items"}; $skipped were already here."
    }
}
