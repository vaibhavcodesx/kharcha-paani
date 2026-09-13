package com.vaibhav.kharchapaani

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.domain.DebtSimplifier
import com.vaibhav.kharchapaani.domain.LocalHeuristicParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kharcha Paani", appName)
  }

  @Test
  fun `test heuristic parser for messy shorthand`() {
    val result = LocalHeuristicParser.parse("burger 370 mcd gpay", listOf("Rohan", "Priya"))
    assertEquals(370.0, result.amount, 0.01)
    assertEquals("Food", result.category)
    assertEquals("GPay", result.paymentMethod)
  }

  @Test
  fun `test sample statement generator`() {
    val result = com.vaibhav.kharchapaani.domain.LocalStatementParser.generateSampleParsedStatement()
    assertTrue(result.allItems.isNotEmpty())
    assertTrue(result.monthGroups.isNotEmpty())
  }
}
