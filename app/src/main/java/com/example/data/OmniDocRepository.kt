package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val Context.accessibilityDataStore by preferencesDataStore(name = "omnidoc_accessibility_prefs")

class OmniDocRepository(
    private val context: Context,
    private val dao: OmniDocDao
) {
    val allDocuments: Flow<List<DocumentEntity>> = dao.getAllDocuments()
    val allBookmarks: Flow<List<BookmarkEntity>> = dao.getAllBookmarks()
    val allAnnotations: Flow<List<AnnotationEntity>> = dao.getAllAnnotations()

    private object PrefKeys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val BLUE_LIGHT_ENABLED = booleanPreferencesKey("blue_light_enabled")
        val BLUE_LIGHT_INTENSITY = floatPreferencesKey("blue_light_intensity")
        val SHOW_VIEWER_FILTER_BAR = booleanPreferencesKey("show_viewer_filter_bar")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val DYSLEXIA_SPACING = booleanPreferencesKey("dyslexia_spacing")
        val MONOSPACE_TEXT = booleanPreferencesKey("monospace_text")
        val SHOW_RULER = booleanPreferencesKey("show_ruler")
        val RULER_POS = floatPreferencesKey("ruler_pos")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val COMIC_MODE = stringPreferencesKey("comic_mode")
        val INVERT_PDF = booleanPreferencesKey("invert_pdf")
        val COMIC_WARM_FILTER = booleanPreferencesKey("comic_warm_filter")
    }

    val accessibilitySettings: Flow<AccessibilitySettings> =
        context.accessibilityDataStore.data.map { prefs ->
            val modeName = prefs[PrefKeys.COLOR_MODE] ?: ReadingColorMode.DAYLIGHT.name
            val comicModeName = prefs[PrefKeys.COMIC_MODE] ?: ComicReadingMode.LEFT_TO_RIGHT.name
            AccessibilitySettings(
                darkThemeApp = prefs[PrefKeys.DARK_THEME] ?: true,
                colorMode = runCatching { ReadingColorMode.valueOf(modeName) }
                    .getOrDefault(ReadingColorMode.DAYLIGHT),
                blueLightFilterEnabled = prefs[PrefKeys.BLUE_LIGHT_ENABLED] ?: false,
                blueLightIntensity = prefs[PrefKeys.BLUE_LIGHT_INTENSITY] ?: 0.35f,
                showViewerFilterBar = prefs[PrefKeys.SHOW_VIEWER_FILTER_BAR] ?: true,
                fontScale = prefs[PrefKeys.FONT_SCALE] ?: 1.0f,
                lineSpacingMultiplier = prefs[PrefKeys.LINE_SPACING] ?: 1.45f,
                dyslexiaFriendlySpacing = prefs[PrefKeys.DYSLEXIA_SPACING] ?: false,
                useMonospaceForText = prefs[PrefKeys.MONOSPACE_TEXT] ?: false,
                showReadingRuler = prefs[PrefKeys.SHOW_RULER] ?: false,
                rulerPositionFraction = prefs[PrefKeys.RULER_POS] ?: 0.38f,
                ttsSpeechRate = prefs[PrefKeys.TTS_RATE] ?: 1.0f,
                ttsPitch = prefs[PrefKeys.TTS_PITCH] ?: 1.0f,
                comicReadingMode = runCatching { ComicReadingMode.valueOf(comicModeName) }
                    .getOrDefault(ComicReadingMode.LEFT_TO_RIGHT),
                invertPdfColors = prefs[PrefKeys.INVERT_PDF] ?: false,
                comicWarmFilter = prefs[PrefKeys.COMIC_WARM_FILTER] ?: false
            )
        }

    suspend fun updateAccessibility(transform: (AccessibilitySettings) -> AccessibilitySettings) {
        context.accessibilityDataStore.edit { prefs ->
            val currentModeName = prefs[PrefKeys.COLOR_MODE] ?: ReadingColorMode.DAYLIGHT.name
            val currentComicName = prefs[PrefKeys.COMIC_MODE] ?: ComicReadingMode.LEFT_TO_RIGHT.name
            val current = AccessibilitySettings(
                darkThemeApp = prefs[PrefKeys.DARK_THEME] ?: true,
                colorMode = runCatching { ReadingColorMode.valueOf(currentModeName) }
                    .getOrDefault(ReadingColorMode.DAYLIGHT),
                blueLightFilterEnabled = prefs[PrefKeys.BLUE_LIGHT_ENABLED] ?: false,
                blueLightIntensity = prefs[PrefKeys.BLUE_LIGHT_INTENSITY] ?: 0.35f,
                showViewerFilterBar = prefs[PrefKeys.SHOW_VIEWER_FILTER_BAR] ?: true,
                fontScale = prefs[PrefKeys.FONT_SCALE] ?: 1.0f,
                lineSpacingMultiplier = prefs[PrefKeys.LINE_SPACING] ?: 1.45f,
                dyslexiaFriendlySpacing = prefs[PrefKeys.DYSLEXIA_SPACING] ?: false,
                useMonospaceForText = prefs[PrefKeys.MONOSPACE_TEXT] ?: false,
                showReadingRuler = prefs[PrefKeys.SHOW_RULER] ?: false,
                rulerPositionFraction = prefs[PrefKeys.RULER_POS] ?: 0.38f,
                ttsSpeechRate = prefs[PrefKeys.TTS_RATE] ?: 1.0f,
                ttsPitch = prefs[PrefKeys.TTS_PITCH] ?: 1.0f,
                comicReadingMode = runCatching { ComicReadingMode.valueOf(currentComicName) }
                    .getOrDefault(ComicReadingMode.LEFT_TO_RIGHT),
                invertPdfColors = prefs[PrefKeys.INVERT_PDF] ?: false,
                comicWarmFilter = prefs[PrefKeys.COMIC_WARM_FILTER] ?: false
            )
            val updated = transform(current)
            prefs[PrefKeys.DARK_THEME] = updated.darkThemeApp
            prefs[PrefKeys.COLOR_MODE] = updated.colorMode.name
            prefs[PrefKeys.BLUE_LIGHT_ENABLED] = updated.blueLightFilterEnabled
            prefs[PrefKeys.BLUE_LIGHT_INTENSITY] = updated.blueLightIntensity
            prefs[PrefKeys.SHOW_VIEWER_FILTER_BAR] = updated.showViewerFilterBar
            prefs[PrefKeys.FONT_SCALE] = updated.fontScale
            prefs[PrefKeys.LINE_SPACING] = updated.lineSpacingMultiplier
            prefs[PrefKeys.DYSLEXIA_SPACING] = updated.dyslexiaFriendlySpacing
            prefs[PrefKeys.MONOSPACE_TEXT] = updated.useMonospaceForText
            prefs[PrefKeys.SHOW_RULER] = updated.showReadingRuler
            prefs[PrefKeys.RULER_POS] = updated.rulerPositionFraction
            prefs[PrefKeys.TTS_RATE] = updated.ttsSpeechRate
            prefs[PrefKeys.TTS_PITCH] = updated.ttsPitch
            prefs[PrefKeys.COMIC_MODE] = updated.comicReadingMode.name
            prefs[PrefKeys.INVERT_PDF] = updated.invertPdfColors
            prefs[PrefKeys.COMIC_WARM_FILTER] = updated.comicWarmFilter
        }
    }

    fun getBookmarksForDocument(docId: Long): Flow<List<BookmarkEntity>> =
        dao.getBookmarksForDocument(docId)

    fun getAnnotationsForDocument(docId: Long): Flow<List<AnnotationEntity>> =
        dao.getAnnotationsForDocument(docId)

    suspend fun getDocumentCount(): Int = dao.getDocumentCount()

    suspend fun getDocumentById(id: Long): DocumentEntity? = dao.getDocumentById(id)

    suspend fun insertDocument(doc: DocumentEntity): Long = dao.insertDocument(doc)

    suspend fun updateDocument(doc: DocumentEntity) = dao.updateDocument(doc)

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        dao.deleteDocumentById(doc.id)
    }

    suspend fun toggleFavorite(doc: DocumentEntity) {
        dao.updateDocument(doc.copy(isFavorite = !doc.isFavorite))
    }

    suspend fun toggleOfflinePin(doc: DocumentEntity) {
        dao.updateDocument(doc.copy(isPinnedOffline = !doc.isPinnedOffline))
    }

    suspend fun updateReadingProgress(docId: Long, currentUnit: Int, totalUnits: Int) {
        val doc = dao.getDocumentById(docId) ?: return
        val safeTotal = totalUnits.coerceAtLeast(1)
        val safeCurrent = currentUnit.coerceIn(0, safeTotal - 1)
        val pct = (((safeCurrent + 1).toFloat() / safeTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)
        dao.updateDocument(
            doc.copy(
                currentUnit = safeCurrent,
                totalUnits = safeTotal,
                progressPercent = pct,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addBookmark(
        doc: DocumentEntity,
        unitIndex: Int,
        label: String,
        excerpt: String
    ) {
        dao.insertBookmark(
            BookmarkEntity(
                documentId = doc.id,
                documentTitle = doc.title,
                format = doc.format,
                unitIndex = unitIndex,
                label = label,
                excerpt = excerpt
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmarkById(id)

    suspend fun addAnnotation(
        doc: DocumentEntity,
        unitIndex: Int,
        noteText: String,
        colorHex: String
    ) {
        dao.insertAnnotation(
            AnnotationEntity(
                documentId = doc.id,
                documentTitle = doc.title,
                unitIndex = unitIndex,
                noteText = noteText,
                highlightColorHex = colorHex
            )
        )
    }

    suspend fun deleteAnnotation(id: Long) = dao.deleteAnnotationById(id)

    /**
     * Imports any external content:// or file:// Uri into internal offline storage
     * (`filesDir/imported_docs/`) so it remains 100% available offline at all times.
     */
    suspend fun importExternalUri(uri: Uri): DocumentEntity? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var displayName = "Imported_Document"
            var sizeBytes = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (displayName == "Imported_Document") {
                displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.txt"
            }

            val mimeType = contentResolver.getType(uri)
            val format = DocFormat.fromFileName(displayName, mimeType)

            val importDir = File(context.filesDir, "imported_docs").apply { mkdirs() }
            val sanitizedName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(importDir, "${System.currentTimeMillis()}_$sanitizedName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (sizeBytes <= 0L) {
                sizeBytes = targetFile.length()
            }

            val cleanTitle = displayName.substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifEmpty { displayName }

            val entity = DocumentEntity(
                title = cleanTitle,
                fileName = displayName,
                localFilePath = targetFile.absolutePath,
                format = format.name,
                fileSizeBytes = sizeBytes,
                totalUnits = 1,
                currentUnit = 0,
                progressPercent = 0,
                isFavorite = false,
                isPinnedOffline = true,
                tags = "Imported,Offline",
                summarySnippet = "Offline cached ${format.label} document (${formatFileSize(sizeBytes)})"
            )
            val id = dao.insertDocument(entity)
            entity.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format("%.2f MB", mb)
        }
    }
}
