package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dost_prefs")

enum class UiLanguage {
    TM, EN
}

data class UserPrefsSnapshot(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val uiLanguage: UiLanguage = UiLanguage.TM,
    val lastConversationId: String? = null
)

class UserPreferences(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val LANGUAGE = stringPreferencesKey("ui_language")
        val LAST_CONVERSATION_ID = stringPreferencesKey("last_conversation_id")
    }

    val preferencesFlow: Flow<UserPrefsSnapshot> = appContext.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME]?.let { raw ->
            ThemePreference.entries.firstOrNull { it.name == raw }
        } ?: ThemePreference.DARK

        val lang = prefs[Keys.LANGUAGE]?.let { raw ->
            UiLanguage.entries.firstOrNull { it.name == raw }
        } ?: UiLanguage.TM

        UserPrefsSnapshot(
            themePreference = theme,
            uiLanguage = lang,
            lastConversationId = prefs[Keys.LAST_CONVERSATION_ID]
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        appContext.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setUiLanguage(language: UiLanguage) {
        appContext.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setLastConversationId(conversationId: String?) {
        appContext.dataStore.edit { prefs ->
            if (conversationId == null) {
                prefs.remove(Keys.LAST_CONVERSATION_ID)
            } else {
                prefs[Keys.LAST_CONVERSATION_ID] = conversationId
            }
        }
    }
}
