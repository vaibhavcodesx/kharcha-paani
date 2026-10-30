package com.vaibhav.kharchapaani.ui.groups

import com.vaibhav.kharchapaani.R

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.sync.GroupMembershipRemote
import com.vaibhav.kharchapaani.data.sync.InvitePreview
import com.vaibhav.kharchapaani.domain.InviteLink
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class JoinGroupState(
    val input: String = "",
    val preview: InvitePreview? = null,
    val isLoadingPreview: Boolean = false,
    val isJoining: Boolean = false,
    val error: String? = null,
) {
    val code: String? get() = InviteLink.parse(input)
    val canJoin: Boolean get() = preview != null && !isJoining
}

data class GroupInviteState(
    val groupId: Long,
    val code: String? = null,
    val expiresAtMillis: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val link: String? get() = code?.let(InviteLink::linkFor)
}

data class SharedGroupsUiState(
    /** The join sheet is open while this is non-null. */
    val join: JoinGroupState? = null,
    val invite: GroupInviteState? = null,
    val leavingGroupId: Long? = null,
    val message: String? = null,
    /** Set by a join that went through, until the shell has offered notifications for it. */
    val justJoined: Boolean = false,
)

/**
 * Invites, joining, leaving and archiving for shared groups. These need the server now, so they
 * report failures to the person instead of queueing; archiving is a group edit and does queue.
 * Kept free of Android types so the flow can be tested with fakes.
 */
class SharedGroupsController(
    private val scope: CoroutineScope,
    private val repository: LedgerRepository,
    private val remote: GroupMembershipRemote,
    private val selfProfileId: String,
    private val requestSync: () -> Unit,
    private val resolveText: (Int, Array<out Any>) -> String,
) {
    private fun copyText(@androidx.annotation.StringRes id: Int, vararg args: Any): String = resolveText(id, args)

    private val _state = MutableStateFlow(SharedGroupsUiState())
    val state: StateFlow<SharedGroupsUiState> = _state.asStateFlow()
    private var previewJob: Job? = null

    fun openJoin(prefill: String = "") {
        _state.update { it.copy(join = JoinGroupState(input = prefill)) }
        if (InviteLink.parse(prefill) != null) loadPreview()
    }

    fun dismissJoin() {
        previewJob?.cancel()
        _state.update { it.copy(join = null) }
    }

    fun onJoinInputChange(input: String) {
        previewJob?.cancel()
        _state.update { it.copy(join = it.join?.copy(input = input, preview = null, isLoadingPreview = false, error = null)) }
        if (InviteLink.parse(input) != null) loadPreview()
    }

    private fun loadPreview() {
        val code = _state.value.join?.code ?: return
        previewJob = scope.launch {
            _state.update { it.copy(join = it.join?.copy(isLoadingPreview = true, error = null)) }
            val result = runRemote { remote.previewInvite(code) }
            _state.update { state ->
                val join = state.join?.takeIf { it.code == code } ?: return@update state
                state.copy(
                    join = result.fold(
                        onSuccess = { preview ->
                            join.copy(
                                preview = preview,
                                isLoadingPreview = false,
                                error = if (preview == null) copyText(INVALID_CODE) else null,
                            )
                        },
                        onFailure = { join.copy(isLoadingPreview = false, error = it.message) },
                    )
                )
            }
        }
    }

    fun confirmJoin() {
        val join = _state.value.join ?: return
        val code = join.code ?: return
        if (!join.canJoin) return
        scope.launch {
            _state.update { it.copy(join = it.join?.copy(isJoining = true, error = null)) }
            runRemote { remote.joinGroup(code) }.fold(
                onSuccess = {
                    requestSync()
                    val name = join.preview?.groupName ?: copyText(R.string.shared_group_notice_default_name)
                    _state.update { it.copy(join = null, message = copyText(R.string.shared_group_notice_joined, name), justJoined = true) }
                },
                onFailure = { error ->
                    _state.update { it.copy(join = it.join?.copy(isJoining = false, error = error.message)) }
                },
            )
        }
    }

    /** A group created offline has to reach the server before anyone can be invited to it. */
    @OptIn(ExperimentalTime::class)
    fun createInvite(group: GroupEntity) {
        if (group.syncState != SyncState.SYNCED) {
            requestSync()
            _state.update {
                it.copy(invite = GroupInviteState(group.id, error = copyText(R.string.shared_group_notice_not_synced)))
            }
            return
        }
        scope.launch {
            _state.update { it.copy(invite = GroupInviteState(group.id, isLoading = true)) }
            runRemote { remote.createInvite(group.remoteId) }.fold(
                onSuccess = { invite ->
                    _state.update {
                        it.copy(
                            invite = GroupInviteState(
                                groupId = group.id,
                                code = invite.code,
                                expiresAtMillis = runCatching { Instant.parse(invite.expiresAt).toEpochMilliseconds() }.getOrNull(),
                            )
                        )
                    }
                },
                onFailure = { error -> _state.update { it.copy(invite = GroupInviteState(group.id, error = error.message)) } },
            )
        }
    }

    fun revokeInvite() {
        val invite = _state.value.invite ?: return
        val code = invite.code ?: return
        scope.launch {
            runRemote { remote.revokeInvite(code) }.fold(
                onSuccess = { _state.update { it.copy(invite = null, message = copyText(R.string.shared_group_notice_revoked)) } },
                onFailure = { error -> _state.update { it.copy(invite = invite.copy(error = error.message)) } },
            )
        }
    }

    fun dismissInvite() {
        _state.update { it.copy(invite = null) }
    }

    fun leaveGroup(group: GroupEntity, onLeft: () -> Unit) {
        if (!group.isShared || _state.value.leavingGroupId != null) return
        scope.launch {
            _state.update { it.copy(leavingGroupId = group.id) }
            val result = if (group.syncState == SyncState.SYNCED || group.syncState == SyncState.PENDING) {
                runRemote { remote.leaveGroup(group.remoteId, selfProfileId) }
            } else {
                // Never reached the server (creation was refused): there is no membership to end.
                Result.success(Unit)
            }
            result.fold(
                onSuccess = {
                    repository.removeSharedGroupLocally(group.id)
                    // Callback first: anything observing the "left" state can rely on it having run.
                    onLeft()
                    _state.update { it.copy(leavingGroupId = null, invite = null, message = copyText(R.string.shared_group_notice_left, group.name)) }
                },
                onFailure = { error -> _state.update { it.copy(leavingGroupId = null, message = error.message) } },
            )
        }
    }

    fun setArchived(group: GroupEntity, archived: Boolean) {
        scope.launch {
            repository.setGroupArchived(group.id, archived)
            _state.update { it.copy(message = if (archived) "${group.name} archived." else "${group.name} restored.") }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearJustJoined() {
        _state.update { it.copy(justJoined = false) }
    }

    private suspend fun <T> runRemote(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(Exception(copyText(OFFLINE)))
    } catch (e: RestException) {
        Result.failure(Exception(messageFor(e)))
    }

    private fun messageFor(e: RestException): String = when {
        e.error.contains("invalid or expired", ignoreCase = true) -> copyText(INVALID_CODE)
        e.statusCode == 401 -> copyText(R.string.shared_group_notice_session)
        e.statusCode == 403 || e.error.contains("not a member", ignoreCase = true) -> copyText(R.string.shared_group_notice_membership)
        else -> copyText(R.string.shared_group_notice_problem, e.statusCode)
    }

    companion object {
        @androidx.annotation.StringRes val INVALID_CODE = R.string.shared_group_notice_invalid
        @androidx.annotation.StringRes val OFFLINE = R.string.shared_group_notice_offline
    }
}
