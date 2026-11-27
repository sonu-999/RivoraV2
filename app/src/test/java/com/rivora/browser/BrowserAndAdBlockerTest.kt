package com.rivora.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BrowserAndAdBlockerTest {

    private val domains = HashSet<String>()
    private val patterns = ArrayList<String>()

    @Before
    fun setUp() {
        val candidates = listOf(
            File("src/main/assets/adblock/easylist_hosts.txt"),
            File("app/src/main/assets/adblock/easylist_hosts.txt")
        )
        val assetFile = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate bundled easylist_hosts.txt asset")

        assetFile.bufferedReader().useLines { lines ->
            AdBlockRuleMatcher.parseLinesInto(lines, domains, patterns)
        }
    }

    @Test
    fun adBlocker_loadsBundledRulesAndBlocksKnownAdAndTrackerSubdomains() {
        assertTrue("Expected bundled rules to be loaded", domains.size + patterns.size > 100)

        assertNotNull(
            AdBlockRuleMatcher.findBlockedHostForRequest(
                requestUrl = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
                pageHost = "example.com",
                isMainFrame = false,
                isEnabled = true,
                allowlistedSites = emptySet(),
                blockedDomains = domains,
                pathPatterns = patterns
            )
        )
        assertNotNull(
            AdBlockRuleMatcher.findBlockedHostForRequest(
                requestUrl = "https://sub.ad.doubleclick.net/pixel.gif",
                pageHost = "example.com",
                isMainFrame = false,
                isEnabled = true,
                allowlistedSites = emptySet(),
                blockedDomains = domains,
                pathPatterns = patterns
            )
        )
        assertNull(
            AdBlockRuleMatcher.findBlockedHostForRequest(
                requestUrl = "https://github.com/JishnuPG-tech/Rivora/releases/download/v1.1.6/Rivora.apk",
                pageHost = "github.com",
                isMainFrame = false,
                isEnabled = true,
                allowlistedSites = emptySet(),
                blockedDomains = domains,
                pathPatterns = patterns
            )
        )
    }

    @Test
    fun adBlocker_respectsSiteAllowlistAndPopupRules() {
        val allowlisted = setOf("trustedsite.org")

        assertNull(
            AdBlockRuleMatcher.findBlockedHostForRequest(
                requestUrl = "https://cdn.taboola.com/libtrc/impl.js",
                pageHost = "www.trustedsite.org",
                isMainFrame = false,
                isEnabled = true,
                allowlistedSites = allowlisted,
                blockedDomains = domains,
                pathPatterns = patterns
            )
        )

        assertNotNull(
            AdBlockRuleMatcher.findBlockedHostForPopup(
                targetUrl = "https://click.doubleclick.net/pcs/click?ad=1",
                pageHost = "example.org",
                isEnabled = true,
                blockPopups = true,
                allowlistedSites = emptySet(),
                blockedDomains = domains
            )
        )
    }

    @Test
    fun browserUrlResolver_resolvesDomainsUrlsMagnetsAndSearchQueries() {
        assertEquals("https://github.com", BrowserUrlResolver.resolveInputToUrl("github.com", SearchEngine.DUCKDUCKGO))
        assertEquals("https://example.org/file.zip", BrowserUrlResolver.resolveInputToUrl("https://example.org/file.zip", SearchEngine.DUCKDUCKGO))
        assertEquals(
            "magnet:?xt=urn:btih:abc123",
            BrowserUrlResolver.resolveInputToUrl("magnet:?xt=urn:btih:abc123", SearchEngine.DUCKDUCKGO)
        )
        assertTrue(
            BrowserUrlResolver.resolveInputToUrl("ubuntu iso download", SearchEngine.DUCKDUCKGO)
                .startsWith("https://duckduckgo.com/?q=")
        )
        assertTrue(
            BrowserUrlResolver.resolveInputToUrl("rivora download", SearchEngine.GOOGLE)
                .startsWith("https://www.google.com/search?q=")
        )
    }
}
