package com.rivora.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.rivora.browser.BrowserScreen
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.theme.rememberLiquidBackdrop
import com.rivora.theme.liquidGlassSource
import com.rivora.theme.liquidGlass
import com.rivora.ui.screens.AddDownloadScreen
import com.rivora.ui.screens.ChunksTab
import com.rivora.ui.screens.DownloadsListScreen
import com.rivora.ui.screens.LimitsDialog
import com.rivora.ui.screens.NetworksDialog
import com.rivora.ui.screens.NetworksTab
import com.rivora.ui.screens.NewDownloadDialog
import com.rivora.ui.screens.OverviewTab
import com.rivora.ui.screens.QueueScreen
import com.rivora.ui.screens.ThroughputTab
import com.rivora.util.ByteFormatter

/**
 * MainScreen orchestrating the Rivora UI layout with AndroidLiquidGlass backdrop effects.
 */
@Composable
fun MainScreen(
  viewModel: MainScreenViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val backdrop = rememberLiquidBackdrop()

  val totalSpeed = if (state.isPaused) 0f else (state.wifiSpeedMBps + state.cellularSpeedMBps)
  val formattedTotal = ByteFormatter.formatSpeed(totalSpeed, state.unit)
  val formattedWifi = ByteFormatter.formatSpeed(state.wifiSpeedMBps, state.unit)
  val formattedCell = ByteFormatter.formatSpeed(state.cellularSpeedMBps, state.unit)

  val avgSpeed = state.throughputHistory.average().toFloat()
  val peakSpeed = state.throughputHistory.maxOrNull() ?: 2.4f
  val formattedAvg = ByteFormatter.formatSpeed(avgSpeed, state.unit)
  val formattedPeak = ByteFormatter.formatSpeed(peakSpeed, state.unit)

  val displayTotal = formattedTotal.value
  val currentUnitText = formattedTotal.unit
  val displayWifi = formattedWifi.text
  val displayCell = formattedCell.text
  val displayAvg = formattedAvg.text
  val displayPeak = formattedPeak.text
  val etaDisplay = when {
    state.isPaused -> "Paused"
    !state.statusMessage.isNullOrBlank() -> state.statusMessage!!
    state.etaSeconds > 0L -> ByteFormatter.formatEta(state.etaSeconds)
    (state.activeItem != null && !state.isPaused) -> "Downloading..."
    else -> "Connecting..."
  }

  val mult = if (state.unit == "Mbps") 8f else 1f

  val downloadedGB = (state.activeItem?.downloadedBytes ?: 0L) / (1024f * 1024f * 1024f)
  val progressPercent = if ((state.activeItem?.totalBytes ?: 0L) > 0L) {
    (((state.activeItem?.downloadedBytes ?: 0L).toFloat() / (state.activeItem?.totalBytes ?: 1L)) * 100).toInt()
  } else 0

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(RivoraColor.App)
      .liquidGlassSource(backdrop)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
    when (state.currentView) {
      "add" -> {
        BackHandler { viewModel.switchView("queue") }
        AddDownloadScreen(
          queueCount = (if (state.activeItem != null) 1 else 0) + state.queue.size,
          cellularActive = state.isCellularActive,
          isWifiConnected = state.isWifiConnected,
          isCellularConnected = state.isCellularConnected,
          onNavigateToQueue = { viewModel.switchView("queue") },
          onNavigateToBrowser = { viewModel.switchView("browser") },
          onStartDownload = { url, fileName, totalBytes, isResumable, customTreeUri, displayPath ->
            viewModel.startDownload(url, fileName, totalBytes, isResumable, customTreeUri, displayPath)
          },
          modifier = Modifier.weight(1f)
        )
      }
      "browser" -> {
        BrowserScreen(
          onStartRivoraDownload = { url, fileName, totalBytes, isResumable, customTreeUri, displayPath, headers ->
            viewModel.startDownload(
              targetNameOrUrl = url,
              suggestedFileName = fileName,
              totalBytes = totalBytes,
              isResumable = isResumable,
              customTreeUri = customTreeUri,
              displayPath = displayPath,
              requestHeaders = headers,
              switchToDetail = false
            )
          },
          onNavigateToDownloads = {
            if (state.activeItem != null) {
              viewModel.switchView("detail")
              viewModel.selectTab(0)
            } else {
              viewModel.switchView("queue")
            }
          },
          onBackToQueue = { viewModel.switchView("queue") },
          modifier = Modifier.weight(1f)
        )
      }
      "queue", "list" -> {
        QueueScreen(
          activeItem = state.activeItem,
          queue = state.queue,
          finished = state.finished,
          currentSpeedText = displayTotal,
          unitText = currentUnitText,
          wifiActive = state.isWifiActive,
          cellularActive = state.isCellularActive,
          isPaused = state.isPaused,
          etaText = etaDisplay,
          onOpenDetail = { item ->
            viewModel.switchView("detail")
            viewModel.selectTab(0)
          },
          onNavigateToAdd = { viewModel.switchView("add") },
          onOpenNetworks = { viewModel.openModal("networks") },
          onOpenSettings = { viewModel.openModal("settings") },
          onTogglePause = { viewModel.togglePause() },
          onCancelActive = { viewModel.cancelDownload() },
          onStartQueued = { id -> viewModel.startQueuedDownload(id) },
          onRemoveQueued = { id -> viewModel.removeQueuedDownload(id) },
          onOpenFile = { item -> viewModel.openDownloadedFile(item) },
          onDeleteFinished = { item -> viewModel.deleteDownload(item) },
          isWifiConnected = state.isWifiConnected,
          isCellularConnected = state.isCellularConnected,
          modifier = Modifier.weight(1f)
        )
      }
      else -> {
        BackHandler { viewModel.switchView("queue") }
        // VIEW: ACTIVE DOWNLOAD DETAIL VIEW
        Column(modifier = Modifier.weight(1f)) {
          // 1. Seamless, Borderless Top Navigation Bar (Only ‹ Downloads)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier
                .clickable { viewModel.switchView("queue") }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "‹ Downloads",
                style = RivoraType.BackLabel.copy(
                  color = RivoraColor.TextPrimary,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 17.sp
                )
              )
            }
          }

        // 2. Scrollable Middle Viewport with Maximum Vertical Room
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
          AnimatedContent(
            targetState = state.activeTab,
            transitionSpec = {
              (fadeIn(tween(180)) + slideInVertically(tween(180)) { 20 }) togetherWith fadeOut(tween(120))
            },
            label = "tabContent"
          ) { tabIdx ->
            when (tabIdx) {
              0 -> OverviewTab(
                fileName = state.activeItem?.fileName ?: "No active download",
                fileExt = state.activeItem?.fileExtension ?: "BIN",
                downloadedBytes = state.activeItem?.downloadedBytes ?: 0L,
                totalBytes = state.activeItem?.totalBytes ?: 0L,
                currentSpeedText = displayTotal,
                unitText = currentUnitText,
                avgSpeedText = displayAvg,
                peakSpeedText = displayPeak,
                wifiSpeedText = displayWifi,
                cellularSpeedText = displayCell,
                wifiSpeedMBps = state.wifiSpeedMBps,
                cellularSpeedMBps = state.cellularSpeedMBps,
                wifiActive = state.isWifiActive,
                cellularActive = state.isCellularActive,
                completedChunks = state.completedChunks,
                totalChunks = state.totalChunks,
                wifiStreams = state.wifiStreams,
                isPaused = state.isPaused,
                etaText = etaDisplay,
                history = state.throughputHistory.map { it * mult },
                onCancel = { viewModel.cancelDownload() },
                onTogglePause = { viewModel.togglePause() },
                onShowInFolder = { viewModel.switchView("queue") },
                onOpenFile = {
                  state.activeItem?.let { viewModel.openDownloadedFile(it) }
                },
                wifiInterface = state.wifiInterfaceName,
                cellularInterface = state.cellularInterfaceName,
                activeNetworksCount = (if (state.isWifiActive && state.isWifiConnected) 1 else 0) + (if (state.isCellularActive && state.isCellularConnected) 1 else 0),
                isWifiConnected = state.isWifiConnected,
                isCellularConnected = state.isCellularConnected
              )
              1 -> ThroughputTab(
                currentSpeedText = displayTotal,
                unitText = currentUnitText,
                wifiSpeedText = displayWifi,
                cellularSpeedText = displayCell,
                wifiSpeedMBps = state.wifiSpeedMBps,
                cellularSpeedMBps = state.cellularSpeedMBps,
                wifiActive = state.isWifiActive,
                cellularActive = state.isCellularActive,
                avgSpeedText = displayAvg,
                peakSpeedText = displayPeak,
                history = state.throughputHistory.map { it * mult },
                maxAxisValue = maxOf(peakSpeed * mult * 1.15f, 10f),
                wifiStreams = state.wifiStreams,
                cellularStreams = state.cellularStreams,
                wifiInterface = state.wifiInterfaceName,
                cellularInterface = state.cellularInterfaceName,
                isWifiConnected = state.isWifiConnected,
                isCellularConnected = state.isCellularConnected
              )
              2 -> ChunksTab(
                completedChunks = state.completedChunks,
                cellularActive = state.isCellularActive,
                isPaused = state.isPaused,
                totalChunks = state.totalChunks,
                fileName = state.activeItem?.fileName ?: "No active download",
                fileExt = state.activeItem?.fileExtension ?: "BIN",
                downloadedBytes = state.activeItem?.downloadedBytes ?: 0L,
                totalBytes = state.activeItem?.totalBytes ?: 0L,
                currentSpeedMBps = state.wifiSpeedMBps + state.cellularSpeedMBps,
                etaText = etaDisplay
              )
              3 -> NetworksTab(
                currentSpeedText = displayTotal,
                wifiSpeedText = formattedWifi.value,
                cellularSpeedText = formattedCell.value,
                wifiUnitText = formattedWifi.unit,
                cellularUnitText = formattedCell.unit,
                wifiSpeedMBps = state.wifiSpeedMBps,
                cellularSpeedMBps = state.cellularSpeedMBps,
                wifiActive = state.isWifiActive,
                cellularActive = state.isCellularActive,
                unitText = currentUnitText,
                wifiStreams = state.wifiStreams,
                cellularStreams = state.cellularStreams,
                downloadedGB = downloadedGB,
                onToggleWifi = { viewModel.toggleWifi() },
                onToggleCellular = { viewModel.toggleCellular() },
                onOpenLimits = { viewModel.openModal("limits") },
                wifiInterface = state.wifiInterfaceName,
                cellularInterface = state.cellularInterfaceName,
                isCellularConnected = state.isCellularConnected,
                isWifiConnected = state.isWifiConnected
              )
            }
          }
        }

        // 3. Bottom Navigation Tab Bar (Fixed Permanently at Bottom)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(RivoraColor.Surface)
            .border(1.dp, RivoraColor.BorderSubtle)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(RivoraColor.TabTrack)
              .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(12.dp))
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            listOf("Overview", "Throughput", "Chunks", "Networks").forEachIndexed { idx, title ->
              val isSelected = state.activeTab == idx
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(38.dp)
                  .clip(RoundedCornerShape(9.dp))
                  .background(if (isSelected) RivoraColor.Elevated else Color.Transparent)
                  .then(if (isSelected) Modifier.border(1.dp, Color(0xFF383844), RoundedCornerShape(9.dp)) else Modifier)
                  .clickable { viewModel.selectTab(idx) },
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = title,
                  style = RivoraType.TabLabel.copy(
                    color = if (isSelected) RivoraColor.TextPrimary else RivoraColor.TextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                  )
                )
              }
            }
          }
        }
      }
    }
  }

    // Persistent Top-Level Bottom Bar (Downloads | Browser) when not in Detail view
    if (state.currentView in listOf("queue", "list", "add", "browser")) {
      val activeOrQueuedCount = (if (state.activeItem != null && state.activeItem?.status != com.rivora.data.DownloadStatus.FINISHED) 1 else 0) + state.queue.size
      RivoraPrimaryBottomNav(
        currentView = state.currentView,
        activeCount = activeOrQueuedCount,
        backdrop = backdrop,
        onSelectDownloads = { viewModel.switchView("queue") },
        onSelectBrowser = { viewModel.switchView("browser") }
      )
    }
  }
}

  // Full-Page Software Update Screen
  val updateResult by viewModel.updateCheckResult.collectAsStateWithLifecycle()
  val updateDownloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
  val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()

  (updateResult as? com.rivora.updater.UpdateCheckResult.UpdateAvailable)?.let { update ->
    com.rivora.ui.screens.UpdateScreen(
      currentVersion = update.currentVersion,
      newVersion = update.newVersion,
      release = update.release,
      downloadState = updateDownloadState,
      onStartDownload = { viewModel.startDownloadUpdate(update.release) },
      onInstallApk = { file -> viewModel.installUpdateApk(file) },
      onDismiss = { viewModel.dismissUpdate() }
    )
  }

  // Modals & Dialog Overlays
  when (state.activeModal) {
    "newdl" -> NewDownloadDialog(
      onDismiss = { viewModel.closeModal() },
      onStartDownload = { url, fileName, totalBytes, isResumable, customTreeUri, displayPath ->
        viewModel.closeModal()
        viewModel.startDownload(url, fileName, totalBytes, isResumable, customTreeUri, displayPath)
        viewModel.switchView("detail")
        viewModel.selectTab(0)
      },
      isWifiConnected = state.isWifiConnected,
      isCellularConnected = state.isCellularConnected
    )
    "networks" -> NetworksDialog(
      cellularActive = state.isCellularActive,
      onDismiss = { viewModel.closeModal() },
      onOpenLimits = {
        viewModel.closeModal()
        viewModel.openModal("limits")
      },
      isWifiConnected = state.isWifiConnected,
      isCellularConnected = state.isCellularConnected
    )
    "limits" -> LimitsDialog(
      onDismiss = { viewModel.closeModal() }
    )
    "settings" -> com.rivora.ui.screens.SettingsDialog(
      currentVersion = try { com.rivora.updater.AppUpdateManager.getInstance().getCurrentVersionName() } catch (_: Exception) { "1.1.6" },
      isCheckingUpdate = isCheckingUpdate,
      updateResult = updateResult,
      onCheckForUpdates = { viewModel.checkForUpdates() },
      onViewUpdate = {
        viewModel.closeModal()
      },
      onOpenLimits = {
        viewModel.closeModal()
        viewModel.openModal("limits")
      },
      onDismiss = { viewModel.closeModal() }
    )
  }
}

@Composable
fun RivoraPrimaryBottomNav(
  currentView: String,
  activeCount: Int,
  backdrop: LayerBackdrop,
  onSelectDownloads: () -> Unit,
  onSelectBrowser: () -> Unit
) {
  val isBrowser = currentView == "browser"
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .liquidGlass(backdrop, radius = 0.dp, tint = RivoraColor.Surface.copy(alpha = 0.85f))
      .navigationBarsPadding()
      .padding(horizontal = 14.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(RivoraColor.TabTrack)
        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(14.dp))
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Downloads Tab
      Row(
        modifier = Modifier
          .weight(1f)
          .height(42.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (!isBrowser) RivoraColor.Elevated else Color.Transparent)
          .then(
            if (!isBrowser) Modifier.border(1.dp, Color(0xFF383844), RoundedCornerShape(10.dp)) else Modifier
          )
          .clickable(onClick = onSelectDownloads)
          .testTag("nav_downloads_tab"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.Download,
          contentDescription = "Downloads",
          tint = if (!isBrowser) RivoraColor.OrangeBright else RivoraColor.TextSecondary,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Downloads",
          style = RivoraType.TabLabel.copy(
            color = if (!isBrowser) RivoraColor.TextPrimary else RivoraColor.TextSecondary,
            fontWeight = if (!isBrowser) FontWeight.SemiBold else FontWeight.Medium
          )
        )
        if (activeCount > 0) {
          Spacer(modifier = Modifier.width(6.dp))
          Box(
            modifier = Modifier
              .clip(CircleShape)
              .background(RivoraColor.Orange)
              .padding(horizontal = 6.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = activeCount.toString(),
              style = RivoraType.MiniLabel.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
          }
        }
      }

      // Browser Tab
      Row(
        modifier = Modifier
          .weight(1f)
          .height(42.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (isBrowser) RivoraColor.Elevated else Color.Transparent)
          .then(
            if (isBrowser) Modifier.border(1.dp, Color(0xFF383844), RoundedCornerShape(10.dp)) else Modifier
          )
          .clickable(onClick = onSelectBrowser)
          .testTag("nav_browser_tab"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.Public,
          contentDescription = "Browser",
          tint = if (isBrowser) RivoraColor.OrangeBright else RivoraColor.TextSecondary,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Browser",
          style = RivoraType.TabLabel.copy(
            color = if (isBrowser) RivoraColor.TextPrimary else RivoraColor.TextSecondary,
            fontWeight = if (isBrowser) FontWeight.SemiBold else FontWeight.Medium
          )
        )
      }
    }
  }
}
