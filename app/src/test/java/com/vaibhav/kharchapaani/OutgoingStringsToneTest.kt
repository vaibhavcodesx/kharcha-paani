package com.vaibhav.kharchapaani

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.domain.HisaabItemiser
import com.vaibhav.kharchapaani.reminder.SettlementReminders
import com.vaibhav.kharchapaani.ui.format.AskMessage
import com.vaibhav.kharchapaani.util.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory

/**
 * T12 Safety Net Test: Ensures that nothing one person sends or shows another says "you owe",
 * "overdue", or "pay now" (Handbook section 4 & 7). In-app labels about what you yourself owe
 * are strictly restricted to the 9 designated self-balance views.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutgoingStringsToneTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val bannedPhrases = listOf("you owe", "overdue", "pay now")

    private val allowedSelfOweKeys = setOf(
        "home_you_owe",
        "splits_you_owe",
        "splits_group_owe",
        "splits_friend_you_owe",
        "payment_you_owe",
        "group_detail_owe",
        "person_ledger_owe",
        "person_ledger_owe_amount",
        "widget_copy_owe",
    )

    private val outgoingResourceKeys = listOf(
        "shell_copy_invite",
        "shell_copy_share_invite",
        "payment_share_hisaab",
        "payment_ask_name",
        "action_notice_ask_chooser",
        "ask_payment_code_label",
        "ask_status_today",
        "ask_status_yesterday",
        "ask_status_days",
        "upi_copy_chooser",
        "support_ai_report_subject",
        "support_ai_report_body",
        "support_device_details",
        "action_notice_backup_chooser",
        "action_notice_export",
    )

    private fun assertNoBannedPhrases(text: String, contextDescription: String) {
        for (phrase in bannedPhrases) {
            assertFalse(
                "Found banned phrase '$phrase' in $contextDescription: \"$text\"",
                text.contains(phrase, ignoreCase = true),
            )
        }
    }

    private fun loadStringsXml(): Map<String, List<String>> {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("../app/src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("strings.xml not found at any candidate path: ${candidates.map { it.absolutePath }}")

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val map = mutableMapOf<String, MutableList<String>>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            map.getOrPut(name) { mutableListOf() }.add(node.textContent)
        }

        val pluralsNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralsNodes.length) {
            val node = pluralsNodes.item(i) as Element
            val name = node.getAttribute("name")
            val itemNodes = node.getElementsByTagName("item")
            for (j in 0 until itemNodes.length) {
                val itemNode = itemNodes.item(j) as Element
                map.getOrPut(name) { mutableListOf() }.add(itemNode.textContent)
            }
        }

        return map
    }

    @Test
    fun `all notification string resources are free of banned phrases`() {
        val allStrings = loadStringsXml()
        val notificationStrings = allStrings.filter { (key, _) -> key.startsWith("notification_") }

        assertTrue("strings.xml must define notification resources", notificationStrings.isNotEmpty())
        for ((key, texts) in notificationStrings) {
            for (text in texts) {
                assertNoBannedPhrases(text, "notification resource '$key'")
            }
        }
    }

    @Test
    fun `all outgoing and share string resources are free of banned phrases`() {
        val allStrings = loadStringsXml()

        for (key in outgoingResourceKeys) {
            val texts = allStrings[key] ?: error("Missing outgoing resource '$key'")
            for (text in texts) {
                assertNoBannedPhrases(text, "outgoing resource '$key'")
            }
        }
    }

    @Test
    fun `overdue and pay now never appear in strings_xml and you owe is strictly restricted to self debt labels`() {
        val allStrings = loadStringsXml()

        for ((key, texts) in allStrings) {
            for (text in texts) {
                assertFalse("Overdue must never appear in resource '$key'", text.contains("overdue", ignoreCase = true))
                assertFalse("Pay now must never appear in resource '$key'", text.contains("pay now", ignoreCase = true))
            }
        }

        val keysWithYouOwe = allStrings.filter { (_, texts) ->
            texts.any { it.contains("you owe", ignoreCase = true) }
        }.keys

        assertEquals(
            "Only designated in-app self-debt labels may say 'you owe'",
            allowedSelfOweKeys,
            keysWithYouOwe,
        )
    }

    @Test
    fun `AskMessage builder produces friendly phrasing without banned phrases across all item kinds`() {
        val hisaab = HisaabItemiser.Hisaab(
            carriedOver = -40.0,
            earlier = -10.0,
            lines = listOf(
                HisaabItemiser.Line("Dinner", HisaabItemiser.Kind.THEIR_SHARE, 1_000L, 300.0),
                HisaabItemiser.Line("Cab", HisaabItemiser.Kind.MY_SHARE, 2_000L, -50.0),
                HisaabItemiser.Line("Snacks", HisaabItemiser.Kind.AS_GUEST, 3_000L, 120.0),
                HisaabItemiser.Line("Transfer", HisaabItemiser.Kind.I_SENT, 4_000L, 200.0),
                HisaabItemiser.Line("UPI refund", HisaabItemiser.Kind.THEY_SENT, 5_000L, -100.0),
            ),
            total = 420.0,
        )

        // Full ask
        val fullMsg = AskMessage.build("Rohan Sharma", hisaab, asked = 420.0, upiId = "rohan@okhdfcbank", zone = ZoneOffset.UTC)
        assertNoBannedPhrases(fullMsg, "AskMessage.build (full ask)")
        assertTrue(fullMsg.contains("Hey Rohan, here's our hisaab:"))
        assertTrue(fullMsg.contains("Total: ₹420"))

        // Partial ask
        val partialMsg = AskMessage.build("Rohan", hisaab, asked = 200.0, upiId = null, zone = ZoneOffset.UTC)
        assertNoBannedPhrases(partialMsg, "AskMessage.build (partial ask)")
        assertTrue(partialMsg.contains("Total: ₹420\nAsking ₹200 of the ₹420 for now.\n"))
        assertFalse("partial ask must not say 'you owe'", partialMsg.contains("you owe", ignoreCase = true))
    }

    @Test
    fun `AskMessage asked label produces neutral status without banned phrases`() {
        val now = 1_800_000_000_000L
        val dayMillis = 86_400_000L

        for (days in listOf(0L, 1L, 2L, 5L, 30L)) {
            val askedAt = now - days * dayMillis
            val label = AskMessage.askedLabel(app, askedAt, now, ZoneOffset.UTC)
            assertNoBannedPhrases(label, "AskMessage.askedLabel (days=$days)")
        }
    }

    @Test
    fun `SettlementReminders builder across all stages and kinds never uses banned phrases`() {
        val now = 1_800_000_000_000L
        val dayMillis = 86_400_000L

        for (theyPaidMe in listOf(true, false)) {
            for (isIou in listOf(true, false)) {
                for (stage in listOf(0, 1, 2, 5, 10)) {
                    val settlement = SettlementEntity(
                        id = 101,
                        fromPersonId = if (theyPaidMe) 2 else -1L,
                        toPersonId = if (theyPaidMe) -1L else 2,
                        amount = 250.0,
                        isIou = isIou,
                        status = SettlementStatus.PENDING,
                        recordedByPersonId = 2,
                        createdAt = now - (stage * SettlementReminders.EVERY_DAYS * dayMillis),
                    )

                    val (title, body) = SettlementReminders.textFor(app, settlement, "Priya", stage, now)
                    assertNoBannedPhrases(title, "SettlementReminders title (theyPaidMe=$theyPaidMe, isIou=$isIou, stage=$stage)")
                    assertNoBannedPhrases(body, "SettlementReminders body (theyPaidMe=$theyPaidMe, isIou=$isIou, stage=$stage)")

                    val (fallbackTitle, fallbackBody) = SettlementReminders.textFor(
                        app,
                        settlement,
                        app.getString(R.string.notification_groupmate),
                        stage,
                        now,
                    )
                    assertNoBannedPhrases(fallbackTitle, "SettlementReminders fallback title")
                    assertNoBannedPhrases(fallbackBody, "SettlementReminders fallback body")
                }
            }
        }
    }

    @Test
    fun `NotificationHelper notification titles channels and public redaction stay friendly`() {
        assertNoBannedPhrases(app.getString(R.string.notification_daily_channel), "notification_daily_channel")
        assertNoBannedPhrases(app.getString(R.string.notification_daily_description), "notification_daily_description")
        assertNoBannedPhrases(app.getString(R.string.notification_payment_channel), "notification_payment_channel")
        assertNoBannedPhrases(app.getString(R.string.notification_payment_description), "notification_payment_description")
        assertNoBannedPhrases(app.getString(R.string.notification_daily_title), "notification_daily_title")
        assertNoBannedPhrases(app.getString(R.string.notification_daily_body), "notification_daily_body")
        assertNoBannedPhrases(app.getString(R.string.notification_payment_public), "notification_payment_public")
    }

    @Test
    fun `outgoing invite share intent text and chooser title stay welcoming`() {
        val inviteText = app.getString(R.string.shell_copy_invite, "Goa Trip", "kharchapaani://join/AB2CD3EF")
        assertNoBannedPhrases(inviteText, "shell_copy_invite")
        assertNoBannedPhrases(app.getString(R.string.shell_copy_share_invite), "shell_copy_share_invite")
    }

    @Test
    fun `Support email and report drafts do not use coercive debt phrases`() {
        assertNoBannedPhrases(app.getString(R.string.support_ai_report_subject), "support_ai_report_subject")
        assertNoBannedPhrases(
            app.getString(R.string.support_ai_report_body, "Split inquiry", "AI suggested answer"),
            "support_ai_report_body",
        )
    }
}
