package com.vaibhav.kharchapaani.ui.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.sync.GroupInvite
import com.vaibhav.kharchapaani.data.sync.GroupMembershipRemote
import com.vaibhav.kharchapaani.data.sync.InvitePreview
import com.vaibhav.kharchapaani.data.sync.personalTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

private class FakeMembershipRemote : GroupMembershipRemote {
    val previews = mutableMapOf<String, InvitePreview>()
    val joined = mutableListOf<String>()
    val left = mutableListOf<String>()
    val revoked = mutableListOf<String>()
    var online = true

    private fun check() {
        if (!online) throw IOException("offline")
    }

    override suspend fun createInvite(groupRemoteId: String): GroupInvite {
        check()
        return GroupInvite("AB2CD3EF", "2026-09-21T10:00:00+00:00")
    }

    override suspend fun activeInvites(groupRemoteId: String) = emptyList<GroupInvite>()

    override suspend fun revokeInvite(code: String) {
        check()
        revoked += code
    }

    override suspend fun previewInvite(code: String): InvitePreview? {
        check()
        return previews[code]
    }

    override suspend fun joinGroup(code: String): String {
        check()
        joined += code
        return previews.getValue(code).groupId
    }

    override suspend fun leaveGroup(groupRemoteId: String, selfProfileId: String) {
        check()
        left += groupRemoteId
    }
}

/** Runs on CI (Robolectric + Room for the leave path). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedGroupsControllerTest {

    private val self = "00000000-0000-4000-8000-0000000000a1"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "shared-groups-ui-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val remote = FakeMembershipRemote().apply {
        previews["AB2CD3EF"] = InvitePreview("10000000-0000-4000-8000-000000000001", "Goa", "🏖️", "Bob", 3)
    }
    private var syncRequests = 0
    private val repository = LedgerRepository(db, selfProfileId = self)
    private val controller = SharedGroupsController(scope, repository, remote, self, requestSync = { syncRequests++ }, resolveText = { id, args -> context.getString(id, *args) })

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun awaitState(predicate: (SharedGroupsUiState) -> Boolean): SharedGroupsUiState = runBlocking {
        withTimeout(5_000) { controller.state.first(predicate) }
    }

    @Test
    fun `opening a join link previews the group before joining`() {
        controller.openJoin("kharchapaani://join/AB2CD3EF")

        val state = awaitState { it.join?.preview != null }
        assertEquals("Goa", state.join?.preview?.groupName)
        assertTrue(state.join!!.canJoin)
        assertTrue("nothing is joined by just opening the link", remote.joined.isEmpty())

        controller.confirmJoin()

        val joined = awaitState { it.join == null }
        assertEquals(listOf("AB2CD3EF"), remote.joined)
        assertEquals(1, syncRequests)
        assertTrue(joined.message!!.contains("Goa"))
    }

    @Test
    fun `an unknown code says so and can't be joined`() {
        controller.openJoin()
        controller.onJoinInputChange("ZZ2ZZ3ZZ")

        val state = awaitState { it.join?.error != null }
        assertEquals(context.getString(SharedGroupsController.INVALID_CODE), state.join?.error)
        assertFalse(state.join!!.canJoin)
    }

    @Test
    fun `a partly typed code doesn't reach the server`() {
        controller.openJoin()
        controller.onJoinInputChange("AB2CD")

        val join = controller.state.value.join!!
        assertNull(join.code)
        assertFalse(join.isLoadingPreview)
        assertNull(join.error)
    }

    @Test
    fun `being offline is reported instead of queued`() {
        remote.online = false
        controller.openJoin("AB2CD3EF")

        assertEquals(context.getString(SharedGroupsController.OFFLINE), awaitState { it.join?.error != null }.join?.error)
    }

    @Test
    fun `an invite can only be made for a group the server knows about`() = runBlocking {
        val pending = GroupEntity(id = 7, name = "Flat", syncState = SyncState.PENDING, createdByProfileId = self)
        controller.createInvite(pending)
        assertNotNull(controller.state.value.invite?.error)
        assertEquals(1, syncRequests)

        controller.createInvite(pending.copy(syncState = SyncState.SYNCED))
        val invite = awaitState { it.invite?.code != null }.invite!!
        assertEquals("kharchapaani://join/AB2CD3EF", invite.link)
        assertNotNull(invite.expiresAtMillis)

        controller.revokeInvite()
        awaitState { it.invite == null }
        assertEquals(listOf("AB2CD3EF"), remote.revoked)
    }

    @Test
    fun `leaving removes the group and its history from this device`() = runBlocking {
        val groupId = db.groupDao().insertGroup(GroupEntity(name = "Goa", syncState = SyncState.SYNCED, createdByProfileId = "someone"))
        val txId = db.transactionDao().insertTransaction(personalTransaction("Villa").copy(groupId = groupId))
        val group = db.groupDao().getGroupByIdDirect(groupId)!!
        var left = false

        controller.leaveGroup(group) { left = true }

        awaitState { it.leavingGroupId == null && it.message != null }
        assertTrue(left)
        assertEquals(listOf(group.remoteId), remote.left)
        assertNull(db.groupDao().getGroupByIdDirect(groupId))
        assertNull(db.transactionDao().getTransactionByIdDirect(txId))
    }

    @Test
    fun `a failed leave keeps everything`() = runBlocking {
        val groupId = db.groupDao().insertGroup(GroupEntity(name = "Goa", syncState = SyncState.SYNCED))
        remote.online = false

        controller.leaveGroup(db.groupDao().getGroupByIdDirect(groupId)!!) { error("must not be called") }

        assertEquals(context.getString(SharedGroupsController.OFFLINE), awaitState { it.message != null }.message)
        assertNotNull(db.groupDao().getGroupByIdDirect(groupId))
    }
}
