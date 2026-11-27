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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraSwitch
import com.rivora.util.ByteFormatter

/**
 * Hallmark-crafted Sibling Stat Bar:
 * Displays side-by-side telemetry figures separated by 1px hairlines without nested card boxes.
 */
@Composable
fun HallmarkSiblingStatBar(
    stats: List<Triple<String, String, Color>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(RivoraColor.SurfaceRaised)
            .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
    ) {
        stats.forEachIndexed { index, (label, value, color) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(RivoraColor.BorderSubtle)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = label.uppercase(),
                    style = RivoraType.MiniLabel.copy(
                        color = RivoraColor.TextMuted,
                        letterSpacing = 0.6.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = RivoraType.rivoraText(
                        family = RivoraType.Mono,
                        size = 14f,
                        weight = FontWeight.Bold,
                        tracking = -0.3f,
                        line = 18f
                    ).copy(color = color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Download Detected Sheet — Hallmark Workbench Restyling
 */
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
                .clip(RoundedCornerShape(18.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RivoraColor.OrangeDim)
                                .border(1.dp, RivoraColor.OrangeBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = RivoraColor.OrangeBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Stream Capture Detected",
                                style = RivoraType.headingCard.copy(
                                    color = RivoraColor.TextPrimary,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = "Direct network stream intercepted",
                                style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                            )
                        }
                    }
                    if (detected.isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RivoraColor.OrangeBright,
                            strokeWidth = 2.dp
                        )
                    }
                }

                // File metadata inspection card (Single well, no double borders)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RivoraColor.SurfaceRaised)
                        .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FileTile(extension = detected.fileExtension)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val sizeText = if (detected.totalBytes > 0L) {
                            ByteFormatter.formatBytes(detected.totalBytes)
                        } else {
                            "Dynamic Stream Size"
                        }
                        val streamTag = if (detected.isResumable) "Multi-stream merge ready" else "Single stream"
                        Text(
                            text = "$sizeText · $streamTag",
                            style = RivoraType.rivoraText(
                                family = RivoraType.Mono,
                                size = 12f,
                                weight = FontWeight.SemiBold,
                                tracking = 0f,
                                line = 16f
                            ).copy(color = RivoraColor.OrangeBright),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = detected.url,
                            style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // File name input
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "FILE NAME",
                        style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RivoraColor.SurfaceRaised)
                            .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(8.dp))
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

                // Destination path row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RivoraColor.SurfaceRaised)
                        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save directory",
                            style = RivoraType.RowName.copy(color = RivoraColor.TextSecondary, fontSize = 12.sp)
                        )
                        Text(
                            text = detected.saveDisplayPath,
                            style = RivoraType.MetaLine.copy(color = RivoraColor.TextPrimary)
                        )
                    }
                    RivoraButton(
                        text = "Browse",
                        onClick = { folderPicker.launch(null) },
                        variant = RivoraButtonVariant.DARK,
                        height = 30.dp,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = RivoraColor.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
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
                            text = "Auto-fetch captured streams",
                            style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                        )
                        Text(
                            text = "Skip confirmation for links tapped in Sonu Merger",
                            style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                        )
                    }
                    RivoraSwitch(
                        checked = autoFetchEnabled,
                        onCheckedChange = onToggleAutoFetch
                    )
                }

                // Actions
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
                        text = "Fetch with Sonu Merger",
                        onClick = onConfirmDownload,
                        variant = RivoraButtonVariant.AMBER,
                        height = 42.dp,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_detected_download_button")
                    )
                }
            }
        }
    }
}

/**
 * Ad Blocker Control Dialog — Hallmark Workbench Inspection Cockpit
 */
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
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(18.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cockpit Header: Master Shield Switch + Status Badge
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (stats.isEnabled) RivoraColor.OrangeDim else RivoraColor.SurfaceRaised)
                                .border(
                                    1.dp,
                                    if (stats.isEnabled) RivoraColor.OrangeBorder else RivoraColor.BorderDefault,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Sonu Merger Ad Shield",
                                tint = if (stats.isEnabled) RivoraColor.OrangeBright else RivoraColor.TextMuted,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Sonu Merger Ad Shield",
                                    style = RivoraType.headingCard.copy(
                                        color = RivoraColor.TextPrimary,
                                        fontSize = 16.sp
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (stats.isEnabled) RivoraColor.OrangeDim else Color(0xFF26262B)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (stats.isEnabled) "ACTIVE" else "PAUSED",
                                        style = RivoraType.MiniLabel.copy(
                                            color = if (stats.isEnabled) RivoraColor.OrangeBright else RivoraColor.TextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                            Text(
                                text = "EasyList · AdAway · Peter Lowe (O(1) Domain Filter)",
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

                // Hallmark Sibling Stat Bar (3-column telemetry without box-in-box nesting)
                HallmarkSiblingStatBar(
                    stats = listOf(
                        Triple("This Tab", stats.pageBlockedCount.toString(), RivoraColor.OrangeBright),
                        Triple("Total Blocked", stats.totalBlockedCount.toString(), RivoraColor.StreamSecondary),
                        Triple("Active Rules", stats.ruleCount.toString(), RivoraColor.TextPrimary)
                    )
                )

                // Active Site Allowlist / Protection Strip
                if (!currentHost.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RivoraColor.SurfaceRaised)
                            .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentHost,
                                style = RivoraType.rivoraText(
                                    family = RivoraType.Mono,
                                    size = 13f,
                                    weight = FontWeight.SemiBold,
                                    tracking = 0f,
                                    line = 17f
                                ).copy(color = RivoraColor.TextPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isCurrentSiteAllowlisted) {
                                    "Shield bypassed for this origin"
                                } else {
                                    "Shield actively inspecting requests"
                                },
                                style = RivoraType.MetaLine.copy(
                                    color = if (isCurrentSiteAllowlisted) RivoraColor.TextMuted else RivoraColor.OrangeBright
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        RivoraButton(
                            text = if (isCurrentSiteAllowlisted) "Protect" else "Allowlist",
                            onClick = onToggleSiteAllowlist,
                            variant = if (isCurrentSiteAllowlisted) RivoraButtonVariant.AMBER else RivoraButtonVariant.DARK,
                            height = 30.dp,
                            modifier = Modifier.testTag("adblock_allowlist_button")
                        )
                    }
                }

                // Consolidated Workbench Guard Card (1px dividers between rows)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RivoraColor.SurfaceRaised)
                        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    // Row 1: Popup & Redirect Guard
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Popups & Redirect Guard",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                            )
                            Text(
                                text = "Neutralizes popunders, clickjacking overlays & auto-redirects",
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

                    // Row 2: Cosmetic Element Hiding
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cosmetic Element Hiding",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                            )
                            Text(
                                text = "Collapses empty ad slots, sponsored frames & tracking pixels",
                                style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                            )
                        }
                        RivoraSwitch(
                            checked = stats.cosmeticFiltering,
                            onCheckedChange = onToggleCosmetic
                        )
                    }
                }

                // Upstream Filter Rule Sync
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RivoraColor.SurfaceRaised)
                        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open-Source Filter Rulesets",
                            style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontSize = 13.sp)
                        )
                        Text(
                            text = stats.updateStatusMessage ?: stats.lastUpdatedLabel ?: "Bundled EasyList + Hosts",
                            style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (stats.isUpdatingLists) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RivoraColor.OrangeBright,
                            strokeWidth = 2.dp
                        )
                    } else {
                        RivoraButton(
                            text = "Sync Feeds",
                            onClick = onUpdateLists,
                            variant = RivoraButtonVariant.DARK,
                            height = 30.dp,
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Sync open-source lists",
                                    tint = RivoraColor.TextPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            },
                            modifier = Modifier.testTag("adblock_sync_button")
                        )
                    }
                }

                // Live Intercepted Activity Log (Terminal Stream)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE INTERCEPTED ACTIVITY STREAM",
                            style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
                        )
                        Text(
                            text = "${stats.recentBlockedDomains.size} items",
                            style = RivoraType.MiniLabel.copy(color = RivoraColor.TextMuted)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RivoraColor.Canvas)
                            .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (stats.recentBlockedDomains.isEmpty()) {
                            Text(
                                text = ">> No third-party ad or tracking requests intercepted yet on this page.",
                                style = RivoraType.MetaLine.copy(
                                    color = RivoraColor.TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        } else {
                            stats.recentBlockedDomains.forEach { domain ->
                                val tag = when {
                                    domain.contains("pop") || domain.contains("click") -> "[POPUP]"
                                    domain.contains("track") || domain.contains("telemetry") || domain.contains("pixel") -> "[TRACK]"
                                    else -> "[AD]"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            style = RivoraType.MiniLabel.copy(
                                                color = RivoraColor.OrangeBright,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = domain,
                                            style = RivoraType.MetaLine.copy(color = RivoraColor.TextPrimary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "BLOCKED",
                                        style = RivoraType.MiniLabel.copy(
                                            color = RivoraColor.DangerText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Done Button
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

/**
 * Tabs & Search Engine Dialog — Hallmark Style
 */
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
                .clip(RoundedCornerShape(18.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Browser Tabs (${tabs.size})",
                        style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary, fontSize = 16.sp)
                    )
                    RivoraButton(
                        text = "New Tab",
                        onClick = onNewTab,
                        variant = RivoraButtonVariant.AMBER,
                        height = 32.dp,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "New Tab",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        modifier = Modifier.testTag("browser_new_tab_button")
                    )
                }

                // Tabs List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTabId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) RivoraColor.SurfaceRaised else RivoraColor.Canvas)
                                .border(
                                    1.dp,
                                    if (isActive) RivoraColor.OrangeBorder else RivoraColor.BorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectTab(tab.id) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = if (isActive) RivoraColor.OrangeBright else RivoraColor.TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.title.ifBlank { "Start Page" },
                                    style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tab.url.ifBlank { "sonu://start" },
                                    style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(RivoraColor.Surface)
                                    .clickable { onCloseTab(tab.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close tab",
                                    tint = RivoraColor.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Search Engine Switcher
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DEFAULT SEARCH ENGINE",
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
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) RivoraColor.OrangeDim else RivoraColor.SurfaceRaised)
                                    .border(
                                        1.dp,
                                        if (selected) RivoraColor.OrangeBorder else RivoraColor.BorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSelectSearchEngine(engine) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = engine.label.substringBefore(' '),
                                    style = RivoraType.ButtonLabel.copy(
                                        color = if (selected) RivoraColor.OrangeBright else RivoraColor.TextSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1
                                )
                            }
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

/**
 * Page Media Inspector Dialog — Hallmark Style
 */
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
                .clip(RoundedCornerShape(18.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected Media & Files (${mediaList.size})",
                        style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary, fontSize = 16.sp)
                    )
                }

                if (mediaList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RivoraColor.SurfaceRaised)
                            .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No direct media streams or downloadable files detected on this webpage yet.",
                            style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mediaList, key = { it.url }) { media ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RivoraColor.SurfaceRaised)
                                    .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
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
                                        style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                RivoraButton(
                                    text = "Fetch",
                                    onClick = { onFetchMedia(media) },
                                    variant = RivoraButtonVariant.AMBER,
                                    height = 30.dp
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
