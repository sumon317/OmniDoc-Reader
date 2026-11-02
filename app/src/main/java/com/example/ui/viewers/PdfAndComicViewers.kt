package com.example.ui.viewers

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AccessibilitySettings
import com.example.data.AnnotationEntity
import com.example.data.BookmarkEntity
import com.example.data.ComicArchiveInfo
import com.example.data.ComicReadingMode
import com.example.data.DocumentEntity
import com.example.engine.PdfAndComicEngine
import com.example.ui.OmniDocViewModel
import com.example.ui.components.BookmarksAndNotesSheet
import com.example.ui.components.DocumentInViewerSearchBar
import com.example.ui.components.DocumentSearchHit
import com.example.ui.components.DocumentViewerFilterBar
import com.example.ui.components.FormatBadge
import com.example.ui.components.QuickAccessibilitySheet
import com.example.ui.components.ReadingFilterOverlay
import com.example.ui.components.ReadingRulerOverlay
import com.example.ui.components.TtsControlBar
import com.example.ui.components.TtsInteractiveModalSheet
import com.example.ui.components.UBlockOriginDashboardSheet
import com.example.ui.components.UBlockOriginShieldPill
import com.example.ui.components.buildHighlightedAnnotatedString
import com.example.ui.components.countQueryMatches
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfDocumentViewerScreen(
    document: DocumentEntity,
    pageCount: Int,
    initialPage: Int,
    settings: AccessibilitySettings,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    viewModel: OmniDocViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var currentPage by remember(document.id) { mutableIntStateOf(initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var caseSensitiveSearch by remember { mutableStateOf(false) }
    var wholeWordSearch by remember { mutableStateOf(false) }
    var activeHitIndex by remember { mutableIntStateOf(0) }
    var pdfPagesText by remember(document.id) { mutableStateOf<List<String>>(emptyList()) }

    var showTtsBar by remember { mutableStateOf(false) }
    var showTtsStudioSheet by remember { mutableStateOf(false) }
    var showAccessibilitySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showUBlockSheet by remember { mutableStateOf(false) }

    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val hfStatuses by viewModel.hfModelStatuses.collectAsStateWithLifecycle()
    val uboState by viewModel.uBlockState.collectAsStateWithLifecycle()
    val isCurrentPageBookmarked = bookmarks.any { it.unitIndex == currentPage }

    LaunchedEffect(document.localFilePath, pageCount) {
        val file = File(document.localFilePath)
        pdfPagesText = withContext(Dispatchers.IO) {
            PdfAndComicEngine.extractPdfPagesText(
                file = file,
                pageCount = pageCount,
                fallbackTitle = document.title,
                fallbackSummary = document.summarySnippet
            )
        }
    }

    val searchHits = remember(pdfPagesText, searchQuery, caseSensitiveSearch, wholeWordSearch) {
        if (searchQuery.isBlank()) emptyList()
        else {
            pdfPagesText.mapIndexedNotNull { pIdx, pageText ->
                val count = countQueryMatches(pageText, searchQuery, caseSensitiveSearch, wholeWordSearch)
                if (count > 0) {
                    DocumentSearchHit(
                        unitIndex = pIdx,
                        locationBadge = "PDF Page ${pIdx + 1}",
                        snippetText = pageText,
                        matchCountInUnit = count
                    )
                } else null
            }
        }
    }

    val totalMatchOccurrences = remember(searchHits) {
        searchHits.sumOf { it.matchCountInUnit }
    }

    LaunchedEffect(searchQuery, caseSensitiveSearch, wholeWordSearch) {
        activeHitIndex = 0
    }

    val activeSearchHit = searchHits.getOrNull(activeHitIndex.coerceIn(0, (searchHits.size - 1).coerceAtLeast(0)))

    LaunchedEffect(activeHitIndex, searchHits.size) {
        if (activeSearchHit != null) {
            currentPage = activeSearchHit.unitIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(
        document.localFilePath,
        currentPage,
        settings.invertPdfColors,
        settings.colorMode,
        settings.blueLightFilterEnabled
    ) {
        isLoadingPage = true
        val file = File(document.localFilePath)
        pageBitmap = viewModel.renderPdfPageBitmap(file, currentPage)
        isLoadingPage = false
        viewModel.updateUnitProgress(document.id, currentPage, pageCount)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(settings.colorMode.backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Viewer Bar
            Surface(
                color = Slate900,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FormatBadge(format = document.docFormat)
                            Text(
                                text = document.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Page ${currentPage + 1} of $pageCount • Zoom ${(zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                        )
                    }

                    UBlockOriginShieldPill(
                        uboState = uboState,
                        onClick = { showUBlockSheet = true },
                        modifier = Modifier.padding(end = 2.dp)
                    )

                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.testTag("viewer_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in PDF",
                            tint = if (showSearchBar || searchQuery.isNotBlank()) Amber400 else Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleInvertPdfColors() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contrast,
                            contentDescription = "Toggle PDF Night Inversion",
                            tint = if (settings.invertPdfColors) Amber400 else Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleReadingRuler() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = "Toggle Reading Ruler",
                            tint = if (settings.showReadingRuler) Amber400 else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            showTtsBar = true
                            viewModel.toggleTtsPlayPause()
                        },
                        modifier = Modifier.testTag("viewer_tts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Read Aloud Offline TTS",
                            tint = if (ttsState.isPlaying || showTtsBar) Amber400 else Color.White
                        )
                    }

                    IconButton(
                        onClick = { showBookmarksSheet = true },
                        modifier = Modifier.testTag("viewer_bookmarks_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmarks & Study Notes",
                            tint = if (isCurrentPageBookmarked) Amber400 else Color.White
                        )
                    }

                    IconButton(
                        onClick = { showAccessibilitySheet = true },
                        modifier = Modifier.testTag("viewer_accessibility_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Accessibility Settings",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { viewModel.shareDocumentOffline(document) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share PDF",
                            tint = Color.White
                        )
                    }
                }
            }

            // Reading Filter Bar (Sepia, High Contrast, Blue Light Filter, etc.)
            DocumentViewerFilterBar(
                settings = settings,
                onSelectColorMode = { viewModel.setReadingColorMode(it) },
                onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
                onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) },
                onOpenFullSheet = { showAccessibilitySheet = true }
            )

            // Searchable Interface for PDF Pages
            if (showSearchBar) {
                DocumentInViewerSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    hits = searchHits,
                    totalOccurrences = totalMatchOccurrences,
                    activeHitIndex = activeHitIndex,
                    caseSensitive = caseSensitiveSearch,
                    onToggleCaseSensitive = { caseSensitiveSearch = !caseSensitiveSearch },
                    wholeWord = wholeWordSearch,
                    onToggleWholeWord = { wholeWordSearch = !wholeWordSearch },
                    filterOnlyMatches = false,
                    onToggleFilterOnlyMatches = null,
                    onSelectHitIndex = { idx ->
                        activeHitIndex = idx.coerceIn(0, (searchHits.size - 1).coerceAtLeast(0))
                    },
                    onClose = {
                        showSearchBar = false
                        searchQuery = ""
                    },
                    placeholderText = "Search extracted text across PDF pages…"
                )
            }

            // Main PDF Page Viewport with Pinch-to-Zoom & Double-Tap
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.0f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            zoomScale = newScale
                            panOffset = if (newScale > 1f) {
                                Offset(
                                    x = (panOffset.x + pan.x).coerceIn(-900f, 900f),
                                    y = (panOffset.y + pan.y).coerceIn(-1200f, 1200f)
                                )
                            } else {
                                Offset.Zero
                            }
                        }
                    }
            ) {
                if (isLoadingPage) {
                    CircularProgressIndicator(color = Amber400)
                } else {
                    val bmp = pageBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = panOffset.x,
                                    translationY = panOffset.y
                                )
                        )
                    } else {
                        Text(
                            text = "Unable to rasterize PDF page.",
                            color = settings.colorMode.textColor
                        )
                    }
                }

                // Compose-Styled PDF Page Text Match Highlight Overlay Card when Searching
                val currentPageText = pdfPagesText.getOrNull(currentPage).orEmpty()
                if (searchQuery.isNotBlank() && currentPageText.isNotBlank()) {
                    val matchesOnThisPage = countQueryMatches(
                        currentPageText,
                        searchQuery,
                        caseSensitiveSearch,
                        wholeWordSearch
                    )
                    Surface(
                        color = Slate900.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 14.dp, end = 140.dp, bottom = 14.dp)
                            .border(
                                width = 1.dp,
                                color = if (matchesOnThisPage > 0) Cyan400 else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("pdf_search_match_overlay")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = if (matchesOnThisPage > 0) {
                                    "PDF PAGE ${currentPage + 1} TEXT LAYER • $matchesOnThisPage MATCH(ES)"
                                } else {
                                    "PDF PAGE ${currentPage + 1} TEXT LAYER • NO MATCHES ON THIS PAGE"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (matchesOnThisPage > 0) Cyan400 else Amber400,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildHighlightedAnnotatedString(
                                    text = currentPageText,
                                    query = searchQuery,
                                    caseSensitive = caseSensitiveSearch,
                                    wholeWord = wholeWordSearch,
                                    isFocusedTarget = activeSearchHit?.unitIndex == currentPage
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Floating Zoom Pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Slate900.copy(alpha = 0.88f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f)
                            if (zoomScale == 1f) panOffset = Offset.Zero
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                    }
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(color = Amber400),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                    }
                }
            }

            // TTS Floating Bar
            TtsControlBar(
                ttsState = ttsState,
                visible = showTtsBar || ttsState.isPlaying,
                onTogglePlayPause = { viewModel.toggleTtsPlayPause() },
                onPrevSentence = { viewModel.prevTtsSentence() },
                onNextSentence = { viewModel.nextTtsSentence() },
                onStopAndClose = {
                    viewModel.stopTts()
                    showTtsBar = false
                },
                onCycleSpeed = {
                    val nextRate = when {
                        ttsState.speechRate < 1.0f -> 1.0f
                        ttsState.speechRate < 1.25f -> 1.25f
                        ttsState.speechRate < 1.5f -> 1.5f
                        ttsState.speechRate < 1.8f -> 2.0f
                        else -> 0.75f
                    }
                    viewModel.setTtsRateAndPitch(nextRate, ttsState.pitch)
                }
            )

            // Bottom Page Navigation & Scrubber Bar
            Surface(
                color = Slate900,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (currentPage > 0) {
                                currentPage--
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }
                        },
                        enabled = currentPage > 0,
                        modifier = Modifier.testTag("pdf_prev_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Page",
                            tint = if (currentPage > 0) Color.White else Color.Gray
                        )
                    }

                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = {
                            currentPage = it.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                        },
                        valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                        steps = (pageCount - 2).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${currentPage + 1}/$pageCount",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Amber400,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    IconButton(
                        onClick = {
                            if (currentPage < pageCount - 1) {
                                currentPage++
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }
                        },
                        enabled = currentPage < pageCount - 1,
                        modifier = Modifier.testTag("pdf_next_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Page",
                            tint = if (currentPage < pageCount - 1) Color.White else Color.Gray
                        )
                    }
                }
            }
        }

        // Optical Reading Filter Overlay (2700K Blue Light Shield, Sepia Warmth, High Contrast Frame)
        ReadingFilterOverlay(settings = settings)

        // Draggable Reading Focus Ruler Overlay
        ReadingRulerOverlay(
            visible = settings.showReadingRuler,
            rulerFraction = settings.rulerPositionFraction,
            onUpdateFraction = { viewModel.updateRulerPosition(it) },
            onClose = { viewModel.toggleReadingRuler() }
        )
    }

    if (showAccessibilitySheet) {
        QuickAccessibilitySheet(
            settings = settings,
            onDismiss = { showAccessibilitySheet = false },
            onSelectColorMode = { viewModel.setReadingColorMode(it) },
            onFontScaleChange = { viewModel.setFontScale(it) },
            onLineSpacingChange = { viewModel.setLineSpacing(it) },
            onToggleDyslexia = { viewModel.toggleDyslexiaSpacing() },
            onToggleRuler = { viewModel.toggleReadingRuler() },
            onToggleInvertPdf = { viewModel.toggleInvertPdfColors() },
            onToggleComicWarmth = { viewModel.toggleComicWarmFilter() },
            onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
            onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) }
        )
    }

    if (showBookmarksSheet) {
        BookmarksAndNotesSheet(
            documentTitle = document.title,
            currentUnitIndex = currentPage,
            bookmarks = bookmarks,
            annotations = annotations,
            onDismiss = { showBookmarksSheet = false },
            onAddBookmark = { label ->
                viewModel.addBookmarkForActiveDoc(
                    unitIndex = currentPage,
                    label = label.ifBlank { "Page ${currentPage + 1}" },
                    excerpt = "${document.title} • PDF Page ${currentPage + 1} of $pageCount"
                )
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onAddAnnotation = { text, hex ->
                viewModel.addAnnotationForActiveDoc(currentPage, text, hex)
            },
            onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
            onJumpToUnit = { targetPage ->
                currentPage = targetPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            }
        )
    }

    if (showTtsStudioSheet) {
        TtsInteractiveModalSheet(
            documentTitle = document.title,
            ttsState = ttsState,
            curatedModels = viewModel.curatedHfModels,
            modelStatuses = hfStatuses,
            onDismiss = { showTtsStudioSheet = false },
            onJumpToSentence = { idx ->
                showTtsBar = true
                viewModel.jumpToTtsSentence(idx)
            },
            onTogglePlayPause = { viewModel.toggleTtsPlayPause() },
            onSelectHfModel = { viewModel.activateHfModel(it) },
            onQuickDownloadAndSelectHfModel = { viewModel.downloadHfModel(it, fullOnnxWeights = false) },
            onSynthesizeToWav = { viewModel.exportActiveDocToWavAudio() }
        )
    }

    if (showUBlockSheet) {
        UBlockOriginDashboardSheet(
            uboState = uboState,
            currentContextTitle = "Inbuilt PDF Reader",
            currentHostOrDocName = document.title,
            isBrowserContext = false,
            onToggleGlobalPower = { viewModel.toggleUBlockGlobalPower() },
            onToggleWhitelistCurrentHost = { viewModel.toggleUBlockWhitelistHost(document.title) },
            onToggleElementZapper = {},
            onToggleBlockPopups = { viewModel.toggleUBlockPopups() },
            onToggleCosmeticFiltering = { viewModel.toggleUBlockCosmeticFiltering() },
            onToggleStrictThirdParty = { viewModel.toggleUBlockStrictThirdParty() },
            onToggleReaderTrackerSanitizer = { viewModel.toggleUBlockReaderTrackerSanitizer() },
            onToggleComicPromoFilter = { viewModel.toggleUBlockComicPromoFilter() },
            onToggleFilterList = { viewModel.toggleUBlockFilterList(it) },
            onSyncFilterListsNow = { viewModel.syncUBlockFilterListsFromGitHub() },
            onAddCustomRule = { viewModel.addUBlockCustomRule(it) },
            onRemoveCustomRule = { viewModel.removeUBlockCustomRule(it) },
            onClearLogger = { viewModel.clearUBlockLogger() },
            onDismiss = { showUBlockSheet = false }
        )
    }
}

/**
 * CBZ & CBR Comic Book Archive Reader with Western (L->R), Manga (R<-L), and Vertical Webtoon modes.
 */
@Composable
fun ComicArchiveViewerScreen(
    document: DocumentEntity,
    archiveInfo: ComicArchiveInfo,
    initialPage: Int,
    settings: AccessibilitySettings,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    viewModel: OmniDocViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val pageCount = archiveInfo.pageCount.coerceAtLeast(1)
    var currentPage by remember(document.id) { mutableIntStateOf(initialPage.coerceIn(0, pageCount - 1)) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showComicInfoDialog by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAccessibilitySheet by remember { mutableStateOf(false) }
    var showUBlockSheet by remember { mutableStateOf(false) }

    val uboState by viewModel.uBlockState.collectAsStateWithLifecycle()
    val isBookmarked = bookmarks.any { it.unitIndex == currentPage }

    LaunchedEffect(
        archiveInfo.filePath,
        currentPage,
        settings.comicWarmFilter,
        settings.colorMode,
        settings.blueLightFilterEnabled
    ) {
        isLoadingPage = true
        currentBitmap = viewModel.renderComicPageBitmap(archiveInfo, currentPage)
        isLoadingPage = false
        viewModel.updateUnitProgress(document.id, currentPage, pageCount)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Comic Studio Bar
            Surface(
                color = Slate900,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("comic_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FormatBadge(format = document.docFormat)
                                Text(
                                    text = archiveInfo.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Page ${currentPage + 1}/$pageCount • ${archiveInfo.archiveEngineType} Engine • ${settings.comicReadingMode.label}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                            )
                        }

                        UBlockOriginShieldPill(
                            uboState = uboState,
                            onClick = { showUBlockSheet = true },
                            modifier = Modifier.padding(end = 2.dp)
                        )

                        IconButton(onClick = { showComicInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Comic Info Metadata",
                                tint = Cyan400
                            )
                        }

                        IconButton(onClick = { showBookmarksSheet = true }) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark Comic Page",
                                tint = if (isBookmarked) Amber400 else Color.White
                            )
                        }

                        IconButton(onClick = { showAccessibilitySheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Visual & Eye-Care Settings",
                                tint = Color.White
                            )
                        }
                    }

                    // Reading Mode Quick Switcher Chips (Western L->R, Manga R<-L, Webtoon Vertical)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        ComicReadingMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.comicReadingMode == mode,
                                onClick = { viewModel.setComicReadingMode(mode) },
                                label = {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Reading Filter Bar (Sepia, High Contrast, Blue Light Filter, etc.)
            DocumentViewerFilterBar(
                settings = settings,
                onSelectColorMode = { viewModel.setReadingColorMode(it) },
                onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
                onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) },
                onOpenFullSheet = { showAccessibilitySheet = true }
            )

            // Main Comic Viewport
            if (settings.comicReadingMode == ComicReadingMode.VERTICAL_WEBTOON) {
                // Continuous Vertical Webtoon Scroll Strip
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(pageCount) { pageIdx ->
                        WebtoonPageItem(
                            archiveInfo = archiveInfo,
                            pageIndex = pageIdx,
                            viewModel = viewModel,
                            onPageVisible = {
                                currentPage = pageIdx
                                viewModel.updateUnitProgress(document.id, pageIdx, pageCount)
                            }
                        )
                    }
                }
            } else {
                // Single Page Zoomable Viewport (supports Western L->R and Manga R<-L tap zones)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(settings.comicReadingMode, currentPage, zoomScale) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.05f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.2f
                                    }
                                },
                                onTap = { tapOffset ->
                                    if (zoomScale <= 1.05f) {
                                        val isRightSide = tapOffset.x > size.width * 0.55f
                                        val isLeftSide = tapOffset.x < size.width * 0.45f
                                        val isManga = settings.comicReadingMode == ComicReadingMode.RIGHT_TO_LEFT
                                        if ((isRightSide && !isManga) || (isLeftSide && isManga)) {
                                            if (currentPage < pageCount - 1) currentPage++
                                        } else if ((isLeftSide && !isManga) || (isRightSide && isManga)) {
                                            if (currentPage > 0) currentPage--
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = newScale
                                panOffset = if (newScale > 1f) {
                                    Offset(
                                        x = (panOffset.x + pan.x).coerceIn(-900f, 900f),
                                        y = (panOffset.y + pan.y).coerceIn(-1200f, 1200f)
                                    )
                                } else {
                                    Offset.Zero
                                }
                            }
                        }
                ) {
                    if (isLoadingPage) {
                        CircularProgressIndicator(color = Cyan400)
                    } else {
                        val bmp = currentBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Comic Page ${currentPage + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale,
                                        translationX = panOffset.x,
                                        translationY = panOffset.y
                                    )
                            )
                        } else {
                            Text(
                                text = "Unable to decode comic panel.",
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Bottom Comic Scrubber Bar
            Surface(
                color = Slate900,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val isManga = settings.comicReadingMode == ComicReadingMode.RIGHT_TO_LEFT
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isManga) {
                                if (currentPage < pageCount - 1) currentPage++
                            } else {
                                if (currentPage > 0) currentPage--
                            }
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier.testTag("comic_left_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous",
                            tint = Color.White
                        )
                    }

                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = {
                            currentPage = it.roundToInt().coerceIn(0, pageCount - 1)
                        },
                        valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                        steps = (pageCount - 2).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        color = Slate800,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "${currentPage + 1} / $pageCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Cyan400,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isManga) {
                                if (currentPage > 0) currentPage--
                            } else {
                                if (currentPage < pageCount - 1) currentPage++
                            }
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier.testTag("comic_right_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Optical Reading Filter Overlay (2700K Blue Light Shield, Sepia Warmth, High Contrast Frame)
        ReadingFilterOverlay(settings = settings)
    }

    if (showComicInfoDialog) {
        AlertDialog(
            onDismissRequest = { showComicInfoDialog = false },
            containerColor = Slate900,
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            title = {
                Text(
                    text = archiveInfo.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Series: ${archiveInfo.series}", fontWeight = FontWeight.SemiBold, color = Amber400)
                    Text("Creator / Writer: ${archiveInfo.writer}", color = Cyan400)
                    Text("Archive Format: ${document.docFormat.label} (${archiveInfo.archiveEngineType} Stream Engine)")
                    Text("Pages: ${archiveInfo.pageCount}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(archiveInfo.summary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showComicInfoDialog = false }) {
                    Text("Close", color = Amber400)
                }
            }
        )
    }

    if (showAccessibilitySheet) {
        QuickAccessibilitySheet(
            settings = settings,
            onDismiss = { showAccessibilitySheet = false },
            onSelectColorMode = { viewModel.setReadingColorMode(it) },
            onFontScaleChange = { viewModel.setFontScale(it) },
            onLineSpacingChange = { viewModel.setLineSpacing(it) },
            onToggleDyslexia = { viewModel.toggleDyslexiaSpacing() },
            onToggleRuler = { viewModel.toggleReadingRuler() },
            onToggleInvertPdf = { viewModel.toggleInvertPdfColors() },
            onToggleComicWarmth = { viewModel.toggleComicWarmFilter() },
            onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
            onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) }
        )
    }

    if (showBookmarksSheet) {
        BookmarksAndNotesSheet(
            documentTitle = archiveInfo.title,
            currentUnitIndex = currentPage,
            bookmarks = bookmarks,
            annotations = annotations,
            onDismiss = { showBookmarksSheet = false },
            onAddBookmark = { label ->
                viewModel.addBookmarkForActiveDoc(
                    unitIndex = currentPage,
                    label = label.ifBlank { "Comic Page ${currentPage + 1}" },
                    excerpt = "${archiveInfo.title} • Panel Page ${currentPage + 1} of $pageCount"
                )
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onAddAnnotation = { text, hex ->
                viewModel.addAnnotationForActiveDoc(currentPage, text, hex)
            },
            onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
            onJumpToUnit = { targetPage ->
                currentPage = targetPage.coerceIn(0, pageCount - 1)
            }
        )
    }

    if (showUBlockSheet) {
        UBlockOriginDashboardSheet(
            uboState = uboState,
            currentContextTitle = "Inbuilt ${document.docFormat.label} Comic Reader",
            currentHostOrDocName = archiveInfo.title,
            isBrowserContext = false,
            onToggleGlobalPower = { viewModel.toggleUBlockGlobalPower() },
            onToggleWhitelistCurrentHost = { viewModel.toggleUBlockWhitelistHost(archiveInfo.title) },
            onToggleElementZapper = {},
            onToggleBlockPopups = { viewModel.toggleUBlockPopups() },
            onToggleCosmeticFiltering = { viewModel.toggleUBlockCosmeticFiltering() },
            onToggleStrictThirdParty = { viewModel.toggleUBlockStrictThirdParty() },
            onToggleReaderTrackerSanitizer = { viewModel.toggleUBlockReaderTrackerSanitizer() },
            onToggleComicPromoFilter = { viewModel.toggleUBlockComicPromoFilter() },
            onToggleFilterList = { viewModel.toggleUBlockFilterList(it) },
            onSyncFilterListsNow = { viewModel.syncUBlockFilterListsFromGitHub() },
            onAddCustomRule = { viewModel.addUBlockCustomRule(it) },
            onRemoveCustomRule = { viewModel.removeUBlockCustomRule(it) },
            onClearLogger = { viewModel.clearUBlockLogger() },
            onDismiss = { showUBlockSheet = false }
        )
    }
}

@Composable
private fun WebtoonPageItem(
    archiveInfo: ComicArchiveInfo,
    pageIndex: Int,
    viewModel: OmniDocViewModel,
    onPageVisible: () -> Unit
) {
    var bmp by remember(archiveInfo.filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(archiveInfo.filePath, pageIndex) {
        bmp = viewModel.renderComicPageBitmap(archiveInfo, pageIndex)
        onPageVisible()
    }

    val loaded = bmp
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Webtoon Page ${pageIndex + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Slate900, RoundedCornerShape(8.dp))
        ) {
            CircularProgressIndicator(color = Cyan400)
        }
    }
}
