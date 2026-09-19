package com.vaibhav.kharchapaani.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A backup of the half of the ledger the server does not hold.
 *
 * Everything else — your own expenses, shared groups and their expenses and settlements, your
 * profile — comes back on its own when you sign in, because it is on the server. These do not:
 * there is no `people` table up there, and every row that names a person does so by profile id, so
 * a friend with no account has nowhere to live but this phone. The same goes for local-only groups,
 * subscriptions, and your category and payment-method choices.
 *
 * **This is insurance, not routine.** A normal update keeps all of it — verified on 2026-09-19 by
 * installing v1.0.1 over the published v1.0.0 and watching a local friend, a subscription and a
 * custom category come through untouched. What loses it is uninstalling, a new phone, or a change
 * of signing key (which forces an uninstall). Those are the cases this file is for.
 *
 * ## Why people are matched by name on restore
 *
 * Row ids are `autoGenerate` and mean nothing outside the database that issued them. A backup
 * restored into a different install would point at whatever happens to hold id 4 there. So people
 * are written out by name, every reference to them is by name, and [Restore] resolves those names
 * to whatever ids the target database ends up using.
 */
@Serializable
data class LocalBackup(
    /** Bumped when the shape changes in a way an older app could not read. */
    val version: Int = FORMAT_VERSION,
    @SerialName("exported_at") val exportedAt: Long,
    @SerialName("app_version") val appVersion: String,
    val people: List<BackupPerson> = emptyList(),
    val groups: List<BackupGroup> = emptyList(),
    val subscriptions: List<BackupSubscription> = emptyList(),
    val settlements: List<BackupSettlement> = emptyList(),
    val splits: List<BackupSplit> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
) {
    /** Nothing local-only on this phone; offering to write the file would be a lie. */
    val isEmpty: Boolean
        get() = people.isEmpty() && groups.isEmpty() && subscriptions.isEmpty() &&
            settlements.isEmpty() && splits.isEmpty() && settings.isEmpty

    /** What the UI reports, in the order a person would check it. */
    fun summary(): String = listOfNotNull(
        people.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "friend" else "friends"}" },
        groups.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "group" else "groups"}" },
        subscriptions.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "subscription" else "subscriptions"}" },
        (settlements.size + splits.size).takeIf { it > 0 }?.let { "$it ${if (it == 1) "record" else "records"}" },
    ).joinToString(", ").ifEmpty { "settings only" }

    companion object {
        const val FORMAT_VERSION = 1

        /** Pretty-printed on purpose: a backup you can open and read is one you can trust. */
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(backup: LocalBackup): String = json.encodeToString(backup)

        /**
         * Reads a backup file, or fails with why.
         *
         * A file chosen by hand from a file picker is the one input here that is entirely
         * arbitrary, so every failure is a message rather than a crash.
         */
        fun decode(text: String): BackupReadResult = try {
            val backup = json.decodeFromString<LocalBackup>(text)
            when {
                backup.version > FORMAT_VERSION ->
                    BackupReadResult.TooNew(backup.version)
                else -> BackupReadResult.Ok(backup)
            }
        } catch (e: Exception) {
            BackupReadResult.Unreadable
        }

        fun fileName(exportedAt: Long): String =
            "kharcha-paani-backup-${java.time.Instant.ofEpochMilli(exportedAt).toString().take(10)}.json"
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
 * Money that changed hands with a local friend.
 *
 * [fromName] and [toName] are null for "you", which is how the ledger stores it (`personId = -1`)
 * and is stable across installs in a way a profile id would not need to be.
 */
@Serializable
data class BackupSettlement(
    @SerialName("from_name") val fromName: String? = null,
    @SerialName("to_name") val toName: String? = null,
    val amount: Double,
    val date: Long,
    val note: String = "",
    @SerialName("is_iou") val isIou: Boolean = false,
    @SerialName("created_at") val createdAt: Long,
)

/**
 * A split that gives a local friend a share, and the expense it belongs to.
 *
 * The expense itself may well be on the server — personal transactions sync — but its *split* is
 * not, because shares are stored server-side against profile ids and a local friend has none. So
 * the expense is carried here too, and [Restore] attaches the split to the copy that came back
 * from the server when it can find it, rather than creating a second one.
 */
@Serializable
data class BackupSplit(
    @SerialName("transaction_remote_id") val transactionRemoteId: String? = null,
    val merchant: String,
    val category: String,
    @SerialName("payment_method") val paymentMethod: String,
    val date: Long,
    val note: String = "",
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("split_type") val splitType: String,
    /** Name to share. Null name is your own share. */
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
    @SerialName("hidden_income_categories") val hiddenIncomeCategories: List<String> = emptyList(),
    @SerialName("custom_payment_methods") val customPaymentMethods: List<String> = emptyList(),
    @SerialName("hidden_payment_methods") val hiddenPaymentMethods: List<String> = emptyList(),
    @SerialName("ask_before_discarding") val askBeforeDiscarding: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = customCategories.isEmpty() && hiddenCategories.isEmpty() &&
            customIncomeCategories.isEmpty() && hiddenIncomeCategories.isEmpty() &&
            customPaymentMethods.isEmpty() && hiddenPaymentMethods.isEmpty() &&
            askBeforeDiscarding == null
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
    val splitsAdded: Int = 0,
    val splitsSkipped: Int = 0,
    val settingsRestored: Boolean = false,
) {
    val added: Int get() = peopleAdded + groupsAdded + subscriptionsAdded + settlementsAdded + splitsAdded
    val skipped: Int get() = peopleSkipped + groupsSkipped + subscriptionsSkipped + settlementsSkipped + splitsSkipped

    /**
     * Deliberately says what was skipped as well as what was added. A restore run twice should be
     * visibly a no-op the second time, not silently "successful" in a way that leaves you unsure
     * whether it duplicated anything.
     */
    fun message(): String = when {
        added == 0 && skipped == 0 && settingsRestored -> "Settings restored. Nothing else in that backup was missing."
        added == 0 && skipped > 0 -> "Everything in that backup was already here. Nothing changed."
        added == 0 -> "Nothing to restore from that backup."
        skipped == 0 -> "Restored $added ${if (added == 1) "item" else "items"}."
        else -> "Restored $added ${if (added == 1) "item" else "items"}; $skipped were already here."
    }
}
