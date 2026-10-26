package com.vaibhav.kharchapaani.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pages the app links to exist in site/ (what gets published), and the policy keeps saying the
 * things Play and the DPDP Act require. Unit tests run from the app module, so the repo is "..".
 */
class LegalPagesTest {

    private val repo = File("..")

    @Test
    fun `every page the app opens is in the site`() {
        for (url in listOf(LegalLinks.PRIVACY_POLICY, LegalLinks.TERMS, LegalLinks.DELETE_ACCOUNT)) {
            val name = url.removePrefix(LegalLinks.BASE)
            assertTrue("site/$name is missing; run scripts/legal_site.py", File(repo, "site/$name").isFile)
        }
    }

    @Test
    fun `the policy states the region, the free-tier Gemini use, 18+, deletion and the grievance contact`() {
        val policy = File(repo, "docs/legal/privacy-policy.md").readText()
        for (must in listOf("Tokyo, Japan", "Google may use what's sent to improve", "18 or older", "Former member", Support.EMAIL, "90 days")) {
            assertTrue("the privacy policy no longer says \"$must\"", policy.contains(must))
        }
    }
}
