package com.example.ui.state

import com.example.data.audio.TtsPlaybackState
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PersonaEntity
import com.example.data.local.UiLanguage
import com.example.ui.theme.ThemePreference

sealed interface StreamingStatus {
    data object Idle : StreamingStatus
    data object WaitingForFirstToken : StreamingStatus
    data object GeneratingImage : StreamingStatus
    data class Streaming(val text: String) : StreamingStatus
}

sealed interface AppScreen {
    data object Chat : AppScreen
    data object PersonaStudio : AppScreen
}

data class DostUiState(
    val screen: AppScreen = AppScreen.Chat,
    val personas: List<PersonaEntity> = emptyList(),
    val activePersona: PersonaEntity? = null,
    val conversations: List<ConversationEntity> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<MessageEntity> = emptyList(),
    val streamingStatus: StreamingStatus = StreamingStatus.Idle,
    val composerText: String = "",
    val pendingImagePath: String? = null,
    val isAttachingImage: Boolean = false,
    val useSearchGrounding: Boolean = false,
    val isImageGenMode: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val recordingSeconds: Int = 0,
    val recordingAmplitude: Float = 0f,
    val isTranscribingAudio: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val uiLanguage: UiLanguage = UiLanguage.TM,
    val ttsState: TtsPlaybackState = TtsPlaybackState(),
    val errorBanner: String? = null
) {
    val isGenerating: Boolean
        get() = streamingStatus !is StreamingStatus.Idle
}
