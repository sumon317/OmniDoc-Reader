package com.example.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

data class AndroidSystemVoiceItem(
    val name: String,
    val localeDisplayName: String,
    val languageTag: String,
    val isOfflineCapable: Boolean,
    val quality: Int
)

data class TtsPlaybackState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isSynthesizingFile: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val sentences: List<String> = emptyList(),
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val activeHfModelId: String? = null,
    val activeVoiceLabel: String = "Android System Neural TTS",
    val availableSystemVoices: List<AndroidSystemVoiceItem> = emptyList(),
    val selectedSystemVoiceName: String? = null,
    val lastExportedWavPath: String? = null
)

class OfflineTtsController(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var rawVoicesMap: Map<String, Voice> = emptyMap()

    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private var modelRateMultiplier: Float = 1.0f
    private var modelPitchMultiplier: Float = 1.0f

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            runCatching {
                tts?.language = Locale.US
                val voices = tts?.voices?.filterNotNull().orEmpty()
                rawVoicesMap = voices.associateBy { it.name }

                val voiceItems = voices
                    .filter { it.locale.language.startsWith("en") || !it.isNetworkConnectionRequired }
                    .sortedWith(
                        compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                            .thenByDescending { it.quality }
                            .thenBy { it.locale.displayName }
                    )
                    .take(24)
                    .map { v ->
                        AndroidSystemVoiceItem(
                            name = v.name,
                            localeDisplayName = "${v.locale.displayLanguage} (${v.locale.displayCountry.ifEmpty { v.locale.country }})",
                            languageTag = v.locale.toLanguageTag(),
                            isOfflineCapable = !v.isNetworkConnectionRequired,
                            quality = v.quality
                        )
                    }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "wav_export_utt") {
                            _state.value = _state.value.copy(isSynthesizingFile = false)
                            return
                        }
                        val nextIdx = _state.value.currentSentenceIndex + 1
                        val list = _state.value.sentences
                        if (nextIdx < list.size && _state.value.isPlaying) {
                            speakSentenceAt(nextIdx)
                        } else {
                            _state.value = _state.value.copy(isPlaying = false)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isSynthesizingFile = false
                        )
                    }
                })

                _state.value = _state.value.copy(
                    isReady = true,
                    availableSystemVoices = voiceItems,
                    selectedSystemVoiceName = tts?.voice?.name
                )
            }
        }
    }

    /**
     * Activates a downloaded Hugging Face TTS model profile, aligning locale, prosody scaling,
     * and Android TextToSpeech synthesis parameters.
     */
    fun applyHuggingFaceModelProfile(modelInfo: HuggingFaceTtsModelInfo?) {
        if (modelInfo == null) {
            modelRateMultiplier = 1.0f
            modelPitchMultiplier = 1.0f
            _state.value = _state.value.copy(
                activeHfModelId = null,
                activeVoiceLabel = "Android System Neural TTS"
            )
            return
        }

        modelRateMultiplier = modelInfo.recommendedRateMultiplier
        modelPitchMultiplier = modelInfo.recommendedPitchMultiplier

        runCatching {
            tts?.language = modelInfo.defaultLocale
            // Select best matching offline voice for the model's locale if available
            val matchingVoice = rawVoicesMap.values
                .filter { !it.isNetworkConnectionRequired && it.locale.language == modelInfo.defaultLocale.language }
                .maxByOrNull {
                    val countryMatch = if (it.locale.country.equals(modelInfo.defaultLocale.country, ignoreCase = true)) 1000 else 0
                    countryMatch + it.quality
                }
            if (matchingVoice != null) {
                tts?.voice = matchingVoice
            }
        }

        _state.value = _state.value.copy(
            activeHfModelId = modelInfo.id,
            activeVoiceLabel = "HF: ${modelInfo.displayName}"
        )

        if (_state.value.isPlaying) {
            speakSentenceAt(_state.value.currentSentenceIndex)
        }
    }

    /**
     * Selects a specific Android system Voice from `TextToSpeech.getVoices()`.
     */
    fun selectAndroidSystemVoice(voiceName: String) {
        val voice = rawVoicesMap[voiceName] ?: return
        runCatching {
            tts?.voice = voice
        }
        modelRateMultiplier = 1.0f
        modelPitchMultiplier = 1.0f
        _state.value = _state.value.copy(
            selectedSystemVoiceName = voiceName,
            activeHfModelId = null,
            activeVoiceLabel = "System: ${voice.locale.displayName} (${voice.name.takeLast(14)})"
        )
        if (_state.value.isPlaying) {
            speakSentenceAt(_state.value.currentSentenceIndex)
        }
    }

    fun prepareDocumentText(
        rawText: String,
        rate: Float = _state.value.speechRate,
        pitch: Float = _state.value.pitch
    ) {
        stop()
        val parsed = rawText
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .ifEmpty { listOf(rawText.trim().ifEmpty { "No readable text found in this document." }) }

        _state.value = _state.value.copy(
            currentSentenceIndex = 0,
            totalSentences = parsed.size,
            currentSentenceText = parsed.firstOrNull() ?: "",
            sentences = parsed,
            speechRate = rate,
            pitch = pitch,
            isPlaying = false
        )
    }

    fun speakDemoSample(sampleText: String) {
        prepareDocumentText(sampleText, _state.value.speechRate, _state.value.pitch)
        play()
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val list = _state.value.sentences
        if (list.isEmpty()) return
        val idx = _state.value.currentSentenceIndex.coerceIn(0, list.lastIndex)
        speakSentenceAt(idx)
    }

    fun jumpToSentence(index: Int) {
        val list = _state.value.sentences
        if (list.isEmpty()) return
        val safeIdx = index.coerceIn(0, list.lastIndex)
        speakSentenceAt(safeIdx)
    }

    fun pause() {
        runCatching { tts?.stop() }
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun stop() {
        runCatching { tts?.stop() }
        val first = _state.value.sentences.firstOrNull() ?: ""
        _state.value = _state.value.copy(
            isPlaying = false,
            currentSentenceIndex = 0,
            currentSentenceText = first
        )
    }

    fun skipNext() {
        val list = _state.value.sentences
        if (list.isEmpty()) return
        val next = (_state.value.currentSentenceIndex + 1).coerceAtMost(list.lastIndex)
        if (_state.value.isPlaying) {
            speakSentenceAt(next)
        } else {
            _state.value = _state.value.copy(
                currentSentenceIndex = next,
                currentSentenceText = list[next]
            )
        }
    }

    fun skipPrevious() {
        val list = _state.value.sentences
        if (list.isEmpty()) return
        val prev = (_state.value.currentSentenceIndex - 1).coerceAtLeast(0)
        if (_state.value.isPlaying) {
            speakSentenceAt(prev)
        } else {
            _state.value = _state.value.copy(
                currentSentenceIndex = prev,
                currentSentenceText = list[prev]
            )
        }
    }

    fun setRateAndPitch(rate: Float, pitch: Float) {
        val safeRate = rate.coerceIn(0.5f, 2.2f)
        val safePitch = pitch.coerceIn(0.6f, 1.6f)
        _state.value = _state.value.copy(speechRate = safeRate, pitch = safePitch)
        if (_state.value.isPlaying) {
            speakSentenceAt(_state.value.currentSentenceIndex)
        }
    }

    /**
     * Uses Android's `TextToSpeech.synthesizeToFile` API to synthesize the active document
     * text into a standalone offline `.wav` audio file!
     */
    fun synthesizeCurrentDocToWavFile(
        docTitle: String,
        onResult: (File?) -> Unit
    ) {
        val engine = tts ?: run {
            onResult(null)
            return
        }
        val textToSynthesize = _state.value.sentences.take(35).joinToString(" ").take(3800)
        if (textToSynthesize.isBlank()) {
            onResult(null)
            return
        }

        val audioDir = File(context.filesDir, "tts_audio_exports").apply { mkdirs() }
        val sanitized = docTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val wavFile = File(audioDir, "${sanitized}_TTS_Audio.wav")

        _state.value = _state.value.copy(isSynthesizingFile = true)
        runCatching {
            val effectiveRate = (_state.value.speechRate * modelRateMultiplier).coerceIn(0.4f, 2.5f)
            val effectivePitch = (_state.value.pitch * modelPitchMultiplier).coerceIn(0.5f, 1.8f)
            engine.setSpeechRate(effectiveRate)
            engine.setPitch(effectivePitch)
            val res = engine.synthesizeToFile(textToSynthesize, Bundle(), wavFile, "wav_export_utt")
            if (res == TextToSpeech.SUCCESS) {
                _state.value = _state.value.copy(
                    isSynthesizingFile = false,
                    lastExportedWavPath = wavFile.absolutePath
                )
                onResult(wavFile)
            } else {
                _state.value = _state.value.copy(isSynthesizingFile = false)
                onResult(null)
            }
        }.onFailure {
            _state.value = _state.value.copy(isSynthesizingFile = false)
            onResult(null)
        }
    }

    private fun speakSentenceAt(index: Int) {
        val sentence = _state.value.sentences.getOrNull(index) ?: return
        _state.value = _state.value.copy(
            isPlaying = true,
            currentSentenceIndex = index,
            currentSentenceText = sentence
        )
        runCatching {
            val effectiveRate = (_state.value.speechRate * modelRateMultiplier).coerceIn(0.4f, 2.5f)
            val effectivePitch = (_state.value.pitch * modelPitchMultiplier).coerceIn(0.5f, 1.8f)
            tts?.setSpeechRate(effectiveRate)
            tts?.setPitch(effectivePitch)
            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "utt_$index")
        }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }
}
