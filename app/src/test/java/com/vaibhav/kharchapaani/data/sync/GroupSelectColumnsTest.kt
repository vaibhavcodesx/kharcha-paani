package com.vaibhav.kharchapaani.data.sync

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug this exists to stop happening again.
 *
 * `visibleGroups()` asked PostgREST for `profiles(id, display_name, avatar_url, avatar_emoji)`.
 * `RemoteProfile` had grown a fifth field, `upi_id`, and nothing connected the two: PostgREST
 * returns exactly the columns it is asked for, a column it wasn't asked for is simply absent from
 * the JSON, and an absent field with a default deserialises silently. So every groupmate's UPI ID
 * arrived as null on every device, for ever, with no error anywhere — while the server was serving
 * it correctly and the RLS policy allowing it was verified separately and passed.
 *
 * A query that drops data without failing is only catchable by checking the query against the type
 * it fills, which is what this does.
 */
@OptIn(ExperimentalSerializationApi::class)
class GroupSelectColumnsTest {

    private val columns = SupabaseGroupRemote.VISIBLE_GROUPS_COLUMNS

    @Test
    fun `every field of a remote profile is actually asked for`() {
        assertEveryFieldRequested(serializer<RemoteProfile>().descriptor, "RemoteProfile")
    }

    @Test
    fun `every field of a group member is actually asked for`() {
        // `profiles` is the embed itself, named in the string as a relation rather than a column.
        assertEveryFieldRequested(serializer<RemoteGroupMember>().descriptor, "RemoteGroupMember")
    }

    @Test
    fun `the group row itself is a star, so its fields need no listing`() {
        // RemoteGroup has ten fields and counting; naming them all would be the same trap again.
        assertTrue("the groups row must stay a wildcard", columns.startsWith("*,"))
    }

    @Test
    fun `the UPI ID specifically is in there`() {
        // Named on its own, because this is the one that broke and the one that matters: without it
        // the person settling up sees "hasn't added a UPI ID yet" for someone who has.
        assertTrue(columns.contains("upi_id"))
    }

    private fun assertEveryFieldRequested(descriptor: SerialDescriptor, typeName: String) {
        for (i in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(i)
            assertTrue(
                "$typeName.$name is not in the select list, so it will silently arrive as its " +
                    "default: $columns",
                columns.contains(name),
            )
        }
    }
}
