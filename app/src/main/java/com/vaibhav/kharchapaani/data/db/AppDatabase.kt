package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.vaibhav.kharchapaani.data.db.dao.GroupDao
import com.vaibhav.kharchapaani.data.db.dao.PersonDao
import com.vaibhav.kharchapaani.data.db.dao.SettlementDao
import com.vaibhav.kharchapaani.data.db.dao.SplitDao
import com.vaibhav.kharchapaani.data.db.dao.TransactionDao
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity

@Database(
    entities = [
        PersonEntity::class,
        TransactionEntity::class,
        SplitEntity::class,
        SplitShareEntity::class,
        SettlementEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao
    abstract fun splitDao(): SplitDao
    abstract fun settlementDao(): SettlementDao
    abstract fun groupDao(): GroupDao

    companion object {
        /**
         * Every schema change adds its [Migration] here, with a snapshot in
         * app/schemas and a MigrationTestHelper test. There is deliberately no destructive
         * fallback: a missing migration crashes on open instead of silently wiping the ledger.
         */
        internal val ALL_MIGRATIONS = emptyArray<Migration>()

        /** Open databases through [LedgerDatabases], which scopes the file to the signed-in account. */
        internal fun builder(context: Context, name: String): Builder<AppDatabase> =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
    }
}
