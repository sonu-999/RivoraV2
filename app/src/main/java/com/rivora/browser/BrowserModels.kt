package com.rivora.browser

import android.net.Uri

enum class SearchEngine(val label: String, val searchUrlTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s")
}

data class BrowserTab(
    val id: String,
    val title: String = "Start Page",
    val url: String = "",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopMode: Boolean = false
)

data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val tag: String = "WEB"
)

data class DetectedDownload(
    val url: String,
    val fileName: String,
    val fileExtension: String,
    val totalBytes: Long = 0L,
    val mimeType: String? = null,
    val isResumable: Boolean = true,
    val isProbing: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val saveTreeUri: Uri? = null,
    val saveDisplayPath: String = "Downloads/Rivora/"
)

data class DetectedPageMedia(
    val url: String,
    val fileName: String,
    val extension: String,
    val sourceKind: String = "LINK" // "VIDEO" | "AUDIO" | "LINK"
)

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab(id = "tab-1")),
    val activeTabId: String = "tab-1",
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val autoFetchDownloads: Boolean = false,
    val bookmarks: List<BookmarkItem> = DEFAULT_BOOKMARKS,
    val detectedDownload: DetectedDownload? = null,
    val detectedPageMedia: List<DetectedPageMedia> = emptyList(),
    val lastStartedDownloadName: String? = null,
    val activeModal: String? = null // "adblock" | "tabs" | "media" | "menu" | null
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    companion object {
        val DEFAULT_BOOKMARKS = listOf(
            BookmarkItem("bm-1", "DuckDuckGo", "https://duckduckgo.com", "SEARCH"),
            BookmarkItem("bm-2", "GitHub", "https://github.com", "CODE"),
            BookmarkItem("bm-3", "Internet Archive", "https://archive.org", "FILES"),
            BookmarkItem("bm-4", "F-Droid", "https://f-droid.org", "APK"),
            BookmarkItem("bm-5", "Ubuntu Releases", "https://releases.ubuntu.com", "ISO"),
            BookmarkItem("bm-6", "Sample Media", "https://filesamples.com", "MEDIA")
        )
    }
}
