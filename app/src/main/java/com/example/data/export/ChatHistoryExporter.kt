package com.example.data.export

import android.content.Context
import android.net.Uri
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PersonaEntity
import com.example.data.repository.DostRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ChatExportFormat(
    val extension: String,
    val mimeType: String
) {
    JSON(extension = "json", mimeType = "application/json"),
    TEXT(extension = "txt", mimeType = "text/plain")
}

object ChatHistoryExporter {

    fun buildSuggestedFileName(
        conversationTitle: String?,
        format: ChatExportFormat,
        timestampMillis: Long = System.currentTimeMillis()
    ): String {
        val rawSlug = conversationTitle
            ?.lowercase(Locale.ROOT)
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
            ?.take(32)
            .orEmpty()
        val prefix = if (rawSlug.isNotBlank()) "dost-ai-$rawSlug" else "dost-ai-chat"
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(timestampMillis))
        return "$prefix-$stamp.${format.extension}"
    }

    fun formatAsJson(
        conversation: ConversationEntity?,
        persona: PersonaEntity?,
        messages: List<MessageEntity>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val companionName = persona?.name ?: "Dost AI"
        val messagesArray = JSONArray()

        messages.forEach { msg ->
            val sources = DostRepository.deserializeSources(msg.groundingSourcesJson)
            val sourcesArray = JSONArray()
            sources.forEach { src ->
                sourcesArray.put(
                    JSONObject().apply {
                        put("title", src.title)
                        put("uri", src.uri)
                    }
                )
            }

            messagesArray.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role)
                    put(
                        "sender",
                        if (msg.role == MessageEntity.ROLE_USER) "user" else companionName
                    )
                    put("content", msg.content)
                    put("timestampMillis", msg.createdAt)
                    put("timestampIso", formatIsoUtc(msg.createdAt))
                    put("isError", msg.isError)
                    put("hasImage", !msg.imagePath.isNullOrBlank())
                    if (sourcesArray.length() > 0) {
                        put("sources", sourcesArray)
                    }
                }
            )
        }

        val root = JSONObject().apply {
            put("app", "Dost AI")
            put("formatVersion", 1)
            put("exportedAtMillis", exportedAtMillis)
            put("exportedAtIso", formatIsoUtc(exportedAtMillis))
            put("conversationId", conversation?.id ?: JSONObject.NULL)
            put("title", conversation?.title ?: companionName)
            put(
                "companion",
                JSONObject().apply {
                    put("id", persona?.id ?: JSONObject.NULL)
                    put("name", companionName)
                    put("modelName", persona?.modelName ?: JSONObject.NULL)
                }
            )
            put("messageCount", messages.size)
            put("messages", messagesArray)
        }
        return root.toString(2)
    }

    fun formatAsPlainText(
        conversation: ConversationEntity?,
        persona: PersonaEntity?,
        messages: List<MessageEntity>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val companionName = persona?.name ?: "Dost AI"
        val title = conversation?.title ?: companionName
        val builder = StringBuilder()

        builder.appendLine("Dost AI — Chat Backup")
        builder.appendLine("Title: $title")
        builder.appendLine("Companion: $companionName")
        builder.appendLine("Exported: ${formatReadableTimestamp(exportedAtMillis)}")
        builder.appendLine("Messages: ${messages.size}")
        builder.appendLine("========================================")
        builder.appendLine()

        messages.forEach { msg ->
            val sender = if (msg.role == MessageEntity.ROLE_USER) "You" else companionName
            val stamp = formatReadableTimestamp(msg.createdAt)
            builder.appendLine("[$stamp] $sender:")
            if (!msg.imagePath.isNullOrBlank()) {
                builder.appendLine("[Image attached]")
            }
            if (msg.isError) {
                builder.appendLine("[Error] ${msg.content}")
            } else if (msg.content.isNotBlank()) {
                builder.appendLine(msg.content)
            }
            val sources = DostRepository.deserializeSources(msg.groundingSourcesJson)
            if (sources.isNotEmpty()) {
                builder.appendLine("Sources:")
                sources.forEach { src ->
                    builder.appendLine("  - ${src.title}: ${src.uri}")
                }
            }
            builder.appendLine()
        }

        return builder.toString().trimEnd() + "\n"
    }

    suspend fun writeToUri(
        context: Context,
        uri: Uri,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("Could not open destination file for writing.")
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(content)
            }
        }
    }

    private fun formatIsoUtc(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(timestampMillis))
    }

    private fun formatReadableTimestamp(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMillis))
    }
}
