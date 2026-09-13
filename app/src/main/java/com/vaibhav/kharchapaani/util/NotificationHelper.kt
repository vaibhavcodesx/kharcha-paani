package com.vaibhav.kharchapaani.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vaibhav.kharchapaani.MainActivity
import com.vaibhav.kharchapaani.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID_MONTHLY_INSIGHTS = "kharcha_paani_monthly_insights"
    const val CHANNEL_NAME_MONTHLY_INSIGHTS = "Monthly Spending Insights & Alerts"
    const val NOTIFICATION_ID_MONTHLY = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_MONTHLY_INSIGHTS,
                CHANNEL_NAME_MONTHLY_INSIGHTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monthly spending breakdown, top category alerts, and budget insights"
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showMonthlySpendingNotification(
        context: Context,
        totalExpense: Double,
        topCategory: String,
        topCategoryAmount: Double
    ): Boolean {
        val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        return sendMonthlySpendingAlert(
            context = context,
            monthName = currentMonth,
            topCategory = topCategory,
            topCategoryAmount = topCategoryAmount,
            totalExpense = totalExpense
        )
    }

    fun sendMonthlySpendingAlert(
        context: Context,
        monthName: String,
        topCategory: String,
        topCategoryAmount: Double,
        totalExpense: Double
    ): Boolean {
        createNotificationChannels(context)

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }

        val formattedTotal = "₹${String.format(Locale.US, "%.0f", totalExpense)}"
        val formattedTop = "₹${String.format(Locale.US, "%.0f", topCategoryAmount)}"
        val percentage = if (totalExpense > 0) ((topCategoryAmount / totalExpense) * 100).toInt() else 0

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "📊 $monthName Spending Digest • Kharcha Paani"
        val message = "You spent $formattedTotal this month. Top category was $topCategory at $formattedTop ($percentage% of total)."

        val bigText = """
            Monthly Financial Summary ($monthName):
            • Total Spent: $formattedTotal
            • Highest Expense: $topCategory ($formattedTop - $percentage%)
            • Tap to view your complete category breakdown and settle pending group splits!
        """.trimIndent()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MONTHLY_INSIGHTS)
            .setSmallIcon(R.drawable.ic_kharcha_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFFF7A3D.toInt())
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MONTHLY, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
