package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity

/*
 * Reading a person's share of a split, now that a member can host guests (design item 9).
 *
 * A guest's share is stored under their host's personId with the guest's name on it, so a person can
 * have more than one row in a split. Two questions then have different answers, and every caller
 * has to say which it's asking:
 *
 *  - [coveredBy]: what the person owes the payer for — their own share plus their guests'. Group
 *    balances, who owes whom.
 *  - [ownShareOf]: what the person themselves consumed. Spending totals and "your share": covering
 *    a friend's dinner is lending, not spending.
 *
 * `find { it.personId == x }` answered neither: it returned whichever row came first.
 */

/** The person's own share, not counting guests they cover; null when they have no row of their own. */
fun List<SplitShareEntity>.ownShareOf(personId: Long): Double? =
    filter { it.personId == personId && !it.isGuest }.takeIf { it.isNotEmpty() }?.sumOf { it.shareAmount }

/** Everything the person owes the payer for, their guests included; null when they have no rows. */
fun List<SplitShareEntity>.coveredBy(personId: Long): Double? =
    filter { it.personId == personId }.takeIf { it.isNotEmpty() }?.sumOf { it.shareAmount }

/** Every share that isn't the person's own consumption: other people's, and guests they cover. */
fun List<SplitShareEntity>.allButOwnShareOf(personId: Long): Double =
    filterNot { it.personId == personId && !it.isGuest }.sumOf { it.shareAmount }

/** On the host's phone: what the friend owes the host as a guest in these shares. */
fun List<SplitShareEntity>.owedAsGuestBy(friendId: Long): Double =
    filter { it.guestPersonId == friendId }.sumOf { it.shareAmount }
