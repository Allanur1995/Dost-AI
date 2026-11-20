package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PersonaEntity
import com.example.data.local.UiLanguage
import com.example.ui.state.LocalizedCopy
import com.example.ui.theme.ThemePreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsSheet(
    activePersona: PersonaEntity?,
    themePreference: ThemePreference,
    language: UiLanguage,
    copy: LocalizedCopy,
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
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenPersonaStudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presets = remember(copy, language) { personaPresetTemplates(copy, language) }

    var name by remember(activePersona?.id) {
        mutableStateOf(activePersona?.name ?: copy.presetFriendlyAssistantName)
    }
    var tagline by remember(activePersona?.id, language) {
        mutableStateOf(
            if (language == UiLanguage.TM) activePersona?.taglineTm.orEmpty()
            else activePersona?.taglineEn.orEmpty()
        )
    }
    var systemPrompt by remember(activePersona?.id) {
        mutableStateOf(activePersona?.systemPrompt.orEmpty())
    }
    var modelName by remember(activePersona?.id) {
        mutableStateOf(activePersona?.modelName ?: PersonaEntity.MODEL_GEMINI_FLASH)
    }
    var temperature by remember(activePersona?.id) {
        mutableFloatStateOf(activePersona?.temperature ?: 0.7f)
    }
    var iconKey by remember(activePersona?.id) {
        mutableStateOf(activePersona?.iconKey ?: "spark")
    }
    var accentIndex by remember(activePersona?.id) {
        mutableIntStateOf(activePersona?.accentIndex ?: 0)
    }
    var selectedPresetKey by remember(activePersona?.id) {
        mutableStateOf<String?>(null)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("ai_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.settingsMenuTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = copy.settingsSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = copy.quickPresetsLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets, key = { it.key }) { preset ->
                        val accent = PersonaVisuals.accentColorFor(preset.accentIndex)
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
                            label = {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = PersonaVisuals.iconFor(preset.iconKey),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag(preset.testTag)
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
                    .testTag("settings_persona_name_input")
            )

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = {
                    systemPrompt = it
                    selectedPresetKey = null
                },
                label = { Text(copy.systemPromptLabel) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_system_prompt_input")
            )

            Column {
                val roundedTemp = ((temperature * 10f).roundToInt() / 10f)
                Text(
                    text = "${copy.creativityLabel}: $roundedTemp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..1.2f,
                    modifier = Modifier.testTag("settings_temperature_slider")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSavePersona(
                            null,
                            name.trim().ifBlank { copy.presetFriendlyAssistantName },
                            tagline,
                            systemPrompt,
                            modelName,
                            temperature,
                            iconKey,
                            accentIndex
                        )
                    },
                    enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_save_as_new_button")
                ) {
                    Text(
                        text = copy.saveAsNewPersona,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        onSavePersona(
                            activePersona?.id,
                            name.trim().ifBlank { activePersona?.name ?: copy.presetFriendlyAssistantName },
                            tagline,
                            systemPrompt,
                            modelName,
                            temperature,
                            iconKey,
                            accentIndex
                        )
                    },
                    enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_apply_button")
                ) {
                    Text(
                        text = copy.applyChanges,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TextButton(
                onClick = {
                    onDismiss()
                    onOpenPersonaStudio()
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(48.dp)
                    .testTag("settings_open_studio_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(copy.openPersonaStudio)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Appearance & Language controls inside Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = copy.themeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val options = listOf(
                            Triple(ThemePreference.LIGHT, copy.themeLight, Icons.Rounded.LightMode),
                            Triple(ThemePreference.DARK, copy.themeDark, Icons.Rounded.DarkMode),
                            Triple(ThemePreference.SYSTEM, copy.themeSystem, Icons.Rounded.BrightnessAuto)
                        )
                        options.forEach { (pref, label, icon) ->
                            val selected = themePreference == pref
                            Surface(
                                onClick = { onSelectTheme(pref) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_toggle_language_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (language == UiLanguage.TM) "Dil: Türkmençe" else "Language: English")
                }
            }
        }
    }
}
