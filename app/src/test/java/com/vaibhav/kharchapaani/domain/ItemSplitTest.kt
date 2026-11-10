package com.vaibhav.kharchapaani.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split-by-items arithmetic. The cases live in item-split-vectors.json because the page friends
 * open (site/split.html) has to reach the same paise: scripts/check_split_page.mjs runs that page's
 * own code against the same file.
 */
class ItemSplitTest {

    private val vectors: JsonObject = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("item-split-vectors.json")) { "item-split-vectors.json is missing" }
            .bufferedReader().use { it.readText() },
    ).jsonObject

    private fun splitOf(json: JsonObject) = ItemSplit(
        totalPaise = json.getValue("total_paise").jsonPrimitive.long,
        items = json.getValue("items").jsonArray.map {
            val item = it.jsonObject
            SplitItem(id = item.getValue("id").jsonPrimitive.content, name = "", amountPaise = item.getValue("amount_paise").jsonPrimitive.long)
        },
        people = json.getValue("people").jsonArray.map { SplitPerson(id = it.jsonObject.getValue("id").jsonPrimitive.content, name = "") },
        claims = json.getValue("claims").jsonArray.map {
            val claim = it.jsonObject
            SplitClaim(
                itemId = claim.getValue("item_id").jsonPrimitive.content,
                personId = claim.getValue("person_id").jsonPrimitive.content,
                weight = claim.getValue("weight").jsonPrimitive.int,
            )
        },
    )

    @Test
    fun `every shared case comes out as the page computes it`() {
        val cases = vectors.getValue("cases").jsonArray
        assertTrue("the shared cases are there", cases.size >= 10)
        for (case in cases) {
            val name = case.jsonObject.getValue("name").jsonPrimitive.content
            val expect = case.jsonObject.getValue("expect").jsonObject
            val shares = ItemSplitMath.shares(splitOf(case.jsonObject.getValue("split").jsonObject))

            val people = expect.getValue("people").jsonObject
            assertEquals("$name: who is at the table", people.keys, shares.people.keys)
            for ((id, share) in people) {
                val got = shares.people.getValue(id)
                assertEquals("$name: $id items", share.jsonObject.getValue("items").jsonPrimitive.long, got.itemsPaise)
                assertEquals("$name: $id extras", share.jsonObject.getValue("extras").jsonPrimitive.long, got.extrasPaise)
                assertEquals("$name: $id total", share.jsonObject.getValue("total").jsonPrimitive.long, got.totalPaise)
            }
            assertEquals("$name: unpicked items", expect.getValue("unclaimed_count").jsonPrimitive.int, shares.unclaimedCount)
            assertEquals("$name: unpicked paise", expect.getValue("unclaimed_paise").jsonPrimitive.long, shares.unclaimedPaise)
            assertEquals("$name: items", expect.getValue("items_paise").jsonPrimitive.long, shares.itemsPaise)
            assertEquals("$name: extras", expect.getValue("extras_paise").jsonPrimitive.long, shares.extrasPaise)
        }
    }

    @Test
    fun `once nothing is left over, every case adds up to its total to the paisa`() {
        for (case in vectors.getValue("cases").jsonArray) {
            val split = splitOf(case.jsonObject.getValue("split").jsonObject)
            val amounts = ItemSplitMath.finalAmounts(split)
            assertEquals(case.jsonObject.getValue("name").jsonPrimitive.content, split.totalPaise, amounts.values.sum())
            assertTrue("nobody ends up owed money by the bill", amounts.values.all { it >= 0 })
        }
    }

    @Test
    fun `what nobody picked is shared by everyone, and only that`() {
        val split = ItemSplit(
            totalPaise = 33_000,
            items = listOf(SplitItem("biryani", "Biryani", amountPaise = 20_000), SplitItem("water", "Water", amountPaise = 10_000)),
            people = listOf(SplitPerson("me", "Me", isHost = true), SplitPerson("rohan", "Rohan"), SplitPerson("priya", "Priya")),
            claims = listOf(SplitClaim("biryani", "rohan")),
        )

        val settled = ItemSplitMath.withLeftoversShared(split)
        assertEquals("the water goes three ways", 3, settled.claims.count { it.itemId == "water" })
        assertEquals("the biryani stays Rohan's alone", 1, settled.claims.count { it.itemId == "biryani" })

        // Water: 3334 / 3333 / 3333. Extras of 3000 follow the items: 333.4, 2333.3, 333.3.
        assertEquals(mapOf("me" to 3_334L + 334L, "rohan" to 23_333L + 2_333L, "priya" to 3_333L + 333L), ItemSplitMath.finalAmounts(split))
    }

    @Test
    fun `a bill with no items is shared equally`() {
        val split = ItemSplit(
            totalPaise = 10_000,
            items = emptyList(),
            people = listOf(SplitPerson("a", "A"), SplitPerson("b", "B"), SplitPerson("c", "C")),
            claims = emptyList(),
        )
        assertEquals(mapOf("a" to 3_334L, "b" to 3_333L, "c" to 3_333L), ItemSplitMath.finalAmounts(split))
    }

    @Test
    fun `each person's part of a shared item is reported`() {
        val split = ItemSplit(
            totalPaise = 18_000,
            items = listOf(SplitItem("naan", "Butter naan", quantity = 3, amountPaise = 18_000)),
            people = listOf(SplitPerson("a", "A"), SplitPerson("b", "B")),
            claims = listOf(SplitClaim("naan", "a", weight = 2), SplitClaim("naan", "b", weight = 1)),
        )
        assertEquals(mapOf("a" to 12_000L, "b" to 6_000L), ItemSplitMath.shares(split).parts.getValue("naan"))
    }

    @Test
    fun `rupees and paise convert without drifting`() {
        assertEquals(97_440L, ItemSplitMath.paise(974.40))
        assertEquals(974.40, ItemSplitMath.rupees(97_440L), 0.0)
        assertEquals(1_999L, ItemSplitMath.paise(19.99))
    }
}
