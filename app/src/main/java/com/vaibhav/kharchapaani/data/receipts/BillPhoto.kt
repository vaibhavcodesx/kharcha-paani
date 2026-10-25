package com.vaibhav.kharchapaani.data.receipts

import java.io.File

/** The add sheet's bill photo while the sheet is open; nothing in the ledger changes until Save. */
sealed interface BillPhoto {
    data object None : BillPhoto

    /** A picture being decoded and written; Save waits for it. */
    data object Processing : BillPhoto

    /**
     * A photo to show. [staged] is a new one waiting for Save; null for the one the expense already
     * had when the sheet opened.
     */
    data class Kept(val file: File, val staged: ReceiptStore.Staged?) : BillPhoto

    /** The expense has a photo row, but its file isn't on this phone (cleared data, a restore). */
    data object Missing : BillPhoto
}
