package com.vaibhav.kharchapaani.data.sync

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Realtime as a doorbell, not a data feed (step 4 design): an event only says "something you can
 * see changed", and the answer is always a normal sync. Pulls stay the single way rows arrive, so
 * a dropped or duplicated event can't corrupt anything; at worst a change waits for the next
 * foreground or periodic sync.
 */
class SyncDoorbell(
    private val changes: () -> Flow<Unit>,
    private val ring: () -> Unit,
    private val quietPeriod: Duration = 2.seconds,
    private val maxRetryDelay: Duration = 60.seconds,
    private val onRetry: (cause: Throwable, wait: Duration) -> Unit = { cause, wait ->
        Log.w(TAG, "Realtime subscription failed; retrying in $wait", cause)
    },
) {
    /** Runs until cancelled; call it while the app is in the foreground and an account is signed in. */
    @OptIn(FlowPreview::class)
    suspend fun run() {
        flow { emitAll(changes()) }
            // A burst (someone saving several expenses, or this device's own push echoing back)
            // becomes one sync.
            .debounce(quietPeriod)
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                val wait = minOf((1 shl attempt.coerceAtMost(6).toInt()).seconds, maxRetryDelay)
                onRetry(cause, wait)
                delay(wait)
                true
            }
            .collect { ring() }
    }

    private companion object {
        const val TAG = "SyncDoorbell"
    }
}

/**
 * Changes to the tables sync pulls, as Realtime delivers them after RLS. Splits, shares and
 * memberships aren't published: every change to them bumps a published parent row.
 */
class SupabaseLedgerChanges(private val supabase: SupabaseClient, private val userId: String) {

    fun changes(): Flow<Unit> = flow {
        val channel = supabase.channel("ledger-$userId")
        val tableChanges = PUBLISHED_TABLES.map { table ->
            channel.postgresChangeFlow<PostgresAction>(schema = "public") { this.table = table }.map { }
        }
        // A reconnect after a dropped socket may have missed events, so it rings too. The first
        // subscription is skipped: opening the app already syncs.
        val resubscribed = channel.status
            .filter { it == RealtimeChannel.Status.SUBSCRIBED }
            .drop(1)
            .map { }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            merge(merge(*tableChanges.toTypedArray()), resubscribed).collect { emit(it) }
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }

    private companion object {
        val PUBLISHED_TABLES = listOf("transactions", "settlements", "groups")
    }
}
