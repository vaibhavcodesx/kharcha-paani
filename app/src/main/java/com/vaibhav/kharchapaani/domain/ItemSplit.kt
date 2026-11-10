package com.vaibhav.kharchapaani.domain

import kotlinx.serialization.Serializable
import java.math.BigInteger

/**
 * A bill split by who had what (docs/designs/split-by-items.md). Amounts are paise, so nothing
 * here is ever a float. The same shape travels to the server and to the page friends open, and
 * site/split.html does the same arithmetic as [ItemSplitMath]: both are checked against
 * app/src/test/resources/item-split-vectors.json.
 */
data class ItemSplit(
    /** What was actually paid: items plus tax, service charge and tip, less any discount. */
    val totalPaise: Long,
    val items: List<SplitItem>,
    /** In the order they came to the table, which is also the order leftover paise go out in. */
    val people: List<SplitPerson>,
    val claims: List<SplitClaim>,
)

/** One line of the bill. [amountPaise] is the line's total, not a unit price. */
@Serializable
data class SplitItem(val id: String, val name: String, val quantity: Int = 1, val amountPaise: Long)

/**
 * Someone at the table. [ledgerId] is who they are in this phone's ledger (-1 for me), when known:
 * a name typed on the web page has none until the split is used.
 */
@Serializable
data class SplitPerson(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    val done: Boolean = false,
    val ledgerId: Long? = null,
)

/** [personId] had [weight] parts of [itemId]: two of the three naan is weight 2. */
@Serializable
data class SplitClaim(val itemId: String, val personId: String, val weight: Int = 1)

data class PersonShare(val itemsPaise: Long, val extrasPaise: Long) {
    val totalPaise: Long get() = itemsPaise + extrasPaise
}

data class ItemSplitShares(
    /** Every person at the table, including those who picked nothing. */
    val people: Map<String, PersonShare>,
    /** Per item, what each person who picked it carries of it. */
    val parts: Map<String, Map<String, Long>>,
    val unclaimedCount: Int,
    val unclaimedPaise: Long,
    val itemsPaise: Long,
    /** Total minus items: tax and charges when positive, a discount when negative. */
    val extrasPaise: Long,
)

object ItemSplitMath {

    /**
     * Who carries what so far. An item is divided between the people who picked it, by weight.
     * Whatever the total has beyond the items (tax, service charge, tip, round-off; less for a
     * discount) follows the items: each person carries it in proportion to what they picked.
     * Leftover paise go to whoever was closest to earning one, earlier people first on a tie, so
     * once every item is picked the shares add up to the total exactly.
     */
    fun shares(split: ItemSplit): ItemSplitShares {
        val order = split.people.map { it.id }
        val rank = order.withIndex().associate { (index, id) -> id to index }
        val base = LongArray(order.size)
        val weights = HashMap<String, MutableList<SplitClaim>>()
        for (claim in split.claims) {
            if (claim.personId !in rank || claim.weight <= 0) continue
            weights.getOrPut(claim.itemId) { mutableListOf() }.add(claim)
        }

        val parts = LinkedHashMap<String, Map<String, Long>>()
        var itemsPaise = 0L
        var unclaimedPaise = 0L
        var unclaimedCount = 0
        for (item in split.items) {
            itemsPaise += item.amountPaise
            val claimers = weights[item.id].orEmpty().sortedBy { rank.getValue(it.personId) }
            if (claimers.isEmpty()) {
                unclaimedPaise += item.amountPaise
                unclaimedCount++
                continue
            }
            val amount = BigInteger.valueOf(item.amountPaise)
            val totalWeight = BigInteger.valueOf(claimers.sumOf { it.weight.toLong() })
            val handed = handOut(item.amountPaise, claimers.map { amount * BigInteger.valueOf(it.weight.toLong()) }, totalWeight)
            val itemParts = LinkedHashMap<String, Long>()
            claimers.forEachIndexed { index, claim ->
                base[rank.getValue(claim.personId)] += handed[index]
                itemParts[claim.personId] = handed[index]
            }
            parts[item.id] = itemParts
        }

        val extrasPaise = split.totalPaise - itemsPaise
        var extras = LongArray(order.size)
        if (itemsPaise > 0 && extrasPaise != 0L) {
            val size = BigInteger.valueOf(kotlin.math.abs(extrasPaise))
            val all = BigInteger.valueOf(itemsPaise)
            val claimed = BigInteger.valueOf(itemsPaise - unclaimedPaise)
            // The part of the extras that belongs to picked items, to the nearest paisa.
            val target = ((TWO * size * claimed + all) / (TWO * all)).toLong()
            val handed = handOut(target, base.map { size * BigInteger.valueOf(it) }, all)
            extras = if (extrasPaise < 0) LongArray(handed.size) { -handed[it] } else handed
        }

        return ItemSplitShares(
            people = order.withIndex().associate { (index, id) -> id to PersonShare(base[index], extras[index]) },
            parts = parts,
            unclaimedCount = unclaimedCount,
            unclaimedPaise = unclaimedPaise,
            itemsPaise = itemsPaise,
            extrasPaise = extrasPaise,
        )
    }

    /**
     * The split with everything nobody picked shared between everyone at the table: the starter
     * that went round, the water nobody thought to tick.
     */
    fun withLeftoversShared(split: ItemSplit): ItemSplit {
        val picked = split.claims.filter { it.weight > 0 && split.people.any { p -> p.id == it.personId } }.map { it.itemId }.toSet()
        val added = split.items.filter { it.id !in picked }.flatMap { item -> split.people.map { SplitClaim(item.id, it.id, 1) } }
        return if (added.isEmpty()) split else split.copy(claims = split.claims + added)
    }

    /**
     * What each person owes once nothing is left over, adding up to the total to the paisa. With no
     * items at all (or items worth nothing) the total is simply shared equally.
     */
    fun finalAmounts(split: ItemSplit): Map<String, Long> {
        if (split.people.isEmpty()) return emptyMap()
        val settled = withLeftoversShared(split)
        val result = shares(settled)
        if (result.itemsPaise <= 0) {
            val equal = handOut(split.totalPaise, split.people.map { BigInteger.valueOf(split.totalPaise) }, BigInteger.valueOf(split.people.size.toLong()))
            return split.people.withIndex().associate { (index, person) -> person.id to equal[index] }
        }
        return result.people.mapValues { it.value.totalPaise }
    }

    /**
     * Shares of [target] paise in the ratio numerators[i] / [denominator], rounded down, then one
     * paisa each to the largest remainders (earlier first on a tie) until [target] is reached.
     */
    private fun handOut(target: Long, numerators: List<BigInteger>, denominator: BigInteger): LongArray {
        val parts = LongArray(numerators.size) { (numerators[it] / denominator).toLong() }
        var left = target - parts.sum()
        val byRemainder = numerators.indices.sortedWith(
            compareByDescending<Int> { numerators[it].mod(denominator) }.thenBy { it },
        )
        for (index in byRemainder) {
            if (left <= 0) break
            parts[index] += 1
            left--
        }
        return parts
    }

    private val TWO = BigInteger.valueOf(2)

    /** Rupees as the ledger holds them, from paise. */
    fun rupees(paise: Long): Double = paise / 100.0

    /** Paise from a rupee amount, rounded to the nearest paisa. */
    fun paise(rupees: Double): Long = Math.round(rupees * 100.0)
}
