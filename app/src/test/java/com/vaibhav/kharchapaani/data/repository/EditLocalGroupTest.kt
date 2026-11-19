package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UX-19: a local group can be renamed, given another icon and about line, and have members added or
 * removed, and none of that touches what was already spent in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditLocalGroupTest {

    private val db: AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = LedgerRepository(db, selfProfileId = "me")

    @After
    fun tearDown() = db.close()

    private suspend fun groupWithDinner(): Triple<Long, Long, Long> {
        val asha = repository.addPerson("Asha", "#70C1FF")
        val bilal = repository.addPerson("Bilal", "#FFB3BA")
        val groupId = repository.createGroup("Goa", "trip", "🏖️", "#FF7A3D", listOf(asha, bilal))
        repository.saveTransactionWithSplits(
            TransactionEntity(type = "expense", amount = 600.0, category = "Food", merchant = "Dinner", paymentMethod = "UPI", date = 1_000L, groupId = groupId),
            paidByPersonId = -1L,
            shares = listOf(
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 200.0),
                SplitShareEntity(splitId = 0, personId = asha, shareAmount = 200.0),
                SplitShareEntity(splitId = 0, personId = bilal, shareAmount = 200.0),
            ),
            splitType = "equal",
        )
        return Triple(groupId, asha, bilal)
    }

    @Test
    fun `renaming and changing the icon keeps the expenses and what each person owes`() = runTest {
        val (groupId, asha, bilal) = groupWithDinner()
        val before = repository.personBalances.first().sortedBy { it.person.id }

        val group = db.groupDao().getGroupByIdDirect(groupId)!!
        repository.updateGroup(group.copy(name = "Goa 2026", emoji = "✈️", description = "new year"), listOf(asha, bilal))

        val after = db.groupDao().getGroupByIdDirect(groupId)!!
        assertEquals("Goa 2026", after.name)
        assertEquals("✈️", after.emoji)
        assertEquals("new year", after.description)
        assertEquals(before, repository.personBalances.first().sortedBy { it.person.id })
        assertEquals(1, repository.groupsWithDetails.first().single { it.group.id == groupId }.transactionsCount)
    }

    @Test
    fun `a member removed from a local group keeps their past expenses, and one can be added`() = runTest {
        val (groupId, asha, bilal) = groupWithDinner()
        val chitra = repository.addPerson("Chitra", "#B5EAD7")

        val group = db.groupDao().getGroupByIdDirect(groupId)!!
        repository.updateGroup(group, listOf(asha, chitra))

        val details = repository.groupsWithDetails.first().single { it.group.id == groupId }
        assertEquals(setOf(asha, chitra), details.members.map { it.id }.toSet())
        assertEquals("the dinner is still there", 1, details.transactionsCount)
        val owedByBilal = repository.personBalances.first().single { it.person.id == bilal }.netBalance
        assertEquals("Bilal still owes his share of the dinner", 200.0, owedByBilal, 0.001)
    }
}
