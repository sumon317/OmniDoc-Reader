package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ActiveViewerState
import com.example.ui.OmniDocViewModel
import com.example.ui.SortOption
import com.example.ui.screens.AccessibilityHubTabScreen
import com.example.ui.screens.BookmarksAndNotesTabScreen
import com.example.ui.screens.ComicVaultTabScreen
import com.example.ui.screens.CreateOfflineDocDialog
import com.example.ui.screens.LibraryTabScreen
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.OmniDocTheme
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.viewers.ComicArchiveViewerScreen
import com.example.ui.viewers.DocxViewerScreen
import com.example.ui.viewers.EverythingMoePortalBrowserScreen
import com.example.ui.viewers.NativeOnlineChapterReaderScreen
import com.example.ui.viewers.OnlineSeriesChaptersSheet
import com.example.ui.viewers.PdfDocumentViewerScreen
import com.example.ui.viewers.PptxViewerScreen
import com.example.ui.viewers.TextAndMarkdownViewerScreen
import com.example.ui.viewers.XlsxViewerScreen
import kotlinx.coroutines.delay

enum class MainNavTab(val label: String, val icon: ImageVector, val tag: String) {
    LIBRARY("Library", Icons.Default.LibraryBooks, "nav_tab_library"),
    COMICS("Manga & Comics", Icons.Default.AutoStories, "nav_tab_comics"),
    BOOKMARKS("Study Hub", Icons.Default.Bookmarks, "nav_tab_bookmarks"),
    ACCESSIBILITY("TTS & Comfort", Icons.Default.AccessibilityNew, "nav_tab_accessibility")
}

class MainActivity : ComponentActivity() {

    private val viewModel: OmniDocViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.accessibilitySettings.collectAsStateWithLifecycle()
            OmniDocTheme(darkTheme = settings.darkThemeApp) {
                OmniDocRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.importExternalFile(uri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniDocRootApp(viewModel: OmniDocViewModel) {
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val filteredDocs by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val onlyFavorites by viewModel.onlyFavorites.collectAsStateWithLifecycle()
    val settings by viewModel.accessibilitySettings.collectAsStateWithLifecycle()
    val viewerState by viewModel.activeViewerState.collectAsStateWithLifecycle()
    val allBookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()
    val allAnnotations by viewModel.allAnnotations.collectAsStateWithLifecycle()
    val activeDocBookmarks by viewModel.activeDocumentBookmarks.collectAsStateWithLifecycle()
    val activeDocAnnotations by viewModel.activeDocumentAnnotations.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val hfModelStatuses by viewModel.hfModelStatuses.collectAsStateWithLifecycle()
    val hfHubSearchResults by viewModel.hfHubSearchResults.collectAsStateWithLifecycle()
    val isSearchingHfHub by viewModel.isSearchingHfHub.collectAsStateWithLifecycle()

    // Online Manga / Manhwa / Comics / EverythingMoe Hub State
    val onlineSeriesList by viewModel.onlineSeriesList.collectAsStateWithLifecycle()
    val isLoadingOnlineSeries by viewModel.isLoadingOnlineSeries.collectAsStateWithLifecycle()
    val activeOnlineFilter by viewModel.activeOnlineFilter.collectAsStateWithLifecycle()
    val onlineDownloadProgress by viewModel.onlineDownloadProgress.collectAsStateWithLifecycle()
    val activePortalBrowser by viewModel.activePortalBrowser.collectAsStateWithLifecycle()
    val selectedOnlineSeries by viewModel.selectedOnlineSeries.collectAsStateWithLifecycle()
    val selectedSeriesChapters by viewModel.selectedSeriesChapters.collectAsStateWithLifecycle()
    val isLoadingChapters by viewModel.isLoadingChapters.collectAsStateWithLifecycle()
    val activeOnlineChapterReader by viewModel.activeOnlineChapterReader.collectAsStateWithLifecycle()
    val activeOnlineChapterPages by viewModel.activeOnlineChapterPages.collectAsStateWithLifecycle()
    val isLoadingChapterPages by viewModel.isLoadingChapterPages.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainNavTab.LIBRARY) }
    var showCreateDocDialog by remember { mutableStateOf(false) }

    // Native Android System File Picker (zero storage permission required)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalFile(uri)
        }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3500)
            viewModel.clearStatusMessage()
        }
    }

    // 1. Route to Native Online Chapter Reader when active
    activeOnlineChapterReader?.let { (series, chapter) ->
        NativeOnlineChapterReaderScreen(
            series = series,
            chapter = chapter,
            pageUrls = activeOnlineChapterPages,
            isLoadingPages = isLoadingChapterPages,
            downloadProgress = onlineDownloadProgress,
            onSaveChapterAsOfflineCbz = {
                viewModel.downloadOnlineChapterToCbz(series, chapter)
            },
            onOpenInWebPortalFallback = { url ->
                viewModel.closeOnlineChapterReader()
                viewModel.openCustomComicUrlInBrowser(url, "${series.title} - Ch. ${chapter.chapterNumber}")
            },
            onClose = { viewModel.closeOnlineChapterReader() }
        )
        return
    }

    // 2. Route to EverythingMoe / Comic Portal In-App Browser when active
    activePortalBrowser?.let { portal ->
        EverythingMoePortalBrowserScreen(
            initialPortal = portal,
            allPortals = viewModel.curatedComicPortals,
            downloadProgress = onlineDownloadProgress,
            onDownloadDirectUrl = { url, filename ->
                viewModel.downloadDirectArchiveUrlToLibrary(url, filename)
            },
            onPackWebImagesToCbz = { pageTitle, imgUrls ->
                viewModel.packWebPageImagesToOfflineCbz(pageTitle, imgUrls)
            },
            onClose = { viewModel.closeEverythingMoePortal() }
        )
        return
    }

    // 3. Route to active offline document viewer when open
    when (val active = viewerState) {
        is ActiveViewerState.Loading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate950)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Amber400)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Opening ${active.document.title} offline…",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                }
            }
            return
        }
        is ActiveViewerState.Error -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate950)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = active.message,
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.closeViewer() },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Slate950)
                    ) {
                        Text("Back to Library")
                    }
                }
            }
            return
        }
        is ActiveViewerState.PdfReady -> {
            PdfDocumentViewerScreen(
                document = active.document,
                pageCount = active.pageCount,
                initialPage = active.currentPage,
                settings = settings,
                bookmarks = activeDocBookmarks,
                annotations = activeDocAnnotations,
                viewModel = viewModel,
                onBack = { viewModel.closeViewer() }
            )
            return
        }
        is ActiveViewerState.DocxReady -> {
            DocxViewerScreen(
                document = active.document,
                parsed = active.parsed,
                settings = settings,
                bookmarks = activeDocBookmarks,
                annotations = activeDocAnnotations,
                viewModel = viewModel,
                onBack = { viewModel.closeViewer() }
            )
            return
        }
        is ActiveViewerState.XlsxReady -> {
            XlsxViewerScreen(
                document = active.document,
                parsed = active.parsed,
                settings = settings,
                bookmarks = activeDocBookmarks,
                annotations = activeDocAnnotations,
                viewModel = viewModel,
                onBack = { viewModel.closeViewer() }
            )
            return
        }
        is ActiveViewerState.PptxReady -> {
            PptxViewerScreen(
                document = active.document,
                parsed = active.parsed,
                settings = settings,
                bookmarks = activeDocBookmarks,
                annotations = activeDocAnnotations,
                viewModel = viewModel,
                onBack = { viewModel.closeViewer() }
            )
            return
        }
        is ActiveViewerState.TextReady -> {
            TextAndMarkdownViewerScreen(
                document = active.document,
                parsed = active.parsed,
                settings = settings,
                bookmarks = activeDocBookmarks,
                annotations = activeDocAnnotations,
                viewModel = viewModel,
                onBack = { viewModel.closeViewer() }
            )
            return
        }
        is ActiveViewerState.ComicReady -> {
            ComicArchiveViewerScreen(
                document = active.document,
                archiveInfo = active.archiveInfo,
                initialPage = active.currentPage,
                settings = settings,
                bookmarks = activeDocBookmarks,
                annotations = activeDocAnnotations,
                viewModel = viewModel,
                onBack = { viewModel.closeViewer() }
            )
            return
        }
        ActiveViewerState.Idle -> {
            // Render main workspace below
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = Amber400,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "OD",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Slate950,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "OmniDoc Reader",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "PDF • DOCX • XLSX • PPTX • TXT • CBZ • CBR",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("topbar_open_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open File from Device",
                                tint = Amber400
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setDarkThemeApp(!settings.darkThemeApp) },
                            modifier = Modifier.testTag("topbar_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (settings.darkThemeApp) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark/Light Theme"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(imageVector = tab.icon, contentDescription = tab.label)
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MainNavTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Status Toast Banner
                    AnimatedVisibility(visible = statusMessage != null) {
                        Surface(
                            color = Slate900,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { viewModel.clearStatusMessage() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Amber400, RoundedCornerShape(50))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = statusMessage ?: "",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                                )
                            }
                        }
                    }

                    when (currentTab) {
                        MainNavTab.LIBRARY -> {
                            LibraryTabScreen(
                                allDocs = allDocs,
                                filteredDocs = filteredDocs,
                                searchQuery = searchQuery,
                                selectedFormat = selectedFormat,
                                sortOption = sortOption,
                                onlyFavorites = onlyFavorites,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onSelectFormat = { viewModel.setFormatFilter(it) },
                                onCycleSort = {
                                    val next = when (sortOption) {
                                        SortOption.RECENT -> SortOption.NAME_ASC
                                        SortOption.NAME_ASC -> SortOption.SIZE_DESC
                                        SortOption.SIZE_DESC -> SortOption.PROGRESS
                                        SortOption.PROGRESS -> SortOption.RECENT
                                    }
                                    viewModel.setSortOption(next)
                                },
                                onToggleFavorites = { viewModel.toggleOnlyFavorites() },
                                onOpenDoc = { viewModel.openDocument(it) },
                                onToggleDocFavorite = { viewModel.toggleFavorite(it) },
                                onShareDoc = { viewModel.shareDocumentOffline(it) },
                                onDeleteDoc = { viewModel.deleteDocument(it) },
                                onOpenDeviceFile = { openDocumentLauncher.launch(arrayOf("*/*")) },
                                onCreateNewDoc = { showCreateDocDialog = true }
                            )
                        }
                        MainNavTab.COMICS -> {
                            ComicVaultTabScreen(
                                allDocs = allDocs,
                                settings = settings,
                                curatedPortals = viewModel.curatedComicPortals,
                                onlineSeriesList = onlineSeriesList,
                                isLoadingOnlineSeries = isLoadingOnlineSeries,
                                activeOnlineFilter = activeOnlineFilter,
                                onlineDownloadProgress = onlineDownloadProgress,
                                onSelectComicMode = { viewModel.setComicReadingMode(it) },
                                onToggleComicWarmth = { viewModel.toggleComicWarmFilter() },
                                onOpenComic = { viewModel.openDocument(it) },
                                onImportComicFile = { openDocumentLauncher.launch(arrayOf("*/*")) },
                                onOpenPortal = { viewModel.openEverythingMoePortal(it) },
                                onOpenCustomPortalUrl = { url ->
                                    viewModel.openCustomComicUrlInBrowser(url)
                                },
                                onLoadOnlineSeries = { filter, query ->
                                    viewModel.loadOnlineComics(filter, query)
                                },
                                onSelectOnlineSeries = { series ->
                                    viewModel.selectOnlineSeries(series)
                                }
                            )
                        }
                        MainNavTab.BOOKMARKS -> {
                            BookmarksAndNotesTabScreen(
                                bookmarks = allBookmarks,
                                annotations = allAnnotations,
                                onJumpToDocUnit = { docId, unitIdx ->
                                    viewModel.openDocumentByIdAndUnit(docId, unitIdx)
                                },
                                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                                onDeleteAnnotation = { viewModel.deleteAnnotation(it) }
                            )
                        }
                        MainNavTab.ACCESSIBILITY -> {
                            AccessibilityHubTabScreen(
                                settings = settings,
                                ttsState = ttsState,
                                curatedHfModels = viewModel.curatedHfModels,
                                hfModelStatuses = hfModelStatuses,
                                hfHubSearchResults = hfHubSearchResults,
                                isSearchingHfHub = isSearchingHfHub,
                                onSelectColorMode = { viewModel.setReadingColorMode(it) },
                                onFontScaleChange = { viewModel.setFontScale(it) },
                                onLineSpacingChange = { viewModel.setLineSpacing(it) },
                                onToggleDyslexia = { viewModel.toggleDyslexiaSpacing() },
                                onToggleMonospace = { viewModel.toggleMonospaceText() },
                                onToggleRuler = { viewModel.toggleReadingRuler() },
                                onToggleInvertPdf = { viewModel.toggleInvertPdfColors() },
                                onToggleComicWarmth = { viewModel.toggleComicWarmFilter() },
                                onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
                                onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) },
                                onUpdateTtsRatePitch = { r, p -> viewModel.setTtsRateAndPitch(r, p) },
                                onTestOfflineTts = { viewModel.testActiveVoiceSample() },
                                onDownloadHfModel = { spec, fullWeights ->
                                    viewModel.downloadHfModel(spec, fullWeights)
                                },
                                onCancelHfDownload = { modelId ->
                                    viewModel.cancelHfModelDownload(modelId)
                                },
                                onActivateHfModel = { spec ->
                                    viewModel.activateHfModel(spec)
                                },
                                onTestHfModelVoice = { spec ->
                                    viewModel.testHfModelVoice(spec)
                                },
                                onDeleteHfModel = { modelId ->
                                    viewModel.deleteHfModel(modelId)
                                },
                                onSearchHfHub = { query ->
                                    viewModel.searchHuggingFaceModels(query)
                                },
                                onSelectSystemVoice = { voiceName ->
                                    viewModel.selectAndroidSystemVoice(voiceName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedOnlineSeries?.let { series ->
        OnlineSeriesChaptersSheet(
            series = series,
            chapters = selectedSeriesChapters,
            isLoadingChapters = isLoadingChapters,
            downloadProgress = onlineDownloadProgress,
            onReadChapterOnline = { ch ->
                viewModel.dismissOnlineSeriesSheet()
                viewModel.openOnlineChapterForReading(series, ch)
            },
            onDownloadChapterCbz = { ch ->
                viewModel.downloadOnlineChapterToCbz(series, ch)
            },
            onOpenSeriesInWebPortal = { webUrl ->
                viewModel.dismissOnlineSeriesSheet()
                viewModel.openCustomComicUrlInBrowser(webUrl, series.title)
            },
            onDismiss = { viewModel.dismissOnlineSeriesSheet() }
        )
    }

    if (showCreateDocDialog) {
        CreateOfflineDocDialog(
            onDismiss = { showCreateDocDialog = false },
            onCreate = { title, content, asPdf ->
                viewModel.createNewOfflineDocument(title, content, asPdf)
            }
        )
    }
}
