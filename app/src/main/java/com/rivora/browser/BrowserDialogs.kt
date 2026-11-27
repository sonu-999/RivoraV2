package com.rivora.browser

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.FileTile
import com.rivora.ui.components.MiniStatBox
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraInsetBox
import com.rivora.ui.components.RivoraSwitch
import com.rivora.util.ByteFormatter

@Composable
fun DownloadDetectedDialog(
    detected: DetectedDownload,
    autoFetchEnabled: Boolean,
    onFileNameChange: (String) -> Unit,
    onDestinationChange: (Uri?, String) -> Unit,
    onToggleAutoFetch: (Boolean) -> Unit,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editableName by remember(detected.url) { mutableStateOf(detected.fileName) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val clean = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?.ifBlank { "Downloads" } ?: "Downloads"
            onDestinationChange(uri, "$clean/")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download detected",
                            tint = RivoraColor.OrangeBright,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Download detected",
                            style = RivoraType.headingCard.copy(
                                color = RivoraColor.TextPrimary,
                                fontSize = 17.sp
                            )
                        )
                    }
                    if (detected.isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RivoraColor.OrangeBright,
                            strokeWidth = 2.dp
                        )
                    }
                }

                // File summary card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RivoraColor.SurfaceRaised)
                        .border(1.dp, RivoraColor.OrangeBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FileTile(extension = detected.fileExtension)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val sizeText = if (detected.totalBytes > 0L) {
                            ByteFormatter.formatBytes(detected.totalBytes)
                        } else {
                            "Stream / Unknown size"
                        }
                        val streamTag = if (detected.isResumable) "Multi-stream ready" else "Single-stream"
                        Text(
                            text = "$sizeText · $streamTag",
                            style = RivoraType.MetaLine.copy(color = RivoraColor.OrangeBright),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = detected.url,
                            style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Editable filename input
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "FILE NAME",
                        style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RivoraColor.SurfaceRaised)
                            .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = editableName,
                            onValueChange = {
                                editableName = it
                                onFileNameChange(it)
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(RivoraColor.Orange),
                            textStyle = TextStyle(
                                fontFamily = RivoraType.Mono,
                                fontSize = 13.sp,
                                color = RivoraColor.TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detected_filename_input")
                        )
                    }
                }

                // Save path row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Save to",
                            style = RivoraType.RowName.copy(color = RivoraColor.TextSecondary)
                        )
                        Text(
                            text = detected.saveDisplayPath,
                            style = RivoraType.MetaLine.copy(color = RivoraColor.TextPrimary)
                        )
                    }
                    RivoraButton(
                        text = "Change",
                        onClick = { folderPicker.launch(null) },
                        variant = RivoraButtonVariant.DARK,
                        height = 32.dp
                    )
                }

                // Auto-fetch toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-fetch downloads",
                            style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                        )
                        Text(
                            text = "Start Rivora automatically without asking",
                            style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                        )
                    }
                    RivoraSwitch(
                        checked = autoFetchEnabled,
                        onCheckedChange = onToggleAutoFetch
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RivoraButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = RivoraButtonVariant.DARK,
                        height = 42.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_detected_download_button")
                    )
                    RivoraButton(
                        text = "Fetch with Rivora",
                        onClick = onConfirmDownload,
                        variant = RivoraButtonVariant.AMBER,
                        height = 42.dp,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("confirm_detected_download_button")
                    )
                }
            }
        }
    }
}

@Composable
fun AdBlockerControlDialog(
    stats: AdBlockStats,
    currentHost: String?,
    isCurrentSiteAllowlisted: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePopups: (Boolean) -> Unit,
    onToggleCosmetic: (Boolean) -> Unit,
    onToggleSiteAllowlist: () -> Unit,
    onUpdateLists: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (stats.isEnabled) RivoraColor.OrangeDim else RivoraColor.SurfaceRaised)
                                .border(
                                    1.dp,
                                    if (stats.isEnabled) RivoraColor.OrangeBorder else RivoraColor.BorderDefault,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Ad Blocker",
                                tint = if (stats.isEnabled) RivoraColor.OrangeBright else RivoraColor.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Rivora Ad Shield",
                                style = RivoraType.headingCard.copy(
                                    color = RivoraColor.TextPrimary,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = "EasyList · AdAway · Peter Lowe",
                                style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                            )
                        }
                    }
                    RivoraSwitch(
                        checked = stats.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        activeTrackColor = RivoraColor.Orange,
                        modifier = Modifier.testTag("adblock_master_switch")
                    )
                }

                // 3-column metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStatBox(
                        label = "This Page",
                        value = stats.pageBlockedCount.toString(),
                        valueColor = RivoraColor.OrangeBright,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatBox(
                        label = "Total Blocked",
                        value = stats.totalBlockedCount.toString(),
                        valueColor = RivoraColor.StreamSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatBox(
                        label = "Filter Rules",
                        value = stats.ruleCount.toString(),
                        valueColor = RivoraColor.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Site-specific allowlist toggle
                if (!currentHost.isNullOrBlank()) {
                    RivoraInsetBox(padding = 12.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentHost,
                                    style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isCurrentSiteAllowlisted) "Shield paused for this site" else "Shield active on this site",
                                    style = RivoraType.RowSub.copy(
                                        color = if (isCurrentSiteAllowlisted) RivoraColor.TextMuted else RivoraColor.OrangeBright
                                    )
                                )
                            }
                            RivoraButton(
                                text = if (isCurrentSiteAllowlisted) "Enable" else "Allowlist",
                                onClick = onToggleSiteAllowlist,
                                variant = if (isCurrentSiteAllowlisted) RivoraButtonVariant.AMBER else RivoraButtonVariant.DARK,
                                height = 32.dp,
                                modifier = Modifier.testTag("adblock_allowlist_button")
                            )
                        }
                    }
                }

                // Toggles card
                RivoraCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Block Popups & Redirects",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                            )
                            Text(
                                text = "Stops ad tabs & popunders on download sites",
                                style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                            )
                        }
                        RivoraSwitch(
                            checked = stats.blockPopups,
                            onCheckedChange = onTogglePopups
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(RivoraColor.BorderSubtle)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cosmetic Element Hiding",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                            )
                            Text(
                                text = "Collapses empty ad frames & sponsored slots",
                                style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                            )
                        }
                        RivoraSwitch(
                            checked = stats.cosmeticFiltering,
                            onCheckedChange = onToggleCosmetic
                        )
                    }
                }

                // Update open-source lists
                RivoraInsetBox(padding = 12.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open-Source Filter Lists",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                            )
                            Text(
                                text = stats.updateStatusMessage ?: stats.lastUpdatedLabel ?: "Bundled",
                                style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (stats.isUpdatingLists) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = RivoraColor.OrangeBright,
                                strokeWidth = 2.dp
                            )
                        } else {
                            RivoraButton(
                                text = "Sync Lists",
                                onClick = onUpdateLists,
                                variant = RivoraButtonVariant.DARK,
                                height = 32.dp,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Sync open-source lists",
                                        tint = RivoraColor.TextPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("adblock_sync_button")
                            )
                        }
                    }
                }

                // Recently blocked domains on page
                if (stats.recentBlockedDomains.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "RECENTLY BLOCKED ON PAGE",
                            style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RivoraColor.SurfaceRaised)
                                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            stats.recentBlockedDomains.forEach { domain ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = domain,
                                        style = RivoraType.MetaLine.copy(color = RivoraColor.TextPrimary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "BLOCKED",
                                        style = RivoraType.MiniLabel.copy(color = RivoraColor.DangerText)
                                    )
                                }
                            }
                        }
                    }
                }

                RivoraButton(
                    text = "Done",
                    onClick = onDismiss,
                    variant = RivoraButtonVariant.DARK,
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TabsAndSearchDialog(
    tabs: List<BrowserTab>,
    activeTabId: String,
    searchEngine: SearchEngine,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Browser Tabs (${tabs.size})",
                        style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary, fontSize = 17.sp)
                    )
                    RivoraButton(
                        text = "New Tab",
                        onClick = onNewTab,
                        variant = RivoraButtonVariant.AMBER,
                        height = 34.dp,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "New Tab",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("browser_new_tab_button")
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTabId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) RivoraColor.SurfaceRaised else RivoraColor.TabTrack)
                                .border(
                                    1.dp,
                                    if (isActive) RivoraColor.OrangeBorder else RivoraColor.BorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectTab(tab.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = if (isActive) RivoraColor.OrangeBright else RivoraColor.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.title.ifBlank { "Start Page" },
                                    style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tab.url.ifBlank { "rivora://home" },
                                    style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(RivoraColor.Surface)
                                    .clickable { onCloseTab(tab.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close tab",
                                    tint = RivoraColor.TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "SEARCH ENGINE",
                    style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SearchEngine.entries.forEach { engine ->
                        val selected = engine == searchEngine
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) RivoraColor.OrangeDim else RivoraColor.SurfaceRaised)
                                .border(
                                    1.dp,
                                    if (selected) RivoraColor.OrangeBorder else RivoraColor.BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectSearchEngine(engine) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = engine.label.substringBefore(' '),
                                style = RivoraType.ButtonLabel.copy(
                                    color = if (selected) RivoraColor.OrangeBright else RivoraColor.TextSecondary,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                RivoraButton(
                    text = "Close",
                    onClick = onDismiss,
                    variant = RivoraButtonVariant.DARK,
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PageMediaDialog(
    mediaList: List<DetectedPageMedia>,
    onFetchMedia: (DetectedPageMedia) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Detected Files on Page (${mediaList.size})",
                    style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary, fontSize = 17.sp)
                )
                if (mediaList.isEmpty()) {
                    Text(
                        text = "No direct downloadable media or archive links detected on this page yet. Tap any download link on the webpage to capture it automatically.",
                        style = RivoraType.RowSub.copy(color = RivoraColor.TextSecondary)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mediaList, key = { it.url }) { media ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RivoraColor.SurfaceRaised)
                                    .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FileTile(extension = media.extension)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = media.fileName,
                                        style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${media.sourceKind} · ${media.url}",
                                        style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                RivoraButton(
                                    text = "Fetch",
                                    onClick = { onFetchMedia(media) },
                                    variant = RivoraButtonVariant.AMBER,
                                    height = 32.dp
                                )
                            }
                        }
                    }
                }
                RivoraButton(
                    text = "Done",
                    onClick = onDismiss,
                    variant = RivoraButtonVariant.DARK,
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
