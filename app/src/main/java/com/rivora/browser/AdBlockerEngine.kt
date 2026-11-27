package com.rivora.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AdBlockStats(
    val isEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val cosmeticFiltering: Boolean = true,
    val pageBlockedCount: Int = 0,
    val totalBlockedCount: Long = 0L,
    val ruleCount: Int = 0,
    val recentBlockedDomains: List<String> = emptyList(),
    val allowlistedSites: Set<String> = emptySet(),
    val isUpdatingLists: Boolean = false,
    val lastUpdatedLabel: String? = null,
    val updateStatusMessage: String? = null
)

object AdBlockRuleMatcher {
    fun extractScheme(url: String): String? {
        val idx = url.indexOf("://")
        if (idx <= 0) return null
        return url.substring(0, idx).lowercase()
    }

    fun extractHost(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        val afterScheme = url.substring(schemeEnd + 3)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter("[").substringBefore("]")
        } else {
            hostPort.substringBefore(':')
        }
        return host.lowercase().removePrefix("www.").takeIf { it.isNotBlank() }
    }

    fun parseLinesInto(
        lines: Sequence<String>,
        domains: MutableSet<String>,
        patterns: MutableList<String>
    ) {
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return@forEach

            if (line.startsWith("||")) {
                val pattern = line.removePrefix("||").lowercase()
                if (pattern.isNotEmpty() && !patterns.contains(pattern)) {
                    patterns.add(pattern)
                }
                return@forEach
            }

            // Support hosts file syntax: "0.0.0.0 ad.example.com" or "127.0.0.1 ad.example.com"
            val candidate = when {
                line.startsWith("0.0.0.0 ") -> line.substringAfter("0.0.0.0 ").substringBefore("#").trim()
                line.startsWith("127.0.0.1 ") -> line.substringAfter("127.0.0.1 ").substringBefore("#").trim()
                else -> line.substringBefore("#").trim()
            }.lowercase().removePrefix("www.")

            if (candidate.isNotEmpty() &&
                candidate.contains('.') &&
                !candidate.contains(' ') &&
                candidate != "localhost" &&
                candidate != "localhost.localdomain"
            ) {
                domains.add(candidate)
            }
        }
    }

    fun isDomainBlocked(rawHost: String, domains: Set<String>): Boolean {
        if (domains.isEmpty()) return false
        var candidate = rawHost.lowercase().removePrefix("www.")
        while (candidate.contains('.')) {
            if (domains.contains(candidate)) return true
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0 || dotIdx + 1 >= candidate.length) break
            candidate = candidate.substring(dotIdx + 1)
        }
        return false
    }

    fun isAllowlisted(pageHost: String?, allowlistedSites: Set<String>): Boolean {
        val normalized = pageHost?.lowercase()?.removePrefix("www.")?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        if (allowlistedSites.contains(normalized)) return true
        return allowlistedSites.any { normalized.endsWith(".$it") }
    }

    fun findBlockedHostForRequest(
        requestUrl: String,
        pageHost: String?,
        isMainFrame: Boolean,
        isEnabled: Boolean,
        allowlistedSites: Set<String>,
        blockedDomains: Set<String>,
        pathPatterns: List<String>
    ): String? {
        if (!isEnabled) return null
        if (isAllowlisted(pageHost, allowlistedSites)) return null

        val scheme = extractScheme(requestUrl) ?: return null
        if (scheme != "http" && scheme != "https") return null

        val host = extractHost(requestUrl) ?: return null
        if (isMainFrame) return null

        if (isDomainBlocked(host, blockedDomains)) {
            return host
        }

        val normalizedPageHost = pageHost?.lowercase()?.removePrefix("www.")
        val isThirdParty = normalizedPageHost == null ||
            (host != normalizedPageHost && !host.endsWith(".$normalizedPageHost"))

        if (isThirdParty) {
            val lowerUrl = requestUrl.lowercase()
            val matchedPattern = pathPatterns.firstOrNull { lowerUrl.contains(it) }
            if (matchedPattern != null) {
                return host
            }
        }

        return null
    }

    fun findBlockedHostForPopup(
        targetUrl: String,
        pageHost: String?,
        isEnabled: Boolean,
        blockPopups: Boolean,
        allowlistedSites: Set<String>,
        blockedDomains: Set<String>
    ): String? {
        if (!isEnabled || !blockPopups) return null
        if (isAllowlisted(pageHost, allowlistedSites)) return null

        val host = extractHost(targetUrl) ?: return null
        return if (isDomainBlocked(host, blockedDomains)) host else null
    }
}

/**
 * Free Open-Source Ad & Tracker Blocker Engine for Rivora Browser.
 *
 * Compatible with EasyList, StevenBlack Unified Hosts, and Peter Lowe's Ad Server list.
 * Supports:
 * - O(1) domain & parent-subdomain matching
 * - Third-party URL path fragment rules (EasyList style)
 * - Popup / redirect domain blocking
 * - Cosmetic CSS element hiding
 * - Site-level allowlisting
 * - Live open-source list updates from pgl.yoyo.org
 */
class AdBlockerEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AdBlockerEngine"
        private const val PREFS_NAME = "rivora_adblock_prefs"
        private const val KEY_ENABLED = "adblock_enabled"
        private const val KEY_POPUPS = "adblock_popups"
        private const val KEY_COSMETIC = "adblock_cosmetic"
        private const val KEY_TOTAL_BLOCKED = "adblock_total_blocked"
        private const val KEY_ALLOWLIST = "adblock_allowlist"
        private const val KEY_LAST_UPDATED = "adblock_last_updated"
        private const val BUNDLED_ASSET_PATH = "adblock/easylist_hosts.txt"
        private const val COMMUNITY_HOSTS_FILE = "adblock_community_hosts.txt"

        // Free & open-source Peter Lowe's Ad & Tracker Server List (CC-BY-SA / open source)
        private const val OPEN_SOURCE_LIST_URL =
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=plain&showintro=0&mimetype=plaintext"

        const val COSMETIC_FILTER_JS = """
            (function() {
                if (window.__rivoraCosmeticApplied) return;
                window.__rivoraCosmeticApplied = true;
                var style = document.createElement('style');
                style.id = '__rivora_adblock_css';
                style.textContent = [
                    'ins.adsbygoogle',
                    '.adsbygoogle',
                    '[id^="div-gpt-ad"]',
                    '[id^="google_ads_iframe"]',
                    'iframe[src*="doubleclick.net"]',
                    'iframe[src*="googlesyndication.com"]',
                    'iframe[src*="adnxs.com"]',
                    '[class*="taboola"]',
                    '[id*="taboola"]',
                    '[class*="outbrain"]',
                    '[id*="outbrain"]',
                    '.ad-banner',
                    '.ad-container',
                    '.ad-wrapper',
                    '.sponsored-ad',
                    '[data-ad-slot]',
                    '[data-google-query-id]'
                ].join(', ') + ' { display: none !important; height: 0 !important; min-height: 0 !important; visibility: hidden !important; }';
                (document.head || document.documentElement).appendChild(style);
            })();
        """

        @Volatile
        private var INSTANCE: AdBlockerEngine? = null

        fun getInstance(context: Context): AdBlockerEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdBlockerEngine(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun emptyAdResponse(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var blockedDomains: Set<String> = emptySet()

    @Volatile
    private var pathPatterns: List<String> = emptyList()

    private val pageBlockedSet = ConcurrentHashMap.newKeySet<String>()

    private val _stats = MutableStateFlow(
        AdBlockStats(
            isEnabled = prefs.getBoolean(KEY_ENABLED, true),
            blockPopups = prefs.getBoolean(KEY_POPUPS, true),
            cosmeticFiltering = prefs.getBoolean(KEY_COSMETIC, true),
            totalBlockedCount = prefs.getLong(KEY_TOTAL_BLOCKED, 0L),
            allowlistedSites = prefs.getStringSet(KEY_ALLOWLIST, emptySet())?.toSet() ?: emptySet(),
            lastUpdatedLabel = prefs.getString(KEY_LAST_UPDATED, "Bundled EasyList/Hosts")
        )
    )
    val stats: StateFlow<AdBlockStats> = _stats.asStateFlow()

    init {
        scope.launch {
            loadFilterLists()
        }
    }

    private fun loadFilterLists() {
        val domains = HashSet<String>(8192)
        val patterns = ArrayList<String>(64)

        try {
            context.assets.open(BUNDLED_ASSET_PATH).bufferedReader().useLines { lines ->
                AdBlockRuleMatcher.parseLinesInto(lines, domains, patterns)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read bundled filter list: ${e.message}")
        }

        val communityFile = File(context.filesDir, COMMUNITY_HOSTS_FILE)
        if (communityFile.exists()) {
            try {
                communityFile.bufferedReader().useLines { lines ->
                    AdBlockRuleMatcher.parseLinesInto(lines, domains, patterns)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read community filter list: ${e.message}")
            }
        }

        blockedDomains = domains
        pathPatterns = patterns
        _stats.update { it.copy(ruleCount = domains.size + patterns.size) }
        Log.d(TAG, "Loaded ${domains.size} domains and ${patterns.size} path rules")
    }

    fun shouldBlockRequest(requestUrl: String, pageHost: String?, isMainFrame: Boolean): Boolean {
        val current = _stats.value
        val blockedHost = AdBlockRuleMatcher.findBlockedHostForRequest(
            requestUrl = requestUrl,
            pageHost = pageHost,
            isMainFrame = isMainFrame,
            isEnabled = current.isEnabled,
            allowlistedSites = current.allowlistedSites,
            blockedDomains = blockedDomains,
            pathPatterns = pathPatterns
        ) ?: return false

        recordBlocked(blockedHost)
        return true
    }

    fun shouldBlockPopupOrRedirect(targetUrl: String, pageHost: String?): Boolean {
        val current = _stats.value
        val blockedHost = AdBlockRuleMatcher.findBlockedHostForPopup(
            targetUrl = targetUrl,
            pageHost = pageHost,
            isEnabled = current.isEnabled,
            blockPopups = current.blockPopups,
            allowlistedSites = current.allowlistedSites,
            blockedDomains = blockedDomains
        ) ?: return false

        recordBlocked(blockedHost)
        return true
    }

    fun isDomainBlocked(rawHost: String): Boolean {
        return AdBlockRuleMatcher.isDomainBlocked(rawHost, blockedDomains)
    }

    private fun recordBlocked(host: String) {
        pageBlockedSet.add(host)
        val newTotal = prefs.getLong(KEY_TOTAL_BLOCKED, 0L) + 1L
        prefs.edit().putLong(KEY_TOTAL_BLOCKED, newTotal).apply()

        _stats.update { current ->
            val updatedRecent = (listOf(host) + current.recentBlockedDomains.filterNot { it == host }).take(25)
            current.copy(
                pageBlockedCount = current.pageBlockedCount + 1,
                totalBlockedCount = newTotal,
                recentBlockedDomains = updatedRecent
            )
        }
    }

    fun resetPageStats() {
        pageBlockedSet.clear()
        _stats.update {
            it.copy(
                pageBlockedCount = 0,
                recentBlockedDomains = emptyList()
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _stats.update { it.copy(isEnabled = enabled) }
    }

    fun setBlockPopups(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POPUPS, enabled).apply()
        _stats.update { it.copy(blockPopups = enabled) }
    }

    fun setCosmeticFiltering(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COSMETIC, enabled).apply()
        _stats.update { it.copy(cosmeticFiltering = enabled) }
    }

    fun toggleSiteAllowlist(rawHost: String?) {
        val host = rawHost?.lowercase()?.removePrefix("www.")?.trim().orEmpty()
        if (host.isEmpty()) return
        val currentSet = _stats.value.allowlistedSites.toMutableSet()
        if (currentSet.contains(host)) {
            currentSet.remove(host)
        } else {
            currentSet.add(host)
        }
        prefs.edit().putStringSet(KEY_ALLOWLIST, currentSet).apply()
        _stats.update { it.copy(allowlistedSites = currentSet) }
    }

    fun isSiteAllowlisted(rawHost: String?): Boolean {
        val host = rawHost?.lowercase()?.removePrefix("www.")?.trim().orEmpty()
        if (host.isEmpty()) return false
        return _stats.value.allowlistedSites.contains(host)
    }

    fun updateOpenSourceLists() {
        if (_stats.value.isUpdatingLists) return
        _stats.update { it.copy(isUpdatingLists = true, updateStatusMessage = "Fetching Peter Lowe's open-source list...") }

        scope.launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val req = Request.Builder()
                    .url(OPEN_SOURCE_LIST_URL)
                    .header("User-Agent", "Rivora/1.1.6 (Android)")
                    .build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        throw Exception("HTTP ${res.code}")
                    }
                    val body = res.body?.string().orEmpty()
                    val validLines = body.lineSequence()
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('.') && !it.contains(' ') }
                        .toList()

                    if (validLines.size < 100) {
                        throw Exception("Invalid filter list response")
                    }

                    val outFile = File(context.filesDir, COMMUNITY_HOSTS_FILE)
                    outFile.writeText(validLines.joinToString("\n"))

                    loadFilterLists()
                    val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date())
                    prefs.edit().putString(KEY_LAST_UPDATED, "Updated $stamp").apply()
                    _stats.update {
                        it.copy(
                            isUpdatingLists = false,
                            lastUpdatedLabel = "Updated $stamp",
                            updateStatusMessage = "Synced ${validLines.size} open-source filter rules"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating open-source lists: ${e.message}")
                _stats.update {
                    it.copy(
                        isUpdatingLists = false,
                        updateStatusMessage = "Using bundled list (${e.message ?: "offline"})"
                    )
                }
            }
        }
    }

    fun clearUpdateMessage() {
        _stats.update { it.copy(updateStatusMessage = null) }
    }
}
