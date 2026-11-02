package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.data.DocFormat
import com.example.data.DocumentEntity
import com.example.data.OmniDocDao
import com.example.data.OmniDocRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PersistedFolderGrant(
    val uriString: String,
    val displayName: String,
    val grantedAt: Long
)

data class DeviceIndexState(
    val isIndexing: Boolean = false,
    val scannedFilesCount: Int = 0,
    val scannedFoldersCount: Int = 0,
    val totalSupportedFound: Int = 0,
    val newlyAddedCount: Int = 0,
    val currentScanningLocation: String = "Ready to index device storage",
    val formatBreakdown: Map<DocFormat, Int> = emptyMap(),
    val persistedFolders: List<PersistedFolderGrant> = emptyList(),
    val recentDiscoveredNames: List<String> = emptyList(),
    val lastIndexedAt: Long = 0L
)

/**
 * Multi-Vector Device-Wide File Indexer that discovers all supported document and comic formats
 * (PDF, DOCX, XLSX, PPTX, TXT/MD/CSV/JSON, CBZ, CBR) across:
 *  1. Accessible FileSystem & Shared Storage Roots (Downloads, Documents, Books, Comics, App Storage)
 *  2. Android MediaStore Content Resolver (MediaStore.Downloads & MediaStore.Files)
 *  3. Persisted Storage Access Framework (SAF) Directory Trees (DocumentsContract recursive crawler)
 */
class DeviceStorageIndexer(
    private val context: Context,
    private val dao: OmniDocDao
) {
    private val _state = MutableStateFlow(DeviceIndexState())
    val state: StateFlow<DeviceIndexState> = _state.asStateFlow()

    private val supportedExtensions: Set<String> = DocFormat.entries
        .flatMap { it.extensions }
        .map { it.lowercase() }
        .toSet()

    init {
        refreshPersistedFolders()
    }

    fun refreshPersistedFolders() {
        val grants = runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { perm ->
                    val decoded = Uri.decode(perm.uri.toString())
                    val cleanName = decoded
                        .substringAfterLast(':')
                        .substringAfterLast('/')
                        .ifBlank { "Device Storage Root" }
                    PersistedFolderGrant(
                        uriString = perm.uri.toString(),
                        displayName = cleanName,
                        grantedAt = perm.persistedTime
                    )
                }
        }.getOrDefault(emptyList())

        _state.value = _state.value.copy(persistedFolders = grants)
    }

    fun takePersistableFolderGrant(treeUri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            refreshPersistedFolders()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun releasePersistableFolderGrant(uriString: String) {
        runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        refreshPersistedFolders()
    }

    private fun detectSupportedFormat(fileName: String, mimeType: String? = null): DocFormat? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext in supportedExtensions) {
            return DocFormat.fromFileName(fileName, mimeType)
        }
        if (mimeType != null) {
            val lower = mimeType.lowercase()
            return when {
                "application/pdf" in lower -> DocFormat.PDF
                "wordprocessingml" in lower || "msword" in lower -> DocFormat.DOCX
                "spreadsheetml" in lower || "ms-excel" in lower || "text/csv" in lower -> DocFormat.XLSX
                "presentationml" in lower || "ms-powerpoint" in lower -> DocFormat.PPTX
                "comicbook+zip" in lower || "x-cbz" in lower -> DocFormat.CBZ
                "comicbook-rar" in lower || "x-cbr" in lower -> DocFormat.CBR
                "text/plain" in lower || "text/markdown" in lower -> DocFormat.TXT
                else -> null
            }
        }
        return null
    }

    /**
     * Executes a full device-wide index across all accessible storage roots, MediaStore,
     * and user-granted SAF directory trees.
     */
    suspend fun indexEntireDevice(
        selectedFormats: Set<DocFormat> = DocFormat.entries.toSet()
    ): DeviceIndexState = withContext(Dispatchers.IO) {
        if (_state.value.isIndexing) return@withContext _state.value

        refreshPersistedFolders()
        _state.value = _state.value.copy(
            isIndexing = true,
            scannedFilesCount = 0,
            scannedFoldersCount = 0,
            totalSupportedFound = 0,
            newlyAddedCount = 0,
            currentScanningLocation = "Initializing device storage index…",
            formatBreakdown = emptyMap(),
            recentDiscoveredNames = emptyList()
        )

        val existingDocs = dao.getAllDocumentsSnapshot()
        val knownPaths = existingDocs.map { it.localFilePath }.toMutableSet()
        val knownNameAndSize = existingDocs.map { "${it.fileName.lowercase()}|${it.fileSizeBytes}" }.toMutableSet()

        var filesScanned = 0
        var foldersScanned = 0
        var totalSupported = 0
        var newlyAdded = 0
        val breakdown = mutableMapOf<DocFormat, Int>()
        val recentNames = mutableListOf<String>()

        fun emitProgress(location: String) {
            _state.value = _state.value.copy(
                isIndexing = true,
                scannedFilesCount = filesScanned,
                scannedFoldersCount = foldersScanned,
                totalSupportedFound = totalSupported,
                newlyAddedCount = newlyAdded,
                currentScanningLocation = location,
                formatBreakdown = breakdown.toMap(),
                recentDiscoveredNames = recentNames.takeLast(8).reversed()
            )
        }

        // 1. Scan Accessible File System & Public Storage Directories
        val rootCandidates = buildList {
            add(File(context.filesDir, "sample_library"))
            add(File(context.filesDir, "imported_docs"))
            context.filesDir.listFiles()?.filter { it.isDirectory && it.name != "tts_models" && it.name != " datastore" }?.forEach {
                add(it)
            }
            context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { add(it) }
            runCatching {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { add(it) }
            }
            runCatching {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { add(it) }
            }
            runCatching {
                val extRoot = Environment.getExternalStorageDirectory()
                if (extRoot != null && extRoot.exists() && extRoot.canRead()) {
                    add(extRoot)
                }
            }
        }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

        val visitedDirs = HashSet<String>()

        suspend fun walkDirectory(dir: File, depth: Int) {
            if (depth > 8 || !dir.exists() || !dir.canRead()) return
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!visitedDirs.add(canonical)) return

            // Skip hidden system/cache/model directories
            val dirName = dir.name
            if (dirName.startsWith(".") || dirName.equals("Android", ignoreCase = true) && depth == 1) return
            if (dirName == "tts_models" || dirName == "cache" || dirName == "code_cache") return

            foldersScanned++
            if (foldersScanned % 3 == 1) {
                emitProgress("Scanning folder: ${dir.path.takeLast(48)}")
            }

            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    walkDirectory(child, depth + 1)
                } else if (child.isFile && child.length() > 0L) {
                    filesScanned++
                    val format = detectSupportedFormat(child.name)
                    if (format != null && format in selectedFormats) {
                        totalSupported++
                        breakdown[format] = (breakdown[format] ?: 0) + 1
                        val sig = "${child.name.lowercase()}|${child.length()}"
                        if (child.absolutePath !in knownPaths && sig !in knownNameAndSize) {
                            val entity = createDocumentEntityFromFile(
                                file = child,
                                format = format,
                                sourceTag = dir.name.ifBlank { "Device" }
                            )
                            dao.insertDocument(entity)
                            knownPaths.add(child.absolutePath)
                            knownNameAndSize.add(sig)
                            newlyAdded++
                            recentNames.add(entity.fileName)
                            emitProgress("Indexed: ${entity.fileName}")
                        }
                    }
                }
            }
        }

        for (root in rootCandidates) {
            walkDirectory(root, depth = 0)
        }

        // 2. Scan Android MediaStore (MediaStore.Downloads & MediaStore.Files)
        emitProgress("Querying Android MediaStore & Downloads index…")
        val mediaUris = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            add(MediaStore.Files.getContentUri("external"))
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
        )

        for (collectionUri in mediaUris) {
            runCatching {
                context.contentResolver.query(
                    collectionUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                        val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
                        val sizeBytes = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                        val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null

                        filesScanned++
                        val format = detectSupportedFormat(displayName, mimeType) ?: continue
                        if (format !in selectedFormats) continue

                        val sig = "${displayName.lowercase()}|$sizeBytes"
                        totalSupported++
                        breakdown[format] = (breakdown[format] ?: 0) + 1

                        if (sig !in knownNameAndSize) {
                            val itemUri = Uri.withAppendedPath(collectionUri, id.toString())
                            val imported = copyContentUriToIndexedStorage(
                                uri = itemUri,
                                displayName = displayName,
                                format = format,
                                sourceBadge = "MediaStore"
                            )
                            if (imported != null) {
                                dao.insertDocument(imported)
                                knownPaths.add(imported.localFilePath)
                                knownNameAndSize.add("${imported.fileName.lowercase()}|${imported.fileSizeBytes}")
                                newlyAdded++
                                recentNames.add(imported.fileName)
                                emitProgress("Indexed from MediaStore: ${imported.fileName}")
                            }
                        }
                    }
                }
            }
        }

        // 3. Scan Persisted Storage Access Framework (SAF) Directory Trees
        val persistedPermissions = runCatching {
            context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
        }.getOrDefault(emptyList())

        for (perm in persistedPermissions) {
            val treeUri = perm.uri
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: continue
            val visitedDocIds = HashSet<String>()

            suspend fun walkSafTree(parentDocId: String, depth: Int, folderLabel: String) {
                if (depth > 10 || !visitedDocIds.add(parentDocId)) return
                foldersScanned++
                emitProgress("Scanning SAF Tree: $folderLabel")

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                val safProjection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                )

                val subDirs = mutableListOf<Pair<String, String>>()

                runCatching {
                    context.contentResolver.query(childrenUri, safProjection, null, null, null)?.use { cursor ->
                        val docIdIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                        while (cursor.moveToNext()) {
                            val docId = if (docIdIdx >= 0) cursor.getString(docIdIdx) ?: continue else continue
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: continue else continue
                            val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                            val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L

                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (!name.startsWith(".")) {
                                    subDirs.add(docId to "$folderLabel/$name")
                                }
                            } else {
                                filesScanned++
                                val format = detectSupportedFormat(name, mime)
                                if (format != null && format in selectedFormats) {
                                    totalSupported++
                                    breakdown[format] = (breakdown[format] ?: 0) + 1
                                    val sig = "${name.lowercase()}|$size"
                                    if (sig !in knownNameAndSize) {
                                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                        val entity = copyContentUriToIndexedStorage(
                                            uri = docUri,
                                            displayName = name,
                                            format = format,
                                            sourceBadge = folderLabel.substringAfterLast('/')
                                        )
                                        if (entity != null) {
                                            dao.insertDocument(entity)
                                            knownPaths.add(entity.localFilePath)
                                            knownNameAndSize.add("${entity.fileName.lowercase()}|${entity.fileSizeBytes}")
                                            newlyAdded++
                                            recentNames.add(entity.fileName)
                                            emitProgress("Indexed: ${entity.fileName}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for ((subId, subLabel) in subDirs) {
                    walkSafTree(subId, depth + 1, subLabel)
                }
            }

            val treeLabel = Uri.decode(treeUri.toString()).substringAfterLast(':').ifBlank { "Storage Root" }
            walkSafTree(rootDocId, depth = 0, folderLabel = treeLabel)
        }

        // Recalculate full library breakdown so the user always sees complete format counts
        val finalDocs = dao.getAllDocumentsSnapshot()
        val finalBreakdown = DocFormat.entries.associateWith { fmt ->
            finalDocs.count { it.docFormat == fmt }
        }

        val completedState = _state.value.copy(
            isIndexing = false,
            scannedFilesCount = filesScanned,
            scannedFoldersCount = foldersScanned,
            totalSupportedFound = finalDocs.size,
            newlyAddedCount = newlyAdded,
            currentScanningLocation = if (newlyAdded > 0) {
                "Index complete • Added $newlyAdded new file(s) (${finalDocs.size} total in library)"
            } else {
                "Index up to date • ${finalDocs.size} supported files verified across $foldersScanned folders"
            },
            formatBreakdown = finalBreakdown,
            recentDiscoveredNames = recentNames.takeLast(8).reversed(),
            lastIndexedAt = System.currentTimeMillis()
        )
        _state.value = completedState
        completedState
    }

    private suspend fun copyContentUriToIndexedStorage(
        uri: Uri,
        displayName: String,
        format: DocFormat,
        sourceBadge: String
    ): DocumentEntity? {
        return try {
            val indexDir = File(context.filesDir, "indexed_device_docs").apply { mkdirs() }
            val sanitized = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(indexDir, "${System.currentTimeMillis()}_$sanitized")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (targetFile.length() <= 0L) {
                targetFile.delete()
                return null
            }

            createDocumentEntityFromFile(
                file = targetFile,
                format = format,
                sourceTag = sourceBadge,
                originalDisplayName = displayName
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun createDocumentEntityFromFile(
        file: File,
        format: DocFormat,
        sourceTag: String,
        originalDisplayName: String = file.name
    ): DocumentEntity {
        val units = runCatching {
            when (format) {
                DocFormat.PDF -> PdfAndComicEngine.getPdfPageCount(file).coerceAtLeast(1)
                DocFormat.CBZ, DocFormat.CBR -> PdfAndComicEngine.inspectComicArchive(file, format).pageCount.coerceAtLeast(1)
                DocFormat.DOCX -> OfficeOpenXmlParser.parseDocx(file).blocks.size.coerceAtLeast(1)
                DocFormat.XLSX -> OfficeOpenXmlParser.parseXlsxOrCsv(file).sheets.size.coerceAtLeast(1)
                DocFormat.PPTX -> OfficeOpenXmlParser.parsePptx(file).slides.size.coerceAtLeast(1)
                DocFormat.TXT -> OfficeOpenXmlParser.parseTextFile(file).lines.size.coerceAtLeast(1)
            }
        }.getOrDefault(1)

        val cleanTitle = originalDisplayName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifEmpty { originalDisplayName }

        return DocumentEntity(
            title = cleanTitle,
            fileName = originalDisplayName,
            localFilePath = file.absolutePath,
            format = format.name,
            fileSizeBytes = file.length(),
            totalUnits = units,
            currentUnit = 0,
            progressPercent = 0,
            isFavorite = false,
            isPinnedOffline = true,
            tags = "Indexed,$sourceTag,${format.label},Offline",
            summarySnippet = "Indexed ${format.label} from $sourceTag (${OmniDocRepository.formatFileSize(file.length())} • $units units)"
        )
    }
}
