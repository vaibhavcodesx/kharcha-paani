package com.vaibhav.kharchapaani.data.model

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity

/** Home can show money only after all of its ledger sources have answered at least once. */
data class HomeSnapshot(
    val activity: List<ActivityItem>,
    val people: List<PersonEntity>,
    val totalNetOwed: Double,
    val monthlyAnalytics: MonthlyAnalytics,
)
