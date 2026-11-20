package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val taglineTm: String,
    val taglineEn: String,
    val systemPrompt: String,
    val modelName: String = MODEL_GEMINI_FLASH,
    val temperature: Float = 0.7f,
    val iconKey: String = "spark",
    val accentIndex: Int = 0,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MODEL_GEMINI_FLASH = "gemini-3.8-flash"
        const val MODEL_GEMINI_FLASH_LITE = "gemini-3.5-flash-lite"
        const val MODEL_GEMINI_ARGON = "gemini-4-argon"
    }
}

@Entity(
    tableName = "conversations",
    indices = [Index("updatedAt")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "USER" or "MODEL"
    val content: String,
    val imagePath: String? = null,
    val groundingSourcesJson: String? = null,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "USER"
        const val ROLE_MODEL = "MODEL"
    }
}

data class GroundingSource(
    val title: String,
    val uri: String
)
