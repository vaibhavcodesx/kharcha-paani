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
