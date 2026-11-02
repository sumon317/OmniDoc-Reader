package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.example.data.ComicArchiveInfo
import com.example.data.DocFormat
import com.example.data.DocxBlock
import com.example.data.ParsedDocxDocument
import com.example.data.ParsedPptxDeck
import com.example.data.ParsedTextDocument
import com.example.data.ParsedXlsxWorkbook
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile

object PdfAndComicEngine {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /**
     * Returns total page count of a PDF file using Android's native PdfRenderer.
     */
    suspend fun getPdfPageCount(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
    }

    /**
     * Renders a specific page of a PDF file to a high-resolution Bitmap, with optional
     * accessibility color inversion / night mode filter.
     */
    suspend fun renderPdfPage(
        file: File,
        pageIndex: Int,
        scaleFactor: Float = 2.0f,
        invertColors: Boolean = false,
        warmSepia: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val width = (page.width * scaleFactor).toInt().coerceIn(400, 2600)
                        val height = (page.height * scaleFactor).toInt().coerceIn(400, 3400)
                        val rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(rawBitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        if (!invertColors && !warmSepia) {
                            return@withContext rawBitmap
                        }

                        val filteredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val filterCanvas = Canvas(filteredBitmap)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        val matrix = when {
                            invertColors -> ColorMatrix(
                                floatArrayOf(
                                    -0.88f, 0f, 0f, 0f, 240f,
                                    0f, -0.88f, 0f, 0f, 242f,
                                    0f, 0f, -0.85f, 0f, 248f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                            warmSepia -> ColorMatrix(
                                floatArrayOf(
                                    0.96f, 0.02f, 0.0f, 0f, 0f,
                                    0.0f, 0.92f, 0.02f, 0f, 0f,
                                    0.0f, 0.0f, 0.82f, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                            else -> ColorMatrix()
                        }
                        paint.colorFilter = ColorMatrixColorFilter(matrix)
                        filterCanvas.drawBitmap(rawBitmap, 0f, 0f, paint)
                        rawBitmap.recycle()
                        filteredBitmap
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * Inspects a .cbz or .cbr comic archive (detecting ZIP vs RAR magic bytes automatically)
     * and extracts page entries plus optional ComicInfo.xml metadata.
     */
    suspend fun inspectComicArchive(file: File, format: DocFormat): ComicArchiveInfo = withContext(Dispatchers.IO) {
        val defaultTitle = file.nameWithoutExtension.replace('_', ' ')
        if (!file.exists()) {
            return@withContext ComicArchiveInfo(
                filePath = file.absolutePath,
                format = format,
                title = defaultTitle,
                series = defaultTitle,
                writer = "Unknown Creator",
                summary = "Offline Comic Archive",
                pageCount = 0,
                pageEntryNames = emptyList(),
                archiveEngineType = "ZIP"
            )
        }

        val headerBytes = ByteArray(8)
        runCatching {
            FileInputStream(file).use { it.read(headerBytes) }
        }
        val isRarMagic = headerBytes[0] == 0x52.toByte() &&
            headerBytes[1] == 0x61.toByte() &&
            headerBytes[2] == 0x72.toByte() &&
            headerBytes[3] == 0x21.toByte()

        if (isRarMagic) {
            // Parse using junrar Archive
            val pageNames = mutableListOf<String>()
            var comicInfoBytes: ByteArray? = null
            runCatching {
                Archive(file).use { archive ->
                    var fh = archive.nextFileHeader()
                    while (fh != null) {
                        if (!fh.isDirectory) {
                            val name = fh.fileName.replace('\\', '/')
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in imageExtensions && !name.substringAfterLast('/').startsWith(".")) {
                                pageNames.add(name)
                            } else if (name.endsWith("ComicInfo.xml", ignoreCase = true)) {
                                val baos = ByteArrayOutputStream()
                                archive.extractFile(fh, baos)
                                comicInfoBytes = baos.toByteArray()
                            }
                        }
                        fh = archive.nextFileHeader()
                    }
                }
            }
            val sortedPages = pageNames.sortedWith(naturalOrderComparator)
            val meta = parseComicInfoXml(comicInfoBytes, defaultTitle)
            return@withContext ComicArchiveInfo(
                filePath = file.absolutePath,
                format = format,
                title = meta.first,
                series = meta.second,
                writer = meta.third,
                summary = meta.fourth,
                pageCount = sortedPages.size,
                pageEntryNames = sortedPages,
                archiveEngineType = "RAR"
            )
        } else {
            // Parse using standard ZipFile (handles .cbz and ZIP-backed .cbr archives)
            val pageNames = mutableListOf<String>()
            var comicInfoBytes: ByteArray? = null
            runCatching {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            val name = entry.name.replace('\\', '/')
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in imageExtensions && !name.substringAfterLast('/').startsWith(".")) {
                                pageNames.add(name)
                            } else if (name.endsWith("ComicInfo.xml", ignoreCase = true)) {
                                comicInfoBytes = zip.getInputStream(entry).use { it.readBytes() }
                            }
                        }
                    }
                }
            }
            val sortedPages = pageNames.sortedWith(naturalOrderComparator)
            val meta = parseComicInfoXml(comicInfoBytes, defaultTitle)
            return@withContext ComicArchiveInfo(
                filePath = file.absolutePath,
                format = format,
                title = meta.first,
                series = meta.second,
                writer = meta.third,
                summary = meta.fourth,
                pageCount = sortedPages.size,
                pageEntryNames = sortedPages,
                archiveEngineType = "ZIP"
            )
        }
    }

    /**
     * Decodes a specific comic page from a CBZ or CBR archive into a Bitmap, applying optional
     * eye-care warm tint filter.
     */
    suspend fun renderComicPage(
        archiveInfo: ComicArchiveInfo,
        pageIndex: Int,
        warmFilter: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val entryName = archiveInfo.pageEntryNames.getOrNull(pageIndex) ?: return@withContext null
        val file = File(archiveInfo.filePath)
        if (!file.exists()) return@withContext null

        val imageBytes: ByteArray? = if (archiveInfo.archiveEngineType == "RAR") {
            runCatching {
                Archive(file).use { archive ->
                    var fh = archive.nextFileHeader()
                    var found: ByteArray? = null
                    while (fh != null) {
                        val normalized = fh.fileName.replace('\\', '/')
                        if (!fh.isDirectory && normalized == entryName) {
                            val baos = ByteArrayOutputStream()
                            archive.extractFile(fh, baos)
                            found = baos.toByteArray()
                            break
                        }
                        fh = archive.nextFileHeader()
                    }
                    found
                }
            }.getOrNull()
        } else {
            runCatching {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry(entryName)
                        ?: zip.entries().asSequence().firstOrNull { it.name.replace('\\', '/') == entryName }
                    entry?.let { zip.getInputStream(it).use { stream -> stream.readBytes() } }
                }
            }.getOrNull()
        }

        if (imageBytes == null || imageBytes.isEmpty()) return@withContext null

        // Decode with safe bounds check so large comic scans never OOM
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOpts)
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > 2048 || boundsOpts.outHeight / sampleSize > 2800) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts)
            ?: return@withContext null

        if (!warmFilter) return@withContext raw

        val tinted = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tinted)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0.98f, 0.02f, 0f, 0f, 0f,
                        0f, 0.93f, 0.02f, 0f, 0f,
                        0f, 0f, 0.83f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        canvas.drawBitmap(raw, 0f, 0f, paint)
        raw.recycle()
        tinted
    }

    /**
     * Exports any parsed DOCX, TXT, XLSX, or PPTX content into a clean multi-page offline PDF file.
     */
    suspend fun exportLinesToPdf(
        outputDir: File,
        fileNamePrefix: String,
        title: String,
        subtitle: String,
        paragraphs: List<String>
    ): File = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val sanitized = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(outputDir, "${sanitized}_Export.pdf")

        val pdfDoc = PdfDocument()
        val pageWidth = 595 // A4 standard points
        val pageHeight = 842

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 20f
            isFakeBoldText = true
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 11f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11.5f
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 158, 11)
        }

        // Wrap paragraphs into lines of ~78 chars
        val wrappedLines = mutableListOf<String>()
        paragraphs.forEach { para ->
            if (para.isBlank()) {
                wrappedLines.add("")
            } else {
                val words = para.split(" ")
                val currentLine = StringBuilder()
                for (w in words) {
                    if (currentLine.length + w.length + 1 > 76) {
                        wrappedLines.add(currentLine.toString())
                        currentLine.clear()
                    }
                    if (currentLine.isNotEmpty()) currentLine.append(" ")
                    currentLine.append(w)
                }
                if (currentLine.isNotEmpty()) wrappedLines.add(currentLine.toString())
                wrappedLines.add("")
            }
        }

        val linesPerPage = 38
        val chunks = if (wrappedLines.isEmpty()) listOf(listOf("Empty Document")) else wrappedLines.chunked(linesPerPage)

        chunks.forEachIndexed { idx, pageLines ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(Color.rgb(250, 248, 245))
            canvas.drawRect(44f, 36f, 551f, 40f, accentPaint)

            var y = 68f
            if (idx == 0) {
                canvas.drawText(title.take(48), 44f, y, titlePaint)
                y += 20f
                canvas.drawText(subtitle.take(75), 44f, y, subPaint)
                y += 28f
            } else {
                canvas.drawText("$title — Page ${idx + 1}", 44f, y, subPaint)
                y += 24f
            }

            pageLines.forEach { line ->
                if (y < pageHeight - 52f) {
                    canvas.drawText(line, 44f, y, bodyPaint)
                    y += 17f
                }
            }

            canvas.drawText("Generated Offline by OmniDoc Reader • Page ${idx + 1} of ${chunks.size}", 44f, pageHeight - 28f, subPaint)
            pdfDoc.finishPage(page)
        }

        FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        // Save full readable text sidecar for instant multi-page offline TTS playback
        runCatching {
            File(outFile.parentFile, "${outFile.name}.txt").writeText(
                "$title. $subtitle.\n\n" + paragraphs.joinToString("\n")
            )
        }
        outFile
    }

    /**
     * Extracts readable text from a PDF file for Android TextToSpeech playback.
     * Checks for a cached text sidecar first, then scans PDF text literal streams `( ... )`.
     */
    suspend fun extractPdfTextForTts(
        file: File,
        fallbackTitle: String,
        fallbackSummary: String
    ): String = withContext(Dispatchers.IO) {
        val sidecar = File(file.parentFile, "${file.name}.txt")
        if (sidecar.exists() && sidecar.length() > 10L) {
            return@withContext runCatching { sidecar.readText() }.getOrDefault("$fallbackTitle. $fallbackSummary")
        }

        // Scan raw PDF bytes for printable parenthesized text runs (...) used by PDF Tj/TJ operators
        val extracted = runCatching {
            val rawBytes = file.readBytes()
            val rawAscii = String(rawBytes, Charsets.ISO_8859_1)
            val regex = Regex("\\(([^()\\\\]{4,120})\\)")
            val matches = regex.findAll(rawAscii)
                .map { it.groupValues[1].trim() }
                .filter { str ->
                    str.any { it.isLetter() } &&
                        str.all { ch -> ch.code in 32..126 } &&
                        !str.startsWith("http") &&
                        !str.contains("Adobe") &&
                        !str.contains("Skia")
                }
                .toList()
            if (matches.size >= 4) {
                matches.joinToString(". ")
            } else {
                ""
            }
        }.getOrDefault("")

        if (extracted.isNotBlank()) {
            "$fallbackTitle. $extracted"
        } else {
            "$fallbackTitle. $fallbackSummary"
        }
    }

    /**
     * Extracts per-page searchable text for PDF documents so the in-viewer search bar
     * can find strings, jump to matching pages, and highlight matches with Compose text styling.
     */
    suspend fun extractPdfPagesText(
        file: File,
        pageCount: Int,
        fallbackTitle: String,
        fallbackSummary: String
    ): List<String> = withContext(Dispatchers.IO) {
        val fullText = extractPdfTextForTts(file, fallbackTitle, fallbackSummary)
        val totalPages = pageCount.coerceAtLeast(1)
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return@withContext List(totalPages) { idx ->
                "Page ${idx + 1}: $fallbackTitle — $fallbackSummary"
            }
        }
        val sentences = fullText
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val chunkSize = (sentences.size / totalPages).coerceAtLeast(1)
        List(totalPages) { pageIdx ->
            val start = pageIdx * chunkSize
            val end = if (pageIdx == totalPages - 1) sentences.size else ((pageIdx + 1) * chunkSize).coerceAtMost(sentences.size)
            if (start < sentences.size && start < end) {
                sentences.subList(start, end).joinToString(" ")
            } else {
                sentences.getOrElse(pageIdx % sentences.size) { "$fallbackTitle (Page ${pageIdx + 1})" }
            }
        }
    }

    private fun parseComicInfoXml(
        bytes: ByteArray?,
        defaultTitle: String
    ): Quadruple<String, String, String, String> {
        if (bytes == null) {
            return Quadruple(
                defaultTitle,
                defaultTitle,
                "OmniDoc Studio Press",
                "Offline digital comic archive with high-resolution sequential art panels."
            )
        }
        var title = defaultTitle
        var series = defaultTitle
        var writer = "OmniDoc Studio Press"
        var summary = "Offline digital comic archive."
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "Title" -> title = parser.nextText().trim().ifEmpty { defaultTitle }
                        "Series" -> series = parser.nextText().trim().ifEmpty { defaultTitle }
                        "Writer" -> writer = parser.nextText().trim().ifEmpty { writer }
                        "Summary" -> summary = parser.nextText().trim().ifEmpty { summary }
                    }
                }
                ev = parser.next()
            }
        }
        return Quadruple(title, series, writer, summary)
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private val naturalOrderComparator = Comparator<String> { a, b ->
        val numRegex = Regex("(\\d+)")
        val aNums = numRegex.findAll(a).mapNotNull { it.value.toIntOrNull() }.toList()
        val bNums = numRegex.findAll(b).mapNotNull { it.value.toIntOrNull() }.toList()
        if (aNums.isNotEmpty() && bNums.isNotEmpty() && aNums.last() != bNums.last()) {
            aNums.last().compareTo(bNums.last())
        } else {
            a.compareTo(b, ignoreCase = true)
        }
    }
}
