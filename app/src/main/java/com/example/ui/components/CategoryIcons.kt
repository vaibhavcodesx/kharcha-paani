package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CatBills
import com.example.ui.theme.CatEntertainment
import com.example.ui.theme.CatFood
import com.example.ui.theme.CatHealth
import com.example.ui.theme.CatIncome
import com.example.ui.theme.CatOther
import com.example.ui.theme.CatRent
import com.example.ui.theme.CatShopping
import com.example.ui.theme.CatTeamPayment
import com.example.ui.theme.CatTransport

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
            "Food" -> Icons.Default.Restaurant
            "Transport" -> Icons.Default.DirectionsCar
            "Shopping" -> Icons.Default.ShoppingBag
            "Bills" -> Icons.Default.Receipt
            "Entertainment" -> Icons.Default.Movie
            "Health" -> Icons.Default.MedicalServices
            "Rent" -> Icons.Default.Home
            "Team Payment" -> Icons.Default.Group
            "Income (Client)", "Income (Other)" -> Icons.Default.AttachMoney
            else -> Icons.Default.Category
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
