package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CatBills
import com.vaibhav.kharchapaani.ui.theme.CatEntertainment
import com.vaibhav.kharchapaani.ui.theme.CatFood
import com.vaibhav.kharchapaani.ui.theme.CatHealth
import com.vaibhav.kharchapaani.ui.theme.CatIncome
import com.vaibhav.kharchapaani.ui.theme.CatOther
import com.vaibhav.kharchapaani.ui.theme.CatRent
import com.vaibhav.kharchapaani.ui.theme.CatShopping
import com.vaibhav.kharchapaani.ui.theme.CatTeamPayment
import com.vaibhav.kharchapaani.ui.theme.CatTransport

object CategoryHelper {

    val allCategories = listOf(
        "Food",
        "Transport",
        "Shopping",
        "Bills",
        "Entertainment",
        "Health",
        "Rent",
        "Team Payment",
        "Income (Client)",
        "Income (Other)",
        "Other"
    )

    val allPaymentMethods = listOf(
        "PopUPI",
        "Debit Card",
        "GPay",
        "PhonePe",
        "Paytm",
        "Credit Card",
        "Cash",
        "Bank Transfer",
        "Other"
    )

    fun getCategoryIcon(category: String): ImageVector {
        return when (category) {
            "Food" -> KpIcons.Food
            "Transport" -> KpIcons.Transport
            "Shopping" -> KpIcons.Shopping
            "Bills" -> KpIcons.Bills
            "Entertainment" -> KpIcons.Entertainment
            "Health" -> KpIcons.Health
            "Rent" -> KpIcons.Rent
            "Team Payment" -> KpIcons.TeamPayment
            "Income (Client)", "Income (Other)" -> KpIcons.Income
            else -> KpIcons.Other
        }
    }

    fun getCategoryColor(category: String): Color {
        return when (category) {
            "Food" -> CatFood
            "Transport" -> CatTransport
            "Shopping" -> CatShopping
            "Bills" -> CatBills
            "Entertainment" -> CatEntertainment
            "Health" -> CatHealth
            "Rent" -> CatRent
            "Team Payment" -> CatTeamPayment
            "Income (Client)", "Income (Other)" -> CatIncome
            else -> CatOther
        }
    }
}
