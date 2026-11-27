package com.rivora.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rivora.backend.UrlProber
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.RivoraCard
import org.json.JSONArray

private const val MEDIA_SCAN_JS = """
    (function() {
        try {
            var results = [];
            var seen = {};
            var exts = ['mp4','mkv','webm','avi','mov','mp3','flac','wav','aac','ogg','m4a','zip','rar','7z','tar','gz','iso','apk','xapk','pdf','epub','torrent','exe','dmg'];
            function addItem(url, label, kind) {
                if (!url || typeof url !== 'string') return;
                if (!url.startsWith('http://') && !url.startsWith('https://') && !url.startsWith('magnet:')) return;
                if (seen[url]) return;
                seen[url] = true;
                var cleanPath = url.split('?')[0].split('#')[0];
                var dotIdx = cleanPath.lastIndexOf('.');
                var ext = dotIdx > -1 ? cleanPath.substring(dotIdx + 1).toLowerCase() : '';
                if (url.startsWith('magnet:')) ext = 'torrent';
                if (kind !== 'VIDEO' && kind !== 'AUDIO' && exts.indexOf(ext) === -1) return;
                var name = (label && label.trim().length > 2) ? label.trim() : (cleanPath.substring(cleanPath.lastIndexOf('/') + 1) || ('media.' + (ext || 'mp4')));
                results.push({ url: url, fileName: name.substring(0, 80), extension: (ext || 'MEDIA').toUpperCase(), kind: kind });
            }
            document.querySelectorAll('video, video source').forEach(function(el) {
                addItem(el.currentSrc || el.src, document.title || 'video.mp4', 'VIDEO');
            });
            document.querySelectorAll('audio, audio source').forEach(function(el) {
                addItem(el.currentSrc || el.src, document.title || 'audio.mp3', 'AUDIO');
            });
            document.querySelectorAll('a[href]').forEach(function(a) {
                addItem(a.href, a.download || a.textContent, 'LINK');
            });
            return JSON.stringify(results.slice(0, 25));
        } catch (e) {
            return '[]';
        }
    })();
"""

@Composable
fun BrowserScreen(
    onStartRivoraDownload: (
        url: String,
        fileName: String,
        totalBytes: Long,
        isResumable: Boolean,
        customTreeUri: Uri?,
        displayPath: String,
        headers: Map<String, String>
    ) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onBackToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adStats by viewModel.adBlockStats.collectAsStateWithLifecycle()

    val activeTab = state.activeTab
    var addressInput by remember(activeTab.id, activeTab.url) { mutableStateOf(activeTab.url) }
    var isAddressFocused by remember { mutableStateOf(false) }

    val webViewCache = remember { mutableStateMapOf<String, WebView>() }

    // Clean up closed tab WebViews and dispose all on screen exit
    LaunchedEffect(state.tabs) {
        val validIds = state.tabs.map { it.id }.toSet()
        val staleIds = webViewCache.keys.filter { it !in validIds }
        staleIds.forEach { id ->
            webViewCache.remove(id)?.destroy()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewCache.values.forEach { it.destroy() }
            webViewCache.clear()
        }
    }

    val currentWebView = webViewCache[activeTab.id]

    BackHandler {
        when {
            state.detectedDownload != null -> viewModel.dismissDetectedDownload()
            state.activeModal != null -> viewModel.closeModal()
            currentWebView != null && currentWebView.canGoBack() -> currentWebView.goBack()
            activeTab.url.isNotBlank() -> viewModel.goHomeInActiveTab()
            else -> onBackToQueue()
        }
    }

    val currentHost = remember(activeTab.url) {
        try {
            if (activeTab.url.isNotBlank()) Uri.parse(activeTab.url).host else null
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RivoraColor.App)
    ) {
        // 1. Top Browser Omnibox Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RivoraColor.Surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Ad Blocker Shield Badge Button
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (adStats.isEnabled) Color(0x1E22C55E) else RivoraColor.SurfaceRaised
                    )
                    .border(
                        1.dp,
                        if (adStats.isEnabled) Color(0x5522C55E) else RivoraColor.BorderDefault,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { viewModel.openModal("adblock") }
                    .padding(horizontal = 9.dp)
                    .testTag("browser_adblock_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Ad Blocker Shield",
                    tint = if (adStats.isEnabled) Color(0xFF4ADE80) else RivoraColor.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                if (adStats.pageBlockedCount > 0 || adStats.isEnabled) {
                    Text(
                        text = adStats.pageBlockedCount.toString(),
                        style = RivoraType.LivePill.copy(
                            color = if (adStats.isEnabled) Color(0xFF4ADE80) else RivoraColor.TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Address / Search Field
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RivoraColor.SurfaceRaised)
                    .border(
                        1.dp,
                        if (isAddressFocused) RivoraColor.OrangeBorder else RivoraColor.BorderDefault,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isHttps = activeTab.url.startsWith("https://", ignoreCase = true)
                Icon(
                    imageVector = when {
                        activeTab.url.isBlank() || isAddressFocused -> Icons.Rounded.Search
                        isHttps -> Icons.Rounded.Lock
                        else -> Icons.Rounded.Public
                    },
                    contentDescription = null,
                    tint = if (isHttps && !isAddressFocused) Color(0xFF4ADE80) else RivoraColor.TextMuted,
                    modifier = Modifier.size(15.dp)
                )

                BasicTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    singleLine = true,
                    cursorBrush = SolidColor(RivoraColor.Orange),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            viewModel.openUrlInActiveTab(addressInput)
                        }
                    ),
                    textStyle = TextStyle(
                        fontFamily = RivoraType.Sans,
                        fontSize = 13.sp,
                        color = RivoraColor.TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isAddressFocused = it.isFocused }
                        .testTag("browser_url_input"),
                    decorationBox = { innerTextField ->
                        if (addressInput.isEmpty()) {
                            Text(
                                text = "Search ${state.searchEngine.label} or enter URL...",
                                style = TextStyle(
                                    fontFamily = RivoraType.Sans,
                                    fontSize = 13.sp,
                                    color = RivoraColor.TextMuted
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                )

                if (addressInput.isNotEmpty() && isAddressFocused) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { addressInput = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear URL",
                            tint = RivoraColor.TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else if (activeTab.url.isNotBlank()) {
                    val bookmarked = viewModel.isCurrentBookmarked()
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { viewModel.toggleCurrentBookmark() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "Bookmark page",
                            tint = if (bookmarked) RivoraColor.OrangeBright else RivoraColor.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Refresh / Stop or Go button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RivoraColor.SurfaceRaised)
                    .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(10.dp))
                    .clickable {
                        if (isAddressFocused && addressInput.isNotBlank()) {
                            focusManager.clearFocus()
                            viewModel.openUrlInActiveTab(addressInput)
                        } else if (activeTab.isLoading) {
                            currentWebView?.stopLoading()
                            viewModel.updateTabState(activeTab.id, isLoading = false)
                        } else if (activeTab.url.isNotBlank()) {
                            currentWebView?.reload()
                        } else if (addressInput.isNotBlank()) {
                            focusManager.clearFocus()
                            viewModel.openUrlInActiveTab(addressInput)
                        }
                    }
                    .testTag("browser_reload_go_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isAddressFocused -> Icons.AutoMirrored.Rounded.ArrowForward
                        activeTab.isLoading -> Icons.Rounded.Close
                        else -> Icons.Rounded.Refresh
                    },
                    contentDescription = if (activeTab.isLoading) "Stop" else "Reload",
                    tint = RivoraColor.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Loading Progress Line
        if (activeTab.isLoading && activeTab.progress in 1..99) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(RivoraColor.ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((activeTab.progress / 100f).coerceIn(0.05f, 1f))
                        .fillMaxHeight()
                        .background(RivoraColor.OrangeBright)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(RivoraColor.BorderSubtle)
            )
        }

        // 2. Main Viewport (Start Page OR WebView)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTab.url.isBlank()) {
                BrowserStartPage(
                    searchEngine = state.searchEngine,
                    adStats = adStats,
                    bookmarks = state.bookmarks,
                    onOpenBookmark = { bm -> viewModel.openUrlInActiveTab(bm.url) },
                    onOpenAdBlocker = { viewModel.openModal("adblock") }
                )
            } else {
                androidx.compose.runtime.key(activeTab.id) {
                    RivoraWebViewHost(
                        context = context,
                        tab = activeTab,
                        webViewCache = webViewCache,
                        adBlocker = viewModel.adBlocker,
                        onPageStateChanged = { url, title, isLoading, progress, canBack, canFwd ->
                            viewModel.updateTabState(
                                tabId = activeTab.id,
                                url = url,
                                title = title,
                                isLoading = isLoading,
                                progress = progress,
                                canGoBack = canBack,
                                canGoForward = canFwd
                            )
                        },
                        onDownloadDetected = { url, ua, disp, mime, len, cookies, referer ->
                            viewModel.onWebViewDownloadDetected(
                                url = url,
                                userAgent = ua,
                                contentDisposition = disp,
                                mimeType = mime,
                                contentLength = len,
                                cookies = cookies,
                                referer = referer,
                                onAutoDispatch = { fUrl, fName, fBytes, fRes, fTree, fPath, fHeaders ->
                                    onStartRivoraDownload(fUrl, fName, fBytes, fRes, fTree, fPath, fHeaders)
                                }
                            )
                        },
                        onMagnetOrTorrent = { magnetUrl ->
                            viewModel.onMagnetOrTorrentDetected(magnetUrl)
                        },
                        onMediaFound = { mediaList ->
                            viewModel.onPageMediaFound(mediaList)
                        }
                    )
                }
            }

            // Floating Pills Overlay (Detected Page Files + Started Download Toast)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                AnimatedVisibility(visible = state.lastStartedDownloadName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(RivoraColor.Surface)
                            .border(1.dp, RivoraColor.OrangeBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = RivoraColor.OrangeBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Fetching ${state.lastStartedDownloadName}",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "View →",
                                style = RivoraType.ButtonLabel.copy(
                                    color = RivoraColor.OrangeBright,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.clickable {
                                    viewModel.clearLastStartedBanner()
                                    onNavigateToDownloads()
                                }
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss banner",
                                tint = RivoraColor.TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.clearLastStartedBanner() }
                            )
                        }
                    }
                }

                if (state.detectedPageMedia.isNotEmpty() && activeTab.url.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(RivoraColor.Orange)
                            .clickable { viewModel.openModal("media") }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("browser_detected_media_pill"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Detected page files",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${state.detectedPageMedia.size} downloadable ${if (state.detectedPageMedia.size == 1) "file" else "files"}",
                            style = RivoraType.ButtonLabel.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }

        // 3. Bottom Browser Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserToolIcon(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                enabled = activeTab.canGoBack,
                testTag = "browser_back_button",
                onClick = { currentWebView?.goBack() }
            )
            BrowserToolIcon(
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Forward",
                enabled = activeTab.canGoForward,
                testTag = "browser_forward_button",
                onClick = { currentWebView?.goForward() }
            )
            BrowserToolIcon(
                icon = Icons.Rounded.Home,
                contentDescription = "Home",
                enabled = true,
                testTag = "browser_home_button",
                onClick = { viewModel.goHomeInActiveTab() }
            )
            BrowserToolIcon(
                icon = if (activeTab.desktopMode) Icons.Rounded.Computer else Icons.Rounded.Smartphone,
                contentDescription = if (activeTab.desktopMode) "Desktop site active" else "Mobile site active",
                enabled = true,
                tint = if (activeTab.desktopMode) RivoraColor.OrangeBright else RivoraColor.TextSecondary,
                testTag = "browser_desktop_mode_button",
                onClick = {
                    viewModel.toggleDesktopMode()
                    currentWebView?.let { wv ->
                        val nextDesktop = !activeTab.desktopMode
                        wv.settings.userAgentString = if (nextDesktop) {
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                        } else null
                        wv.settings.useWideViewPort = true
                        wv.reload()
                    }
                }
            )
            // Tabs Pill Button
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable { viewModel.openModal("tabs") }
                    .testTag("browser_tabs_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(RivoraColor.SurfaceRaised)
                        .border(1.5.dp, RivoraColor.TextSecondary, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.tabs.size.toString(),
                        style = RivoraType.LivePill.copy(
                            color = RivoraColor.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }

    // Download Detected Sheet
    state.detectedDownload?.let { detected ->
        DownloadDetectedDialog(
            detected = detected,
            autoFetchEnabled = state.autoFetchDownloads,
            onFileNameChange = { viewModel.updateDetectedDownloadFileName(it) },
            onDestinationChange = { uri, path -> viewModel.updateDetectedDownloadDestination(uri, path) },
            onToggleAutoFetch = { viewModel.setAutoFetchDownloads(it) },
            onConfirmDownload = {
                val finalName = detected.fileName.ifBlank { "download_${System.currentTimeMillis()}.bin" }
                onStartRivoraDownload(
                    detected.url,
                    finalName,
                    detected.totalBytes,
                    detected.isResumable,
                    detected.saveTreeUri,
                    detected.saveDisplayPath,
                    detected.headers
                )
                viewModel.markDownloadStarted(finalName)
            },
            onDismiss = { viewModel.dismissDetectedDownload() }
        )
    }

    // Browser Modals
    when (state.activeModal) {
        "adblock" -> AdBlockerControlDialog(
            stats = adStats,
            currentHost = currentHost,
            isCurrentSiteAllowlisted = viewModel.adBlocker.isSiteAllowlisted(currentHost),
            onToggleEnabled = { enabled ->
                viewModel.adBlocker.setEnabled(enabled)
                currentWebView?.reload()
            },
            onTogglePopups = { viewModel.adBlocker.setBlockPopups(it) },
            onToggleCosmetic = { viewModel.adBlocker.setCosmeticFiltering(it) },
            onToggleSiteAllowlist = {
                viewModel.adBlocker.toggleSiteAllowlist(currentHost)
                currentWebView?.reload()
            },
            onUpdateLists = { viewModel.adBlocker.updateOpenSourceLists() },
            onDismiss = {
                viewModel.adBlocker.clearUpdateMessage()
                viewModel.closeModal()
            }
        )
        "tabs" -> TabsAndSearchDialog(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            searchEngine = state.searchEngine,
            onSelectTab = { viewModel.selectTab(it) },
            onCloseTab = { viewModel.closeTab(it) },
            onNewTab = { viewModel.newTab() },
            onSelectSearchEngine = { viewModel.setSearchEngine(it) },
            onDismiss = { viewModel.closeModal() }
        )
        "media" -> PageMediaDialog(
            mediaList = state.detectedPageMedia,
            onFetchMedia = { media ->
                val cookies = try {
                    CookieManager.getInstance().getCookie(media.url)
                } catch (_: Exception) {
                    null
                }
                val ua = currentWebView?.settings?.userAgentString
                viewModel.selectDetectedMedia(media, cookies, ua, activeTab.url)
            },
            onDismiss = { viewModel.closeModal() }
        )
    }
}

@Composable
private fun BrowserToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    testTag: String,
    tint: Color = RivoraColor.TextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else RivoraColor.TextDisabled,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun BrowserStartPage(
    searchEngine: SearchEngine,
    adStats: AdBlockStats,
    bookmarks: List<BookmarkItem>,
    onOpenBookmark: (BookmarkItem) -> Unit,
    onOpenAdBlocker: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RivoraColor.App)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RivoraCard(hasAura = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Rivora Web Browser",
                            style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary)
                        )
                        Text(
                            text = "Search via ${searchEngine.label}. Download links and streams are automatically captured by Rivora.",
                            style = RivoraType.RowSub.copy(color = RivoraColor.TextSecondary)
                        )
                    }
                }

                // Ad Shield Live Status Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RivoraColor.SurfaceRaised)
                        .border(
                            1.dp,
                            if (adStats.isEnabled) Color(0x4422C55E) else RivoraColor.BorderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onOpenAdBlocker() }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Ad Shield",
                            tint = if (adStats.isEnabled) Color(0xFF4ADE80) else RivoraColor.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (adStats.isEnabled) "Open-Source Ad Blocker Active" else "Ad Blocker Paused",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                            )
                            Text(
                                text = "${adStats.totalBlockedCount} blocked · ${adStats.ruleCount} filter rules",
                                style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted)
                            )
                        }
                    }
                    Text(
                        text = "Configure",
                        style = RivoraType.ButtonLabel.copy(color = RivoraColor.OrangeBright)
                    )
                }
            }
        }

        item {
            Text(
                text = "QUICK ACCESS & BOOKMARKS",
                style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            val rows = bookmarks.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { bm ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RivoraColor.Surface)
                                    .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(14.dp))
                                    .clickable { onOpenBookmark(bm) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(RivoraColor.SurfaceRaised)
                                        .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = bm.tag.take(4),
                                        style = RivoraType.MiniLabel.copy(
                                            color = RivoraColor.OrangeBright,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.title,
                                        style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bm.url.removePrefix("https://").removePrefix("http://"),
                                        style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RivoraWebViewHost(
    context: Context,
    tab: BrowserTab,
    webViewCache: MutableMap<String, WebView>,
    adBlocker: AdBlockerEngine,
    onPageStateChanged: (
        url: String?,
        title: String?,
        isLoading: Boolean?,
        progress: Int?,
        canGoBack: Boolean?,
        canGoForward: Boolean?
    ) -> Unit,
    onDownloadDetected: (
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        cookies: String?,
        referer: String?
    ) -> Unit,
    onMagnetOrTorrent: (String) -> Unit,
    onMediaFound: (List<DetectedPageMedia>) -> Unit
) {
    val currentUrlState = remember { mutableStateOf(tab.url) }
    currentUrlState.value = tab.url

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            webViewCache.getOrPut(tab.id) {
                createConfiguredWebView(
                    ctx = ctx,
                    tab = tab,
                    adBlocker = adBlocker,
                    currentUrlProvider = { currentUrlState.value },
                    onPageStateChanged = onPageStateChanged,
                    onDownloadDetected = onDownloadDetected,
                    onMagnetOrTorrent = onMagnetOrTorrent,
                    onMediaFound = onMediaFound
                )
            }
        },
        update = { webView ->
            val targetUrl = tab.url
            val currentLoaded = webView.url
            if (targetUrl.isNotBlank() &&
                currentLoaded != targetUrl &&
                webView.tag != targetUrl
            ) {
                webView.tag = targetUrl
                webView.loadUrl(targetUrl)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    ctx: Context,
    tab: BrowserTab,
    adBlocker: AdBlockerEngine,
    currentUrlProvider: () -> String,
    onPageStateChanged: (String?, String?, Boolean?, Int?, Boolean?, Boolean?) -> Unit,
    onDownloadDetected: (String, String?, String?, String?, Long, String?, String?) -> Unit,
    onMagnetOrTorrent: (String) -> Unit,
    onMediaFound: (List<DetectedPageMedia>) -> Unit
): WebView {
    return WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.parseColor("#0A0A0B"))

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            if (tab.desktopMode) {
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                val pageHost = try {
                    Uri.parse(currentUrlProvider()).host
                } catch (_: Exception) {
                    null
                }
                if (adBlocker.shouldBlockRequest(reqUrl, pageHost, request.isForMainFrame)) {
                    return AdBlockerEngine.emptyAdResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUrl = request?.url?.toString() ?: return false
                if (UrlProber.isTorrentUrl(targetUrl)) {
                    onMagnetOrTorrent(targetUrl)
                    return true
                }
                val pageHost = try {
                    Uri.parse(currentUrlProvider()).host
                } catch (_: Exception) {
                    null
                }
                if (adBlocker.shouldBlockPopupOrRedirect(targetUrl, pageHost)) {
                    return true
                }
                val scheme = request.url?.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") {
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                adBlocker.resetPageStats()
                view?.tag = url
                onPageStateChanged(
                    url,
                    view?.title,
                    true,
                    15,
                    view?.canGoBack(),
                    view?.canGoForward()
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageStateChanged(
                    url,
                    view?.title,
                    false,
                    100,
                    view?.canGoBack(),
                    view?.canGoForward()
                )
                val stats = adBlocker.stats.value
                val pageHost = try {
                    if (!url.isNullOrBlank()) Uri.parse(url).host else null
                } catch (_: Exception) {
                    null
                }
                if (stats.isEnabled && stats.cosmeticFiltering && !adBlocker.isSiteAllowlisted(pageHost)) {
                    view?.evaluateJavascript(AdBlockerEngine.COSMETIC_FILTER_JS, null)
                }
                view?.evaluateJavascript(MEDIA_SCAN_JS) { rawJson ->
                    val parsed = parseMediaJson(rawJson)
                    onMediaFound(parsed)
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onPageStateChanged(
                    view?.url,
                    view?.title,
                    newProgress < 100,
                    newProgress,
                    view?.canGoBack(),
                    view?.canGoForward()
                )
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onPageStateChanged(
                    view?.url,
                    title,
                    null,
                    null,
                    view?.canGoBack(),
                    view?.canGoForward()
                )
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val hitResult = view?.hitTestResult
                val targetUrl = hitResult?.extra
                val pageHost = try {
                    Uri.parse(currentUrlProvider()).host
                } catch (_: Exception) {
                    null
                }
                if (targetUrl != null && adBlocker.shouldBlockPopupOrRedirect(targetUrl, pageHost)) {
                    return false
                }
                if (!isUserGesture && adBlocker.stats.value.isEnabled && adBlocker.stats.value.blockPopups) {
                    return false
                }
                if (!targetUrl.isNullOrBlank() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                    view?.loadUrl(targetUrl)
                }
                return false
            }
        }

        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val cookies = try {
                CookieManager.getInstance().getCookie(url)
            } catch (_: Exception) {
                null
            }
            onDownloadDetected(
                url,
                userAgent,
                contentDisposition,
                mimetype,
                contentLength,
                cookies,
                currentUrlProvider().takeIf { it.isNotBlank() }
            )
        }

        if (tab.url.isNotBlank()) {
            tag = tab.url
            loadUrl(tab.url)
        }
    }
}

private fun parseMediaJson(rawJson: String?): List<DetectedPageMedia> {
    if (rawJson.isNullOrBlank() || rawJson == "null") return emptyList()
    return try {
        val unescaped = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
            JSONArray("[$rawJson]").getString(0)
        } else {
            rawJson
        }
        val arr = JSONArray(unescaped)
        val list = mutableListOf<DetectedPageMedia>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("url")
            val name = obj.optString("fileName", "file")
            val ext = obj.optString("extension", "FILE")
            val kind = obj.optString("kind", "LINK")
            if (url.isNotBlank()) {
                list.add(DetectedPageMedia(url = url, fileName = name, extension = ext, sourceKind = kind))
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}
