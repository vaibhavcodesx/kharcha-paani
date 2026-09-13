package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        const val DATABASE_NAME = "splitflow.db"

        /**
         * Every schema change adds its [Migration] here, with a snapshot in
         * app/schemas and a MigrationTestHelper test. There is deliberately no destructive
         * fallback: a missing migration crashes on open instead of silently wiping the ledger.
         */
        internal val ALL_MIGRATIONS = emptyArray<Migration>()

        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal fun builder(context: Context, name: String): Builder<AppDatabase> =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = builder(context, DATABASE_NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default teammates and a sample group
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            val p1 = database.personDao().insertPerson(PersonEntity(name = "Rahul", avatarColor = "#70C1FF"))
                            val p2 = database.personDao().insertPerson(PersonEntity(name = "Priya", avatarColor = "#FFB3BA"))
                            val p3 = database.personDao().insertPerson(PersonEntity(name = "Arjun", avatarColor = "#C6F24C"))
                            val p4 = database.personDao().insertPerson(PersonEntity(name = "Alex", avatarColor = "#FF9E79"))

                            // Seed a default Trip / Event Group "Lonavala 2026"
                            val groupId = database.groupDao().insertGroup(
                                GroupEntity(
                                    name = "Lonavala 2026",
                                    description = "Weekend monsoon villa trip & food",
                                    emoji = "⛰️",
                                    colorHex = "#FF7A3D"
                                )
                            )
                            database.groupDao().insertGroupMembers(
                                listOf(
                                    GroupMemberEntity(groupId = groupId, personId = -1L), // Me
                                    GroupMemberEntity(groupId = groupId, personId = p1),
                                    GroupMemberEntity(groupId = groupId, personId = p3),
                                    GroupMemberEntity(groupId = groupId, personId = p4)
                                )
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
