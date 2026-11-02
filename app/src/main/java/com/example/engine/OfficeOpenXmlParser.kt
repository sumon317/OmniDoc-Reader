package com.example.engine

import android.util.Xml
import com.example.data.DocxBlock
import com.example.data.ParsedDocxDocument
import com.example.data.ParsedPptxDeck
import com.example.data.ParsedTextDocument
import com.example.data.ParsedXlsxWorkbook
import com.example.data.PptxSlide
import com.example.data.XlsxSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import kotlin.math.max

object OfficeOpenXmlParser {

    /**
     * Parses a real .docx OpenXML archive (word/document.xml) into structured blocks.
     * Falls back gracefully if the file is plain text with a .doc/.docx extension.
     */
    suspend fun parseDocx(file: File): ParsedDocxDocument = withContext(Dispatchers.IO) {
        val defaultTitle = file.nameWithoutExtension.replace('_', ' ')
        val zipEntries = readZipEntries(file)
        val docXmlBytes = zipEntries["word/document.xml"]

        if (docXmlBytes == null) {
            // Fallback for plain text or non-zipped .doc files
            val rawText = runCatching { file.readText() }.getOrDefault("Unable to read document content.")
            val paragraphs = rawText.lines().filter { it.isNotBlank() }.map { line ->
                when {
                    line.startsWith("# ") -> DocxBlock.Heading(line.removePrefix("# ").trim(), 1)
                    line.startsWith("## ") -> DocxBlock.Heading(line.removePrefix("## ").trim(), 2)
                    line.startsWith("- ") || line.startsWith("* ") -> DocxBlock.BulletItem(line.drop(2).trim())
                    else -> DocxBlock.Paragraph(line.trim())
                }
            }
            val words = rawText.split(Regex("\\s+")).count { it.isNotBlank() }
            return@withContext ParsedDocxDocument(
                title = defaultTitle,
                blocks = paragraphs,
                wordCount = words,
                charCount = rawText.length,
                estimatedMinutes = max(1, words / 200),
                plainTextForTts = rawText
            )
        }

        val blocks = mutableListOf<DocxBlock>()
        val ttsBuilder = StringBuilder()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(docXmlBytes), "UTF-8")

            var inParagraph = false
            var inTable = false
            var inTableRow = false
            var inTableCell = false

            var currentParaStyle = ""
            var currentParaIsList = false
            var currentRunBold = false
            var currentRunItalic = false
            var paraBold = false
            var paraItalic = false
            val currentParaText = StringBuilder()

            val currentTableRows = mutableListOf<List<String>>()
            var currentRowCells = mutableListOf<String>()
            val currentCellText = StringBuilder()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawName = parser.name ?: ""
                val tag = rawName.substringAfterLast(':')

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "tbl" -> {
                                inTable = true
                                currentTableRows.clear()
                            }
                            "tr" -> if (inTable) {
                                inTableRow = true
                                currentRowCells = mutableListOf()
                            }
                            "tc" -> if (inTableRow) {
                                inTableCell = true
                                currentCellText.clear()
                            }
                            "p" -> if (!inTable) {
                                inParagraph = true
                                currentParaStyle = ""
                                currentParaIsList = false
                                paraBold = false
                                paraItalic = false
                                currentParaText.clear()
                            }
                            "pStyle" -> if (inParagraph) {
                                val styleVal = parser.getAttributeValue(null, "w:val")
                                    ?: parser.getAttributeValue(null, "val") ?: ""
                                currentParaStyle = styleVal
                            }
                            "numPr" -> if (inParagraph) {
                                currentParaIsList = true
                            }
                            "b" -> {
                                currentRunBold = true
                                if (inParagraph) paraBold = true
                            }
                            "i" -> {
                                currentRunItalic = true
                                if (inParagraph) paraItalic = true
                            }
                            "t" -> {
                                val textContent = parser.nextText()
                                if (inTableCell) {
                                    if (currentCellText.isNotEmpty() && !currentCellText.endsWith(" ")) {
                                        currentCellText.append(" ")
                                    }
                                    currentCellText.append(textContent)
                                } else if (inParagraph) {
                                    currentParaText.append(textContent)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tag) {
                            "r" -> {
                                currentRunBold = false
                                currentRunItalic = false
                            }
                            "tc" -> if (inTableCell) {
                                currentRowCells.add(currentCellText.toString().trim())
                                inTableCell = false
                            }
                            "tr" -> if (inTableRow) {
                                if (currentRowCells.isNotEmpty()) {
                                    currentTableRows.add(currentRowCells.toList())
                                }
                                inTableRow = false
                            }
                            "tbl" -> if (inTable) {
                                if (currentTableRows.isNotEmpty()) {
                                    val headers = currentTableRows.first()
                                    val dataRows = if (currentTableRows.size > 1) {
                                        currentTableRows.drop(1)
                                    } else {
                                        emptyList()
                                    }
                                    blocks.add(DocxBlock.TableBlock(headers, dataRows))
                                    ttsBuilder.append(headers.joinToString(", ")).append(". ")
                                    dataRows.forEach { row ->
                                        ttsBuilder.append(row.joinToString(", ")).append(". ")
                                    }
                                }
                                inTable = false
                            }
                            "p" -> if (inParagraph && !inTable) {
                                val text = currentParaText.toString().trim()
                                if (text.isNotEmpty()) {
                                    val styleLower = currentParaStyle.lowercase()
                                    val block = when {
                                        "title" in styleLower || "heading1" in styleLower || "h1" == styleLower ->
                                            DocxBlock.Heading(text, 1)
                                        "heading2" in styleLower || "h2" == styleLower || "subtitle" in styleLower ->
                                            DocxBlock.Heading(text, 2)
                                        "heading3" in styleLower || "h3" == styleLower ->
                                            DocxBlock.Heading(text, 3)
                                        "callout" in styleLower || text.startsWith("[NOTE]") || text.startsWith("IMPORTANT:") ->
                                            DocxBlock.CalloutBox(
                                                title = "Key Insight",
                                                body = text.removePrefix("[NOTE]").removePrefix("IMPORTANT:").trim()
                                            )
                                        "quote" in styleLower ->
                                            DocxBlock.Paragraph(text, isBold = false, isItalic = true, isQuote = true)
                                        currentParaIsList || text.startsWith("• ") ->
                                            DocxBlock.BulletItem(text.removePrefix("• ").trim())
                                        else ->
                                            DocxBlock.Paragraph(text, isBold = paraBold, isItalic = paraItalic)
                                    }
                                    blocks.add(block)
                                    ttsBuilder.append(text).append("\n\n")
                                }
                                inParagraph = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fullPlain = ttsBuilder.toString().trim()
        val words = fullPlain.split(Regex("\\s+")).count { it.isNotBlank() }

        ParsedDocxDocument(
            title = defaultTitle,
            blocks = blocks,
            wordCount = words,
            charCount = fullPlain.length,
            estimatedMinutes = max(1, words / 200),
            plainTextForTts = fullPlain.ifEmpty { "Empty Word Document" }
        )
    }

    /**
     * Parses a real .xlsx OpenXML workbook (xl/workbook.xml, xl/sharedStrings.xml, xl/worksheets/sheet*.xml)
     * or a .csv/.tsv spreadsheet file into a multi-sheet ParsedXlsxWorkbook.
     */
    suspend fun parseXlsxOrCsv(file: File): ParsedXlsxWorkbook = withContext(Dispatchers.IO) {
        val defaultTitle = file.nameWithoutExtension.replace('_', ' ')
        val ext = file.extension.lowercase()
        if (ext == "csv" || ext == "tsv") {
            return@withContext parseDelimitedSpreadsheet(file, if (ext == "tsv") '\t' else ',')
        }

        val zipEntries = readZipEntries(file)
        if (zipEntries.isEmpty() || zipEntries.keys.none { it.startsWith("xl/") }) {
            return@withContext parseDelimitedSpreadsheet(file, ',')
        }

        // 1. Parse xl/sharedStrings.xml if present
        val sharedStrings = mutableListOf<String>()
        zipEntries["xl/sharedStrings.xml"]?.let { bytes ->
            runCatching {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
                var inSi = false
                val sb = StringBuilder()
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = (parser.name ?: "").substringAfterLast(':')
                    if (ev == XmlPullParser.START_TAG && tag == "si") {
                        inSi = true
                        sb.clear()
                    } else if (ev == XmlPullParser.START_TAG && tag == "t" && inSi) {
                        sb.append(parser.nextText())
                    } else if (ev == XmlPullParser.END_TAG && tag == "si") {
                        sharedStrings.add(sb.toString())
                        inSi = false
                    }
                    ev = parser.next()
                }
            }
        }

        // 2. Parse sheet names from xl/workbook.xml
        val sheetNames = mutableListOf<String>()
        zipEntries["xl/workbook.xml"]?.let { bytes ->
            runCatching {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = (parser.name ?: "").substringAfterLast(':')
                    if (ev == XmlPullParser.START_TAG && tag == "sheet") {
                        val name = parser.getAttributeValue(null, "name") ?: "Sheet ${sheetNames.size + 1}"
                        sheetNames.add(name)
                    }
                    ev = parser.next()
                }
            }
        }

        // 3. Parse each worksheet in xl/worksheets/sheet*.xml
        val worksheetKeys = zipEntries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { key ->
                key.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: 999
            }

        val parsedSheets = mutableListOf<XlsxSheet>()
        var totalCells = 0
        val ttsBuilder = StringBuilder()

        worksheetKeys.forEachIndexed { index, key ->
            val sheetName = sheetNames.getOrNull(index) ?: "Sheet ${index + 1}"
            val bytes = zipEntries[key] ?: return@forEachIndexed
            val rowsMap = mutableListOf<MutableList<String>>()
            var maxCol = 0

            runCatching {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

                var currentRow = mutableListOf<String>()
                var cellType = ""
                var cellRef = ""
                var cellValue = ""

                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = (parser.name ?: "").substringAfterLast(':')
                    when (ev) {
                        XmlPullParser.START_TAG -> {
                            when (tag) {
                                "row" -> currentRow = mutableListOf()
                                "c" -> {
                                    cellType = parser.getAttributeValue(null, "t") ?: ""
                                    cellRef = parser.getAttributeValue(null, "r") ?: ""
                                    cellValue = ""
                                }
                                "v", "t" -> {
                                    cellValue = parser.nextText()
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (tag) {
                                "c" -> {
                                    val resolved = if (cellType == "s") {
                                        val sIdx = cellValue.toIntOrNull() ?: -1
                                        sharedStrings.getOrNull(sIdx) ?: cellValue
                                    } else {
                                        cellValue
                                    }
                                    val colIdx = columnIndexFromCellRef(cellRef)
                                    if (colIdx >= 0) {
                                        while (currentRow.size < colIdx) {
                                            currentRow.add("")
                                        }
                                    }
                                    currentRow.add(resolved)
                                    totalCells++
                                }
                                "row" -> {
                                    if (currentRow.any { it.isNotBlank() }) {
                                        maxCol = max(maxCol, currentRow.size)
                                        rowsMap.add(currentRow)
                                    }
                                }
                            }
                        }
                    }
                    ev = parser.next()
                }
            }

            if (rowsMap.isNotEmpty()) {
                val normalized = rowsMap.map { row ->
                    if (row.size < maxCol) row + List(maxCol - row.size) { "" } else row
                }
                val headers = normalized.first()
                val bodyRows = if (normalized.size > 1) normalized.drop(1) else emptyList()
                parsedSheets.add(XlsxSheet(name = sheetName, headers = headers, rows = bodyRows))

                ttsBuilder.append("Worksheet $sheetName. Columns: ${headers.joinToString(", ")}. ")
                bodyRows.take(12).forEach { r ->
                    ttsBuilder.append(r.joinToString(", ")).append(". ")
                }
            }
        }

        ParsedXlsxWorkbook(
            title = defaultTitle,
            sheets = parsedSheets,
            totalCells = totalCells,
            plainTextForTts = ttsBuilder.toString().ifEmpty { "Spreadsheet $defaultTitle" }
        )
    }

    private fun parseDelimitedSpreadsheet(file: File, delimiter: Char): ParsedXlsxWorkbook {
        val defaultTitle = file.nameWithoutExtension.replace('_', ' ')
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParsedXlsxWorkbook(defaultTitle, emptyList(), 0, "Empty spreadsheet")
        }
        val parsedRows = lines.map { line ->
            line.split(delimiter).map { it.trim().removeSurrounding("\"") }
        }
        val maxCol = parsedRows.maxOfOrNull { it.size } ?: 1
        val normalized = parsedRows.map { row ->
            if (row.size < maxCol) row + List(maxCol - row.size) { "" } else row
        }
        val headers = normalized.first()
        val dataRows = normalized.drop(1)
        val sheet = XlsxSheet(name = "Main Sheet", headers = headers, rows = dataRows)
        val tts = "Spreadsheet $defaultTitle with ${dataRows.size} rows and columns ${headers.joinToString(", ")}."
        return ParsedXlsxWorkbook(
            title = defaultTitle,
            sheets = listOf(sheet),
            totalCells = normalized.size * maxCol,
            plainTextForTts = tts
        )
    }

    private fun columnIndexFromCellRef(cellRef: String): Int {
        if (cellRef.isEmpty()) return -1
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        var col = 0
        for (ch in letters) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return col - 1
    }

    /**
     * Parses a real .pptx OpenXML presentation archive (ppt/slides/slide*.xml and ppt/notesSlides/notesSlide*.xml).
     */
    suspend fun parsePptx(file: File): ParsedPptxDeck = withContext(Dispatchers.IO) {
        val defaultTitle = file.nameWithoutExtension.replace('_', ' ')
        val zipEntries = readZipEntries(file)

        val slideKeys = zipEntries.keys
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy { key ->
                key.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 999
            }

        val accentPalette = listOf("#F59E0B", "#06B6D4", "#10B981", "#8B5CF6", "#EC4899", "#3B82F6")
        val slides = mutableListOf<PptxSlide>()
        val ttsBuilder = StringBuilder()

        slideKeys.forEachIndexed { index, key ->
            val slideNum = index + 1
            val slideBytes = zipEntries[key] ?: return@forEachIndexed
            val notesKey = "ppt/notesSlides/notesSlide$slideNum.xml"
            val notesBytes = zipEntries[notesKey]

            val shapeTexts = mutableListOf<String>()
            var explicitTitle = ""
            var explicitSubtitle = ""
            var explicitMetric: String? = null

            runCatching {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(slideBytes), "UTF-8")

                var currentPhType = ""
                val paragraphBuilder = StringBuilder()
                var inParagraph = false

                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = (parser.name ?: "").substringAfterLast(':')
                    when (ev) {
                        XmlPullParser.START_TAG -> {
                            when (tag) {
                                "sp" -> currentPhType = ""
                                "ph" -> {
                                    currentPhType = parser.getAttributeValue(null, "type") ?: ""
                                }
                                "p" -> {
                                    inParagraph = true
                                    paragraphBuilder.clear()
                                }
                                "t" -> {
                                    val t = parser.nextText()
                                    if (inParagraph) {
                                        paragraphBuilder.append(t)
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tag == "p" && inParagraph) {
                                val text = paragraphBuilder.toString().trim()
                                if (text.isNotEmpty()) {
                                    when {
                                        currentPhType == "title" || currentPhType == "ctrTitle" -> {
                                            if (explicitTitle.isEmpty()) explicitTitle = text
                                            else shapeTexts.add(text)
                                        }
                                        currentPhType == "subTitle" -> {
                                            if (explicitSubtitle.isEmpty()) explicitSubtitle = text
                                            else shapeTexts.add(text)
                                        }
                                        text.startsWith("METRIC:") -> {
                                            explicitMetric = text.removePrefix("METRIC:").trim()
                                        }
                                        else -> shapeTexts.add(text)
                                    }
                                }
                                inParagraph = false
                            }
                        }
                    }
                    ev = parser.next()
                }
            }

            val title = explicitTitle.ifEmpty {
                shapeTexts.firstOrNull() ?: "Slide $slideNum"
            }
            val remaining = if (explicitTitle.isEmpty() && shapeTexts.isNotEmpty()) {
                shapeTexts.drop(1)
            } else {
                shapeTexts
            }
            val subtitle = explicitSubtitle.ifEmpty {
                remaining.firstOrNull()?.takeIf { remaining.size > 1 && it.length < 90 } ?: ""
            }
            val bullets = if (explicitSubtitle.isEmpty() && subtitle.isNotEmpty()) {
                remaining.drop(1)
            } else {
                remaining
            }

            // Extract speaker notes if available
            val notesBuilder = StringBuilder()
            if (notesBytes != null) {
                runCatching {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(ByteArrayInputStream(notesBytes), "UTF-8")
                    var ev = parser.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        val tag = (parser.name ?: "").substringAfterLast(':')
                        if (ev == XmlPullParser.START_TAG && tag == "t") {
                            val t = parser.nextText().trim()
                            if (t.isNotEmpty() && t != slideNum.toString()) {
                                if (notesBuilder.isNotEmpty()) notesBuilder.append(" ")
                                notesBuilder.append(t)
                            }
                        }
                        ev = parser.next()
                    }
                }
            }

            val slideObj = PptxSlide(
                slideNumber = slideNum,
                title = title,
                subtitle = subtitle,
                bullets = bullets,
                speakerNotes = notesBuilder.toString().trim(),
                accentHex = accentPalette[index % accentPalette.size],
                keyMetric = explicitMetric
            )
            slides.add(slideObj)

            ttsBuilder.append("Slide $slideNum: $title. ")
            if (subtitle.isNotEmpty()) ttsBuilder.append("$subtitle. ")
            bullets.forEach { b -> ttsBuilder.append("$b. ") }
            if (slideObj.speakerNotes.isNotEmpty()) {
                ttsBuilder.append("Speaker notes: ${slideObj.speakerNotes}. ")
            }
        }

        ParsedPptxDeck(
            title = defaultTitle,
            slides = slides,
            plainTextForTts = ttsBuilder.toString().ifEmpty { "Presentation $defaultTitle" }
        )
    }

    /**
     * Parses any .txt, .md, .json, .xml, or code file into a ParsedTextDocument.
     */
    suspend fun parseTextFile(file: File): ParsedTextDocument = withContext(Dispatchers.IO) {
        val raw = runCatching { file.readText() }.getOrDefault("")
        val lines = raw.lines()
        val words = raw.split(Regex("\\s+")).count { it.isNotBlank() }
        val ext = file.extension.lowercase()
        val isMarkdown = ext == "md" || ext == "markdown" || raw.contains("\n# ") || raw.startsWith("# ")
        ParsedTextDocument(
            title = file.nameWithoutExtension.replace('_', ' '),
            content = raw,
            lines = lines,
            isMarkdown = isMarkdown,
            wordCount = words,
            charCount = raw.length,
            estimatedMinutes = max(1, words / 200)
        )
    }

    private fun readZipEntries(file: File): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        if (!file.exists()) return map
        runCatching {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        map[entry.name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return map
    }
}
