package com.vaibhav.kharchapaani.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
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
    @SerialName("used_count") val usedCount: Int? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
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

    /** Replaces all live codes for the group. Null when the server has no reset RPC. */
    suspend fun resetInvite(groupRemoteId: String): GroupInvite?

    /** A member's newest unexpired, unrevoked invite, or the specified code when refreshing it. */
    suspend fun activeInvite(groupRemoteId: String, creatorProfileId: String, code: String? = null): GroupInvite?

    suspend fun revokeInvite(code: String)

    /** Null for an unknown, expired, revoked or archived code; the server doesn't say which. */
    suspend fun previewInvite(code: String): InvitePreview?

    /**
     * Returns the joined group's remote id. Joining a group twice is a no-op.
     * @throws InvalidInviteException when the server has no live invite for the code.
     */
    suspend fun joinGroup(code: String): String

    suspend fun supportsInviteLimits(groupRemoteId: String): Boolean

    suspend fun removeMember(groupRemoteId: String, memberProfileId: String)

    suspend fun leaveGroup(groupRemoteId: String, selfProfileId: String)
}

/**
 * An unknown, expired, revoked, used-up or archived code. Newer servers answer `join_group` with a null
 * result for these (an error would roll back the failed-try count that limits guessing); older ones
 * raised "invite code is invalid or expired". Both reach the person as the same message.
 */
class InvalidInviteException : Exception("invite code is invalid or expired")

class SupabaseGroupMembershipRemote(private val supabase: SupabaseClient) : GroupMembershipRemote {

    override suspend fun createInvite(groupRemoteId: String): GroupInvite =
        supabase.postgrest.rpc("create_group_invite", buildJsonObject { put("p_group_id", groupRemoteId) })
            .decodeList<GroupInvite>()
            .single()

    override suspend fun resetInvite(groupRemoteId: String): GroupInvite? = try {
        supabase.postgrest.rpc("reset_group_invites", buildJsonObject { put("p_group_id", groupRemoteId) })
            .decodeList<GroupInvite>()
            .single()
    } catch (e: RestException) {
        if (e.statusCode == 404 || e.error.contains("PGRST202", ignoreCase = true)) null else throw e
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun activeInvite(groupRemoteId: String, creatorProfileId: String, code: String?): GroupInvite? =
        supabase.from("group_invites").select(Columns.list("code", "expires_at", "used_count", "max_uses")) {
            filter {
                eq("group_id", groupRemoteId)
                eq("created_by", creatorProfileId)
                if (code != null) eq("code", code)
                exact("revoked_at", null)
                gt("expires_at", Clock.System.now().toString())
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<GroupInvite>().firstOrNull()

    override suspend fun revokeInvite(code: String) {
        supabase.postgrest.rpc("revoke_group_invite", buildJsonObject { put("p_code", code) })
    }

    override suspend fun previewInvite(code: String): InvitePreview? =
        supabase.postgrest.rpc("preview_invite", buildJsonObject { put("p_code", code) })
            .decodeList<InvitePreview>()
            .firstOrNull()

    override suspend fun joinGroup(code: String): String =
        supabase.postgrest.rpc("join_group", buildJsonObject { put("p_code", code) }).decodeAs<String?>()
            ?: throw InvalidInviteException()

    override suspend fun supportsInviteLimits(groupRemoteId: String): Boolean {
        // These columns and the reset and removal RPCs ship in the same migration. Probe without a write.
        supabase.from("group_invites").select(Columns.list("max_uses")) {
            filter { eq("group_id", groupRemoteId) }
            limit(1)
        }
        return true
    }

    override suspend fun removeMember(groupRemoteId: String, memberProfileId: String) {
        supabase.postgrest.rpc("remove_group_member", buildJsonObject {
            put("p_group_id", groupRemoteId)
            put("p_profile_id", memberProfileId)
        })
    }

    override suspend fun leaveGroup(groupRemoteId: String, selfProfileId: String) {
        supabase.from("group_members").delete {
            filter {
                eq("group_id", groupRemoteId)
                eq("profile_id", selfProfileId)
            }
        }
    }
}
