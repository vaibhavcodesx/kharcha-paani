package com.vaibhav.kharchapaani.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * A restore in progress keeps a plaintext copy of the whole archive in `files/backup-recovery/`.
 * Android's Auto Backup and device transfer must never carry it off the phone.
 */
class BackupRulesTest {

    private fun xml(name: String): Element {
        val file = listOf("src/main/res/xml/$name", "app/src/main/res/xml/$name").map(::File).first { it.exists() }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun excludesJournal(parent: Element): Boolean {
        val excludes = parent.getElementsByTagName("exclude")
        return (0 until excludes.length).map { excludes.item(it) as Element }.any {
            it.getAttribute("domain") == "file" && it.getAttribute("path") == "backup-recovery/"
        }
    }

    @Test fun `Auto Backup on Android 11 and lower leaves out the restore journal`() {
        assertTrue(excludesJournal(xml("backup_rules.xml")))
    }

    @Test fun `cloud backup and device transfer on Android 12 and later leave out the restore journal`() {
        val rules = xml("data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = rules.getElementsByTagName(section).item(0) as Element
            assertTrue("$section must exclude backup-recovery/", excludesJournal(element))
        }
    }
}
