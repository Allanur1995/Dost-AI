package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PersonaEntity
import com.example.data.local.UiLanguage
import com.example.ui.components.PersonaVisuals
import com.example.ui.components.personaPresetTemplates
import com.example.ui.state.LocalizedCopy
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaStudioScreen(
    personas: List<PersonaEntity>,
    activePersona: PersonaEntity?,
    language: UiLanguage,
    copy: LocalizedCopy,
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
    onDeletePersona: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var editingPersona by remember { mutableStateOf<PersonaEntity?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(copy.personaStudio, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = copy.personasTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_close_studio)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPersona = null
                    showEditorSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_persona_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = copy.createPersona
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(personas, key = { it.id }) { persona ->
                    val isSelected = persona.id == activePersona?.id
                    val accent = PersonaVisuals.accentColorFor(persona.accentIndex)
                    val tagline = if (language == UiLanguage.TM) persona.taglineTm else persona.taglineEn

                    Surface(
                        onClick = {
                            onSelectPersona(persona)
                            onBack()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) accent.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_persona_card_${persona.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = PersonaVisuals.iconFor(persona.iconKey),
                                        contentDescription = null,
                                        tint = accent
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = persona.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = if (persona.isBuiltIn) copy.builtInBadge else copy.customBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = tagline,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                }
                            }

                            Text(
                                text = persona.systemPrompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${persona.modelName}  •  T=${String.format("%.1f", persona.temperature)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingPersona = persona
                                            showEditorSheet = true
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_persona_${persona.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = copy.editPersona,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (!persona.isBuiltIn) {
                                        IconButton(
                                            onClick = { onDeletePersona(persona.id) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("delete_persona_${persona.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = copy.delete,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
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
    }

    if (showEditorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditorSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PersonaEditorForm(
                initial = editingPersona,
                language = language,
                copy = copy,
                onDismiss = { showEditorSheet = false },
                onSave = { name, tagline, systemPrompt, modelName, temp, iconKey, accentIndex ->
                    onSavePersona(
                        editingPersona?.id,
                        name,
                        tagline,
                        systemPrompt,
                        modelName,
                        temp,
                        iconKey,
                        accentIndex
                    )
                    showEditorSheet = false
                }
            )
        }
    }
}

@Composable
private fun PersonaEditorForm(
    initial: PersonaEntity?,
    language: UiLanguage,
    copy: LocalizedCopy,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        tagline: String,
        systemPrompt: String,
        modelName: String,
        temperature: Float,
        iconKey: String,
        accentIndex: Int
    ) -> Unit
) {
    val presets = remember(copy, language) { personaPresetTemplates(copy, language) }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var tagline by remember(initial, language) {
        mutableStateOf(
            if (language == UiLanguage.TM) initial?.taglineTm.orEmpty()
            else initial?.taglineEn.orEmpty()
        )
    }
    var systemPrompt by remember(initial) {
        mutableStateOf(
            initial?.systemPrompt
                ?: "You are a helpful, thoughtful AI companion. Respond clearly in the user's language."
        )
    }
    var modelName by remember(initial) {
        mutableStateOf(initial?.modelName ?: PersonaEntity.MODEL_GEMINI_FLASH)
    }
    var temperature by remember(initial) { mutableFloatStateOf(initial?.temperature ?: 0.7f) }
    var iconKey by remember(initial) { mutableStateOf(initial?.iconKey ?: "spark") }
    var accentIndex by remember(initial) { mutableIntStateOf(initial?.accentIndex ?: 0) }
    var selectedPresetKey by remember(initial) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (initial == null) copy.createPersona else copy.editPersona,
            style = MaterialTheme.typography.headlineSmall
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copy.quickPresetsLabel, style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets, key = { it.key }) { preset ->
                    FilterChip(
                        selected = selectedPresetKey == preset.key,
                        onClick = {
                            selectedPresetKey = preset.key
                            name = preset.displayName
                            tagline = if (language == UiLanguage.TM) preset.taglineTm else preset.taglineEn
                            systemPrompt = preset.systemPrompt
                            modelName = preset.modelName
                            temperature = preset.temperature
                            iconKey = preset.iconKey
                            accentIndex = preset.accentIndex
                        },
                        label = { Text(preset.displayName) },
                        modifier = Modifier.testTag("studio_${preset.testTag}")
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(copy.personaNameLabel) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("persona_name_input")
        )

        OutlinedTextField(
            value = tagline,
            onValueChange = { tagline = it },
            label = { Text(copy.personaTaglineLabel) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("persona_tagline_input")
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text(copy.systemPromptLabel) },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("persona_prompt_input")
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copy.modelTierLabel, style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = modelName == PersonaEntity.MODEL_GEMINI_FLASH,
                        onClick = { modelName = PersonaEntity.MODEL_GEMINI_FLASH },
                        label = { Text("3.8 Flash") }
                    )
                }
                item {
                    FilterChip(
                        selected = modelName == PersonaEntity.MODEL_GEMINI_FLASH_LITE,
                        onClick = { modelName = PersonaEntity.MODEL_GEMINI_FLASH_LITE },
                        label = { Text("3.5 Flash-Lite") }
                    )
                }
                item {
                    FilterChip(
                        selected = modelName == PersonaEntity.MODEL_GEMINI_ARGON,
                        onClick = { modelName = PersonaEntity.MODEL_GEMINI_ARGON },
                        label = { Text("4 Argon") }
                    )
                }
            }
        }

        Column {
            val rounded = ((temperature * 10f).roundToInt() / 10f)
            Text(
                text = "${copy.creativityLabel}: $rounded",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0.0f..1.2f
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PersonaVisuals.iconKeys) { key ->
                val selected = key == iconKey
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable { iconKey = key },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = PersonaVisuals.iconFor(key),
                        contentDescription = key,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copy.accentColorLabel, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (0..4).forEach { idx ->
                    val swatch = PersonaVisuals.accentColorFor(idx)
                    val selected = idx == accentIndex
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(swatch.copy(alpha = 0.25f))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) swatch else swatch.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { accentIndex = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(swatch)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(copy.cancel)
            }
            Button(
                onClick = {
                    onSave(name, tagline, systemPrompt, modelName, temperature, iconKey, accentIndex)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_persona_button")
            ) {
                Text(copy.save)
            }
        }
    }
}
