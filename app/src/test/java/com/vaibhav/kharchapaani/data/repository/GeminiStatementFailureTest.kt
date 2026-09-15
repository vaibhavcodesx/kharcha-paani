package com.vaibhav.kharchapaani.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiStatementFailureTest {

    @Test
    fun `a statement that can't be read yields no result, never sample transactions`() = runTest {
        // An unreadable PDF used to come back as a generated sample statement (Zomato ₹450 and so on),
        // shown in the import sheet as if it were the person's own, one tap away from their ledger.
        val result = GeminiRepository().parseBankOrUpiStatementPdf(ByteArray(0), "statement.pdf")

        assertNull(result)
    }
}
