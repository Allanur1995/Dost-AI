package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.ui.screens.DostChatScreen
import com.example.ui.screens.PersonaStudioScreen
import com.example.ui.state.AppScreen
import com.example.ui.state.localizedCopy
import com.example.ui.theme.DostAiTheme
import com.example.ui.viewmodel.DostViewModel

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before setContent, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val viewModel: DostViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val copy = localizedCopy(uiState.uiLanguage)

            DostAiTheme(themePreference = uiState.themePreference) {
                when (uiState.screen) {
                    AppScreen.Chat -> {
                        DostChatScreen(
                            uiState = uiState,
                            copy = copy,
                            onComposerTextChanged = viewModel::onComposerTextChanged,
                            onSendMessage = viewModel::sendMessage,
                            onStopStreaming = viewModel::stopStreaming,
                            onRegenerate = viewModel::regenerateLastResponse,
                            onToggleSpeakMessage = viewModel::toggleMessageSpeech,
                            onAttachImageUri = viewModel::attachImageFromPicker,
                            onRemovePendingImage = viewModel::removePendingImage,
                            onToggleSearchGrounding = viewModel::toggleSearchGrounding,
                            onToggleImageGenMode = viewModel::toggleImageGenMode,
                            onToggleVoiceRecording = viewModel::toggleVoiceRecording,
                            onCancelVoiceRecording = viewModel::cancelVoiceRecording,
                            onShowErrorMessage = viewModel::showErrorMessage,
                            onNewChat = { viewModel.startNewChat() },
                            onClearChatHistory = viewModel::clearCurrentChatHistory,
                            onExportChatToUri = viewModel::exportChatToUri,
                            onSelectConversation = viewModel::selectConversation,
                            onDeleteConversation = viewModel::deleteConversation,
                            onSelectPersona = viewModel::selectPersona,
                            onSavePersona = viewModel::saveCustomPersona,
                            onOpenPersonaStudio = viewModel::openPersonaStudio,
                            onSelectStarterPrompt = viewModel::sendStarterPrompt,
                            onSelectTheme = viewModel::setThemePreference,
                            onToggleLanguage = viewModel::toggleLanguage,
                            onDismissErrorBanner = viewModel::dismissErrorBanner
                        )
                    }
                    AppScreen.PersonaStudio -> {
                        PersonaStudioScreen(
                            personas = uiState.personas,
                            activePersona = uiState.activePersona,
                            language = uiState.uiLanguage,
                            copy = copy,
                            onSelectPersona = viewModel::selectPersona,
                            onSavePersona = viewModel::saveCustomPersona,
                            onDeletePersona = viewModel::deleteCustomPersona,
                            onBack = viewModel::closePersonaStudio
                        )
                    }
                }
            }
        }
    }
}
