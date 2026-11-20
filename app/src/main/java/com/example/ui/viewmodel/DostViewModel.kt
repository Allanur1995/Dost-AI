package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.TextToSpeechPlayer
import com.example.data.audio.VoiceRecorder
import com.example.data.export.ChatExportFormat
import com.example.data.export.ChatHistoryExporter
import com.example.data.local.ConversationEntity
import com.example.data.local.DostDatabase
import com.example.data.local.MessageEntity
import com.example.data.local.PersonaEntity
import com.example.data.local.UiLanguage
import com.example.data.local.UserPreferences
import com.example.data.repository.AiStreamEvent
import com.example.data.repository.DostRepository
import com.example.data.repository.ImageGenResult
import com.example.ui.state.AppScreen
import com.example.ui.state.DostUiState
import com.example.ui.state.StreamingStatus
import com.example.ui.state.localizedCopy
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DostViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DostDatabase.getInstance(application)
    private val repository = DostRepository(application, database.dostDao())
    private val userPreferences = UserPreferences(application)
    private val voiceRecorder = VoiceRecorder(application)
    private val ttsPlayer = TextToSpeechPlayer(application)

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Chat)
    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _selectedPersonaId = MutableStateFlow(DostDatabase.defaultPersonas.first().id)
    private val _streamingStatus = MutableStateFlow<StreamingStatus>(StreamingStatus.Idle)
    private val _composerText = MutableStateFlow("")
    private val _pendingImagePath = MutableStateFlow<String?>(null)
    private val _isAttachingImage = MutableStateFlow(false)
    private val _useSearchGrounding = MutableStateFlow(false)
    private val _isImageGenMode = MutableStateFlow(false)
    private data class VoiceRecordingState(
        val isRecording: Boolean = false,
        val seconds: Int = 0,
        val amplitude: Float = 0f,
        val isTranscribing: Boolean = false
    )

    private val _voiceState = MutableStateFlow(VoiceRecordingState())
    private val _errorBanner = MutableStateFlow<String?>(null)

    private var activeStreamJob: Job? = null
    private var recordingMeterJob: Job? = null
    private var transcriptionJob: Job? = null

    private val messagesFlow = _activeConversationId.flatMapLatest { convId ->
        if (convId == null) flowOf(emptyList()) else repository.observeMessages(convId)
    }

    private data class CoreFlows(
        val screen: AppScreen,
        val personas: List<PersonaEntity>,
        val conversations: List<ConversationEntity>,
        val activeConvId: String?,
        val selectedPersonaId: String
    )

    private data class ComposerFlows(
        val messages: List<MessageEntity>,
        val streamingStatus: StreamingStatus,
        val composerText: String,
        val pendingImagePath: String?,
        val isAttachingImage: Boolean
    )

    private data class ModeFlows(
        val useSearchGrounding: Boolean,
        val isImageGenMode: Boolean,
        val voiceState: VoiceRecordingState,
        val errorBanner: String?
    )

    private val coreFlow = combine(
        _screen,
        repository.personas,
        repository.conversations,
        _activeConversationId,
        _selectedPersonaId
    ) { screen, personas, conversations, activeConvId, selectedPersonaId ->
        CoreFlows(screen, personas, conversations, activeConvId, selectedPersonaId)
    }

    private val composerFlow = combine(
        messagesFlow,
        _streamingStatus,
        _composerText,
        _pendingImagePath,
        _isAttachingImage
    ) { messages, streaming, composer, pendingImg, attaching ->
        ComposerFlows(messages, streaming, composer, pendingImg, attaching)
    }

    private val modeFlow = combine(
        _useSearchGrounding,
        _isImageGenMode,
        _voiceState,
        _errorBanner
    ) { search, imageGen, voiceState, errorBanner ->
        ModeFlows(search, imageGen, voiceState, errorBanner)
    }

    val uiState: StateFlow<DostUiState> = combine(
        coreFlow,
        composerFlow,
        modeFlow,
        userPreferences.preferencesFlow,
        ttsPlayer.playbackState
    ) { core, composer, modes, prefs, ttsState ->
        val activeConversation = core.conversations.firstOrNull { it.id == core.activeConvId }
        val effectivePersonaId = activeConversation?.personaId ?: core.selectedPersonaId
        val activePersona = core.personas.firstOrNull { it.id == effectivePersonaId }
            ?: core.personas.firstOrNull()

        DostUiState(
            screen = core.screen,
            personas = core.personas,
            activePersona = activePersona,
            conversations = core.conversations,
            activeConversationId = core.activeConvId,
            messages = composer.messages,
            streamingStatus = composer.streamingStatus,
            composerText = composer.composerText,
            pendingImagePath = composer.pendingImagePath,
            isAttachingImage = composer.isAttachingImage,
            useSearchGrounding = modes.useSearchGrounding,
            isImageGenMode = modes.isImageGenMode,
            isRecordingAudio = modes.voiceState.isRecording,
            recordingSeconds = modes.voiceState.seconds,
            recordingAmplitude = modes.voiceState.amplitude,
            isTranscribingAudio = modes.voiceState.isTranscribing,
            themePreference = prefs.themePreference,
            uiLanguage = prefs.uiLanguage,
            ttsState = ttsState,
            errorBanner = modes.errorBanner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DostUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val prefs = userPreferences.preferencesFlow.first()
            val savedConvId = prefs.lastConversationId
            val restored = if (!savedConvId.isNullOrBlank()) {
                repository.getConversation(savedConvId)
            } else {
                null
            } ?: repository.getMostRecentConversation()

            if (restored != null) {
                _activeConversationId.value = restored.id
                _selectedPersonaId.value = restored.personaId
                userPreferences.setLastConversationId(restored.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingMeterJob?.cancel()
        transcriptionJob?.cancel()
        voiceRecorder.stopAndDiscard()
        ttsPlayer.release()
    }

    fun toggleMessageSpeech(message: MessageEntity) {
        if (message.content.isBlank() || message.isError) return
        val copy = localizedCopy(uiState.value.uiLanguage)
        ttsPlayer.togglePlayPause(
            messageId = message.id,
            rawText = message.content,
            language = uiState.value.uiLanguage,
            onUnavailable = {
                _errorBanner.value = copy.ttsUnavailableError
            }
        )
    }

    fun stopMessageSpeech() {
        ttsPlayer.stop()
    }

    fun openPersonaStudio() {
        _screen.value = AppScreen.PersonaStudio
    }

    fun closePersonaStudio() {
        _screen.value = AppScreen.Chat
    }

    fun onComposerTextChanged(text: String) {
        _composerText.value = text
    }

    fun dismissErrorBanner() {
        _errorBanner.value = null
    }

    fun showErrorMessage(message: String) {
        _errorBanner.value = message
    }

    fun toggleSearchGrounding() {
        val next = !_useSearchGrounding.value
        _useSearchGrounding.value = next
        if (next) _isImageGenMode.value = false
    }

    fun toggleImageGenMode() {
        val next = !_isImageGenMode.value
        _isImageGenMode.value = next
        if (next) _useSearchGrounding.value = false
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            userPreferences.setThemePreference(theme)
        }
    }

    fun toggleTheme() {
        val next = when (uiState.value.themePreference) {
            ThemePreference.DARK -> ThemePreference.LIGHT
            ThemePreference.LIGHT -> ThemePreference.SYSTEM
            ThemePreference.SYSTEM -> ThemePreference.DARK
        }
        setThemePreference(next)
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (uiState.value.uiLanguage == UiLanguage.TM) {
                UiLanguage.EN
            } else {
                UiLanguage.TM
            }
            userPreferences.setUiLanguage(next)
        }
    }

    fun startNewChat(personaId: String? = null) {
        stopStreaming()
        ttsPlayer.stop()
        viewModelScope.launch {
            _activeConversationId.value = null
            if (personaId != null) {
                _selectedPersonaId.value = personaId
            }
            userPreferences.setLastConversationId(null)
            _screen.value = AppScreen.Chat
        }
    }

    fun selectConversation(conversation: ConversationEntity) {
        if (conversation.id == _activeConversationId.value) {
            _screen.value = AppScreen.Chat
            return
        }
        stopStreaming()
        ttsPlayer.stop()
        viewModelScope.launch {
            _activeConversationId.value = conversation.id
            _selectedPersonaId.value = conversation.personaId
            userPreferences.setLastConversationId(conversation.id)
            _screen.value = AppScreen.Chat
        }
    }

    fun deleteConversation(conversationId: String) {
        if (conversationId == _activeConversationId.value) {
            stopStreaming()
            ttsPlayer.stop()
        }
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
                userPreferences.setLastConversationId(null)
            }
        }
    }

    fun clearCurrentChatHistory() {
        stopStreaming()
        ttsPlayer.stop()
        val convId = _activeConversationId.value
        _activeConversationId.value = null
        _errorBanner.value = null
        viewModelScope.launch {
            if (convId != null) {
                repository.clearConversationHistory(convId)
            }
            userPreferences.setLastConversationId(null)
        }
    }

    fun buildSuggestedExportFileName(format: ChatExportFormat): String {
        val state = uiState.value
        val activeConv = state.conversations.firstOrNull { it.id == state.activeConversationId }
        return ChatHistoryExporter.buildSuggestedFileName(
            conversationTitle = activeConv?.title ?: state.activePersona?.name,
            format = format
        )
    }

    suspend fun exportChatToUri(uri: Uri, format: ChatExportFormat): Result<Unit> {
        val state = uiState.value
        val activeConv = state.conversations.firstOrNull { it.id == state.activeConversationId }
        val payload = when (format) {
            ChatExportFormat.JSON -> ChatHistoryExporter.formatAsJson(
                conversation = activeConv,
                persona = state.activePersona,
                messages = state.messages
            )
            ChatExportFormat.TEXT -> ChatHistoryExporter.formatAsPlainText(
                conversation = activeConv,
                persona = state.activePersona,
                messages = state.messages
            )
        }
        return ChatHistoryExporter.writeToUri(getApplication(), uri, payload)
    }

    fun selectPersona(persona: PersonaEntity) {
        viewModelScope.launch {
            _selectedPersonaId.value = persona.id
            val convId = _activeConversationId.value
            if (convId != null) {
                repository.updateConversationPersona(convId, persona.id)
            }
        }
    }

    fun saveCustomPersona(
        existingId: String?,
        name: String,
        tagline: String,
        systemPrompt: String,
        modelName: String,
        temperature: Float,
        iconKey: String,
        accentIndex: Int
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        viewModelScope.launch {
            val id = existingId ?: "custom_${UUID.randomUUID()}"
            val existing = existingId?.let { repository.getPersona(it) }
            val persona = PersonaEntity(
                id = id,
                name = trimmedName,
                taglineTm = tagline.trim().ifBlank { trimmedName },
                taglineEn = tagline.trim().ifBlank { trimmedName },
                systemPrompt = systemPrompt.trim(),
                modelName = modelName,
                temperature = temperature,
                iconKey = iconKey,
                accentIndex = accentIndex,
                isBuiltIn = existing?.isBuiltIn ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            repository.upsertCustomPersona(persona)
            _selectedPersonaId.value = persona.id
            _activeConversationId.value?.let { convId ->
                repository.updateConversationPersona(convId, persona.id)
            }
        }
    }

    fun deleteCustomPersona(personaId: String) {
        viewModelScope.launch {
            repository.deleteCustomPersona(personaId)
            if (_selectedPersonaId.value == personaId) {
                _selectedPersonaId.value = DostDatabase.defaultPersonas.first().id
            }
        }
    }

    fun attachImageFromPicker(uri: Uri) {
        viewModelScope.launch {
            _isAttachingImage.value = true
            val oldPath = _pendingImagePath.value
            val savedPath = repository.savePickedImage(uri)
            if (savedPath != null) {
                if (oldPath != null) {
                    runCatching { File(oldPath).delete() }
                }
                _pendingImagePath.value = savedPath
            } else {
                _errorBanner.value = "Could not load selected photo."
            }
            _isAttachingImage.value = false
        }
    }

    fun removePendingImage() {
        val path = _pendingImagePath.value ?: return
        _pendingImagePath.value = null
        runCatching { File(path).delete() }
    }

    fun toggleVoiceRecording() {
        val voice = _voiceState.value
        if (voice.isTranscribing || uiState.value.isGenerating) return
        val copy = localizedCopy(uiState.value.uiLanguage)
        if (!voice.isRecording) {
            val started = voiceRecorder.startRecording()
            if (started) {
                _voiceState.value = VoiceRecordingState(isRecording = true)
                _errorBanner.value = null
                startRecordingMeter()
            } else {
                _errorBanner.value = copy.voiceMicUnavailable
            }
        } else {
            finishRecordingAndTranscribe(copy)
        }
    }

    private fun finishRecordingAndTranscribe(copy: com.example.ui.state.LocalizedCopy) {
        recordingMeterJob?.cancel()
        recordingMeterJob = null
        _voiceState.value = VoiceRecordingState(isRecording = false)
        val recordedFile = voiceRecorder.stopAndGetFile()
        if (recordedFile == null) {
            _errorBanner.value = copy.voiceTooShort
            return
        }
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch {
            _voiceState.value = VoiceRecordingState(isTranscribing = true)
            try {
                val result = repository.transcribeAudio(recordedFile)
                result.fold(
                    onSuccess = { transcript ->
                        _composerText.value = mergeTranscribedText(_composerText.value, transcript)
                    },
                    onFailure = { error ->
                        _errorBanner.value = error.localizedMessage ?: copy.voiceTranscriptionFailed
                    }
                )
            } finally {
                _voiceState.value = VoiceRecordingState()
                transcriptionJob = null
            }
        }
    }

    fun cancelVoiceRecording() {
        recordingMeterJob?.cancel()
        recordingMeterJob = null
        transcriptionJob?.cancel()
        transcriptionJob = null
        voiceRecorder.stopAndDiscard()
        _voiceState.value = VoiceRecordingState()
    }

    private fun startRecordingMeter() {
        recordingMeterJob?.cancel()
        recordingMeterJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (_voiceState.value.isRecording) {
                val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                if (elapsedSeconds >= MAX_RECORDING_SECONDS) {
                    finishRecordingAndTranscribe(localizedCopy(uiState.value.uiLanguage))
                    break
                }
                val amplitude = voiceRecorder.getAmplitudeRatio()
                _voiceState.value = _voiceState.value.copy(
                    seconds = elapsedSeconds,
                    amplitude = amplitude
                )
                kotlinx.coroutines.delay(150L)
            }
        }
    }

    fun sendStarterPrompt(prompt: String) {
        _composerText.value = prompt
        sendMessage()
    }

    fun sendMessage() {
        val voice = _voiceState.value
        if (uiState.value.isGenerating || voice.isRecording || voice.isTranscribing) return
        val text = _composerText.value.trim()
        val imagePath = _pendingImagePath.value
        if (text.isBlank() && imagePath == null) return

        val generateImage = _isImageGenMode.value
        val useSearch = _useSearchGrounding.value

        _composerText.value = ""
        _pendingImagePath.value = null
        _errorBanner.value = null

        activeStreamJob = viewModelScope.launch {
            try {
                val activePersona = uiState.value.activePersona
                    ?: repository.getPersona(_selectedPersonaId.value)

                var convId = _activeConversationId.value
                val priorMessages: List<MessageEntity>
                if (convId == null) {
                    val title = buildConversationTitle(text, activePersona.name)
                    val created = repository.createConversation(activePersona.id, title)
                    convId = created.id
                    _activeConversationId.value = convId
                    userPreferences.setLastConversationId(convId)
                    priorMessages = emptyList()
                } else {
                    priorMessages = repository.getMessagesOnce(convId)
                    val conv = repository.getConversation(convId)
                    if (conv != null && priorMessages.isEmpty()) {
                        repository.updateConversationTitle(
                            convId,
                            buildConversationTitle(text, activePersona.name)
                        )
                    }
                }

                val userMessage = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = MessageEntity.ROLE_USER,
                    content = text,
                    imagePath = imagePath,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertMessage(userMessage)

                if (generateImage) {
                    runImageGenerationTurn(
                        conversationId = convId,
                        promptText = text,
                        inputImagePath = imagePath
                    )
                } else {
                    streamAssistantTurn(
                        conversationId = convId,
                        persona = activePersona,
                        historyBeforeTurn = priorMessages,
                        promptText = text,
                        imagePath = imagePath,
                        useSearchGrounding = useSearch
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _streamingStatus.value = StreamingStatus.Idle
                _errorBanner.value = e.localizedMessage ?: "Failed to send message."
            }
        }
    }

    fun regenerateLastResponse() {
        if (uiState.value.isGenerating) return
        val convId = _activeConversationId.value ?: return

        activeStreamJob = viewModelScope.launch {
            try {
                val messages = repository.getMessagesOnce(convId)
                if (messages.isEmpty()) return@launch

                val lastMessage = messages.last()
                val remaining = if (lastMessage.role == MessageEntity.ROLE_MODEL) {
                    repository.deleteMessage(lastMessage.id)
                    messages.dropLast(1)
                } else {
                    messages
                }

                val lastUserMessage = remaining.lastOrNull { it.role == MessageEntity.ROLE_USER }
                    ?: return@launch
                val historyBeforeTurn = remaining.dropLastWhile { it.id != lastUserMessage.id }.dropLast(1)
                val persona = uiState.value.activePersona
                    ?: repository.getPersona(_selectedPersonaId.value)

                if (_isImageGenMode.value) {
                    runImageGenerationTurn(
                        conversationId = convId,
                        promptText = lastUserMessage.content,
                        inputImagePath = lastUserMessage.imagePath
                    )
                } else {
                    streamAssistantTurn(
                        conversationId = convId,
                        persona = persona,
                        historyBeforeTurn = historyBeforeTurn,
                        promptText = lastUserMessage.content,
                        imagePath = lastUserMessage.imagePath,
                        useSearchGrounding = _useSearchGrounding.value
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to regenerate response", e)
                _streamingStatus.value = StreamingStatus.Idle
                _errorBanner.value = e.localizedMessage ?: "Failed to regenerate response."
            }
        }
    }

    fun stopStreaming() {
        val currentStatus = _streamingStatus.value
        activeStreamJob?.cancel()
        activeStreamJob = null
        _streamingStatus.value = StreamingStatus.Idle

        if (currentStatus is StreamingStatus.Streaming && currentStatus.text.isNotBlank()) {
            val convId = _activeConversationId.value ?: return
            viewModelScope.launch {
                repository.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = MessageEntity.ROLE_MODEL,
                        content = currentStatus.text,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun runImageGenerationTurn(
        conversationId: String,
        promptText: String,
        inputImagePath: String?
    ) {
        _streamingStatus.value = StreamingStatus.GeneratingImage
        val copy = localizedCopy(uiState.value.uiLanguage)
        when (val result = repository.generateOrEditImage(promptText, inputImagePath)) {
            is ImageGenResult.Success -> {
                _streamingStatus.value = StreamingStatus.Idle
                repository.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = MessageEntity.ROLE_MODEL,
                        content = copy.generatedImageCaption,
                        imagePath = result.savedImagePath,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            is ImageGenResult.Error -> {
                _streamingStatus.value = StreamingStatus.Idle
                repository.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = MessageEntity.ROLE_MODEL,
                        content = result.message,
                        isError = true,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun streamAssistantTurn(
        conversationId: String,
        persona: PersonaEntity,
        historyBeforeTurn: List<MessageEntity>,
        promptText: String,
        imagePath: String?,
        useSearchGrounding: Boolean
    ) {
        _streamingStatus.value = StreamingStatus.WaitingForFirstToken
        try {
            repository.streamChatResponse(
                persona = persona,
                priorMessages = historyBeforeTurn,
                promptText = promptText,
                imagePath = imagePath,
                useSearchGrounding = useSearchGrounding
            ).collect { event ->
                when (event) {
                    is AiStreamEvent.Chunk -> {
                        _streamingStatus.value = StreamingStatus.Streaming(event.accumulatedText)
                    }
                    is AiStreamEvent.Completed -> {
                        _streamingStatus.value = StreamingStatus.Idle
                        repository.insertMessage(
                            MessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = conversationId,
                                role = MessageEntity.ROLE_MODEL,
                                content = event.fullText,
                                groundingSourcesJson = DostRepository.serializeSources(event.sources),
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    is AiStreamEvent.Timeout -> {
                        _streamingStatus.value = StreamingStatus.Idle
                        val copy = localizedCopy(uiState.value.uiLanguage)
                        repository.insertMessage(
                            MessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = conversationId,
                                role = MessageEntity.ROLE_MODEL,
                                content = copy.timeoutError,
                                isError = true,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    is AiStreamEvent.Error -> {
                        _streamingStatus.value = StreamingStatus.Idle
                        repository.insertMessage(
                            MessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = conversationId,
                                role = MessageEntity.ROLE_MODEL,
                                content = event.message,
                                isError = true,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            if (_streamingStatus.value != StreamingStatus.Idle) {
                _streamingStatus.value = StreamingStatus.Idle
            }
        }
    }

    private fun buildConversationTitle(prompt: String, fallback: String): String {
        val cleaned = prompt.replace("\n", " ").trim()
        if (cleaned.isBlank()) return "$fallback • Photo"
        return if (cleaned.length <= 42) cleaned else cleaned.take(39).trimEnd() + "..."
    }

    companion object {
        private const val TAG = "DostViewModel"
        private const val MAX_RECORDING_SECONDS = 120

        internal fun mergeTranscribedText(existing: String, transcript: String): String {
            val cleanedTranscript = transcript.trim()
            if (cleanedTranscript.isEmpty()) return existing
            if (existing.isBlank()) return cleanedTranscript
            return if (existing.last().isWhitespace()) {
                "$existing$cleanedTranscript"
            } else {
                "${existing.trimEnd()} $cleanedTranscript"
            }
        }
    }
}
