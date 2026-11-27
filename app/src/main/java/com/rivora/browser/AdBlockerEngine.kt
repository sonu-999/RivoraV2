package com.rivora.browser

import android.content.Context
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

    private val AD_SUBDOMAIN_PREFIXES = setOf(
        "ad", "ads", "adserver", "adservice", "adservices", "adv", "advert", "adverts",
        "pop", "pops", "popup", "popups", "popunder", "onclick", "clk",
        "tr", "trk", "track", "tracker", "tracking",
        "metrics", "telemetry", "pixel", "px", "beacon",
        "redir", "redirect", "banners", "banner", "vast", "ima", "promo"
    )

    private val DOWNLOAD_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "iso", "img",
        "apk", "xapk", "apks", "exe", "msi", "dmg", "pkg", "deb", "rpm",
        "mp4", "mkv", "webm", "avi", "mov", "mp3", "flac", "wav", "aac", "ogg", "m4a",
        "pdf", "epub", "mobi", "doc", "docx", "torrent"
    )

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

    fun extractRootDomain(rawHost: String): String {
        val host = rawHost.lowercase().removePrefix("www.")
        val parts = host.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) return host
        val secondLast = parts[parts.size - 2]
        val last = parts.last()
        val isTwoLevelTld = last.length == 2 && secondLast in setOf("co", "com", "org", "net", "gov", "edu", "ac")
        val takeCount = if (isTwoLevelTld && parts.size >= 3) 3 else 2
        return parts.takeLast(takeCount).joinToString(".")
    }

    fun isThirdParty(requestHost: String, pageHost: String?): Boolean {
        val normalizedPageHost = pageHost?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
            ?: return false
        val normalizedReqHost = requestHost.lowercase().removePrefix("www.")
        if (normalizedReqHost == normalizedPageHost) return false
        if (normalizedReqHost.endsWith(".$normalizedPageHost")) return false
        if (normalizedPageHost.endsWith(".$normalizedReqHost")) return false
        return extractRootDomain(normalizedReqHost) != extractRootDomain(normalizedPageHost)
    }

    fun hasAdSubdomainPrefix(rawHost: String): Boolean {
        val host = rawHost.lowercase().removePrefix("www.")
        val firstLabel = host.substringBefore('.')
        if (firstLabel in AD_SUBDOMAIN_PREFIXES) return true
        // Also catch numbered ad subdomains like ad1., ads2., pop3., track01.
        val strippedDigits = firstLabel.trimEnd { it.isDigit() }
        return strippedDigits.isNotEmpty() && strippedDigits in AD_SUBDOMAIN_PREFIXES
    }

    fun looksLikeDirectDownload(url: String): Boolean {
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase()
        val ext = cleanPath.substringAfterLast('.', "")
        return ext in DOWNLOAD_EXTENSIONS
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

        // Even on main-frame navigations, block known pure-ad/malvertising domains
        if (isDomainBlocked(host, blockedDomains)) {
            return host
        }

        val thirdParty = pageHost == null || isThirdParty(host, pageHost)

        if (!isMainFrame && thirdParty) {
            if (hasAdSubdomainPrefix(host)) {
                return host
            }
            val lowerUrl = requestUrl.lowercase()
            if (pathPatterns.any { lowerUrl.contains(it) }) {
                return host
            }
        }

        return null
    }

    fun findBlockedHostForNavigation(
        targetUrl: String,
        pageHost: String?,
        isRedirect: Boolean,
        hasGesture: Boolean,
        isEnabled: Boolean,
        blockPopups: Boolean,
        allowlistedSites: Set<String>,
        blockedDomains: Set<String>,
        pathPatterns: List<String>
    ): String? {
        if (!isEnabled) return null
        if (isAllowlisted(pageHost, allowlistedSites)) return null

        val host = extractHost(targetUrl) ?: return null

        if (isDomainBlocked(host, blockedDomains)) {
            return host
        }

        if (!blockPopups) return null

        val thirdParty = isThirdParty(host, pageHost)
        if (thirdParty) {
            if (hasAdSubdomainPrefix(host)) {
                return host
            }
            val lowerUrl = targetUrl.lowercase()
            if (pathPatterns.any { lowerUrl.contains(it) }) {
                return host
            }
            // Block unprompted third-party JavaScript auto-redirects unless downloading a file
            if (!hasGesture && !isRedirect && !looksLikeDirectDownload(targetUrl)) {
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
        blockedDomains: Set<String>,
        pathPatterns: List<String> = emptyList()
    ): String? {
        if (!isEnabled || !blockPopups) return null
        if (isAllowlisted(pageHost, allowlistedSites)) return null

        val host = extractHost(targetUrl) ?: return null
        if (isDomainBlocked(host, blockedDomains)) {
            return host
        }
        val thirdParty = isThirdParty(host, pageHost)
        if (thirdParty) {
            if (hasAdSubdomainPrefix(host)) return host
            val lowerUrl = targetUrl.lowercase()
            if (pathPatterns.any { lowerUrl.contains(it) }) return host
            // Any third-party window.open popup that isn't a direct file download is blocked
            if (!looksLikeDirectDownload(targetUrl)) return host
        }
        return null
    }
}

/**
 * Free Open-Source Ad, Popup, Redirect & Tracker Blocker Engine for Rivora Browser.
 *
 * Compatible with EasyList, AdAway, StevenBlack Unified Hosts, and Peter Lowe's Ad Server list.
 * Supports:
 * - O(1) domain & parent-subdomain matching across 10,000+ open-source rules
 * - Rotating third-party ad/popup subdomain heuristics (ads.*, pop.*, clk.*, track.*)
 * - Automatic JS popunder & clickjacking overlay neutralization
 * - Unprompted third-party auto-redirect blocking
 * - EasyList cosmetic CSS element hiding + MutationObserver
 * - Site-level allowlisting & background open-source list auto-sync
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
        private const val KEY_LAST_SYNC_MS = "adblock_last_sync_ms"
        private const val BUNDLED_ASSET_PATH = "adblock/easylist_hosts.txt"
        private const val COMMUNITY_HOSTS_FILE = "adblock_community_hosts.txt"
        private const val SYNC_INTERVAL_MS = 24L * 60L * 60L * 1000L

        // Free & open-source filter lists (Peter Lowe + AdAway Mobile Hosts)
        private val OPEN_SOURCE_LIST_URLS = listOf(
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=plain&showintro=0&mimetype=plaintext",
            "https://adaway.org/hosts.txt"
        )

        const val ANTI_POPUP_AND_REDIRECT_JS = """
            (function() {
                try {
                    if (!window.__rivoraPopupGuardInstalled) {
                        window.__rivoraPopupGuardInstalled = true;

                        // Neutralize popup & popunder window.open calls
                        var fakeWin = {
                            closed: false,
                            focus: function() {},
                            blur: function() {},
                            close: function() {},
                            document: { write: function() {}, open: function() {}, close: function() {} },
                            location: { href: 'about:blank', replace: function() {}, assign: function() {} }
                        };
                        try {
                            Object.defineProperty(window, 'open', {
                                configurable: true,
                                writable: true,
                                value: function(url) {
                                    try {
                                        if (typeof url === 'string' && url.length > 0) {
                                            var target = new URL(url, window.location.href);
                                            if (target.hostname === window.location.hostname) {
                                                window.location.href = target.href;
                                            }
                                        }
                                    } catch (e) {}
                                    return fakeWin;
                                }
                            });
                        } catch (e) {
                            window.open = function() { return fakeWin; };
                        }

                        // Silence notification permission spam
                        if (window.Notification) {
                            try {
                                Notification.requestPermission = function() {
                                    return Promise.resolve('denied');
                                };
                            } catch (e) {}
                        }

                        // Block invisible full-screen clickjacking overlays & third-party _blank hijackers
                        document.addEventListener('click', function(ev) {
                            try {
                                var el = ev.target;
                                if (!el) return;
                                var cur = el;
                                while (cur && cur !== document.body && cur !== document.documentElement) {
                                    if (cur.tagName === 'DIV' || cur.tagName === 'SECTION' || cur.tagName === 'SPAN') {
                                        var st = window.getComputedStyle(cur);
                                        var rect = cur.getBoundingClientRect();
                                        var coversScreen = rect.width >= window.innerWidth * 0.7 && rect.height >= window.innerHeight * 0.6;
                                        var isOverlay = (st.position === 'fixed' || st.position === 'absolute') && parseInt(st.zIndex || '0', 10) >= 999;
                                        var looksInvisible = parseFloat(st.opacity || '1') <= 0.08 || st.backgroundColor === 'rgba(0, 0, 0, 0)' || st.backgroundColor === 'transparent';
                                        if (coversScreen && isOverlay && looksInvisible && cur.innerText.trim().length < 8) {
                                            ev.preventDefault();
                                            ev.stopImmediatePropagation();
                                            cur.style.setProperty('display', 'none', 'important');
                                            cur.remove();
                                            return false;
                                        }
                                    }
                                    if (cur.tagName === 'A' && cur.getAttribute('target') === '_blank') {
                                        var href = cur.href || '';
                                        if (href.indexOf('http') === 0) {
                                            var dest = new URL(href);
                                            var sameHost = dest.hostname === window.location.hostname ||
                                                dest.hostname.endsWith('.' + window.location.hostname);
                                            if (!sameHost) {
                                                // Force navigation through WebView client instead of popup window
                                                cur.removeAttribute('target');
                                            }
                                        }
                                    }
                                    cur = cur.parentElement;
                                }
                            } catch (e) {}
                        }, true);
                    }
                } catch (e) {}
            })();
        """

        const val COSMETIC_FILTER_JS = """
            (function() {
                try {
                    if (!document.getElementById('__rivora_adblock_css')) {
                        var style = document.createElement('style');
                        style.id = '__rivora_adblock_css';
                        style.textContent = [
                            'ins.adsbygoogle',
                            '.adsbygoogle',
                            '[id^="div-gpt-ad"]',
                            '[id*="google_ads"]',
                            '[id^="google_ads_iframe"]',
                            'iframe[src*="doubleclick"]',
                            'iframe[src*="googlesyndication"]',
                            'iframe[src*="adnxs"]',
                            'iframe[src*="exoclick"]',
                            'iframe[src*="adsterra"]',
                            'iframe[src*="monetag"]',
                            'iframe[src*="juicyads"]',
                            'iframe[src*="trafficjunky"]',
                            'iframe[src*="propeller"]',
                            'iframe[src*="popads"]',
                            'iframe[src*="realsrv"]',
                            'iframe[src*="mgid"]',
                            'iframe[id*="ad_"]',
                            'iframe[class*="ad-"]',
                            '[class*="taboola"]',
                            '[id*="taboola"]',
                            '[class*="outbrain"]',
                            '[id*="outbrain"]',
                            '[class*="mgid"]',
                            '[id*="mgid"]',
                            '[class*="revcontent"]',
                            '[id*="revcontent"]',
                            '.ad-banner',
                            '.ad-box',
                            '.ad-container',
                            '.ad-wrapper',
                            '.ad-slot',
                            '.ad-unit',
                            '.ad-zone',
                            '.ad-placeholder',
                            '.ad_banner',
                            '.adsbox',
                            '.ad-overlay',
                            '.popup-ad',
                            '.popunder',
                            '.sponsored-ad',
                            '.sponsored-content',
                            '[data-ad]',
                            '[data-ad-slot]',
                            '[data-ad-client]',
                            '[data-ad-unit]',
                            '[data-google-query-id]',
                            '[aria-label="Advertisement"]',
                            '[aria-label="Sponsored"]'
                        ].join(', ') + ' { display: none !important; width: 0 !important; height: 0 !important; min-height: 0 !important; max-height: 0 !important; opacity: 0 !important; pointer-events: none !important; visibility: hidden !important; }';
                        (document.head || document.documentElement).appendChild(style);
                    }

                    function purgeDynamicAds() {
                        try {
                            var nodes = document.querySelectorAll('div[style*="z-index"], section[style*="z-index"]');
                            for (var i = 0; i < nodes.length; i++) {
                                var n = nodes[i];
                                var st = window.getComputedStyle(n);
                                var z = parseInt(st.zIndex || '0', 10);
                                var r = n.getBoundingClientRect();
                                if ((st.position === 'fixed' || st.position === 'absolute') &&
                                    z >= 9999 &&
                                    r.width >= window.innerWidth * 0.8 &&
                                    r.height >= window.innerHeight * 0.7 &&
                                    parseFloat(st.opacity || '1') <= 0.1 &&
                                    n.innerText.trim().length < 10) {
                                    n.style.setProperty('display', 'none', 'important');
                                    n.style.setProperty('pointer-events', 'none', 'important');
                                }
                            }
                        } catch (e) {}
                    }

                    purgeDynamicAds();
                    if (!window.__rivoraObserverAttached && document.documentElement) {
                        window.__rivoraObserverAttached = true;
                        var timer = null;
                        var obs = new MutationObserver(function() {
                            if (timer) return;
                            timer = setTimeout(function() {
                                timer = null;
                                purgeDynamicAds();
                            }, 350);
                        });
                        obs.observe(document.documentElement, { childList: true, subtree: true });
                    }
                } catch (e) {}
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
            val communityFile = File(context.filesDir, COMMUNITY_HOSTS_FILE)
            val lastSync = prefs.getLong(KEY_LAST_SYNC_MS, 0L)
            val needsAutoSync = !communityFile.exists() || (System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS)
            if (needsAutoSync) {
                updateOpenSourceLists()
            }
        }
    }

    private fun loadFilterLists() {
        val domains = HashSet<String>(16384)
        val patterns = ArrayList<String>(128)

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

    fun shouldBlockNavigation(
        targetUrl: String,
        pageHost: String?,
        isRedirect: Boolean,
        hasGesture: Boolean
    ): Boolean {
        val current = _stats.value
        val blockedHost = AdBlockRuleMatcher.findBlockedHostForNavigation(
            targetUrl = targetUrl,
            pageHost = pageHost,
            isRedirect = isRedirect,
            hasGesture = hasGesture,
            isEnabled = current.isEnabled,
            blockPopups = current.blockPopups,
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
            blockedDomains = blockedDomains,
            pathPatterns = pathPatterns
        ) ?: return false

        recordBlocked(blockedHost)
        return true
    }

    fun recordBlockedPopup(label: String = "popup-blocked") {
        recordBlocked(label)
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
        return AdBlockRuleMatcher.isAllowlisted(rawHost, _stats.value.allowlistedSites)
    }

    fun updateOpenSourceLists() {
        if (_stats.value.isUpdatingLists) return
        _stats.update { it.copy(isUpdatingLists = true, updateStatusMessage = "Syncing Peter Lowe & AdAway lists...") }

        scope.launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()

                val collectedDomains = HashSet<String>(16384)
                val dummyPatterns = ArrayList<String>(16)

                OPEN_SOURCE_LIST_URLS.forEach { listUrl ->
                    try {
                        val req = Request.Builder()
                            .url(listUrl)
                            .header("User-Agent", "Rivora/1.1.7 (Android)")
                            .build()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                AdBlockRuleMatcher.parseLinesInto(body.lineSequence(), collectedDomains, dummyPatterns)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "List fetch failed for $listUrl: ${e.message}")
                    }
                }

                if (collectedDomains.size < 100) {
                    throw Exception("No community rules downloaded")
                }

                val outFile = File(context.filesDir, COMMUNITY_HOSTS_FILE)
                outFile.writeText(collectedDomains.joinToString("\n"))

                loadFilterLists()
                val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date())
                prefs.edit()
                    .putString(KEY_LAST_UPDATED, "Synced $stamp")
                    .putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis())
                    .apply()
                _stats.update {
                    it.copy(
                        isUpdatingLists = false,
                        lastUpdatedLabel = "Synced $stamp",
                        updateStatusMessage = "Synced ${collectedDomains.size} open-source ad & popup rules"
                    )
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
