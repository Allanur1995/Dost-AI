package com.example.ui.components

import com.example.data.local.PersonaEntity
import com.example.data.local.UiLanguage
import com.example.ui.state.LocalizedCopy

data class PersonaPresetTemplate(
    val key: String,
    val displayName: String,
    val taglineTm: String,
    val taglineEn: String,
    val systemPrompt: String,
    val modelName: String,
    val temperature: Float,
    val iconKey: String,
    val accentIndex: Int,
    val testTag: String
)

fun personaPresetTemplates(copy: LocalizedCopy, language: UiLanguage): List<PersonaPresetTemplate> = listOf(
    PersonaPresetTemplate(
        key = "creative_coder",
        displayName = copy.presetCreativeCoderName,
        taglineTm = "Täzeçil kod, arassa arhitektura we döredijilikli çözgütler",
        taglineEn = "Expressive code, clean architecture & inventive solutions",
        systemPrompt = if (language == UiLanguage.TM) {
            "Sen Dörediji Kodçy (Creative Coder). Arassa, döwrebap we döredijilikli kod ýaz, algoritmleri aýdyň düşündir we Kotlin, Python ýa-da Web boýunça amaly mysallar ber. Ulanyjynyň dilinde jogap ber."
        } else {
            "You are a Creative Coder. Combine clean software architecture with inventive, practical solutions. Provide well-structured code blocks, concise explanations, and thoughtful edge-case insights."
        },
        modelName = PersonaEntity.MODEL_GEMINI_FLASH,
        temperature = 0.55f,
        iconKey = "code",
        accentIndex = 1,
        testTag = "preset_creative_coder"
    ),
    PersonaPresetTemplate(
        key = "tutor",
        displayName = copy.presetTutorName,
        taglineTm = "Çylşyrymly mowzuklary ädimme-ädim we mysallar bilen düşündirýär",
        taglineEn = "Patient step-by-step teacher with intuitive examples",
        systemPrompt = if (language == UiLanguage.TM) {
            "Sen sabyrly we ruhlandyryjy Mugallym (Tutor). Çylşyrymly mowzuklary ýönekeý ädimlere böl, aýdyň mysallar getir we öwrenijiniň düşünşini barlamak üçin gysga soraglar ber."
        } else {
            "You are a patient, encouraging Tutor. Break complex concepts into clear step-by-step explanations, use intuitive analogies, and offer short check-in questions to reinforce understanding."
        },
        modelName = PersonaEntity.MODEL_GEMINI_FLASH,
        temperature = 0.6f,
        iconKey = "school",
        accentIndex = 3,
        testTag = "preset_tutor"
    ),
    PersonaPresetTemplate(
        key = "friendly_assistant",
        displayName = copy.presetFriendlyAssistantName,
        taglineTm = "Gündelik işlerde mähirli, çalasyn we kömekçi söhbetdeş",
        taglineEn = "Warm, conversational & upbeat everyday helper",
        systemPrompt = if (language == UiLanguage.TM) {
            "Sen mähirli, açyk göwünli we kömekçi Dost (Friendly Assistant). Söhbetdeşlik äheňinde, aýdyň we peýdaly jogap ber, ulanyjynyň islegine görä çeýe kömek et."
        } else {
            "You are a warm, Friendly Assistant. Keep your tone welcoming, conversational, and clear while delivering accurate, actionable help tailored to the user's request."
        },
        modelName = PersonaEntity.MODEL_GEMINI_FLASH,
        temperature = 0.75f,
        iconKey = "spark",
        accentIndex = 0,
        testTag = "preset_friendly_assistant"
    )
)
