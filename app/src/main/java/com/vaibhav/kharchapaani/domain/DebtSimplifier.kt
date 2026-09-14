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
     * Calculates simplified debt settlements across all participants using
     * greedy min-cash-flow reduction.
     *
     * @param people List of all friends / teammates in the group
     * @param netBalances Map of personId -> net amount relative to Me (positive = person owes Me, negative = I owe person)
     * @param externalDebts Optional list of direct peer-to-peer debts among teammates (e.g. from multi-person splits)
     */
    fun simplify(
        people: List<PersonEntity>,
        netBalances: Map<Long, Double>
    ): List<SimplifiedDebt> {
        val peopleMap = people.associateBy { it.id }
        val meId = -1L

        // Net position: positive = overall creditor, negative = overall debtor
        val netPositions = mutableMapOf<Long, Double>()

        // For each person, their net balance with 'Me':
        // netBalances[personId] > 0 means Person owes Me (Person is debtor, Me is creditor)
        // netBalances[personId] < 0 means I owe Person (Me is debtor, Person is creditor)
        var myNet = 0.0
        for ((personId, balance) in netBalances) {
            if (abs(balance) >= HALF_PAISA) {
                // balance > 0 => Person owes Me => Person net -= balance, My net += balance
                netPositions[personId] = (netPositions[personId] ?: 0.0) - balance
                myNet += balance
            }
        }
        netPositions[meId] = myNet

        return simplifyPositions(netPositions) { id -> peopleMap[id]?.name ?: "Friend" }
    }

    /**
     * Simplifies positions that already cover everyone, such as [GroupBalances.netPositions] for a
     * shared group, where debts between two groupmates don't pass through "me".
     *
     * @param netPositions personId -> net position (positive = owed money, negative = owes money)
     */
    fun simplifyPositions(netPositions: Map<Long, Double>, nameOf: (Long) -> String): List<SimplifiedDebt> {
        val meId = -1L
        val meName = "You"

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
