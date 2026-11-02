package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OmniDocRepository
import com.example.engine.DownloadedModelStatus
import com.example.engine.HuggingFaceTtsModelInfo
import com.example.engine.TtsPlaybackState
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlin.math.roundToInt

@Composable
fun HuggingFaceModelCard(
    model: HuggingFaceTtsModelInfo,
    status: DownloadedModelStatus,
    isActiveModel: Boolean,
    onDownloadFullOnnx: () -> Unit,
    onDownloadQuickProfile: () -> Unit,
    onCancelDownload: () -> Unit,
    onActivateModel: () -> Unit,
    onTestModelVoice: () -> Unit,
    onDeleteModel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActiveModel) 2.dp else 1.dp,
                color = if (isActiveModel) Amber400 else Cyan400.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("hf_model_card_${model.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (isActiveModel) Amber400.copy(alpha = 0.2f) else Cyan400.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isActiveModel) "ACTIVE VOICE ENGINE" else model.mobileBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isActiveModel) Amber400 else Cyan400,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${model.estimatedSizeLabel} • ${model.languageTag}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF94A3B8),
                        fontFamily = JetBrainsMonoFontFamily
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = model.displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )

            Text(
                text = "huggingface.co/${model.repoId} • ${model.architecture}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Cyan400,
                    fontFamily = JetBrainsMonoFontFamily
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                status.isDownloading -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Downloading from Hugging Face… ${(status.progressFraction * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(color = Amber400)
                            )
                            Text(
                                text = "${OmniDocRepository.formatFileSize(status.downloadedBytes)} / ${OmniDocRepository.formatFileSize(status.totalBytes)} (${status.speedKbps} KB/s)",
                                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { status.progressFraction.coerceIn(0f, 1f) },
                            color = Amber400,
                            trackColor = Slate900,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", color = Color(0xFFF87171))
                            }
                        }
                    }
                }
                status.isDownloaded -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = Color(0xFF064E3B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = status.localConfigSummary.ifBlank { "Offline Model Ready" },
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onActivateModel,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActiveModel) Color(0xFF10B981) else Amber400,
                                    contentColor = Slate950
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("activate_hf_model_${model.id}")
                            ) {
                                Icon(
                                    imageVector = if (isActiveModel) Icons.Default.CheckCircle else Icons.Default.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isActiveModel) "Active Model" else "Use Model",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onTestModelVoice,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("test_hf_model_${model.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sample", color = Color.White)
                            }

                            if (!status.hasFullWeights) {
                                IconButton(onClick = onDownloadFullOnnx) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Download Full ONNX Weights",
                                        tint = Cyan400
                                    )
                                }
                            }

                            IconButton(onClick = onDeleteModel) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Downloaded Model",
                                    tint = Color(0xFFF87171)
                                )
                            }
                        }
                    }
                }
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onDownloadFullOnnx,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cyan400,
                                contentColor = Slate950
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_full_hf_${model.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download ONNX",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Slate950,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = onDownloadQuickProfile,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("download_quick_hf_${model.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Quick Setup",
                                style = MaterialTheme.typography.labelMedium.copy(color = Amber400)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Modal Bottom Sheet inside Document Viewers for Sentence-by-Sentence Transcript,
 * Hugging Face Model Switching, and Offline WAV Audio File Synthesis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsInteractiveModalSheet(
    documentTitle: String,
    ttsState: TtsPlaybackState,
    curatedModels: List<HuggingFaceTtsModelInfo>,
    modelStatuses: Map<String, DownloadedModelStatus>,
    onDismiss: () -> Unit,
    onJumpToSentence: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectHfModel: (HuggingFaceTtsModelInfo) -> Unit,
    onQuickDownloadAndSelectHfModel: (HuggingFaceTtsModelInfo) -> Unit,
    onSynthesizeToWav: () -> Unit
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
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Document Audio Studio (TTS)",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Text(
                        text = "${ttsState.activeVoiceLabel} • ${ttsState.totalSentences} Sentences",
                        style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
                    )
                }

                Button(
                    onClick = onSynthesizeToWav,
                    enabled = !ttsState.isSynthesizingFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan400,
                        contentColor = Slate950
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("export_wav_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (ttsState.isSynthesizingFile) "Rendering…" else "Export .WAV",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Slate950,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Hugging Face Mobile Model Switcher Bar
            Text(
                text = "HUGGING FACE MOBILE VOICE MODELS",
                style = MaterialTheme.typography.labelSmall.copy(color = Amber400)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                curatedModels.forEach { model ->
                    val status = modelStatuses[model.id]
                    val isDownloaded = status?.isDownloaded == true
                    val isActive = ttsState.activeHfModelId == model.id
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            if (isDownloaded) {
                                onSelectHfModel(model)
                            } else {
                                onQuickDownloadAndSelectHfModel(model)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (isActive) Amber400 else Cyan400,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = model.displayName.substringBefore(" ("),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "INTERACTIVE SENTENCE TRANSCRIPT (TAP ANY LINE TO SPEAK)",
                style = MaterialTheme.typography.labelSmall.copy(color = Cyan400)
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                itemsIndexed(ttsState.sentences) { index, sentence ->
                    val isCurrent = index == ttsState.currentSentenceIndex
                    Surface(
                        color = if (isCurrent) Amber400.copy(alpha = 0.18f) else Slate800,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isCurrent) 1.5.dp else 0.dp,
                                color = if (isCurrent) Amber400 else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onJumpToSentence(index) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Surface(
                                color = if (isCurrent) Amber400 else Slate900,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isCurrent) Slate950 else Cyan400,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = sentence,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isCurrent) Color.White else Color(0xFFCBD5E1),
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
