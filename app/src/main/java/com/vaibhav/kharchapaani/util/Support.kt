package com.vaibhav.kharchapaani.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.vaibhav.kharchapaani.BuildConfig

/** Writing to the person who makes the app: feedback, and reporting an AI answer. */
object Support {

    /** Also the grievance contact in the privacy policy. */
    const val EMAIL = "vaibhav.alt.chauhan@gmail.com"

    /**
     * Opens the person's mail app on a message to [EMAIL], with the app and phone versions filled
     * in so a report can be followed up. Nothing is sent until they send it. False when the phone
     * has no mail app.
     */
    fun email(context: Context, subject: String, body: String): Boolean {
        val details = "\n\n—\nKharcha Paani ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body + details)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Play's AI-generated content policy: an AI answer someone finds wrong or offensive can be reported. */
    fun reportAiAnswer(context: Context, question: String, answer: String): Boolean = email(
        context,
        subject = "Reporting an AI answer",
        body = "What was wrong with it:\n\n\nThe question: $question\nThe answer:\n$answer",
    )
}
