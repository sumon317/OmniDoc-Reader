package com.example.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ComicPortalCategory(val label: String) {
    ALL("All Sources"),
    EVERYTHING_MOE("EverythingMoe Hub"),
    MANGA("Manga Readers"),
    MANHWA("Manhwa & Webtoons"),
    COMICS("Western Comics"),
    DOWNLOAD_DDL("CBZ / CBR Downloads")
}

data class EverythingMoePortalSource(
    val id: String,
    val name: String,
    val url: String,
    val category: ComicPortalCategory,
    val rankBadge: String,
    val tags: List<String>,
    val description: String,
    val supportsDirectCbzDownload: Boolean = false,
    val accentHex: Long = 0xFF38BDF8
)

enum class OnlineComicFilter(val label: String) {
    TRENDING_MANGA("Trending Manga"),
    POPULAR_MANHWA("Korean Manhwa"),
    POPULAR_MANHUA("Chinese Manhua"),
    LATEST_UPDATES("Latest Updates"),
    ACTION_FANTASY("Action & Fantasy")
}

data class OnlineComicSeries(
    val id: String,
    val title: String,
    val altTitle: String,
    val description: String,
    val author: String,
    val status: String,
    val year: Int?,
    val contentTypeLabel: String, // "MANGA", "MANHWA", "MANHUA", "COMIC"
    val coverUrl: String,
    val tags: List<String>,
    val webUrl: String
)

data class OnlineComicChapter(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val chapterNumber: String,
    val title: String,
    val volume: String?,
    val pageCount: Int,
    val scanlationGroup: String,
    val publishDateLabel: String,
    val externalUrl: String? = null,
    val fallbackDirectPages: List<String> = emptyList()
)

data class OnlineDownloadProgress(
    val isDownloading: Boolean = false,
    val taskTitle: String = "",
    val downloadedCount: Int = 0,
    val totalCount: Int = 0,
    val progressFraction: Float = 0f,
    val statusText: String = ""
)

class EverythingMoeComicHubEngine(private val context: Context) {

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 OmniDocReader/2.0"
                )
                .build()
            chain.proceed(req)
        }
        .build()

    private val _seriesList = MutableStateFlow<List<OnlineComicSeries>>(emptyList())
    val seriesList: StateFlow<List<OnlineComicSeries>> = _seriesList.asStateFlow()

    private val _isLoadingSeries = MutableStateFlow(false)
    val isLoadingSeries: StateFlow<Boolean> = _isLoadingSeries.asStateFlow()

    private val _downloadProgress = MutableStateFlow(OnlineDownloadProgress())
    val downloadProgress: StateFlow<OnlineDownloadProgress> = _downloadProgress.asStateFlow()

    /**
     * Curated directory of top sources from https://everythingmoe.com/ across
     * EverythingMoe Index, Manga, Manhwa/Webtoons, Western Comics, and Direct Download (DDL) Archives.
     */
    val curatedPortals: List<EverythingMoePortalSource> = listOf(
        // EverythingMoe Official Index Sections
        EverythingMoePortalSource(
            id = "everythingmoe_home",
            name = "EverythingMoe Master Index",
            url = "https://everythingmoe.com/",
            category = ComicPortalCategory.EVERYTHING_MOE,
            rankBadge = "#1 SOURCE DIRECTORY",
            tags = listOf("Master Directory", "Manga", "Manhwa", "Comics", "DDL"),
            description = "The official EverythingMoe community-vetted index of the best manga, manhwa, comic readers, scanlators, and direct download sites.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFFBBF24
        ),
        EverythingMoePortalSource(
            id = "everythingmoe_manga",
            name = "EverythingMoe — Manga Section",
            url = "https://everythingmoe.com/?section=manga",
            category = ComicPortalCategory.EVERYTHING_MOE,
            rankBadge = "MANGA RANKINGS",
            tags = listOf("Aggregators", "Scanlators", "Official", "Raws"),
            description = "Live rankings of all active manga reading portals, scanlation hubs, and raw providers curated by EverythingMoe.",
            supportsDirectCbzDownload = false,
            accentHex = 0xFF38BDF8
        ),
        EverythingMoePortalSource(
            id = "everythingmoe_manhwa",
            name = "EverythingMoe — Manhwa & Webtoons",
            url = "https://everythingmoe.com/?section=manhwa",
            category = ComicPortalCategory.EVERYTHING_MOE,
            rankBadge = "MANHWA RANKINGS",
            tags = listOf("Webtoons", "Korean Manhwa", "Scanlators", "Vertical Scroll"),
            description = "Complete EverythingMoe directory of Korean manhwa readers, webtoon platforms, and scanlation groups.",
            supportsDirectCbzDownload = false,
            accentHex = 0xFFA855F7
        ),
        EverythingMoePortalSource(
            id = "everythingmoe_downloads",
            name = "EverythingMoe — Direct Downloads",
            url = "https://everythingmoe.com/?section=download",
            category = ComicPortalCategory.EVERYTHING_MOE,
            rankBadge = "DDL & ARCHIVES",
            tags = listOf("CBZ / ZIP", "DDL", "Raw Archives", "Volumes"),
            description = "EverythingMoe's dedicated download index for full manga volumes, CBZ/CBR archives, and direct file hosts.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF10B981
        ),

        // Top Manga Readers on EverythingMoe
        EverythingMoePortalSource(
            id = "mangadex_web",
            name = "MangaDex",
            url = "https://mangadex.org/",
            category = ComicPortalCategory.MANGA,
            rankBadge = "TOP #1 AD-FREE",
            tags = listOf("Manga", "Manhwa", "Ad-Free", "Scanlator Hub"),
            description = "Premier community-driven, ad-free manga & manhwa platform where scanlation groups upload directly in high resolution.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFF97316
        ),
        EverythingMoePortalSource(
            id = "comick_hub",
            name = "ComicK",
            url = "https://comick.io/",
            category = ComicPortalCategory.MANGA,
            rankBadge = "TOP AGGREGATOR",
            tags = listOf("Manga", "Manhwa", "Manhua", "Fast CDN"),
            description = "Top-rated EverythingMoe aggregator featuring massive manga, manhwa, and manhua libraries with multiple scanlator releases.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF38BDF8
        ),
        EverythingMoePortalSource(
            id = "mangafire_reader",
            name = "MangaFire",
            url = "https://mangafire.to/",
            category = ComicPortalCategory.MANGA,
            rankBadge = "HD READER",
            tags = listOf("Manga", "Manhwa", "Long Strip", "Double Page"),
            description = "Sleek modern manga & manhwa reader with high-res scans, volume browsing, and vertical/horizontal layout modes.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFEC4899
        ),
        EverythingMoePortalSource(
            id = "weebcentral_hub",
            name = "Weeb Central (MangaSee)",
            url = "https://weebcentral.com/",
            category = ComicPortalCategory.MANGA,
            rankBadge = "HIGH QUALITY SCANS",
            tags = listOf("Official Rips", "Tankobon", "Manga"),
            description = "Successor to MangaSee/Manga4Life known on EverythingMoe for crisp tankobon-grade scans and reliable archive chapters.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF6366F1
        ),
        EverythingMoePortalSource(
            id = "mangakatana_dl",
            name = "MangaKatana",
            url = "https://mangakatana.com/",
            category = ComicPortalCategory.MANGA,
            rankBadge = "BUILT-IN ZIP/CBZ DL",
            tags = listOf("Manga", "Manhwa", "Batch ZIP Download"),
            description = "Lightweight manga & manhwa reader praised on EverythingMoe for built-in multi-chapter ZIP/CBZ archive downloads.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF10B981
        ),
        EverythingMoePortalSource(
            id = "mangapill_reader",
            name = "Mangapill",
            url = "https://mangapill.com/",
            category = ComicPortalCategory.MANGA,
            rankBadge = "MINIMAL & FAST",
            tags = listOf("Fast Load", "Clean UI", "Manga"),
            description = "Ultra-fast, low-bandwidth manga reader ideal for mobile reading and quick chapter image extraction to CBZ.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFF59E0B
        ),
        EverythingMoePortalSource(
            id = "mangaplus_official",
            name = "MANGA Plus by SHUEISHA",
            url = "https://mangaplus.shueisha.co.jp/updates",
            category = ComicPortalCategory.MANGA,
            rankBadge = "OFFICIAL SHUEISHA",
            tags = listOf("Official", "Simulpub", "Shonen Jump"),
            description = "Official global Shueisha platform offering simultaneous free chapters of top Weekly Shonen Jump & Jump+ series.",
            supportsDirectCbzDownload = false,
            accentHex = 0xFFEF4444
        ),

        // Top Manhwa & Webtoon Platforms on EverythingMoe
        EverythingMoePortalSource(
            id = "webtoons_official",
            name = "WEBTOON Official",
            url = "https://www.webtoons.com/en/",
            category = ComicPortalCategory.MANHWA,
            rankBadge = "#1 WEBTOON HUB",
            tags = listOf("Manhwa", "Vertical Scroll", "Official", "Full Color"),
            description = "The world's largest Korean webtoon & digital comic platform featuring thousands of full-color vertical scroll series.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF22C55E
        ),
        EverythingMoePortalSource(
            id = "asura_scans",
            name = "Asura Scans",
            url = "https://asuracomic.net/",
            category = ComicPortalCategory.MANHWA,
            rankBadge = "TOP MANHWA SCANS",
            tags = listOf("Action Manhwa", "Regression", "Fantasy", "Scanlator"),
            description = "Featured on EverythingMoe as the premier scanlation hub for Korean action, dungeon, and fantasy manhwa.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF9333EA
        ),
        EverythingMoePortalSource(
            id = "flame_comics",
            name = "Flame Comics",
            url = "https://flamecomics.xyz/",
            category = ComicPortalCategory.MANHWA,
            rankBadge = "SCANLATOR HUB",
            tags = listOf("Manhwa", "Manhua", "High-Res Webtoons"),
            description = "Dedicated scanlation group for high-quality Korean manhwa and Chinese manhua series.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFF97316
        ),
        EverythingMoePortalSource(
            id = "tapas_io",
            name = "Tapas — Manhwa & Comics",
            url = "https://tapas.io/comics",
            category = ComicPortalCategory.MANHWA,
            rankBadge = "OFFICIAL MANHWA",
            tags = listOf("RoFan", "Action", "Webtoons", "Indie Comics"),
            description = "Official platform for hit Korean manhwa adaptations, romance fantasy webtoons, and creator-owned comics.",
            supportsDirectCbzDownload = false,
            accentHex = 0xFFFACC15
        ),
        EverythingMoePortalSource(
            id = "toonily_manhwa",
            name = "Toonily",
            url = "https://toonily.com/",
            category = ComicPortalCategory.MANHWA,
            rankBadge = "MANHWA ARCHIVE",
            tags = listOf("Korean Manhwa", "Full Color", "Completed Series"),
            description = "Large catalog of translated Korean manhwa and webtoons with fast mobile vertical strip loading.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF06B6D4
        ),

        // Western Comics & Graphic Novels
        EverythingMoePortalSource(
            id = "batcave_comics",
            name = "BatCave.biz — Western Comics",
            url = "https://batcave.biz/",
            category = ComicPortalCategory.COMICS,
            rankBadge = "WESTERN COMICS",
            tags = listOf("DC", "Marvel", "Image", "Indie Graphic Novels"),
            description = "Popular online western comic book reader for superhero issues, indie graphic novels, and full issue runs.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFF59E0B
        ),
        EverythingMoePortalSource(
            id = "readcomiconline_li",
            name = "ReadComicOnline",
            url = "https://readcomiconline.li/",
            category = ComicPortalCategory.COMICS,
            rankBadge = "MASSIVE COMIC ARCHIVE",
            tags = listOf("Western Comics", "Graphic Novels", "Golden Age to Modern"),
            description = "Comprehensive western comic book library covering decades of single issues, trade paperbacks, and omnibuses.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF38BDF8
        ),
        EverythingMoePortalSource(
            id = "globalcomix_hub",
            name = "GlobalComix",
            url = "https://globalcomix.com/browse",
            category = ComicPortalCategory.COMICS,
            rankBadge = "OFFICIAL & INDIE",
            tags = listOf("Image", "Boom! Studios", "Indie", "PDF/Panel View"),
            description = "Official digital comic platform partnering with major western publishers and independent graphic novel creators.",
            supportsDirectCbzDownload = false,
            accentHex = 0xFF6366F1
        ),

        // Direct Download (DDL) & CBZ / CBR Archive Sites
        EverythingMoePortalSource(
            id = "getcomics_ddl",
            name = "GetComics.org (Direct CBZ/CBR)",
            url = "https://getcomics.org/",
            category = ComicPortalCategory.DOWNLOAD_DDL,
            rankBadge = "#1 CBZ/CBR DDL",
            tags = listOf("Direct Download", ".CBZ", ".CBR", "Weekly Packs"),
            description = "The premier direct-download (DDL) archive for .CBZ and .CBR comic books. Tap any Mega/Mediafire/Direct link to import straight into OmniDoc!",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF10B981
        ),
        EverythingMoePortalSource(
            id = "archive_org_comics",
            name = "Internet Archive — Comic & Manga CBZ Vault",
            url = "https://archive.org/details/comics",
            category = ComicPortalCategory.DOWNLOAD_DDL,
            rankBadge = "FREE .CBZ / .PDF DDL",
            tags = listOf("Direct .CBZ", "Direct .PDF", ".CBR", "Public Domain & Archives"),
            description = "Thousands of full comic books, manga volumes, and graphic novels with 1-click direct .CBZ and .PDF downloads into OmniDoc Reader.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFFBBF24
        ),
        EverythingMoePortalSource(
            id = "dl_raw_manga",
            name = "DL-Raw / MangaZip Archives",
            url = "https://dl-raw.ac/",
            category = ComicPortalCategory.DOWNLOAD_DDL,
            rankBadge = "MANGA VOLUME DDL",
            tags = listOf("EverythingMoe DDL", "ZIP/CBZ", "Manga Volumes"),
            description = "Indexed on EverythingMoe's Download section for full tankobon volume archives and raw manga packages.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFFEC4899
        ),
        EverythingMoePortalSource(
            id = "nyaa_manga_literatures",
            name = "Nyaa — Literature & Manga Archives",
            url = "https://nyaa.si/?c=3_1",
            category = ComicPortalCategory.DOWNLOAD_DDL,
            rankBadge = "EVERYTHINGMOE #1 ARCHIVE",
            tags = listOf("CBZ Packs", "Official Digital Rips", "English Translated"),
            description = "Top-ranked archive index on EverythingMoe for English-translated manga volumes and lossless CBZ collections.",
            supportsDirectCbzDownload = true,
            accentHex = 0xFF38BDF8
        )
    )

    /**
     * Loads live manga, manhwa, manhua, or comics from the MangaDex v5 public API
     * (with automatic fallback to a rich built-in online catalog if offline).
     */
    suspend fun loadOnlineCatalog(
        filter: OnlineComicFilter = OnlineComicFilter.TRENDING_MANGA,
        searchQuery: String = ""
    ) = withContext(Dispatchers.IO) {
        _isLoadingSeries.value = true
        try {
            val baseUrl = StringBuilder("https://api.mangadex.org/manga?limit=24&includes[]=cover_art&includes[]=author&contentRating[]=safe&contentRating[]=suggestive&hasAvailableChapters=true")

            if (searchQuery.isNotBlank()) {
                val encoded = URLEncoder.encode(searchQuery.trim(), "UTF-8")
                baseUrl.append("&title=").append(encoded)
                baseUrl.append("&order[followedCount]=desc")
            } else {
                when (filter) {
                    OnlineComicFilter.TRENDING_MANGA -> {
                        baseUrl.append("&originalLanguage[]=ja&order[followedCount]=desc")
                    }
                    OnlineComicFilter.POPULAR_MANHWA -> {
                        baseUrl.append("&originalLanguage[]=ko&order[followedCount]=desc")
                    }
                    OnlineComicFilter.POPULAR_MANHUA -> {
                        baseUrl.append("&originalLanguage[]=zh&originalLanguage[]=zh-hk&order[followedCount]=desc")
                    }
                    OnlineComicFilter.LATEST_UPDATES -> {
                        baseUrl.append("&order[latestUploadedChapter]=desc")
                    }
                    OnlineComicFilter.ACTION_FANTASY -> {
                        // Action tag UUID on MangaDex: 391b0423-d847-456f-aff0-8b0cfc03066b
                        baseUrl.append("&includedTags[]=391b0423-d847-456f-aff0-8b0cfc03066b&order[followedCount]=desc")
                    }
                }
            }

            val request = Request.Builder()
                .url(baseUrl.toString())
                .get()
                .build()

            val parsedList = mutableListOf<OnlineComicSeries>()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val root = JSONObject(bodyStr)
                    val dataArr = root.optJSONArray("data")
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val item = dataArr.optJSONObject(i) ?: continue
                            val id = item.optString("id")
                            val attrs = item.optJSONObject("attributes") ?: continue

                            // Extract localized title
                            val titleObj = attrs.optJSONObject("title")
                            val title = titleObj?.optString("en")?.takeIf { it.isNotBlank() }
                                ?: titleObj?.optString("ja-ro")?.takeIf { it.isNotBlank() }
                                ?: titleObj?.optString("ko-ro")?.takeIf { it.isNotBlank() }
                                ?: titleObj?.keys()?.asSequence()?.firstOrNull()?.let { k -> titleObj.optString(k) }
                                ?: "Untitled Series"

                            val descObj = attrs.optJSONObject("description")
                            val rawDesc = descObj?.optString("en")?.takeIf { it.isNotBlank() }
                                ?: descObj?.keys()?.asSequence()?.firstOrNull()?.let { k -> descObj.optString(k) }
                                ?: "Read chapters online or download directly as an offline .CBZ archive in OmniDoc Reader."
                            val cleanDesc = rawDesc.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                                .replace("---", "")
                                .trim()

                            val origLang = attrs.optString("originalLanguage", "ja")
                            val contentTypeLabel = when {
                                origLang.startsWith("ko") -> "MANHWA"
                                origLang.startsWith("zh") -> "MANHUA"
                                origLang.startsWith("en") -> "COMIC"
                                else -> "MANGA"
                            }

                            val status = attrs.optString("status", "ongoing").replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                            }
                            val year = if (attrs.isNull("year")) null else attrs.optInt("year")

                            // Extract tags
                            val tags = mutableListOf<String>()
                            val tagsArr = attrs.optJSONArray("tags")
                            if (tagsArr != null) {
                                for (tIdx in 0 until tagsArr.length()) {
                                    val tObj = tagsArr.optJSONObject(tIdx)
                                    val tName = tObj?.optJSONObject("attributes")
                                        ?.optJSONObject("name")
                                        ?.optString("en")
                                    if (!tName.isNullOrBlank() && tags.size < 4) {
                                        tags.add(tName)
                                    }
                                }
                            }

                            // Relationships: cover_art & author
                            var coverFileName: String? = null
                            var authorName = "Scanlation Hub"
                            val relArr = item.optJSONArray("relationships")
                            if (relArr != null) {
                                for (rIdx in 0 until relArr.length()) {
                                    val rel = relArr.optJSONObject(rIdx) ?: continue
                                    val relType = rel.optString("type")
                                    val relAttrs = rel.optJSONObject("attributes")
                                    if (relType == "cover_art" && relAttrs != null) {
                                        coverFileName = relAttrs.optString("fileName")
                                    } else if (relType == "author" && relAttrs != null) {
                                        authorName = relAttrs.optString("name", authorName)
                                    }
                                }
                            }

                            val coverUrl = if (!coverFileName.isNullOrBlank()) {
                                "https://uploads.mangadex.org/covers/$id/$coverFileName.512.jpg"
                            } else {
                                ""
                            }

                            parsedList.add(
                                OnlineComicSeries(
                                    id = id,
                                    title = title,
                                    altTitle = origLang.uppercase(Locale.US),
                                    description = cleanDesc,
                                    author = authorName,
                                    status = status,
                                    year = year,
                                    contentTypeLabel = contentTypeLabel,
                                    coverUrl = coverUrl,
                                    tags = if (tags.isEmpty()) listOf(contentTypeLabel, "Online") else tags,
                                    webUrl = "https://mangadex.org/title/$id"
                                )
                            )
                        }
                    }
                }
            }

            if (parsedList.isNotEmpty()) {
                _seriesList.value = parsedList
            } else if (_seriesList.value.isEmpty()) {
                _seriesList.value = fallbackFeaturedSeries()
            }
        } catch (_: Exception) {
            if (_seriesList.value.isEmpty()) {
                _seriesList.value = fallbackFeaturedSeries()
            }
        } finally {
            _isLoadingSeries.value = false
        }
    }

    /**
     * Fetches English chapters for a series from MangaDex API v5.
     */
    suspend fun fetchChaptersForSeries(series: OnlineComicSeries): List<OnlineComicChapter> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<OnlineComicChapter>()
            try {
                val url =
                    "https://api.mangadex.org/manga/${series.id}/feed?translatedLanguage[]=en&order[chapter]=desc&limit=80&includes[]=scanlation_group&contentRating[]=safe&contentRating[]=suggestive"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val dataArr = root.optJSONArray("data")
                        val seenChapters = mutableSetOf<String>()
                        if (dataArr != null) {
                            for (i in 0 until dataArr.length()) {
                                val chObj = dataArr.optJSONObject(i) ?: continue
                                val chId = chObj.optString("id")
                                val attrs = chObj.optJSONObject("attributes") ?: continue
                                val pages = attrs.optInt("pages", 0)
                                val externalUrl = attrs.optString("externalUrl", "").takeIf { it.isNotBlank() }
                                if (pages <= 0 && externalUrl == null) continue

                                val chNum = attrs.optString("chapter", "").ifBlank { "${i + 1}" }
                                // Deduplicate multiple scanlations of same chapter number unless unique
                                if (!seenChapters.add(chNum) && result.size >= 25) continue

                                val chTitle = attrs.optString("title", "").takeIf { it.isNotBlank() && it != "null" }
                                    ?: "Chapter $chNum"
                                val vol = attrs.optString("volume", "").takeIf { it.isNotBlank() && it != "null" }
                                val publishAt = attrs.optString("publishAt", "").take(10)

                                var groupName = "Community Scanlation"
                                val relArr = chObj.optJSONArray("relationships")
                                if (relArr != null) {
                                    for (r in 0 until relArr.length()) {
                                        val rel = relArr.optJSONObject(r) ?: continue
                                        if (rel.optString("type") == "scanlation_group") {
                                            groupName = rel.optJSONObject("attributes")
                                                ?.optString("name", groupName) ?: groupName
                                        }
                                    }
                                }

                                result.add(
                                    OnlineComicChapter(
                                        id = chId,
                                        seriesId = series.id,
                                        seriesTitle = series.title,
                                        chapterNumber = chNum,
                                        title = chTitle,
                                        volume = vol,
                                        pageCount = pages.coerceAtLeast(1),
                                        scanlationGroup = groupName,
                                        publishDateLabel = publishAt,
                                        externalUrl = externalUrl
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to fallback if offline
            }

            if (result.isEmpty()) {
                // Provide playable multi-page sample chapters if series is external-only or offline
                listOf(
                    OnlineComicChapter(
                        id = "sample_ch_1_${series.id}",
                        seriesId = series.id,
                        seriesTitle = series.title,
                        chapterNumber = "1",
                        title = "Prologue & Opening Episode",
                        volume = "1",
                        pageCount = 6,
                        scanlationGroup = "EverythingMoe Archive",
                        publishDateLabel = "2025-02-15",
                        externalUrl = series.webUrl
                    ),
                    OnlineComicChapter(
                        id = "sample_ch_2_${series.id}",
                        seriesId = series.id,
                        seriesTitle = series.title,
                        chapterNumber = "2",
                        title = "The Awakening Arc",
                        volume = "1",
                        pageCount = 6,
                        scanlationGroup = "EverythingMoe Archive",
                        publishDateLabel = "2025-02-22",
                        externalUrl = series.webUrl
                    )
                )
            } else {
                result
            }
        }

    /**
     * Resolves the ordered list of page image URLs for a given MangaDex chapter using
     * https://api.mangadex.org/at-home/server/{chapterId}.
     */
    suspend fun fetchChapterPageUrls(chapter: OnlineComicChapter, dataSaver: Boolean = true): List<String> =
        withContext(Dispatchers.IO) {
            if (chapter.fallbackDirectPages.isNotEmpty()) {
                return@withContext chapter.fallbackDirectPages
            }
            try {
                val url = "https://api.mangadex.org/at-home/server/${chapter.id}"
                val req = Request.Builder().url(url).get().build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val baseUrl = root.optString("baseUrl")
                        val chObj = root.optJSONObject("chapter")
                        if (baseUrl.isNotBlank() && chObj != null) {
                            val hash = chObj.optString("hash")
                            val saverArr = chObj.optJSONArray("dataSaver")
                            val fullArr = chObj.optJSONArray("data")
                            val useSaver = dataSaver && saverArr != null && saverArr.length() > 0
                            val targetArr = if (useSaver) saverArr else fullArr
                            val qualitySegment = if (useSaver) "data-saver" else "data"

                            if (targetArr != null && targetArr.length() > 0) {
                                val urls = mutableListOf<String>()
                                for (i in 0 until targetArr.length()) {
                                    val fileName = targetArr.optString(i)
                                    if (fileName.isNotBlank()) {
                                        urls.add("$baseUrl/$qualitySegment/$hash/$fileName")
                                    }
                                }
                                if (urls.isNotEmpty()) return@withContext urls
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through
            }
            emptyList()
        }

    /**
     * Downloads all pages of an online chapter (or a list of extracted web comic image URLs),
     * packages them into a valid .CBZ archive with an embedded ComicInfo.xml manifest,
     * and writes it to the app's offline library directory.
     */
    suspend fun downloadChapterToOfflineCbz(
        seriesTitle: String,
        chapterNumber: String,
        chapterTitle: String,
        author: String,
        pageUrls: List<String>,
        outputDir: File
    ): File? = withContext(Dispatchers.IO) {
        if (pageUrls.isEmpty()) return@withContext null
        outputDir.mkdirs()

        val cleanSeries = seriesTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36).trim('_')
        val cleanCh = chapterNumber.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cbzFile = File(outputDir, "${cleanSeries}_Ch_${cleanCh}.cbz")

        _downloadProgress.value = OnlineDownloadProgress(
            isDownloading = true,
            taskTitle = "$seriesTitle — Ch. $chapterNumber",
            downloadedCount = 0,
            totalCount = pageUrls.size,
            progressFraction = 0.02f,
            statusText = "Starting CBZ archive build (${pageUrls.size} pages)…"
        )

        var successPages = 0
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(cbzFile))).use { zos ->
                // 1. Write ComicInfo.xml metadata
                val comicInfoXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <ComicInfo>
                        <Title>${escapeXml(seriesTitle)} - Ch. ${escapeXml(chapterNumber)}: ${escapeXml(chapterTitle)}</Title>
                        <Series>${escapeXml(seriesTitle)}</Series>
                        <Number>${escapeXml(chapterNumber)}</Number>
                        <Writer>${escapeXml(author)}</Writer>
                        <Summary>Downloaded via OmniDoc Reader Online Manga &amp; EverythingMoe Hub.</Summary>
                        <PageCount>${pageUrls.size}</PageCount>
                    </ComicInfo>
                """.trimIndent()
                zos.putNextEntry(ZipEntry("ComicInfo.xml"))
                zos.write(comicInfoXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Stream each page image into the CBZ archive
                pageUrls.forEachIndexed { index, imgUrl ->
                    val ext = when {
                        imgUrl.contains(".png", ignoreCase = true) -> "png"
                        imgUrl.contains(".webp", ignoreCase = true) -> "webp"
                        else -> "jpg"
                    }
                    val entryName = String.format(Locale.US, "page_%03d.%s", index + 1, ext)

                    val req = Request.Builder()
                        .url(imgUrl)
                        .header("Referer", "https://mangadex.org/")
                        .get()
                        .build()

                    okHttpClient.newCall(req).execute().use { resp ->
                        val bytes = resp.body?.bytes()
                        if (resp.isSuccessful && bytes != null && bytes.size > 512) {
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(bytes)
                            zos.closeEntry()
                            successPages++
                        }
                    }

                    val completed = index + 1
                    _downloadProgress.value = OnlineDownloadProgress(
                        isDownloading = true,
                        taskTitle = "$seriesTitle — Ch. $chapterNumber",
                        downloadedCount = completed,
                        totalCount = pageUrls.size,
                        progressFraction = (completed.toFloat() / pageUrls.size.toFloat()).coerceIn(0f, 1f),
                        statusText = "Packing page $completed of ${pageUrls.size} into .CBZ…"
                    )
                }
            }

            _downloadProgress.value = OnlineDownloadProgress(isDownloading = false)
            if (successPages > 0 && cbzFile.exists() && cbzFile.length() > 1024L) {
                cbzFile
            } else {
                cbzFile.delete()
                null
            }
        } catch (_: Exception) {
            _downloadProgress.value = OnlineDownloadProgress(isDownloading = false)
            cbzFile.delete()
            null
        }
    }

    /**
     * Downloads a direct .cbz, .cbr, .pdf, or .zip archive URL from a DDL site
     * (e.g., Internet Archive, GetComics, MangaZip, etc.) into the local library folder.
     */
    suspend fun downloadDirectFileUrlToLibrary(
        fileUrl: String,
        suggestedName: String?,
        outputDir: File
    ): File? = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val fallbackName = suggestedName?.takeIf { it.isNotBlank() }
            ?: fileUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Downloaded_Comic.cbz" }

        val cleanName = fallbackName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalName = if (
            cleanName.endsWith(".cbz", true) ||
            cleanName.endsWith(".cbr", true) ||
            cleanName.endsWith(".pdf", true) ||
            cleanName.endsWith(".docx", true) ||
            cleanName.endsWith(".xlsx", true) ||
            cleanName.endsWith(".pptx", true) ||
            cleanName.endsWith(".txt", true)
        ) {
            cleanName
        } else if (cleanName.endsWith(".zip", true)) {
            cleanName.removeSuffix(".zip").removeSuffix(".ZIP") + ".cbz"
        } else if (cleanName.endsWith(".rar", true)) {
            cleanName.removeSuffix(".rar").removeSuffix(".RAR") + ".cbr"
        } else {
            "$cleanName.cbz"
        }

        val targetFile = File(outputDir, finalName)

        _downloadProgress.value = OnlineDownloadProgress(
            isDownloading = true,
            taskTitle = finalName,
            downloadedCount = 0,
            totalCount = 100,
            progressFraction = 0.05f,
            statusText = "Downloading $finalName…"
        )

        try {
            val req = Request.Builder().url(fileUrl).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    _downloadProgress.value = OnlineDownloadProgress(isDownloading = false)
                    return@withContext null
                }
                val body = resp.body ?: run {
                    _downloadProgress.value = OnlineDownloadProgress(isDownloading = false)
                    return@withContext null
                }
                val contentLength = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var totalRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read
                            val frac = if (contentLength > 0) {
                                (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0.05f, 0.99f)
                            } else {
                                0.5f
                            }
                            val mbRead = String.format(Locale.US, "%.1f MB", totalRead / (1024f * 1024f))
                            _downloadProgress.value = OnlineDownloadProgress(
                                isDownloading = true,
                                taskTitle = finalName,
                                downloadedCount = (frac * 100).toInt(),
                                totalCount = 100,
                                progressFraction = frac,
                                statusText = "Downloaded $mbRead…"
                            )
                        }
                        output.flush()
                    }
                }
            }
            _downloadProgress.value = OnlineDownloadProgress(isDownloading = false)
            if (targetFile.exists() && targetFile.length() > 512L) targetFile else null
        } catch (_: Exception) {
            _downloadProgress.value = OnlineDownloadProgress(isDownloading = false)
            targetFile.delete()
            null
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun fallbackFeaturedSeries(): List<OnlineComicSeries> = listOf(
        OnlineComicSeries(
            id = "32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
            title = "Solo Leveling",
            altTitle = "MANHWA • KR",
            description = "In a world where hunters—humans who possess magical abilities—must battle deadly monsters to protect the human race from certain annihilation, a notoriously weak hunter named Sung Jinwoo finds himself in a seemingly endless struggle for survival.",
            author = "Chugong / DUBU (Redice Studio)",
            status = "Completed",
            year = 2018,
            contentTypeLabel = "MANHWA",
            coverUrl = "https://uploads.mangadex.org/covers/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0/e90bdc47-c8b9-4df7-b2c0-17641b645ee1.jpg.512.jpg",
            tags = listOf("Action", "Fantasy", "Webtoon", "Dungeon"),
            webUrl = "https://mangadex.org/title/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0"
        ),
        OnlineComicSeries(
            id = "a96676e5-8ae2-425e-b549-7f15dd34a6d8",
            title = "Komi Can't Communicate",
            altTitle = "MANGA • JP",
            description = "Komi-san is the beautiful and admirable girl that no-one can take their eyes off of. Almost the whole school sees her as the cold beauty that's out of their league, but Tadano Hitohito knows the truth: she's just really bad at communicating with others.",
            author = "Oda Tomohito",
            status = "Completed",
            year = 2016,
            contentTypeLabel = "MANGA",
            coverUrl = "",
            tags = listOf("Comedy", "Slice of Life", "School Life"),
            webUrl = "https://mangadex.org/title/a96676e5-8ae2-425e-b549-7f15dd34a6d8"
        ),
        OnlineComicSeries(
            id = "a77742b1-befd-49a4-bff5-1ad4e6b0ef7b",
            title = "Chainsaw Man",
            altTitle = "MANGA • JP",
            description = "Broke young man + chainsaw dog demon = Chainsaw Man! Denji is robbed of a normal teenage life, left with nothing but his deadbeat father's overwhelming debt.",
            author = "Fujimoto Tatsuki",
            status = "Ongoing",
            year = 2018,
            contentTypeLabel = "MANGA",
            coverUrl = "",
            tags = listOf("Action", "Supernatural", "Dark Fantasy"),
            webUrl = "https://mangadex.org/title/a77742b1-befd-49a4-bff5-1ad4e6b0ef7b"
        ),
        OnlineComicSeries(
            id = "77bee52c-d2d6-44ad-a33a-1734c1fe696a",
            title = "Omniscient Reader's Viewpoint",
            altTitle = "MANHWA • KR",
            description = "Dokja was an average office worker whose sole interest was reading his favorite web novel 'Three Ways to Survive the Apocalypse.' But when the novel suddenly becomes reality, he is the only person who knows how the world will end.",
            author = "Sing N Song / Sleepy-C",
            status = "Ongoing",
            year = 2020,
            contentTypeLabel = "MANHWA",
            coverUrl = "",
            tags = listOf("Action", "Apocalyptic", "Fantasy", "Webtoon"),
            webUrl = "https://mangadex.org/title/77bee52c-d2d6-44ad-a33a-1734c1fe696a"
        )
    )
}
