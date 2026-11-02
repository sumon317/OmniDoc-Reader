package com.example.ui.viewers

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.engine.EverythingMoePortalSource
import com.example.engine.OnlineComicChapter
import com.example.engine.OnlineComicSeries
import com.example.engine.OnlineDownloadProgress
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.ByteArrayInputStream

private val BLOCKED_AD_DOMAINS = listOf(
    "doubleclick.net",
    "googlesyndication.com",
    "adservice.google",
    "popads.net",
    "popcash.net",
    "propellerads.com",
    "adsterra.com",
    "exoclick.com",
    "juicyads.com",
    "trafficjunky.net",
    "hilltopads.net",
    "clickadu.com",
    "admaven.com",
    "galaksion.com",
    "a-ads.com",
    "outbrain.com",
    "taboola.com",
    "zedo.com",
    "bidvertiser.com"
)

/**
 * Full-screen In-App EverythingMoe & Online Manga / Manhwa / Comic / DDL Portal Browser
 * with Ad-Shield, Reader Comfort Injector, Direct .CBZ/.CBR/.PDF Download Interceptor,
 * and 1-Tap "Pack Page Images to Offline .CBZ" converter.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EverythingMoePortalBrowserScreen(
    initialPortal: EverythingMoePortalSource,
    allPortals: List<EverythingMoePortalSource>,
    downloadProgress: OnlineDownloadProgress,
    onDownloadDirectUrl: (url: String, suggestedFilename: String?) -> Unit,
    onPackWebImagesToCbz: (pageTitle: String, imageUrls: List<String>) -> Unit,
    onClose: () -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialPortal.url) }
    var urlInputText by remember { mutableStateOf(initialPortal.url) }
    var pageTitle by remember { mutableStateOf(initialPortal.name) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var adShieldEnabled by remember { mutableStateOf(true) }
    var blockedAdCount by remember { mutableIntStateOf(0) }
    var showUrlEditor by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onClose()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Slate950,
        topBar = {
            Column(modifier = Modifier.background(Slate900)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Slate900,
                        titleContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("portal_browser_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Portal Browser",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showUrlEditor = !showUrlEditor }
                        ) {
                            Text(
                                text = pageTitle.ifBlank { initialPortal.name },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        // Ad-Shield Toggle with blocked count badge
                        Surface(
                            color = if (adShieldEnabled) Emerald400.copy(alpha = 0.18f) else Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable { adShieldEnabled = !adShieldEnabled }
                                .padding(end = 4.dp)
                                .testTag("portal_ad_shield_toggle")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Ad Shield",
                                    tint = if (adShieldEnabled) Emerald400 else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (adShieldEnabled) "Shield ($blockedAdCount)" else "Off",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (adShieldEnabled) Emerald400 else Color.LightGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Reader Mode CSS Enhancer (fits comic images 100% width on dark canvas)
                        IconButton(
                            onClick = {
                                webViewRef?.evaluateJavascript(
                                    """
                                    (function() {
                                        document.body.style.backgroundColor = '#090D16';
                                        document.body.style.color = '#E2E8F0';
                                        var imgs = document.querySelectorAll('img');
                                        for (var i = 0; i < imgs.length; i++) {
                                            if (imgs[i].naturalWidth > 280) {
                                                imgs[i].style.maxWidth = '100%';
                                                imgs[i].style.height = 'auto';
                                                imgs[i].style.display = 'block';
                                                imgs[i].style.margin = '0 auto';
                                            }
                                        }
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            },
                            modifier = Modifier.testTag("portal_reader_enhance_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Optimize Comic Page Layout",
                                tint = Amber400
                            )
                        }

                        // Grab current page comic images -> Offline .CBZ
                        IconButton(
                            onClick = {
                                val wv = webViewRef ?: return@IconButton
                                wv.evaluateJavascript(
                                    """
                                    (function() {
                                        var urls = [];
                                        var imgs = document.querySelectorAll('img');
                                        for (var i = 0; i < imgs.length; i++) {
                                            var src = imgs[i].currentSrc || imgs[i].src || imgs[i].getAttribute('data-src') || '';
                                            var w = imgs[i].naturalWidth || imgs[i].width || 0;
                                            var h = imgs[i].naturalHeight || imgs[i].height || 0;
                                            if (src.indexOf('http') === 0 && (w >= 260 || h >= 320 || src.match(/\.(jpg|jpeg|png|webp)/i))) {
                                                if (urls.indexOf(src) === -1) {
                                                    urls.push(src);
                                                }
                                            }
                                        }
                                        return JSON.stringify(urls.slice(0, 85));
                                    })();
                                    """.trimIndent()
                                ) { rawJson ->
                                    runCatching {
                                        val clean = rawJson?.trim() ?: return@runCatching
                                        val unquoted = if (clean.startsWith("\"") && clean.endsWith("\"")) {
                                            clean.substring(1, clean.length - 1).replace("\\\"", "\"")
                                        } else {
                                            clean
                                        }
                                        val arr = JSONArray(unquoted)
                                        val extracted = mutableListOf<String>()
                                        for (i in 0 until arr.length()) {
                                            val u = arr.optString(i)
                                            if (u.startsWith("http")) extracted.add(u)
                                        }
                                        onPackWebImagesToCbz(pageTitle.ifBlank { "Web_Comic_Chapter" }, extracted)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("portal_pack_cbz_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Pack Page Images into Offline .CBZ",
                                tint = Cyan400
                            )
                        }
                    }
                )

                // Expandable custom URL bar
                AnimatedVisibility(visible = showUrlEditor) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = urlInputText,
                            onValueChange = { urlInputText = it },
                            placeholder = { Text("Enter manga, manhwa, comic or DDL URL…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val normalized = if (urlInputText.startsWith("http")) {
                                    urlInputText.trim()
                                } else {
                                    "https://${urlInputText.trim()}"
                                }
                                currentUrl = normalized
                                webViewRef?.loadUrl(normalized)
                                showUrlEditor = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Slate950)
                        ) {
                            Text("Go", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Portal Switcher Strip (EverythingMoe, MangaDex, ComicK, Webtoons, GetComics, etc.)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Cyan400,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    allPortals.forEach { portal ->
                        val isCurrent = currentUrl.contains(
                            Uri.parse(portal.url).host ?: portal.url,
                            ignoreCase = true
                        )
                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                currentUrl = portal.url
                                urlInputText = portal.url
                                webViewRef?.loadUrl(portal.url)
                            },
                            label = {
                                Text(
                                    text = portal.name,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                if (isLoading && loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        color = Amber400,
                        trackColor = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }

                // Live Download Progress Banner inside Portal Browser
                AnimatedVisibility(visible = downloadProgress.isDownloading) {
                    Surface(
                        color = Slate800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = downloadProgress.statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Amber400,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${(downloadProgress.progressFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress.progressFraction },
                                color = Emerald400,
                                trackColor = Slate900,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("everythingmoe_portal_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        setSupportMultipleWindows(false) // Prevents pop-under ad tabs
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    }

                    // Intercept direct file downloads (.cbz, .cbr, .pdf, .zip, .rar, .epub, etc.)
                    setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                        val suggestedFilename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                        onDownloadDirectUrl(url, suggestedFilename)
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            isLoading = newProgress < 100
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            if (!title.isNullOrBlank()) {
                                pageTitle = title
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (!url.isNullOrBlank()) {
                                currentUrl = url
                                urlInputText = url
                            }
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString()?.lowercase() ?: return null
                            if (adShieldEnabled && BLOCKED_AD_DOMAINS.any { reqUrl.contains(it) }) {
                                blockedAdCount++
                                return WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUri = request?.url ?: return false
                            val targetUrl = targetUri.toString()

                            // Block non-http schemes (intent:// malware redirects)
                            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                return true
                            }

                            // Block known ad domains on click
                            if (adShieldEnabled && BLOCKED_AD_DOMAINS.any { targetUrl.contains(it, ignoreCase = true) }) {
                                blockedAdCount++
                                return true
                            }

                            // Auto-intercept direct .cbz, .cbr, .pdf archive links
                            val pathLower = targetUri.path?.lowercase() ?: ""
                            if (
                                pathLower.endsWith(".cbz") ||
                                pathLower.endsWith(".cbr") ||
                                pathLower.endsWith(".pdf")
                            ) {
                                onDownloadDirectUrl(targetUrl, pathLower.substringAfterLast('/'))
                                return true
                            }

                            return false
                        }
                    }

                    loadUrl(initialPortal.url)
                }
            }
        )
    }
}

/**
 * Modal Sheet showing an Online Manga / Manhwa / Manhua / Comic Series,
 * its metadata, tags, synopsis, and live Chapter List with "Read Online" and "Save .CBZ".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSeriesChaptersSheet(
    series: OnlineComicSeries,
    chapters: List<OnlineComicChapter>,
    isLoadingChapters: Boolean,
    downloadProgress: OnlineDownloadProgress,
    onReadChapterOnline: (OnlineComicChapter) -> Unit,
    onDownloadChapterCbz: (OnlineComicChapter) -> Unit,
    onOpenSeriesInWebPortal: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Series Header
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(132.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate800)
                        .border(1.dp, Cyan400.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
                            tint = Cyan400,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Amber400.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
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
                        Text(
                            text = "${series.status}${series.year?.let { " • $it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = series.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "By ${series.author}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { onOpenSeriesInWebPortal(series.webUrl) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open in Web Portal",
                            style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = series.description,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1)),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Active CBZ Download Progress
            AnimatedVisibility(visible = downloadProgress.isDownloading) {
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = downloadProgress.statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress.progressFraction },
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

            Text(
                text = "AVAILABLE CHAPTERS (${chapters.size}) — READ ONLINE OR SAVE TO OFFLINE .CBZ",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Amber400,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingChapters) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    CircularProgressIndicator(color = Amber400)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(chapters, key = { it.id }) { chapter ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate950),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ch. ${chapter.chapterNumber} — ${chapter.title}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${chapter.scanlationGroup} • ${chapter.pageCount} pages${if (chapter.publishDateLabel.isNotBlank()) " • ${chapter.publishDateLabel}" else ""}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onReadChapterOnline(chapter) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Cyan400,
                                            contentColor = Slate950
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Read",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onDownloadChapterCbz(chapter) },
                                        enabled = !downloadProgress.isDownloading,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Save as Offline CBZ",
                                            tint = Amber400,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = ".CBZ",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Amber400,
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
        }
    }
}

/**
 * Full-screen Native Online Chapter Reader (supports Webtoon Continuous Vertical Scroll
 * and Paged Manga Viewer, double-tap zoom, and 1-tap Offline .CBZ export).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeOnlineChapterReaderScreen(
    series: OnlineComicSeries,
    chapter: OnlineComicChapter,
    pageUrls: List<String>,
    isLoadingPages: Boolean,
    downloadProgress: OnlineDownloadProgress,
    onSaveChapterAsOfflineCbz: () -> Unit,
    onOpenInWebPortalFallback: (String) -> Unit,
    onClose: () -> Unit
) {
    var isWebtoonVerticalMode by remember {
        mutableStateOf(series.contentTypeLabel == "MANHWA" || series.contentTypeLabel == "MANHUA")
    }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val pagerState = rememberPagerState(pageCount = { pageUrls.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    BackHandler { onClose() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Slate950,
        topBar = {
            Column(modifier = Modifier.background(Slate900)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Slate900,
                        titleContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Reader",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = series.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Ch. ${chapter.chapterNumber} • ${if (isWebtoonVerticalMode) "Webtoon Scroll" else "Paged Mode"} (${pageUrls.size} pages)",
                                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                            )
                        }
                    },
                    actions = {
                        // Toggle Webtoon Scroll vs Paged Mode
                        IconButton(onClick = { isWebtoonVerticalMode = !isWebtoonVerticalMode }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Switch Scroll / Page Mode",
                                tint = Amber400
                            )
                        }

                        // Save Chapter to Offline .CBZ
                        Button(
                            onClick = onSaveChapterAsOfflineCbz,
                            enabled = !downloadProgress.isDownloading && pageUrls.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald400,
                                contentColor = Slate950
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Save .CBZ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                )

                AnimatedVisibility(visible = downloadProgress.isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.progressFraction },
                        color = Emerald400,
                        trackColor = Slate800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate950)
        ) {
            when {
                isLoadingPages -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(color = Amber400)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching Ch. ${chapter.chapterNumber} pages from server…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                }
                pageUrls.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "This chapter is hosted on an external publisher portal.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap below to open it directly inside OmniDoc's built-in Ad-Shielded Portal Reader.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onOpenInWebPortalFallback(chapter.externalUrl ?: series.webUrl)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Amber400,
                                contentColor = Slate950
                            )
                        ) {
                            Text("Open in In-App Portal Reader", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                isWebtoonVerticalMode -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        zoomScale = if (zoomScale > 1.05f) 1f else 1.75f
                                    }
                                )
                            }
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale
                            )
                    ) {
                        itemsIndexed(pageUrls) { idx, imgUrl ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imgUrl)
                                        .addHeader("Referer", "https://mangadex.org/")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Page ${idx + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Surface(
                                    color = Slate900.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1} / ${pageUrls.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) { pageIdx ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                zoomScale = if (zoomScale > 1.05f) 1f else 1.85f
                                            }
                                        )
                                    }
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale
                                    )
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(pageUrls[pageIdx])
                                        .addHeader("Referer", "https://mangadex.org/")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Page ${pageIdx + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Bottom Page Scrubber Bar
                        Surface(
                            color = Slate900,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (pagerState.currentPage > 0) {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                        }
                                    },
                                    enabled = pagerState.currentPage > 0
                                ) {
                                    Text("Prev Page")
                                }
                                Text(
                                    text = "Page ${pagerState.currentPage + 1} of ${pageUrls.size}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Amber400,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (pagerState.currentPage < pageUrls.size - 1) {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        }
                                    },
                                    enabled = pagerState.currentPage < pageUrls.size - 1
                                ) {
                                    Text("Next Page")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
