package com.vaibhav.kharchapaani.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class GroupInvite(
    val code: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class InvitePreview(
    @SerialName("group_id") val groupId: String,
    @SerialName("group_name") val groupName: String,
    @SerialName("group_emoji") val groupEmoji: String? = null,
    @SerialName("inviter_name") val inviterName: String? = null,
    @SerialName("member_count") val memberCount: Int,
)

/**
 * Membership changes are interactive, so unlike sync they run online and report failures to the
 * person who asked; nothing here is queued.
 */
interface GroupMembershipRemote {
    suspend fun createInvite(groupRemoteId: String): GroupInvite

    /** Unexpired, unrevoked invites for a group, newest first. */
    suspend fun activeInvites(groupRemoteId: String): List<GroupInvite>

    suspend fun revokeInvite(code: String)

    /** Null for an unknown, expired, revoked or archived code; the server doesn't say which. */
    suspend fun previewInvite(code: String): InvitePreview?

    /** Returns the joined group's remote id. Joining a group twice is a no-op. */
    suspend fun joinGroup(code: String): String

    suspend fun leaveGroup(groupRemoteId: String, selfProfileId: String)
}

class SupabaseGroupMembershipRemote(private val supabase: SupabaseClient) : GroupMembershipRemote {

    override suspend fun createInvite(groupRemoteId: String): GroupInvite =
        supabase.postgrest.rpc("create_group_invite", buildJsonObject { put("p_group_id", groupRemoteId) })
            .decodeList<GroupInvite>()
            .single()

    @OptIn(ExperimentalTime::class)
    override suspend fun activeInvites(groupRemoteId: String): List<GroupInvite> =
        supabase.from("group_invites").select {
            filter {
                eq("group_id", groupRemoteId)
                exact("revoked_at", null)
                gt("expires_at", Clock.System.now().toString())
            }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    override suspend fun revokeInvite(code: String) {
        supabase.postgrest.rpc("revoke_group_invite", buildJsonObject { put("p_code", code) })
    }

    override suspend fun previewInvite(code: String): InvitePreview? =
        supabase.postgrest.rpc("preview_invite", buildJsonObject { put("p_code", code) })
            .decodeList<InvitePreview>()
            .firstOrNull()

    override suspend fun joinGroup(code: String): String =
        supabase.postgrest.rpc("join_group", buildJsonObject { put("p_code", code) }).decodeAs<String>()

    override suspend fun leaveGroup(groupRemoteId: String, selfProfileId: String) {
        supabase.from("group_members").delete {
            filter {
                eq("group_id", groupRemoteId)
                eq("profile_id", selfProfileId)
            }
        }
    }
}
