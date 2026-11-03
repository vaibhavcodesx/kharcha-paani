package com.vaibhav.kharchapaani.ui.store

import android.graphics.Bitmap
import android.view.View
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.Profile
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.auth.ProfileState
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO

/**
 * The Play listing's fictional fixture (docs/store-listing/screenshots.md, "Shared fixture"),
 * written into a real Room database so every figure on the captures is one the app worked out.
 *
 * Priya is the signed-in demo user ("me", -1). In the shared group Weekend trip she pays ₹1,800
 * for Dinner, split equally with Rohan and Ankit. Personal entries: Chai stall ₹40, Groceries
 * ₹1,260 and Bus ₹80, all Cash. Only Cash and UPI appear, and no payment brand or real person.
 */
object StoreFixture {
    const val USER_ID = "5a7e0000-0000-4000-8000-00000000a11a"
    const val ROHAN = 1L
    const val ANKIT = 2L
    const val TRIP = 10L
    const val DINNER = 100L
    const val ROHANS_PAYMENT = 200L

    val authUiState = AuthUiState(
        authState = AuthState.SignedIn(userId = USER_ID, email = "priya@example.com", isRefreshPending = false),
        profile = ProfileState.Loaded(Profile(id = USER_ID, displayName = "Priya")),
    )

    private const val HOUR = 60 * 60 * 1000L

    /**
     * When things happened: always in [now]'s month, the most recent first. Early in a month the days
     * are pulled forward to its first; within a day, an entry is kept before now where the month
     * allows. Just after midnight on the 1st that would have meant last month (#210 review), so the
     * month's start wins: an entry may then sit a few seconds after now, never in the month before,
     * and Home and Insights always show the same month.
     */
    internal fun stamp(daysAgo: Long, hour: Int, minute: Int = 0, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstOfMonth = today.withDayOfMonth(1)
        val day = today.minusDays(daysAgo).coerceAtLeast(firstOfMonth)
        val at = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val beforeNow = at.coerceAtMost(now - (daysAgo + 1) * 5 * 60_000L)
        // Seconds after the month starts, the most recent latest, so the order holds there too.
        val monthStart = firstOfMonth.atStartOfDay(zone).toInstant().toEpochMilli() + (10 - daysAgo).coerceAtLeast(0) * 1_000L
        return beforeNow.coerceAtLeast(monthStart)
    }

    suspend fun seed(db: AppDatabase) {
        db.personDao().insertPerson(PersonEntity(id = ROHAN, name = "Rohan", avatarColor = "#70C1FF", profileId = "f1c0ffee-0000-4000-8000-0000000000r0"))
        db.personDao().insertPerson(PersonEntity(id = ANKIT, name = "Ankit", avatarColor = "#B5EAD7", profileId = "f1c0ffee-0000-4000-8000-0000000000a0"))

        db.groupDao().insertGroup(
            GroupEntity(id = TRIP, name = "Weekend trip", emoji = "⛰️", colorHex = "#70C1FF", syncState = SyncState.SYNCED, createdByProfileId = USER_ID),
        )
        db.groupDao().insertGroupMembers(listOf(GroupMemberEntity(groupId = TRIP, personId = ROHAN), GroupMemberEntity(groupId = TRIP, personId = ANKIT)))

        db.transactionDao().insertTransaction(
            TransactionEntity(id = DINNER, type = "expense", amount = 1800.0, category = "Food", merchant = "Dinner", paymentMethod = "UPI", date = stamp(2, 21), isSplit = true, groupId = TRIP, syncState = SyncState.SYNCED),
        )
        val splitId = db.splitDao().insertSplit(SplitEntity(transactionId = DINNER, totalAmount = 1800.0, paidByPersonId = -1L, splitType = "equal"))
        db.splitDao().insertSplitShares(
            listOf(-1L, ROHAN, ANKIT).map { SplitShareEntity(splitId = splitId, personId = it, shareAmount = 600.0, percentage = 100.0 / 3) },
        )

        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 40.0, category = "Food", merchant = "Chai stall", paymentMethod = "Cash", date = stamp(0, 9, 10)))
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 1260.0, category = "Groceries", merchant = "Groceries", paymentMethod = "Cash", date = stamp(1, 18, 30)))
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 80.0, category = "Transport", merchant = "Bus", paymentMethod = "Cash", date = stamp(1, 8, 45)))
    }

    /** Screen 5's state: Rohan says he paid Priya back his ₹600, and she hasn't answered yet. */
    suspend fun rohanSaysHePaid(db: AppDatabase) {
        db.settlementDao().insertSettlement(
            SettlementEntity(
                id = ROHANS_PAYMENT, fromPersonId = ROHAN, toPersonId = -1L, amount = 600.0, date = stamp(0, 8),
                groupId = TRIP, syncState = SyncState.SYNCED, status = SettlementStatus.PENDING, recordedByPersonId = ROHAN,
            ),
        )
    }

    /**
     * Stops the ViewModels a capture created (held in [store]) before its Room database closes: their
     * flows otherwise keep querying a closed database. Their jobs finish on Robolectric's real main
     * looper, so it's kept turning rather than blocked. Safe after a partial setup.
     */
    fun stopViewModels(store: androidx.lifecycle.ViewModelStore, main: com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel?) {
        val job = main?.viewModelScope?.coroutineContext?.get(Job)
        store.clear()
        if (job == null) return
        val deadline = System.currentTimeMillis() + 10_000
        while (!job.isCompleted && System.currentTimeMillis() < deadline) org.robolectric.shadows.ShadowLooper.idleMainLooper()
        check(job.isCompleted) { "the capture's ViewModel didn't stop" }
    }

    /**
     * Draws [view] as it is and writes it as an opaque 24-bit PNG (Play takes no alpha), under the
     * unit-test results so CI keeps it with each run. Returns the file.
     */
    fun save(view: View, name: String): File {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
        rgb.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
        val out = File("build/test-results/testDebugUnitTest/store-listing/$name.png").apply { parentFile?.mkdirs() }
        ImageIO.write(rgb, "png", out)
        return out
    }
}
