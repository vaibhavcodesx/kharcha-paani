package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.db.dao.GroupDao
import com.example.data.db.dao.PersonDao
import com.example.data.db.dao.SettlementDao
import com.example.data.db.dao.SplitDao
import com.example.data.db.dao.TransactionDao
import com.example.data.db.entity.GroupEntity
import com.example.data.db.entity.GroupMemberEntity
import com.example.data.db.entity.PersonEntity
import com.example.data.db.entity.SettlementEntity
import com.example.data.db.entity.SplitEntity
import com.example.data.db.entity.SplitShareEntity
import com.example.data.db.entity.TransactionEntity
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
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao
    abstract fun splitDao(): SplitDao
    abstract fun settlementDao(): SettlementDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "splitflow.db"
                )
                .fallbackToDestructiveMigration()
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
