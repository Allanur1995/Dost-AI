package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.export.ChatExportFormat
import com.example.data.export.ChatHistoryExporter
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PersonaEntity
import com.example.ui.components.AiSettingsSheet
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.ComposerBar
import com.example.ui.components.ConversationDrawerContent
import com.example.ui.components.ExportChatDialog
import com.example.ui.components.PersonaVisuals
import com.example.ui.components.StreamingModelBubble
import com.example.ui.components.WelcomeHeroView
import com.example.ui.state.DostUiState
import com.example.ui.state.LocalizedCopy
import com.example.ui.state.StreamingStatus
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.launch

@Composable
fun DostChatScreen(
    uiState: DostUiState,
    copy: LocalizedCopy,
    onComposerTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopStreaming: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleSpeakMessage: (MessageEntity) -> Unit = {},
    onAttachImageUri: (android.net.Uri) -> Unit,
    onRemovePendingImage: () -> Unit,
    onToggleSearchGrounding: () -> Unit,
    onToggleImageGenMode: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit = {},
    onShowErrorMessage: (String) -> Unit,
    onNewChat: () -> Unit,
    onClearChatHistory: () -> Unit = {},
    onExportChatToUri: suspend (android.net.Uri, ChatExportFormat) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    onSelectConversation: (ConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectPersona: (PersonaEntity) -> Unit,
    onSavePersona: (
        existingId: String?,
        name: String,
        tagline: String,
        systemPrompt: String,
        modelName: String,
        temperature: Float,
        iconKey: String,
        accentIndex: Int
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onOpenPersonaStudio: () -> Unit,
    onSelectStarterPrompt: (String) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleLanguage: () -> Unit,
    onDismissErrorBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAttachImageUri(uri)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ChatExportFormat.JSON.mimeType)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = onExportChatToUri(uri, ChatExportFormat.JSON)
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) copy.exportSuccess else copy.exportFailed
                )
            }
        }
    }

    val exportTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ChatExportFormat.TEXT.mimeType)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = onExportChatToUri(uri, ChatExportFormat.TEXT)
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) copy.exportSuccess else copy.exportFailed
                )
            }
        }
    }

    val launchExport: (ChatExportFormat) -> Unit = { format ->
        val activeConv = uiState.conversations.firstOrNull { it.id == uiState.activeConversationId }
        val suggestedName = ChatHistoryExporter.buildSuggestedFileName(
            conversationTitle = activeConv?.title ?: uiState.activePersona?.name,
            format = format
        )
        when (format) {
            ChatExportFormat.JSON -> exportJsonLauncher.launch(suggestedName)
            ChatExportFormat.TEXT -> exportTextLauncher.launch(suggestedName)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        val drawerBody: @Composable () -> Unit = {
            ConversationDrawerContent(
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversationId,
                personas = uiState.personas,
                themePreference = uiState.themePreference,
                language = uiState.uiLanguage,
                copy = copy,
                onNewChat = {
                    onNewChat()
                    if (!isWideScreen) scope.launch { drawerState.close() }
                },
                onSelectConversation = { conv ->
                    onSelectConversation(conv)
                    if (!isWideScreen) scope.launch { drawerState.close() }
                },
                onDeleteConversation = onDeleteConversation,
                onOpenStudio = {
                    onOpenPersonaStudio()
                    if (!isWideScreen) scope.launch { drawerState.close() }
                },
                onSelectTheme = onSelectTheme,
                onToggleLanguage = onToggleLanguage
            )
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        drawerBody()
                    }
                }
            ) {
                ChatMainPane(
                    uiState = uiState,
                    copy = copy,
                    showMenuButton = false,
                    onOpenDrawer = {},
                    onComposerTextChanged = onComposerTextChanged,
                    onSendMessage = onSendMessage,
                    onStopStreaming = onStopStreaming,
                    onRegenerate = onRegenerate,
                    onToggleSpeakMessage = onToggleSpeakMessage,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemovePendingImage = onRemovePendingImage,
                    onToggleSearchGrounding = onToggleSearchGrounding,
                    onToggleImageGenMode = onToggleImageGenMode,
                    onToggleVoiceRecording = onToggleVoiceRecording,
                    onCancelVoiceRecording = onCancelVoiceRecording,
                    onShowErrorMessage = onShowErrorMessage,
                    onNewChat = onNewChat,
                    onClearChatHistory = onClearChatHistory,
                    onExportChat = launchExport,
                    snackbarHostState = snackbarHostState,
                    onSelectPersona = onSelectPersona,
                    onSavePersona = onSavePersona,
                    onOpenPersonaStudio = onOpenPersonaStudio,
                    onSelectStarterPrompt = onSelectStarterPrompt,
                    onSelectTheme = onSelectTheme,
                    onToggleLanguage = onToggleLanguage,
                    onDismissErrorBanner = onDismissErrorBanner
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        drawerBody()
                    }
                }
            ) {
                ChatMainPane(
                    uiState = uiState,
                    copy = copy,
                    showMenuButton = true,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onComposerTextChanged = onComposerTextChanged,
                    onSendMessage = onSendMessage,
                    onStopStreaming = onStopStreaming,
                    onRegenerate = onRegenerate,
                    onToggleSpeakMessage = onToggleSpeakMessage,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemovePendingImage = onRemovePendingImage,
                    onToggleSearchGrounding = onToggleSearchGrounding,
                    onToggleImageGenMode = onToggleImageGenMode,
                    onToggleVoiceRecording = onToggleVoiceRecording,
                    onCancelVoiceRecording = onCancelVoiceRecording,
                    onShowErrorMessage = onShowErrorMessage,
                    onNewChat = onNewChat,
                    onClearChatHistory = onClearChatHistory,
                    onExportChat = launchExport,
                    snackbarHostState = snackbarHostState,
                    onSelectPersona = onSelectPersona,
                    onSavePersona = onSavePersona,
                    onOpenPersonaStudio = onOpenPersonaStudio,
                    onSelectStarterPrompt = onSelectStarterPrompt,
                    onSelectTheme = onSelectTheme,
                    onToggleLanguage = onToggleLanguage,
                    onDismissErrorBanner = onDismissErrorBanner
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatMainPane(
    uiState: DostUiState,
    copy: LocalizedCopy,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onComposerTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopStreaming: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleSpeakMessage: (MessageEntity) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePendingImage: () -> Unit,
    onToggleSearchGrounding: () -> Unit,
    onToggleImageGenMode: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onShowErrorMessage: (String) -> Unit,
    onNewChat: () -> Unit,
    onClearChatHistory: () -> Unit,
    onExportChat: (ChatExportFormat) -> Unit,
    snackbarHostState: SnackbarHostState,
    onSelectPersona: (PersonaEntity) -> Unit,
    onSavePersona: (
        existingId: String?,
        name: String,
        tagline: String,
        systemPrompt: String,
        modelName: String,
        temperature: Float,
        iconKey: String,
        accentIndex: Int
    ) -> Unit,
    onOpenPersonaStudio: () -> Unit,
    onSelectStarterPrompt: (String) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleLanguage: () -> Unit,
    onDismissErrorBanner: () -> Unit
) {
    val activePersona = uiState.activePersona
    val accentColor = PersonaVisuals.accentColorFor(activePersona?.accentIndex ?: 0)
    val glowPrimary = MaterialTheme.colorScheme.primary
    val glowSecondary = MaterialTheme.colorScheme.secondary

    var showPersonaDropdown by remember { mutableStateOf(false) }
    var showThemeDropdown by remember { mutableStateOf(false) }
    var showChatOptionsDropdown by remember { mutableStateOf(false) }
    var showAiSettingsSheet by remember { mutableStateOf(false) }
    var showClearChatConfirmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val hasMessagesToExport = uiState.messages.isNotEmpty()
    val hasChatHistory = hasMessagesToExport ||
        uiState.activeConversationId != null ||
        uiState.isGenerating
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val showScrollToBottom by remember {
        derivedStateOf { listState.canScrollForward && uiState.messages.isNotEmpty() }
    }

    val streamingText = (uiState.streamingStatus as? StreamingStatus.Streaming)?.text
    val scrollKey = uiState.messages.size to (streamingText?.length ?: 0)

    LaunchedEffect(scrollKey, uiState.isGenerating) {
        val totalItems = uiState.messages.size + if (uiState.isGenerating) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.12f),
                        radius = size.maxDimension * 0.45f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowSecondary.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.78f),
                        radius = size.maxDimension * 0.4f
                    )
                )
            },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer)
                            )
                        }
                    }
                },
                title = {
                    Box {
                        Surface(
                            onClick = { showPersonaDropdown = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("active_persona_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = PersonaVisuals.iconFor(activePersona?.iconKey ?: "spark"),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = activePersona?.name ?: copy.appTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPersonaDropdown,
                            onDismissRequest = { showPersonaDropdown = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            uiState.personas.forEach { persona ->
                                val pAccent = PersonaVisuals.accentColorFor(persona.accentIndex)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = persona.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = PersonaVisuals.iconFor(persona.iconKey),
                                            contentDescription = null,
                                            tint = pAccent
                                        )
                                    },
                                    onClick = {
                                        showPersonaDropdown = false
                                        onSelectPersona(persona)
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = copy.personaStudio,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showPersonaDropdown = false
                                    onOpenPersonaStudio()
                                }
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        val currentThemeIcon = when (uiState.themePreference) {
                            ThemePreference.LIGHT -> Icons.Rounded.LightMode
                            ThemePreference.DARK -> Icons.Rounded.DarkMode
                            ThemePreference.SYSTEM -> Icons.Rounded.BrightnessAuto
                        }
                        IconButton(
                            onClick = { showThemeDropdown = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_theme_picker_button")
                        ) {
                            Icon(
                                imageVector = currentThemeIcon,
                                contentDescription = stringResource(R.string.cd_theme_picker),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showThemeDropdown,
                            onDismissRequest = { showThemeDropdown = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.testTag("theme_picker_menu")
                        ) {
                            val themeOptions = listOf(
                                Triple(ThemePreference.LIGHT, copy.themeLight, Icons.Rounded.LightMode),
                                Triple(ThemePreference.DARK, copy.themeDark, Icons.Rounded.DarkMode),
                                Triple(ThemePreference.SYSTEM, copy.themeSystem, Icons.Rounded.BrightnessAuto)
                            )
                            themeOptions.forEach { (pref, label, icon) ->
                                val selected = uiState.themePreference == pref
                                val optionTag = when (pref) {
                                    ThemePreference.LIGHT -> "theme_option_light"
                                    ThemePreference.DARK -> "theme_option_dark"
                                    ThemePreference.SYSTEM -> "theme_option_system"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        showThemeDropdown = false
                                        onSelectTheme(pref)
                                    },
                                    modifier = Modifier.testTag(optionTag)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddComment,
                            contentDescription = stringResource(R.string.cd_new_chat)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showChatOptionsDropdown = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_options_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.cd_chat_options)
                            )
                        }

                        DropdownMenu(
                            expanded = showChatOptionsDropdown,
                            onDismissRequest = { showChatOptionsDropdown = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.testTag("chat_options_menu")
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = copy.settingsMenuItem,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showChatOptionsDropdown = false
                                    showAiSettingsSheet = true
                                },
                                modifier = Modifier.testTag("open_settings_menu_item")
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = copy.exportChatHistory,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (hasMessagesToExport) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = if (hasMessagesToExport) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                },
                                enabled = hasMessagesToExport,
                                onClick = {
                                    showChatOptionsDropdown = false
                                    showExportDialog = true
                                },
                                modifier = Modifier.testTag("export_chat_history_menu_item")
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = copy.clearChatHistory,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (hasChatHistory) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = null,
                                        tint = if (hasChatHistory) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                },
                                enabled = hasChatHistory,
                                onClick = {
                                    showChatOptionsDropdown = false
                                    showClearChatConfirmDialog = true
                                },
                                modifier = Modifier.testTag("clear_chat_history_menu_item")
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!uiState.errorBanner.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDismissErrorBanner,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        WelcomeHeroView(
                            activePersona = activePersona,
                            personas = uiState.personas,
                            language = uiState.uiLanguage,
                            copy = copy,
                            onSelectPersona = onSelectPersona,
                            onOpenStudio = onOpenPersonaStudio,
                            onSelectStarterPrompt = onSelectStarterPrompt
                        )
                    }
                } else {
                    val lastModelId = remember(uiState.messages) {
                        uiState.messages.lastOrNull { it.role == MessageEntity.ROLE_MODEL }?.id
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize()
                            .testTag("message_history_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { _, msg -> msg.id }
                        ) { _, msg ->
                            val isSpeaking = uiState.ttsState.activeMessageId == msg.id && uiState.ttsState.isPlaying
                            val isSpeechPaused = uiState.ttsState.activeMessageId == msg.id && uiState.ttsState.isPaused
                            ChatMessageBubble(
                                message = msg,
                                persona = activePersona,
                                copy = copy,
                                isLastModelMessage = msg.id == lastModelId,
                                isGenerating = uiState.isGenerating,
                                onRegenerate = onRegenerate,
                                isSpeaking = isSpeaking,
                                isSpeechPaused = isSpeechPaused,
                                onToggleSpeak = { onToggleSpeakMessage(msg) }
                            )
                        }

                        if (uiState.isGenerating) {
                            item(key = "streaming_indicator") {
                                StreamingModelBubble(
                                    persona = activePersona,
                                    streamingText = streamingText,
                                    isGeneratingImage = uiState.streamingStatus is StreamingStatus.GeneratingImage,
                                    thinkingLabel = copy.thinking,
                                    generatingImageLabel = copy.generatingImage,
                                    copy = copy
                                )
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val total = uiState.messages.size + if (uiState.isGenerating) 1 else 0
                                    if (total > 0) listState.animateScrollToItem(total - 1)
                                }
                            },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("scroll_to_bottom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_scroll_to_bottom)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.widthIn(max = 760.dp),
                contentAlignment = Alignment.Center
            ) {
                ComposerBar(
                    text = uiState.composerText,
                    pendingImagePath = uiState.pendingImagePath,
                    isAttachingImage = uiState.isAttachingImage,
                    isGenerating = uiState.isGenerating,
                    useSearchGrounding = uiState.useSearchGrounding,
                    isImageGenMode = uiState.isImageGenMode,
                    isRecordingAudio = uiState.isRecordingAudio,
                    recordingSeconds = uiState.recordingSeconds,
                    recordingAmplitude = uiState.recordingAmplitude,
                    isTranscribingAudio = uiState.isTranscribingAudio,
                    copy = copy,
                    onTextChange = onComposerTextChanged,
                    onPickPhoto = onPickPhoto,
                    onRemovePhoto = onRemovePendingImage,
                    onToggleSearchGrounding = onToggleSearchGrounding,
                    onToggleImageGenMode = onToggleImageGenMode,
                    onToggleVoiceRecording = onToggleVoiceRecording,
                    onCancelVoiceRecording = onCancelVoiceRecording,
                    onMicPermissionDenied = onShowErrorMessage,
                    onSend = onSendMessage,
                    onStop = onStopStreaming
                )
            }
        }
    }

    if (showClearChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(copy.clearChatConfirmTitle) },
            text = { Text(copy.clearChatConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatConfirmDialog = false
                        onClearChatHistory()
                    },
                    modifier = Modifier.testTag("confirm_clear_chat_button")
                ) {
                    Text(
                        text = copy.clearAction,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearChatConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_clear_chat_button")
                ) {
                    Text(copy.cancel)
                }
            },
            modifier = Modifier.testTag("clear_chat_confirm_dialog")
        )
    }

    if (showExportDialog) {
        ExportChatDialog(
            copy = copy,
            onSelectFormat = { format ->
                showExportDialog = false
                onExportChat(format)
            },
            onDismiss = { showExportDialog = false }
        )
    }

    if (showAiSettingsSheet) {
        AiSettingsSheet(
            activePersona = activePersona,
            themePreference = uiState.themePreference,
            language = uiState.uiLanguage,
            copy = copy,
            onSavePersona = { existingId, name, tagline, systemPrompt, modelName, temp, iconKey, accentIndex ->
                onSavePersona(existingId, name, tagline, systemPrompt, modelName, temp, iconKey, accentIndex)
                showAiSettingsSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar(copy.settingsSavedMessage)
                }
            },
            onSelectTheme = onSelectTheme,
            onToggleLanguage = onToggleLanguage,
            onOpenPersonaStudio = onOpenPersonaStudio,
            onDismiss = { showAiSettingsSheet = false }
        )
    }
}
