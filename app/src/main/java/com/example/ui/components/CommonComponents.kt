package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccessibilitySettings
import com.example.data.AnnotationEntity
import com.example.data.BookmarkEntity
import com.example.data.DocFormat
import com.example.data.OmniDocRepository
import com.example.data.ReadingColorMode
import com.example.engine.TtsPlaybackState
import com.example.ui.theme.Amber400
import com.example.ui.theme.BlueLightOverlayTint
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlin.math.roundToInt

@Composable
fun FormatBadge(
    format: DocFormat,
    modifier: Modifier = Modifier
) {
    Surface(
        color = format.badgeColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(
            width = 1.dp,
            color = format.badgeColor.copy(alpha = 0.55f),
            shape = RoundedCornerShape(6.dp)
        )
    ) {
        Text(
            text = format.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = format.badgeColor
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun EditorialHeroBanner(
    totalDocs: Int,
    totalComics: Int,
    totalBytes: Long,
    onOpenDeviceFile: () -> Unit,
    onCreateNewDoc: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Custom Atmospheric Editorial Studio Canvas Illustration
            Canvas(
                modifier = Modifier
                    .matchParentSize()
            ) {
                val w = size.width
                val h = size.height

                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF082F49)
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                )

                // Decorative glowing grid lines on right side
                val gridColor = Color(0xFF38BDF8).copy(alpha = 0.08f)
                val step = 36.dp.toPx()
                var x = w * 0.45f
                while (x < w) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                    x += step
                }

                // Glowing layered floating document & comic cards on the right side
                val cardW = 88.dp.toPx()
                val cardH = 114.dp.toPx()
                val baseX = w - cardW - 24.dp.toPx()
                val baseY = 22.dp.toPx()

                // Back cyan comic page
                drawRoundRect(
                    color = Color(0xFF06B6D4).copy(alpha = 0.25f),
                    topLeft = Offset(baseX - 28.dp.toPx(), baseY + 12.dp.toPx()),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                // Middle amber document prism
                drawRoundRect(
                    color = Color(0xFFF59E0B).copy(alpha = 0.30f),
                    topLeft = Offset(baseX - 12.dp.toPx(), baseY + 6.dp.toPx()),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                // Foreground crisp page outline
                drawRoundRect(
                    color = Color(0xFFF8FAFC).copy(alpha = 0.12f),
                    topLeft = Offset(baseX, baseY),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFFBBF24).copy(alpha = 0.65f),
                    topLeft = Offset(baseX, baseY),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Amber400.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "100% OFFLINE STUDIO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = Cyan400.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "$totalDocs Files • $totalComics Comics • ${OmniDocRepository.formatFileSize(totalBytes)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Cyan400),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Universal Document & Comic Vault",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Native offline engines for PDF, DOCX, XLSX, PPTX, TXT, CBZ & CBR with Read-Aloud TTS and visual comfort filters.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth(0.84f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenDeviceFile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber400,
                            contentColor = Slate950
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_device_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open File",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Device File",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = onCreateNewDoc,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.testTag("create_offline_doc_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New Document",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "New Note / PDF")
                    }
                }
            }
        }
    }
}

/**
 * Interactive Draggable Reading Focus Ruler Overlay for Dyslexia & Visual Tracking Accessibility.
 */
@Composable
fun ReadingRulerOverlay(
    visible: Boolean,
    rulerFraction: Float,
    onUpdateFraction: (Float) -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
        val rulerHeightDp = 68.dp
        val rulerHeightPx = with(LocalDensity.current) { rulerHeightDp.toPx() }
        val yOffsetPx = (rulerFraction * (totalHeightPx - rulerHeightPx)).roundToInt()

        // Dimmed top mask
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { yOffsetPx.toDp().coerceAtLeast(0.dp) })
                .background(Color.Black.copy(alpha = 0.35f))
        )

        // Dimmed bottom mask
        val bottomTopPx = (yOffsetPx + rulerHeightPx).coerceAtMost(totalHeightPx)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, bottomTopPx.roundToInt()) }
                .height(with(LocalDensity.current) { (totalHeightPx - bottomTopPx).toDp().coerceAtLeast(0.dp) })
                .background(Color.Black.copy(alpha = 0.35f))
        )

        // Draggable Focus Strip
        Box(
            modifier = Modifier
                .offset { IntOffset(0, yOffsetPx) }
                .fillMaxWidth()
                .height(rulerHeightDp)
                .background(Amber400.copy(alpha = 0.14f))
                .border(width = 2.dp, color = Amber400)
                .pointerInput(totalHeightPx) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val newY = (yOffsetPx + dragAmount).coerceIn(0f, totalHeightPx - rulerHeightPx)
                        onUpdateFraction(newY / (totalHeightPx - rulerHeightPx).coerceAtLeast(1f))
                    }
                }
                .testTag("reading_focus_ruler")
        ) {
            // Center baseline indicator
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Cyan400.copy(alpha = 0.45f))
            )

            Surface(
                color = Slate950.copy(alpha = 0.85f),
                shape = RoundedCornerShape(bottomStart = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable { onClose() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DRAG FOCUS RULER",
                        style = MaterialTheme.typography.labelSmall.copy(color = Amber400, fontSize = 9.sp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hide Reading Ruler",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Floating Offline Text-to-Speech (TTS) Read-Aloud Player Bar inside Document Viewers.
 */
@Composable
fun TtsControlBar(
    ttsState: TtsPlaybackState,
    visible: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onStopAndClose: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenTtsStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Surface(
            color = Slate900,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(1.dp, Amber400.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .testTag("tts_control_bar")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Offline TTS Active",
                            tint = Amber400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "TTS AUDIO • ${ttsState.currentSentenceIndex + 1}/${ttsState.totalSentences.coerceAtLeast(1)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Amber400,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = ttsState.activeVoiceLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Cyan400,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (onOpenTtsStudio != null) {
                            Surface(
                                color = Cyan400.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable { onOpenTtsStudio() }
                                    .testTag("open_tts_studio_sheet_button")
                            ) {
                                Text(
                                    text = "HF Models & Transcript",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Cyan400,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onCycleSpeed() }
                        ) {
                            Text(
                                text = String.format("%.2fx", ttsState.speechRate),
                                style = MaterialTheme.typography.labelSmall.copy(color = Amber400),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(onClick = onStopAndClose, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close TTS Player",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (ttsState.currentSentenceText.isNotBlank()) {
                    Text(
                        text = ttsState.currentSentenceText,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTtsStudio?.invoke() }
                            .padding(vertical = 6.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onPrevSentence) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Sentence",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onTogglePlayPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber400,
                            contentColor = Slate950
                        ),
                        shape = CircleShape,
                        modifier = Modifier.testTag("tts_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (ttsState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (ttsState.isPlaying) "Pause" else "Play"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (ttsState.isPlaying) "Pause Audio" else "Play Audio",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onNextSentence) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Sentence",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onStopAndClose) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color(0xFFF87171)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Non-blocking Optical Reading Filter Overlay (2700K Blue Light Filter Shield, Sepia Warmth, High Contrast Frame)
 * applied over any Document or Comic Viewer interface.
 */
@Composable
fun ReadingFilterOverlay(
    settings: AccessibilitySettings,
    modifier: Modifier = Modifier
) {
    val isBlueLightActive = settings.blueLightFilterEnabled ||
        settings.colorMode == ReadingColorMode.BLUE_LIGHT_SHIELD
    val blueLightAlpha = if (isBlueLightActive) {
        settings.blueLightIntensity.coerceIn(0.10f, 0.75f) * 0.52f
    } else {
        0f
    }

    val modeTint = when (settings.colorMode) {
        ReadingColorMode.WARM_SEPIA -> Color(0xFFD97706).copy(alpha = 0.08f)
        ReadingColorMode.E_INK_MONOCHROME -> Color(0xFF64748B).copy(alpha = 0.07f)
        ReadingColorMode.SAGE_COMFORT -> Color(0xFF10B981).copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val isHighContrastBorder = settings.colorMode == ReadingColorMode.HIGH_CONTRAST ||
        settings.colorMode == ReadingColorMode.HIGH_CONTRAST_LIGHT

    if (blueLightAlpha > 0f || modeTint != Color.Transparent || isHighContrastBorder) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .let { base ->
                    if (isHighContrastBorder) {
                        base.border(
                            width = 2.dp,
                            color = if (settings.colorMode == ReadingColorMode.HIGH_CONTRAST) {
                                Color(0xFFFEF08A).copy(alpha = 0.75f)
                            } else {
                                Color.Black.copy(alpha = 0.75f)
                            }
                        )
                    } else {
                        base
                    }
                }
                .testTag("reading_filter_overlay")
        ) {
            if (modeTint != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(modeTint)
                )
            }
            if (blueLightAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlueLightOverlayTint.copy(alpha = blueLightAlpha))
                )
            }
        }
    }
}

/**
 * Interactive Accessibility Reading Filter Strip displayed inside Document Viewers
 * for 1-tap switching between Sepia Mode, High Contrast Mode, Blue Light Filter, and more.
 */
@Composable
fun DocumentViewerFilterBar(
    settings: AccessibilitySettings,
    onSelectColorMode: (ReadingColorMode) -> Unit,
    onToggleBlueLight: () -> Unit,
    onBlueLightIntensityChange: (Float) -> Unit,
    onOpenFullSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBlueLightSlider by remember { mutableStateOf(false) }
    val isBlueLightActive = settings.blueLightFilterEnabled ||
        settings.colorMode == ReadingColorMode.BLUE_LIGHT_SHIELD

    Surface(
        color = Slate900.copy(alpha = 0.96f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_viewer_filter_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = "FILTERS:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Amber400,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                )

                // Dedicated Blue Light Filter Shield Pill
                val blueSelected = isBlueLightActive
                Surface(
                    color = if (blueSelected) Amber400.copy(alpha = 0.24f) else Slate800,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (blueSelected) Amber400 else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable {
                            onToggleBlueLight()
                            showBlueLightSlider = !blueSelected
                        }
                        .testTag("filter_chip_blue_light")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (blueSelected) Amber400 else Color(0xFFF59E0B))
                        )
                        Text(
                            text = if (blueSelected) {
                                "Blue Light (${(settings.blueLightIntensity * 100).roundToInt()}%)"
                            } else {
                                "Blue Light Filter"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (blueSelected) Amber400 else Color.White,
                                fontWeight = if (blueSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }

                // Quick Preset Chips: Sepia Mode, High Contrast, Daylight, AMOLED Night, Stark Contrast, E-Ink
                val quickModes = listOf(
                    ReadingColorMode.WARM_SEPIA,
                    ReadingColorMode.HIGH_CONTRAST,
                    ReadingColorMode.BLUE_LIGHT_SHIELD,
                    ReadingColorMode.DAYLIGHT,
                    ReadingColorMode.AMOLED_DARK,
                    ReadingColorMode.HIGH_CONTRAST_LIGHT,
                    ReadingColorMode.E_INK_MONOCHROME,
                    ReadingColorMode.SAGE_COMFORT
                )

                quickModes.forEach { mode ->
                    val selected = settings.colorMode == mode
                    Surface(
                        color = if (selected) Cyan400.copy(alpha = 0.22f) else Slate800,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (selected) Cyan400 else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelectColorMode(mode) }
                            .testTag("filter_chip_${mode.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(mode.backgroundColor)
                                    .border(0.75.dp, mode.textColor, CircleShape)
                            )
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selected) Cyan400 else Color(0xFFE2E8F0),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // Tune Warmth / Full Sheet Button
                Surface(
                    color = Emerald400.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(1.dp, Emerald400.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .clickable { showBlueLightSlider = !showBlueLightSlider }
                ) {
                    Text(
                        text = if (showBlueLightSlider) "Hide Warmth" else "Warmth %",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Emerald400,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { onOpenFullSheet() }
                ) {
                    Text(
                        text = "More Aa",
                        style = MaterialTheme.typography.labelSmall.copy(color = Amber400),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showBlueLightSlider || isBlueLightActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "2700K Shield: ${(settings.blueLightIntensity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Amber400,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Slider(
                        value = settings.blueLightIntensity,
                        onValueChange = onBlueLightIntensityChange,
                        valueRange = 0.10f..0.75f,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )
                    Text(
                        text = settings.colorMode.badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Cyan400,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessibilitySheet(
    settings: AccessibilitySettings,
    onDismiss: () -> Unit,
    onSelectColorMode: (ReadingColorMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onToggleDyslexia: () -> Unit,
    onToggleRuler: () -> Unit,
    onToggleInvertPdf: () -> Unit,
    onToggleComicWarmth: () -> Unit,
    onToggleBlueLight: () -> Unit = {},
    onBlueLightIntensityChange: (Float) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Amber400
                )
                Text(
                    text = "Reading Filters & Accessibility",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "READING FILTER MODES (SEPIA • HIGH CONTRAST • BLUE LIGHT • NIGHT)",
                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                ReadingColorMode.entries.forEach { mode ->
                    val isSelected = settings.colorMode == mode
                    Surface(
                        color = mode.backgroundColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(138.dp)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Amber400 else Color.Gray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectColorMode(mode) }
                            .padding(10.dp)
                    ) {
                        Column(modifier = Modifier.background(mode.backgroundColor)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Aa",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = mode.textColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = mode.badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = mode.accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = mode.textColor,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = mode.textColor.copy(alpha = 0.75f),
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated Blue Light Filter Shield Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Blue Light Filter Shield (2700K Warmth)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Amber400,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Applies an optical warm amber filter across all document & comic pages",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                            )
                        }
                        Switch(
                            checked = settings.blueLightFilterEnabled || settings.colorMode == ReadingColorMode.BLUE_LIGHT_SHIELD,
                            onCheckedChange = { onToggleBlueLight() }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SHIELD INTENSITY (${(settings.blueLightIntensity * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                    )
                    Slider(
                        value = settings.blueLightIntensity,
                        onValueChange = onBlueLightIntensityChange,
                        valueRange = 0.10f..0.75f
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "FONT SCALE (${(settings.fontScale * 100).roundToInt()}%)",
                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
            )
            Slider(
                value = settings.fontScale,
                onValueChange = onFontScaleChange,
                valueRange = 0.75f..1.75f
            )

            Text(
                text = "LINE HEIGHT (${String.format("%.2fx", settings.lineSpacingMultiplier)})",
                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
            )
            Slider(
                value = settings.lineSpacingMultiplier,
                onValueChange = onLineSpacingChange,
                valueRange = 1.15f..2.1f
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reading Focus Ruler Guide",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Draggable horizontal highlight bar to prevent line skipping",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                }
                Switch(
                    checked = settings.showReadingRuler,
                    onCheckedChange = { onToggleRuler() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dyslexia-Friendly Character Spacing",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Expanded letter tracking & paragraph separation",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                }
                Switch(
                    checked = settings.dyslexiaFriendlySpacing,
                    onCheckedChange = { onToggleDyslexia() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = settings.colorMode == ReadingColorMode.WARM_SEPIA,
                    onClick = { onSelectColorMode(ReadingColorMode.WARM_SEPIA) },
                    label = { Text("Sepia Mode") }
                )
                FilterChip(
                    selected = settings.colorMode == ReadingColorMode.HIGH_CONTRAST,
                    onClick = { onSelectColorMode(ReadingColorMode.HIGH_CONTRAST) },
                    label = { Text("High Contrast") }
                )
                FilterChip(
                    selected = settings.invertPdfColors,
                    onClick = onToggleInvertPdf,
                    label = { Text("PDF Night Inversion") }
                )
                FilterChip(
                    selected = settings.comicWarmFilter,
                    onClick = onToggleComicWarmth,
                    label = { Text("Comic Eye-Care Tint") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksAndNotesSheet(
    documentTitle: String,
    currentUnitIndex: Int,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    onDismiss: () -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onAddAnnotation: (String, String) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onJumpToUnit: (Int) -> Unit
) {
    var bookmarkLabel by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#F59E0B") }
    val highlightColors = listOf("#F59E0B", "#06B6D4", "#10B981", "#EC4899", "#8B5CF6")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        contentColor = Color.White
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    text = "Bookmarks & Study Notes",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                Text(
                    text = "$documentTitle • Active Section #${currentUnitIndex + 1}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Cyan400)
                )
            }

            // Quick Add Bookmark
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "BOOKMARK CURRENT PAGE / SECTION (#${currentUnitIndex + 1})",
                            style = MaterialTheme.typography.labelSmall.copy(color = Amber400)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = bookmarkLabel,
                                onValueChange = { bookmarkLabel = it },
                                placeholder = { Text("Bookmark title (optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onAddBookmark(bookmarkLabel)
                                    bookmarkLabel = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Amber400,
                                    contentColor = Slate950
                                )
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // Quick Add Study Note / Annotation
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ADD STUDY ANNOTATION FOR SECTION #${currentUnitIndex + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            placeholder = { Text("Write an offline study note, summary, or action item…") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                highlightColors.forEach { hex ->
                                    val c = Color(android.graphics.Color.parseColor(hex))
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                width = if (selectedColorHex == hex) 2.5.dp else 0.dp,
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColorHex = hex }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (noteInput.isNotBlank()) {
                                        onAddAnnotation(noteInput, selectedColorHex)
                                        noteInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Cyan400,
                                    contentColor = Slate950
                                )
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Note")
                            }
                        }
                    }
                }
            }

            if (bookmarks.isNotEmpty()) {
                item {
                    Text(
                        text = "SAVED BOOKMARKS (${bookmarks.size})",
                        style = MaterialTheme.typography.labelSmall.copy(color = Amber400)
                    )
                }
                items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onJumpToUnit(bm.unitIndex)
                                onDismiss()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${bm.label} • #${bm.unitIndex + 1}",
                                    style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
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

            if (annotations.isNotEmpty()) {
                item {
                    Text(
                        text = "STUDY NOTES (${annotations.size})",
                        style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                    )
                }
                items(annotations, key = { "an_${it.id}" }) { note ->
                    val tagColor = runCatching {
                        Color(android.graphics.Color.parseColor(note.highlightColorHex))
                    }.getOrDefault(Amber400)

                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tagColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                onJumpToUnit(note.unitIndex)
                                onDismiss()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Section #${note.unitIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = tagColor)
                                )
                                Text(
                                    text = note.noteText,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )
                            }
                            IconButton(onClick = { onDeleteAnnotation(note.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Note",
                                    tint = Color(0xFFF87171)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

/**
 * Represents a matched search location inside a document viewer (DOCX block, TXT line, XLSX row/cell, PPTX slide, or PDF page).
 */
data class DocumentSearchHit(
    val unitIndex: Int,
    val subIndex: Int = 0,
    val locationBadge: String,
    val snippetText: String,
    val matchCountInUnit: Int = 1
)

/**
 * Finds all character index ranges where [query] occurs inside [text], supporting
 * case-sensitivity and whole-word matching.
 */
fun findQueryRanges(
    text: String,
    query: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false
): List<IntRange> {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty() || text.isEmpty()) return emptyList()
    return runCatching {
        val escaped = Regex.escape(cleanQuery)
        val pattern = if (wholeWord) "\\b$escaped\\b" else escaped
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val regex = Regex(pattern, options)
        regex.findAll(text).map { it.range }.toList()
    }.getOrDefault(emptyList())
}

/**
 * Counts how many times [query] appears in [text].
 */
fun countQueryMatches(
    text: String,
    query: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false
): Int = findQueryRanges(text, query, caseSensitive, wholeWord).size

/**
 * Builds a Jetpack Compose [AnnotatedString] that highlights all occurrences of [query]
 * using [SpanStyle] (background highlight, high-contrast ink color, bold weight, and
 * active-match underline styling when [isFocusedTarget] is true).
 */
fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false,
    isFocusedTarget: Boolean = false,
    highlightBackground: Color = Color(0xFFFACC15),
    highlightTextColor: Color = Color(0xFF0F172A),
    activeHighlightBackground: Color = Color(0xFF38BDF8),
    activeHighlightTextColor: Color = Color(0xFF020617)
): AnnotatedString {
    val ranges = findQueryRanges(text, query, caseSensitive, wholeWord)
    if (ranges.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        ranges.forEachIndexed { idx, range ->
            val isPrimaryFocus = isFocusedTarget && idx == 0
            addStyle(
                style = SpanStyle(
                    background = if (isPrimaryFocus) activeHighlightBackground else highlightBackground,
                    color = if (isPrimaryFocus) activeHighlightTextColor else highlightTextColor,
                    fontWeight = FontWeight.ExtraBold,
                    textDecoration = if (isPrimaryFocus) TextDecoration.Underline else TextDecoration.None
                ),
                start = range.first,
                end = (range.last + 1).coerceAtMost(text.length)
            )
        }
    }
}

/**
 * Interactive In-Viewer Document Search Bar with Compose Text Styling Highlights,
 * Match Counter, Prev/Next Match Navigation, Case/Whole-Word Toggles, and Jump-to-Match Results Drawer.
 */
@Composable
fun DocumentInViewerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hits: List<DocumentSearchHit>,
    totalOccurrences: Int,
    activeHitIndex: Int,
    caseSensitive: Boolean,
    onToggleCaseSensitive: () -> Unit,
    wholeWord: Boolean,
    onToggleWholeWord: () -> Unit,
    filterOnlyMatches: Boolean = false,
    onToggleFilterOnlyMatches: (() -> Unit)? = null,
    onSelectHitIndex: (Int) -> Unit,
    onClose: () -> Unit,
    placeholderText: String = "Find text in document…",
    modifier: Modifier = Modifier
) {
    var showResultsDrawer by remember { mutableStateOf(false) }
    val safeHitCount = hits.size
    val safeActiveHit = if (safeHitCount > 0) activeHitIndex.coerceIn(0, safeHitCount - 1) else 0

    Surface(
        color = Slate900,
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Cyan400.copy(alpha = 0.35f))
            .testTag("document_in_viewer_search_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top Row: Search Input + Prev / Next Match Navigation + Close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            tint = Amber400,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("viewer_search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search Query",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("viewer_search_input")
                )

                // Previous Match Button
                IconButton(
                    onClick = {
                        if (safeHitCount > 0) {
                            val prev = if (safeActiveHit - 1 < 0) safeHitCount - 1 else safeActiveHit - 1
                            onSelectHitIndex(prev)
                        }
                    },
                    enabled = safeHitCount > 0,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Slate800, RoundedCornerShape(10.dp))
                        .testTag("viewer_search_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous Match",
                        tint = if (safeHitCount > 0) Cyan400 else Color.Gray
                    )
                }

                // Next Match Button
                IconButton(
                    onClick = {
                        if (safeHitCount > 0) {
                            val next = (safeActiveHit + 1) % safeHitCount
                            onSelectHitIndex(next)
                        }
                    },
                    enabled = safeHitCount > 0,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Slate800, RoundedCornerShape(10.dp))
                        .testTag("viewer_search_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next Match",
                        tint = if (safeHitCount > 0) Amber400 else Color.Gray
                    )
                }

                // Close Search Bar Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("viewer_search_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Search",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Second Row: Match Telemetry Badge + Case Sensitive / Whole Word / Filter / Results List Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                // Live Match Count Pill
                val statusText = when {
                    query.isBlank() -> "Type to highlight matches"
                    safeHitCount == 0 -> "0 matches found"
                    else -> "Match ${safeActiveHit + 1} of $safeHitCount ($totalOccurrences total)"
                }
                val statusColor = when {
                    query.isBlank() -> Color(0xFF94A3B8)
                    safeHitCount == 0 -> Color(0xFFF87171)
                    else -> Amber400
                }

                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .testTag("viewer_search_match_counter")
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Case-Sensitive Toggle Pill
                Surface(
                    color = if (caseSensitive) Cyan400.copy(alpha = 0.22f) else Slate800,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (caseSensitive) Cyan400 else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onToggleCaseSensitive() }
                        .testTag("viewer_search_case_toggle")
                ) {
                    Text(
                        text = "Aa Case",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (caseSensitive) Cyan400 else Color.LightGray,
                            fontWeight = if (caseSensitive) FontWeight.Bold else FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Whole-Word Toggle Pill
                Surface(
                    color = if (wholeWord) Cyan400.copy(alpha = 0.22f) else Slate800,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (wholeWord) Cyan400 else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onToggleWholeWord() }
                        .testTag("viewer_search_word_toggle")
                ) {
                    Text(
                        text = "[w] Whole Word",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (wholeWord) Cyan400 else Color.LightGray,
                            fontWeight = if (wholeWord) FontWeight.Bold else FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Optional Filter-Only Matching Sections Toggle
                if (onToggleFilterOnlyMatches != null) {
                    Surface(
                        color = if (filterOnlyMatches) Emerald400.copy(alpha = 0.22f) else Slate800,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (filterOnlyMatches) Emerald400 else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onToggleFilterOnlyMatches() }
                            .testTag("viewer_search_filter_only_toggle")
                    ) {
                        Text(
                            text = if (filterOnlyMatches) "Showing Matches Only" else "Show Context",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (filterOnlyMatches) Emerald400 else Color.LightGray,
                                fontWeight = if (filterOnlyMatches) FontWeight.Bold else FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Expandable Match Snippets List Toggle
                if (safeHitCount > 0) {
                    Surface(
                        color = if (showResultsDrawer) Amber400.copy(alpha = 0.24f) else Slate800,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (showResultsDrawer) Amber400 else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { showResultsDrawer = !showResultsDrawer }
                            .testTag("viewer_search_results_drawer_toggle")
                    ) {
                        Text(
                            text = if (showResultsDrawer) "Hide Match List" else "Match List ($safeHitCount)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (showResultsDrawer) Amber400 else Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Expandable Search Match Snippets Drawer with Compose Highlighted Previews
            AnimatedVisibility(visible = showResultsDrawer && safeHitCount > 0 && query.isNotBlank()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .padding(top = 8.dp)
                        .testTag("viewer_search_results_list")
                ) {
                    items(hits.size) { idx ->
                        val hit = hits[idx]
                        val isFocused = idx == safeActiveHit
                        Surface(
                            color = if (isFocused) Cyan400.copy(alpha = 0.16f) else Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isFocused) 1.5.dp else 1.dp,
                                    color = if (isFocused) Cyan400 else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectHitIndex(idx) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = hit.locationBadge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isFocused) Cyan400 else Amber400,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = JetBrainsMonoFontFamily
                                            )
                                        )
                                        if (hit.matchCountInUnit > 1) {
                                            Text(
                                                text = "• ${hit.matchCountInUnit}x in section",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = buildHighlightedAnnotatedString(
                                            text = hit.snippetText,
                                            query = query,
                                            caseSensitive = caseSensitive,
                                            wholeWord = wholeWord,
                                            isFocusedTarget = isFocused
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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

