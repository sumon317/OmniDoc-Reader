package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccessibilitySettings
import com.example.data.AnnotationEntity
import com.example.data.BookmarkEntity
import com.example.data.ComicArchiveInfo
import com.example.data.ComicReadingMode
import com.example.data.DocFormat
import com.example.data.DocxBlock
import com.example.data.DocumentEntity
import com.example.data.OmniDocDatabase
import com.example.data.OmniDocRepository
import com.example.data.ParsedDocxDocument
import com.example.data.ParsedPptxDeck
import com.example.data.ParsedTextDocument
import com.example.data.ParsedXlsxWorkbook
import com.example.data.ReadingColorMode
import com.example.engine.ComicPortalCategory
import com.example.engine.DownloadedModelStatus
import com.example.engine.EverythingMoeComicHubEngine
import com.example.engine.EverythingMoePortalSource
import com.example.engine.HuggingFaceTtsModelInfo
import com.example.engine.HuggingFaceTtsModelManager
import com.example.engine.OfficeOpenXmlParser
import com.example.engine.OfflineSampleGenerator
import com.example.engine.OfflineTtsController
import com.example.engine.OnlineComicChapter
import com.example.engine.OnlineComicFilter
import com.example.engine.OnlineComicSeries
import com.example.engine.OnlineDownloadProgress
import com.example.engine.PdfAndComicEngine
import com.example.engine.TtsPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption(val label: String) {
    RECENT("Recently Opened"),
    NAME_ASC("Title (A → Z)"),
    SIZE_DESC("Largest Size"),
    PROGRESS("Reading Progress")
}

sealed class ActiveViewerState {
    data object Idle : ActiveViewerState()
    data class Loading(val document: DocumentEntity) : ActiveViewerState()
    data class PdfReady(
        val document: DocumentEntity,
        val pageCount: Int,
        val currentPage: Int
    ) : ActiveViewerState()
    data class DocxReady(
        val document: DocumentEntity,
        val parsed: ParsedDocxDocument
    ) : ActiveViewerState()
    data class XlsxReady(
        val document: DocumentEntity,
        val parsed: ParsedXlsxWorkbook
    ) : ActiveViewerState()
    data class PptxReady(
        val document: DocumentEntity,
        val parsed: ParsedPptxDeck
    ) : ActiveViewerState()
    data class TextReady(
        val document: DocumentEntity,
        val parsed: ParsedTextDocument
    ) : ActiveViewerState()
    data class ComicReady(
        val document: DocumentEntity,
        val archiveInfo: ComicArchiveInfo,
        val currentPage: Int
    ) : ActiveViewerState()
    data class Error(val document: DocumentEntity, val message: String) : ActiveViewerState()
}

class OmniDocViewModel(application: Application) : AndroidViewModel(application) {

    private val database = OmniDocDatabase.getInstance(application)
    private val repository = OmniDocRepository(application, database.omniDocDao())
    private val ttsController = OfflineTtsController(application)
    private val hfModelManager = HuggingFaceTtsModelManager(application)
    private val comicHubEngine = EverythingMoeComicHubEngine(application)

    val ttsState: StateFlow<TtsPlaybackState> = ttsController.state
    val curatedHfModels: List<HuggingFaceTtsModelInfo> = hfModelManager.curatedMobileModels
    val hfModelStatuses: StateFlow<Map<String, DownloadedModelStatus>> = hfModelManager.modelStatuses
    val hfHubSearchResults: StateFlow<List<HuggingFaceTtsModelInfo>> = hfModelManager.hubSearchResults
    val isSearchingHfHub: StateFlow<Boolean> = hfModelManager.isSearchingHub

    // Online Manga / Manhwa / Comics / EverythingMoe Hub State
    val curatedComicPortals: List<EverythingMoePortalSource> = comicHubEngine.curatedPortals
    val onlineSeriesList: StateFlow<List<OnlineComicSeries>> = comicHubEngine.seriesList
    val isLoadingOnlineSeries: StateFlow<Boolean> = comicHubEngine.isLoadingSeries
    val onlineDownloadProgress: StateFlow<OnlineDownloadProgress> = comicHubEngine.downloadProgress

    private val _activeOnlineFilter = MutableStateFlow(OnlineComicFilter.TRENDING_MANGA)
    val activeOnlineFilter: StateFlow<OnlineComicFilter> = _activeOnlineFilter.asStateFlow()

    private val _activePortalBrowser = MutableStateFlow<EverythingMoePortalSource?>(null)
    val activePortalBrowser: StateFlow<EverythingMoePortalSource?> = _activePortalBrowser.asStateFlow()

    private val _selectedOnlineSeries = MutableStateFlow<OnlineComicSeries?>(null)
    val selectedOnlineSeries: StateFlow<OnlineComicSeries?> = _selectedOnlineSeries.asStateFlow()

    private val _selectedSeriesChapters = MutableStateFlow<List<OnlineComicChapter>>(emptyList())
    val selectedSeriesChapters: StateFlow<List<OnlineComicChapter>> = _selectedSeriesChapters.asStateFlow()

    private val _isLoadingChapters = MutableStateFlow(false)
    val isLoadingChapters: StateFlow<Boolean> = _isLoadingChapters.asStateFlow()

    private val _activeOnlineChapterReader = MutableStateFlow<Pair<OnlineComicSeries, OnlineComicChapter>?>(null)
    val activeOnlineChapterReader: StateFlow<Pair<OnlineComicSeries, OnlineComicChapter>?> = _activeOnlineChapterReader.asStateFlow()

    private val _activeOnlineChapterPages = MutableStateFlow<List<String>>(emptyList())
    val activeOnlineChapterPages: StateFlow<List<String>> = _activeOnlineChapterPages.asStateFlow()

    private val _isLoadingChapterPages = MutableStateFlow(false)
    val isLoadingChapterPages: StateFlow<Boolean> = _isLoadingChapterPages.asStateFlow()

    val accessibilitySettings: StateFlow<AccessibilitySettings> = repository.accessibilitySettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AccessibilitySettings()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFormat = MutableStateFlow<DocFormat?>(null)
    val selectedFormat: StateFlow<DocFormat?> = _selectedFormat.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENT)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        repository.allDocuments,
        _searchQuery,
        _selectedFormat,
        _sortOption,
        _onlyFavorites
    ) { docs, query, format, sort, favOnly ->
        docs.filter { doc ->
            val matchesQuery = query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.fileName.contains(query, ignoreCase = true) ||
                doc.tags.contains(query, ignoreCase = true) ||
                doc.summarySnippet.contains(query, ignoreCase = true)
            val matchesFormat = format == null || doc.docFormat == format
            val matchesFav = !favOnly || doc.isFavorite
            matchesQuery && matchesFormat && matchesFav
        }.let { list ->
            when (sort) {
                SortOption.RECENT -> list.sortedByDescending { it.lastOpenedAt }
                SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
                SortOption.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
                SortOption.PROGRESS -> list.sortedByDescending { it.progressPercent }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBookmarks: StateFlow<List<BookmarkEntity>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAnnotations: StateFlow<List<AnnotationEntity>> = repository.allAnnotations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeViewerState = MutableStateFlow<ActiveViewerState>(ActiveViewerState.Idle)
    val activeViewerState: StateFlow<ActiveViewerState> = _activeViewerState.asStateFlow()

    private val _activeDocumentId = MutableStateFlow<Long?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeDocumentBookmarks: StateFlow<List<BookmarkEntity>> = _activeDocumentId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getBookmarksForDocument(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeDocumentAnnotations: StateFlow<List<AnnotationEntity>> = _activeDocumentId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getAnnotationsForDocument(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            OfflineSampleGenerator.ensureSeeded(getApplication(), database.omniDocDao())
        }
        viewModelScope.launch {
            comicHubEngine.loadOnlineCatalog(OnlineComicFilter.TRENDING_MANGA)
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setFormatFilter(format: DocFormat?) {
        _selectedFormat.value = if (_selectedFormat.value == format) null else format
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleOnlyFavorites() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun clearStatusMessage() {
        _statusBannerMessage.value = null
    }

    fun showStatusMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    fun openDocument(doc: DocumentEntity, initialUnitOverride: Int? = null) {
        viewModelScope.launch {
            _activeDocumentId.value = doc.id
            _activeViewerState.value = ActiveViewerState.Loading(doc)
            val file = File(doc.localFilePath)
            if (!file.exists()) {
                _activeViewerState.value = ActiveViewerState.Error(
                    doc,
                    "File not found at ${doc.localFilePath}."
                )
                return@launch
            }

            val startUnit = initialUnitOverride ?: doc.currentUnit
            val settings = accessibilitySettings.value

            when (doc.docFormat) {
                DocFormat.PDF -> {
                    val count = PdfAndComicEngine.getPdfPageCount(file).coerceAtLeast(1)
                    val safePage = startUnit.coerceIn(0, count - 1)
                    repository.updateReadingProgress(doc.id, safePage, count)
                    val pdfText = PdfAndComicEngine.extractPdfTextForTts(
                        file = file,
                        fallbackTitle = doc.title,
                        fallbackSummary = doc.summarySnippet
                    )
                    ttsController.prepareDocumentText(
                        rawText = pdfText,
                        rate = settings.ttsSpeechRate,
                        pitch = settings.ttsPitch
                    )
                    _activeViewerState.value = ActiveViewerState.PdfReady(
                        document = doc.copy(totalUnits = count, currentUnit = safePage),
                        pageCount = count,
                        currentPage = safePage
                    )
                }
                DocFormat.DOCX -> {
                    val parsed = OfficeOpenXmlParser.parseDocx(file)
                    val totalBlocks = parsed.blocks.size.coerceAtLeast(1)
                    repository.updateReadingProgress(doc.id, startUnit.coerceIn(0, totalBlocks - 1), totalBlocks)
                    ttsController.prepareDocumentText(
                        rawText = parsed.plainTextForTts,
                        rate = settings.ttsSpeechRate,
                        pitch = settings.ttsPitch
                    )
                    _activeViewerState.value = ActiveViewerState.DocxReady(doc, parsed)
                }
                DocFormat.XLSX -> {
                    val parsed = OfficeOpenXmlParser.parseXlsxOrCsv(file)
                    val sheetCount = parsed.sheets.size.coerceAtLeast(1)
                    repository.updateReadingProgress(doc.id, startUnit.coerceIn(0, sheetCount - 1), sheetCount)
                    ttsController.prepareDocumentText(
                        rawText = parsed.plainTextForTts,
                        rate = settings.ttsSpeechRate,
                        pitch = settings.ttsPitch
                    )
                    _activeViewerState.value = ActiveViewerState.XlsxReady(doc, parsed)
                }
                DocFormat.PPTX -> {
                    val parsed = OfficeOpenXmlParser.parsePptx(file)
                    val slideCount = parsed.slides.size.coerceAtLeast(1)
                    repository.updateReadingProgress(doc.id, startUnit.coerceIn(0, slideCount - 1), slideCount)
                    ttsController.prepareDocumentText(
                        rawText = parsed.plainTextForTts,
                        rate = settings.ttsSpeechRate,
                        pitch = settings.ttsPitch
                    )
                    _activeViewerState.value = ActiveViewerState.PptxReady(doc, parsed)
                }
                DocFormat.TXT -> {
                    val parsed = OfficeOpenXmlParser.parseTextFile(file)
                    val lineCount = parsed.lines.size.coerceAtLeast(1)
                    repository.updateReadingProgress(doc.id, startUnit.coerceIn(0, lineCount - 1), lineCount)
                    ttsController.prepareDocumentText(
                        rawText = parsed.content,
                        rate = settings.ttsSpeechRate,
                        pitch = settings.ttsPitch
                    )
                    _activeViewerState.value = ActiveViewerState.TextReady(doc, parsed)
                }
                DocFormat.CBZ, DocFormat.CBR -> {
                    val info = PdfAndComicEngine.inspectComicArchive(file, doc.docFormat)
                    val pageCount = info.pageCount.coerceAtLeast(1)
                    val safePage = startUnit.coerceIn(0, pageCount - 1)
                    repository.updateReadingProgress(doc.id, safePage, pageCount)
                    ttsController.prepareDocumentText(
                        rawText = "${info.title}. Written by ${info.writer}. ${info.summary}. Comic archive with $pageCount pages.",
                        rate = settings.ttsSpeechRate,
                        pitch = settings.ttsPitch
                    )
                    _activeViewerState.value = ActiveViewerState.ComicReady(
                        document = doc.copy(totalUnits = pageCount, currentUnit = safePage),
                        archiveInfo = info,
                        currentPage = safePage
                    )
                }
            }
        }
    }

    fun openDocumentByIdAndUnit(docId: Long, unitIndex: Int) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(docId) ?: return@launch
            openDocument(doc, initialUnitOverride = unitIndex)
        }
    }

    fun closeViewer() {
        ttsController.stop()
        _activeDocumentId.value = null
        _activeViewerState.value = ActiveViewerState.Idle
    }

    fun updateUnitProgress(docId: Long, currentUnit: Int, totalUnits: Int) {
        viewModelScope.launch {
            repository.updateReadingProgress(docId, currentUnit, totalUnits)
            val current = _activeViewerState.value
            if (current is ActiveViewerState.PdfReady && current.document.id == docId) {
                _activeViewerState.value = current.copy(currentPage = currentUnit)
            } else if (current is ActiveViewerState.ComicReady && current.document.id == docId) {
                _activeViewerState.value = current.copy(currentPage = currentUnit)
            }
        }
    }

    suspend fun renderPdfPageBitmap(file: File, pageIndex: Int): Bitmap? {
        val settings = accessibilitySettings.value
        val invert = settings.invertPdfColors ||
            settings.colorMode == ReadingColorMode.AMOLED_DARK ||
            settings.colorMode == ReadingColorMode.HIGH_CONTRAST
        val warm = settings.colorMode == ReadingColorMode.WARM_SEPIA ||
            settings.colorMode == ReadingColorMode.BLUE_LIGHT_SHIELD ||
            settings.blueLightFilterEnabled
        return PdfAndComicEngine.renderPdfPage(
            file = file,
            pageIndex = pageIndex,
            scaleFactor = 2.0f,
            invertColors = invert,
            warmSepia = warm
        )
    }

    suspend fun renderComicPageBitmap(archiveInfo: ComicArchiveInfo, pageIndex: Int): Bitmap? {
        val warm = accessibilitySettings.value.comicWarmFilter ||
            accessibilitySettings.value.colorMode == ReadingColorMode.WARM_SEPIA ||
            accessibilitySettings.value.colorMode == ReadingColorMode.BLUE_LIGHT_SHIELD ||
            accessibilitySettings.value.blueLightFilterEnabled
        return PdfAndComicEngine.renderComicPage(archiveInfo, pageIndex, warmFilter = warm)
    }

    fun importExternalFile(uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importExternalUri(uri)
            if (imported != null) {
                _statusBannerMessage.value = "Imported '${imported.title}' for offline access"
                openDocument(imported)
            } else {
                _statusBannerMessage.value = "Could not open the selected file"
            }
        }
    }

    fun createNewOfflineDocument(title: String, content: String, asPdf: Boolean) {
        viewModelScope.launch {
            val dir = File(getApplication<Application>().filesDir, "sample_library").apply { mkdirs() }
            val cleanTitle = title.trim().ifEmpty { "Untitled Field Note" }
            val sanitized = cleanTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")

            if (asPdf) {
                val paragraphs = content.lines()
                val pdfFile = PdfAndComicEngine.exportLinesToPdf(
                    outputDir = dir,
                    fileNamePrefix = sanitized,
                    title = cleanTitle,
                    subtitle = "Created Offline in OmniDoc Studio",
                    paragraphs = paragraphs
                )
                val pageCount = PdfAndComicEngine.getPdfPageCount(pdfFile).coerceAtLeast(1)
                val entity = DocumentEntity(
                    title = cleanTitle,
                    fileName = pdfFile.name,
                    localFilePath = pdfFile.absolutePath,
                    format = DocFormat.PDF.name,
                    fileSizeBytes = pdfFile.length(),
                    totalUnits = pageCount,
                    currentUnit = 0,
                    progressPercent = 100,
                    isFavorite = false,
                    isPinnedOffline = true,
                    tags = "Created,PDF,Offline",
                    summarySnippet = content.take(120).replace('\n', ' ')
                )
                val id = repository.insertDocument(entity)
                _statusBannerMessage.value = "Created offline PDF '${entity.title}'"
                openDocument(entity.copy(id = id))
            } else {
                val mdFile = File(dir, "${sanitized}.md")
                mdFile.writeText(content)
                val lineCount = content.lines().size.coerceAtLeast(1)
                val entity = DocumentEntity(
                    title = cleanTitle,
                    fileName = mdFile.name,
                    localFilePath = mdFile.absolutePath,
                    format = DocFormat.TXT.name,
                    fileSizeBytes = mdFile.length(),
                    totalUnits = lineCount,
                    currentUnit = 0,
                    progressPercent = 100,
                    isFavorite = false,
                    isPinnedOffline = true,
                    tags = "Created,Markdown,Offline",
                    summarySnippet = content.take(120).replace('\n', ' ')
                )
                val id = repository.insertDocument(entity)
                _statusBannerMessage.value = "Saved offline Markdown note '${entity.title}'"
                openDocument(entity.copy(id = id))
            }
        }
    }

    fun saveEditedTextDocument(doc: DocumentEntity, updatedContent: String) {
        viewModelScope.launch {
            val file = File(doc.localFilePath)
            runCatching {
                file.writeText(updatedContent)
            }
            val parsed = OfficeOpenXmlParser.parseTextFile(file)
            repository.updateDocument(
                doc.copy(
                    fileSizeBytes = file.length(),
                    totalUnits = parsed.lines.size.coerceAtLeast(1),
                    summarySnippet = updatedContent.take(120).replace('\n', ' '),
                    lastOpenedAt = System.currentTimeMillis()
                )
            )
            ttsController.prepareDocumentText(
                rawText = parsed.content,
                rate = accessibilitySettings.value.ttsSpeechRate,
                pitch = accessibilitySettings.value.ttsPitch
            )
            _activeViewerState.value = ActiveViewerState.TextReady(doc, parsed)
            _statusBannerMessage.value = "Saved changes to ${doc.fileName}"
        }
    }

    fun exportActiveDocumentToPdf() {
        val state = _activeViewerState.value
        viewModelScope.launch {
            val dir = File(getApplication<Application>().filesDir, "sample_library").apply { mkdirs() }
            val exportedFile: File? = when (state) {
                is ActiveViewerState.DocxReady -> {
                    val lines = state.parsed.blocks.map { block ->
                        when (block) {
                            is DocxBlock.Heading -> "## ${block.text}"
                            is DocxBlock.Paragraph -> block.text
                            is DocxBlock.BulletItem -> "• ${block.text}"
                            is DocxBlock.CalloutBox -> "[${block.title}] ${block.body}"
                            is DocxBlock.TableBlock ->
                                (listOf(block.headers) + block.rows).joinToString("\n") { it.joinToString(" | ") }
                        }
                    }
                    PdfAndComicEngine.exportLinesToPdf(
                        outputDir = dir,
                        fileNamePrefix = state.document.title,
                        title = state.document.title,
                        subtitle = "Exported from DOCX • ${state.parsed.wordCount} words",
                        paragraphs = lines
                    )
                }
                is ActiveViewerState.TextReady -> {
                    PdfAndComicEngine.exportLinesToPdf(
                        outputDir = dir,
                        fileNamePrefix = state.document.title,
                        title = state.document.title,
                        subtitle = "Exported from Text/Markdown • ${state.parsed.wordCount} words",
                        paragraphs = state.parsed.lines
                    )
                }
                is ActiveViewerState.XlsxReady -> {
                    val lines = state.parsed.sheets.flatMap { sheet ->
                        listOf("WORKSHEET: ${sheet.name}", sheet.headers.joinToString("  |  ")) +
                            sheet.rows.map { it.joinToString("  |  ") } + listOf("")
                    }
                    PdfAndComicEngine.exportLinesToPdf(
                        outputDir = dir,
                        fileNamePrefix = state.document.title,
                        title = state.document.title,
                        subtitle = "Exported from XLSX Workbook • ${state.parsed.sheets.size} sheets",
                        paragraphs = lines
                    )
                }
                is ActiveViewerState.PptxReady -> {
                    val lines = state.parsed.slides.flatMap { s ->
                        listOf("SLIDE ${s.slideNumber}: ${s.title}", s.subtitle) +
                            s.bullets.map { "• $it" } +
                            listOf("Notes: ${s.speakerNotes}", "")
                    }
                    PdfAndComicEngine.exportLinesToPdf(
                        outputDir = dir,
                        fileNamePrefix = state.document.title,
                        title = state.document.title,
                        subtitle = "Exported from PPTX Deck • ${state.parsed.slides.size} slides",
                        paragraphs = lines
                    )
                }
                else -> null
            }

            if (exportedFile != null) {
                val pages = PdfAndComicEngine.getPdfPageCount(exportedFile).coerceAtLeast(1)
                val newDoc = DocumentEntity(
                    title = exportedFile.nameWithoutExtension.replace('_', ' '),
                    fileName = exportedFile.name,
                    localFilePath = exportedFile.absolutePath,
                    format = DocFormat.PDF.name,
                    fileSizeBytes = exportedFile.length(),
                    totalUnits = pages,
                    currentUnit = 0,
                    progressPercent = 0,
                    isFavorite = false,
                    isPinnedOffline = true,
                    tags = "Exported,PDF,Offline",
                    summarySnippet = "Offline PDF export created by OmniDoc Studio ($pages pages)."
                )
                repository.insertDocument(newDoc)
                _statusBannerMessage.value = "Exported '${exportedFile.name}' to offline library!"
            }
        }
    }

    fun shareDocumentOffline(doc: DocumentEntity) {
        runCatching {
            val file = File(doc.localFilePath)
            if (!file.exists()) return
            val app = getApplication<Application>()
            val uri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${doc.title}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(doc)
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _statusBannerMessage.value = "Removed '${doc.title}' from library"
        }
    }

    fun addBookmarkForActiveDoc(unitIndex: Int, label: String, excerpt: String) {
        val doc = currentDocumentOrNull() ?: return
        viewModelScope.launch {
            repository.addBookmark(
                doc = doc,
                unitIndex = unitIndex,
                label = label.ifBlank { "Page ${unitIndex + 1} Bookmark" },
                excerpt = excerpt.ifBlank { "${doc.title} • Section ${unitIndex + 1}" }
            )
            _statusBannerMessage.value = "Bookmarked ${doc.title} (#${unitIndex + 1})"
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    fun addAnnotationForActiveDoc(unitIndex: Int, noteText: String, colorHex: String) {
        val doc = currentDocumentOrNull() ?: return
        if (noteText.isBlank()) return
        viewModelScope.launch {
            repository.addAnnotation(doc, unitIndex, noteText.trim(), colorHex)
            _statusBannerMessage.value = "Saved study note to ${doc.title}"
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch {
            repository.deleteAnnotation(id)
        }
    }

    private fun currentDocumentOrNull(): DocumentEntity? {
        return when (val s = _activeViewerState.value) {
            is ActiveViewerState.PdfReady -> s.document
            is ActiveViewerState.DocxReady -> s.document
            is ActiveViewerState.XlsxReady -> s.document
            is ActiveViewerState.PptxReady -> s.document
            is ActiveViewerState.TextReady -> s.document
            is ActiveViewerState.ComicReady -> s.document
            else -> null
        }
    }

    // TTS & Hugging Face Model Controls
    fun toggleTtsPlayPause() = ttsController.togglePlayPause()
    fun stopTts() = ttsController.stop()
    fun nextTtsSentence() = ttsController.skipNext()
    fun prevTtsSentence() = ttsController.skipPrevious()
    fun jumpToTtsSentence(index: Int) = ttsController.jumpToSentence(index)

    fun downloadHfModel(modelInfo: HuggingFaceTtsModelInfo, fullOnnxWeights: Boolean) {
        val job = viewModelScope.launch {
            hfModelManager.downloadModelFromHuggingFace(
                modelInfo = modelInfo,
                downloadFullOnnxWeights = fullOnnxWeights
            ) { success, msg ->
                if (success) {
                    ttsController.applyHuggingFaceModelProfile(modelInfo)
                }
                _statusBannerMessage.value = msg
            }
        }
        hfModelManager.registerDownloadJob(modelInfo.id, job)
    }

    fun cancelHfModelDownload(modelId: String) {
        hfModelManager.cancelDownload(modelId)
        _statusBannerMessage.value = "Cancelled Hugging Face download"
    }

    fun deleteHfModel(modelId: String) {
        if (ttsState.value.activeHfModelId == modelId) {
            ttsController.applyHuggingFaceModelProfile(null)
        }
        hfModelManager.deleteDownloadedModel(modelId)
        _statusBannerMessage.value = "Removed downloaded TTS model from device"
    }

    fun activateHfModel(modelInfo: HuggingFaceTtsModelInfo) {
        ttsController.applyHuggingFaceModelProfile(modelInfo)
        _statusBannerMessage.value = "Activated ${modelInfo.displayName} for TTS"
    }

    fun testHfModelVoice(modelInfo: HuggingFaceTtsModelInfo) {
        ttsController.applyHuggingFaceModelProfile(modelInfo)
        ttsController.speakDemoSample(
            "Hello from OmniDoc Reader! You are listening to the ${modelInfo.displayName} profile from Hugging Face, running offline on your Android phone."
        )
        _statusBannerMessage.value = "Playing sample with ${modelInfo.displayName}"
    }

    fun testActiveVoiceSample() {
        val label = ttsState.value.activeVoiceLabel
        ttsController.speakDemoSample(
            "OmniDoc Reader Text-to-Speech engine is ready. Active voice profile: $label. Open any PDF, Word, Excel, PowerPoint, or Markdown document to listen offline."
        )
        _statusBannerMessage.value = "Speaking test sample ($label)"
    }

    fun selectAndroidSystemVoice(voiceName: String) {
        ttsController.selectAndroidSystemVoice(voiceName)
        _statusBannerMessage.value = "Switched to Android System Voice: $voiceName"
    }

    fun searchHuggingFaceModels(query: String) {
        viewModelScope.launch {
            hfModelManager.searchHuggingFaceHub(query)
        }
    }

    fun exportActiveDocToWavAudio() {
        val doc = currentDocumentOrNull() ?: return
        ttsController.synthesizeCurrentDocToWavFile(doc.title) { wavFile ->
            if (wavFile != null && wavFile.exists()) {
                _statusBannerMessage.value = "Synthesized offline audio file: ${wavFile.name} (${OmniDocRepository.formatFileSize(wavFile.length())})"
            } else {
                _statusBannerMessage.value = "Could not synthesize WAV audio on this device"
            }
        }
    }

    // Accessibility Settings Controls
    fun setDarkThemeApp(dark: Boolean) {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(darkThemeApp = dark) }
        }
    }

    fun setReadingColorMode(mode: ReadingColorMode) {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(colorMode = mode) }
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(fontScale = scale.coerceIn(0.75f, 1.75f)) }
        }
    }

    fun setLineSpacing(multiplier: Float) {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(lineSpacingMultiplier = multiplier.coerceIn(1.1f, 2.2f)) }
        }
    }

    fun toggleDyslexiaSpacing() {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(dyslexiaFriendlySpacing = !it.dyslexiaFriendlySpacing) }
        }
    }

    fun toggleMonospaceText() {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(useMonospaceForText = !it.useMonospaceForText) }
        }
    }

    fun toggleReadingRuler() {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(showReadingRuler = !it.showReadingRuler) }
        }
    }

    fun updateRulerPosition(fraction: Float) {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(rulerPositionFraction = fraction.coerceIn(0.1f, 0.88f)) }
        }
    }

    fun setTtsRateAndPitch(rate: Float, pitch: Float) {
        ttsController.setRateAndPitch(rate, pitch)
        viewModelScope.launch {
            repository.updateAccessibility {
                it.copy(ttsSpeechRate = rate.coerceIn(0.5f, 2.0f), ttsPitch = pitch.coerceIn(0.6f, 1.5f))
            }
        }
    }

    fun setComicReadingMode(mode: ComicReadingMode) {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(comicReadingMode = mode) }
        }
    }

    fun toggleInvertPdfColors() {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(invertPdfColors = !it.invertPdfColors) }
        }
    }

    fun toggleComicWarmFilter() {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(comicWarmFilter = !it.comicWarmFilter) }
        }
    }

    fun toggleBlueLightFilter() {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(blueLightFilterEnabled = !it.blueLightFilterEnabled) }
        }
    }

    fun setBlueLightIntensity(intensity: Float) {
        viewModelScope.launch {
            repository.updateAccessibility {
                it.copy(
                    blueLightIntensity = intensity.coerceIn(0.10f, 0.75f),
                    blueLightFilterEnabled = true
                )
            }
        }
    }

    fun toggleViewerFilterBar() {
        viewModelScope.launch {
            repository.updateAccessibility { it.copy(showViewerFilterBar = !it.showViewerFilterBar) }
        }
    }

    // Online Manga / Manhwa / Comics / EverythingMoe Portal Actions
    fun loadOnlineComics(filter: OnlineComicFilter, searchQuery: String = "") {
        _activeOnlineFilter.value = filter
        viewModelScope.launch {
            comicHubEngine.loadOnlineCatalog(filter = filter, searchQuery = searchQuery)
        }
    }

    fun openEverythingMoePortal(portal: EverythingMoePortalSource) {
        _activePortalBrowser.value = portal
    }

    fun openCustomComicUrlInBrowser(url: String, title: String = "Online Comic Portal") {
        val normalized = if (url.startsWith("http")) url.trim() else "https://${url.trim()}"
        _activePortalBrowser.value = EverythingMoePortalSource(
            id = "custom_portal",
            name = title,
            url = normalized,
            category = ComicPortalCategory.ALL,
            rankBadge = "WEB PORTAL",
            tags = listOf("Online Reader"),
            description = normalized
        )
    }

    fun closeEverythingMoePortal() {
        _activePortalBrowser.value = null
    }

    fun selectOnlineSeries(series: OnlineComicSeries) {
        _selectedOnlineSeries.value = series
        _isLoadingChapters.value = true
        viewModelScope.launch {
            val chList = comicHubEngine.fetchChaptersForSeries(series)
            _selectedSeriesChapters.value = chList
            _isLoadingChapters.value = false
        }
    }

    fun dismissOnlineSeriesSheet() {
        _selectedOnlineSeries.value = null
        _selectedSeriesChapters.value = emptyList()
    }

    fun openOnlineChapterForReading(series: OnlineComicSeries, chapter: OnlineComicChapter) {
        _activeOnlineChapterReader.value = series to chapter
        _isLoadingChapterPages.value = true
        viewModelScope.launch {
            val pages = comicHubEngine.fetchChapterPageUrls(chapter, dataSaver = true)
            _activeOnlineChapterPages.value = pages
            _isLoadingChapterPages.value = false
        }
    }

    fun closeOnlineChapterReader() {
        _activeOnlineChapterReader.value = null
        _activeOnlineChapterPages.value = emptyList()
    }

    fun downloadOnlineChapterToCbz(series: OnlineComicSeries, chapter: OnlineComicChapter) {
        viewModelScope.launch {
            val pages = if (_activeOnlineChapterReader.value?.second?.id == chapter.id && _activeOnlineChapterPages.value.isNotEmpty()) {
                _activeOnlineChapterPages.value
            } else {
                comicHubEngine.fetchChapterPageUrls(chapter, dataSaver = true)
            }
            if (pages.isEmpty()) {
                _statusBannerMessage.value = "Opening portal to download chapter directly…"
                openCustomComicUrlInBrowser(chapter.externalUrl ?: series.webUrl, series.title)
                return@launch
            }
            val dir = File(getApplication<Application>().filesDir, "sample_library").apply { mkdirs() }
            val cbzFile = comicHubEngine.downloadChapterToOfflineCbz(
                seriesTitle = series.title,
                chapterNumber = chapter.chapterNumber,
                chapterTitle = chapter.title,
                author = series.author,
                pageUrls = pages,
                outputDir = dir
            )
            if (cbzFile != null && cbzFile.exists()) {
                val info = PdfAndComicEngine.inspectComicArchive(cbzFile, DocFormat.CBZ)
                val entity = DocumentEntity(
                    title = "${series.title} — Ch. ${chapter.chapterNumber}",
                    fileName = cbzFile.name,
                    localFilePath = cbzFile.absolutePath,
                    format = DocFormat.CBZ.name,
                    fileSizeBytes = cbzFile.length(),
                    totalUnits = info.pageCount.coerceAtLeast(1),
                    currentUnit = 0,
                    progressPercent = 0,
                    isFavorite = false,
                    isPinnedOffline = true,
                    tags = "${series.contentTypeLabel},Downloaded,CBZ,Offline",
                    summarySnippet = "${series.title} Chapter ${chapter.chapterNumber}: ${chapter.title} (${info.pageCount} pages, offline .CBZ)."
                )
                repository.insertDocument(entity)
                _statusBannerMessage.value = "Saved '${entity.title}' (${info.pageCount} pages) to Offline Comic Vault!"
            } else {
                _statusBannerMessage.value = "Could not download chapter pages to CBZ"
            }
        }
    }

    fun downloadDirectArchiveUrlToLibrary(url: String, suggestedFilename: String?) {
        viewModelScope.launch {
            val dir = File(getApplication<Application>().filesDir, "sample_library").apply { mkdirs() }
            val downloaded = comicHubEngine.downloadDirectFileUrlToLibrary(
                fileUrl = url,
                suggestedName = suggestedFilename,
                outputDir = dir
            )
            if (downloaded != null && downloaded.exists()) {
                val ext = downloaded.extension.lowercase()
                val format = when (ext) {
                    "cbr", "rar" -> DocFormat.CBR
                    "pdf" -> DocFormat.PDF
                    "docx" -> DocFormat.DOCX
                    "xlsx", "csv" -> DocFormat.XLSX
                    "pptx" -> DocFormat.PPTX
                    "txt", "md" -> DocFormat.TXT
                    else -> DocFormat.CBZ
                }
                val units = when (format) {
                    DocFormat.CBZ, DocFormat.CBR -> PdfAndComicEngine.inspectComicArchive(downloaded, format).pageCount.coerceAtLeast(1)
                    DocFormat.PDF -> PdfAndComicEngine.getPdfPageCount(downloaded).coerceAtLeast(1)
                    else -> 1
                }
                val cleanTitle = downloaded.nameWithoutExtension.replace('_', ' ')
                val entity = DocumentEntity(
                    title = cleanTitle,
                    fileName = downloaded.name,
                    localFilePath = downloaded.absolutePath,
                    format = format.name,
                    fileSizeBytes = downloaded.length(),
                    totalUnits = units,
                    currentUnit = 0,
                    progressPercent = 0,
                    isFavorite = false,
                    isPinnedOffline = true,
                    tags = "Downloaded,${format.name},EverythingMoe,Offline",
                    summarySnippet = "Downloaded directly from online portal ($units pages/units)."
                )
                repository.insertDocument(entity)
                _statusBannerMessage.value = "Imported '${entity.title}' into Offline Library!"
            } else {
                _statusBannerMessage.value = "Download failed or unsupported link"
            }
        }
    }

    fun packWebPageImagesToOfflineCbz(pageTitle: String, imageUrls: List<String>) {
        if (imageUrls.isEmpty()) {
            _statusBannerMessage.value = "No comic page images detected on this web page"
            return
        }
        viewModelScope.launch {
            val dir = File(getApplication<Application>().filesDir, "sample_library").apply { mkdirs() }
            val cbzFile = comicHubEngine.downloadChapterToOfflineCbz(
                seriesTitle = pageTitle.take(36),
                chapterNumber = "Web",
                chapterTitle = pageTitle,
                author = "EverythingMoe Portal",
                pageUrls = imageUrls,
                outputDir = dir
            )
            if (cbzFile != null && cbzFile.exists()) {
                val info = PdfAndComicEngine.inspectComicArchive(cbzFile, DocFormat.CBZ)
                val entity = DocumentEntity(
                    title = pageTitle.take(48),
                    fileName = cbzFile.name,
                    localFilePath = cbzFile.absolutePath,
                    format = DocFormat.CBZ.name,
                    fileSizeBytes = cbzFile.length(),
                    totalUnits = info.pageCount.coerceAtLeast(1),
                    currentUnit = 0,
                    progressPercent = 0,
                    isFavorite = false,
                    isPinnedOffline = true,
                    tags = "WebRip,CBZ,EverythingMoe,Offline",
                    summarySnippet = "Packed ${info.pageCount} comic pages from web portal into an offline .CBZ archive."
                )
                repository.insertDocument(entity)
                _statusBannerMessage.value = "Packed ${info.pageCount} pages into offline .CBZ '${entity.title}'!"
            } else {
                _statusBannerMessage.value = "Could not pack images from this page"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsController.shutdown()
    }
}
