package com.vaibhav.kharchapaani.ui.session

/**
 * Work that intentionally outlives a ViewModel scope but must finish before its Room ledger closes.
 * Called after the store clears and registered scope jobs finish. Repeated awaits must be safe:
 * another account change may cancel a previous waiter without canceling the closing work itself.
 */
interface SessionClosingWork {
    suspend fun awaitSessionClosingWork()
}
