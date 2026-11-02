package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import com.example.data.AnnotationEntity
import com.example.data.BookmarkEntity
import com.example.data.DocFormat
import com.example.data.DocumentEntity
import com.example.data.OmniDocDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OfflineSampleGenerator {

    suspend fun ensureSeeded(context: Context, dao: OmniDocDao) = withContext(Dispatchers.IO) {
        if (dao.getDocumentCount() > 0) return@withContext

        val sampleDir = File(context.filesDir, "sample_library").apply { mkdirs() }

        // 1. Generate Real Multi-Page PDF
        val pdfFile = File(sampleDir, "Architecture_Field_Guide_2026.pdf")
        generateSamplePdf(pdfFile)
        val pdfId = dao.insertDocument(
            DocumentEntity(
                title = "Architecture Field Guide 2026",
                fileName = pdfFile.name,
                localFilePath = pdfFile.absolutePath,
                format = DocFormat.PDF.name,
                fileSizeBytes = pdfFile.length(),
                totalUnits = 4,
                currentUnit = 0,
                progressPercent = 25,
                isFavorite = true,
                isPinnedOffline = true,
                tags = "Engineering,Blueprint,Offline",
                summarySnippet = "4-page illustrated engineering handbook with system topology diagrams, latency budgets, and edge storage benchmarks."
            )
        )

        // 2. Generate Real OpenXML DOCX
        val docxFile = File(sampleDir, "Project_Hyperion_Proposal.docx")
        generateSampleDocx(docxFile)
        val docxId = dao.insertDocument(
            DocumentEntity(
                title = "Project Hyperion Proposal",
                fileName = docxFile.name,
                localFilePath = docxFile.absolutePath,
                format = DocFormat.DOCX.name,
                fileSizeBytes = docxFile.length(),
                totalUnits = 14,
                currentUnit = 3,
                progressPercent = 30,
                isFavorite = true,
                isPinnedOffline = true,
                tags = "Product,Spec,Executive",
                summarySnippet = "Next-generation satellite mesh & offline edge synchronization specification with milestone tables and security architecture."
            )
        )

        // 3. Generate Real OpenXML XLSX (with 3 worksheets)
        val xlsxFile = File(sampleDir, "Global_Supply_Metrics_Q4.xlsx")
        generateSampleXlsx(xlsxFile)
        dao.insertDocument(
            DocumentEntity(
                title = "Global Supply Metrics Q4",
                fileName = xlsxFile.name,
                localFilePath = xlsxFile.absolutePath,
                format = DocFormat.XLSX.name,
                fileSizeBytes = xlsxFile.length(),
                totalUnits = 3,
                currentUnit = 0,
                progressPercent = 40,
                isFavorite = false,
                isPinnedOffline = true,
                tags = "Finance,Analytics,Q4",
                summarySnippet = "3-sheet workbook covering Regional Revenue, Warehouse Inventory Audit, and Unit Economics with live column statistics."
            )
        )

        // 4. Generate Real OpenXML PPTX (with 5 slides + speaker notes)
        val pptxFile = File(sampleDir, "NextGen_Spatial_OS_Deck.pptx")
        generateSamplePptx(pptxFile)
        dao.insertDocument(
            DocumentEntity(
                title = "NextGen Spatial OS Deck",
                fileName = pptxFile.name,
                localFilePath = pptxFile.absolutePath,
                format = DocFormat.PPTX.name,
                fileSizeBytes = pptxFile.length(),
                totalUnits = 5,
                currentUnit = 1,
                progressPercent = 40,
                isFavorite = true,
                isPinnedOffline = true,
                tags = "Keynote,Design,Slides",
                summarySnippet = "5-slide executive keynote deck with speaker notes, KPI callouts, hardware latency targets, and rollout roadmap."
            )
        )

        // 5. Generate Rich Markdown / Text File
        val txtFile = File(sampleDir, "Offline_Accessibility_Handbook.md")
        generateSampleMarkdown(txtFile)
        dao.insertDocument(
            DocumentEntity(
                title = "Offline Accessibility Handbook",
                fileName = txtFile.name,
                localFilePath = txtFile.absolutePath,
                format = DocFormat.TXT.name,
                fileSizeBytes = txtFile.length(),
                totalUnits = 58,
                currentUnit = 0,
                progressPercent = 15,
                isFavorite = false,
                isPinnedOffline = true,
                tags = "Guide,Markdown,Accessibility",
                summarySnippet = "Complete guide to offline Text-to-Speech, Dyslexia-friendly spacing, High-Contrast modes, and Reading Focus Rulers."
            )
        )

        // 6. Generate Real CBZ Comic Book Archive (5 high-res illustrated pages + ComicInfo.xml)
        val cbzFile = File(sampleDir, "Starlight_Vanguard_Issue_01.cbz")
        generateSampleComicArchive(cbzFile, isCyberpunk = false, pageCount = 5)
        val cbzId = dao.insertDocument(
            DocumentEntity(
                title = "Starlight Vanguard #01",
                fileName = cbzFile.name,
                localFilePath = cbzFile.absolutePath,
                format = DocFormat.CBZ.name,
                fileSizeBytes = cbzFile.length(),
                totalUnits = 5,
                currentUnit = 0,
                progressPercent = 20,
                isFavorite = true,
                isPinnedOffline = true,
                tags = "Sci-Fi,Comic,CBZ",
                summarySnippet = "Commander Lyra Vance navigates the shattered orbital rings of Kepler-442b in this 5-page full-color sci-fi comic issue."
            )
        )

        // 7. Generate Real CBR Comic Book Archive (4 full-color cyberpunk manga/comic pages)
        val cbrFile = File(sampleDir, "Cyber_Ronin_Chronicles_Vol1.cbr")
        generateSampleComicArchive(cbrFile, isCyberpunk = true, pageCount = 4)
        dao.insertDocument(
            DocumentEntity(
                title = "Cyber Ronin Chronicles Vol. 1",
                fileName = cbrFile.name,
                localFilePath = cbrFile.absolutePath,
                format = DocFormat.CBR.name,
                fileSizeBytes = cbrFile.length(),
                totalUnits = 4,
                currentUnit = 0,
                progressPercent = 25,
                isFavorite = false,
                isPinnedOffline = true,
                tags = "Cyberpunk,Manga,CBR",
                summarySnippet = "Neon-drenched rooftop pursuit across Neo-Kyoto Sector 9 in this 4-page sequential art comic archive."
            )
        )

        // Seed helpful initial Bookmarks & Annotations
        dao.insertBookmark(
            BookmarkEntity(
                documentId = pdfId,
                documentTitle = "Architecture Field Guide 2026",
                format = DocFormat.PDF.name,
                unitIndex = 1,
                label = "Zero-Copy Memory Bus Diagram",
                excerpt = "Page 2: Core pipeline latency targets under 4.2ms at 99.9th percentile."
            )
        )
        dao.insertBookmark(
            BookmarkEntity(
                documentId = cbzId,
                documentTitle = "Starlight Vanguard #01",
                format = DocFormat.CBZ.name,
                unitIndex = 2,
                label = "Kepler Ring Anomaly Reveal",
                excerpt = "Page 3: Full-spread arrival at the Crystalline Citadel."
            )
        )
        dao.insertAnnotation(
            AnnotationEntity(
                documentId = docxId,
                documentTitle = "Project Hyperion Proposal",
                unitIndex = 0,
                noteText = "Verify offline AES-256 key rotation schedule with hardware security team before Q3 freeze.",
                highlightColorHex = "#F59E0B"
            )
        )
        dao.insertAnnotation(
            AnnotationEntity(
                documentId = pdfId,
                documentTitle = "Architecture Field Guide 2026",
                unitIndex = 2,
                noteText = "Benchmark confirms 62% battery reduction when using vectorized tile caching.",
                highlightColorHex = "#06B6D4"
            )
        )
    }

    private fun generateSamplePdf(file: File) {
        val pdf = PdfDocument()
        val width = 595
        val height = 842

        val bgPaint = Paint().apply { color = Color.rgb(250, 248, 244) }
        val headerBandPaint = Paint().apply { color = Color.rgb(15, 23, 42) }
        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 158, 11) }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(6, 182, 212) }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 245, 249) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val titleWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            isFakeBoldText = true
        }
        val subCyan = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 211, 238)
            textSize = 12f
            isFakeBoldText = true
        }
        val h1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 11.5f
        }
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
            isFakeBoldText = true
        }

        val pageTitles = listOf(
            "01 // EXECUTIVE SYSTEM TOPOLOGY",
            "02 // ZERO-COPY RENDERING PIPELINE",
            "03 // OFFLINE STORAGE & FAULT TOLERANCE",
            "04 // ACCESSIBILITY & TELEMETRY BENCHMARKS"
        )

        pageTitles.forEachIndexed { pageIdx, sectionTitle ->
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIdx + 1).create()
            val page = pdf.startPage(pageInfo)
            val c = page.canvas

            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Top Banner
            c.drawRect(0f, 0f, width.toFloat(), 132f, headerBandPaint)
            c.drawRect(0f, 128f, width.toFloat(), 132f, amberPaint)
            c.drawText("OMNIDOC ENGINEERING PRESS • EDITION 2026", 42f, 48f, subCyan)
            c.drawText("Architecture Field Guide", 42f, 82f, titleWhite)
            c.drawText("Page ${pageIdx + 1} of 4  •  $sectionTitle", 42f, 108f, bodyPaint.apply { color = Color.rgb(203, 213, 225) })
            bodyPaint.color = Color.rgb(51, 65, 85)

            var y = 176f
            c.drawText(sectionTitle, 42f, y, h1Paint)
            y += 26f

            val paragraphs = when (pageIdx) {
                0 -> listOf(
                    "Modern mobile workstations demand instantaneous access to heterogeneous document formats—spanning paginated PDF blueprints, OpenXML office specifications, multi-sheet financial ledgers, and high-resolution sequential art archives—without relying on cloud round-trips.",
                    "The OmniDoc Core Engine isolates format decoders into deterministic zero-allocation streams. Each document is indexed into a local SQLite WAL catalog while rendering tiles directly to hardware-backed bitmaps.",
                    "Key Architectural Pillars:",
                    "  • Deterministic Local Caching: Every external URI is mirrored into encrypted app-private storage.",
                    "  • Adaptive Color Matrix Pipeline: Real-time LUT transformation for AMOLED Night & Sepia modes.",
                    "  • Sub-Millisecond Zip & RAR Seek: Random-access stream decoding for CBZ and CBR comic archives."
                )
                1 -> listOf(
                    "Traditional document viewers decode entire files into heap memory, triggering frequent garbage collection pauses on high-density displays. Our tiled viewport compositor renders only visible regions at 2.0x supersampled DPI.",
                    "Pipeline Stage Breakdown:",
                    "  1. Descriptor Acquisition: ParcelFileDescriptor opened in read-only memory-mapped mode.",
                    "  2. Vector Rasterization: Native PdfRenderer / XmlPullParser extracts layout geometry.",
                    "  3. Accessibility Post-Process: ColorMatrix and Reading Focus Ruler overlays applied at 120Hz."
                )
                2 -> listOf(
                    "Offline resilience is treated as a first-class invariant. All user bookmarks, page progress percentages, and color-coded study annotations are persisted immediately via Room coroutines.",
                    "Archive Format Matrix:",
                    "  • PDF (Portable Document Format): Native kernel-backed page rasterizer + PDF export engine.",
                    "  • DOCX / XLSX / PPTX (ECMA-376 OpenXML): Streaming XML pull parser with shared string resolution.",
                    "  • CBZ / CBR (Comic Book Archives): Automatic magic-byte detection for ZIP and RAR headers."
                )
                else -> listOf(
                    "Visual ergonomics directly impact comprehension during extended study sessions. The built-in Accessibility Suite combines five calibrated color temperatures with offline neural Text-to-Speech synthesis.",
                    "Verified Field Metrics across 120 Test Devices:",
                    "  • Cold Document Open Time: 38ms average (PDF 50-page technical manual).",
                    "  • Memory Footprint Ceiling: < 42 MB RSS during 4K comic double-page spread zoom.",
                    "  • Offline TTS Utterance Latency: < 18ms per sentence transition."
                )
            }

            paragraphs.forEach { para ->
                val words = para.split(" ")
                val line = StringBuilder()
                for (w in words) {
                    if (line.length + w.length + 1 > 74) {
                        c.drawText(line.toString(), 42f, y, bodyPaint)
                        y += 18f
                        line.clear()
                    }
                    if (line.isNotEmpty()) line.append(" ")
                    line.append(w)
                }
                if (line.isNotEmpty()) {
                    c.drawText(line.toString(), 42f, y, bodyPaint)
                    y += 24f
                }
            }

            // Draw Visual Technical Diagram Card on every page
            y += 10f
            val cardRect = RectF(42f, y, width - 42f, y + 210f)
            c.drawRoundRect(cardRect, 12f, 12f, cardPaint)
            c.drawRoundRect(cardRect, 12f, 12f, borderPaint)

            c.drawText("FIG ${pageIdx + 1}.0 — TELEMETRY & ARCHITECTURE DIAGRAM", 62f, y + 30f, monoPaint)

            // Draw nodes & bars inside diagram
            val barColors = listOf(amberPaint, cyanPaint, headerBandPaint)
            for (i in 0..2) {
                val boxTop = y + 52f + i * 46f
                val boxRect = RectF(62f, boxTop, 220f, boxTop + 34f)
                c.drawRoundRect(boxRect, 6f, 6f, barColors[i])
                val label = when (i) {
                    0 -> "IO STREAM LAYER"
                    1 -> "PARSER ENGINE"
                    else -> "COMPOSE VIEWPORT"
                }
                c.drawText(label, 76f, boxTop + 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (i == 0) Color.BLACK else Color.WHITE
                    textSize = 10f
                    isFakeBoldText = true
                })

                // Metric bar
                val metricWidth = (240f - i * 35f + pageIdx * 18f).coerceAtMost(280f)
                val metricRect = RectF(245f, boxTop + 6f, 245f + metricWidth, boxTop + 28f)
                c.drawRoundRect(metricRect, 4f, 4f, barColors[(i + 1) % 3])
                c.drawText("${98 - i * 4 + pageIdx}% Efficiency", 255f, boxTop + 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 9.5f
                    isFakeBoldText = true
                })
            }

            // Footer
            c.drawLine(42f, height - 56f, width - 42f, height - 56f, borderPaint)
            c.drawText("OFFLINE VERIFIED DOCUMENT  •  OMNIDOC STUDIO", 42f, height - 34f, monoPaint)
            c.drawText("PAGE 0${pageIdx + 1}", width - 96f, height - 34f, monoPaint)

            pdf.finishPage(page)
        }

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        val fullPdfTranscript = """
            Architecture Field Guide 2026. OmniDoc Engineering Press.
            Page 1: Executive System Topology.
            Modern mobile workstations demand instantaneous access to heterogeneous document formats, spanning paginated PDF blueprints, OpenXML office specifications, multi-sheet financial ledgers, and high-resolution sequential art archives, without relying on cloud round-trips.
            The OmniDoc Core Engine isolates format decoders into deterministic zero-allocation streams. Each document is indexed into a local SQLite WAL catalog while rendering tiles directly to hardware-backed bitmaps.
            Key Architectural Pillars: Deterministic Local Caching, Adaptive Color Matrix Pipeline, and Sub-Millisecond Zip and RAR Seek.
            Page 2: Zero-Copy Rendering Pipeline.
            Traditional document viewers decode entire files into heap memory, triggering frequent garbage collection pauses on high-density displays. Our tiled viewport compositor renders only visible regions at 2.0x supersampled DPI.
            Page 3: Offline Storage and Fault Tolerance.
            Offline resilience is treated as a first-class invariant. All user bookmarks, page progress percentages, and color-coded study annotations are persisted immediately via Room coroutines.
            Page 4: Accessibility and Telemetry Benchmarks.
            Visual ergonomics directly impact comprehension during extended study sessions. The built-in Accessibility Suite combines five calibrated color temperatures with offline neural Text-to-Speech synthesis.
        """.trimIndent()
        File(file.parentFile, "${file.name}.txt").writeText(fullPdfTranscript)
    }

    private fun generateSampleDocx(file: File) {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:r><w:t>Project Hyperion: Offline-First Orbital &amp; Edge Mesh Architecture</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="Subtitle"/></w:pPr>
                  <w:r><w:t>Technical Specification Document • Revision 4.2 • Prepared by Core Systems Group</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:b/><w:t>Executive Summary: </w:t></w:r>
                  <w:r><w:t>Project Hyperion establishes a resilient, zero-dependency document and telemetry synchronization protocol designed for remote research stations, maritime vessels, and aviation crews operating with intermittent or zero connectivity.</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="Callout"/></w:pPr>
                  <w:r><w:t>IMPORTANT: All field manuals, emergency checklists, and structural schematics must remain 100% readable, searchable, and audible via offline Text-to-Speech even under complete network blackout conditions.</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
                  <w:r><w:t>1. Core Functional Objectives</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>Universal Format Compatibility: Native decoding of PDF, DOCX, XLSX, PPTX, Markdown, CBZ, and CBR archives with zero external cloud conversion APIs.</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>Cognitive &amp; Visual Accessibility: Integrated Reading Focus Ruler, Dyslexia-friendly character spacing, High-Contrast Yellow-on-Black theme, and neural offline Text-to-Speech.</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>Cryptographic Integrity: SHA-256 verification and automatic local storage mirroring for all imported field documents.</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
                  <w:r><w:t>2. Deployment Milestone Schedule &amp; SLA Matrix</w:t></w:r>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Phase</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Deliverable</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Target Latency</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Status</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Phase I</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Core OpenXML &amp; PDF Engine</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>&lt; 25 ms</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Verified</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Phase II</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>CBZ / CBR Sequential Art Studio</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>120 fps Pan/Zoom</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Verified</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Phase III</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Offline TTS &amp; Dyslexia Ruler</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Zero Network</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Active</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
                  <w:r><w:t>3. Field Verification Sign-Off</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="Quote"/></w:pPr>
                  <w:r><w:i/><w:t>"When operating 400 kilometers offshore or in subterranean transit tunnels, software that depends on a remote server is a liability. Local-first architecture ensures mission continuity."</w:t></w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun generateSampleXlsx(file: File) {
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheets>
                <sheet name="Regional Revenue" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                <sheet name="Inventory Audit" sheetId="2" r:id="rId2" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                <sheet name="Unit Economics" sheetId="3" r:id="rId3" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
              </sheets>
            </workbook>
        """.trimIndent()

        val sheet1Rows = listOf(
            listOf("Region", "Hub Code", "Q3 Revenue (USD K)", "Q4 Revenue (USD K)", "YoY Growth (%)", "SLA Uptime (%)"),
            listOf("North America", "NA-WEST-01", "4280", "5190", "21.3", "99.98"),
            listOf("Western Europe", "EU-CENT-04", "3610", "4120", "14.1", "99.95"),
            listOf("Asia Pacific", "AP-EAST-02", "5040", "6480", "28.6", "99.99"),
            listOf("Nordic Cluster", "EU-NORD-09", "1890", "2340", "23.8", "100.00"),
            listOf("Latin America", "LA-SOUTH-03", "1420", "1795", "26.4", "99.92"),
            listOf("Oceania Edge", "OC-SYD-01", "1150", "1410", "22.6", "99.97")
        )

        val sheet2Rows = listOf(
            listOf("SKU ID", "Component Name", "Warehouse", "In Stock", "Reorder Point", "Unit Cost ($)"),
            listOf("HW-901-AX", "Optical Lidar Array v4", "Zurich Vault", "480", "150", "245.00"),
            listOf("HW-902-BX", "Solid-State Thermal Cell", "Osaka Depot", "1240", "400", "88.50"),
            listOf("HW-905-CX", "Carbon-Titanium Chassis", "Austin Hub", "310", "120", "410.00"),
            listOf("HW-908-DX", "Low-Power Neural NPU", "Taipei Fab", "890", "250", "172.25"),
            listOf("HW-912-EX", "High-Gain Mesh Antenna", "Reykjavik Node", "620", "180", "64.00")
        )

        val sheet3Rows = listOf(
            listOf("Tier Plan", "Active Seats", "ARPU ($)", "Gross Margin (%)", "Churn Rate (%)", "LTV ($)"),
            listOf("Enterprise Grid", "14500", "89.00", "84.5", "0.8", "4820"),
            listOf("Studio Pro", "42800", "29.00", "79.2", "1.6", "1640"),
            listOf("Academic Lab", "19200", "12.00", "71.0", "2.1", "540"),
            listOf("Field Expedition", "6400", "149.00", "88.1", "0.4", "9250")
        )

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(workbookXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            listOf(sheet1Rows, sheet2Rows, sheet3Rows).forEachIndexed { idx, rows ->
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet${idx + 1}.xml"))
                zos.write(buildWorksheetXml(rows).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun buildWorksheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            sb.append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, cellVal ->
                val colLetter = ('A' + cIdx)
                val ref = "$colLetter$rowNum"
                val isNumeric = cellVal.toDoubleOrNull() != null
                val escaped = cellVal
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                if (isNumeric) {
                    sb.append("""<c r="$ref"><v>$escaped</v></c>""")
                } else {
                    sb.append("""<c r="$ref" t="inlineStr"><is><t>$escaped</t></is></c>""")
                }
            }
            sb.append("""</row>""")
        }
        sb.append("""</sheetData></worksheet>""")
        return sb.toString()
    }

    private fun generateSamplePptx(file: File) {
        data class SlideSeed(
            val title: String,
            val sub: String,
            val metric: String,
            val bullets: List<String>,
            val notes: String
        )

        val seeds = listOf(
            SlideSeed(
                title = "NextGen Spatial & Offline OS",
                sub = "Q4 Architecture Keynote • Hardware & Document Convergence",
                metric = "99.98% Offline Reliability",
                bullets = listOf(
                    "Unified rendering engine across PDF, OpenXML, and Sequential Art.",
                    "Zero cloud latency: instantaneous local storage indexing & search.",
                    "Built for field engineers, researchers, and avid comic collectors."
                ),
                notes = "Welcome everyone. Emphasize how our offline-first architecture eliminates cloud dependency completely."
            ),
            SlideSeed(
                title = "Universal Document Matrix",
                sub = "Seven Core Formats, One Cohesive Interface",
                metric = "7 Native Engines",
                bullets = listOf(
                    "PDF Studio: Hardware-accelerated page rendering + color inversion.",
                    "Office Suite: Full DOCX typography, XLSX multi-sheet math, PPTX presenter.",
                    "Comic Vault: Native CBZ (ZIP) & CBR (RAR) dual-engine with Manga & Webtoon modes."
                ),
                notes = "Highlight that users don't need 5 separate apps to read spreadsheets, slides, and comics."
            ),
            SlideSeed(
                title = "Inclusive Offline Accessibility",
                sub = "Engineered for Visual Comfort & Neurodiverse Readers",
                metric = "5 Calibrated Color Modes",
                bullets = listOf(
                    "Interactive Reading Focus Ruler isolates active lines to prevent eye skipping.",
                    "Dyslexia-Friendly typography toggle expands letter and word tracking.",
                    "Built-in Offline Text-to-Speech (TTS) reads any document aloud without internet."
                ),
                notes = "Demonstrate the draggable Reading Ruler and toggle High-Contrast mode live."
            ),
            SlideSeed(
                title = "Spreadsheet & Data Intelligence",
                sub = "Instant Column Telemetry Without Formulas",
                metric = "< 12ms Grid Sort",
                bullets = listOf(
                    "Tap any numeric column in XLSX/CSV to compute Sum, Average, Min, and Max.",
                    "Multi-sheet tab bar with live row search and cell inspection bar.",
                    "One-tap export of any spreadsheet or document directly to offline PDF."
                ),
                notes = "Show how selecting a column in Global Supply Metrics Q4 computes instant financial totals."
            ),
            SlideSeed(
                title = "2026 Rollout & Ecosystem Roadmap",
                sub = "Production Milestones & Open Standards",
                metric = "100% Local Privacy",
                bullets = listOf(
                    "Q1: Zero-copy PDF & Office OpenXML parser deployment.",
                    "Q2: CBZ/CBR Smart Panel Zoom and Webtoon continuous strip.",
                    "Q3: Cross-document study annotations and PDF export bundler."
                ),
                notes = "Wrap up by inviting attendees to test importing their own files from device storage."
            )
        )

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            seeds.forEachIndexed { idx, seed ->
                val num = idx + 1
                val slideXml = buildSlideXml(seed.title, seed.sub, seed.metric, seed.bullets)
                zos.putNextEntry(ZipEntry("ppt/slides/slide$num.xml"))
                zos.write(slideXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val notesXml = """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                      <p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>${seed.notes}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld>
                    </p:notes>
                """.trimIndent()
                zos.putNextEntry(ZipEntry("ppt/notesSlides/notesSlide$num.xml"))
                zos.write(notesXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun buildSlideXml(
        title: String,
        subtitle: String,
        metric: String,
        bullets: List<String>
    ): String {
        val bulletParas = bullets.joinToString("") { b ->
            val esc = b.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<a:p><a:r><a:t>$esc</a:t></a:r></a:p>"
        }
        val escTitle = title.replace("&", "&amp;")
        val escSub = subtitle.replace("&", "&amp;")
        val escMetric = "METRIC:$metric".replace("&", "&amp;")
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                   xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:sp>
                    <p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:txBody><a:p><a:r><a:t>$escTitle</a:t></a:r></a:p></p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:nvSpPr><p:nvPr><p:ph type="subTitle"/></p:nvPr></p:nvSpPr>
                    <p:txBody><a:p><a:r><a:t>$escSub</a:t></a:r></a:p></p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:nvSpPr><p:nvPr><p:ph type="body"/></p:nvPr></p:nvSpPr>
                    <p:txBody>
                      <a:p><a:r><a:t>$escMetric</a:t></a:r></a:p>
                      $bulletParas
                    </p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()
    }

    private fun generateSampleMarkdown(file: File) {
        val md = """
            # OmniDoc Offline Accessibility & Reader Handbook
            
            Welcome to **OmniDoc Reader**, your unified offline studio for reading technical manuals, office documents, financial spreadsheets, presentations, and comic book archives.
            
            ## 1. Supported Formats & Native Engines
            - **PDF (`.pdf`)**: High-DPI native rasterizer with pinch-to-zoom, page scrubber, and Dark/Sepia color matrix inversion.
            - **Word Documents (`.docx`)**: Structured OpenXML parser supporting headings, callouts, lists, and embedded tables.
            - **Spreadsheets (`.xlsx`, `.csv`, `.tsv`)**: Interactive multi-sheet grid with sticky headers, search filter, column sorting, and automatic numeric column statistics (`SUM`, `AVG`, `MIN`, `MAX`).
            - **Presentations (`.pptx`)**: Visual slide deck canvas with slide filmstrip, KPI badges, and presenter notes drawer.
            - **Comics & Manga (`.cbz`, `.cbr`)**: Dual ZIP/RAR archive engine with Western (`L -> R`), Manga (`R <- L`), and Vertical Webtoon scroll modes.
            
            ## 2. Offline Accessibility Suite
            1. **Offline Text-to-Speech (TTS)**: Tap the speaker icon inside any viewer to listen to the document read aloud sentence-by-sentence with adjustable speed (`0.5x` – `2.0x`) and pitch.
            2. **Draggable Reading Focus Ruler**: Enable the horizontal focus guide to highlight the active reading line and reduce visual crowding.
            3. **Five Calibrated Reading Surfaces**:
               - *Daylight Paper*: Crisp warm ivory for bright environments.
               - *Warm Sepia*: Low-blue-light parchment for extended afternoon study.
               - *AMOLED Night*: Pitch black surface for zero glare in dark rooms.
               - *High Contrast*: Accessible vibrant yellow text on pure black background.
               - *Botanical Sage*: Soft green tint designed to reduce ocular fatigue.
            
            ## 3. Quick Reference Code Snippet
            ```kotlin
            // Offline Document Integrity Verification
            fun verifyOfflineAvailability(file: File): Boolean {
                return file.exists() && file.canRead() && file.length() > 0L
            }
            ```
            
            > Tip: You can also edit any `.txt` or `.md` file directly inside OmniDoc Reader or export any document to a standalone `.pdf` file!
        """.trimIndent()
        file.writeText(md)
    }

    /**
     * Generates a real comic book archive (.cbz or .cbr) containing ComicInfo.xml and
     * full-color multi-panel comic pages rendered via Android Canvas.
     */
    private fun generateSampleComicArchive(file: File, isCyberpunk: Boolean, pageCount: Int) {
        val comicInfoXml = if (!isCyberpunk) {
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo>
              <Title>Starlight Vanguard: Issue #01</Title>
              <Series>Starlight Vanguard</Series>
              <Number>1</Number>
              <Writer>Elena Rostova &amp; Marcus Vance</Writer>
              <Summary>Commander Lyra Vance pilots the reconnaissance corvette Helios-9 into the crystalline ring system of Kepler-442b after receiving an ancient sub-space beacon.</Summary>
              <PageCount>$pageCount</PageCount>
            </ComicInfo>
            """.trimIndent()
        } else {
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo>
              <Title>Cyber Ronin Chronicles: Vol. 1</Title>
              <Series>Cyber Ronin Chronicles</Series>
              <Number>1</Number>
              <Writer>Kenji Takahashi</Writer>
              <Summary>Across the rain-slicked neon spires of Neo-Kyoto Sector 9, rogue courier Ren intercepts an encrypted neural core sought by the Syndicate.</Summary>
              <PageCount>$pageCount</PageCount>
            </ComicInfo>
            """.trimIndent()
        }

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("ComicInfo.xml"))
            zos.write(comicInfoXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for (pageIdx in 1..pageCount) {
                val bmp = drawComicPageBitmap(pageIdx, pageCount, isCyberpunk)
                zos.putNextEntry(ZipEntry("page_0$pageIdx.png"))
                bmp.compress(Bitmap.CompressFormat.PNG, 90, zos)
                zos.closeEntry()
                bmp.recycle()
            }
        }
    }

    private fun drawComicPageBitmap(pageNumber: Int, totalPages: Int, isCyberpunk: Boolean): Bitmap {
        val width = 900
        val height = 1320
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Page margin paper
        c.drawColor(if (isCyberpunk) Color.rgb(12, 14, 24) else Color.rgb(245, 242, 235))

        val borderStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCyberpunk) Color.rgb(34, 211, 238) else Color.rgb(15, 23, 42)
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }

        val narrationBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCyberpunk) Color.rgb(245, 158, 11) else Color.rgb(254, 243, 199)
        }
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val textBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 24f
            isFakeBoldText = true
        }
        val textBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 20f
            isFakeBoldText = true
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCyberpunk) Color.rgb(244, 114, 182) else Color.rgb(15, 23, 42)
            textSize = 22f
            isFakeBoldText = true
        }

        val seriesTitle = if (isCyberpunk) "CYBER RONIN CHRONICLES // VOL. 1" else "STARLIGHT VANGUARD // ISSUE #01"
        c.drawText(seriesTitle, 42f, 44f, headerPaint)
        c.drawText("PAGE 0$pageNumber / 0$totalPages", width - 210f, 44f, headerPaint)

        // Panel 1 (Top Wide Panel)
        val p1 = RectF(40f, 64f, width - 40f, 490f)
        drawPanelBackground(
            c, p1,
            if (isCyberpunk) Color.rgb(17, 24, 39) else Color.rgb(15, 23, 42),
            if (isCyberpunk) Color.rgb(131, 24, 67) else Color.rgb(8, 51, 68)
        )
        drawStarsAndPlanetOrCity(c, p1, pageNumber, isCyberpunk)
        c.drawRect(p1, borderStroke)

        // Narration Caption Box in Panel 1
        val cap1 = RectF(64f, 88f, 640f, 158f)
        c.drawRect(cap1, narrationBoxPaint)
        c.drawRect(cap1, Paint(borderStroke).apply { strokeWidth = 4f })
        val capText1 = when {
            !isCyberpunk && pageNumber == 1 -> "ORBITAL SECTOR KEPLER-442B • 04:18 SHIP TIME"
            !isCyberpunk && pageNumber == 2 -> "THE CRYSTALLINE RINGS HUM WITH QUANTUM RESONANCE..."
            !isCyberpunk && pageNumber == 3 -> "DEEP INSIDE THE CITADEL CORE, AN ANCIENT MAP AWAKENS."
            !isCyberpunk && pageNumber == 4 -> "GRAVITY SHEAR ALARMS ECHO ACROSS THE BRIDGE DECK!"
            !isCyberpunk -> "TO BE CONTINUED IN ISSUE #02: BEYOND THE NEBULA..."
            pageNumber == 1 -> "NEO-KYOTO SECTOR 9 • MIDNIGHT DOWNPOUR"
            pageNumber == 2 -> "SYNDICATE DRONES LOCK ONTO THE ENCRYPTED NEURAL CORE..."
            pageNumber == 3 -> "KATANA OVERDRIVE: MAGNETIC PULSE DEFLECTS THE LASER VOLLEY!"
            else -> "THE SKYWAY EXPRESS VANISHES INTO THE DIGITAL HORIZON."
        }
        c.drawText(capText1, 82f, 132f, textBody)

        // Panel 2 (Middle Left) & Panel 3 (Middle Right)
        val p2 = RectF(40f, 516f, 445f, 890f)
        val p3 = RectF(468f, 516f, width - 40f, 890f)

        drawPanelBackground(
            c, p2,
            if (isCyberpunk) Color.rgb(49, 16, 66) else Color.rgb(30, 41, 59),
            if (isCyberpunk) Color.rgb(6, 95, 70) else Color.rgb(120, 53, 15)
        )
        drawCharacterSilhouette(c, p2, isCyberpunk, isLeft = true)
        c.drawRect(p2, borderStroke)

        drawPanelBackground(
            c, p3,
            if (isCyberpunk) Color.rgb(15, 23, 42) else Color.rgb(23, 37, 84),
            if (isCyberpunk) Color.rgb(217, 119, 6) else Color.rgb(6, 182, 212)
        )
        drawActionBurst(c, p3, pageNumber)
        c.drawRect(p3, borderStroke)

        // Speech Bubble in Panel 2
        val bubble2 = RectF(60f, 540f, 425f, 645f)
        c.drawRoundRect(bubble2, 28f, 28f, bubblePaint)
        c.drawRoundRect(bubble2, 28f, 28f, Paint(borderStroke).apply { strokeWidth = 4f })
        val speech1 = if (!isCyberpunk) {
            when (pageNumber) {
                1 -> "\"Sensors confirm a structure!\""
                2 -> "\"Hold steady through the ring!\""
                3 -> "\"These glyphs predate Earth...\""
                4 -> "\"Full power to forward thrusters!\""
                else -> "\"Coordinates locked. Jump now!\""
            }
        } else {
            when (pageNumber) {
                1 -> "\"The data courier has arrived.\""
                2 -> "\"Three interceptors on my tail!\""
                3 -> "\"EMP pulse charged to 100%!\""
                else -> "\"Offline vault secured. We won.\""
            }
        }
        c.drawText(speech1, 80f, 600f, textBody)

        // Panel 4 (Bottom Wide Dramatic Panel)
        val p4 = RectF(40f, 916f, width - 40f, height - 48f)
        drawPanelBackground(
            c, p4,
            if (isCyberpunk) Color.rgb(9, 13, 22) else Color.rgb(12, 74, 110),
            if (isCyberpunk) Color.rgb(236, 72, 153) else Color.rgb(245, 158, 11)
        )
        drawSciFiHorizon(c, p4, pageNumber, isCyberpunk)
        c.drawRect(p4, borderStroke)

        // Speech Bubble in Panel 4
        val bubble4 = RectF(430f, 946f, width - 68f, 1068f)
        c.drawRoundRect(bubble4, 32f, 32f, bubblePaint)
        c.drawRoundRect(bubble4, 32f, 32f, Paint(borderStroke).apply { strokeWidth = 4f })
        c.drawText(
            if (isCyberpunk) "\"Transmission decoded offline!\"" else "\"Engage hyper-drive core!\"",
            456f,
            1004f,
            textBold
        )
        c.drawText(
            "PANEL SEQ #0$pageNumber • FULL RESOLUTION",
            456f,
            1040f,
            Paint(textBody).apply { textSize = 16f; color = Color.rgb(100, 116, 139) }
        )

        return bmp
    }

    private fun drawPanelBackground(c: Canvas, rect: RectF, colorStart: Int, colorEnd: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                colorStart, colorEnd, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(rect, paint)
    }

    private fun drawStarsAndPlanetOrCity(c: Canvas, rect: RectF, seed: Int, isCyberpunk: Boolean) {
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        for (i in 0..35) {
            val sx = rect.left + ((i * 97 + seed * 43) % rect.width().toInt())
            val sy = rect.top + ((i * 53 + seed * 29) % (rect.height().toInt() - 60))
            val r = if (i % 5 == 0) 4.5f else 2f
            c.drawCircle(sx, sy, r, starPaint)
        }

        if (!isCyberpunk) {
            // Draw ringed planet
            val cx = rect.right - 210f
            val cy = rect.centerY() + 25f
            val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(6, 182, 212)
            }
            c.drawCircle(cx, cy, 92f, planetPaint)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(251, 191, 36)
                style = Paint.Style.STROKE
                strokeWidth = 12f
            }
            c.drawOval(RectF(cx - 165f, cy - 32f, cx + 165f, cy + 32f), ringPaint)
        } else {
            // Draw cyberpunk neon towers
            val towerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42) }
            val neonLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(34, 211, 238)
                strokeWidth = 4f
            }
            for (b in 0..7) {
                val left = rect.left + 60f + b * 95f
                val top = rect.bottom - 90f - ((b * 47 + seed * 35) % 180)
                c.drawRect(left, top, left + 74f, rect.bottom, towerPaint)
                c.drawLine(left, top, left + 74f, top, neonLine)
            }
        }
    }

    private fun drawCharacterSilhouette(c: Canvas, rect: RectF, isCyberpunk: Boolean, isLeft: Boolean) {
        val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCyberpunk) Color.rgb(244, 114, 182) else Color.rgb(34, 211, 238)
        }
        val suitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
        }
        val cx = if (isLeft) rect.centerX() else rect.centerX()
        val cy = rect.bottom - 95f
        // Shoulders
        c.drawRoundRect(RectF(cx - 110f, cy - 20f, cx + 110f, rect.bottom), 40f, 40f, suitPaint)
        // Helmet
        c.drawCircle(cx, cy - 65f, 64f, suitPaint)
        // Glowing Visor
        c.drawRoundRect(RectF(cx - 42f, cy - 82f, cx + 46f, cy - 48f), 14f, 14f, visorPaint)
    }

    private fun drawActionBurst(c: Canvas, rect: RectF, pageNumber: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY() + 20f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(254, 240, 138)
            strokeWidth = 5f
        }
        for (angle in 0 until 360 step 18) {
            val rad = Math.toRadians(angle.toDouble())
            val x1 = cx + (Math.cos(rad) * 45).toFloat()
            val y1 = cy + (Math.sin(rad) * 45).toFloat()
            val x2 = cx + (Math.cos(rad) * 160).toFloat()
            val y2 = cy + (Math.sin(rad) * 160).toFloat()
            c.drawLine(x1, y1, x2, y2, linePaint)
        }
        val sfxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            isFakeBoldText = true
        }
        val sfx = when (pageNumber % 3) {
            1 -> "VROOOOM!"
            2 -> "KRAAAK-OW!"
            else -> "ZAP-PULSE!"
        }
        c.drawText(sfx, cx - 115f, cy + 14f, sfxPaint)
    }

    private fun drawSciFiHorizon(c: Canvas, rect: RectF, pageNumber: Int, isCyberpunk: Boolean) {
        val path = Path().apply {
            moveTo(rect.left, rect.bottom)
            lineTo(rect.left, rect.bottom - 120f)
            lineTo(rect.left + 190f, rect.bottom - 220f - (pageNumber * 12f))
            lineTo(rect.left + 390f, rect.bottom - 140f)
            lineTo(rect.left + 610f, rect.bottom - 250f)
            lineTo(rect.right, rect.bottom - 110f)
            lineTo(rect.right, rect.bottom)
            close()
        }
        val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCyberpunk) Color.rgb(17, 24, 39) else Color.rgb(15, 23, 42)
        }
        c.drawPath(path, ridgePaint)

        // Glowing ship / vehicle trail
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 211, 238)
            strokeWidth = 10f
        }
        c.drawLine(rect.left + 80f, rect.bottom - 190f, rect.left + 360f, rect.bottom - 260f, beamPaint)
        c.drawCircle(rect.left + 360f, rect.bottom - 260f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })
    }
}
