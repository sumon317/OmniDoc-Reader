package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AccessibilitySettings
import com.example.data.AnnotationEntity
import com.example.data.BookmarkEntity
import com.example.data.ComicReadingMode
import com.example.data.DocFormat
import com.example.data.DocumentEntity
import com.example.data.OmniDocRepository
import com.example.data.ReadingColorMode
import com.example.engine.ComicPortalCategory
import com.example.engine.DeviceIndexState
import com.example.engine.DownloadedModelStatus
import com.example.engine.EverythingMoePortalSource
import com.example.engine.HuggingFaceTtsModelInfo
import com.example.engine.OnlineComicFilter
import com.example.engine.OnlineComicSeries
import com.example.engine.OnlineDownloadProgress
import com.example.engine.TtsPlaybackState
import com.example.ui.SortOption
import com.example.ui.components.EditorialHeroBanner
import com.example.ui.components.FormatBadge
import com.example.ui.components.HuggingFaceModelCard
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlin.math.roundToInt

@Composable
fun LibraryTabScreen(
    allDocs: List<DocumentEntity>,
    filteredDocs: List<DocumentEntity>,
    searchQuery: String,
    selectedFormat: DocFormat?,
    sortOption: SortOption,
    onlyFavorites: Boolean,
    deviceIndexState: DeviceIndexState,
    onSearchQueryChange: (String) -> Unit,
    onSelectFormat: (DocFormat?) -> Unit,
    onCycleSort: () -> Unit,
    onToggleFavorites: () -> Unit,
    onOpenDoc: (DocumentEntity) -> Unit,
    onToggleDocFavorite: (DocumentEntity) -> Unit,
    onShareDoc: (DocumentEntity) -> Unit,
    onDeleteDoc: (DocumentEntity) -> Unit,
    onOpenDeviceFile: () -> Unit,
    onCreateNewDoc: () -> Unit,
    onScanEntireDevice: (Set<DocFormat>) -> Unit,
    onGrantStorageFolder: () -> Unit,
    onRemoveGrantedFolder: (String) -> Unit
) {
    val totalComics = allDocs.count { it.docFormat.isComic }
    val totalBytes = allDocs.sumOf { it.fileSizeBytes }
    var showIndexerDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_tab_list")
    ) {
        item {
            EditorialHeroBanner(
                totalDocs = allDocs.size,
                totalComics = totalComics,
                totalBytes = totalBytes,
                onOpenDeviceFile = onOpenDeviceFile,
                onCreateNewDoc = onCreateNewDoc
            )
        }

        // Device-Wide File Indexer Card
        item {
            DeviceStorageIndexerCard(
                indexState = deviceIndexState,
                onQuickScanDevice = { onScanEntireDevice(DocFormat.entries.toSet()) },
                onGrantFolder = onGrantStorageFolder,
                onOpenIndexerDialog = { showIndexerDialog = true }
            )
        }

        // Search & Filter Controls
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                placeholder = {
                    Text("Search PDF, DOCX, XLSX, PPTX, TXT, CBZ, CBR…")
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input")
            )
        }

        // Format Filter Chips + Favorites + Sort
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedFormat == null,
                            onClick = { onSelectFormat(null) },
                            label = { Text("All (${allDocs.size})") },
                            modifier = Modifier.testTag("filter_chip_all")
                        )
                    }
                    items(DocFormat.entries) { format ->
                        val count = allDocs.count { it.docFormat == format }
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { onSelectFormat(format) },
                            label = { Text("${format.label} ($count)") },
                            modifier = Modifier.testTag("filter_chip_${format.name.lowercase()}")
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = onToggleFavorites,
                        leadingIcon = {
                            Icon(
                                imageVector = if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Starred") }
                    )

                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { onCycleSort() }
                            .testTag("cycle_sort_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Order",
                                tint = Cyan400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sortOption.label,
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                            )
                        }
                    }
                }
            }
        }

        if (filteredDocs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                    ) {
                        Text(
                            text = "No matching documents found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing your filter or tap 'Open Device File' to import a PDF, Office document, or CBZ/CBR comic archive.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }
            }
        } else {
            items(filteredDocs, key = { it.id }) { doc ->
                DocumentLibraryCard(
                    doc = doc,
                    onClick = { onOpenDoc(doc) },
                    onToggleFavorite = { onToggleDocFavorite(doc) },
                    onShare = { onShareDoc(doc) },
                    onDelete = { onDeleteDoc(doc) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showIndexerDialog) {
        DeviceIndexerStudioDialog(
            indexState = deviceIndexState,
            onDismiss = { showIndexerDialog = false },
            onStartScan = { formats ->
                onScanEntireDevice(formats)
            },
            onGrantFolder = onGrantStorageFolder,
            onRemoveFolder = onRemoveGrantedFolder
        )
    }
}

@Composable
fun DeviceStorageIndexerCard(
    indexState: DeviceIndexState,
    onQuickScanDevice: () -> Unit,
    onGrantFolder: () -> Unit,
    onOpenIndexerDialog: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Cyan400.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
            .testTag("device_indexer_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Cyan400.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Device Indexer",
                            tint = Cyan400,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Device-Wide File Indexer",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Surface(
                                color = Emerald400.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (indexState.isIndexing) "SCANNING…" else "AUTO-DISCOVERY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Emerald400,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = indexState.currentScanningLocation,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TextButton(
                    onClick = onOpenIndexerDialog,
                    modifier = Modifier.testTag("manage_device_index_button")
                ) {
                    Text(
                        text = "Configure",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Amber400,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            if (indexState.isIndexing) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    color = Cyan400,
                    trackColor = Slate800,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Format breakdown badges
            if (indexState.formatBreakdown.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    DocFormat.entries.forEach { fmt ->
                        val count = indexState.formatBreakdown[fmt] ?: 0
                        Surface(
                            color = fmt.badgeColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(
                                1.dp,
                                fmt.badgeColor.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = "${fmt.label}: $count",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = fmt.badgeColor,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onQuickScanDevice,
                    enabled = !indexState.isIndexing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan400,
                        contentColor = Slate950
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_device_button")
                ) {
                    if (indexState.isIndexing) {
                        CircularProgressIndicator(
                            color = Slate950,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (indexState.isIndexing) "Indexing Device…" else "Index Entire Device",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onGrantFolder,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("grant_folder_tree_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (indexState.persistedFolders.isEmpty()) "Add Storage Root" else "Roots (${indexState.persistedFolders.size})"
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceIndexerStudioDialog(
    indexState: DeviceIndexState,
    onDismiss: () -> Unit,
    onStartScan: (Set<DocFormat>) -> Unit,
    onGrantFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit
) {
    var selectedFormats by remember { mutableStateOf(DocFormat.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Column {
                Text(
                    text = "Device-Wide File Indexer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Automatically discovers supported documents & comics across internal storage, Downloads, MediaStore, and granted folders.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Telemetry Stats Box
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "SCANNED",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "${indexState.scannedFilesCount} files • ${indexState.scannedFoldersCount} dirs",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "DISCOVERED",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "${indexState.totalSupportedFound} supported (+${indexState.newlyAddedCount} new)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Cyan400,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Supported Format Check Chips
                Text(
                    text = "Supported File Formats to Index:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Amber400,
                        fontWeight = FontWeight.Bold
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    DocFormat.entries.forEach { fmt ->
                        val isSelected = fmt in selectedFormats
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFormats = if (isSelected && selectedFormats.size > 1) {
                                    selectedFormats - fmt
                                } else {
                                    selectedFormats + fmt
                                }
                            },
                            label = {
                                Text("${fmt.label} (.${fmt.extensions.joinToString(", .")})")
                            }
                        )
                    }
                }

                // Granted Storage Roots (SAF)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Granted Storage Roots (${indexState.persistedFolders.size}):",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Cyan400,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    TextButton(onClick = onGrantFolder) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Grant Folder", color = Amber400)
                    }
                }

                if (indexState.persistedFolders.isEmpty()) {
                    Text(
                        text = "Standard public folders (Downloads, Documents, MediaStore) are indexed automatically. Tap 'Grant Folder' to authorize deep recursive indexing of any custom device folder or SD card root.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        indexState.persistedFolders.forEach { grant ->
                            Surface(
                                color = Slate800,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Emerald400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = grant.displayName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveFolder(grant.uriString) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Folder Grant",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (indexState.recentDiscoveredNames.isNotEmpty()) {
                    Text(
                        text = "Recently Discovered:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Emerald400)
                    )
                    Text(
                        text = indexState.recentDiscoveredNames.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartScan(selectedFormats)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan400,
                    contentColor = Slate950
                )
            ) {
                Text("Run Full Device Index", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Gray)
            }
        }
    )
}

@Composable
fun DocumentLibraryCard(
    doc: DocumentEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val format = doc.docFormat
    val unitLabel = when (format) {
        DocFormat.PDF, DocFormat.CBZ, DocFormat.CBR -> "${doc.totalUnits} pages"
        DocFormat.XLSX -> "${doc.totalUnits} sheets"
        DocFormat.PPTX -> "${doc.totalUnits} slides"
        DocFormat.DOCX -> "${doc.totalUnits} blocks"
        DocFormat.TXT -> "${doc.totalUnits} lines"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = format.badgeColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .testTag("doc_card_${doc.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FormatBadge(format = format)
                    Column {
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OfflinePin,
                                contentDescription = "Offline Available",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${doc.fileName} • $unitLabel • ${OmniDocRepository.formatFileSize(doc.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (doc.isFavorite) Amber400 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Document",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (doc.summarySnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = doc.summarySnippet,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { (doc.progressPercent / 100f).coerceIn(0f, 1f) },
                    color = format.badgeColor,
                    trackColor = format.badgeColor.copy(alpha = 0.16f),
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Text(
                    text = "${doc.progressPercent}% Read",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = format.badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

private enum class ComicHubSection(val label: String) {
    EVERYTHING_MOE_PORTALS("EverythingMoe & Portals"),
    LIVE_MANGA_MANHWA("Live Manga & Manhwa"),
    OFFLINE_VAULT("Offline CBZ/CBR")
}

/**
 * Dedicated Online Manga, Manhwa, Comics, EverythingMoe Portal Directory & Offline CBZ/CBR Vault Tab.
 */
@Composable
fun ComicVaultTabScreen(
    allDocs: List<DocumentEntity>,
    settings: AccessibilitySettings,
    curatedPortals: List<EverythingMoePortalSource>,
    onlineSeriesList: List<OnlineComicSeries>,
    isLoadingOnlineSeries: Boolean,
    activeOnlineFilter: OnlineComicFilter,
    onlineDownloadProgress: OnlineDownloadProgress,
    onSelectComicMode: (ComicReadingMode) -> Unit,
    onToggleComicWarmth: () -> Unit,
    onOpenComic: (DocumentEntity) -> Unit,
    onImportComicFile: () -> Unit,
    onOpenPortal: (EverythingMoePortalSource) -> Unit,
    onOpenCustomPortalUrl: (String) -> Unit,
    onLoadOnlineSeries: (OnlineComicFilter, String) -> Unit,
    onSelectOnlineSeries: (OnlineComicSeries) -> Unit
) {
    val comicDocs = remember(allDocs) {
        allDocs.filter { it.docFormat == DocFormat.CBZ || it.docFormat == DocFormat.CBR }
    }

    var activeSection by remember { mutableStateOf(ComicHubSection.EVERYTHING_MOE_PORTALS) }
    var selectedPortalCategory by remember { mutableStateOf(ComicPortalCategory.ALL) }
    var portalSearchText by remember { mutableStateOf("") }
    var customUrlInput by remember { mutableStateOf("") }
    var onlineSearchQuery by remember { mutableStateOf("") }

    val filteredPortals = remember(curatedPortals, selectedPortalCategory, portalSearchText) {
        curatedPortals.filter { portal ->
            val matchesCat = selectedPortalCategory == ComicPortalCategory.ALL || portal.category == selectedPortalCategory
            val matchesQuery = portalSearchText.isBlank() ||
                portal.name.contains(portalSearchText, ignoreCase = true) ||
                portal.description.contains(portalSearchText, ignoreCase = true) ||
                portal.tags.any { it.contains(portalSearchText, ignoreCase = true) }
            matchesCat && matchesQuery
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("comic_vault_tab_list")
    ) {
        // Hero Header Card with 3-Mode Selector
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Cyan400.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FormatBadge(format = DocFormat.CBZ)
                        FormatBadge(format = DocFormat.CBR)
                        Surface(
                            color = Amber400.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "EVERYTHINGMOE.COM INTEGRATED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Amber400,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Manga, Manhwa, Comics & DDL Hub",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Browse EverythingMoe's top Manga, Korean Manhwa, Western Comics, and Direct Download (.CBZ / .CBR) portals in-app with Ad-Shield, or read & save chapters directly to your offline vault.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3-Mode Segmented Pill Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        ComicHubSection.entries.forEach { section ->
                            val isSelected = activeSection == section
                            Button(
                                onClick = { activeSection = section },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Amber400 else Slate800,
                                    contentColor = if (isSelected) Slate950 else Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("comic_section_${section.name.lowercase()}")
                            ) {
                                Text(
                                    text = if (section == ComicHubSection.OFFLINE_VAULT) {
                                        "${section.label} (${comicDocs.size})"
                                    } else {
                                        section.label
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    // Active Download Progress Bar
                    if (onlineDownloadProgress.isDownloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = onlineDownloadProgress.statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Amber400,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { onlineDownloadProgress.progressFraction },
                                    color = Emerald400,
                                    trackColor = Slate950,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        when (activeSection) {
            ComicHubSection.EVERYTHING_MOE_PORTALS -> {
                // Featured EverythingMoe.com Direct Launch Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Amber400.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "OFFICIAL EVERYTHINGMOE.COM DIRECTORY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Amber400,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Launch EverythingMoe In-App Reader",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Includes Ad-Shield, 1-Tap Page Image-to-.CBZ Packer, and automatic .CBZ / .CBR / .PDF download import.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        curatedPortals.firstOrNull()?.let { onOpenPortal(it) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Amber400,
                                        contentColor = Slate950
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("launch_everythingmoe_main_btn")
                                ) {
                                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Hub", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom URL / Direct Download Link Launcher
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = customUrlInput,
                                    onValueChange = { customUrlInput = it },
                                    placeholder = { Text("Paste any manga, manhwa, comic or DDL URL…") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("custom_comic_portal_url_input")
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (customUrlInput.isNotBlank()) {
                                            onOpenCustomPortalUrl(customUrlInput)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Launch", color = Cyan400, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Category Chips + Filter Input
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ComicPortalCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedPortalCategory == cat,
                                    onClick = { selectedPortalCategory = cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = portalSearchText,
                            onValueChange = { portalSearchText = it },
                            placeholder = {
                                Text("Filter portals (e.g. MangaDex, Asura, Webtoon, GetComics, Nyaa, CBZ)…")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400)
                            },
                            trailingIcon = {
                                if (portalSearchText.isNotEmpty()) {
                                    IconButton(onClick = { portalSearchText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Portal Source Cards
                items(filteredPortals, key = { it.id }) { portal ->
                    val accent = Color(portal.accentHex)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .clickable { onOpenPortal(portal) }
                            .testTag("portal_card_${portal.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = accent.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = portal.category.label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = accent,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        color = Slate800,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = portal.rankBadge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Amber400,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (portal.supportsDirectCbzDownload) {
                                    Surface(
                                        color = Emerald400.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "CBZ / DDL READY",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Emerald400,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = portal.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = portal.url,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Cyan400,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = portal.description,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    portal.tags.forEach { tag ->
                                        Surface(
                                            color = Slate800,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { onOpenPortal(portal) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = Slate950
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Open In-App",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ComicHubSection.LIVE_MANGA_MANHWA -> {
                // Live Search & Filter Controls
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "NATIVE ONLINE MANGA, MANHWA & MANHUA READER (READ OR SAVE .CBZ)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Amber400,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = onlineSearchQuery,
                                    onValueChange = { onlineSearchQuery = it },
                                    placeholder = { Text("Search Manga, Korean Manhwa, Webtoons…") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("online_manga_search_input")
                                )
                                Button(
                                    onClick = { onLoadOnlineSeries(activeOnlineFilter, onlineSearchQuery) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Cyan400,
                                        contentColor = Slate950
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("online_manga_search_btn")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Search", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                OnlineComicFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = activeOnlineFilter == filter && onlineSearchQuery.isBlank(),
                                        onClick = {
                                            onlineSearchQuery = ""
                                            onLoadOnlineSeries(filter, "")
                                        },
                                        label = { Text(filter.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isLoadingOnlineSeries) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Amber400)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Fetching live Manga & Manhwa catalog…",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )
                            }
                        }
                    }
                } else {
                    items(onlineSeriesList, key = { it.id }) { series ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate900),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Cyan400.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                .clickable { onSelectOnlineSeries(series) }
                                .testTag("online_series_${series.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                // Cover Art with AsyncImage + Fallback
                                Box(
                                    modifier = Modifier
                                        .width(88.dp)
                                        .height(126.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Slate800)
                                        .border(1.dp, Amber400.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                ) {
                                    if (series.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(series.coverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = series.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = Amber400,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                    Surface(
                                        color = Slate950.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = series.contentTypeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Amber400,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${series.author} • ${series.status}${series.year?.let { " ($it)" } ?: ""}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = series.description,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1)),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            series.tags.take(2).forEach { tag ->
                                                Surface(
                                                    color = Slate800,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = { onSelectOnlineSeries(series) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Amber400,
                                                contentColor = Slate950
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Chapters & .CBZ",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ComicHubSection.OFFLINE_VAULT -> {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "OFFLINE CBZ & CBR READING PREFERENCES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Cyan400,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                ComicReadingMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.comicReadingMode == mode,
                                        onClick = { onSelectComicMode(mode) },
                                        label = { Text(mode.label) }
                                    )
                                }
                                FilterChip(
                                    selected = settings.comicWarmFilter,
                                    onClick = onToggleComicWarmth,
                                    label = { Text("Eye-Care Tint") }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onImportComicFile,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Cyan400,
                                    contentColor = Slate950
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("import_comic_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Local CBZ / CBR Archive", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                items(comicDocs, key = { it.id }) { comic ->
                    val isCbr = comic.docFormat == DocFormat.CBR
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                comic.docFormat.badgeColor.copy(alpha = 0.4f),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { onOpenComic(comic) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(86.dp)
                                    .height(122.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.5.dp, comic.docFormat.badgeColor, RoundedCornerShape(12.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = if (isCbr) {
                                                listOf(Color(0xFF3B0764), Color(0xFF9D174D), Color(0xFF0F172A))
                                            } else {
                                                listOf(Color(0xFF083344), Color(0xFF0284C7), Color(0xFF0F172A))
                                            }
                                        )
                                    )
                                    drawCircle(
                                        color = if (isCbr) Color(0xFFF472B6) else Color(0xFFFBBF24),
                                        radius = 24.dp.toPx(),
                                        center = Offset(size.width * 0.55f, size.height * 0.38f)
                                    )
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.4f),
                                        start = Offset(0f, size.height * 0.68f),
                                        end = Offset(size.width, size.height * 0.56f),
                                        strokeWidth = 3f
                                    )
                                }
                                FormatBadge(
                                    format = comic.docFormat,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = comic.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${comic.totalUnits} Pages • ${OmniDocRepository.formatFileSize(comic.fileSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = comic.summarySnippet,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1)),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { (comic.progressPercent / 100f).coerceIn(0f, 1f) },
                                        color = comic.docFormat.badgeColor,
                                        trackColor = Slate800,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                    Text(
                                        text = "Page ${comic.currentUnit + 1}/${comic.totalUnits}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Amber400)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Unified Bookmarks & Study Annotations Hub Tab.
 */
@Composable
fun BookmarksAndNotesTabScreen(
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    onJumpToDocUnit: (Long, Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteAnnotation: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("bookmarks_notes_tab_list")
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Offline Study Hub & Bookmarks",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${bookmarks.size} Saved Page Bookmarks • ${annotations.size} Study Notes across your offline documents. Tap any entry to jump directly to that page or slide.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                    )
                }
            }
        }

        item {
            Text(
                text = "SAVED PAGE BOOKMARKS (${bookmarks.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Amber400,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (bookmarks.isEmpty()) {
            item {
                Text(
                    text = "No bookmarks yet. Open any document or comic and tap the bookmark icon.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }
        } else {
            items(bookmarks, key = { "hub_bm_${it.id}" }) { bm ->
                val fmt = runCatching { DocFormat.valueOf(bm.format) }.getOrDefault(DocFormat.PDF)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJumpToDocUnit(bm.documentId, bm.unitIndex) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FormatBadge(format = fmt)
                                Text(
                                    text = bm.label,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${bm.documentTitle} • Section/Page #${bm.unitIndex + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                            )
                            Text(
                                text = bm.excerpt,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Bookmark",
                                tint = Color(0xFFF87171)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "COLOR-CODED STUDY ANNOTATIONS (${annotations.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Cyan400,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (annotations.isEmpty()) {
            item {
                Text(
                    text = "No study annotations yet. Add notes inside any viewer.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }
        } else {
            items(annotations, key = { "hub_an_${it.id}" }) { note ->
                val accent = runCatching {
                    Color(android.graphics.Color.parseColor(note.highlightColorHex))
                }.getOrDefault(Amber400)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .clickable { onJumpToDocUnit(note.documentId, note.unitIndex) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${note.documentTitle} • Section #${note.unitIndex + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.noteText,
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                        }
                        IconButton(onClick = { onDeleteAnnotation(note.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Annotation",
                                tint = Color(0xFFF87171)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Comprehensive Offline Accessibility & Visual Comfort Control Center Tab.
 */
@Composable
fun AccessibilityHubTabScreen(
    settings: AccessibilitySettings,
    ttsState: TtsPlaybackState,
    curatedHfModels: List<HuggingFaceTtsModelInfo>,
    hfModelStatuses: Map<String, DownloadedModelStatus>,
    hfHubSearchResults: List<HuggingFaceTtsModelInfo>,
    isSearchingHfHub: Boolean,
    onSelectColorMode: (ReadingColorMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onToggleDyslexia: () -> Unit,
    onToggleMonospace: () -> Unit,
    onToggleRuler: () -> Unit,
    onToggleInvertPdf: () -> Unit,
    onToggleComicWarmth: () -> Unit,
    onToggleBlueLight: () -> Unit = {},
    onBlueLightIntensityChange: (Float) -> Unit = {},
    onUpdateTtsRatePitch: (Float, Float) -> Unit,
    onTestOfflineTts: () -> Unit,
    onDownloadHfModel: (HuggingFaceTtsModelInfo, Boolean) -> Unit,
    onCancelHfDownload: (String) -> Unit,
    onActivateHfModel: (HuggingFaceTtsModelInfo) -> Unit,
    onTestHfModelVoice: (HuggingFaceTtsModelInfo) -> Unit,
    onDeleteHfModel: (String) -> Unit,
    onSearchHfHub: (String) -> Unit,
    onSelectSystemVoice: (String) -> Unit
) {
    var hfSearchQuery by remember { mutableStateOf("onnx piper kokoro") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("accessibility_hub_tab_list")
    ) {
        // Active TTS Engine & Hugging Face Model Hub Header
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Cyan400.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Android TTS & Hugging Face Model Hub",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Active Voice: ${ttsState.activeVoiceLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Download mobile-optimized neural TTS models (Piper VITS ONNX, Kokoro-82M INT8 ONNX, NeuML JETS) directly from Hugging Face to your phone, or pair with Android's native TextToSpeech engine.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Speech Rate: ${String.format("%.2fx", settings.ttsSpeechRate)}  •  Pitch: ${String.format("%.2fx", settings.ttsPitch)}",
                        style = MaterialTheme.typography.labelMedium.copy(color = Amber400)
                    )
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = { onUpdateTtsRatePitch(it, settings.ttsPitch) },
                        valueRange = 0.5f..2.0f
                    )
                    Slider(
                        value = settings.ttsPitch,
                        onValueChange = { onUpdateTtsRatePitch(settings.ttsSpeechRate, it) },
                        valueRange = 0.6f..1.5f
                    )

                    Button(
                        onClick = onTestOfflineTts,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber400,
                            contentColor = Slate950
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_offline_tts_button")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Speak Test Sample (${ttsState.activeVoiceLabel})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Curated Mobile-Ready Hugging Face TTS Models
        item {
            Text(
                text = "RECOMMENDED MOBILE TTS MODELS ON HUGGING FACE",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Amber400,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        items(curatedHfModels, key = { it.id }) { model ->
            val status = hfModelStatuses[model.id] ?: DownloadedModelStatus(modelId = model.id)
            HuggingFaceModelCard(
                model = model,
                status = status,
                isActiveModel = ttsState.activeHfModelId == model.id,
                onDownloadFullOnnx = { onDownloadHfModel(model, true) },
                onDownloadQuickProfile = { onDownloadHfModel(model, false) },
                onCancelDownload = { onCancelHfDownload(model.id) },
                onActivateModel = { onActivateHfModel(model) },
                onTestModelVoice = { onTestHfModelVoice(model) },
                onDeleteModel = { onDeleteHfModel(model.id) }
            )
        }

        // Live Hugging Face Hub Search Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SEARCH HUGGING FACE HUB (TEXT-TO-SPEECH API)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Cyan400,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = hfSearchQuery,
                            onValueChange = { hfSearchQuery = it },
                            placeholder = { Text("e.g. piper, kokoro, vits, onnx…") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("hf_hub_search_input")
                        )
                        Button(
                            onClick = { onSearchHfHub(hfSearchQuery) },
                            enabled = !isSearchingHfHub,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cyan400,
                                contentColor = Slate950
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("hf_hub_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSearchingHfHub) "…" else "Search", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (hfHubSearchResults.isNotEmpty()) {
            items(hfHubSearchResults, key = { "search_${it.id}" }) { model ->
                val status = hfModelStatuses[model.id] ?: DownloadedModelStatus(modelId = model.id)
                HuggingFaceModelCard(
                    model = model,
                    status = status,
                    isActiveModel = ttsState.activeHfModelId == model.id,
                    onDownloadFullOnnx = { onDownloadHfModel(model, true) },
                    onDownloadQuickProfile = { onDownloadHfModel(model, false) },
                    onCancelDownload = { onCancelHfDownload(model.id) },
                    onActivateModel = { onActivateHfModel(model) },
                    onTestModelVoice = { onTestHfModelVoice(model) },
                    onDeleteModel = { onDeleteHfModel(model.id) }
                )
            }
        }

        // Android System TextToSpeech Voices Card
        if (ttsState.availableSystemVoices.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ANDROID DEVICE SYSTEM TTS VOICES (${ttsState.availableSystemVoices.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ttsState.availableSystemVoices.forEach { sysVoice ->
                                FilterChip(
                                    selected = ttsState.selectedSystemVoiceName == sysVoice.name && ttsState.activeHfModelId == null,
                                    onClick = { onSelectSystemVoice(sysVoice.name) },
                                    label = {
                                        Text(
                                            text = "${sysVoice.localeDisplayName} • ${sysVoice.name.takeLast(10)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Interactive Reading Surface Preview Card
        item {
            val mode = settings.colorMode
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = mode.backgroundColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Amber400, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "LIVE ACCESSIBILITY PREVIEW • ${mode.title.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = mode.textColor.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = mode.textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. Universal offline reading adapts to your eyes with calibrated contrast, dyslexia-friendly tracking, and neural Text-to-Speech.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = mode.textColor,
                            fontFamily = if (settings.useMonospaceForText) JetBrainsMonoFontFamily else MaterialTheme.typography.bodyLarge.fontFamily,
                            fontSize = (16f * settings.fontScale).sp,
                            lineHeight = (16f * settings.fontScale * settings.lineSpacingMultiplier).sp,
                            letterSpacing = if (settings.dyslexiaFriendlySpacing) 1.3.sp else 0.2.sp
                        )
                    )
                }
            }
        }

        // 5 Calibrated Color Modes
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CALIBRATED READING COLOR MODES",
                        style = MaterialTheme.typography.labelSmall.copy(color = Amber400)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ReadingColorMode.entries.forEach { mode ->
                        val isSelected = settings.colorMode == mode
                        Surface(
                            color = mode.backgroundColor,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Amber400 else Color.Gray.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectColorMode(mode) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = mode.textColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = mode.textColor.copy(alpha = 0.8f)
                                        )
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = mode.textColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Typography & Dyslexia Controls
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TYPOGRAPHY, SCALING & VISUAL TRACKING",
                        style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Font Size Scale: ${(settings.fontScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    Slider(
                        value = settings.fontScale,
                        onValueChange = onFontScaleChange,
                        valueRange = 0.75f..1.75f
                    )

                    Text(
                        text = "Line Height Multiplier: ${String.format("%.2fx", settings.lineSpacingMultiplier)}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    Slider(
                        value = settings.lineSpacingMultiplier,
                        onValueChange = onLineSpacingChange,
                        valueRange = 1.15f..2.10f
                    )

                    AccessibilityToggleRow(
                        title = "Dyslexia-Friendly Character Tracking",
                        subtitle = "Increases letter spacing and paragraph separation",
                        checked = settings.dyslexiaFriendlySpacing,
                        onToggle = onToggleDyslexia
                    )
                    AccessibilityToggleRow(
                        title = "Draggable Reading Focus Ruler",
                        subtitle = "Highlights active line and dims surrounding text",
                        checked = settings.showReadingRuler,
                        onToggle = onToggleRuler
                    )
                    AccessibilityToggleRow(
                        title = "JetBrains Mono Font for Text",
                        subtitle = "Use high-legibility monospace font across text documents",
                        checked = settings.useMonospaceForText,
                        onToggle = onToggleMonospace
                    )
                    AccessibilityToggleRow(
                        title = "Blue Light Filter Shield (2700K Amber)",
                        subtitle = "Applies a warm amber optical filter across all document and comic viewers",
                        checked = settings.blueLightFilterEnabled || settings.colorMode == ReadingColorMode.BLUE_LIGHT_SHIELD,
                        onToggle = onToggleBlueLight
                    )
                    Text(
                        text = "Blue Light Shield Intensity: ${(settings.blueLightIntensity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Amber400)
                    )
                    Slider(
                        value = settings.blueLightIntensity,
                        onValueChange = onBlueLightIntensityChange,
                        valueRange = 0.10f..0.75f
                    )
                    AccessibilityToggleRow(
                        title = "PDF Dark Mode Color Matrix Inversion",
                        subtitle = "Transforms white PDF pages into low-glare dark pages",
                        checked = settings.invertPdfColors,
                        onToggle = onToggleInvertPdf
                    )
                    AccessibilityToggleRow(
                        title = "Comic Book Warm Eye-Care Filter",
                        subtitle = "Reduces blue light on bright CBZ and CBR comic panels",
                        checked = settings.comicWarmFilter,
                        onToggle = onToggleComicWarmth
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibilityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
fun CreateOfflineDocDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, content: String, asPdf: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("Field Inspection Report") }
    var content by remember {
        mutableStateOf(
            "# Field Inspection & Summary\n\n" +
                "- **Location**: Sector 04 Offline Station\n" +
                "- **Status**: All systems nominal\n\n" +
                "## Observations\n" +
                "1. Primary power array operating at 99.4% efficiency.\n" +
                "2. Local document & comic repository verified for offline access."
        )
    }
    var saveAsPdf by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text("Create Offline Document", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !saveAsPdf,
                        onClick = { saveAsPdf = false },
                        label = { Text("Markdown (.md)") }
                    )
                    FilterChip(
                        selected = saveAsPdf,
                        onClick = { saveAsPdf = true },
                        label = { Text("Paginated PDF (.pdf)") }
                    )
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Document Content") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(title, content, saveAsPdf)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Slate950)
            ) {
                Text(if (saveAsPdf) "Generate PDF" else "Save Note", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
