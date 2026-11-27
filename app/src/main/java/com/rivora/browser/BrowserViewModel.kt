package com.rivora.browser

import android.app.Application
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rivora.backend.UrlProber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BrowserUrlResolver {
    fun resolveInputToUrl(rawInput: String, searchEngine: SearchEngine): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("magnet:", ignoreCase = true)) return trimmed
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val looksLikeDomain = !trimmed.contains(' ') &&
            trimmed.contains('.') &&
            !trimmed.startsWith(".") &&
            !trimmed.endsWith(".")
        if (looksLikeDomain) {
            return "https://$trimmed"
        }
        val encoded = try {
            URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            trimmed.replace(" ", "+")
        }
        return searchEngine.searchUrlTemplate.replace("%s", encoded)
    }
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "rivora_browser_prefs"
        private const val KEY_BOOKMARKS = "browser_bookmarks"
        private const val KEY_AUTO_FETCH = "browser_auto_fetch"
        private const val KEY_SEARCH_ENGINE = "browser_search_engine"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val adBlocker: AdBlockerEngine = AdBlockerEngine.getInstance(application)

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            autoFetchDownloads = prefs.getBoolean(KEY_AUTO_FETCH, false),
            searchEngine = SearchEngine.entries.firstOrNull {
                it.name == prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.DUCKDUCKGO.name)
            } ?: SearchEngine.DUCKDUCKGO,
            bookmarks = loadBookmarks()
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val adBlockStats: StateFlow<AdBlockStats> = adBlocker.stats

    private var tabCounter = 1

    private fun loadBookmarks(): List<BookmarkItem> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return BrowserUiState.DEFAULT_BOOKMARKS
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<BookmarkItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BookmarkItem(
                        id = obj.optString("id", "bm-$i"),
                        title = obj.optString("title", "Bookmark"),
                        url = obj.optString("url", ""),
                        tag = obj.optString("tag", "WEB")
                    )
                )
            }
            if (list.isEmpty()) BrowserUiState.DEFAULT_BOOKMARKS else list
        } catch (_: Exception) {
            BrowserUiState.DEFAULT_BOOKMARKS
        }
    }

    private fun saveBookmarks(list: List<BookmarkItem>) {
        val arr = JSONArray()
        list.forEach { bm ->
            arr.put(
                JSONObject().apply {
                    put("id", bm.id)
                    put("title", bm.title)
                    put("url", bm.url)
                    put("tag", bm.tag)
                }
            )
        }
        prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }

    fun resolveInputToUrl(rawInput: String): String {
        return BrowserUrlResolver.resolveInputToUrl(rawInput, _uiState.value.searchEngine)
    }

    fun openUrlInActiveTab(rawInput: String) {
        val resolved = resolveInputToUrl(rawInput)
        if (resolved.isEmpty()) return
        if (UrlProber.isTorrentUrl(resolved)) {
            onMagnetOrTorrentDetected(resolved)
            return
        }
        adBlocker.resetPageStats()
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(url = resolved, isLoading = true, progress = 10)
                } else tab
            }
            state.copy(tabs = updatedTabs, detectedPageMedia = emptyList())
        }
    }

    fun goHomeInActiveTab() {
        adBlocker.resetPageStats()
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(
                        url = "",
                        title = "Start Page",
                        isLoading = false,
                        progress = 0,
                        canGoBack = false,
                        canGoForward = false
                    )
                } else tab
            }
            state.copy(tabs = updatedTabs, detectedPageMedia = emptyList())
        }
    }

    fun updateTabState(
        tabId: String,
        url: String? = null,
        title: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null
    ) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url ?: tab.url,
                        title = title?.takeIf { it.isNotBlank() } ?: tab.title,
                        isLoading = isLoading ?: tab.isLoading,
                        progress = progress ?: tab.progress,
                        canGoBack = canGoBack ?: tab.canGoBack,
                        canGoForward = canGoForward ?: tab.canGoForward
                    )
                } else tab
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun newTab(initialUrl: String = "") {
        tabCounter += 1
        val newTab = BrowserTab(id = "tab-$tabCounter", url = initialUrl)
        adBlocker.resetPageStats()
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
                detectedPageMedia = emptyList(),
                activeModal = null
            )
        }
    }

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            if (state.tabs.any { it.id == tabId }) {
                state.copy(activeTabId = tabId, activeModal = null, detectedPageMedia = emptyList())
            } else state
        }
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                tabCounter += 1
                val fresh = BrowserTab(id = "tab-$tabCounter")
                state.copy(tabs = listOf(fresh), activeTabId = fresh.id, detectedPageMedia = emptyList())
            } else {
                val nextActive = if (state.activeTabId == tabId) remaining.last().id else state.activeTabId
                state.copy(tabs = remaining, activeTabId = nextActive, detectedPageMedia = emptyList())
            }
        }
    }

    fun toggleDesktopMode() {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(desktopMode = !tab.desktopMode) else tab
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun toggleCurrentBookmark() {
        val active = _uiState.value.activeTab
        if (active.url.isBlank()) return
        val current = _uiState.value.bookmarks
        val exists = current.any { it.url.equals(active.url, ignoreCase = true) }
        val updated = if (exists) {
            current.filterNot { it.url.equals(active.url, ignoreCase = true) }
        } else {
            val hostTag = try {
                Uri.parse(active.url).host?.removePrefix("www.")?.substringBefore('.')?.uppercase()?.take(6) ?: "WEB"
            } catch (_: Exception) { "WEB" }
            listOf(
                BookmarkItem(
                    id = "bm-${System.currentTimeMillis()}",
                    title = active.title.take(28).ifBlank { active.url },
                    url = active.url,
                    tag = hostTag
                )
            ) + current
        }
        saveBookmarks(updated)
        _uiState.update { it.copy(bookmarks = updated) }
    }

    fun isCurrentBookmarked(): Boolean {
        val url = _uiState.value.activeTab.url
        if (url.isBlank()) return false
        return _uiState.value.bookmarks.any { it.url.equals(url, ignoreCase = true) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine.name).apply()
        _uiState.update { it.copy(searchEngine = engine) }
    }

    fun setAutoFetchDownloads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FETCH, enabled).apply()
        _uiState.update { it.copy(autoFetchDownloads = enabled) }
    }

    fun onWebViewDownloadDetected(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        cookies: String?,
        referer: String?,
        onAutoDispatch: (
            url: String,
            fileName: String,
            totalBytes: Long,
            isResumable: Boolean,
            customTreeUri: Uri?,
            displayPath: String,
            headers: Map<String, String>
        ) -> Unit
    ) {
        val headers = buildMap {
            if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
            if (!cookies.isNullOrBlank()) put("Cookie", cookies)
            if (!referer.isNullOrBlank()) put("Referer", referer)
        }

        val guessedName = try {
            URLUtil.guessFileName(url, contentDisposition, mimeType)
        } catch (_: Exception) {
            url.substringAfterLast('/').substringBefore('?').ifBlank { "download_${System.currentTimeMillis()}.bin" }
        }
        val ext = guessedName.substringAfterLast('.', "BIN").uppercase().take(6)

        val initialDetected = DetectedDownload(
            url = url,
            fileName = guessedName,
            fileExtension = ext,
            totalBytes = contentLength.coerceAtLeast(0L),
            mimeType = mimeType,
            isResumable = true,
            isProbing = true,
            headers = headers
        )

        if (_uiState.value.autoFetchDownloads) {
            viewModelScope.launch {
                val probe = UrlProber.probe(url, headers = headers)
                val finalUrl = if (probe.isSuccess) probe.url else url
                val finalName = if (probe.isSuccess && probe.fileName.isNotBlank()) probe.fileName else guessedName
                val finalBytes = if (probe.isSuccess && probe.totalBytes > 0L) probe.totalBytes else contentLength.coerceAtLeast(0L)
                val resumable = if (probe.isSuccess) probe.isResumable else true
                onAutoDispatch(finalUrl, finalName, finalBytes, resumable, null, "Downloads/SonuMerger/", headers)
                _uiState.update { it.copy(lastStartedDownloadName = finalName) }
            }
            return
        }

        _uiState.update { it.copy(detectedDownload = initialDetected, activeModal = null) }

        viewModelScope.launch {
            val probe = UrlProber.probe(url, headers = headers)
            _uiState.update { current ->
                val active = current.detectedDownload ?: return@update current
                if (active.url != url) return@update current
                if (probe.isSuccess) {
                    val refinedName = if (probe.fileName.isNotBlank() && !probe.fileName.startsWith("download_")) {
                        probe.fileName
                    } else active.fileName
                    val refinedExt = refinedName.substringAfterLast('.', active.fileExtension).uppercase().take(6)
                    current.copy(
                        detectedDownload = active.copy(
                            url = probe.url.ifBlank { active.url },
                            fileName = refinedName,
                            fileExtension = refinedExt,
                            totalBytes = if (probe.totalBytes > 0L) probe.totalBytes else active.totalBytes,
                            isResumable = probe.isResumable,
                            isProbing = false
                        )
                    )
                } else {
                    current.copy(detectedDownload = active.copy(isProbing = false))
                }
            }
        }
    }

    fun onMagnetOrTorrentDetected(url: String) {
        val guessedName = if (url.startsWith("magnet:", ignoreCase = true)) {
            "torrent_download.torrent"
        } else {
            url.substringAfterLast('/').substringBefore('?').ifBlank { "download.torrent" }
        }
        val initial = DetectedDownload(
            url = url,
            fileName = guessedName,
            fileExtension = "TORRENT",
            totalBytes = 0L,
            mimeType = "application/x-bittorrent",
            isResumable = true,
            isProbing = true
        )
        _uiState.update { it.copy(detectedDownload = initial, activeModal = null) }

        viewModelScope.launch {
            val probe = UrlProber.probe(url)
            _uiState.update { current ->
                val active = current.detectedDownload ?: return@update current
                if (active.url != url) return@update current
                current.copy(
                    detectedDownload = active.copy(
                        fileName = if (probe.isSuccess && probe.fileName.isNotBlank()) probe.fileName else active.fileName,
                        fileExtension = if (probe.isSuccess) probe.fileExtension else "TORRENT",
                        totalBytes = probe.totalBytes.coerceAtLeast(0L),
                        isProbing = false
                    )
                )
            }
        }
    }

    fun onPageMediaFound(items: List<DetectedPageMedia>) {
        val filtered = items.filter { media ->
            val host = try {
                Uri.parse(media.url).host
            } catch (_: Exception) {
                null
            }
            host == null || !adBlocker.isDomainBlocked(host)
        }.distinctBy { it.url }
        _uiState.update { it.copy(detectedPageMedia = filtered) }
    }

    fun selectDetectedMedia(media: DetectedPageMedia, cookies: String?, userAgent: String?, referer: String?) {
        val headers = buildMap {
            if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
            if (!cookies.isNullOrBlank()) put("Cookie", cookies)
            if (!referer.isNullOrBlank()) put("Referer", referer)
        }
        val initial = DetectedDownload(
            url = media.url,
            fileName = media.fileName,
            fileExtension = media.extension,
            totalBytes = 0L,
            isResumable = true,
            isProbing = true,
            headers = headers
        )
        _uiState.update { it.copy(detectedDownload = initial, activeModal = null) }
        viewModelScope.launch {
            val probe = UrlProber.probe(media.url, headers = headers)
            _uiState.update { current ->
                val active = current.detectedDownload ?: return@update current
                if (active.url != media.url) return@update current
                if (probe.isSuccess) {
                    current.copy(
                        detectedDownload = active.copy(
                            url = probe.url.ifBlank { active.url },
                            fileName = probe.fileName.ifBlank { active.fileName },
                            fileExtension = probe.fileExtension.ifBlank { active.fileExtension },
                            totalBytes = probe.totalBytes.coerceAtLeast(0L),
                            isResumable = probe.isResumable,
                            isProbing = false
                        )
                    )
                } else {
                    current.copy(detectedDownload = active.copy(isProbing = false))
                }
            }
        }
    }

    fun updateDetectedDownloadFileName(newName: String) {
        _uiState.update { current ->
            val dl = current.detectedDownload ?: return@update current
            val ext = newName.substringAfterLast('.', dl.fileExtension).uppercase().take(6).ifBlank { dl.fileExtension }
            current.copy(detectedDownload = dl.copy(fileName = newName, fileExtension = ext))
        }
    }

    fun updateDetectedDownloadDestination(treeUri: Uri?, displayPath: String) {
        _uiState.update { current ->
            val dl = current.detectedDownload ?: return@update current
            current.copy(detectedDownload = dl.copy(saveTreeUri = treeUri, saveDisplayPath = displayPath))
        }
    }

    fun dismissDetectedDownload() {
        _uiState.update { it.copy(detectedDownload = null) }
    }

    fun markDownloadStarted(fileName: String) {
        _uiState.update { it.copy(detectedDownload = null, lastStartedDownloadName = fileName) }
    }

    fun clearLastStartedBanner() {
        _uiState.update { it.copy(lastStartedDownloadName = null) }
    }

    fun openModal(modal: String) {
        _uiState.update { it.copy(activeModal = modal) }
    }

    fun closeModal() {
        _uiState.update { it.copy(activeModal = null) }
    }
}
