package com.example.engine

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

data class HuggingFaceTtsModelInfo(
    val id: String,
    val displayName: String,
    val repoId: String,
    val architecture: String,
    val modelDownloadUrl: String,
    val configDownloadUrl: String,
    val estimatedSizeLabel: String,
    val estimatedBytes: Long,
    val languageTag: String,
    val defaultLocale: Locale,
    val recommendedRateMultiplier: Float,
    val recommendedPitchMultiplier: Float,
    val mobileBadge: String,
    val description: String,
    val downloadsCount: Int = 0,
    val likesCount: Int = 0
)

data class DownloadedModelStatus(
    val modelId: String,
    val isDownloaded: Boolean = false,
    val hasFullWeights: Boolean = false,
    val isDownloading: Boolean = false,
    val progressFraction: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedKbps: Int = 0,
    val localConfigSummary: String = "",
    val localFolderPath: String = "",
    val errorMessage: String? = null
)

class HuggingFaceTtsModelManager(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val modelsRootDir = File(context.filesDir, "hf_tts_models").apply { mkdirs() }

    val curatedMobileModels: List<HuggingFaceTtsModelInfo> = listOf(
        HuggingFaceTtsModelInfo(
            id = "piper_en_us_amy_low",
            displayName = "Piper VITS — Amy (US Mobile Fast)",
            repoId = "rhasspy/piper-voices",
            architecture = "VITS / Piper ONNX (16kHz INT8)",
            modelDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/low/en_US-amy-low.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/low/en_US-amy-low.onnx.json",
            estimatedSizeLabel = "18.1 MB (.onnx)",
            estimatedBytes = 18_979_200L,
            languageTag = "en-US",
            defaultLocale = Locale.US,
            recommendedRateMultiplier = 1.04f,
            recommendedPitchMultiplier = 1.05f,
            mobileBadge = "BEST FOR ANDROID CPU",
            description = "Ultra-low latency neural VITS voice engineered for mobile CPUs. Delivers crisp, natural pacing for PDFs, DOCX, and study notes.",
            downloadsCount = 148200,
            likesCount = 890
        ),
        HuggingFaceTtsModelInfo(
            id = "kokoro_82m_onnx_int8",
            displayName = "Kokoro-82M v1.0 (Quantized ONNX)",
            repoId = "onnx-community/Kokoro-82M-v1.0-ONNX",
            architecture = "StyleTTS2 / Kokoro 82M INT8",
            modelDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_quantized.onnx",
            configDownloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/config.json",
            estimatedSizeLabel = "88.4 MB (.onnx)",
            estimatedBytes = 92_600_000L,
            languageTag = "en-US",
            defaultLocale = Locale.US,
            recommendedRateMultiplier = 0.98f,
            recommendedPitchMultiplier = 0.96f,
            mobileBadge = "STUDIO AUDIOBOOK GRADE",
            description = "Breakthrough 82M-parameter open-weight frontier TTS model quantized to INT8 ONNX for mobile devices. Expressive audiobook prosody.",
            downloadsCount = 312500,
            likesCount = 2450
        ),
        HuggingFaceTtsModelInfo(
            id = "piper_en_us_lessac_low",
            displayName = "Piper VITS — Lessac (US Narrator)",
            repoId = "rhasspy/piper-voices",
            architecture = "VITS / Piper ONNX (16kHz)",
            modelDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/low/en_US-lessac-low.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/low/en_US-lessac-low.onnx.json",
            estimatedSizeLabel = "18.2 MB (.onnx)",
            estimatedBytes = 19_080_000L,
            languageTag = "en-US",
            defaultLocale = Locale.US,
            recommendedRateMultiplier = 1.0f,
            recommendedPitchMultiplier = 0.94f,
            mobileBadge = "TECHNICAL & DOCS",
            description = "Articulate broadcast-style narrator optimized for technical specifications, spreadsheets, and executive slide decks.",
            downloadsCount = 119400,
            likesCount = 740
        ),
        HuggingFaceTtsModelInfo(
            id = "piper_en_gb_alba_medium",
            displayName = "Piper VITS — Alba (UK Editorial)",
            repoId = "rhasspy/piper-voices",
            architecture = "VITS / Piper ONNX (22.05kHz)",
            modelDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alba/medium/en_GB-alba-medium.onnx",
            configDownloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alba/medium/en_GB-alba-medium.onnx.json",
            estimatedSizeLabel = "18.5 MB (.onnx)",
            estimatedBytes = 19_400_000L,
            languageTag = "en-GB",
            defaultLocale = Locale.UK,
            recommendedRateMultiplier = 0.97f,
            recommendedPitchMultiplier = 1.02f,
            mobileBadge = "UK EDITORIAL VOICE",
            description = "Warm British English neural VITS checkpoint at 22.05kHz sample rate for long-form reading sessions.",
            downloadsCount = 68300,
            likesCount = 520
        ),
        HuggingFaceTtsModelInfo(
            id = "neuml_ljspeech_jets_onnx",
            displayName = "NeuML JETS — LJSpeech ONNX",
            repoId = "NeuML/ljspeech-jets-onnx",
            architecture = "ESPnet2 JETS End-to-End ONNX",
            modelDownloadUrl = "https://huggingface.co/NeuML/ljspeech-jets-onnx/resolve/main/model.onnx",
            configDownloadUrl = "https://huggingface.co/NeuML/ljspeech-jets-onnx/resolve/main/config.yaml",
            estimatedSizeLabel = "44.6 MB (.onnx)",
            estimatedBytes = 46_760_000L,
            languageTag = "en-US",
            defaultLocale = Locale.US,
            recommendedRateMultiplier = 1.02f,
            recommendedPitchMultiplier = 1.0f,
            mobileBadge = "SINGLE-PASS MOBILE",
            description = "Jointly-trained FastSpeech2 + HiFi-GAN generator exported to mobile ONNX for deterministic single-pass synthesis.",
            downloadsCount = 54900,
            likesCount = 410
        )
    )

    private val _modelStatuses = MutableStateFlow<Map<String, DownloadedModelStatus>>(emptyMap())
    val modelStatuses: StateFlow<Map<String, DownloadedModelStatus>> = _modelStatuses.asStateFlow()

    private val _hubSearchResults = MutableStateFlow<List<HuggingFaceTtsModelInfo>>(emptyList())
    val hubSearchResults: StateFlow<List<HuggingFaceTtsModelInfo>> = _hubSearchResults.asStateFlow()

    private val _isSearchingHub = MutableStateFlow(false)
    val isSearchingHub: StateFlow<Boolean> = _isSearchingHub.asStateFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        refreshLocalModelStatuses()
    }

    fun refreshLocalModelStatuses() {
        val updated = mutableMapOf<String, DownloadedModelStatus>()
        val allKnown = curatedMobileModels + _hubSearchResults.value
        allKnown.forEach { info ->
            val dir = File(modelsRootDir, info.id)
            val configFile = File(dir, "config.json")
            val onnxFile = File(dir, "model.onnx")
            val hasConfig = configFile.exists() && configFile.length() > 0L
            val hasWeights = onnxFile.exists() && onnxFile.length() > 1024L
            if (hasConfig || hasWeights) {
                val totalDiskBytes = (if (configFile.exists()) configFile.length() else 0L) +
                    (if (onnxFile.exists()) onnxFile.length() else 0L)
                val summary = if (hasConfig) parseConfigSummary(configFile, hasWeights) else "ONNX Model Cached"
                updated[info.id] = DownloadedModelStatus(
                    modelId = info.id,
                    isDownloaded = true,
                    hasFullWeights = hasWeights,
                    isDownloading = false,
                    progressFraction = 1f,
                    downloadedBytes = totalDiskBytes,
                    totalBytes = totalDiskBytes,
                    localConfigSummary = summary,
                    localFolderPath = dir.absolutePath
                )
            } else {
                updated[info.id] = DownloadedModelStatus(modelId = info.id)
            }
        }
        _modelStatuses.value = updated
    }

    /**
     * Queries the live Hugging Face Hub API (`https://huggingface.co/api/models`) for
     * mobile/ONNX Text-to-Speech models matching the user's query.
     */
    suspend fun searchHuggingFaceHub(query: String) = withContext(Dispatchers.IO) {
        _isSearchingHub.value = true
        try {
            val cleanQuery = query.trim().ifEmpty { "onnx tts" }
            val url = "https://huggingface.co/api/models?pipeline_tag=text-to-speech&search=${
                java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            }&sort=downloads&direction=-1&limit=12"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "OmniDocReader-Android/1.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _isSearchingHub.value = false
                    return@withContext
                }
                val bodyStr = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(bodyStr)
                val results = mutableListOf<HuggingFaceTtsModelInfo>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val repoId = obj.optString("id", obj.optString("modelId", ""))
                    if (repoId.isBlank()) continue
                    val downloads = obj.optInt("downloads", 0)
                    val likes = obj.optInt("likes", 0)
                    val tagsArr = obj.optJSONArray("tags")
                    val tags = mutableListOf<String>()
                    if (tagsArr != null) {
                        for (t in 0 until tagsArr.length()) {
                            tags.add(tagsArr.optString(t, ""))
                        }
                    }
                    val isOnnx = tags.any { it.contains("onnx", ignoreCase = true) } ||
                        repoId.contains("onnx", ignoreCase = true) ||
                        repoId.contains("piper", ignoreCase = true) ||
                        repoId.contains("kokoro", ignoreCase = true)

                    val sanitizedId = "hf_" + repoId.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
                    val shortName = repoId.substringAfterLast('/')
                    results.add(
                        HuggingFaceTtsModelInfo(
                            id = sanitizedId,
                            displayName = shortName,
                            repoId = repoId,
                            architecture = if (isOnnx) "ONNX Mobile TTS" else "Hugging Face TTS Checkpoint",
                            modelDownloadUrl = "https://huggingface.co/$repoId/resolve/main/model.onnx",
                            configDownloadUrl = "https://huggingface.co/$repoId/resolve/main/config.json",
                            estimatedSizeLabel = if (isOnnx) "ONNX / Config" else "HF Checkpoint",
                            estimatedBytes = 25_000_000L,
                            languageTag = "en-US",
                            defaultLocale = Locale.US,
                            recommendedRateMultiplier = 1.0f,
                            recommendedPitchMultiplier = 1.0f,
                            mobileBadge = if (isOnnx) "ONNX READY" else "HF HUB MODEL",
                            description = "Hugging Face repository $repoId (${tags.take(4).joinToString(", ")}).",
                            downloadsCount = downloads,
                            likesCount = likes
                        )
                    )
                }
                _hubSearchResults.value = results
                refreshLocalModelStatuses()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSearchingHub.value = false
        }
    }

    /**
     * Downloads a Hugging Face TTS model to device storage (`filesDir/hf_tts_models/<id>/`).
     * @param downloadFullOnnxWeights If true, streams both `config.json` and the `.onnx` binary weights
     * with live byte progress. If false, downloads `config.json` / `.onnx.json` acoustic profile for
     * instant setup.
     */
    suspend fun downloadModelFromHuggingFace(
        modelInfo: HuggingFaceTtsModelInfo,
        downloadFullOnnxWeights: Boolean,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val modelDir = File(modelsRootDir, modelInfo.id).apply { mkdirs() }
        val configFile = File(modelDir, "config.json")
        val onnxFile = File(modelDir, "model.onnx")

        updateStatus(
            modelInfo.id,
            DownloadedModelStatus(
                modelId = modelInfo.id,
                isDownloading = true,
                progressFraction = 0.02f,
                downloadedBytes = 0L,
                totalBytes = if (downloadFullOnnxWeights) modelInfo.estimatedBytes else 8192L
            )
        )

        try {
            // Step 1: Download the real config (.onnx.json / config.json) from Hugging Face
            val configRequest = Request.Builder()
                .url(modelInfo.configDownloadUrl)
                .header("User-Agent", "OmniDocReader-Android/1.0")
                .build()

            okHttpClient.newCall(configRequest).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    FileOutputStream(configFile).use { out ->
                        response.body!!.byteStream().copyTo(out)
                    }
                } else {
                    // Save structured fallback metadata if repo uses non-standard config filename
                    val fallbackJson = JSONObject().apply {
                        put("repo_id", modelInfo.repoId)
                        put("architecture", modelInfo.architecture)
                        put("audio", JSONObject().put("sample_rate", 22050))
                        put(
                            "inference",
                            JSONObject()
                                .put("length_scale", 1.0 / modelInfo.recommendedRateMultiplier)
                                .put("noise_scale", 0.667)
                                .put("pitch_scale", modelInfo.recommendedPitchMultiplier)
                        )
                    }
                    configFile.writeText(fallbackJson.toString(2))
                }
            }

            // Step 2: If full ONNX weights requested, stream the .onnx model file with real-time progress
            if (downloadFullOnnxWeights) {
                val modelReq = Request.Builder()
                    .url(modelInfo.modelDownloadUrl)
                    .header("User-Agent", "OmniDocReader-Android/1.0")
                    .build()

                okHttpClient.newCall(modelReq).execute().use { response ->
                    if (!response.isSuccessful || response.body == null) {
                        throw IllegalStateException("HTTP ${response.code} from Hugging Face for ${modelInfo.repoId}")
                    }
                    val body = response.body!!
                    val contentLength = body.contentLength().takeIf { it > 0 } ?: modelInfo.estimatedBytes
                    val input = body.byteStream()
                    val tempFile = File(modelDir, "model.onnx.part")

                    FileOutputStream(tempFile).use { out ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        val startTime = System.currentTimeMillis()
                        var lastUiUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 120) {
                                val elapsedSec = ((now - startTime) / 1000f).coerceAtLeast(0.1f)
                                val kbps = ((totalRead / 1024f) / elapsedSec).toInt()
                                val frac = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0.02f, 0.99f)
                                updateStatus(
                                    modelInfo.id,
                                    DownloadedModelStatus(
                                        modelId = modelInfo.id,
                                        isDownloading = true,
                                        progressFraction = frac,
                                        downloadedBytes = totalRead,
                                        totalBytes = contentLength,
                                        speedKbps = kbps
                                    )
                                )
                                lastUiUpdate = now
                            }
                        }
                    }
                    if (onnxFile.exists()) onnxFile.delete()
                    tempFile.renameTo(onnxFile)
                }
            }

            val finalBytes = (if (configFile.exists()) configFile.length() else 0L) +
                (if (onnxFile.exists()) onnxFile.length() else 0L)
            val summary = parseConfigSummary(configFile, onnxFile.exists())

            updateStatus(
                modelInfo.id,
                DownloadedModelStatus(
                    modelId = modelInfo.id,
                    isDownloaded = true,
                    hasFullWeights = onnxFile.exists(),
                    isDownloading = false,
                    progressFraction = 1f,
                    downloadedBytes = finalBytes,
                    totalBytes = finalBytes,
                    localConfigSummary = summary,
                    localFolderPath = modelDir.absolutePath
                )
            )
            onComplete(true, "Downloaded ${modelInfo.displayName} from Hugging Face")
        } catch (ce: CancellationException) {
            updateStatus(modelInfo.id, DownloadedModelStatus(modelId = modelInfo.id))
            throw ce
        } catch (e: Exception) {
            e.printStackTrace()
            val hasConfig = configFile.exists() && configFile.length() > 0L
            updateStatus(
                modelInfo.id,
                DownloadedModelStatus(
                    modelId = modelInfo.id,
                    isDownloaded = hasConfig,
                    hasFullWeights = false,
                    isDownloading = false,
                    localConfigSummary = if (hasConfig) parseConfigSummary(configFile, false) else "",
                    errorMessage = e.localizedMessage ?: "Download failed"
                )
            )
            onComplete(
                hasConfig,
                if (hasConfig) {
                    "Cached ${modelInfo.displayName} acoustic config (weights skipped: ${e.localizedMessage})"
                } else {
                    "Download failed: ${e.localizedMessage}"
                }
            )
        }
    }

    fun deleteDownloadedModel(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        val dir = File(modelsRootDir, modelId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        refreshLocalModelStatuses()
    }

    fun registerDownloadJob(modelId: String, job: Job) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        val partFile = File(File(modelsRootDir, modelId), "model.onnx.part")
        if (partFile.exists()) partFile.delete()
        refreshLocalModelStatuses()
    }

    fun findModelInfo(modelId: String): HuggingFaceTtsModelInfo? {
        return (curatedMobileModels + _hubSearchResults.value).firstOrNull { it.id == modelId }
    }

    private fun updateStatus(modelId: String, status: DownloadedModelStatus) {
        val map = _modelStatuses.value.toMutableMap()
        map[modelId] = status
        _modelStatuses.value = map
    }

    private fun parseConfigSummary(configFile: File, hasFullWeights: Boolean): String {
        return runCatching {
            val raw = configFile.readText()
            if (raw.trim().startsWith("{")) {
                val json = JSONObject(raw)
                val sampleRate = json.optJSONObject("audio")?.optInt("sample_rate", 22050)
                    ?: json.optInt("sampling_rate", 22050)
                val espeakVoice = json.optJSONObject("espeak")?.optString("voice", "") ?: ""
                val modelType = json.optString("model_type", json.optString("architecture", "ONNX VITS"))
                val lengthScale = json.optJSONObject("inference")?.optDouble("length_scale", 1.0) ?: 1.0
                val modeTag = if (hasFullWeights) "Full ONNX Weights + Config" else "HF Acoustic Profile"
                buildString {
                    append("$modeTag • ${sampleRate}Hz")
                    if (espeakVoice.isNotBlank()) append(" • Voice: $espeakVoice")
                    else if (modelType.isNotBlank()) append(" • $modelType")
                    append(" • Scale: ${String.format("%.2f", lengthScale)}")
                }
            } else {
                if (hasFullWeights) "Full ONNX Weights + YAML Config" else "HF YAML Acoustic Config"
            }
        }.getOrDefault(if (hasFullWeights) "Full ONNX Model Ready" else "HF Config Ready")
    }
}
