package com.example.ui.viewers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccessibilitySettings
import com.example.data.AnnotationEntity
import com.example.data.BookmarkEntity
import com.example.data.DocxBlock
import com.example.data.DocumentEntity
import com.example.data.ParsedDocxDocument
import com.example.data.ParsedPptxDeck
import com.example.data.ParsedTextDocument
import com.example.data.ParsedXlsxWorkbook
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
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun DocxViewerScreen(
    document: DocumentEntity,
    parsed: ParsedDocxDocument,
    settings: AccessibilitySettings,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    viewModel: OmniDocViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var inDocSearch by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var caseSensitiveSearch by remember { mutableStateOf(false) }
    var wholeWordSearch by remember { mutableStateOf(false) }
    var filterOnlyMatches by remember { mutableStateOf(false) }
    var activeHitIndex by remember { mutableIntStateOf(0) }

    var showTtsBar by remember { mutableStateOf(false) }
    var showTtsStudioSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAccessibilitySheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val hfStatuses by viewModel.hfModelStatuses.collectAsStateWithLifecycle()
    val surfaceBg = settings.colorMode.backgroundColor
    val surfaceText = settings.colorMode.textColor
    val letterSpacing = if (settings.dyslexiaFriendlySpacing) 1.2.sp else 0.2.sp
    val baseFontSize = (16f * settings.fontScale).sp
    val lineHeight = (16f * settings.fontScale * settings.lineSpacingMultiplier).sp

    val indexedBlocksWithCounts = remember(parsed.blocks, inDocSearch, caseSensitiveSearch, wholeWordSearch) {
        parsed.blocks.mapIndexed { idx, block ->
            val text = when (block) {
                is DocxBlock.Heading -> block.text
                is DocxBlock.Paragraph -> block.text
                is DocxBlock.BulletItem -> block.text
                is DocxBlock.CalloutBox -> "${block.title}: ${block.body}"
                is DocxBlock.TableBlock -> (block.headers + block.rows.flatten()).joinToString(" | ")
            }
            val count = if (inDocSearch.isBlank()) 0 else countQueryMatches(text, inDocSearch, caseSensitiveSearch, wholeWordSearch)
            Triple(idx, block, count to text)
        }
    }

    val searchHits = remember(indexedBlocksWithCounts, inDocSearch) {
        if (inDocSearch.isBlank()) emptyList()
        else indexedBlocksWithCounts.mapNotNull { (origIdx, block, countAndText) ->
            val (count, text) = countAndText
            if (count > 0) {
                val blockType = when (block) {
                    is DocxBlock.Heading -> "Heading #${origIdx + 1}"
                    is DocxBlock.CalloutBox -> "Callout #${origIdx + 1}"
                    is DocxBlock.BulletItem -> "Bullet #${origIdx + 1}"
                    is DocxBlock.TableBlock -> "Table #${origIdx + 1}"
                    is DocxBlock.Paragraph -> "Paragraph #${origIdx + 1}"
                }
                DocumentSearchHit(
                    unitIndex = origIdx,
                    locationBadge = blockType,
                    snippetText = text,
                    matchCountInUnit = count
                )
            } else null
        }
    }

    val totalOccurrences = remember(searchHits) {
        searchHits.sumOf { it.matchCountInUnit }
    }

    val displayedBlocks = remember(indexedBlocksWithCounts, inDocSearch, filterOnlyMatches) {
        if (inDocSearch.isBlank() || !filterOnlyMatches) {
            indexedBlocksWithCounts
        } else {
            indexedBlocksWithCounts.filter { it.third.first > 0 }
        }
    }

    val activeHitBlockIndex = searchHits.getOrNull(activeHitIndex.coerceIn(0, (searchHits.size - 1).coerceAtLeast(0)))?.unitIndex

    LaunchedEffect(activeHitBlockIndex, filterOnlyMatches, inDocSearch) {
        if (inDocSearch.isNotBlank() && activeHitBlockIndex != null) {
            val targetIdx = displayedBlocks.indexOfFirst { it.first == activeHitBlockIndex }
            if (targetIdx >= 0) {
                listState.animateScrollToItem(targetIdx)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            CommonOfficeTopBar(
                document = document,
                subtitle = "${parsed.wordCount} words • ~${parsed.estimatedMinutes} min read • Offline DOCX",
                showRuler = settings.showReadingRuler,
                isTtsActive = ttsState.isPlaying || showTtsBar,
                isSearchActive = showSearchField || inDocSearch.isNotBlank(),
                viewModel = viewModel,
                onBack = onBack,
                onToggleSearch = { showSearchField = !showSearchField },
                onToggleRuler = { viewModel.toggleReadingRuler() },
                onToggleTts = {
                    showTtsBar = true
                    viewModel.toggleTtsPlayPause()
                },
                onExportPdf = { viewModel.exportActiveDocumentToPdf() },
                onOpenBookmarks = { showBookmarksSheet = true },
                onOpenAccessibility = { showAccessibilitySheet = true }
            )

            // Reading Filter Bar (Sepia, High Contrast, Blue Light Filter, etc.)
            DocumentViewerFilterBar(
                settings = settings,
                onSelectColorMode = { viewModel.setReadingColorMode(it) },
                onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
                onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) },
                onOpenFullSheet = { showAccessibilitySheet = true }
            )

            if (showSearchField) {
                DocumentInViewerSearchBar(
                    query = inDocSearch,
                    onQueryChange = {
                        inDocSearch = it
                        activeHitIndex = 0
                    },
                    hits = searchHits,
                    totalOccurrences = totalOccurrences,
                    activeHitIndex = activeHitIndex,
                    caseSensitive = caseSensitiveSearch,
                    onToggleCaseSensitive = {
                        caseSensitiveSearch = !caseSensitiveSearch
                        activeHitIndex = 0
                    },
                    wholeWord = wholeWordSearch,
                    onToggleWholeWord = {
                        wholeWordSearch = !wholeWordSearch
                        activeHitIndex = 0
                    },
                    filterOnlyMatches = filterOnlyMatches,
                    onToggleFilterOnlyMatches = { filterOnlyMatches = !filterOnlyMatches },
                    onSelectHitIndex = { activeHitIndex = it },
                    onClose = {
                        showSearchField = false
                        inDocSearch = ""
                    },
                    placeholderText = "Find & highlight text in Word document…"
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (settings.dyslexiaFriendlySpacing) 18.dp else 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(displayedBlocks, key = { _, triple -> "docx_blk_${triple.first}" }) { _, (origIdx, block, _) ->
                    val isFocusedMatchBlock = inDocSearch.isNotBlank() && origIdx == activeHitBlockIndex
                    val focusModifier = if (isFocusedMatchBlock) {
                        Modifier
                            .border(2.dp, Cyan400, RoundedCornerShape(12.dp))
                            .background(Cyan400.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    } else {
                        Modifier
                    }

                    Box(modifier = Modifier.fillMaxWidth().then(focusModifier)) {
                        when (block) {
                            is DocxBlock.Heading -> {
                                val hSize = when (block.level) {
                                    1 -> (24f * settings.fontScale).sp
                                    2 -> (19f * settings.fontScale).sp
                                    else -> (17f * settings.fontScale).sp
                                }
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        text = buildHighlightedAnnotatedString(
                                            text = block.text,
                                            query = inDocSearch,
                                            caseSensitive = caseSensitiveSearch,
                                            wholeWord = wholeWordSearch,
                                            isFocusedTarget = isFocusedMatchBlock
                                        ),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = surfaceText,
                                            fontSize = hSize,
                                            lineHeight = (hSize.value * 1.3f).sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = letterSpacing
                                        )
                                    )
                                    if (block.level == 1) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(64.dp)
                                                .height(3.dp)
                                                .background(Amber400, RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                            }
                            is DocxBlock.CalloutBox -> {
                                Surface(
                                    color = Amber400.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, Amber400.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = buildHighlightedAnnotatedString(
                                                text = block.title.uppercase(),
                                                query = inDocSearch,
                                                caseSensitive = caseSensitiveSearch,
                                                wholeWord = wholeWordSearch,
                                                isFocusedTarget = isFocusedMatchBlock
                                            ),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Amber400,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = buildHighlightedAnnotatedString(
                                                text = block.body,
                                                query = inDocSearch,
                                                caseSensitive = caseSensitiveSearch,
                                                wholeWord = wholeWordSearch,
                                                isFocusedTarget = isFocusedMatchBlock
                                            ),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = surfaceText,
                                                fontSize = baseFontSize,
                                                lineHeight = lineHeight,
                                                fontWeight = FontWeight.Medium,
                                                letterSpacing = letterSpacing
                                            )
                                        )
                                    }
                                }
                            }
                            is DocxBlock.BulletItem -> {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 9.dp)
                                            .size(7.dp)
                                            .background(Cyan400, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = buildHighlightedAnnotatedString(
                                            text = block.text,
                                            query = inDocSearch,
                                            caseSensitive = caseSensitiveSearch,
                                            wholeWord = wholeWordSearch,
                                            isFocusedTarget = isFocusedMatchBlock
                                        ),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = surfaceText,
                                            fontSize = baseFontSize,
                                            lineHeight = lineHeight,
                                            letterSpacing = letterSpacing
                                        )
                                    )
                                }
                            }
                            is DocxBlock.TableBlock -> {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Slate900),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .background(Slate800, RoundedCornerShape(8.dp))
                                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                        ) {
                                            block.headers.forEach { h ->
                                                Text(
                                                    text = buildHighlightedAnnotatedString(
                                                        text = h,
                                                        query = inDocSearch,
                                                        caseSensitive = caseSensitiveSearch,
                                                        wholeWord = wholeWordSearch,
                                                        isFocusedTarget = isFocusedMatchBlock
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        color = Amber400,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.width(140.dp)
                                                )
                                            }
                                        }
                                        block.rows.forEachIndexed { rIdx, row ->
                                            Row(
                                                modifier = Modifier
                                                    .padding(vertical = 8.dp, horizontal = 10.dp)
                                            ) {
                                                row.forEach { cell ->
                                                    Text(
                                                        text = buildHighlightedAnnotatedString(
                                                            text = cell,
                                                            query = inDocSearch,
                                                            caseSensitive = caseSensitiveSearch,
                                                            wholeWord = wholeWordSearch,
                                                            isFocusedTarget = isFocusedMatchBlock
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = Color.White,
                                                            fontFamily = JetBrainsMonoFontFamily
                                                        ),
                                                        modifier = Modifier.width(140.dp)
                                                    )
                                                }
                                            }
                                            if (rIdx < block.rows.lastIndex) {
                                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                            }
                                        }
                                    }
                                }
                            }
                            is DocxBlock.Paragraph -> {
                                Text(
                                    text = buildHighlightedAnnotatedString(
                                        text = block.text,
                                        query = inDocSearch,
                                        caseSensitive = caseSensitiveSearch,
                                        wholeWord = wholeWordSearch,
                                        isFocusedTarget = isFocusedMatchBlock
                                    ),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = surfaceText,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeight,
                                        fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (block.isItalic || block.isQuote) FontStyle.Italic else FontStyle.Normal,
                                        letterSpacing = letterSpacing
                                    ),
                                    modifier = if (block.isQuote) {
                                        Modifier
                                            .fillMaxWidth()
                                            .border(2.dp, Cyan400.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    } else Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

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
                    val nextRate = if (ttsState.speechRate >= 1.75f) 0.75f else ttsState.speechRate + 0.25f
                    viewModel.setTtsRateAndPitch(nextRate, ttsState.pitch)
                },
                onOpenTtsStudio = { showTtsStudioSheet = true }
            )
        }

        ReadingFilterOverlay(settings = settings)

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
            currentUnitIndex = 0,
            bookmarks = bookmarks,
            annotations = annotations,
            onDismiss = { showBookmarksSheet = false },
            onAddBookmark = { label ->
                viewModel.addBookmarkForActiveDoc(0, label.ifBlank { "DOCX Section" }, document.title)
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onAddAnnotation = { text, hex -> viewModel.addAnnotationForActiveDoc(0, text, hex) },
            onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
            onJumpToUnit = {}
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
}

/**
 * Interactive Multi-Sheet XLSX & CSV Spreadsheet Studio with Instant Column Math & Sorting.
 */
@Composable
fun XlsxViewerScreen(
    document: DocumentEntity,
    parsed: ParsedXlsxWorkbook,
    settings: AccessibilitySettings,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    viewModel: OmniDocViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var selectedColumnIndex by remember { mutableIntStateOf(2) }
    var selectedCellCoord by remember { mutableStateOf("C2") }
    var selectedCellValue by remember { mutableStateOf("") }
    var rowFilterQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var caseSensitiveSearch by remember { mutableStateOf(false) }
    var wholeWordSearch by remember { mutableStateOf(false) }
    var filterOnlyMatches by remember { mutableStateOf(false) }
    var activeHitIndex by remember { mutableIntStateOf(0) }
    var sortAscending by remember { mutableStateOf(true) }
    var showTtsBar by remember { mutableStateOf(false) }
    var showTtsStudioSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAccessibilitySheet by remember { mutableStateOf(false) }

    val activeSheet = parsed.sheets.getOrNull(selectedSheetIndex)
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val hfStatuses by viewModel.hfModelStatuses.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Compute live column statistics (Sum, Average, Min, Max) for selected column
    val colStats = remember(activeSheet, selectedColumnIndex) {
        if (activeSheet == null) null
        else {
            val nums = activeSheet.rows.mapNotNull { row ->
                row.getOrNull(selectedColumnIndex)
                    ?.replace(",", "")
                    ?.replace("$", "")
                    ?.replace("%", "")
                    ?.trim()
                    ?.toDoubleOrNull()
            }
            if (nums.isEmpty()) null
            else {
                val sum = nums.sum()
                val avg = sum / nums.size
                val min = nums.minOrNull() ?: 0.0
                val max = nums.maxOrNull() ?: 0.0
                QuadStats(sum, avg, min, max, nums.size)
            }
        }
    }

    val displayedRows = remember(
        activeSheet,
        rowFilterQuery,
        caseSensitiveSearch,
        wholeWordSearch,
        filterOnlyMatches,
        selectedColumnIndex,
        sortAscending
    ) {
        if (activeSheet == null) emptyList()
        else {
            val filtered = if (rowFilterQuery.isBlank() || !filterOnlyMatches) {
                activeSheet.rows
            } else {
                activeSheet.rows.filter { row ->
                    row.any { countQueryMatches(it, rowFilterQuery, caseSensitiveSearch, wholeWordSearch) > 0 }
                }
            }
            filtered.sortedWith { a, b ->
                val va = a.getOrNull(selectedColumnIndex) ?: ""
                val vb = b.getOrNull(selectedColumnIndex) ?: ""
                val na = va.toDoubleOrNull()
                val nb = vb.toDoubleOrNull()
                val cmp = if (na != null && nb != null) na.compareTo(nb) else va.compareTo(vb, ignoreCase = true)
                if (sortAscending) cmp else -cmp
            }
        }
    }

    val searchHits = remember(displayedRows, activeSheet, rowFilterQuery, caseSensitiveSearch, wholeWordSearch) {
        if (rowFilterQuery.isBlank() || activeSheet == null) emptyList()
        else {
            val hits = mutableListOf<DocumentSearchHit>()
            displayedRows.forEachIndexed { rIdx, row ->
                val rowNum = rIdx + 2
                row.forEachIndexed { cIdx, cellText ->
                    val count = countQueryMatches(cellText, rowFilterQuery, caseSensitiveSearch, wholeWordSearch)
                    if (count > 0) {
                        val colLetter = ('A' + cIdx)
                        val colHeader = activeSheet.headers.getOrNull(cIdx) ?: "Col $colLetter"
                        hits.add(
                            DocumentSearchHit(
                                unitIndex = rIdx,
                                subIndex = cIdx,
                                locationBadge = "${activeSheet.name} • $colLetter$rowNum ($colHeader)",
                                snippetText = cellText,
                                matchCountInUnit = count
                            )
                        )
                    }
                }
            }
            hits
        }
    }

    val totalMatchOccurrences = remember(searchHits) {
        searchHits.sumOf { it.matchCountInUnit }
    }

    LaunchedEffect(rowFilterQuery, caseSensitiveSearch, wholeWordSearch, selectedSheetIndex) {
        activeHitIndex = 0
    }

    val activeSearchHit = searchHits.getOrNull(activeHitIndex.coerceIn(0, (searchHits.size - 1).coerceAtLeast(0)))

    LaunchedEffect(activeHitIndex, searchHits.size) {
        if (activeSearchHit != null) {
            val colLetter = ('A' + activeSearchHit.subIndex)
            val rowNumber = activeSearchHit.unitIndex + 2
            selectedColumnIndex = activeSearchHit.subIndex
            selectedCellCoord = "$colLetter$rowNumber"
            selectedCellValue = activeSearchHit.snippetText
            runCatching {
                listState.animateScrollToItem(activeSearchHit.unitIndex.coerceAtLeast(0))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CommonOfficeTopBar(
                document = document,
                subtitle = "${parsed.sheets.size} Worksheets • ${parsed.totalCells} Cells • Live Math",
                showRuler = settings.showReadingRuler,
                isTtsActive = ttsState.isPlaying || showTtsBar,
                isSearchActive = showSearchBar || rowFilterQuery.isNotBlank(),
                viewModel = viewModel,
                onBack = onBack,
                onToggleSearch = { showSearchBar = !showSearchBar },
                onToggleRuler = { viewModel.toggleReadingRuler() },
                onToggleTts = {
                    showTtsBar = true
                    viewModel.toggleTtsPlayPause()
                },
                onExportPdf = { viewModel.exportActiveDocumentToPdf() },
                onOpenBookmarks = { showBookmarksSheet = true },
                onOpenAccessibility = { showAccessibilitySheet = true }
            )

            DocumentViewerFilterBar(
                settings = settings,
                onSelectColorMode = { viewModel.setReadingColorMode(it) },
                onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
                onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) },
                onOpenFullSheet = { showAccessibilitySheet = true }
            )

            if (showSearchBar) {
                DocumentInViewerSearchBar(
                    query = rowFilterQuery,
                    onQueryChange = { rowFilterQuery = it },
                    hits = searchHits,
                    totalOccurrences = totalMatchOccurrences,
                    activeHitIndex = activeHitIndex,
                    caseSensitive = caseSensitiveSearch,
                    onToggleCaseSensitive = { caseSensitiveSearch = !caseSensitiveSearch },
                    wholeWord = wholeWordSearch,
                    onToggleWholeWord = { wholeWordSearch = !wholeWordSearch },
                    filterOnlyMatches = filterOnlyMatches,
                    onToggleFilterOnlyMatches = { filterOnlyMatches = !filterOnlyMatches },
                    onSelectHitIndex = { idx ->
                        activeHitIndex = idx.coerceIn(0, (searchHits.size - 1).coerceAtLeast(0))
                    },
                    onClose = {
                        showSearchBar = false
                        rowFilterQuery = ""
                        filterOnlyMatches = false
                    },
                    placeholderText = "Search cells & values in worksheet…"
                )
            }

        // Formula / Cell Inspector + Search Filter Bar
        Surface(color = Slate900, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = Amber400.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "fx  $selectedCellCoord",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        text = selectedCellValue.ifEmpty { "Tap any cell or column header to inspect & compute stats" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = rowFilterQuery,
                    onValueChange = { rowFilterQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400) },
                    placeholder = { Text("Filter rows across active worksheet…") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Instant Column Telemetry Bar when a numeric column is selected
        if (colStats != null && activeSheet != null) {
            val colHeader = activeSheet.headers.getOrNull(selectedColumnIndex) ?: "Column"
            Surface(
                color = Color(0xFF064E3B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$colHeader:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SUM = ${String.format("%.2f", colStats.sum)}   •   AVG = ${String.format("%.2f", colStats.avg)}   •   MIN = ${String.format("%.2f", colStats.min)}   •   MAX = ${String.format("%.2f", colStats.max)}   (N=${colStats.count})",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                    )
                }
            }
        }

        // 2D Scrollable Spreadsheet Grid
        if (activeSheet != null) {
            val hScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
            ) {
                // Sticky Column Header Row
                Row(
                    modifier = Modifier
                        .background(Slate800)
                        .padding(vertical = 10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.width(48.dp)
                    ) {
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                        )
                    }
                    activeSheet.headers.forEachIndexed { cIdx, headerText ->
                        val colLetter = ('A' + cIdx)
                        val isSelectedCol = selectedColumnIndex == cIdx
                        Box(
                            modifier = Modifier
                                .width(152.dp)
                                .background(
                                    if (isSelectedCol) Amber400.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    if (selectedColumnIndex == cIdx) {
                                        sortAscending = !sortAscending
                                    } else {
                                        selectedColumnIndex = cIdx
                                        sortAscending = false
                                    }
                                    selectedCellCoord = "${colLetter}1"
                                    selectedCellValue = headerText
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text(
                                    text = "COL $colLetter ${if (isSelectedCol) (if (sortAscending) "↑" else "↓") else ""}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelectedCol) Amber400 else Cyan400,
                                        fontSize = 10.sp
                                    )
                                )
                                Text(
                                    text = buildHighlightedAnnotatedString(
                                        text = headerText,
                                        query = rowFilterQuery,
                                        caseSensitive = caseSensitiveSearch,
                                        wholeWord = wholeWordSearch,
                                        isFocusedTarget = false
                                    ),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Data Rows
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(displayedRows) { rIdx, row ->
                        val rowNumber = rIdx + 2
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(if (rIdx % 2 == 0) Slate950 else Slate900)
                                .padding(vertical = 10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.width(48.dp)
                            ) {
                                Text(
                                    text = "$rowNumber",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                )
                            }
                            row.forEachIndexed { cIdx, cellText ->
                                val colLetter = ('A' + cIdx)
                                val coord = "$colLetter$rowNumber"
                                val isSelected = selectedCellCoord == coord
                                val isFocusedMatchCell = activeSearchHit?.unitIndex == rIdx && activeSearchHit.subIndex == cIdx
                                Box(
                                    modifier = Modifier
                                        .width(152.dp)
                                        .background(
                                            if (isFocusedMatchCell) Cyan400.copy(alpha = 0.28f)
                                            else if (isSelected) Cyan400.copy(alpha = 0.22f)
                                            else if (selectedColumnIndex == cIdx) Color.White.copy(alpha = 0.03f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedColumnIndex = cIdx
                                            selectedCellCoord = coord
                                            selectedCellValue = "${activeSheet.headers.getOrNull(cIdx) ?: colLetter}: $cellText"
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = buildHighlightedAnnotatedString(
                                            text = cellText,
                                            query = rowFilterQuery,
                                            caseSensitive = caseSensitiveSearch,
                                            wholeWord = wholeWordSearch,
                                            isFocusedTarget = isFocusedMatchCell
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = (13f * settings.fontScale).sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    }
                }
            }
        }

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
                val nextRate = if (ttsState.speechRate >= 1.75f) 0.75f else ttsState.speechRate + 0.25f
                viewModel.setTtsRateAndPitch(nextRate, ttsState.pitch)
            },
            onOpenTtsStudio = { showTtsStudioSheet = true }
        )

        // Bottom Sheet Tab Switcher
        Surface(
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                itemsIndexed(parsed.sheets) { idx, sheet ->
                    FilterChip(
                        selected = selectedSheetIndex == idx,
                        onClick = {
                            selectedSheetIndex = idx
                            viewModel.updateUnitProgress(document.id, idx, parsed.sheets.size)
                        },
                        label = {
                            Text(
                                text = "${sheet.name} (${sheet.rows.size})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
        }

        ReadingFilterOverlay(settings = settings)

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
            currentUnitIndex = selectedSheetIndex,
            bookmarks = bookmarks,
            annotations = annotations,
            onDismiss = { showBookmarksSheet = false },
            onAddBookmark = { label ->
                val sheetName = activeSheet?.name ?: "Sheet ${selectedSheetIndex + 1}"
                viewModel.addBookmarkForActiveDoc(selectedSheetIndex, label.ifBlank { sheetName }, "$sheetName • Cell $selectedCellCoord")
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onAddAnnotation = { text, hex -> viewModel.addAnnotationForActiveDoc(selectedSheetIndex, text, hex) },
            onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
            onJumpToUnit = { idx ->
                selectedSheetIndex = idx.coerceIn(0, (parsed.sheets.size - 1).coerceAtLeast(0))
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
}

private data class QuadStats(
    val sum: Double,
    val avg: Double,
    val min: Double,
    val max: Double,
    val count: Int
)

/**
 * PPTX Presentation Slide Deck Studio with 16:9 Visual Stage, Filmstrip & Speaker Notes.
 */
@Composable
fun PptxViewerScreen(
    document: DocumentEntity,
    parsed: ParsedPptxDeck,
    settings: AccessibilitySettings,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    viewModel: OmniDocViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val slideCount = parsed.slides.size.coerceAtLeast(1)
    var currentSlideIndex by remember(document.id) {
        mutableIntStateOf(document.currentUnit.coerceIn(0, slideCount - 1))
    }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var caseSensitiveSearch by remember { mutableStateOf(false) }
    var wholeWordSearch by remember { mutableStateOf(false) }
    var activeHitIndex by remember { mutableIntStateOf(0) }
    var showSpeakerNotes by remember { mutableStateOf(true) }
    var showTtsBar by remember { mutableStateOf(false) }
    var showTtsStudioSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAccessibilitySheet by remember { mutableStateOf(false) }

    val slide = parsed.slides.getOrNull(currentSlideIndex)
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val hfStatuses by viewModel.hfModelStatuses.collectAsStateWithLifecycle()

    val searchHits = remember(parsed.slides, searchQuery, caseSensitiveSearch, wholeWordSearch) {
        if (searchQuery.isBlank()) emptyList()
        else {
            parsed.slides.mapIndexedNotNull { sIdx, s ->
                val combined = buildString {
                    append(s.title).append(" • ")
                    if (s.subtitle.isNotBlank()) append(s.subtitle).append(" • ")
                    if (s.keyMetric != null) append(s.keyMetric).append(" • ")
                    append(s.bullets.joinToString(" • "))
                    if (s.speakerNotes.isNotBlank()) append(" • Notes: ").append(s.speakerNotes)
                }
                val count = countQueryMatches(combined, searchQuery, caseSensitiveSearch, wholeWordSearch)
                if (count > 0) {
                    DocumentSearchHit(
                        unitIndex = sIdx,
                        locationBadge = "Slide ${s.slideNumber} • ${s.title.take(28)}",
                        snippetText = combined,
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
            currentSlideIndex = activeSearchHit.unitIndex.coerceIn(0, slideCount - 1)
            viewModel.updateUnitProgress(document.id, currentSlideIndex, slideCount)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CommonOfficeTopBar(
                document = document,
                subtitle = "Slide ${currentSlideIndex + 1} of $slideCount • Keynote Presenter Mode",
                showRuler = settings.showReadingRuler,
                isTtsActive = ttsState.isPlaying || showTtsBar,
                isSearchActive = showSearchBar || searchQuery.isNotBlank(),
                viewModel = viewModel,
                onBack = onBack,
                onToggleSearch = { showSearchBar = !showSearchBar },
                onToggleRuler = { viewModel.toggleReadingRuler() },
                onToggleTts = {
                    showTtsBar = true
                    viewModel.toggleTtsPlayPause()
                },
                onExportPdf = { viewModel.exportActiveDocumentToPdf() },
                onOpenBookmarks = { showBookmarksSheet = true },
                onOpenAccessibility = { showAccessibilitySheet = true }
            )

            DocumentViewerFilterBar(
                settings = settings,
                onSelectColorMode = { viewModel.setReadingColorMode(it) },
                onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
                onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) },
                onOpenFullSheet = { showAccessibilitySheet = true }
            )

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
                    placeholderText = "Search slides, bullets & speaker notes…"
                )
            }

        if (slide != null) {
            val accentColor = runCatching {
                Color(android.graphics.Color.parseColor(slide.accentHex))
            }.getOrDefault(Amber400)
            val isFocusedSlide = activeSearchHit?.unitIndex == currentSlideIndex

            LazyColumn(
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Visual Presentation Slide Canvas Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = accentColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "SLIDE 0${slide.slideNumber} // 0$slideCount",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                if (slide.keyMetric != null) {
                                    Surface(
                                        color = Cyan400.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = buildHighlightedAnnotatedString(
                                                text = slide.keyMetric,
                                                query = searchQuery,
                                                caseSensitive = caseSensitiveSearch,
                                                wholeWord = wholeWordSearch,
                                                isFocusedTarget = isFocusedSlide
                                            ),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Cyan400,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = buildHighlightedAnnotatedString(
                                    text = slide.title,
                                    query = searchQuery,
                                    caseSensitive = caseSensitiveSearch,
                                    wholeWord = wholeWordSearch,
                                    isFocusedTarget = isFocusedSlide
                                ),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = Color.White,
                                    fontSize = (24f * settings.fontScale).sp,
                                    lineHeight = (30f * settings.fontScale).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            if (slide.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = buildHighlightedAnnotatedString(
                                        text = slide.subtitle,
                                        query = searchQuery,
                                        caseSensitive = caseSensitiveSearch,
                                        wholeWord = wholeWordSearch,
                                        isFocusedTarget = isFocusedSlide
                                    ),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = accentColor,
                                        fontSize = (15f * settings.fontScale).sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(16.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                slide.bullets.forEach { bullet ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .size(8.dp)
                                                .background(accentColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = buildHighlightedAnnotatedString(
                                                text = bullet,
                                                query = searchQuery,
                                                caseSensitive = caseSensitiveSearch,
                                                wholeWord = wholeWordSearch,
                                                isFocusedTarget = isFocusedSlide
                                            ),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = Color(0xFFF1F5F9),
                                                fontSize = (16f * settings.fontScale).sp,
                                                lineHeight = (23f * settings.fontScale).sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Speaker Notes Card
                if (slide.speakerNotes.isNotBlank() && showSpeakerNotes) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate800)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SpeakerNotes,
                                        contentDescription = null,
                                        tint = Amber400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PRESENTER SPEAKER NOTES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Amber400,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = buildHighlightedAnnotatedString(
                                        text = slide.speakerNotes,
                                        query = searchQuery,
                                        caseSensitive = caseSensitiveSearch,
                                        wholeWord = wholeWordSearch,
                                        isFocusedTarget = isFocusedSlide
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE2E8F0))
                                )
                            }
                        }
                    }
                }
            }
        }

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
                val nextRate = if (ttsState.speechRate >= 1.75f) 0.75f else ttsState.speechRate + 0.25f
                viewModel.setTtsRateAndPitch(nextRate, ttsState.pitch)
            },
            onOpenTtsStudio = { showTtsStudioSheet = true }
        )

        // Slide Filmstrip & Navigation Controls
        Surface(
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(parsed.slides) { idx, s ->
                        val isSelected = currentSlideIndex == idx
                        val slideHasMatch = searchHits.any { it.unitIndex == idx }
                        Surface(
                            color = if (isSelected) Amber400.copy(alpha = 0.2f)
                            else if (slideHasMatch) Cyan400.copy(alpha = 0.16f)
                            else Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .width(136.dp)
                                .border(
                                    width = if (isSelected || slideHasMatch) 2.dp else 1.dp,
                                    color = if (isSelected) Amber400
                                    else if (slideHasMatch) Cyan400
                                    else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    currentSlideIndex = idx
                                    viewModel.updateUnitProgress(document.id, idx, slideCount)
                                }
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Slide ${s.slideNumber}${if (slideHasMatch) " • Match" else ""}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Amber400 else Cyan400
                                    )
                                )
                                Text(
                                    text = buildHighlightedAnnotatedString(
                                        text = s.title,
                                        query = searchQuery,
                                        caseSensitive = caseSensitiveSearch,
                                        wholeWord = wholeWordSearch,
                                        isFocusedTarget = false
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (currentSlideIndex > 0) {
                                currentSlideIndex--
                                viewModel.updateUnitProgress(document.id, currentSlideIndex, slideCount)
                            }
                        },
                        enabled = currentSlideIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Slide", tint = Color.White)
                    }

                    FilterChip(
                        selected = showSpeakerNotes,
                        onClick = { showSpeakerNotes = !showSpeakerNotes },
                        label = { Text("Speaker Notes") }
                    )

                    IconButton(
                        onClick = {
                            if (currentSlideIndex < slideCount - 1) {
                                currentSlideIndex++
                                viewModel.updateUnitProgress(document.id, currentSlideIndex, slideCount)
                            }
                        },
                        enabled = currentSlideIndex < slideCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Slide", tint = Color.White)
                    }
                }
            }
        }
        }

        ReadingFilterOverlay(settings = settings)

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
            currentUnitIndex = currentSlideIndex,
            bookmarks = bookmarks,
            annotations = annotations,
            onDismiss = { showBookmarksSheet = false },
            onAddBookmark = { label ->
                viewModel.addBookmarkForActiveDoc(
                    currentSlideIndex,
                    label.ifBlank { "Slide ${currentSlideIndex + 1}" },
                    slide?.title ?: document.title
                )
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onAddAnnotation = { text, hex -> viewModel.addAnnotationForActiveDoc(currentSlideIndex, text, hex) },
            onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
            onJumpToUnit = { idx ->
                currentSlideIndex = idx.coerceIn(0, slideCount - 1)
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
}

/**
 * TXT, Markdown & Code Reader + Direct Offline Editor.
 */
@Composable
fun TextAndMarkdownViewerScreen(
    document: DocumentEntity,
    parsed: ParsedTextDocument,
    settings: AccessibilitySettings,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    viewModel: OmniDocViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember(parsed.content) { mutableStateOf(parsed.content) }
    var showRawLineNumbers by remember { mutableStateOf(!parsed.isMarkdown) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var caseSensitiveSearch by remember { mutableStateOf(false) }
    var wholeWordSearch by remember { mutableStateOf(false) }
    var filterOnlyMatches by remember { mutableStateOf(false) }
    var activeHitIndex by remember { mutableIntStateOf(0) }
    var showTtsBar by remember { mutableStateOf(false) }
    var showTtsStudioSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAccessibilitySheet by remember { mutableStateOf(false) }

    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val hfStatuses by viewModel.hfModelStatuses.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val bg = settings.colorMode.backgroundColor
    val fg = settings.colorMode.textColor
    val fontFamily = if (settings.useMonospaceForText || showRawLineNumbers) {
        JetBrainsMonoFontFamily
    } else {
        PlusJakartaFontFamily
    }

    val visibleLines = remember(parsed.lines, searchQuery, caseSensitiveSearch, wholeWordSearch, filterOnlyMatches) {
        if (searchQuery.isBlank() || !filterOnlyMatches) {
            parsed.lines.mapIndexed { i, l -> (i + 1) to l }
        } else {
            parsed.lines.mapIndexedNotNull { i, l ->
                if (countQueryMatches(l, searchQuery, caseSensitiveSearch, wholeWordSearch) > 0) (i + 1) to l else null
            }
        }
    }

    val searchHits = remember(visibleLines, searchQuery, caseSensitiveSearch, wholeWordSearch) {
        if (searchQuery.isBlank()) emptyList()
        else {
            visibleLines.mapIndexedNotNull { vIdx, (lineNum, lineText) ->
                val count = countQueryMatches(lineText, searchQuery, caseSensitiveSearch, wholeWordSearch)
                if (count > 0) {
                    DocumentSearchHit(
                        unitIndex = vIdx,
                        subIndex = lineNum,
                        locationBadge = "Line $lineNum",
                        snippetText = lineText.trim(),
                        matchCountInUnit = count
                    )
                } else null
            }
        }
    }

    val totalMatchOccurrences = remember(searchHits) {
        searchHits.sumOf { it.matchCountInUnit }
    }

    LaunchedEffect(searchQuery, caseSensitiveSearch, wholeWordSearch, filterOnlyMatches) {
        activeHitIndex = 0
    }

    val activeSearchHit = searchHits.getOrNull(activeHitIndex.coerceIn(0, (searchHits.size - 1).coerceAtLeast(0)))

    LaunchedEffect(activeHitIndex, searchHits.size) {
        if (activeSearchHit != null && !isEditing) {
            runCatching {
                listState.animateScrollToItem(activeSearchHit.unitIndex.coerceAtLeast(0))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CommonOfficeTopBar(
                document = document,
                subtitle = "${parsed.wordCount} words • ${parsed.lines.size} lines • ~${parsed.estimatedMinutes} min read",
                showRuler = settings.showReadingRuler,
                isTtsActive = ttsState.isPlaying || showTtsBar,
                isSearchActive = showSearchBar || searchQuery.isNotBlank(),
                viewModel = viewModel,
                onBack = onBack,
                onToggleSearch = { showSearchBar = !showSearchBar },
                onToggleRuler = { viewModel.toggleReadingRuler() },
                onToggleTts = {
                    showTtsBar = true
                    viewModel.toggleTtsPlayPause()
                },
                onExportPdf = { viewModel.exportActiveDocumentToPdf() },
                onOpenBookmarks = { showBookmarksSheet = true },
                onOpenAccessibility = { showAccessibilitySheet = true }
            )

            // Reading Filter Bar (Sepia, High Contrast, Blue Light Filter, etc.)
            DocumentViewerFilterBar(
                settings = settings,
                onSelectColorMode = { viewModel.setReadingColorMode(it) },
                onToggleBlueLight = { viewModel.toggleBlueLightFilter() },
                onBlueLightIntensityChange = { viewModel.setBlueLightIntensity(it) },
                onOpenFullSheet = { showAccessibilitySheet = true }
            )

            // Secondary Mode Bar (Markdown Preview vs Raw Line Numbers vs Offline Edit)
            Surface(color = Slate900, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showRawLineNumbers && !isEditing,
                            onClick = {
                                showRawLineNumbers = false
                                isEditing = false
                            },
                            label = { Text("Formatted View") }
                        )
                        FilterChip(
                            selected = showRawLineNumbers && !isEditing,
                            onClick = {
                                showRawLineNumbers = true
                                isEditing = false
                            },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Line Numbers") }
                        )
                    }

                    if (isEditing) {
                        Button(
                            onClick = {
                                viewModel.saveEditedTextDocument(document, editedText)
                                isEditing = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Slate950)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Offline")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Text Offline", tint = Amber400)
                        }
                    }
                }
            }

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
                    filterOnlyMatches = filterOnlyMatches,
                    onToggleFilterOnlyMatches = { filterOnlyMatches = !filterOnlyMatches },
                    onSelectHitIndex = { idx ->
                        activeHitIndex = idx.coerceIn(0, (searchHits.size - 1).coerceAtLeast(0))
                    },
                    onClose = {
                        showSearchBar = false
                        searchQuery = ""
                        filterOnlyMatches = false
                    },
                    placeholderText = "Find strings in text / markdown…"
                )
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = fg,
                        fontSize = (14f * settings.fontScale).sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (showRawLineNumbers) 4.dp else 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(visibleLines) { vIdx, (lineNum, lineText) ->
                        val isFocusedLine = activeSearchHit?.unitIndex == vIdx
                        if (showRawLineNumbers) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isFocusedLine) {
                                            Modifier
                                                .background(Cyan400.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                        } else Modifier
                                    )
                            ) {
                                Text(
                                    text = String.format("%3d", lineNum),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isFocusedLine) Cyan400 else Color.Gray,
                                        fontWeight = if (isFocusedLine) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    modifier = Modifier.width(38.dp)
                                )
                                Text(
                                    text = buildHighlightedAnnotatedString(
                                        text = lineText,
                                        query = searchQuery,
                                        caseSensitive = caseSensitiveSearch,
                                        wholeWord = wholeWordSearch,
                                        isFocusedTarget = isFocusedLine
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = fg,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = (13.5f * settings.fontScale).sp,
                                        lineHeight = (19f * settings.fontScale).sp
                                    )
                                )
                            }
                        } else {
                            // Markdown-aware rich rendering with Compose SpanStyle search highlighting
                            when {
                                lineText.startsWith("# ") -> {
                                    Text(
                                        text = buildHighlightedAnnotatedString(
                                            text = lineText.removePrefix("# "),
                                            query = searchQuery,
                                            caseSensitive = caseSensitiveSearch,
                                            wholeWord = wholeWordSearch,
                                            isFocusedTarget = isFocusedLine
                                        ),
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            color = fg,
                                            fontSize = (24f * settings.fontScale).sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                lineText.startsWith("## ") -> {
                                    Text(
                                        text = buildHighlightedAnnotatedString(
                                            text = lineText.removePrefix("## "),
                                            query = searchQuery,
                                            caseSensitive = caseSensitiveSearch,
                                            wholeWord = wholeWordSearch,
                                            isFocusedTarget = isFocusedLine
                                        ),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = Amber400,
                                            fontSize = (19f * settings.fontScale).sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                lineText.startsWith("> ") -> {
                                    Surface(
                                        color = Cyan400.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = buildHighlightedAnnotatedString(
                                                text = lineText.removePrefix("> "),
                                                query = searchQuery,
                                                caseSensitive = caseSensitiveSearch,
                                                wholeWord = wholeWordSearch,
                                                isFocusedTarget = isFocusedLine
                                            ),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = fg,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = (15f * settings.fontScale).sp
                                            ),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                lineText.isNotBlank() -> {
                                    Text(
                                        text = buildHighlightedAnnotatedString(
                                            text = lineText,
                                            query = searchQuery,
                                            caseSensitive = caseSensitiveSearch,
                                            wholeWord = wholeWordSearch,
                                            isFocusedTarget = isFocusedLine
                                        ),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = fg,
                                            fontFamily = fontFamily,
                                            fontSize = (15.5f * settings.fontScale).sp,
                                            lineHeight = (15.5f * settings.fontScale * settings.lineSpacingMultiplier).sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                    val nextRate = if (ttsState.speechRate >= 1.75f) 0.75f else ttsState.speechRate + 0.25f
                    viewModel.setTtsRateAndPitch(nextRate, ttsState.pitch)
                },
                onOpenTtsStudio = { showTtsStudioSheet = true }
            )
        }

        ReadingFilterOverlay(settings = settings)

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
            currentUnitIndex = 0,
            bookmarks = bookmarks,
            annotations = annotations,
            onDismiss = { showBookmarksSheet = false },
            onAddBookmark = { label ->
                viewModel.addBookmarkForActiveDoc(0, label.ifBlank { "Text Section" }, document.title)
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onAddAnnotation = { text, hex -> viewModel.addAnnotationForActiveDoc(0, text, hex) },
            onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
            onJumpToUnit = {}
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
}

@Composable
private fun CommonOfficeTopBar(
    document: DocumentEntity,
    subtitle: String,
    showRuler: Boolean,
    isTtsActive: Boolean,
    isSearchActive: Boolean = false,
    viewModel: OmniDocViewModel? = null,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleRuler: () -> Unit,
    onToggleTts: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    var showUBlockSheet by remember { mutableStateOf(false) }
    val uboState = viewModel?.uBlockState?.collectAsStateWithLifecycle()?.value

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
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(color = Cyan400),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (uboState != null) {
                UBlockOriginShieldPill(
                    uboState = uboState,
                    onClick = { showUBlockSheet = true },
                    modifier = Modifier.padding(end = 2.dp)
                )
            }

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.testTag("viewer_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search in Document",
                    tint = if (isSearchActive) Amber400 else Color.White
                )
            }

            IconButton(onClick = onToggleRuler) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = "Reading Focus Ruler",
                    tint = if (showRuler) Amber400 else Color.White
                )
            }

            IconButton(
                onClick = onToggleTts,
                modifier = Modifier.testTag("viewer_tts_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Offline Read Aloud TTS",
                    tint = if (isTtsActive) Amber400 else Color.White
                )
            }

            IconButton(
                onClick = onExportPdf,
                modifier = Modifier.testTag("export_to_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "Export to Offline PDF",
                    tint = Amber400
                )
            }

            IconButton(onClick = onOpenBookmarks) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Bookmarks & Study Notes",
                    tint = Color.White
                )
            }

            IconButton(onClick = onOpenAccessibility) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Accessibility Settings",
                    tint = Color.White
                )
            }
        }
    }

    if (showUBlockSheet && viewModel != null && uboState != null) {
        UBlockOriginDashboardSheet(
            uboState = uboState,
            currentContextTitle = "Inbuilt ${document.docFormat.label} Reader",
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
