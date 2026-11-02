package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UBlockActionType
import com.example.engine.UBlockOriginState
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val UboCrimson = Color(0xFF800000)
private val UboBrightRed = Color(0xFFDC2626)

/**
 * Custom vector octagon shield emblem inspired by uBlock Origin.
 */
@Composable
fun UBlockOriginLogoBadge(
    isEnabled: Boolean,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cut = w * 0.28f
            val octagon = Path().apply {
                moveTo(cut, 0f)
                lineTo(w - cut, 0f)
                lineTo(w, cut)
                lineTo(w, h - cut)
                lineTo(w - cut, h)
                lineTo(cut, h)
                lineTo(0f, h - cut)
                lineTo(0f, cut)
                close()
            }
            drawPath(
                path = octagon,
                color = if (isEnabled) UboBrightRed else Color(0xFF475569),
                style = Fill
            )
            drawPath(
                path = octagon,
                color = if (isEnabled) Color(0xFFFCA5A5) else Color(0xFF94A3B8),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        Text(
            text = "uBO",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 7.sp,
                fontFamily = JetBrainsMonoFontFamily
            )
        )
    }
}

/**
 * Compact uBlock Origin Shield Pill for In-App Browser and Inbuilt Document/Comic Reader Top Bars.
 */
@Composable
fun UBlockOriginShieldPill(
    uboState: UBlockOriginState,
    badgeCountOverride: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = uboState.isEnabled
    val count = badgeCountOverride ?: uboState.totalBlockedRequests

    Surface(
        color = if (active) UboBrightRed.copy(alpha = 0.18f) else Slate800,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (active) UboBrightRed.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .testTag("ublock_origin_shield_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            UBlockOriginLogoBadge(
                isEnabled = active,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (active) {
                    if (count > 0) "uBO ($count)" else "uBO ON"
                } else {
                    "uBO OFF"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (active) Color(0xFFFCA5A5) else Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily
                )
            )
        }
    }
}

/**
 * Full-Featured uBlock Origin Control Panel & Dashboard Sheet for both the In-App Browser
 * and Inbuilt Document & Comic Readers (PDF, DOCX, XLSX, PPTX, TXT/MD, CBZ, CBR, Online Manga).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UBlockOriginDashboardSheet(
    uboState: UBlockOriginState,
    currentContextTitle: String,
    currentHostOrDocName: String,
    isBrowserContext: Boolean,
    onToggleGlobalPower: () -> Unit,
    onToggleWhitelistCurrentHost: () -> Unit,
    onToggleElementZapper: () -> Unit,
    onToggleBlockPopups: () -> Unit,
    onToggleCosmeticFiltering: () -> Unit,
    onToggleStrictThirdParty: () -> Unit,
    onToggleReaderTrackerSanitizer: () -> Unit,
    onToggleComicPromoFilter: () -> Unit,
    onToggleFilterList: (String) -> Unit,
    onSyncFilterListsNow: () -> Unit,
    onAddCustomRule: (String) -> Unit,
    onRemoveCustomRule: (String) -> Unit,
    onClearLogger: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Shield & Switches, 1 = Filter Lists (uAssets), 2 = Live Logger, 3 = My Filters
    var customRuleInput by remember { mutableStateOf("") }

    val isHostWhitelisted = uboState.whitelistedHosts.any {
        currentHostOrDocName.lowercase().contains(it)
    }
    val effectiveActive = uboState.isEnabled && !isHostWhitelisted

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Header Row: uBlock Origin Branding + Power Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UBlockOriginLogoBadge(
                        isEnabled = effectiveActive,
                        modifier = Modifier.size(34.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "uBlock Origin",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            Surface(
                                color = if (effectiveActive) Emerald400.copy(alpha = 0.2f) else Slate800,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (effectiveActive) "ACTIVE SHIELD" else "BYPASSED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (effectiveActive) Emerald400 else Color.LightGray,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "$currentContextTitle • $currentHostOrDocName",
                            style = MaterialTheme.typography.labelSmall.copy(color = Cyan400),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Classic uBlock Origin Big Power Button
                IconButton(
                    onClick = onToggleGlobalPower,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (uboState.isEnabled) UboBrightRed else Slate800)
                        .border(
                            width = 2.dp,
                            color = if (uboState.isEnabled) Color(0xFFFCA5A5) else Color.Gray,
                            shape = CircleShape
                        )
                        .testTag("ubo_power_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle uBlock Origin Power",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry Summary Bar
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Slate950),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    UboStatColumn(
                        value = "${uboState.totalBlockedRequests}",
                        label = "Blocked Total",
                        color = Color(0xFFF87171)
                    )
                    UboStatColumn(
                        value = "${uboState.totalReaderItemsSanitized}",
                        label = "Reader Sanitized",
                        color = Amber400
                    )
                    UboStatColumn(
                        value = "${uboState.compiledNetworkDomainsCount}",
                        label = "Network Rules",
                        color = Cyan400
                    )
                    UboStatColumn(
                        value = "${uboState.compiledCosmeticSelectorsCount}",
                        label = "DOM ## Rules",
                        color = Emerald400
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Selector Strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                val tabs = listOf(
                    0 to "Shield & Reader Controls",
                    1 to "Filter Lists (${uboState.filterLists.count { it.isEnabled }}/${uboState.filterLists.size})",
                    2 to "Live Logger (${uboState.recentLogs.size})",
                    3 to "My Filters (${uboState.customUserFilters.size})"
                )
                tabs.forEach { (idx, label) ->
                    FilterChip(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        modifier = Modifier.testTag("ubo_tab_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        // Browser Element Zapper & Per-Site Whitelist Row
                        if (isBrowserContext) {
                            item {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            onToggleElementZapper()
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (uboState.elementZapperModeActive) UboBrightRed else Amber400,
                                            contentColor = if (uboState.elementZapperModeActive) Color.White else Slate950
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ubo_element_zapper_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (uboState.elementZapperModeActive) "Exit Zapper Mode" else "Enter Element Zapper",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onToggleWhitelistCurrentHost,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (isHostWhitelisted) "Re-Enable on Site" else "Whitelist Site",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = if (isHostWhitelisted) Emerald400 else Cyan400,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Switch 1: Inbuilt Document Reader Tracker & UTM Sanitizer
                        item {
                            UboToggleRowCard(
                                title = "Inbuilt Reader Link & Tracker Sanitizer (\$removeparam)",
                                subtitle = "Strips UTM tracking parameters, click-IDs (fbclid, gclid), and neutralizes ad/badware URLs inside PDF, DOCX, XLSX, PPTX & TXT readers.",
                                checked = uboState.readerTrackerSanitizerEnabled,
                                badge = "READER",
                                onToggle = onToggleReaderTrackerSanitizer
                            )
                        }

                        // Switch 2: Comic & Manga Promo / Ad Banner Filter
                        item {
                            UboToggleRowCard(
                                title = "Comic & Manga Promo-Ad Page Filter",
                                subtitle = "Automatically filters out gambling banners, pop-under ad overlays, and aggregator promo pages in .CBZ, .CBR, and Online Manga chapters.",
                                checked = uboState.comicPromoPageFilterEnabled,
                                badge = "COMICS",
                                onToggle = onToggleComicPromoFilter
                            )
                        }

                        // Switch 3: Cosmetic DOM Filtering (##selectors)
                        item {
                            UboToggleRowCard(
                                title = "uBlock Cosmetic DOM Filtering (##selectors)",
                                subtitle = "Injects uBlock Origin stylesheet rules and MutationObserver to collapse ad placeholders, sticky banners, and cookie/age walls.",
                                checked = uboState.cosmeticFilteringEnabled,
                                badge = "DOM ##",
                                onToggle = onToggleCosmeticFiltering
                            )
                        }

                        // Switch 4: Popup, Tab-Under & Redirect Defuser (+js)
                        item {
                            UboToggleRowCard(
                                title = "Popup & Scriptlet Defuser (+js(no-window-open-if))",
                                subtitle = "Blocks window.open pop-unders, non-HTTP intent:// redirects, navigator.sendBeacon pings, and anti-adblock property checks.",
                                checked = uboState.blockPopupsAndRedirects,
                                badge = "+js()",
                                onToggle = onToggleBlockPopups
                            )
                        }

                        // Switch 5: Strict 3rd-Party Script & Frame Blocking (uBO Medium Mode)
                        item {
                            UboToggleRowCard(
                                title = "Strict 3rd-Party Script & Frame Blocking (*\$3p,script)",
                                subtitle = "uBlock Origin dynamic filtering mode: blocks unverified third-party scripts and iframes while preserving comic image CDNs.",
                                checked = uboState.strictThirdPartyBlocking,
                                badge = "3P STRICT",
                                onToggle = onToggleStrictThirdParty
                            )
                        }
                    }
                }

                1 -> {
                    // Tab 1: Official uBlock Origin Filter Lists (uAssets)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uboState.lastUpdateSummary,
                                    style = MaterialTheme.typography.labelSmall.copy(color = Cyan400),
                                    maxLines = 2
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onSyncFilterListsNow,
                                enabled = !uboState.isUpdatingLists,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Amber400,
                                    contentColor = Slate950
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("ubo_update_lists_button")
                            ) {
                                if (uboState.isUpdatingLists) {
                                    CircularProgressIndicator(
                                        color = Slate950,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uboState.isUpdatingLists) "Updating…" else "Update uAssets",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uboState.filterLists, key = { it.spec.id }) { status ->
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
                                            .padding(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    color = Amber400.copy(alpha = 0.18f),
                                                    shape = RoundedCornerShape(5.dp)
                                                ) {
                                                    Text(
                                                        text = status.spec.category,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Amber400,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = status.spec.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = status.spec.description,
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${status.activeRulesCount} active rules • ${status.syncSourceLabel}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Emerald400,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                        Switch(
                                            checked = status.isEnabled,
                                            onCheckedChange = { onToggleFilterList(status.spec.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Tab 2: Live uBlock Origin Logger
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "REAL-TIME uBLOCK ORIGIN LOGGER (${uboState.recentLogs.size} EVENTS)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Amber400,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            )
                            if (uboState.recentLogs.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onClearLogger,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Clear Log", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (uboState.recentLogs.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No ad requests or reader trackers detected yet.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                    )
                                    Text(
                                        text = "uBlock Origin is actively monitoring all browser and reader activity.",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                                    )
                                }
                            }
                        } else {
                            val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(uboState.recentLogs, key = { it.id }) { log ->
                                    val badgeColor = when (log.actionType) {
                                        UBlockActionType.NETWORK_BLOCK -> Color(0xFFF87171)
                                        UBlockActionType.POPUP_BLOCK -> Amber400
                                        UBlockActionType.COSMETIC_HIDE -> Cyan400
                                        UBlockActionType.SCRIPTLET_INJECT -> Color(0xFFC084FC)
                                        UBlockActionType.READER_SANITIZE -> Emerald400
                                        UBlockActionType.ELEMENT_ZAP -> UboBrightRed
                                    }
                                    Surface(
                                        color = Slate950,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                badgeColor.copy(alpha = 0.35f),
                                                RoundedCornerShape(10.dp)
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        color = badgeColor.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = log.actionType.badge,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = badgeColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = JetBrainsMonoFontFamily,
                                                                fontSize = 9.sp
                                                            ),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = log.matchedRule,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Amber400,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = JetBrainsMonoFontFamily
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = timeFormatter.format(Date(log.timestampMs)),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color.Gray,
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = log.targetUrlOrElement,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${log.sourceContext} • List: ${log.filterListId}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Tab 3: Custom "My Filters" Editor (||domain^ or ##selector)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        Text(
                            text = "ADD CUSTOM uBLOCK ORIGIN FILTER RULE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Amber400,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customRuleInput,
                                onValueChange = { customRuleInput = it },
                                placeholder = {
                                    Text(
                                        text = "e.g. ||ads.example.com^ or ##.annoying-banner",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ubo_custom_rule_input")
                            )
                            Button(
                                onClick = {
                                    if (customRuleInput.isNotBlank()) {
                                        onAddCustomRule(customRuleInput)
                                        customRuleInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald400,
                                    contentColor = Slate950
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("ubo_add_custom_rule_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uboState.customUserFilters, key = { it }) { rule ->
                                Surface(
                                    color = Slate950,
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
                                        Text(
                                            text = rule,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Cyan400,
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onRemoveCustomRule(rule) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Rule",
                                                tint = Color(0xFFF87171),
                                                modifier = Modifier.size(16.dp)
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
}

@Composable
private fun UboStatColumn(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                color = color,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = JetBrainsMonoFontFamily
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun UboToggleRowCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    badge: String,
    onToggle: () -> Unit
) {
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
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = UboBrightRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFCA5A5),
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
