package com.vaibhav.kharchapaani.data.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Response shapes PostgREST can send for the embedded selects the group remote makes. */
class RemoteGroupsDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun transaction(splits: String) = """
        {"id":"t1","owner_id":"p1","type":"expense","amount":90.0,"category":"Food","merchant":"Shack",
         "payment_method":"UPI","date":"2026-09-14T10:00:00+00:00","note":"","group_id":"g1",
         "receipt_storage_path":null,"created_at":"2026-09-14T10:00:00+00:00",
         "updated_at":"2026-09-14T10:00:01.123456+00:00","deleted_at":null,"splits":$splits}
    """.trimIndent()

    private val split = """
        {"id":"s1","paid_by":"p1","split_type":"equal","total_amount":90.00,"split_shares":[
          {"id":"a","profile_id":"p1","share_amount":45.0,"percentage":null,"deleted_at":null},
          {"id":"b","profile_id":"p2","share_amount":45.0,"percentage":null,"deleted_at":null},
          {"id":"c","profile_id":"p3","share_amount":30.0,"percentage":null,"deleted_at":"2026-09-14T09:00:00+00:00"}]}
    """.trimIndent()

    @Test
    fun `a one-to-one split embed decodes as an object, with removed shares filtered out`() {
        val row = json.decodeFromString(RemoteGroupTransaction.serializer(), transaction(split))

        assertEquals("s1", row.split?.id)
        assertEquals(listOf("p1", "p2"), row.activeShares.map { it.profileId })
    }

    @Test
    fun `a share for someone with no account decodes, named instead of profiled`() {
        val withLocal = split.replace(
            """{"id":"b","profile_id":"p2",""",
            """{"id":"b","profile_id":null,"local_name":"Rohan",""",
        )
        val row = json.decodeFromString(RemoteGroupTransaction.serializer(), transaction(withLocal))

        assertEquals(listOf("p1", null), row.activeShares.map { it.profileId })
        assertEquals("Rohan", row.activeShares[1].localName)
    }

    @Test
    fun `an array embed and a missing split both decode`() {
        assertEquals("s1", json.decodeFromString(RemoteGroupTransaction.serializer(), transaction("[$split]")).split?.id)
        assertNull(json.decodeFromString(RemoteGroupTransaction.serializer(), transaction("null")).split)
        assertNull(json.decodeFromString(RemoteGroupTransaction.serializer(), transaction("[]")).split)
    }

    @Test
    fun `a group decodes with members whose profiles RLS may hide`() {
        val group = json.decodeFromString(
            RemoteGroup.serializer(),
            """
            {"id":"g1","name":"Goa","emoji":null,"created_by":"p1","created_at":"2026-09-14T10:00:00+00:00",
             "updated_at":"2026-09-14T10:00:00+00:00","archived_at":null,"description":"","color_hex":"#FF7A3D",
             "group_members":[{"profile_id":"p1","profiles":{"id":"p1","display_name":"Alice","avatar_url":null}},
                              {"profile_id":"p2","profiles":null}]}
            """.trimIndent(),
        )

        assertEquals(listOf("Alice", null), group.members.map { it.profiles?.displayName })
    }
}
