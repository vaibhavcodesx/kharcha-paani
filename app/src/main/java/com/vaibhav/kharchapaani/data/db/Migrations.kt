package com.vaibhav.kharchapaani.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// SQLite has no uuid(); build a v4 UUID per row: version nibble 4, variant nibble 8-b.
private const val UUID_V4_SQL = """lower(
    hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' ||
    substr(hex(randomblob(2)), 2) || '-' ||
    substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' ||
    hex(randomblob(6))
)"""

/**
 * v3: sync bookkeeping for personal transactions (Migration Plan step 3).
 *
 * Existing rows stay LOCAL_ONLY: pre-sync history is uploaded (or not) by step 5, never
 * implicitly by this migration. Each still gets a unique UUID v4 remoteId so it can sync later.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")

        db.execSQL("UPDATE `transactions` SET `remoteId` = $UUID_V4_SQL, `updatedAt` = `createdAt`")

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_remoteId` ON `transactions` (`remoteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_syncState` ON `transactions` (`syncState`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_remote_deletes` " +
                "(`remoteId` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`remoteId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_meta` " +
                "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
        )
    }
}

/**
 * v4: shared groups (Migration Plan step 4). Purely additive.
 *
 * Existing groups and settlements stay LOCAL_ONLY (decision 7) and existing people have no
 * profile, so nothing here makes old data start syncing. Rows that could sync later get a
 * unique v4 remoteId now so the unique indexes hold from the start.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `people` ADD COLUMN `profileId` TEXT")
        db.execSQL("ALTER TABLE `people` ADD COLUMN `avatarUrl` TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_profileId` ON `people` (`profileId`)")

        db.execSQL("ALTER TABLE `groups` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `groups` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `groups` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
        db.execSQL("ALTER TABLE `groups` ADD COLUMN `archivedAt` INTEGER")
        db.execSQL("ALTER TABLE `groups` ADD COLUMN `createdByProfileId` TEXT")
        db.execSQL("UPDATE `groups` SET `remoteId` = $UUID_V4_SQL, `updatedAt` = `createdAt`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_groups_remoteId` ON `groups` (`remoteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_syncState` ON `groups` (`syncState`)")

        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `ownerProfileId` TEXT")

        db.execSQL("ALTER TABLE `splits` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `splits` SET `remoteId` = $UUID_V4_SQL")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_splits_remoteId` ON `splits` (`remoteId`)")

        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
        db.execSQL("UPDATE `settlements` SET `remoteId` = $UUID_V4_SQL, `updatedAt` = `createdAt`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_settlements_remoteId` ON `settlements` (`remoteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_syncState` ON `settlements` (`syncState`)")

        db.execSQL("ALTER TABLE `pending_remote_deletes` ADD COLUMN `entityType` TEXT NOT NULL DEFAULT 'transaction'")
    }
}

/** v5: groupmates' emoji avatars (backlog item 6). Purely additive; everyone starts on initials. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `people` ADD COLUMN `avatarEmoji` TEXT")
    }
}

/** v6: subscriptions (backlog item 8). A new table only; nothing existing is touched. */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subscriptions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, " +
                "`paymentMethod` TEXT NOT NULL, `cycle` TEXT NOT NULL, `startDate` INTEGER NOT NULL, " +
                "`chargedThrough` INTEGER, `cancelledAt` INTEGER, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_cancelledAt` ON `subscriptions` (`cancelledAt`)")
    }
}

/**
 * A groupmate's UPI handle, so settling up with someone can show where to pay them.
 *
 * Additive and nullable: nobody's rows change, and a person whose groupmate has not set one keeps
 * exactly the behaviour they had.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `people` ADD COLUMN `upiId` TEXT")
    }
}

/**
 * v8 (1.4): settlement confirmation, guests in shared-group expenses, and a groupmate's own colour.
 * Additive only. Every settlement already on the phone is CONFIRMED, as it is on the server.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'CONFIRMED'")
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `recordedByPersonId` INTEGER")
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `respondedAt` INTEGER")
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `responseNote` TEXT")
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `pendingResponse` TEXT")
        db.execSQL("ALTER TABLE `settlements` ADD COLUMN `remindedStage` INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE `split_shares` ADD COLUMN `guestName` TEXT")
        db.execSQL("ALTER TABLE `split_shares` ADD COLUMN `guestPersonId` INTEGER")
        db.execSQL("ALTER TABLE `people` ADD COLUMN `hue` INTEGER")
    }
}

/**
 * v9 (ask-to-pay): a local log of asking a friend to pay you back. Additive: a new table, nothing
 * already on the phone changes.
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `payment_asks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, " +
                "`amount` REAL NOT NULL, `sentAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_asks_personId` ON `payment_asks` (`personId`)")
    }
}

/** v10: bill photos (docs/designs/bill-photos.md). Additive: a new table, nothing else touched. */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, " +
                "`fileName` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `bytes` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `remotePath` TEXT, " +
                "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipts_transactionId` ON `receipts` (`transactionId`)")
    }
}
