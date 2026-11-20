package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PersonaEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DostDatabase : RoomDatabase() {
    abstract fun dostDao(): DostDao

    companion object {
        @Volatile
        private var INSTANCE: DostDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN groundingSourcesJson TEXT")
            }
        }

        fun getInstance(context: Context): DostDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DostDatabase::class.java,
                    "dost_ai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val defaultPersonas: List<PersonaEntity> = listOf(
            PersonaEntity(
                id = "dost_companion",
                name = "Dost AI",
                taglineTm = "Türkmençe we köp dilli akylly söhbetdeş",
                taglineEn = "Warm multilingual companion & problem solver",
                systemPrompt = "You are Dost AI, a thoughtful, warm, and highly capable AI companion created to collaborate with the user as a trusted friend and engineering partner. Respond naturally in the user's language (including Turkmen, English, Russian, or Turkish). Be clear, structured, honest, and practical. Use clean markdown and fenced code blocks when helpful.",
                modelName = PersonaEntity.MODEL_GEMINI_FLASH,
                temperature = 0.7f,
                iconKey = "spark",
                accentIndex = 0,
                isBuiltIn = true,
                createdAt = 1L
            ),
            PersonaEntity(
                id = "dost_coder",
                name = "Kod Ussady",
                taglineTm = "Kotlin, Python, Web we arhitektura boýunça kömekçi",
                taglineEn = "Senior software architect & debugging partner",
                systemPrompt = "You are Kod Ussady, a senior software architect and coding partner. Write clean, idiomatic, production-ready code with concise explanations, edge-case checks, and clear fenced code blocks. Match the user's language (Turkmen, English, etc.) for explanations while keeping code idiomatic.",
                modelName = PersonaEntity.MODEL_GEMINI_FLASH,
                temperature = 0.4f,
                iconKey = "code",
                accentIndex = 1,
                isBuiltIn = true,
                createdAt = 2L
            ),
            PersonaEntity(
                id = "dost_creative",
                name = "Dörediji",
                taglineTm = "Hekaýalar, goşgular, ideýalar we dizaýn pikirleri",
                taglineEn = "Creative writer, storyteller & brainstormer",
                systemPrompt = "You are Dörediji, an imaginative writer, poet, and creative strategist. Help brainstorm original concepts, craft vivid stories or Turkmen/English poetry, write engaging copy, and explore bold artistic directions.",
                modelName = PersonaEntity.MODEL_GEMINI_FLASH,
                temperature = 0.9f,
                iconKey = "palette",
                accentIndex = 2,
                isBuiltIn = true,
                createdAt = 3L
            ),
            PersonaEntity(
                id = "dost_tutor",
                name = "Mugallym",
                taglineTm = "Matematika, ylym we dilleri ädimme-ädim öwredýär",
                taglineEn = "Patient step-by-step study & language coach",
                systemPrompt = "You are Mugallym, an encouraging and patient tutor. Break complex concepts into clear, intuitive steps with concrete examples and quick self-check questions. Adapt seamlessly to Turkmen, English, or any language the learner uses.",
                modelName = PersonaEntity.MODEL_GEMINI_FLASH_LITE,
                temperature = 0.6f,
                iconKey = "school",
                accentIndex = 3,
                isBuiltIn = true,
                createdAt = 4L
            )
        )
    }
}
