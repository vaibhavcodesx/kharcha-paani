package com.vaibhav.kharchapaani.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icon set, drawn on a 24-unit grid in one style: a rounded 1.75 outline over a
 * translucent "tone" fill, with the occasional solid accent. Everything is drawn in black, so
 * `Icon(tint = …)` colours the whole icon and the tone keeps its transparency.
 */
object KpIcons {

    // Navigation

    /** A torn-off slip: the running list of what was spent. */
    val Activity = kpIcon("Activity") {
        tone("M6 3.5h12A1.5 1.5 0 0 1 19.5 5v15.5l-2.5-1.5-2.5 1.5-2.5-1.5-2.5 1.5-2.5-1.5-2.5 1.5V5A1.5 1.5 0 0 1 6 3.5z")
        line("M6 3.5h12A1.5 1.5 0 0 1 19.5 5v15.5l-2.5-1.5-2.5 1.5-2.5-1.5-2.5 1.5-2.5-1.5-2.5 1.5V5A1.5 1.5 0 0 1 6 3.5z")
        line("M8.5 8h7M8.5 11.5h7M8.5 15h3.5")
    }

    /** Two overlapping coins, the shared part solid. */
    val Splits = kpIcon("Splits") {
        tone("M3 12a6 6 0 1 0 12 0a6 6 0 1 0-12 0z")
        tone("M9 12a6 6 0 1 0 12 0a6 6 0 1 0-12 0z")
        solid("M12 6.8A6 6 0 0 1 12 17.2A6 6 0 0 1 12 6.8z")
        line("M3 12a6 6 0 1 0 12 0a6 6 0 1 0-12 0z")
        line("M9 12a6 6 0 1 0 12 0a6 6 0 1 0-12 0z")
    }

    /** A ring chart with one slice pulled out. */
    val Insights = kpIcon("Insights") {
        tone("M12 12V4.5A7.5 7.5 0 1 1 4.5 12z")
        line("M12 12V4.5A7.5 7.5 0 1 1 4.5 12z")
        solid("M9.8 9.8H2.5A7.3 7.3 0 0 1 9.8 2.5z")
    }

    val Add = kpIcon("Add") {
        line("M12 5v14M5 12h14", width = 2.25f)
    }

    // Capture and actions

    val Mic = kpIcon("Mic") {
        tone("M9 5.5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0z")
        line("M9 5.5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0z")
        line("M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M9 21h6")
    }

    /** A bill inside a viewfinder. */
    val Scan = kpIcon("Scan") {
        tone("M8.5 7h7v10l-1.75-1-1.75 1-1.75-1-1.75 1z")
        line("M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2")
        line("M8.5 7h7v10l-1.75-1-1.75 1-1.75-1-1.75 1z")
    }

    /** A bank statement page. */
    val Statement = kpIcon("Statement") {
        tone("M7.5 3H14l4 4v12.5a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19.5v-15A1.5 1.5 0 0 1 7.5 3z")
        line("M7.5 3H14l4 4v12.5a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19.5v-15A1.5 1.5 0 0 1 7.5 3z")
        line("M14 3v4h4M9 12h6M9 15.5h4")
    }

    /** Money going to someone: a coin and an arrow out. */
    val Lend = kpIcon("Lend") {
        tone("M3.5 12a5.5 5.5 0 1 0 11 0a5.5 5.5 0 1 0-11 0z")
        line("M3.5 12a5.5 5.5 0 1 0 11 0a5.5 5.5 0 1 0-11 0z")
        line("M16.5 12H21M18.5 9.5L21 12l-2.5 2.5")
    }

    /** Money that went both ways and evened out. */
    val Settle = kpIcon("Settle") {
        line("M4 8.5h14M14.5 5L18 8.5 14.5 12")
        line("M20 15.5H6M9.5 12L6 15.5 9.5 19")
    }

    val Write = kpIcon("Write") {
        tone("M4 20l1-4L15.5 5.5a2.1 2.1 0 0 1 3 3L8 19z")
        line("M4 20l1-4L15.5 5.5a2.1 2.1 0 0 1 3 3L8 19z")
        line("M13.5 7.5l3 3")
    }

    val Send = kpIcon("Send") {
        line("M12 19V5M6 11l6-6 6 6", width = 2.25f)
    }

    val Search = kpIcon("Search") {
        tone("M4.5 10.5a6 6 0 1 0 12 0a6 6 0 1 0-12 0z")
        line("M4.5 10.5a6 6 0 1 0 12 0a6 6 0 1 0-12 0z")
        line("M15 15l5 5")
    }

    val Calendar = kpIcon("Calendar") {
        tone("M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5V10H4z")
        line("M5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-12A1.5 1.5 0 0 1 5.5 5z")
        line("M8 3v4M16 3v4M4 10h16")
    }

    val Close = kpIcon("Close") {
        line("M6.5 6.5l11 11M17.5 6.5l-11 11")
    }

    val Check = kpIcon("Check") {
        line("M5 12.5l4.5 4.5L19 7.5", width = 2.25f)
    }

    val Delete = kpIcon("Delete") {
        tone("M6.5 7h11l-.9 11.6a1.5 1.5 0 0 1-1.5 1.4H8.9a1.5 1.5 0 0 1-1.5-1.4z")
        line("M5 7h14M9.5 7V5.5A1.5 1.5 0 0 1 11 4h2a1.5 1.5 0 0 1 1.5 1.5V7")
        line("M6.5 7l.9 11.6a1.5 1.5 0 0 0 1.5 1.4h6.2a1.5 1.5 0 0 0 1.5-1.4L17.5 7M10.5 11v5M13.5 11v5")
    }

    val More = kpIcon("More") {
        solid("M4.25 12a1.75 1.75 0 1 0 3.5 0a1.75 1.75 0 1 0-3.5 0zM10.25 12a1.75 1.75 0 1 0 3.5 0a1.75 1.75 0 1 0-3.5 0zM16.25 12a1.75 1.75 0 1 0 3.5 0a1.75 1.75 0 1 0-3.5 0z")
    }

    val ChevronDown = kpIcon("ChevronDown") {
        line("M6 9.5l6 6 6-6")
    }

    // Entry-form rows. Every row in the quick-add sheet carries one, so two-of-five no longer
    // reads as an oversight.

    /** A shopfront with an awning: who or what you paid. */
    val Merchant = kpIcon("Merchant") {
        tone("M4 9.5h16V19a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19z")
        line("M4 9.5h16V19a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19z")
        line("M3 9.5l1.6-4.2A1.5 1.5 0 0 1 6 4.5h12a1.5 1.5 0 0 1 1.4 0.8L21 9.5")
        line("M9.5 20.5v-5h5v5")
    }

    /** A payment card with a stripe. */
    val Card = kpIcon("Card") {
        tone("M3.5 6.5h17a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-17a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z")
        line("M3.5 6.5h17a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-17a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z")
        solid("M2.5 9.5h19v2.5h-19z")
        line("M6 15h3.5")
    }

    /** Two people: which group this belongs to. */
    val Group = kpIcon("Group") {
        tone("M9.5 4a3.2 3.2 0 1 1 0 6.4a3.2 3.2 0 0 1 0-6.4z")
        line("M9.5 4a3.2 3.2 0 1 1 0 6.4a3.2 3.2 0 0 1 0-6.4z")
        line("M2.8 20a6.7 6.7 0 0 1 13.4 0")
        line("M16 4.6a3.2 3.2 0 0 1 0 6.2")
        line("M18 13.8a6.7 6.7 0 0 1 3.2 6.2")
    }

    // Categories

    val Food = kpIcon("Food") {
        tone("M4 11.5h16a8 7 0 0 1-16 0z")
        line("M4 11.5h16a8 7 0 0 1-16 0z")
        line("M9.5 21h5")
        line("M8.5 8.5c-1-1.1 1-2.2 0-3.3M12 8.5c-1-1.1 1-2.2 0-3.3M15.5 8.5c-1-1.1 1-2.2 0-3.3")
    }

    /** An auto-rickshaw: domed canopy over an open side, low body. */
    val Transport = kpIcon("Transport") {
        tone("M3.5 11.5h17v4.5h-17z")
        line("M3.5 11.5V8.5a4 4 0 0 1 4-4h6.5c3.3 0 5.8 2.6 6.5 7")
        line("M5 16H3.5v-4.5h17V16h-1M9 16h6.5")
        line("M11.5 4.5v7")
        line("M5 16.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM15.5 16.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0z")
    }

    val Shopping = kpIcon("Shopping") {
        tone("M5.5 8.5h13l-1 11a1.5 1.5 0 0 1-1.5 1.4H8a1.5 1.5 0 0 1-1.5-1.4z")
        line("M5.5 8.5h13l-1 11a1.5 1.5 0 0 1-1.5 1.4H8a1.5 1.5 0 0 1-1.5-1.4z")
        line("M9 11V7a3 3 0 0 1 6 0v4")
    }

    /** A basket with handles. */
    val Groceries = kpIcon("Groceries") {
        tone("M4 10h16l-1.6 8.4a2 2 0 0 1-2 1.6H7.6a2 2 0 0 1-2-1.6z")
        line("M4 10h16l-1.6 8.4a2 2 0 0 1-2 1.6H7.6a2 2 0 0 1-2-1.6z")
        line("M8 10l3-6M16 10l-3-6")
        line("M9.5 13.5v3M14.5 13.5v3")
    }

    /** A line climbing to the top right. */
    val Beverages = kpIcon("Beverages") {
        tone("M7.5 11h9l-.7 8.1a1 1 0 0 1-1 .9H9.2a1 1 0 0 1-1-.9z")
        line("M7 6.5h10l-1.2 12.6a1 1 0 0 1-1 .9H9.2a1 1 0 0 1-1-.9z")
        line("M7.5 11h9")
        line("M13.5 6.5L15 3h2")
    }
    val PackagedGoods = kpIcon("PackagedGoods") {
        tone("M4 8l8-4 8 4-8 4z")
        line("M4 8l8-4 8 4v8l-8 4-8-4z")
        line("M4 8l8 4 8-4M12 12v8")
    }
    val SelfCare = kpIcon("SelfCare") {
        tone("M4.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0zM14.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z")
        line("M4.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0zM14.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z")
        line("M8.9 15.1L17 4M15.1 15.1L7 4")
    }
    val Investment = kpIcon("Investment") {
        tone("M4 17l5-5 4 4 7-7v11H4z")
        line("M4 17l5-5 4 4 7-7")
        line("M15 9h5v5")
        line("M4 20h16")
    }

    /** Utilities: a bolt. */
    val Bills = kpIcon("Bills") {
        tone("M13.5 3L6 13.5h5.5L10.5 21 18 10.5h-5.5z")
        line("M13.5 3L6 13.5h5.5L10.5 21 18 10.5h-5.5z")
    }

    /** A ticket, the same shape the app uses for money that changed hands. */
    val Entertainment = kpIcon("Entertainment") {
        tone("M4 7.5A1.5 1.5 0 0 1 5.5 6h13A1.5 1.5 0 0 1 20 7.5V10a2 2 0 0 0 0 4v2.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 16.5V14a2 2 0 0 0 0-4z")
        line("M4 7.5A1.5 1.5 0 0 1 5.5 6h13A1.5 1.5 0 0 1 20 7.5V10a2 2 0 0 0 0 4v2.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 16.5V14a2 2 0 0 0 0-4z")
        line("M14.5 8.25v1M14.5 11.5v1M14.5 14.75v1")
    }

    /** A capsule, half filled. */
    val Health = kpIcon("Health") {
        solid("M14.47 14.47L17.66 11.29A3.5 3.5 0 0 0 12.71 6.34L9.53 9.53z")
        tone("M11.29 17.66L17.66 11.29A3.5 3.5 0 0 0 12.71 6.34L6.34 12.71A3.5 3.5 0 0 0 11.29 17.66z")
        line("M11.29 17.66L17.66 11.29A3.5 3.5 0 0 0 12.71 6.34L6.34 12.71A3.5 3.5 0 0 0 11.29 17.66z")
    }

    val Rent = kpIcon("Rent") {
        tone("M4 10.5L12 4l8 6.5V19a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19z")
        line("M4 10.5L12 4l8 6.5V19a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19z")
        line("M10 20.5v-4.5a2 2 0 0 1 4 0v4.5")
    }

    /** Two people, one in front. */
    val TeamPayment = kpIcon("TeamPayment") {
        tone("M5.5 8a3.25 3.25 0 1 0 6.5 0a3.25 3.25 0 1 0-6.5 0zM3 19.5a5.75 5.75 0 0 1 11.5 0z")
        line("M5.5 8a3.25 3.25 0 1 0 6.5 0a3.25 3.25 0 1 0-6.5 0z")
        line("M3 19.5a5.75 5.75 0 0 1 11.5 0")
        line("M14.5 7.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0z")
        line("M16 13.2a5 5 0 0 1 5 5.3v1")
    }

    /** Money arriving: an arrow down into a banknote. */
    val Income = kpIcon("Income") {
        tone("M5 10h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z")
        line("M5 10h3.5M15.5 10H19a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2")
        line("M9.75 15a2.25 2.25 0 1 0 4.5 0a2.25 2.25 0 1 0-4.5 0z")
        line("M12 3v7.5M9 7.5l3 3 3-3")
    }

    // Payment methods. Three glyphs on the same grid as everything else, so a transaction row's
    // payment pill belongs to the same icon family as its category badge.

    /** A banknote: cash. */
    val Cash = kpIcon("Cash") {
        tone("M2.5 6.5h19a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-19a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z")
        line("M2.5 6.5h19a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-19a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z")
        line("M9.75 12a2.25 2.25 0 1 0 4.5 0a2.25 2.25 0 1 0-4.5 0z")
        line("M5 9.5v5M19 9.5v5")
    }

    /** A phone showing a code: paying by UPI. */
    val Upi = kpIcon("Upi") {
        tone("M7.5 2.5h9a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 20V4a1.5 1.5 0 0 1 1.5-1.5z")
        line("M7.5 2.5h9a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 20V4a1.5 1.5 0 0 1 1.5-1.5z")
        line("M9.25 6.5h2.5v2.5h-2.5zM12.25 6.5h2.5v2.5h-2.5zM9.25 11h2.5v2.5h-2.5z")
        solid("M12.25 11h2.5v2.5h-2.5z")
        line("M10 17h4")
    }

    /** A building with columns: a bank transfer. */
    val Bank = kpIcon("Bank") {
        tone("M3 9.5h18v9H3z")
        line("M12 3l9 5H3l9-5z")
        line("M3 18.5h18M5 20.5h14")
        line("M6.5 10.5v6M10.5 10.5v6M13.5 10.5v6M17.5 10.5v6")
    }

    val Other = kpIcon("Other") {
        tone("M15.5 13.5h2.5a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-2.5a2 2 0 0 1-2-2v-2.5a2 2 0 0 1 2-2z")
        line("M6 4h2.5a2 2 0 0 1 2 2v2.5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z")
        line("M13.5 7.25a3.25 3.25 0 1 0 6.5 0a3.25 3.25 0 1 0-6.5 0z")
        line("M6 13.5h2.5a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2.5a2 2 0 0 1 2-2z")
        line("M15.5 13.5h2.5a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-2.5a2 2 0 0 1-2-2v-2.5a2 2 0 0 1 2-2z")
    }
}

private const val ToneAlpha = 0.3f

private class KpIconScope(val builder: ImageVector.Builder) {
    fun line(pathData: String, width: Float = 1.75f) {
        builder.addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun tone(pathData: String) {
        builder.addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black), fillAlpha = ToneAlpha)
    }

    fun solid(pathData: String) {
        builder.addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
    }
}

private fun kpIcon(name: String, draw: KpIconScope.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "KpIcons.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).also { KpIconScope(it).draw() }.build()
