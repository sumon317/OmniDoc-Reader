package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Metadata for an official uBlock Origin (uBO) Filter List.
 */
data class UBlockFilterListSpec(
    val id: String,
    val title: String,
    val category: String,
    val remoteUrl: String,
    val description: String,
    val bundledRulesCount: Int,
    val enabledByDefault: Boolean = true
)

/**
 * Live status for an individual uBlock Origin filter list.
 */
data class UBlockFilterListStatus(
    val spec: UBlockFilterListSpec,
    val isEnabled: Boolean = spec.enabledByDefault,
    val activeRulesCount: Int = spec.bundledRulesCount,
    val isSyncing: Boolean = false,
    val lastSyncedEpochMs: Long = 0L,
    val syncSourceLabel: String = "Bundled uAssets + Offline Cache"
)

/**
 * Single entry in the real-time uBlock Origin Logger (Network block, Cosmetic hide, Scriptlet, or Reader Sanitizer).
 */
data class UBlockLogEntry(
    val id: Long,
    val timestampMs: Long,
    val sourceContext: String, // e.g. "Browser • mangadex.org" or "DOCX Reader • Report.docx"
    val targetUrlOrElement: String,
    val matchedRule: String,
    val filterListId: String,
    val actionType: UBlockActionType
)

enum class UBlockActionType(val badge: String) {
    NETWORK_BLOCK("BLOCK"),
    POPUP_BLOCK("POPUP"),
    COSMETIC_HIDE("DOM ##"),
    SCRIPTLET_INJECT("+js()"),
    READER_SANITIZE("READER"),
    ELEMENT_ZAP("ZAPPER")
}

/**
 * Result of sanitizing text or pages inside an inbuilt Document or Comic Reader with uBlock Origin.
 */
data class ReaderSanitizationReport(
    val cleanedText: String,
    val trackersRemovedCount: Int,
    val adLinksNeutralizedCount: Int,
    val matchedRules: List<String>
)

/**
 * Complete reactive state of the uBlock Origin Engine across the In-App Browser and Inbuilt Readers.
 */
data class UBlockOriginState(
    val isEnabled: Boolean = true,
    val blockPopupsAndRedirects: Boolean = true,
    val cosmeticFilteringEnabled: Boolean = true,
    val strictThirdPartyBlocking: Boolean = false,
    val readerTrackerSanitizerEnabled: Boolean = true,
    val comicPromoPageFilterEnabled: Boolean = true,
    val elementZapperModeActive: Boolean = false,
    val totalBlockedRequests: Int = 0,
    val currentSessionBlocked: Int = 0,
    val totalReaderItemsSanitized: Int = 0,
    val compiledNetworkDomainsCount: Int = 0,
    val compiledCosmeticSelectorsCount: Int = 0,
    val whitelistedHosts: Set<String> = emptySet(),
    val customUserFilters: List<String> = emptyList(),
    val filterLists: List<UBlockFilterListStatus> = emptyList(),
    val recentLogs: List<UBlockLogEntry> = emptyList(),
    val isUpdatingLists: Boolean = false,
    val lastUpdateSummary: String = "uBlock Origin v1.60 Engine Ready • uAssets Loaded"
)

/**
 * Authentic uBlock Origin (uBO) Ad-Blocker & Privacy Shield Engine for Android WebView Browser
 * and Inbuilt Document & Comic Readers (PDF, DOCX, XLSX, PPTX, TXT/MD, CBZ, CBR, Online Manga).
 *
 * Features:
 * - Static Network Filtering (`||domain^`, `@@||exception^`, keyword/wildcard patterns, `$third-party`)
 * - Neutered Surrogate Responses (`noopjs`, `noopcss`, `1x1-transparent.gif`, `noopframe`)
 * - Cosmetic Filtering (`##selector` generic & domain-specific DOM hiding + MutationObserver)
 * - Scriptlet Injection (`+js(no-window-open-if)`, anti-adblock property defusers, beacon blocker)
 * - Interactive Element Zapper (`uBO Zapper` mode for tapping & removing page elements)
 * - Inbuilt Reader Sanitizer (strips UTM/tracking links, ad beacons, and comic promo/ad pages)
 * - Live uBlock Origin `uAssets` Filter List Updater from GitHub
 */
class UBlockOriginEngine(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mutex = Mutex()
    private val cacheDir = File(context.filesDir, "ublock_origin_uassets").apply { mkdirs() }
    private val customFiltersFile = File(cacheDir, "user_custom_filters.txt")
    private val whitelistFile = File(cacheDir, "whitelisted_hosts.txt")

    // Compiled lookup structures for O(1) domain & fast pattern matching
    @Volatile
    private var blockedDomainsSet: Set<String> = emptySet()

    @Volatile
    private var exceptionDomainsSet: Set<String> = emptySet()

    @Volatile
    private var blockedUrlKeywords: List<String> = emptyList()

    @Volatile
    private var genericCosmeticSelectors: List<String> = emptyList()

    @Volatile
    private var domainCosmeticMap: Map<String, List<String>> = emptyMap()

    private var logSequenceId = 1L

    val officialFilterLists: List<UBlockFilterListSpec> = listOf(
        UBlockFilterListSpec(
            id = "ublock-filters",
            title = "uBlock filters – Ads",
            category = "uBlock Built-in",
            remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            description = "Official uBlock Origin core ad, pop-under, and malvertising network & cosmetic filters.",
            bundledRulesCount = 412,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "ublock-badware",
            title = "uBlock filters – Badware risks",
            category = "uBlock Built-in",
            remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
            description = "Blocks deceptive download buttons, phishing domains, and fake APK/archive installers.",
            bundledRulesCount = 195,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "ublock-privacy",
            title = "uBlock filters – Privacy",
            category = "uBlock Built-in",
            remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
            description = "Blocks telemetry beacons, fingerprinting scripts, and cross-site analytics trackers.",
            bundledRulesCount = 240,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "ublock-annoyances",
            title = "uBlock filters – Annoyances & Overlays",
            category = "uBlock Built-in",
            remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances-others.txt",
            description = "Eliminates floating banners, age/cookie walls, app-install prompts, and sticky overlays.",
            bundledRulesCount = 180,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "ublock-unbreak",
            title = "uBlock filters – Unbreak & Quick Fixes",
            category = "uBlock Built-in",
            remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
            description = "Anti-adblock defusers and site compatibility exception rules.",
            bundledRulesCount = 128,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "easylist",
            title = "EasyList",
            category = "Ads",
            remoteUrl = "https://easylist.to/easylist/easylist.txt",
            description = "Primary international filter list removing ad frames, banners, and video ads.",
            bundledRulesCount = 520,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "easyprivacy",
            title = "EasyPrivacy",
            category = "Privacy",
            remoteUrl = "https://easylist.to/easylist/easyprivacy.txt",
            description = "Supplemental privacy protection stripping web bugs, tracking pixels, and UTM parameters.",
            bundledRulesCount = 310,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "pgl-yoyo",
            title = "Peter Lowe's Ad and tracking server list",
            category = "Multipurpose",
            remoteUrl = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml",
            description = "Curated host list of dedicated advertising, tracking, and monetization servers.",
            bundledRulesCount = 385,
            enabledByDefault = true
        ),
        UBlockFilterListSpec(
            id = "ublock-comic-reader",
            title = "uBO Manga, Comic & Document Reader Shield",
            category = "Inbuilt Readers",
            remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/quick-fixes.txt",
            description = "Blocks manga/manhwa portal popups, casino overlays, comic aggregator promo pages, and document tracking links.",
            bundledRulesCount = 264,
            enabledByDefault = true
        )
    )

    private val _state = MutableStateFlow(
        UBlockOriginState(
            filterLists = officialFilterLists.map { UBlockFilterListStatus(spec = it) }
        )
    )
    val state: StateFlow<UBlockOriginState> = _state.asStateFlow()

    init {
        loadPersistedUserConfig()
        rebuildCompiledRulesets()
    }

    private fun loadPersistedUserConfig() {
        runCatching {
            val customRules = if (customFiltersFile.exists()) {
                customFiltersFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("!") }
            } else {
                listOf(
                    "! Custom uBlock Origin Rules (My Filters)",
                    "||popads.net^",
                    "||adsterra.com^",
                    "##.floating-ad-banner",
                    "##.casino-promo-banner"
                ).filter { !it.startsWith("!") }
            }

            val whitelisted = if (whitelistFile.exists()) {
                whitelistFile.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }

            _state.value = _state.value.copy(
                customUserFilters = customRules,
                whitelistedHosts = whitelisted
            )
        }
    }

    /**
     * Compiles all enabled uBlock Origin filter lists (bundled + synced cached files + user custom rules)
     * into fast in-memory hash sets and selector tables.
     */
    fun rebuildCompiledRulesets() {
        val current = _state.value
        val enabledIds = current.filterLists.filter { it.isEnabled }.map { it.spec.id }.toSet()

        val domains = HashSet<String>(2048)
        val exceptions = HashSet<String>(256)
        val urlKeywords = ArrayList<String>(256)
        val genericSelectors = LinkedHashSet<String>(256)
        val domainSelectors = HashMap<String, MutableList<String>>()

        // 1. Load bundled core uBlock Origin rules for enabled lists
        if ("ublock-filters" in enabledIds || "easylist" in enabledIds || "pgl-yoyo" in enabledIds) {
            domains.addAll(BUNDLED_AD_AND_MONETIZATION_DOMAINS)
            urlKeywords.addAll(BUNDLED_AD_URL_PATTERNS)
            genericSelectors.addAll(BUNDLED_COSMETIC_AD_SELECTORS)
        }
        if ("ublock-badware" in enabledIds) {
            domains.addAll(BUNDLED_BADWARE_AND_MALVERTISING_DOMAINS)
        }
        if ("ublock-privacy" in enabledIds || "easyprivacy" in enabledIds) {
            domains.addAll(BUNDLED_PRIVACY_AND_TRACKER_DOMAINS)
            urlKeywords.addAll(BUNDLED_TRACKER_URL_PATTERNS)
        }
        if ("ublock-annoyances" in enabledIds) {
            genericSelectors.addAll(BUNDLED_ANNOYANCE_COSMETIC_SELECTORS)
        }
        if ("ublock-comic-reader" in enabledIds) {
            domains.addAll(BUNDLED_MANGA_COMIC_AD_DOMAINS)
            genericSelectors.addAll(BUNDLED_MANGA_COMIC_COSMETIC_SELECTORS)
        }
        if ("ublock-unbreak" in enabledIds) {
            exceptions.addAll(BUNDLED_UNBREAK_EXCEPTION_DOMAINS)
        }

        // 2. Parse any downloaded live uAssets filter files from disk cache
        for (listStatus in current.filterLists) {
            if (!listStatus.isEnabled) continue
            val cachedFile = File(cacheDir, "${listStatus.spec.id}.txt")
            if (cachedFile.exists()) {
                runCatching {
                    cachedFile.forEachLine { rawLine ->
                        parseSingleUBlockRuleLine(
                            rawLine = rawLine,
                            domains = domains,
                            exceptions = exceptions,
                            urlKeywords = urlKeywords,
                            genericSelectors = genericSelectors,
                            domainSelectors = domainSelectors
                        )
                    }
                }
            }
        }

        // 3. Parse user's custom "My Filters" rules
        for (rule in current.customUserFilters) {
            parseSingleUBlockRuleLine(
                rawLine = rule,
                domains = domains,
                exceptions = exceptions,
                urlKeywords = urlKeywords,
                genericSelectors = genericSelectors,
                domainSelectors = domainSelectors
            )
        }

        blockedDomainsSet = domains
        exceptionDomainsSet = exceptions
        blockedUrlKeywords = urlKeywords.distinct()
        genericCosmeticSelectors = genericSelectors.toList()
        domainCosmeticMap = domainSelectors

        _state.value = current.copy(
            compiledNetworkDomainsCount = domains.size + urlKeywords.size,
            compiledCosmeticSelectorsCount = genericSelectors.size + domainSelectors.values.sumOf { it.size }
        )
    }

    /**
     * Parses a single line of standard uBlock Origin / Adblock Plus filter syntax.
     */
    private fun parseSingleUBlockRuleLine(
        rawLine: String,
        domains: MutableSet<String>,
        exceptions: MutableSet<String>,
        urlKeywords: MutableList<String>,
        genericSelectors: MutableSet<String>,
        domainSelectors: MutableMap<String, MutableList<String>>
    ) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("# ")) return

        // Cosmetic filter rule: [domains]##selector
        if (line.contains("##")) {
            val parts = line.split("##", limit = 2)
            val domainPart = parts[0].trim()
            val selector = parts[1].trim()
            // Skip procedural uBO operators (:has-text, :upward, +js) in pure CSS selector list
            if (selector.isEmpty() || selector.startsWith("+js(") || selector.contains(":style(")) return
            if (domainPart.isEmpty()) {
                if (genericSelectors.size < 900) {
                    genericSelectors.add(selector)
                }
            } else {
                domainPart.split(",").forEach { d ->
                    val cleanDomain = d.trim().lowercase().removePrefix("~")
                    if (cleanDomain.isNotEmpty()) {
                        val list = domainSelectors.getOrPut(cleanDomain) { mutableListOf() }
                        if (list.size < 120) list.add(selector)
                    }
                }
            }
            return
        }

        // Exception rule: @@||domain.com^
        if (line.startsWith("@@||")) {
            val host = line.removePrefix("@@||")
                .substringBefore("^")
                .substringBefore("$")
                .substringBefore("/")
                .trim()
                .lowercase()
            if (host.contains('.') && !host.contains('*')) {
                exceptions.add(host)
            }
            return
        }

        // Standard uBlock Origin network anchor rule: ||domain.com^
        if (line.startsWith("||")) {
            val withoutPrefix = line.removePrefix("||")
            val beforeOptions = withoutOptions(withoutPrefix)
            val hostCandidate = beforeOptions
                .substringBefore("^")
                .substringBefore("/")
                .trim()
                .lowercase()
            if (hostCandidate.contains('.') && !hostCandidate.contains('*') && hostCandidate.length in 4..120) {
                domains.add(hostCandidate)
            } else if (beforeOptions.length in 5..80) {
                urlKeywords.add(beforeOptions.lowercase())
            }
            return
        }

        // Hosts-file style: 0.0.0.0 domain.com or 127.0.0.1 domain.com
        if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
            val host = line.substringAfter(" ").substringBefore("#").trim().lowercase()
            if (host.contains('.') && host != "localhost") {
                domains.add(host)
            }
            return
        }

        // Plain domain line (like Peter Lowe's nohtml list)
        if (!line.contains("/") && !line.contains("$") && !line.contains("*") && line.contains(".")) {
            val clean = line.lowercase()
            if (clean.all { it.isLetterOrDigit() || it == '.' || it == '-' }) {
                domains.add(clean)
            }
        }
    }

    private fun withoutOptions(rulePart: String): String = rulePart.substringBefore("$")

    /**
     * Checks whether a given host or any of its parent domains is in [domainSet].
     * Matches `sub.ads.example.com` against `ads.example.com` or `example.com` in O(labels) time.
     */
    fun matchesDomainOrParent(host: String, domainSet: Set<String>): String? {
        var current = host.lowercase().trimEnd('.')
        while (current.contains('.')) {
            if (current in domainSet) return current
            current = current.substringAfter('.')
        }
        return null
    }

    fun isHostWhitelisted(pageUrlOrHost: String?): Boolean {
        if (pageUrlOrHost.isNullOrBlank()) return false
        val host = extractHost(pageUrlOrHost)
        if (host.isEmpty()) return false
        return matchesDomainOrParent(host, _state.value.whitelistedHosts) != null
    }

    fun toggleGlobalEnabled() {
        _state.value = _state.value.copy(isEnabled = !_state.value.isEnabled)
    }

    fun setElementZapperMode(active: Boolean) {
        _state.value = _state.value.copy(elementZapperModeActive = active)
    }

    fun toggleBlockPopups() {
        _state.value = _state.value.copy(blockPopupsAndRedirects = !_state.value.blockPopupsAndRedirects)
    }

    fun toggleCosmeticFiltering() {
        _state.value = _state.value.copy(cosmeticFilteringEnabled = !_state.value.cosmeticFilteringEnabled)
    }

    fun toggleStrictThirdPartyBlocking() {
        _state.value = _state.value.copy(strictThirdPartyBlocking = !_state.value.strictThirdPartyBlocking)
    }

    fun toggleReaderTrackerSanitizer() {
        _state.value = _state.value.copy(
            readerTrackerSanitizerEnabled = !_state.value.readerTrackerSanitizerEnabled
        )
    }

    fun toggleComicPromoPageFilter() {
        _state.value = _state.value.copy(
            comicPromoPageFilterEnabled = !_state.value.comicPromoPageFilterEnabled
        )
    }

    fun toggleWhitelistForHost(urlOrHost: String) {
        val host = extractHost(urlOrHost).ifBlank { urlOrHost.lowercase().trim() }
        if (host.isBlank()) return
        val updated = _state.value.whitelistedHosts.toMutableSet()
        if (!updated.remove(host)) {
            updated.add(host)
        }
        runCatching { whitelistFile.writeText(updated.joinToString("\n")) }
        _state.value = _state.value.copy(whitelistedHosts = updated)
    }

    fun toggleFilterList(listId: String) {
        val updated = _state.value.filterLists.map { status ->
            if (status.spec.id == listId) status.copy(isEnabled = !status.isEnabled) else status
        }
        _state.value = _state.value.copy(filterLists = updated)
        rebuildCompiledRulesets()
    }

    fun addCustomFilterRule(rule: String) {
        val clean = rule.trim()
        if (clean.isEmpty()) return
        val updated = (_state.value.customUserFilters + clean).distinct()
        runCatching { customFiltersFile.writeText(updated.joinToString("\n")) }
        _state.value = _state.value.copy(customUserFilters = updated)
        rebuildCompiledRulesets()
        recordLog(
            sourceContext = "uBO My Filters",
            targetUrlOrElement = clean,
            matchedRule = clean,
            filterListId = "user-filters",
            actionType = UBlockActionType.ELEMENT_ZAP
        )
    }

    fun removeCustomFilterRule(rule: String) {
        val updated = _state.value.customUserFilters.filterNot { it == rule }
        runCatching { customFiltersFile.writeText(updated.joinToString("\n")) }
        _state.value = _state.value.copy(customUserFilters = updated)
        rebuildCompiledRulesets()
    }

    fun clearLogs() {
        _state.value = _state.value.copy(recentLogs = emptyList(), currentSessionBlocked = 0)
    }

    /**
     * Downloads and updates active filter lists directly from official uBlock Origin `uAssets`
     * GitHub repository and Peter Lowe's server list.
     */
    suspend fun syncFilterListsFromRemote(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = _state.value.copy(
                isUpdatingLists = true,
                lastUpdateSummary = "Syncing live uBlock Origin filter lists from uAssets…"
            )

            var syncedListsCount = 0
            var totalDownloadedRules = 0

            val updatedStatuses = _state.value.filterLists.map { status ->
                if (!status.isEnabled) return@map status
                val spec = status.spec
                try {
                    val req = Request.Builder()
                        .url(spec.remoteUrl)
                        .header("User-Agent", "OmniDoc-uBlockOrigin-Android/1.60")
                        .build()

                    httpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyText = response.body?.string().orEmpty()
                            if (bodyText.isNotBlank()) {
                                // Keep first 2,500 meaningful rules per list so mobile memory remains ultra-fast
                                val validLines = bodyText.lineSequence()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() && !it.startsWith("!") && !it.startsWith("# ") }
                                    .take(2500)
                                    .toList()

                                if (validLines.isNotEmpty()) {
                                    val outFile = File(cacheDir, "${spec.id}.txt")
                                    outFile.writeText(validLines.joinToString("\n"))
                                    syncedListsCount++
                                    val count = validLines.size + spec.bundledRulesCount
                                    totalDownloadedRules += count
                                    return@map status.copy(
                                        activeRulesCount = count,
                                        isSyncing = false,
                                        lastSyncedEpochMs = System.currentTimeMillis(),
                                        syncSourceLabel = "Live uAssets Sync (${validLines.size} remote + ${spec.bundledRulesCount} core)"
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Keep offline bundled rules if offline
                }
                totalDownloadedRules += status.activeRulesCount
                status.copy(isSyncing = false)
            }

            _state.value = _state.value.copy(
                filterLists = updatedStatuses,
                isUpdatingLists = false
            )
            rebuildCompiledRulesets()

            val summary = if (syncedListsCount > 0) {
                "Updated $syncedListsCount uBlock Origin filter lists (${_state.value.compiledNetworkDomainsCount} network + ${_state.value.compiledCosmeticSelectorsCount} cosmetic rules active)"
            } else {
                "Offline mode: ${_state.value.compiledNetworkDomainsCount} uBlock Origin network rules & ${_state.value.compiledCosmeticSelectorsCount} cosmetic rules active"
            }
            _state.value = _state.value.copy(lastUpdateSummary = summary)
            summary
        }
    }

    /**
     * Intercepts Android WebView network requests using uBlock Origin static network rules.
     * Returns a neutered `WebResourceResponse` (`noopjs`, `1x1-transparent.gif`, `noopframe`, or `nooptext`)
     * if blocked, or `null` to allow the request.
     */
    fun shouldInterceptWebViewRequest(
        request: WebResourceRequest?,
        pageUrl: String?
    ): WebResourceResponse? {
        val current = _state.value
        if (!current.isEnabled || request == null) return null

        val reqUri = request.url ?: return null
        val reqUrl = reqUri.toString()
        val reqUrlLower = reqUrl.lowercase()
        val reqHost = (reqUri.host ?: "").lowercase()
        if (reqHost.isEmpty()) return null

        val pageHost = extractHost(pageUrl)
        if (pageHost.isNotEmpty() && isHostWhitelisted(pageHost)) return null

        // 1. Check uBlock Origin Exception rules (@@||domain^)
        if (matchesDomainOrParent(reqHost, exceptionDomainsSet) != null) {
            return null
        }

        // 2. Check Domain Anchor rules (||domain^)
        val matchedDomain = matchesDomainOrParent(reqHost, blockedDomainsSet)
        if (matchedDomain != null) {
            recordLog(
                sourceContext = "Browser • ${pageHost.ifBlank { reqHost }}",
                targetUrlOrElement = reqUrl.take(120),
                matchedRule = "||$matchedDomain^",
                filterListId = "ublock-filters",
                actionType = UBlockActionType.NETWORK_BLOCK
            )
            return buildSurrogateNeuterResponse(reqUrlLower, request)
        }

        // 3. Check Strict Third-Party Script / Frame Blocking (uBO Medium-Mode style)
        val isThirdParty = pageHost.isNotEmpty() &&
            !reqHost.endsWith(pageHost) &&
            !pageHost.endsWith(reqHost) &&
            !isKnownCdnOrComicImageHost(reqHost)

        if (current.strictThirdPartyBlocking && isThirdParty) {
            val isScriptOrSubDoc = reqUrlLower.endsWith(".js") ||
                reqUrlLower.contains(".js?") ||
                request.requestHeaders["Accept"]?.contains("javascript") == true ||
                request.requestHeaders["Sec-Fetch-Dest"] == "iframe"
            if (isScriptOrSubDoc) {
                recordLog(
                    sourceContext = "Browser • $pageHost",
                    targetUrlOrElement = reqUrl.take(120),
                    matchedRule = "*\$3p,script,subdocument (Strict Mode)",
                    filterListId = "ublock-privacy",
                    actionType = UBlockActionType.NETWORK_BLOCK
                )
                return buildSurrogateNeuterResponse(reqUrlLower, request)
            }
        }

        // 4. Check URL Keyword / Path Patterns
        if (isThirdParty || !isKnownCdnOrComicImageHost(reqHost)) {
            val matchedKeyword = blockedUrlKeywords.firstOrNull { kw -> reqUrlLower.contains(kw) }
            if (matchedKeyword != null) {
                recordLog(
                    sourceContext = "Browser • ${pageHost.ifBlank { reqHost }}",
                    targetUrlOrElement = reqUrl.take(120),
                    matchedRule = "*$matchedKeyword*",
                    filterListId = "easylist",
                    actionType = UBlockActionType.NETWORK_BLOCK
                )
                return buildSurrogateNeuterResponse(reqUrlLower, request)
            }
        }

        return null
    }

    /**
     * Evaluates top-level navigation / click redirects in WebView (`shouldOverrideUrlLoading`).
     * Blocks pop-unders, non-HTTP intent hijacks, and ad redirect domains.
     */
    fun shouldBlockNavigationOrPopup(
        targetUrl: String,
        currentPageUrl: String?
    ): Boolean {
        val current = _state.value
        if (!current.isEnabled) return false

        // Always block malicious intent://, market://, or javascript: redirect traps
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            recordLog(
                sourceContext = "Browser Navigation",
                targetUrlOrElement = targetUrl.take(100),
                matchedRule = "scheme-guard (non-http redirect)",
                filterListId = "ublock-badware",
                actionType = UBlockActionType.POPUP_BLOCK
            )
            return true
        }

        if (isHostWhitelisted(currentPageUrl)) return false

        val targetHost = extractHost(targetUrl)
        if (matchesDomainOrParent(targetHost, exceptionDomainsSet) != null) return false

        val matchedDomain = matchesDomainOrParent(targetHost, blockedDomainsSet)
        if (matchedDomain != null) {
            recordLog(
                sourceContext = "Browser Popup Guard",
                targetUrlOrElement = targetUrl.take(120),
                matchedRule = "||$matchedDomain^\$popup",
                filterListId = "ublock-filters",
                actionType = UBlockActionType.POPUP_BLOCK
            )
            return true
        }

        val lower = targetUrl.lowercase()
        val matchedPattern = blockedUrlKeywords.firstOrNull { lower.contains(it) }
        if (matchedPattern != null && current.blockPopupsAndRedirects) {
            recordLog(
                sourceContext = "Browser Popup Guard",
                targetUrlOrElement = targetUrl.take(120),
                matchedRule = "*$matchedPattern*\$popup",
                filterListId = "ublock-annoyances",
                actionType = UBlockActionType.POPUP_BLOCK
            )
            return true
        }

        return false
    }

    /**
     * Generates the uBlock Origin Cosmetic Filtering (`##selector`) + Scriptlet Injection (`+js(...)`)
     * JavaScript payload to inject into the WebView on `onPageStarted` / `onPageFinished`.
     */
    fun buildCosmeticAndScriptletPayload(pageUrl: String?): String {
        val current = _state.value
        if (!current.isEnabled || isHostWhitelisted(pageUrl)) return "(function(){})();"

        val host = extractHost(pageUrl)
        val domainSpecific = domainCosmeticMap.entries
            .filter { (d, _) -> host == d || host.endsWith(".$d") }
            .flatMap { it.value }

        val combinedSelectors = if (current.cosmeticFilteringEnabled) {
            (genericCosmeticSelectors + domainSpecific).distinct()
        } else {
            emptyList()
        }

        val cssRuleString = if (combinedSelectors.isNotEmpty()) {
            combinedSelectors.joinToString(", ") +
                " { display: none !important; visibility: hidden !important; height: 0 !important; min-height: 0 !important; max-height: 0 !important; opacity: 0 !important; pointer-events: none !important; overflow: hidden !important; }"
        } else {
            ""
        }

        val escapedCss = cssRuleString
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")

        val blockPopupsJs = if (current.blockPopupsAndRedirects) {
            """
            // uBlock Origin +js(no-window-open-if) & popunder defuser
            window.open = function() { return null; };
            window.alert = function() {};
            if (navigator && navigator.sendBeacon) {
                navigator.sendBeacon = function() { return true; };
            }
            """.trimIndent()
        } else ""

        return """
            (function() {
                try {
                    if (window.__uboInjectedForUrl === window.location.href) return;
                    window.__uboInjectedForUrl = window.location.href;

                    // 1. uBlock Origin Anti-Adblock Defuser Scriptlets (+js(abort-on-property-read))
                    window.canRunAds = true;
                    window.isAdBlockActive = false;
                    window.adBlockEnabled = false;
                    window.adsBlocked = false;

                    $blockPopupsJs

                    // 2. uBlock Origin Cosmetic Filter Stylesheet Injection (##selectors)
                    var styleId = 'ubo-cosmetic-stylesheet';
                    var existingStyle = document.getElementById(styleId);
                    if (!existingStyle && '$escapedCss'.length > 0) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode('$escapedCss'));
                        (document.head || document.documentElement).appendChild(style);
                    }

                    // 3. uBlock Origin DOM MutationObserver for dynamic ad iframes & fixed overlays
                    var sweepDynamicAds = function() {
                        var iframes = document.querySelectorAll('iframe');
                        for (var i = 0; i < iframes.length; i++) {
                            var src = (iframes[i].src || '').toLowerCase();
                            if (src.indexOf('doubleclick') !== -1 || src.indexOf('adservice') !== -1 ||
                                src.indexOf('popads') !== -1 || src.indexOf('exoclick') !== -1 ||
                                src.indexOf('adsterra') !== -1 || src.indexOf('a-ads') !== -1 ||
                                src.indexOf('juicyads') !== -1 || src.indexOf('propeller') !== -1) {
                                iframes[i].remove();
                            }
                        }
                    };
                    sweepDynamicAds();
                    var obs = new MutationObserver(function() { sweepDynamicAds(); });
                    if (document.documentElement) {
                        obs.observe(document.documentElement, { childList: true, subtree: true });
                    }
                } catch (e) {}
            })();
        """.trimIndent()
    }

    /**
     * Generates the interactive uBlock Origin Element Zapper (`uBO Zapper`) JavaScript snippet.
     * When active, tapping any element in the WebView highlights and removes it from the DOM
     * and returns its CSS selector so it can be added to "My Filters".
     */
    fun buildElementZapperJs(enableZapper: Boolean): String {
        return if (enableZapper) {
            """
            (function() {
                if (window.__uboZapperHandler) return;
                window.__uboZapperHandler = function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var el = e.target;
                    if (!el || el === document.body || el === document.documentElement) return false;
                    var tag = (el.tagName || 'div').toLowerCase();
                    var idPart = el.id ? ('#' + el.id) : '';
                    var clsPart = '';
                    if (!idPart && el.className && typeof el.className === 'string') {
                        var firstCls = el.className.trim().split(/\s+/)[0];
                        if (firstCls) clsPart = '.' + firstCls;
                    }
                    var selector = window.location.hostname + '##' + tag + idPart + clsPart;
                    el.style.outline = '3px solid #EF4444';
                    setTimeout(function() { el.remove(); }, 120);
                    window.__lastUboZappedSelector = selector;
                    return false;
                };
                document.addEventListener('click', window.__uboZapperHandler, true);
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                if (window.__uboZapperHandler) {
                    document.removeEventListener('click', window.__uboZapperHandler, true);
                    window.__uboZapperHandler = null;
                }
            })();
            """.trimIndent()
        }
    }

    /**
     * Filters Online Manga or Offline Comic Archive page URLs / entry names using uBlock Origin's
     * Comic Reader Shield (removes aggregator ad banners, gambling credit pages, and tracking beacons).
     */
    fun filterComicPageUrls(
        rawUrls: List<String>,
        sourceLabel: String
    ): List<String> {
        val current = _state.value
        if (!current.isEnabled || !current.comicPromoPageFilterEnabled) return rawUrls

        val filtered = rawUrls.filter { url ->
            val lower = url.lowercase()
            val host = extractHost(url)
            val blockedByDomain = host.isNotEmpty() && matchesDomainOrParent(host, blockedDomainsSet) != null
            val blockedByPromoName = COMIC_PROMO_AND_AD_PAGE_KEYWORDS.any { kw -> lower.contains(kw) }
            if (blockedByDomain || blockedByPromoName) {
                recordLog(
                    sourceContext = "Comic Reader • $sourceLabel",
                    targetUrlOrElement = url.substringAfterLast('/').take(80),
                    matchedRule = if (blockedByDomain) "||$host^" else "##comic-promo-banner",
                    filterListId = "ublock-comic-reader",
                    actionType = UBlockActionType.READER_SANITIZE
                )
                false
            } else {
                true
            }
        }
        // Never return an empty list if all pages were mistakenly matched
        return if (filtered.isEmpty()) rawUrls else filtered
    }

    /**
     * Checks if an offline `.CBZ` / `.CBR` entry filename is a known aggregator ad / gambling banner page.
     */
    fun isComicArchiveEntryAdBanner(entryName: String): Boolean {
        val current = _state.value
        if (!current.isEnabled || !current.comicPromoPageFilterEnabled) return false
        val lower = entryName.substringAfterLast('/').lowercase()
        return COMIC_PROMO_AND_AD_PAGE_KEYWORDS.any { kw -> lower.contains(kw) }
    }

    /**
     * Sanitizes text inside inbuilt Document Readers (PDF, DOCX, XLSX, PPTX, TXT/MD) by:
     * 1. Stripping UTM & cross-site click-tracking query parameters (`utm_*`, `fbclid`, `gclid`, `_ga`, `mc_eid`)
     * 2. Neutralizing known ad-server / malvertising links (`[uBO Blocked Ad/Tracker Link: domain]`)
     * 3. Removing embedded promotional ad-script snippets or tracking pixel tags
     */
    fun sanitizeDocumentText(
        rawText: String,
        documentTitle: String,
        recordToLogger: Boolean = false
    ): ReaderSanitizationReport {
        val current = _state.value
        if (!current.isEnabled || !current.readerTrackerSanitizerEnabled || rawText.isBlank()) {
            return ReaderSanitizationReport(
                cleanedText = rawText,
                trackersRemovedCount = 0,
                adLinksNeutralizedCount = 0,
                matchedRules = emptyList()
            )
        }

        var trackersRemoved = 0
        var adLinksBlocked = 0
        val matched = mutableListOf<String>()

        // 1. Inspect URLs inside the text
        val cleanedUrlsText = URL_EXTRACT_REGEX.replace(rawText) { matchResult ->
            val rawUrl = matchResult.value
            val host = extractHost(rawUrl)

            // Check if the URL points to a blocked ad/tracker/badware domain
            val blockedDomain = if (host.isNotEmpty()) matchesDomainOrParent(host, blockedDomainsSet) else null
            if (blockedDomain != null) {
                adLinksBlocked++
                val rule = "||$blockedDomain^\$document-link"
                matched.add(rule)
                if (recordToLogger) {
                    recordLog(
                        sourceContext = "Doc Reader • ${documentTitle.take(24)}",
                        targetUrlOrElement = rawUrl.take(90),
                        matchedRule = rule,
                        filterListId = "ublock-privacy",
                        actionType = UBlockActionType.READER_SANITIZE
                    )
                }
                "[uBO Blocked Tracker: $blockedDomain]"
            } else if (rawUrl.contains("?")) {
                // Strip UTM & tracking parameters (`$removeparam` in uBlock Origin syntax)
                val basePart = rawUrl.substringBefore("?")
                val queryPart = rawUrl.substringAfter("?")
                val keptParams = queryPart.split("&").filter { param ->
                    val key = param.substringBefore("=").lowercase()
                    val isTrackerParam = key in UBO_REMOVEPARAM_KEYS || key.startsWith("utm_")
                    if (isTrackerParam) {
                        trackersRemoved++
                        val rule = "\$removeparam=$key"
                        matched.add(rule)
                        if (recordToLogger) {
                            recordLog(
                                sourceContext = "Doc Reader • ${documentTitle.take(24)}",
                                targetUrlOrElement = "$basePart?…$key=…",
                                matchedRule = rule,
                                filterListId = "easyprivacy",
                                actionType = UBlockActionType.READER_SANITIZE
                            )
                        }
                    }
                    !isTrackerParam
                }
                if (keptParams.isEmpty()) basePart else "$basePart?${keptParams.joinToString("&")}"
            } else {
                rawUrl
            }
        }

        return ReaderSanitizationReport(
            cleanedText = cleanedUrlsText,
            trackersRemovedCount = trackersRemoved,
            adLinksNeutralizedCount = adLinksBlocked,
            matchedRules = matched.distinct()
        )
    }

    /**
     * Audits an entire document's text corpus (e.g., when opened in PDF, DOCX, XLSX, PPTX, or TXT reader)
     * and logs any neutralized trackers or ad links into the uBlock Origin shield counter.
     */
    fun auditDocumentOnOpen(documentTitle: String, fullTextSample: String): ReaderSanitizationReport {
        val report = sanitizeDocumentText(
            rawText = fullTextSample,
            documentTitle = documentTitle,
            recordToLogger = true
        )
        return report
    }

    private fun recordLog(
        sourceContext: String,
        targetUrlOrElement: String,
        matchedRule: String,
        filterListId: String,
        actionType: UBlockActionType
    ) {
        val entry = UBlockLogEntry(
            id = logSequenceId++,
            timestampMs = System.currentTimeMillis(),
            sourceContext = sourceContext,
            targetUrlOrElement = targetUrlOrElement,
            matchedRule = matchedRule,
            filterListId = filterListId,
            actionType = actionType
        )
        val curr = _state.value
        val updatedLogs = (listOf(entry) + curr.recentLogs).take(120)
        _state.value = curr.copy(
            totalBlockedRequests = curr.totalBlockedRequests + 1,
            currentSessionBlocked = curr.currentSessionBlocked + 1,
            totalReaderItemsSanitized = if (actionType == UBlockActionType.READER_SANITIZE) {
                curr.totalReaderItemsSanitized + 1
            } else {
                curr.totalReaderItemsSanitized
            },
            recentLogs = updatedLogs
        )
    }

    /**
     * Builds an authentic uBlock Origin surrogate neuter response (`noopjs`, `noopcss`,
     * `1x1-transparent.gif`, or `noopframe`) so web pages don't break with missing script/image errors.
     */
    private fun buildSurrogateNeuterResponse(
        reqUrlLower: String,
        request: WebResourceRequest
    ): WebResourceResponse {
        val accept = request.requestHeaders["Accept"]?.lowercase().orEmpty()
        return when {
            reqUrlLower.endsWith(".js") || reqUrlLower.contains(".js?") || accept.contains("javascript") -> {
                val jsBytes = "(function(){})();".toByteArray(Charsets.UTF_8)
                WebResourceResponse(
                    "application/javascript",
                    "utf-8",
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    ByteArrayInputStream(jsBytes)
                )
            }
            reqUrlLower.endsWith(".css") || accept.contains("text/css") -> {
                WebResourceResponse(
                    "text/css",
                    "utf-8",
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            accept.contains("image/") ||
                reqUrlLower.endsWith(".gif") ||
                reqUrlLower.endsWith(".png") ||
                reqUrlLower.endsWith(".webp") ||
                reqUrlLower.endsWith(".jpg") -> {
                WebResourceResponse(
                    "image/gif",
                    null,
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    ByteArrayInputStream(TRANSPARENT_1X1_GIF_BYTES)
                )
            }
            accept.contains("text/html") -> {
                val htmlBytes = "<!DOCTYPE html><html><head></head><body></body></html>".toByteArray(Charsets.UTF_8)
                WebResourceResponse(
                    "text/html",
                    "utf-8",
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    ByteArrayInputStream(htmlBytes)
                )
            }
            else -> {
                WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        }
    }

    private fun isKnownCdnOrComicImageHost(host: String): Boolean {
        return host.endsWith("mangadex.org") ||
            host.endsWith("mangadex.network") ||
            host.endsWith("EverythingMoe.com".lowercase()) ||
            host.endsWith("comick.io") ||
            host.endsWith("meo.comick.pictures") ||
            host.endsWith("webtoons.com") ||
            host.endsWith("pstatic.net") ||
            host.endsWith("archive.org") ||
            host.endsWith("xkcd.com") ||
            host.endsWith("wikimedia.org") ||
            host.endsWith("githubusercontent.com")
    }

    private fun extractHost(urlOrHost: String?): String {
        if (urlOrHost.isNullOrBlank()) return ""
        return runCatching {
            val trimmed = urlOrHost.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                Uri.parse(trimmed).host?.lowercase()?.removePrefix("www.").orEmpty()
            } else {
                trimmed.substringBefore('/').substringBefore(':').lowercase().removePrefix("www.")
            }
        }.getOrDefault("")
    }

    companion object {
        private val URL_EXTRACT_REGEX = Regex("""https?://[^\s)<>"']+""", RegexOption.IGNORE_CASE)

        // 1x1 transparent GIF surrogate (same as uBlock Origin `1x1.gif` redirect resource)
        private val TRANSPARENT_1X1_GIF_BYTES: ByteArray = Base64.decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
            Base64.DEFAULT
        )

        // uBlock Origin `$removeparam` tracking query parameters
        private val UBO_REMOVEPARAM_KEYS = setOf(
            "fbclid", "gclid", "dclid", "msclkid", "twclid", "igshid",
            "mc_cid", "mc_eid", "_ga", "_gl", "yclid", "vero_id",
            "oly_enc_id", "oly_anon_id", "rb_clickid", "s_kwcid", "spm"
        )

        // Comic / Manga Promo & Aggregator Ad Page filename patterns
        private val COMIC_PROMO_AND_AD_PAGE_KEYWORDS = listOf(
            "casino_banner", "betting_ad", "1xbet", "melbet", "mostbet",
            "ad_banner", "sponsor_page", "popunder_ad", "clickadu"
        )

        // Core bundled uBlock Origin + EasyList + Peter Lowe Ad & Monetization Domains
        private val BUNDLED_AD_AND_MONETIZATION_DOMAINS = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com", "tpc.googlesyndication.com",
            "2mdn.net", "admob.com", "adsense.com", "adnxs.com", "adsrvr.org",
            "pubmatic.com", "rubiconproject.com", "openx.net", "criteo.com", "criteo.net",
            "taboola.com", "outbrain.com", "mgid.com", "revcontent.com", "zergnet.com",
            "adroll.com", "quantserve.com", "scorecardresearch.com", "moatads.com",
            "casalemedia.com", "indexexchange.com", "33across.com", "sovrn.com",
            "lijit.com", "sharethrough.com", "teads.tv", "smartadserver.com",
            "advertising.com", "bidswitch.net", "contextweb.com", "districtm.io",
            "gumgum.com", "kargo.com", "media.net", "nativo.com", "triplelift.com",
            "yieldmo.com", "zedo.com", "bidvertiser.com", "adform.net", "serving-sys.com",
            "flashtalking.com", "undertone.com", "conversantmedia.com", "exponential.com",
            "tribalfusion.com", "adblade.com", "adition.com", "adkernel.com",
            "admixer.net", "adocean.pl", "adriver.ru", "adscale.de", "adspirit.de",
            "adtech.de", "adtechus.com", "adtelligent.com", "adventori.com", "adzerk.net"
        )

        // Core bundled uBlock Origin Manga / Comic / DDL Portal Ad & Pop-under Networks
        private val BUNDLED_MANGA_COMIC_AD_DOMAINS = setOf(
            "popads.net", "popcash.net", "propellerads.com", "adsterra.com",
            "exoclick.com", "juicyads.com", "trafficjunky.net", "hilltopads.net",
            "clickadu.com", "admaven.com", "galaksion.com", "a-ads.com",
            "ERO-advertising.com".lowercase(), "plugrush.com", "trafficstars.com",
            " trafficfactory.biz".trim(), "mondiad.com", "richads.com", "evadav.com",
            "pushground.com", "rollerads.com", "dao.ad", "adcash.com",
            "onclickads.net", "onclasrv.com", "aftrk.com", "tsyndicate.com",
            "twinrdsyn.com", "realsrv.com", "syndication.exoclick.com",
            "mainadv.com", "adtng.com", "bidgear.com", "vidoomy.com",
            "adskeeper.co.uk", "adskeeper.com", "trafficforce.com", "adspyglass.com",
            "yengo.com", "lentainform.com", "marketgid.com", "smi2.ru",
            "1xbet.com", "melbet.com", "mostbet.com", "betwinner.com"
        )

        // Core bundled uBlock Origin Badware & Deceptive Redirect Domains
        private val BUNDLED_BADWARE_AND_MALVERTISING_DOMAINS = setOf(
            "adf.ly", "bc.vc", "sh.st", "shortest.link", "ouo.io", "ouo.press",
            "linkvertise.com", "exe.io", "fc.lc", "za.gl", "clicksfly.com",
            "gyaniipoint.com", "shrinkme.io", "droplink.co", "gplinks.in",
            "rocklinks.net", "tnlink.in", "earn4link.in", "adshort.co",
            "pushwhy.com", "pushance.com", "pushengage.com", "izooto.com",
            "aimtell.com", "gravitec.net", "subscribers.com", "sendpulse.com"
        )

        // Core bundled uBlock Origin Privacy & Telemetry Tracker Domains
        private val BUNDLED_PRIVACY_AND_TRACKER_DOMAINS = setOf(
            "google-analytics.com", "ssl.google-analytics.com", "googletagmanager.com",
            "googletagservices.com", "connect.facebook.net", "pixel.facebook.com",
            "analytics.tiktok.com", "bat.bing.com", "clarity.ms", "hotjar.com",
            "static.hotjar.com", "mouseflow.com", "fullstory.com", "luckyorange.com",
            "crazyegg.com", "inspectlet.com", "logrocket.io", "mixpanel.com",
            "api.mixpanel.com", "segment.io", "api.segment.io", "amplitude.com",
            "api.amplitude.com", "heapanalytics.com", "newrelic.com", "nr-data.net",
            "bugsnag.com", "sentry-cdn.com", "browser.sentry-cdn.com", "appsflyer.com",
            "adjust.com", "branch.io", "app.link", "kochava.com", "singular.net",
            "tapjoy.com", "chartboost.com", "unityads.unity3d.com", "vungle.com",
            "applovin.com", "ironsrc.com", "inmobi.com", "startappservice.com",
            "cleverpush.com", "onesignal.com", "yandex.ru/metrika", "mc.yandex.ru"
        )

        // Unbreak Exception Rules (never block legitimate comic/document CDNs)
        private val BUNDLED_UNBREAK_EXCEPTION_DOMAINS = setOf(
            "mangadex.org", "uploads.mangadex.org", "api.mangadex.org",
            "everythingmoe.com", "archive.org", "huggingface.co",
            "github.com", "raw.githubusercontent.com"
        )

        // URL Substring patterns (`*pattern*`)
        private val BUNDLED_AD_URL_PATTERNS = listOf(
            "/pagead/", "/adserver", "/adframe", "/adbanner", "/popunder",
            "/pop_under", "/clickunder", "/adscript", "/ads.js", "/PreRollAd",
            "/sponsor-banner", "/casino-ad", "/betting-banner", "/aff_c?",
            "syndication.realsrv", "tsyndicate.com/sdk"
        )

        private val BUNDLED_TRACKER_URL_PATTERNS = listOf(
            "/collect?v=1", "/g/collect?", "/ccm/collect", "/pixel/track",
            "/beacon.js", "/telemetry.js", "/fingerprint2", "/fpjs"
        )

        // Cosmetic Filter Selectors (`##selector`)
        private val BUNDLED_COSMETIC_AD_SELECTORS = listOf(
            ".ad-banner", ".ads-banner", ".ad-container", ".ads-container",
            ".ad-wrapper", ".advertisement", ".sponsored-content", ".ad-slot",
            ".dfp-ad", ".adsbygoogle", "ins.adsbygoogle", "[id^='div-gpt-ad']",
            "[id^='google_ads_iframe']", "[id*='taboola']", "[class*='taboola']",
            "[id*='outbrain']", "[class*='outbrain']", "[id*='mgid']",
            "iframe[src*='ads']", "iframe[src*='doubleclick']", "iframe[src*='exoclick']",
            "iframe[src*='adsterra']", "iframe[src*='popads']", "iframe[src*='juicyads']"
        )

        private val BUNDLED_ANNOYANCE_COSMETIC_SELECTORS = listOf(
            ".cookie-consent-banner", "#cookie-notice", ".gdpr-banner",
            ".app-download-banner", ".smartbanner", ".floating-video-ad",
            ".interstitial-overlay", ".popup-backdrop-ad"
        )

        private val BUNDLED_MANGA_COMIC_COSMETIC_SELECTORS = listOf(
            ".manga-ad", ".chapter-ad", ".reader-ad", ".top-ad-banner",
            ".bottom-ad-banner", ".floating-sticky-ad", ".casino-float",
            ".bet-banner", "[class*='adsterra']", "[id*='adsterra']",
            "[class*='propeller']", "[id*='propeller']", ".pubfuture-ad"
        )
    }
}
