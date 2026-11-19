package com.vaibhav.kharchapaani.data.repository

/** An edit or delete committed, or the reason the stored entry could not be changed. */
enum class ExpenseWriteResult {
    Applied,
    Missing,
    NotAllowed,
    PayerEditRestricted,
}
