package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.SimplifiedDebt
import kotlin.math.abs
import kotlin.math.min

object DebtSimplifier {

    data class PersonNet(
        val personId: Long,
        val name: String,
        var netAmount: Double // >0 creditor (owed money), <0 debtor (owes money)
    )

    /**
     * The fewest payments that clear every friend's balance with you, where a friend may be asked to
     * pay another friend directly only if the two are in at least one group together.
     *
     * Every balance here is between you and one friend, so the only way to save a payment is to route
     * one friend's debt straight to someone you owe. Doing that across people who have never met used
     * to produce suggestions like "Hist pays Shubham" for a friend of yours and a groupmate from a
     * different trip (owner decision, 2026-09-27: only between people who share a group). Payments
     * with you are always fine: you know everyone.
     *
     * @param netBalances personId -> balance with you (positive = they owe you, negative = you owe them)
     * @param groups each group's members by person id, local and shared groups alike; "you" (-1) may
     *   be included or left out
     */
    fun simplify(
        people: List<PersonEntity>,
        netBalances: Map<Long, Double>,
        groups: List<Set<Long>>,
        myName: String = "You",
        friendName: String = "Friend",
    ): List<SimplifiedDebt> {
        val names = people.associate { it.id to it.name }
        fun nameOf(id: Long) = if (id == ME) myName else names[id] ?: friendName
        fun shareAGroup(a: Long, b: Long) = groups.any { a in it && b in it }

        // Both lists hold what's still unpaid, as positive amounts.
        val oweMe = netBalances.filterValues { it >= HALF_PAISA }.mapValues { roundPaisa(it.value) }.toMutableMap()
        val iOwe = netBalances.filterValues { it <= -HALF_PAISA }.mapValues { roundPaisa(-it.value) }.toMutableMap()

        val payments = mutableListOf<SimplifiedDebt>()
        fun pay(from: Long, to: Long, amount: Double) {
            val rounded = roundPaisa(amount)
            if (rounded >= ONE_PAISA) payments += SimplifiedDebt(from, nameOf(from), to, nameOf(to), rounded)
        }

        // Friend to friend, biggest first, only inside a shared group. Each one saves a payment.
        while (true) {
            val (debtor, creditor) = oweMe.entries.flatMap { d -> iOwe.entries.map { c -> d to c } }
                .filter { (d, c) -> d.value >= ONE_PAISA && c.value >= ONE_PAISA && shareAGroup(d.key, c.key) }
                .maxWithOrNull(compareBy<Pair<Map.Entry<Long, Double>, Map.Entry<Long, Double>>> { (d, c) -> min(d.value, c.value) }
                    .thenBy { (d, c) -> -d.key }.thenBy { (d, c) -> -c.key })
                ?: break
            val amount = min(debtor.value, creditor.value)
            pay(debtor.key, creditor.key, amount)
            oweMe[debtor.key] = roundPaisa(debtor.value - amount)
            iOwe[creditor.key] = roundPaisa(creditor.value - amount)
        }

        // Whatever is left is settled with you directly.
        oweMe.entries.sortedByDescending { it.value }.forEach { pay(it.key, ME, it.value) }
        iOwe.entries.sortedByDescending { it.value }.forEach { pay(ME, it.key, it.value) }
        return payments
    }

    private const val ME = -1L

    /**
     * Simplifies positions that already cover everyone, such as [GroupBalances.netPositions] for a
     * shared group, where debts between two groupmates don't pass through "me".
     *
     * @param netPositions personId -> net position (positive = owed money, negative = owes money)
     */
    fun simplifyPositions(netPositions: Map<Long, Double>, myName: String = "You", nameOf: (Long) -> String): List<SimplifiedDebt> {
        val meId = -1L
        val meName = myName

        val creditors = mutableListOf<PersonNet>()
        val debtors = mutableListOf<PersonNet>()

        for ((id, net) in netPositions) {
            val rounded = roundPaisa(net)
            val name = if (id == meId) meName else nameOf(id)
            if (rounded >= ONE_PAISA) {
                creditors.add(PersonNet(id, name, rounded))
            } else if (rounded <= -ONE_PAISA) {
                debtors.add(PersonNet(id, name, abs(rounded))) // store as positive debt
            }
        }

        val simplifiedPayments = mutableListOf<SimplifiedDebt>()

        // Greedy matching: match max debtor with max creditor
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            creditors.sortByDescending { it.netAmount }
            debtors.sortByDescending { it.netAmount }

            val creditor = creditors.first()
            val debtor = debtors.first()

            val settleAmount = min(creditor.netAmount, debtor.netAmount)
            val roundedAmount = roundPaisa(settleAmount)

            if (roundedAmount >= ONE_PAISA) {
                simplifiedPayments.add(
                    SimplifiedDebt(
                        fromPersonId = debtor.personId,
                        fromPersonName = debtor.name,
                        toPersonId = creditor.personId,
                        toPersonName = creditor.name,
                        amount = roundedAmount
                    )
                )
            }

            creditor.netAmount -= settleAmount
            debtor.netAmount -= settleAmount

            if (creditor.netAmount < HALF_PAISA) {
                creditors.removeAt(0)
            }
            if (debtor.netAmount < HALF_PAISA) {
                debtors.removeAt(0)
            }
        }

        return simplifiedPayments
    }

    // A debt of exactly one paisa is still a debt; comparisons used to require more than 0.01.
    private const val ONE_PAISA = 0.01
    private const val HALF_PAISA = 0.005
}
