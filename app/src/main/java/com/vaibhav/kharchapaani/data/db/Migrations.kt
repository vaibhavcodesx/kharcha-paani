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
