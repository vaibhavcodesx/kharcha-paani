package com.vaibhav.kharchapaani

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * T13 Safety Net Test: Enforces strict formatting hygiene across all Android string and
 * plural resources in strings.xml and widget_strings.xml:
 * 1. No unescaped stray `%` characters (must be `%%` or a valid format specifier).
 * 2. Positional argument indexing (`%1$s`, `%2$d`) whenever format substitutions are present.
 * 3. Identical format placeholders across all quantity forms within each `<plurals>` resource.
 * 4. Runtime formatting execution safety with dummy arguments.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StringsXmlFormattingTest {

    data class StringEntry(
        val name: String,
        val text: String,
        val formatted: Boolean,
        val sourceFile: String,
    )

    data class PluralEntry(
        val name: String,
        val items: Map<String, String>,
        val formatted: Boolean,
        val sourceFile: String,
    )

    data class FormatSpecifier(
        val raw: String,
        val argumentIndex: Int?,
        val conversion: Char,
    )

    data class StrayPercent(
        val index: Int,
        val context: String,
    )

    companion object {
        /**
         * Android / Java Formatter pattern matching format specifiers:
         * %[argument_index$][flags][width][.precision][tT]conversion
         */
        private val FORMAT_SPECIFIER_PATTERN = Pattern.compile(
            "^%(?:(\\d+)\\$)?([-#+0,(]*\\d*(?:\\.\\d+)?(?:[tT])?([a-zA-Z%n]))",
        )

        fun findStrayPercents(text: String, formatted: Boolean = true): List<StrayPercent> {
            if (!formatted) return emptyList()
            val strays = mutableListOf<StrayPercent>()
            var i = 0
            val n = text.length
            while (i < n) {
                if (text[i] == '%') {
                    val remaining = text.substring(i)
                    val matcher = FORMAT_SPECIFIER_PATTERN.matcher(remaining)
                    if (matcher.find() && matcher.start() == 0) {
                        val matchedText = matcher.group()
                        i += matchedText.length
                    } else {
                        val context = text.substring(i, minOf(i + 15, n))
                        strays.add(StrayPercent(i, context))
                        i++
                    }
                } else {
                    i++
                }
            }
            return strays
        }

        fun extractSpecifiers(text: String, formatted: Boolean = true): List<FormatSpecifier> {
            if (!formatted) return emptyList()
            val specifiers = mutableListOf<FormatSpecifier>()
            var i = 0
            val n = text.length
            while (i < n) {
                if (text[i] == '%') {
                    val remaining = text.substring(i)
                    val matcher = FORMAT_SPECIFIER_PATTERN.matcher(remaining)
                    if (matcher.find() && matcher.start() == 0) {
                        val raw = matcher.group()
                        if (raw != "%%" && raw != "%n") {
                            val argIndex = matcher.group(1)?.toIntOrNull()
                            val conversion = matcher.group(2)!!.last()
                            specifiers.add(FormatSpecifier(raw, argIndex, conversion))
                        }
                        i += raw.length
                    } else {
                        i++
                    }
                } else {
                    i++
                }
            }
            return specifiers
        }

        fun loadResourceFiles(): Pair<List<StringEntry>, List<PluralEntry>> {
            val fileNames = listOf("strings.xml", "widget_strings.xml")
            val strings = mutableListOf<StringEntry>()
            val plurals = mutableListOf<PluralEntry>()

            for (fileName in fileNames) {
                val candidates = listOf(
                    File("src/main/res/values/$fileName"),
                    File("../app/src/main/res/values/$fileName"),
                    File("app/src/main/res/values/$fileName"),
                )
                val file = candidates.firstOrNull { it.exists() }
                    ?: error("$fileName not found at any candidate path: ${candidates.map { it.absolutePath }}")

                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

                val stringNodes = doc.getElementsByTagName("string")
                for (i in 0 until stringNodes.length) {
                    val node = stringNodes.item(i) as Element
                    val name = node.getAttribute("name")
                    val formattedAttr = node.getAttribute("formatted")
                    val formatted = formattedAttr.isEmpty() || formattedAttr.toBoolean()
                    strings.add(StringEntry(name, node.textContent, formatted, fileName))
                }

                val pluralNodes = doc.getElementsByTagName("plurals")
                for (i in 0 until pluralNodes.length) {
                    val node = pluralNodes.item(i) as Element
                    val name = node.getAttribute("name")
                    val formattedAttr = node.getAttribute("formatted")
                    val formatted = formattedAttr.isEmpty() || formattedAttr.toBoolean()
                    val itemNodes = node.getElementsByTagName("item")
                    val items = mutableMapOf<String, String>()
                    for (j in 0 until itemNodes.length) {
                        val itemNode = itemNodes.item(j) as Element
                        items[itemNode.getAttribute("quantity")] = itemNode.textContent
                    }
                    plurals.add(PluralEntry(name, items, formatted, fileName))
                }
            }
            return Pair(strings, plurals)
        }
    }

    @Test
    fun `stray percent detector accurately identifies unescaped percent and passes valid formats`() {
        // Positive cases (valid)
        assertTrue(findStrayPercents("100%% complete").isEmpty())
        assertTrue(findStrayPercents("Percentages add up to %1\$s%%, not 100%%").isEmpty())
        assertTrue(findStrayPercents("Logged %1\$d days in a row").isEmpty())
        assertTrue(findStrayPercents("%1\$s paid %2\$s").isEmpty())
        assertTrue(findStrayPercents("No percent here at all").isEmpty())
        assertTrue(findStrayPercents("Special characters: &, <, >, ', \"").isEmpty())
        assertTrue(findStrayPercents("Literal 50% discount", formatted = false).isEmpty())

        // Negative cases (stray percents that should be caught)
        val straySingle = findStrayPercents("50% discount")
        assertEquals(1, straySingle.size)
        assertEquals("% discount", straySingle[0].context)

        val strayCutoff = findStrayPercents("Missing arg %1$ and text")
        assertEquals(1, strayCutoff.size)
        assertEquals("%1$ and text", strayCutoff[0].context)

        val strayIsolated = findStrayPercents("Just a trailing %")
        assertEquals(1, strayIsolated.size)
        assertEquals("%", strayIsolated[0].context)

        val strayMultiple = findStrayPercents("From 20% to 50% off")
        assertEquals(2, strayMultiple.size)
    }

    @Test
    fun `all strings and plurals in resource files have zero unescaped stray percent characters`() {
        val (strings, plurals) = loadResourceFiles()
        assertTrue("Resource strings must not be empty", strings.isNotEmpty())

        val violations = mutableListOf<String>()

        for (string in strings) {
            val strays = findStrayPercents(string.text, string.formatted)
            if (strays.isNotEmpty()) {
                violations.add("String '${string.name}' in ${string.sourceFile}: strays=${strays.map { it.context }} in \"${string.text}\"")
            }
        }

        for (plural in plurals) {
            for ((quantity, text) in plural.items) {
                val strays = findStrayPercents(text, plural.formatted)
                if (strays.isNotEmpty()) {
                    violations.add("Plural '${plural.name}:$quantity' in ${plural.sourceFile}: strays=${strays.map { it.context }} in \"$text\"")
                }
            }
        }

        assertTrue(
            "Found unescaped stray '%' characters in resources:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `all format strings with multiple substitutions use explicit positional arguments`() {
        val (strings, plurals) = loadResourceFiles()
        val nonPositional = mutableListOf<String>()

        for (string in strings) {
            val specifiers = extractSpecifiers(string.text, string.formatted)
            if (specifiers.size > 1) {
                val missingIndex = specifiers.filter { it.argumentIndex == null }
                if (missingIndex.isNotEmpty()) {
                    nonPositional.add("String '${string.name}': non-positional specifiers ${missingIndex.map { it.raw }} in \"${string.text}\"")
                }
            }
        }

        for (plural in plurals) {
            for ((quantity, text) in plural.items) {
                val specifiers = extractSpecifiers(text, plural.formatted)
                if (specifiers.size > 1) {
                    val missingIndex = specifiers.filter { it.argumentIndex == null }
                    if (missingIndex.isNotEmpty()) {
                        nonPositional.add("Plural '${plural.name}:$quantity': non-positional specifiers ${missingIndex.map { it.raw }} in \"$text\"")
                    }
                }
            }
        }

        assertTrue(
            "Found multiple format specifiers without positional indexing:\n${nonPositional.joinToString("\n")}",
            nonPositional.isEmpty(),
        )
    }

    @Test
    fun `all positional argument indices in strings and plurals are 1-based and contiguous without gaps`() {
        val (strings, plurals) = loadResourceFiles()
        val indexAnomalies = mutableListOf<String>()

        fun verifyIndices(name: String, text: String, formatted: Boolean) {
            val specifiers = extractSpecifiers(text, formatted)
            val indexedSpecifiers = specifiers.filter { it.argumentIndex != null }
            if (indexedSpecifiers.isNotEmpty()) {
                val indices = indexedSpecifiers.map { it.argumentIndex!! }
                val distinctIndices = indices.distinct().sorted()
                val expectedIndices = (1..distinctIndices.maxOrNull()!!).toList()
                if (distinctIndices != expectedIndices) {
                    indexAnomalies.add("Resource '$name': non-contiguous or 0-based indices $distinctIndices (expected $expectedIndices) in \"$text\"")
                }
            }
        }

        for (string in strings) {
            verifyIndices(string.name, string.text, string.formatted)
        }

        for (plural in plurals) {
            for ((quantity, text) in plural.items) {
                verifyIndices("${plural.name}:$quantity", text, plural.formatted)
            }
        }

        assertTrue(
            "Found non-contiguous or invalid argument indices:\n${indexAnomalies.joinToString("\n")}",
            indexAnomalies.isEmpty(),
        )
    }

    @Test
    fun `all plural forms define identical format placeholders across every quantity item`() {
        val (_, plurals) = loadResourceFiles()
        assertTrue("Plural resources must be defined", plurals.isNotEmpty())

        val mismatches = mutableListOf<String>()

        for (plural in plurals) {
            val itemSignatures = plural.items.mapValues { (_, text) ->
                pluralSignature(text, plural.formatted)
            }

            val distinctSignatures = itemSignatures.values.distinct()
            if (distinctSignatures.size > 1) {
                mismatches.add(
                    "Plural '${plural.name}' has mismatched placeholders across quantities:\n" +
                        itemSignatures.entries.joinToString("\n") { "  ${it.key}: ${it.value}" },
                )
            }
        }

        assertTrue(
            "Found plural resources with unequal format placeholders:\n${mismatches.joinToString("\n\n")}",
            mismatches.isEmpty(),
        )
    }

    private fun testFormatExecution(name: String, rawText: String, formatted: Boolean) {
        val specifiers = extractSpecifiers(rawText, formatted)
        if (specifiers.isEmpty()) return

        var implicitIndex = 0
        val indexedSpecifiers = specifiers.map { specifier ->
            (specifier.argumentIndex ?: ++implicitIndex) to specifier
        }
        val maxIndex = indexedSpecifiers.maxOf { it.first }
        val dummyArgs = Array<Any>(maxIndex) { i ->
            val matchingSpecs = indexedSpecifiers.filter { it.first == i + 1 }.map { it.second }
            val dateSpecs = matchingSpecs.filter { it.raw.dropLast(1).lastOrNull() in listOf('t', 'T') }
            val conversions = (matchingSpecs - dateSpecs.toSet()).map { it.conversion.lowercaseChar() }
            // General s/b/h conversions accept any value. Pick from all stricter uses of this argument.
            // Incompatible combinations still fail when the complete template is formatted below.
            when {
                conversions.any { it in "efga" } -> 42.5
                dateSpecs.isNotEmpty() -> 42L
                'c' in conversions -> 65 // Int supports both character and integral conversions.
                conversions.any { it in "dox" } -> 42
                else -> "sample_${i + 1}"
            }
        }

        // Android resources escape unescaped apostrophes as \'
        val template = rawText.replace("\\'", "'").replace("\\\"", "\"")
        try {
            val result = String.format(Locale.ROOT, template, *dummyArgs)
            assertTrue("Formatted result for '$name' must not be empty", result.isNotEmpty())
        } catch (e: Exception) {
            throw AssertionError("Failed to format resource '$name' with template \"$template\": ${e.message}", e)
        }
    }

    @Test
    fun `all format strings evaluate cleanly without runtime formatting exceptions`() {
        val (strings, plurals) = loadResourceFiles()

        for (string in strings) {
            testFormatExecution(string.name, string.text, string.formatted)
        }

        for (plural in plurals) {
            for ((quantity, text) in plural.items) {
                testFormatExecution("${plural.name}:$quantity", text, plural.formatted)
            }
        }
    }

    @Test
    fun `argument reuse is supported and formats cleanly`() {
        val (strings, _) = loadResourceFiles()
        val entry = strings.firstOrNull { it.name == "entry_guests_locked" }
            ?: error("entry_guests_locked not found")

        val specifiers = extractSpecifiers(entry.text, entry.formatted)
        assertEquals(2, specifiers.size)
        assertEquals(listOf(1, 1), specifiers.map { it.argumentIndex })

        val cleanTemplate = entry.text.replace("\\'", "'")
        val formatted = String.format(Locale.ROOT, cleanTemplate, "Vaibhav")
        assertEquals(
            "Vaibhav's guests are in this split, so only Vaibhav can change who's in it or the amounts.",
            formatted,
        )
    }

    private fun pluralSignature(text: String, formatted: Boolean): List<Pair<Int?, String>> =
        extractSpecifiers(text, formatted).map {
            val prefix = it.raw.dropLast(1).lastOrNull()
            val conversion = if (prefix in listOf('t', 'T')) "$prefix${it.conversion}" else it.conversion.toString()
            Pair(it.argumentIndex, conversion)
        }.sortedWith(compareBy<Pair<Int?, String>> { it.first }.thenBy { it.second })

    @Test
    fun `a valid single unindexed integer substitution formats successfully`() {
        assertTrue(findStrayPercents("%d").isEmpty())
        assertEquals(1, extractSpecifiers("%d").size)
        testFormatExecution("single_unindexed_integer", "%d", formatted = true)
    }

    @Test
    fun `plural forms can reorder explicitly indexed arguments without changing their signature`() {
        val one = "%1\$s has %2\$d entry"
        val other = "%2\$d entries belong to %1\$s"
        assertTrue(findStrayPercents(one).isEmpty())
        assertTrue(findStrayPercents(other).isEmpty())
        assertEquals(pluralSignature(one, true), pluralSignature(other, true))
    }

    @Test
    fun `an accepted indexed date time substitution formats successfully`() {
        val template = "%1\$tH"
        assertTrue(findStrayPercents(template).isEmpty())
        assertEquals('H', extractSpecifiers(template).single().conversion)
        testFormatExecution("indexed_date_time", template, formatted = true)
    }

    @Test
    fun `reused arguments support compatible general numeric character and date conversions`() {
        for (template in listOf(
            "%1\$s / %1\$d",
            "%1\$s / %1\$tH",
            "%1\$s / %1\$d / %1\$c",
            "%1\$s / %1\$d / %1\$tH",
        )) {
            testFormatExecution("compatible_reused_argument", template, formatted = true)
        }
        org.junit.Assert.assertThrows(AssertionError::class.java) {
            testFormatExecution("incompatible_reused_argument", "%1\$d / %1\$f", formatted = true)
        }
    }

    @Test
    fun `plural signatures distinguish date time conversions from ordinary conversions`() {
        assertFalse(pluralSignature("%1\$tH", true) == pluralSignature("%1\$H", true))
        assertFalse(pluralSignature("%1\$tH", true) == pluralSignature("%1\$TH", true))
        assertEquals(pluralSignature("%1\$tH %2\$s", true), pluralSignature("%2\$s %1\$tH", true))
    }
}
