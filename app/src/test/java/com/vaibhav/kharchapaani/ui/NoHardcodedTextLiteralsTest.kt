package com.vaibhav.kharchapaani.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

/**
 * W1 Safety Net Test:
 * Enforces that user-facing Compose Text calls do not contain hardcoded string literals
 * starting with letters. Scans app/src/main/java for `Text("<letters>` literals.
 *
 * An allow-list is maintained strictly for the brand name ("Kharcha", "Paani", "Kharcha Paani")
 * so that hardcoded copy cannot creep back into production code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoHardcodedTextLiteralsTest {

    data class Violation(
        val file: String,
        val line: Int,
        val literal: String,
    )

    companion object {
        private val ALLOW_LIST = setOf(
            "Kharcha",
            "Paani",
            "Kharcha Paani",
        )

        // Matches Text( or Text(text = followed by a string literal starting with an ASCII letter
        private val TEXT_LITERAL_PATTERN = Pattern.compile(
            """\bText\s*\(\s*(?:text\s*=\s*)?\s*"([A-Za-z][^"]*)"""",
        )

        fun findViolations(mainJavaDir: File): List<Violation> {
            val violations = mutableListOf<Violation>()
            val ktFiles = mainJavaDir.walkTopDown().filter { it.isFile && it.extension == "kt" }

            for (file in ktFiles) {
                val lines = file.readLines()
                val cleanLines = lines.map { line ->
                    val commentIdx = line.indexOf("//")
                    if (commentIdx != -1) line.substring(0, commentIdx) else line
                }
                var cleanContent = cleanLines.joinToString("\n")
                // Remove block comments
                cleanContent = cleanContent.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")

                val matcher = TEXT_LITERAL_PATTERN.matcher(cleanContent)
                while (matcher.find()) {
                    val literal = matcher.group(1) ?: continue
                    if (literal !in ALLOW_LIST) {
                        // Find line number in original file
                        val matchPos = matcher.start()
                        val lineNum = cleanContent.substring(0, matchPos).count { it == '\n' } + 1
                        violations.add(
                            Violation(
                                file = file.name,
                                line = lineNum,
                                literal = literal,
                            )
                        )
                    }
                }
            }
            return violations
        }

        fun locateMainJavaDir(): File {
            val candidates = listOf(
                File("src/main/java"),
                File("app/src/main/java"),
                File("../app/src/main/java"),
            )
            return candidates.firstOrNull { it.exists() && it.isDirectory }
                ?: error("app/src/main/java not found at any candidate path: ${candidates.map { it.absolutePath }}")
        }
    }

    @Test
    fun `no hardcoded text literals in main java compose code`() {
        val mainJava = locateMainJavaDir()
        val violations = findViolations(mainJava)

        assertTrue(
            "Found ${violations.size} hardcoded Text literals in Compose code (only brand name allowed):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.line}: \"${it.literal}\"" },
            violations.isEmpty(),
        )
    }
}
