package com.vaibhav.kharchapaani.data.sync

import com.vaibhav.kharchapaani.domain.ItemSplit
import com.vaibhav.kharchapaani.domain.SplitItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A bill being split by items while friends pick from the link (docs/designs/split-by-items.md;
 * migration 20261110000000_bill_item_splits). The host's phone starts it, corrects it and finishes
 * it; picks and names go through the same link-keyed functions the web page uses.
 */
interface BillSplitRemote {
    /** Starts sharing [split] under [id] (chosen here, so a retry returns the same link). */
    suspend fun create(id: String, title: String, hostName: String, split: ItemSplit): BillSplitLink

    /** The split as the table sees it now, or null once it's finished or its day is up. */
    suspend fun view(token: String): RemoteBillSplit?

    suspend fun updateBill(id: String, title: String, totalPaise: Long, items: List<SplitItem>): RemoteBillSplit

    /** Adds [name] under [personId]; the reply's id differs when that name was already at the table. */
    suspend fun addPerson(token: String, personId: String, name: String): Pair<String, RemoteBillSplit>

    suspend fun removePerson(id: String, personId: String): RemoteBillSplit

    /** [weight] 0 takes the pick back. */
    suspend fun claim(token: String, personId: String, itemId: String, weight: Int): RemoteBillSplit

    /** Finishes the split: the link stops working and what was typed into it is deleted. */
    suspend fun close(id: String)
}

/** The split was finished or has expired: there is nothing at that link any more. */
class BillSplitEndedException : Exception("the split is not open")

/** The server refused it for good (a full table, too many open splits): retrying won't help. */
class BillSplitRefusedException(val reason: String) : Exception(reason)

@Serializable
data class BillSplitLink(val id: String, val token: String, @SerialName("expires_at") val expiresAt: String = "")

@Serializable
data class RemoteBillSplit(
    val id: String,
    val title: String = "",
    @SerialName("host_name") val hostName: String = "",
    @SerialName("total_paise") val totalPaise: Long = 0,
    val version: Long = 0,
    @SerialName("expires_at") val expiresAt: String = "",
    val items: List<RemoteBillItem> = emptyList(),
    val people: List<RemoteBillPerson> = emptyList(),
    val claims: List<RemoteBillClaim> = emptyList(),
)

@Serializable
data class RemoteBillItem(val id: String, val name: String, val quantity: Int = 1, @SerialName("amount_paise") val amountPaise: Long)

@Serializable
data class RemoteBillPerson(val id: String, val name: String, @SerialName("is_host") val isHost: Boolean = false, val done: Boolean = false)

@Serializable
data class RemoteBillClaim(@SerialName("item_id") val itemId: String, @SerialName("person_id") val personId: String, val weight: Int = 1)

@Serializable
private data class JoinReply(@SerialName("person_id") val personId: String, val split: RemoteBillSplit)

class SupabaseBillSplitRemote(private val supabase: SupabaseClient) : BillSplitRemote {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(id: String, title: String, hostName: String, split: ItemSplit): BillSplitLink = refusing {
        val payload = buildJsonObject {
            put("id", id)
            put("title", title)
            put("host_name", hostName)
            put("total_paise", split.totalPaise)
            putItems(split.items)
            put("people", buildJsonArray {
                split.people.forEach { person ->
                    add(buildJsonObject {
                        put("id", person.id)
                        put("name", person.name)
                        put("is_host", person.isHost)
                    })
                }
            })
            put("claims", buildJsonArray {
                split.claims.filter { it.weight > 0 }.forEach { claim ->
                    add(buildJsonObject {
                        put("item_id", claim.itemId)
                        put("person_id", claim.personId)
                        put("weight", claim.weight)
                    })
                }
            })
        }
        json.decodeFromString<BillSplitLink>(rpc("create_bill_split", buildJsonObject { put("p_payload", payload) }))
    }

    override suspend fun view(token: String): RemoteBillSplit? = refusing {
        json.decodeFromString<RemoteBillSplit?>(rpc("bill_split_view", buildJsonObject { put("p_token", token) }))
    }

    override suspend fun updateBill(id: String, title: String, totalPaise: Long, items: List<SplitItem>): RemoteBillSplit = refusing {
        val payload = buildJsonObject {
            put("title", title)
            put("total_paise", totalPaise)
            putItems(items)
        }
        json.decodeFromString<RemoteBillSplit>(rpc("update_bill_split", buildJsonObject {
            put("p_id", id)
            put("p_payload", payload)
        }))
    }

    override suspend fun addPerson(token: String, personId: String, name: String): Pair<String, RemoteBillSplit> = refusing {
        val reply = json.decodeFromString<JoinReply>(rpc("bill_split_join", buildJsonObject {
            put("p_token", token)
            put("p_name", name)
            put("p_person_id", personId)
        }))
        reply.personId to reply.split
    }

    override suspend fun removePerson(id: String, personId: String): RemoteBillSplit = refusing {
        json.decodeFromString<RemoteBillSplit>(rpc("remove_bill_split_person", buildJsonObject {
            put("p_id", id)
            put("p_person_id", personId)
        }))
    }

    override suspend fun claim(token: String, personId: String, itemId: String, weight: Int): RemoteBillSplit = refusing {
        json.decodeFromString<RemoteBillSplit>(rpc("bill_split_claim", buildJsonObject {
            put("p_token", token)
            put("p_person_id", personId)
            put("p_item_id", itemId)
            put("p_weight", weight)
        }))
    }

    override suspend fun close(id: String) = refusing {
        rpc("close_bill_split", buildJsonObject { put("p_id", id) })
        Unit
    }

    private suspend fun rpc(function: String, parameters: JsonObject): String =
        supabase.postgrest.rpc(function, parameters).data

    private fun JsonObjectBuilder.putItems(items: List<SplitItem>) {
        put("items", buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    put("id", item.id)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("amount_paise", item.amountPaise)
                })
            }
        })
    }

    /**
     * The server's own refusals by name. A dropped connection or an expired session stays what it
     * was (an IOException, a 401) for the caller to treat as "try again".
     */
    private suspend fun <T> refusing(block: suspend () -> T): T = try {
        block()
    } catch (e: RestException) {
        val reason = REFUSALS.firstOrNull { it in e.error || it in e.message.orEmpty() }
        when {
            reason == "not_open" -> throw BillSplitEndedException()
            reason != null -> throw BillSplitRefusedException(reason)
            else -> throw e
        }
    }

    private companion object {
        val REFUSALS = listOf("not_open", "too_many_people", "too_many_splits", "name_taken", "bad_name", "bad_request", "not_allowed")
    }
}
