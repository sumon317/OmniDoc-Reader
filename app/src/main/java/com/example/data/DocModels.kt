package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AmoledNightBg
import com.example.ui.theme.AmoledNightText
import com.example.ui.theme.BlueLightShieldBg
import com.example.ui.theme.BlueLightShieldText
import com.example.ui.theme.EInkPaperBg
import com.example.ui.theme.EInkPaperText
import com.example.ui.theme.FormatCbrColor
import com.example.ui.theme.FormatCbzColor
import com.example.ui.theme.FormatDocxColor
import com.example.ui.theme.FormatPdfColor
import com.example.ui.theme.FormatPptxColor
import com.example.ui.theme.FormatTxtColor
import com.example.ui.theme.FormatXlsxColor
import com.example.ui.theme.HighContrastBg
import com.example.ui.theme.HighContrastLightBg
import com.example.ui.theme.HighContrastLightText
import com.example.ui.theme.HighContrastText
import com.example.ui.theme.PaperDaylightBg
import com.example.ui.theme.PaperDaylightText
import com.example.ui.theme.SageComfortBg
import com.example.ui.theme.SageComfortText
import com.example.ui.theme.SepiaWarmBg
import com.example.ui.theme.SepiaWarmText

enum class DocFormat(
    val label: String,
    val badgeColor: Color,
    val extensions: List<String>,
    val isComic: Boolean = false,
    val categoryName: String
) {
    PDF("PDF", FormatPdfColor, listOf("pdf"), false, "PDF Documents"),
    DOCX("DOCX", FormatDocxColor, listOf("docx", "doc"), false, "Word Docs"),
    XLSX("XLSX", FormatXlsxColor, listOf("xlsx", "xls", "csv", "tsv"), false, "Spreadsheets"),
    PPTX("PPTX", FormatPptxColor, listOf("pptx", "ppt"), false, "Presentations"),
    TXT("TXT", FormatTxtColor, listOf("txt", "md", "markdown", "json", "xml", "log", "kt", "py"), false, "Text & Notes"),
    CBZ("CBZ", FormatCbzColor, listOf("cbz"), true, "Comics (CBZ)"),
    CBR("CBR", FormatCbrColor, listOf("cbr"), true, "Comics (CBR)");

    companion object {
        fun fromFileName(fileName: String, mimeType: String? = null): DocFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            entries.forEach { format ->
                if (ext in format.extensions) return format
            }
            if (mimeType != null) {
                val lower = mimeType.lowercase()
                return when {
                    "pdf" in lower -> PDF
                    "word" in lower || "officedocument.wordprocessingml" in lower -> DOCX
                    "spreadsheet" in lower || "excel" in lower || "csv" in lower -> XLSX
                    "presentation" in lower || "powerpoint" in lower -> PPTX
                    "cbz" in lower || "comicbook+zip" in lower -> CBZ
                    "cbr" in lower || "comicbook-rar" in lower || "rar" in lower -> CBR
                    else -> TXT
                }
            }
            return TXT
        }
    }
}

enum class ReadingColorMode(
    val title: String,
    val subtitle: String,
    val backgroundColor: Color,
    val textColor: Color,
    val badgeText: String,
    val accentColor: Color
) {
    DAYLIGHT("Daylight Paper", "Crisp editorial ivory", PaperDaylightBg, PaperDaylightText, "DAY", Color(0xFF0284C7)),
    WARM_SEPIA("Sepia Mode", "Low-glare warm parchment", SepiaWarmBg, SepiaWarmText, "SEPIA", Color(0xFFB45309)),
    HIGH_CONTRAST("High Contrast", "AAA yellow on pitch black", HighContrastBg, HighContrastText, "AAA+", Color(0xFFFACC15)),
    BLUE_LIGHT_SHIELD("Blue Light Filter", "2700K amber night shield", BlueLightShieldBg, BlueLightShieldText, "2700K", Color(0xFFF59E0B)),
    AMOLED_DARK("AMOLED Night", "Pitch black for night reading", AmoledNightBg, AmoledNightText, "OLED", Color(0xFF38BDF8)),
    HIGH_CONTRAST_LIGHT("Stark Contrast", "Pure ink on stark white", HighContrastLightBg, HighContrastLightText, "INK", Color(0xFF0F172A)),
    E_INK_MONOCHROME("E-Ink Mode", "Glare-free matte grayscale", EInkPaperBg, EInkPaperText, "E-INK", Color(0xFF475569)),
    SAGE_COMFORT("Botanical Sage", "Eye-strain relief green tint", SageComfortBg, SageComfortText, "SAGE", Color(0xFF15803D))
}

enum class ComicReadingMode(val label: String, val description: String) {
    LEFT_TO_RIGHT("Western (L → R)", "Standard horizontal swipe left-to-right"),
    RIGHT_TO_LEFT("Manga (R ← L)", "Traditional manga swipe right-to-left"),
    VERTICAL_WEBTOON("Vertical Scroll", "Continuous vertical webtoon strip")
}

data class AccessibilitySettings(
    val darkThemeApp: Boolean = true,
    val colorMode: ReadingColorMode = ReadingColorMode.DAYLIGHT,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightIntensity: Float = 0.35f,
    val showViewerFilterBar: Boolean = true,
    val fontScale: Float = 1.0f,
    val lineSpacingMultiplier: Float = 1.45f,
    val dyslexiaFriendlySpacing: Boolean = false,
    val useMonospaceForText: Boolean = false,
    val showReadingRuler: Boolean = false,
    val rulerPositionFraction: Float = 0.38f,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val comicReadingMode: ComicReadingMode = ComicReadingMode.LEFT_TO_RIGHT,
    val invertPdfColors: Boolean = false,
    val comicWarmFilter: Boolean = false
)

// Parsed DOCX blocks
sealed class DocxBlock {
    data class Heading(val text: String, val level: Int) : DocxBlock()
    data class Paragraph(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isQuote: Boolean = false
    ) : DocxBlock()
    data class BulletItem(val text: String, val numberLabel: String? = null) : DocxBlock()
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : DocxBlock()
    data class CalloutBox(val title: String, val body: String) : DocxBlock()
}

data class ParsedDocxDocument(
    val title: String,
    val blocks: List<DocxBlock>,
    val wordCount: Int,
    val charCount: Int,
    val estimatedMinutes: Int,
    val plainTextForTts: String
)

// Parsed XLSX Workbook
data class XlsxSheet(
    val name: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class ParsedXlsxWorkbook(
    val title: String,
    val sheets: List<XlsxSheet>,
    val totalCells: Int,
    val plainTextForTts: String
)

// Parsed PPTX Presentation
data class PptxSlide(
    val slideNumber: Int,
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
    val speakerNotes: String,
    val accentHex: String = "#F59E0B",
    val keyMetric: String? = null
)

data class ParsedPptxDeck(
    val title: String,
    val slides: List<PptxSlide>,
    val plainTextForTts: String
)

// Parsed Text / Markdown
data class ParsedTextDocument(
    val title: String,
    val content: String,
    val lines: List<String>,
    val isMarkdown: Boolean,
    val wordCount: Int,
    val charCount: Int,
    val estimatedMinutes: Int
)

// Parsed Comic Archive (CBZ / CBR)
data class ComicArchiveInfo(
    val filePath: String,
    val format: DocFormat,
    val title: String,
    val series: String,
    val writer: String,
    val summary: String,
    val pageCount: Int,
    val pageEntryNames: List<String>,
    val archiveEngineType: String // "ZIP" or "RAR"
)
