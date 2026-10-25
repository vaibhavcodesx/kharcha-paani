package com.vaibhav.kharchapaani.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's date as a flow, so anything built on "this month" moves on at midnight by itself.
 *
 * Home's month used to be read once per database write: open the app on the 1st without logging
 * anything and it kept showing last month's spending as this month's (correctness audit,
 * 2026-10-24).
 */
object Today {

    /**
     * Checked every minute rather than slept until midnight: a delay is measured on a clock that
     * stops while the phone sleeps, so one long sleep could wake hours after the date changed. A
     * minute is cheap, and only while something is collecting.
     */
    internal const val CHECK_EVERY_MILLIS = 60_000L

    fun dates(zone: () -> ZoneId = ZoneId::systemDefault, now: () -> Instant = Instant::now): Flow<LocalDate> = flow {
        while (true) {
            emit(now().atZone(zone()).toLocalDate())
            delay(CHECK_EVERY_MILLIS)
        }
    }.distinctUntilChanged()
}
