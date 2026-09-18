package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.vaibhav.kharchapaani.data.db.dao.GroupDao
import com.vaibhav.kharchapaani.data.db.dao.GroupSyncDao
import com.vaibhav.kharchapaani.data.db.dao.PersonDao
import com.vaibhav.kharchapaani.data.db.dao.SettlementDao
import com.vaibhav.kharchapaani.data.db.dao.SplitDao
import com.vaibhav.kharchapaani.data.db.dao.SyncDao
import com.vaibhav.kharchapaani.data.db.dao.TransactionDao
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PendingRemoteDeleteEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.db.dao.SubscriptionDao
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity

@Database(
    entities = [
        PersonEntity::class,
        TransactionEntity::class,
        SplitEntity::class,
        SplitShareEntity::class,
        SettlementEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        PendingRemoteDeleteEntity::class,
        SyncMetaEntity::class,
        SubscriptionEntity::class,
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao
    abstract fun splitDao(): SplitDao
    abstract fun settlementDao(): SettlementDao
    abstract fun groupDao(): GroupDao
    abstract fun syncDao(): SyncDao
    abstract fun groupSyncDao(): GroupSyncDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        /**
         * Every schema change adds its [Migration] here, with a snapshot in
         * app/schemas and a MigrationTestHelper test. There is deliberately no destructive
         * fallback: a missing migration crashes on open instead of silently wiping the ledger.
         */
        internal val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        /** Open databases through [LedgerDatabases], which scopes the file to the signed-in account. */
        internal fun builder(context: Context, name: String): Builder<AppDatabase> =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
    }
}
