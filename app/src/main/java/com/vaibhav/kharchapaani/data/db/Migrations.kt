package com.vaibhav.kharchapaani.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

        // SQLite has no uuid(); build a v4 UUID per row: version nibble 4, variant nibble 8-b.
        db.execSQL(
            """
            UPDATE `transactions` SET
                `remoteId` = lower(
                    hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' ||
                    substr(hex(randomblob(2)), 2) || '-' ||
                    substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' ||
                    hex(randomblob(6))
                ),
                `updatedAt` = `createdAt`
            """.trimIndent()
        )

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
