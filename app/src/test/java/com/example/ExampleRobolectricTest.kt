package com.example

import android.Manifest
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.audio.TextToSpeechPlayer
import com.example.data.audio.TtsPlaybackState
import com.example.data.export.ChatExportFormat
import com.example.data.export.ChatHistoryExporter
import com.example.data.local.ConversationEntity
import com.example.data.local.DostDatabase
import com.example.data.local.MessageEntity
import com.example.data.local.PersonaEntity
import com.example.data.local.UiLanguage
import com.example.data.repository.DostRepository
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.ComposerBar
import com.example.ui.components.ConversationDrawerContent
import com.example.ui.screens.DostChatScreen
import com.example.ui.state.DostUiState
import com.example.ui.state.StreamingStatus
import com.example.ui.state.localizedCopy
import com.example.ui.theme.DostAiTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.DostViewModel
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: DostDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DostDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app_name matches Dost AI`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Dost AI", appName)
    }

    @Test
    fun `room persists conversations and ordered messages`() = runBlocking {
        val dao = db.dostDao()
        dao.insertPersonasIgnore(DostDatabase.defaultPersonas)

        val conv = ConversationEntity(
            id = "conv-1",
            title = "Kotlin Tips",
            personaId = "dost_coder",
            createdAt = 100L,
            updatedAt = 200L
        )
        dao.upsertConversation(conv)

        dao.insertMessage(
            MessageEntity(
                id = "m1",
                conversationId = "conv-1",
                role = MessageEntity.ROLE_USER,
                content = "Show me a Kotlin data class",
                createdAt = 101L
            )
        )
        dao.insertMessage(
            MessageEntity(
                id = "m2",
                conversationId = "conv-1",
                role = MessageEntity.ROLE_MODEL,
                content = "```kotlin\ndata class User(val name: String)\n```",
                createdAt = 102L
            )
        )

        val savedConversations = dao.observeConversations().first()
        assertEquals(1, savedConversations.size)
        assertEquals("Kotlin Tips", savedConversations.first().title)

        val savedMessages = dao.getMessagesForConversation("conv-1")
        assertEquals(2, savedMessages.size)
        assertEquals(MessageEntity.ROLE_USER, savedMessages[0].role)
        assertEquals(MessageEntity.ROLE_MODEL, savedMessages[1].role)
    }

    @Test
    fun `copy to clipboard button copies AI response and code block`() {
        val aiMessage = MessageEntity(
            id = "ai-1",
            conversationId = "c1",
            role = MessageEntity.ROLE_MODEL,
            content = "Here is sample code:\n```kotlin\nprintln(\"Salam\")\n```",
            createdAt = 1000L
        )

        composeRule.setContent {
            DostAiTheme {
                ChatMessageBubble(
                    message = aiMessage,
                    persona = DostDatabase.defaultPersonas.first(),
                    copy = localizedCopy(UiLanguage.EN),
                    isLastModelMessage = true,
                    isGenerating = false,
                    onRegenerate = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("copy_message_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedFull = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals(aiMessage.content, copiedFull)

        composeRule.onNodeWithTag("copy_code_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val copiedCode = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("println(\"Salam\")", copiedCode)
    }

    @Test
    fun `chat top bar theme picker switches between light dark and system default`() {
        val selectedThemes = mutableListOf<ThemePreference>()
        var currentTheme = ThemePreference.DARK

        composeRule.setContent {
            DostAiTheme(themePreference = currentTheme) {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        themePreference = currentTheme,
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {
                        currentTheme = it
                        selectedThemes += it
                    },
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_theme_picker_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("theme_option_light").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("chat_theme_picker_button").performClick()
        composeRule.onNodeWithTag("theme_option_system").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("chat_theme_picker_button").performClick()
        composeRule.onNodeWithTag("theme_option_dark").assertIsDisplayed().performClick()

        assertEquals(
            listOf(ThemePreference.LIGHT, ThemePreference.SYSTEM, ThemePreference.DARK),
            selectedThemes
        )
    }

    @Test
    fun `drawer theme picker switches between light dark and system default`() {
        val selectedThemes = mutableListOf<ThemePreference>()

        composeRule.setContent {
            DostAiTheme {
                ConversationDrawerContent(
                    conversations = emptyList(),
                    activeConversationId = null,
                    personas = DostDatabase.defaultPersonas,
                    themePreference = ThemePreference.DARK,
                    language = UiLanguage.EN,
                    copy = localizedCopy(UiLanguage.EN),
                    onNewChat = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onOpenStudio = {},
                    onSelectTheme = { selectedThemes += it },
                    onToggleLanguage = {}
                )
            }
        }

        composeRule.onNodeWithTag("drawer_theme_light").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("drawer_theme_system").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("drawer_theme_dark").assertIsDisplayed().performClick()

        assertEquals(
            listOf(ThemePreference.LIGHT, ThemePreference.SYSTEM, ThemePreference.DARK),
            selectedThemes
        )
    }

    @Test
    fun `chat list displays AI is typing indicator while waiting for response`() {
        val userMsg = MessageEntity(
            id = "u-1",
            conversationId = "c-1",
            role = MessageEntity.ROLE_USER,
            content = "Hello Dost",
            createdAt = 1000L
        )

        composeRule.setContent {
            DostAiTheme {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        messages = listOf(userMsg),
                        streamingStatus = StreamingStatus.WaitingForFirstToken,
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {},
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("message_history_list").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_typing_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_typing_label").assertIsDisplayed().assertTextEquals("AI is typing...")
    }

    @Test
    fun `chat list displays streaming typing pill alongside incoming tokens`() {
        composeRule.setContent {
            DostAiTheme {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        messages = emptyList(),
                        streamingStatus = StreamingStatus.Streaming("Working on your answer..."),
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {},
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("streaming_typing_pill").assertIsDisplayed()
        composeRule.onNodeWithText("Working on your answer...").assertIsDisplayed()
    }

    @Test
    fun `room deletes all messages for cleared conversation only`() = runBlocking {
        val dao = db.dostDao()
        dao.insertPersonasIgnore(DostDatabase.defaultPersonas)
        dao.upsertConversation(ConversationEntity("c1", "First", "dost_companion", 1L, 1L))
        dao.upsertConversation(ConversationEntity("c2", "Second", "dost_companion", 2L, 2L))
        dao.insertMessage(MessageEntity("m1", "c1", MessageEntity.ROLE_USER, "Hi 1", createdAt = 10L))
        dao.insertMessage(MessageEntity("m2", "c1", MessageEntity.ROLE_MODEL, "Hello 1", createdAt = 11L))
        dao.insertMessage(MessageEntity("m3", "c2", MessageEntity.ROLE_USER, "Hi 2", createdAt = 12L))

        dao.deleteMessagesForConversation("c1")
        dao.deleteConversation("c1")

        assertEquals(0, dao.getMessagesForConversation("c1").size)
        assertEquals(1, dao.getMessagesForConversation("c2").size)
        assertEquals(listOf("c2"), dao.observeConversations().first().map { it.id })
    }

    @Test
    fun `clear chat history menu option confirms and clears messages from UI`() {
        val initialMessages = listOf(
            MessageEntity("u1", "c1", MessageEntity.ROLE_USER, "Question to clear", createdAt = 100L),
            MessageEntity("a1", "c1", MessageEntity.ROLE_MODEL, "Answer to clear", createdAt = 101L)
        )
        var clearedCalled = false

        composeRule.setContent {
            var messages by remember { mutableStateOf(initialMessages) }
            var activeConvId by remember { mutableStateOf<String?>("c1") }

            DostAiTheme {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        activeConversationId = activeConvId,
                        messages = messages,
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onClearChatHistory = {
                        clearedCalled = true
                        messages = emptyList()
                        activeConvId = null
                    },
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {},
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("message_history_list").performScrollToIndex(0)
        composeRule.onNodeWithText("Question to clear").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_options_menu_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("clear_chat_history_menu_item").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("clear_chat_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_clear_chat_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(true, clearedCalled)
        composeRule.onNodeWithText("Question to clear").assertDoesNotExist()
    }

    @Test
    fun `chat history exporter produces valid json and readable plain text backups`() {
        val persona = DostDatabase.defaultPersonas.first()
        val conversation = ConversationEntity(
            id = "conv-exp",
            title = "Kotlin Architecture",
            personaId = persona.id,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_050_000L
        )
        val messages = listOf(
            MessageEntity(
                id = "m1",
                conversationId = conversation.id,
                role = MessageEntity.ROLE_USER,
                content = "How do I use Room?",
                createdAt = 1_700_000_010_000L
            ),
            MessageEntity(
                id = "m2",
                conversationId = conversation.id,
                role = MessageEntity.ROLE_MODEL,
                content = "Use a @Dao interface with Flow queries.",
                createdAt = 1_700_000_020_000L
            )
        )

        val jsonText = ChatHistoryExporter.formatAsJson(conversation, persona, messages, 1_700_000_100_000L)
        val parsed = JSONObject(jsonText)
        assertEquals("Dost AI", parsed.getString("app"))
        assertEquals("Kotlin Architecture", parsed.getString("title"))
        assertEquals(2, parsed.getInt("messageCount"))
        assertEquals("How do I use Room?", parsed.getJSONArray("messages").getJSONObject(0).getString("content"))

        val plainText = ChatHistoryExporter.formatAsPlainText(conversation, persona, messages, 1_700_000_100_000L)
        assertTrue(plainText.contains("Title: Kotlin Architecture"))
        assertTrue(plainText.contains("You:"))
        assertTrue(plainText.contains("How do I use Room?"))
        assertTrue(plainText.contains("Use a @Dao interface with Flow queries."))

        val jsonFile = ChatHistoryExporter.buildSuggestedFileName(conversation.title, ChatExportFormat.JSON, 1_700_000_100_000L)
        val txtFile = ChatHistoryExporter.buildSuggestedFileName(conversation.title, ChatExportFormat.TEXT, 1_700_000_100_000L)
        assertTrue(jsonFile.startsWith("dost-ai-kotlin-architecture-") && jsonFile.endsWith(".json"))
        assertTrue(txtFile.startsWith("dost-ai-kotlin-architecture-") && txtFile.endsWith(".txt"))
    }

    @Test
    fun `export chat menu item opens json and plain text format chooser dialog`() {
        val sampleMessages = listOf(
            MessageEntity("u1", "c1", MessageEntity.ROLE_USER, "Save my chat", createdAt = 100L),
            MessageEntity("a1", "c1", MessageEntity.ROLE_MODEL, "Ready to back up!", createdAt = 101L)
        )

        composeRule.setContent {
            DostAiTheme {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        activeConversationId = "c1",
                        messages = sampleMessages,
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onClearChatHistory = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {},
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_options_menu_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("export_chat_history_menu_item").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("export_chat_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("export_format_json_button").assertIsDisplayed()
        composeRule.onNodeWithTag("export_format_txt_button").assertIsDisplayed()
    }

    @Test
    fun `each AI message in chat list displays copy icon button and copies response`() {
        val chatMessages = listOf(
            MessageEntity("u1", "c1", MessageEntity.ROLE_USER, "First question", createdAt = 100L),
            MessageEntity("a1", "c1", MessageEntity.ROLE_MODEL, "First AI answer", createdAt = 101L),
            MessageEntity("u2", "c1", MessageEntity.ROLE_USER, "Second question", createdAt = 102L),
            MessageEntity("a2", "c1", MessageEntity.ROLE_MODEL, "Second AI answer", createdAt = 103L)
        )

        composeRule.setContent {
            DostAiTheme {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        activeConversationId = "c1",
                        messages = chatMessages,
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onClearChatHistory = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {},
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("message_history_list").performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("copy_ai_icon_button")[0].assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("First AI answer", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        composeRule.onNodeWithTag("message_history_list").performScrollToIndex(3)
        composeRule.waitForIdle()
        val visibleCopyButtons = composeRule.onAllNodesWithTag("copy_ai_icon_button")
        val secondButtonIndex = visibleCopyButtons.fetchSemanticsNodes().lastIndex
        visibleCopyButtons[secondButtonIndex].assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("Second AI answer", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `sanitizeHistoryMessages drops failed turns and enforces alternating user model history`() {
        val raw = listOf(
            MessageEntity("m0", "c1", MessageEntity.ROLE_MODEL, "Stray greeting", createdAt = 1L),
            MessageEntity("m1", "c1", MessageEntity.ROLE_USER, "Hello", createdAt = 2L),
            MessageEntity("m2", "c1", MessageEntity.ROLE_MODEL, "Firebase App Check token is invalid.", isError = true, createdAt = 3L),
            MessageEntity("m3", "c1", MessageEntity.ROLE_USER, "How are you", createdAt = 4L),
            MessageEntity("m4", "c1", MessageEntity.ROLE_MODEL, "I am great!", createdAt = 5L),
            MessageEntity("m5", "c1", MessageEntity.ROLE_USER, "Write Kotlin code", createdAt = 6L),
            MessageEntity("m6", "c1", MessageEntity.ROLE_MODEL, "Request timed out", isError = true, createdAt = 7L)
        )

        val sanitized = DostRepository.sanitizeHistoryMessages(raw)
        assertEquals(2, sanitized.size)
        assertEquals("m3", sanitized[0].id)
        assertEquals(MessageEntity.ROLE_USER, sanitized[0].role)
        assertEquals("m4", sanitized[1].id)
        assertEquals(MessageEntity.ROLE_MODEL, sanitized[1].role)
    }

    @Test
    fun `formatAiErrorMessage unwraps generic UnknownException and network root causes`() {
        val genericWrapped = RuntimeException("Something unexpected happened.", IllegalStateException("App Check attestation rejected (HTTP 403)"))
        assertEquals(
            "App Check attestation rejected (HTTP 403)",
            DostRepository.formatAiErrorMessage(genericWrapped)
        )

        val dnsWrapped = RuntimeException("Something unexpected happened.", UnknownHostException("firebasevertexai.googleapis.com"))
        assertTrue(DostRepository.formatAiErrorMessage(dnsWrapped).contains("DNS lookup failed"))

        val timeoutWrapped = RuntimeException("Something unexpected happened.", SocketTimeoutException("Read timed out"))
        assertTrue(DostRepository.formatAiErrorMessage(timeoutWrapped).contains("Network connection timed out"))
    }

    @Test
    fun `microphone button in input area records voice, shows timer and transcribing state`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        var toggleCount = 0
        var cancelCount = 0

        composeRule.setContent {
            var isRecording by remember { mutableStateOf(false) }
            var isTranscribing by remember { mutableStateOf(false) }
            var text by remember { mutableStateOf("") }

            DostAiTheme {
                ComposerBar(
                    text = text,
                    pendingImagePath = null,
                    isAttachingImage = false,
                    isGenerating = false,
                    useSearchGrounding = false,
                    isImageGenMode = false,
                    isRecordingAudio = isRecording,
                    recordingSeconds = 4,
                    recordingAmplitude = 0.65f,
                    isTranscribingAudio = isTranscribing,
                    copy = localizedCopy(UiLanguage.EN),
                    onTextChange = { text = it },
                    onPickPhoto = {},
                    onRemovePhoto = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {
                        toggleCount++
                        if (!isRecording) {
                            isRecording = true
                        } else {
                            isRecording = false
                            isTranscribing = true
                        }
                    },
                    onCancelVoiceRecording = {
                        cancelCount++
                        isRecording = false
                        isTranscribing = false
                    },
                    onMicPermissionDenied = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        composeRule.onNodeWithTag("voice_record_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, toggleCount)
        composeRule.onNodeWithTag("recording_status_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_recording_timer").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_voice_recording_button").assertIsDisplayed()

        composeRule.onNodeWithTag("voice_record_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, toggleCount)
        composeRule.onNodeWithTag("transcribing_status_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_transcribing_indicator").assertIsDisplayed()

        composeRule.onNodeWithTag("cancel_voice_recording_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, cancelCount)
    }

    @Test
    fun `mergeTranscribedText appends speech transcript cleanly to composer input`() {
        assertEquals("Hello world", DostViewModel.mergeTranscribedText("", "  Hello world  "))
        assertEquals("Draft: Hello world", DostViewModel.mergeTranscribedText("Draft:", "Hello world"))
        assertEquals("Draft:\nHello world", DostViewModel.mergeTranscribedText("Draft:\n", "Hello world"))
        assertEquals("Keep me", DostViewModel.mergeTranscribedText("Keep me", "   "))
    }

    @Test
    fun `settings menu opens custom system prompt sheet with persona presets and saves custom behavior`() {
        var savedId: String? = "UNSET"
        var savedName = ""
        var savedPrompt = ""

        composeRule.setContent {
            DostAiTheme {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onSavePersona = { existingId, name, _, systemPrompt, _, _, _, _ ->
                        savedId = existingId
                        savedName = name
                        savedPrompt = systemPrompt
                    },
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {},
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_options_menu_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("open_settings_menu_item").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("preset_creative_coder").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("preset_tutor").assertIsDisplayed()
        composeRule.onNodeWithTag("preset_friendly_assistant").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_system_prompt_input")
            .performScrollTo()
            .performTextReplacement("Respond like a concise Rust & Kotlin systems mentor.")
        composeRule.onNodeWithTag("settings_save_as_new_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(null, savedId)
        assertEquals("Creative Coder", savedName)
        assertEquals("Respond like a concise Rust & Kotlin systems mentor.", savedPrompt)
    }

    @Test
    fun `room persists custom persona with user-defined system prompt`() = runBlocking {
        val dao = db.dostDao()
        val custom = PersonaEntity(
            id = "custom_creative_coder",
            name = "Creative Coder",
            taglineTm = "Kodçy",
            taglineEn = "Creative coding partner",
            systemPrompt = "Always show clean idiomatic Kotlin examples.",
            temperature = 0.5f,
            iconKey = "code",
            accentIndex = 1,
            isBuiltIn = false,
            createdAt = 500L
        )
        dao.upsertPersona(custom)

        val fetched = dao.getPersonaById("custom_creative_coder")
        assertEquals("Creative Coder", fetched?.name)
        assertEquals("Always show clean idiomatic Kotlin examples.", fetched?.systemPrompt)
        assertEquals(false, fetched?.isBuiltIn)
    }

    @Test
    fun `play pause button appears on each AI message and toggles listen pause resume states`() {
        val messages = listOf(
            MessageEntity("u1", "c1", MessageEntity.ROLE_USER, "Question", createdAt = 1L),
            MessageEntity("a1", "c1", MessageEntity.ROLE_MODEL, "First AI response", createdAt = 2L),
            MessageEntity("u2", "c1", MessageEntity.ROLE_USER, "Next question", createdAt = 3L),
            MessageEntity("a2", "c1", MessageEntity.ROLE_MODEL, "Second AI response", createdAt = 4L)
        )
        val clickedIds = mutableListOf<String>()

        composeRule.setContent {
            var ttsState by remember { mutableStateOf(TtsPlaybackState()) }

            DostAiTheme {
                DostChatScreen(
                    uiState = DostUiState(
                        personas = DostDatabase.defaultPersonas,
                        activePersona = DostDatabase.defaultPersonas.first(),
                        activeConversationId = "c1",
                        messages = messages,
                        ttsState = ttsState,
                        uiLanguage = UiLanguage.EN
                    ),
                    copy = localizedCopy(UiLanguage.EN),
                    onComposerTextChanged = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRegenerate = {},
                    onToggleSpeakMessage = { msg ->
                        clickedIds += msg.id
                        ttsState = when {
                            ttsState.activeMessageId == msg.id && ttsState.isPlaying ->
                                TtsPlaybackState(activeMessageId = msg.id, isPlaying = false, isPaused = true)
                            ttsState.activeMessageId == msg.id && ttsState.isPaused ->
                                TtsPlaybackState(activeMessageId = msg.id, isPlaying = true, isPaused = false)
                            else ->
                                TtsPlaybackState(activeMessageId = msg.id, isPlaying = true, isPaused = false)
                        }
                    },
                    onAttachImageUri = {},
                    onRemovePendingImage = {},
                    onToggleSearchGrounding = {},
                    onToggleImageGenMode = {},
                    onToggleVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onShowErrorMessage = {},
                    onNewChat = {},
                    onSelectConversation = {},
                    onDeleteConversation = {},
                    onSelectPersona = {},
                    onOpenPersonaStudio = {},
                    onSelectStarterPrompt = {},
                    onSelectTheme = {},
                    onToggleLanguage = {},
                    onDismissErrorBanner = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("message_history_list").performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("tts_play_pause_button")[0].assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pause").assertIsDisplayed()

        // Pause playback
        composeRule.onAllNodesWithTag("tts_play_pause_button")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()

        // Resume playback
        composeRule.onAllNodesWithTag("tts_play_pause_button")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pause").assertIsDisplayed()

        // Second AI message also has its own play button
        composeRule.onNodeWithTag("message_history_list").performScrollToIndex(3)
        composeRule.waitForIdle()
        val visibleButtons = composeRule.onAllNodesWithTag("tts_play_pause_button")
        visibleButtons[visibleButtons.fetchSemanticsNodes().lastIndex].assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("a1", "a1", "a1", "a2"), clickedIds)
    }

    @Test
    fun `text to speech helpers sanitize markdown and split chunks cleanly`() {
        val md = "## Title\n**Hello** [friend](https://example.com) `code`!\n```kotlin\nval x = 1\n```"
        val cleaned = TextToSpeechPlayer.sanitizeMarkdownForSpeech(md)
        assertEquals("Title\nHello friend code!\n\nval x = 1", cleaned)

        val sample = "Hello world from Dost AI"
        assertEquals(6, TextToSpeechPlayer.snapToWordBoundary(sample, 8))
        val endIdx = TextToSpeechPlayer.computeChunkEndIndex("First sentence. Second sentence is longer.", 0, 22)
        assertEquals(15, endIdx)
    }
}
