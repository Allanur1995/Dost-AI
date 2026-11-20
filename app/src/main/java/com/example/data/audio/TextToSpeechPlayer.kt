package com.example.data.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.local.UiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsPlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false
)

class TextToSpeechPlayer(context: Context) {
    private val appContext = context.applicationContext

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isInitializing = false

    private var currentMessageId: String? = null
    private var currentSpeechText: String = ""
    private var currentLanguage: UiLanguage = UiLanguage.TM
    private var segmentStartOffset: Int = 0
    private var segmentEndOffset: Int = 0
    private var lastSpokenRelativeOffset: Int = 0
    private var resumeOffset: Int = 0
    private var utteranceGeneration: Long = 0L
    private var activeUtteranceId: String? = null
    private var pendingOnError: (() -> Unit)? = null

    fun togglePlayPause(
        messageId: String,
        rawText: String,
        language: UiLanguage,
        onUnavailable: () -> Unit = {}
    ) {
        val cleaned = sanitizeMarkdownForSpeech(rawText)
        if (cleaned.isBlank()) return

        val state = _playbackState.value
        if (state.activeMessageId == messageId) {
            if (state.isPlaying) {
                pausePlayback()
                return
            }
            if (state.isPaused) {
                resumePlayback(onUnavailable)
                return
            }
        }

        startNewPlayback(messageId, cleaned, language, onUnavailable)
    }

    private fun startNewPlayback(
        messageId: String,
        cleanedText: String,
        language: UiLanguage,
        onUnavailable: () -> Unit
    ) {
        utteranceGeneration++
        activeUtteranceId = null
        runCatching { tts?.stop() }

        currentMessageId = messageId
        currentSpeechText = cleanedText
        currentLanguage = language
        segmentStartOffset = 0
        segmentEndOffset = 0
        lastSpokenRelativeOffset = 0
        resumeOffset = 0
        pendingOnError = onUnavailable

        speakFromOffset(0, onUnavailable)
    }

    private fun pausePlayback() {
        val msgId = currentMessageId ?: return
        val absolutePos = (segmentStartOffset + lastSpokenRelativeOffset)
            .coerceIn(0, currentSpeechText.length)
        resumeOffset = snapToWordBoundary(currentSpeechText, absolutePos)

        utteranceGeneration++
        activeUtteranceId = null
        runCatching { tts?.stop() }

        if (resumeOffset >= currentSpeechText.length) {
            resetState()
        } else {
            _playbackState.value = TtsPlaybackState(
                activeMessageId = msgId,
                isPlaying = false,
                isPaused = true
            )
        }
    }

    private fun resumePlayback(onUnavailable: () -> Unit) {
        val msgId = currentMessageId ?: return
        if (resumeOffset >= currentSpeechText.length) {
            startNewPlayback(msgId, currentSpeechText, currentLanguage, onUnavailable)
            return
        }
        pendingOnError = onUnavailable
        speakFromOffset(resumeOffset, onUnavailable)
    }

    private fun speakFromOffset(offset: Int, onUnavailable: () -> Unit) {
        val msgId = currentMessageId ?: return
        val clampedOffset = offset.coerceIn(0, currentSpeechText.length)
        if (clampedOffset >= currentSpeechText.length) {
            resetState()
            return
        }

        if (tts == null && !isInitializing) {
            initTts(onUnavailable)
        }

        val engine = tts
        if (!isInitialized || engine == null) {
            _playbackState.value = TtsPlaybackState(
                activeMessageId = msgId,
                isPlaying = true,
                isPaused = false
            )
            return
        }

        applyBestLocale(engine, currentSpeechText, currentLanguage)

        val endIndex = computeChunkEndIndex(currentSpeechText, clampedOffset, MAX_CHUNK_LENGTH)
        val chunk = currentSpeechText.substring(clampedOffset, endIndex)
        if (chunk.isBlank()) {
            resetState()
            return
        }

        segmentStartOffset = clampedOffset
        segmentEndOffset = endIndex
        lastSpokenRelativeOffset = 0

        val gen = ++utteranceGeneration
        val utteranceId = "dost_tts_${msgId}_${gen}_$clampedOffset"
        activeUtteranceId = utteranceId

        _playbackState.value = TtsPlaybackState(
            activeMessageId = msgId,
            isPlaying = true,
            isPaused = false
        )

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TextToSpeech.speak returned ERROR")
            resetState()
            onUnavailable()
        }
    }

    private fun initTts(onUnavailable: () -> Unit) {
        isInitializing = true
        pendingOnError = onUnavailable
        tts = TextToSpeech(appContext) { status ->
            isInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // State is already set to playing when speak() is invoked.
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        if (utteranceId == activeUtteranceId) {
                            lastSpokenRelativeOffset = start.coerceAtLeast(0)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) return
                        if (segmentEndOffset < currentSpeechText.length) {
                            speakFromOffset(
                                offset = segmentEndOffset,
                                onUnavailable = pendingOnError ?: {}
                            )
                        } else {
                            resetState()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) return
                        resetState()
                        pendingOnError?.invoke()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId != activeUtteranceId) return
                        Log.e(TAG, "TTS utterance error code=$errorCode")
                        resetState()
                        pendingOnError?.invoke()
                    }
                })

                // If playback was queued while engine initialized, start speaking now.
                val state = _playbackState.value
                if (state.isPlaying && currentMessageId != null && currentSpeechText.isNotEmpty()) {
                    speakFromOffset(resumeOffset, pendingOnError ?: {})
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status=$status")
                isInitialized = false
                resetState()
                pendingOnError?.invoke()
            }
        }
    }

    private fun applyBestLocale(
        engine: TextToSpeech,
        text: String,
        language: UiLanguage
    ) {
        val candidates = buildList {
            if (language == UiLanguage.EN && !containsTurkmenDiacritics(text)) {
                add(Locale.US)
                add(Locale.ENGLISH)
            } else {
                add(Locale.forLanguageTag("tk-TM"))
                add(Locale.forLanguageTag("tr-TR"))
                add(Locale.US)
            }
            add(Locale.getDefault())
        }

        for (locale in candidates) {
            val availability = runCatching { engine.isLanguageAvailable(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = locale
                return
            }
        }
    }

    fun stop() {
        utteranceGeneration++
        activeUtteranceId = null
        runCatching { tts?.stop() }
        resetState()
    }

    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        isInitialized = false
        isInitializing = false
    }

    private fun resetState() {
        currentMessageId = null
        currentSpeechText = ""
        segmentStartOffset = 0
        segmentEndOffset = 0
        lastSpokenRelativeOffset = 0
        resumeOffset = 0
        activeUtteranceId = null
        _playbackState.value = TtsPlaybackState()
    }

    companion object {
        private const val TAG = "TextToSpeechPlayer"
        private const val MAX_CHUNK_LENGTH = 3500

        internal fun sanitizeMarkdownForSpeech(rawText: String): String {
            if (rawText.isBlank()) return ""
            return rawText
                .replace(Regex("```[a-zA-Z0-9_-]*\\s*"), "\n")
                .replace("```", "\n")
                .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
                .replace(Regex("(?m)^#{1,6}\\s+"), "")
                .replace(Regex("(?m)^>\\s?"), "")
                .replace(Regex("(?m)^[-*+]\\s+"), "")
                .replace(Regex("\\*\\*|__|~~|\\*"), "")
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        }

        internal fun snapToWordBoundary(text: String, index: Int): Int {
            val clamped = index.coerceIn(0, text.length)
            if (clamped == 0 || clamped >= text.length) return clamped
            var pos = clamped
            while (pos > 0 && !text[pos - 1].isWhitespace()) {
                pos--
            }
            while (pos < text.length && text[pos].isWhitespace()) {
                pos++
            }
            return pos
        }

        internal fun computeChunkEndIndex(text: String, startOffset: Int, maxLength: Int): Int {
            val limit = (startOffset + maxLength).coerceAtMost(text.length)
            if (limit >= text.length) return text.length

            val searchSlice = text.substring(startOffset, limit)
            val sentenceBreak = maxOf(
                searchSlice.lastIndexOf(". "),
                searchSlice.lastIndexOf("! "),
                searchSlice.lastIndexOf("? "),
                searchSlice.lastIndexOf('\n')
            )
            if (sentenceBreak > maxLength / 3) {
                return startOffset + sentenceBreak + 1
            }

            val wordBreak = searchSlice.lastIndexOf(' ')
            if (wordBreak > 0) {
                return startOffset + wordBreak + 1
            }
            return limit
        }

        private fun containsTurkmenDiacritics(text: String): Boolean {
            return text.any { it in "äöüýňžçşÄÖÜÝŇŽÇŞ" }
        }
    }
}
