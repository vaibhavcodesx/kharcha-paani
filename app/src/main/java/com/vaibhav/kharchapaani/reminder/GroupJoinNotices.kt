package com.vaibhav.kharchapaani.reminder

import android.content.Context
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.dao.SyncDao
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.domain.NotificationPrefs
import com.vaibhav.kharchapaani.util.NotificationHelper
import java.time.Instant
import java.time.ZoneId

/** Tracks a group and member pair in the account's ledger so a retry cannot enqueue it twice. */
internal object GroupJoinNotices {
    private const val KEY_PREFIX = "group_join:"
    private const val PENDING = "pending"
    private const val SEEN = "seen"

    suspend fun record(sync: SyncDao, groupId: String, profileId: String) {
        sync.insertMetaIfAbsent(SyncMetaEntity("$KEY_PREFIX$groupId:$profileId", PENDING))
    }

    suspend fun deliver(
        context: Context,
        db: AppDatabase,
        prefs: NotificationPrefs,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val localTime = Instant.ofEpochMilli(now).atZone(zone)
        if (prefs.quietHours.isQuietAt(localTime.hour * 60 + localTime.minute)) return 0
        if (!NotificationHelper.canNotify(context, NotificationHelper.CHANNEL_GROUP_JOINS)) return 0

        val sync = db.syncDao()
        val groups = db.groupSyncDao()
        var delivered = 0
        for (key in sync.pendingGroupJoinNotices()) {
            val ids = key.removePrefix(KEY_PREFIX)
            val group = groups.groupByRemoteId(ids.substringBefore(':'))
            val member = groups.personByProfileId(ids.substringAfter(':'))
            if (group != null && group.isShared && !group.isArchived && member != null &&
                db.groupDao().getMembersForGroupDirect(group.id).any { it.personId == member.id }
            ) {
                if (!NotificationHelper.showGroupJoin(context, key, member.name, group.name)) continue
                delivered++
            }
            sync.putMeta(SyncMetaEntity(key, SEEN))
        }
        return delivered
    }
}
