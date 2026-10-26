package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CatBeverages
import com.vaibhav.kharchapaani.ui.theme.CatBills
import com.vaibhav.kharchapaani.ui.theme.CatEntertainment
import com.vaibhav.kharchapaani.ui.theme.CatFood
import com.vaibhav.kharchapaani.ui.theme.CatHealth
import com.vaibhav.kharchapaani.ui.theme.CatIncome
import com.vaibhav.kharchapaani.ui.theme.CatOther
import com.vaibhav.kharchapaani.ui.theme.CatPackagedGoods
import com.vaibhav.kharchapaani.ui.theme.CatRent
import com.vaibhav.kharchapaani.ui.theme.CatSelfCare
import com.vaibhav.kharchapaani.ui.theme.CatShopping
import com.vaibhav.kharchapaani.ui.theme.CatInvestment
import com.vaibhav.kharchapaani.ui.theme.CatGroceries
import com.vaibhav.kharchapaani.ui.theme.CatTeamPayment
import com.vaibhav.kharchapaani.ui.theme.CatTransport

object CategoryHelper {

    // The choices themselves live in domain.Categories; this maps a name to how it looks. A name it
    // doesn't know (your own category, or a groupmate's) gets Other's icon and colour.

    fun getCategoryIcon(category: String): ImageVector {
        return when (category) {
            "Food" -> KpIcons.Food
            "Transport" -> KpIcons.Transport
            "Shopping" -> KpIcons.Shopping
            "Groceries" -> KpIcons.Groceries
            "Beverages" -> KpIcons.Beverages
            "Packaged goods" -> KpIcons.PackagedGoods
            "Self-care" -> KpIcons.SelfCare
            "Investment" -> KpIcons.Investment
            "Bills" -> KpIcons.Bills
            "Entertainment" -> KpIcons.Entertainment
            "Health" -> KpIcons.Health
            "Rent" -> KpIcons.Rent
            "Team Payment" -> KpIcons.TeamPayment
            "Income (Salary)", "Income (Client)", "Income (Other)" -> KpIcons.Income
            else -> KpIcons.Other
        }
    }

    fun getCategoryColor(category: String): Color {
        return when (category) {
            "Food" -> CatFood
            "Transport" -> CatTransport
            "Shopping" -> CatShopping
            "Groceries" -> CatGroceries
            "Beverages" -> CatBeverages
            "Packaged goods" -> CatPackagedGoods
            "Self-care" -> CatSelfCare
            "Investment" -> CatInvestment
            "Bills" -> CatBills
            "Entertainment" -> CatEntertainment
            "Health" -> CatHealth
            "Rent" -> CatRent
            "Team Payment" -> CatTeamPayment
            "Income (Salary)", "Income (Client)", "Income (Other)" -> CatIncome
            else -> CatOther
        }
    }
}
