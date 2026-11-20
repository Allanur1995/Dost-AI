package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.local.ConversationEntity
import com.example.data.local.DostDao
import com.example.data.local.DostDatabase
import com.example.data.local.GroundingSource
import com.example.data.local.MessageEntity
import com.example.data.local.PersonaEntity
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(
        val fullText: String,
        val sources: List<GroundingSource> = emptyList()
    ) : AiStreamEvent
    data object Timeout : AiStreamEvent
    data class Error(val message: String) : AiStreamEvent
}

sealed interface ImageGenResult {
    data class Success(val savedImagePath: String) : ImageGenResult
    data class Error(val message: String) : ImageGenResult
}

class DostRepository(
    context: Context,
    private val dao: DostDao
) {
    private val appContext = context.applicationContext

    private val modelCache = mutableMapOf<String, GenerativeModel>()

    private val imageGenModel: GenerativeModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config,
            requestOptions = RequestOptions(AI_TIMEOUT_MS)
        )
    }

    private val transcriptionModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = PersonaEntity.MODEL_GEMINI_FLASH,
                requestOptions = RequestOptions(AI_TIMEOUT_MS)
            )
    }

    val personas: Flow<List<PersonaEntity>> = dao.observePersonas()
    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    suspend fun ensureSeeded() {
        if (dao.getPersonaCount() == 0) {
            dao.insertPersonasIgnore(DostDatabase.defaultPersonas)
        }
    }

    suspend fun getPersona(personaId: String): PersonaEntity {
        return dao.getPersonaById(personaId) ?: DostDatabase.defaultPersonas.first()
    }

    suspend fun createConversation(personaId: String, initialTitle: String): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = initialTitle,
            personaId = personaId,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertConversation(conversation)
        return conversation
    }

    suspend fun getConversation(conversationId: String): ConversationEntity? =
        dao.getConversationById(conversationId)

    suspend fun getMostRecentConversation(): ConversationEntity? =
        dao.getMostRecentConversation()

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        dao.updateConversationTitle(conversationId, title, System.currentTimeMillis())
    }

    suspend fun updateConversationPersona(conversationId: String, personaId: String) {
        dao.updateConversationPersona(conversationId, personaId, System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversationId: String) {
        val messages = dao.getMessagesForConversation(conversationId)
        withContext(Dispatchers.IO) {
            messages.mapNotNull { it.imagePath }.forEach { path ->
                runCatching { File(path).delete() }
            }
        }
        dao.deleteMessagesForConversation(conversationId)
        dao.deleteConversation(conversationId)
    }

    suspend fun clearConversationHistory(conversationId: String) {
        deleteConversation(conversationId)
    }

    suspend fun upsertCustomPersona(persona: PersonaEntity) {
        dao.upsertPersona(persona)
        modelCache.keys.removeAll { it.startsWith("${persona.id}:") }
    }

    suspend fun deleteCustomPersona(personaId: String) {
        dao.deleteCustomPersona(personaId)
        modelCache.keys.removeAll { it.startsWith("$personaId:") }
    }

    suspend fun insertMessage(message: MessageEntity) {
        dao.insertMessage(message)
        dao.touchConversation(message.conversationId, message.createdAt)
    }

    suspend fun deleteMessage(messageId: String) {
        dao.deleteMessage(messageId)
    }

    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity> =
        dao.getMessagesForConversation(conversationId)

    suspend fun savePickedImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val imagesDir = File(appContext.filesDir, "chat_images").apply { mkdirs() }
            val inputStream = appContext.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val scaled = scaleBitmapDown(original, maxDimension = 1280)
            val outputFile = File(imagesDir, "img_${UUID.randomUUID()}.jpg")
            FileOutputStream(outputFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (scaled !== original) {
                scaled.recycle()
            }
            original.recycle()
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist picked image", e)
            null
        }
    }

    private suspend fun saveGeneratedBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val imagesDir = File(appContext.filesDir, "chat_images").apply { mkdirs() }
        val outputFile = File(imagesDir, "gen_${UUID.randomUUID()}.jpg")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        outputFile.absolutePath
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private suspend fun loadBitmapFromFile(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    private fun getOrCreateModel(
        persona: PersonaEntity,
        useSearchGrounding: Boolean
    ): GenerativeModel {
        val cacheKey = "${persona.id}:${persona.modelName}:${persona.temperature}:${persona.systemPrompt.hashCode()}:search=$useSearchGrounding"
        return modelCache.getOrPut(cacheKey) {
            val config = generationConfig {
                temperature = persona.temperature.coerceIn(0f, 1.5f)
            }
            val sysInstruction = if (persona.systemPrompt.isNotBlank()) {
                content { text(persona.systemPrompt) }
            } else {
                null
            }
            val tools = if (useSearchGrounding) listOf(Tool.googleSearch()) else null
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = persona.modelName,
                generationConfig = config,
                tools = tools,
                systemInstruction = sysInstruction,
                requestOptions = RequestOptions(AI_TIMEOUT_MS)
            )
        }
    }

    private suspend fun ensureAppCheckTokenReady(): String? {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            return null
        }
        return try {
            val token = withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).awaitTask()
            }
            if (token == null) {
                Log.e(TAG, "App Check token exchange timed out after ${APP_CHECK_TIMEOUT_MS}ms.")
                "Firebase App Check timed out while verifying the app. Please check internet connection and try again."
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase App Check token check failed", e)
            val detail = formatAiErrorMessage(e)
            if (detail.contains("App Check", ignoreCase = true)) {
                detail
            } else {
                "Firebase App Check verification failed: $detail"
            }
        }
    }

    fun streamChatResponse(
        persona: PersonaEntity,
        priorMessages: List<MessageEntity>,
        promptText: String,
        imagePath: String?,
        useSearchGrounding: Boolean
    ): Flow<AiStreamEvent> = flow {
        val appCheckError = ensureAppCheckTokenReady()
        if (appCheckError != null) {
            emit(AiStreamEvent.Error(appCheckError))
            return@flow
        }

        val model = getOrCreateModel(persona, useSearchGrounding)

        val sanitizedHistory = sanitizeHistoryMessages(priorMessages)
        val historyContent = mutableListOf<Content>()
        for (msg in sanitizedHistory) {
            val role = if (msg.role == MessageEntity.ROLE_USER) "user" else "model"
            val msgBitmap = if (!msg.imagePath.isNullOrBlank()) {
                loadBitmapFromFile(msg.imagePath)
            } else {
                null
            }
            if (msg.content.isBlank() && msgBitmap == null) continue
            historyContent += content(role) {
                if (msgBitmap != null) image(msgBitmap)
                if (msg.content.isNotBlank()) text(msg.content)
            }
        }

        val currentBitmap = if (!imagePath.isNullOrBlank()) {
            loadBitmapFromFile(imagePath)
        } else {
            null
        }

        val userTurnContent = content("user") {
            if (currentBitmap != null) image(currentBitmap)
            if (promptText.isNotBlank()) {
                text(promptText)
            } else if (currentBitmap != null) {
                text("Please analyze this image and describe what you see.")
            }
        }

        val chat = model.startChat(history = historyContent)
        val accumulated = StringBuilder()
        val collectedSources = linkedMapOf<String, GroundingSource>()

        try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(userTurnContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                    collectGroundingSources(chunk, collectedSources)
                }
            }
            if (accumulated.isNotBlank()) {
                emit(
                    AiStreamEvent.Completed(
                        fullText = accumulated.toString(),
                        sources = collectedSources.values.toList()
                    )
                )
            } else {
                emit(AiStreamEvent.Error("Model returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            if (accumulated.isNotBlank()) {
                Log.w(TAG, "AI streaming timed out after partial output; completing with received text.", e)
                emit(
                    AiStreamEvent.Completed(
                        fullText = accumulated.toString(),
                        sources = collectedSources.values.toList()
                    )
                )
            } else {
                Log.e(TAG, "AI request timed out after ${AI_TIMEOUT_MS}ms", e)
                emit(AiStreamEvent.Timeout)
            }
        } catch (e: RequestTimeoutException) {
            if (accumulated.isNotBlank()) {
                Log.w(TAG, "HTTP request timed out after partial output; completing with received text.", e)
                emit(
                    AiStreamEvent.Completed(
                        fullText = accumulated.toString(),
                        sources = collectedSources.values.toList()
                    )
                )
            } else {
                Log.e(TAG, "Firebase AI RequestTimeoutException", e)
                emit(AiStreamEvent.Timeout)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic streaming failed", e)
            if (accumulated.isNotBlank()) {
                emit(
                    AiStreamEvent.Completed(
                        fullText = accumulated.toString(),
                        sources = collectedSources.values.toList()
                    )
                )
            } else if (shouldRetryUnary(e)) {
                Log.w(TAG, "Retrying with unary sendMessage after stream error", e)
                try {
                    val fallbackChat = model.startChat(history = historyContent)
                    val response = withTimeout(UNARY_FALLBACK_TIMEOUT_MS) {
                        fallbackChat.sendMessage(userTurnContent)
                    }
                    collectGroundingSources(response, collectedSources)
                    val fallbackText = response.text.orEmpty()
                    if (fallbackText.isNotBlank()) {
                        emit(
                            AiStreamEvent.Completed(
                                fullText = fallbackText,
                                sources = collectedSources.values.toList()
                            )
                        )
                    } else {
                        emit(AiStreamEvent.Error("Model returned an empty response."))
                    }
                } catch (fallbackTimeout: TimeoutCancellationException) {
                    Log.e(TAG, "Unary fallback timed out", fallbackTimeout)
                    emit(AiStreamEvent.Timeout)
                } catch (fallbackRequestTimeout: RequestTimeoutException) {
                    Log.e(TAG, "Unary fallback RequestTimeoutException", fallbackRequestTimeout)
                    emit(AiStreamEvent.Timeout)
                } catch (fallbackCancel: CancellationException) {
                    throw fallbackCancel
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Unary fallback failed", fallbackError)
                    emit(AiStreamEvent.Error(formatAiErrorMessage(fallbackError)))
                }
            } else {
                emit(AiStreamEvent.Error(formatAiErrorMessage(e)))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun collectGroundingSources(
        response: GenerateContentResponse,
        collectedSources: MutableMap<String, GroundingSource>
    ) {
        val groundingChunks = response.candidates.firstOrNull()
            ?.groundingMetadata
            ?.groundingChunks
        groundingChunks?.forEach { gChunk ->
            val web = gChunk.web
            val uri = web?.uri
            val title = web?.title
            if (!uri.isNullOrBlank()) {
                collectedSources.putIfAbsent(
                    uri,
                    GroundingSource(
                        title = title?.takeIf { it.isNotBlank() } ?: uri,
                        uri = uri
                    )
                )
            }
        }
    }

    suspend fun generateOrEditImage(
        promptText: String,
        inputImagePath: String?
    ): ImageGenResult = withContext(Dispatchers.IO) {
        try {
            val appCheckError = ensureAppCheckTokenReady()
            if (appCheckError != null) {
                return@withContext ImageGenResult.Error(appCheckError)
            }

            val inputBitmap = if (!inputImagePath.isNullOrBlank()) {
                loadBitmapFromFile(inputImagePath)
            } else {
                null
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                if (inputBitmap != null) {
                    imageGenModel.generateContent(
                        content {
                            image(inputBitmap)
                            text(promptText.ifBlank { "Enhance and creatively restyle this image." })
                        }
                    )
                } else {
                    imageGenModel.generateContent(promptText)
                }
            }

            val generatedBitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image

            if (generatedBitmap != null) {
                val savedPath = saveGeneratedBitmap(generatedBitmap)
                ImageGenResult.Success(savedPath)
            } else {
                ImageGenResult.Error("No image was returned by the model.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            ImageGenResult.Error("Image generation timed out after 60 seconds.")
        } catch (e: RequestTimeoutException) {
            Log.e(TAG, "Image generation RequestTimeoutException", e)
            ImageGenResult.Error("Image generation timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            ImageGenResult.Error(formatAiErrorMessage(e))
        }
    }

    suspend fun transcribeAudio(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val appCheckError = ensureAppCheckTokenReady()
            if (appCheckError != null) {
                return@withContext Result.failure(IllegalStateException(appCheckError))
            }

            val audioBytes = audioFile.readBytes()
            if (audioBytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Recorded audio is empty."))
            }
            val prompt = content {
                inlineData(audioBytes, "audio/mp4")
                text("Transcribe accurately what is spoken in this audio recording in its original language. Output only the transcribed text.")
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                transcriptionModel.generateContent(prompt)
            }
            val transcribed = response.text?.trim().orEmpty()
            if (transcribed.isNotBlank()) {
                Result.success(transcribed)
            } else {
                Result.failure(IllegalStateException("No speech detected in the recording."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            Result.failure(Exception("Audio transcription timed out after 60 seconds."))
        } catch (e: RequestTimeoutException) {
            Log.e(TAG, "Audio transcription RequestTimeoutException", e)
            Result.failure(Exception("Audio transcription timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            Result.failure(Exception(formatAiErrorMessage(e), e))
        } finally {
            runCatching { audioFile.delete() }
        }
    }

    private fun shouldRetryUnary(e: Throwable): Boolean {
        if (e is PromptBlockedException || e is ResponseStoppedException) return false
        val msg = formatAiErrorMessage(e)
        return !msg.contains("App Check", ignoreCase = true) &&
            !msg.contains("403", ignoreCase = true) &&
            !msg.contains("quota", ignoreCase = true) &&
            !msg.contains("API key", ignoreCase = true)
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }

    companion object {
        private const val TAG = "DostRepository"
        private const val AI_TIMEOUT_MS = 60_000L
        private const val UNARY_FALLBACK_TIMEOUT_MS = 30_000L
        private const val APP_CHECK_TIMEOUT_MS = 15_000L

        internal fun sanitizeHistoryMessages(priorMessages: List<MessageEntity>): List<MessageEntity> {
            val nonError = priorMessages.filter { msg ->
                !msg.isError && (msg.content.isNotBlank() || !msg.imagePath.isNullOrBlank())
            }
            val normalized = mutableListOf<MessageEntity>()
            for (msg in nonError) {
                if (normalized.isEmpty()) {
                    if (msg.role == MessageEntity.ROLE_USER) {
                        normalized.add(msg)
                    }
                } else {
                    val last = normalized.last()
                    if (last.role == msg.role) {
                        // Replace incomplete consecutive turn of same role so user/model stays strictly alternating.
                        normalized[normalized.lastIndex] = msg
                    } else {
                        normalized.add(msg)
                    }
                }
            }
            // Prior history passed to startChat before a new user turn must end with a model response.
            if (normalized.lastOrNull()?.role == MessageEntity.ROLE_USER) {
                normalized.removeAt(normalized.lastIndex)
            }
            return normalized
        }

        internal fun formatAiErrorMessage(throwable: Throwable): String {
            val primary = throwable.localizedMessage?.trim().orEmpty()
            var root: Throwable? = throwable
            while (root?.cause != null && root.cause !== root) {
                root = root.cause
            }
            val rootMessage = root?.localizedMessage?.trim().orEmpty()

            return when {
                root is UnknownHostException ->
                    "Unable to reach AI servers (DNS lookup failed). Check internet connection and try again."
                root is SocketTimeoutException ->
                    "Network connection timed out while contacting AI servers. Please try again."
                root is ConnectException ->
                    "Could not connect to AI servers. Please check your internet connection."
                root is SSLException ->
                    "Secure connection (TLS) to AI servers failed: ${rootMessage.ifBlank { "SSL error" }}"
                primary.equals("Something unexpected happened.", ignoreCase = true) && rootMessage.isNotBlank() ->
                    rootMessage
                primary.isNotBlank() && rootMessage.isNotBlank() && !primary.contains(rootMessage) && root !== throwable ->
                    "$primary ($rootMessage)"
                primary.isNotBlank() -> primary
                rootMessage.isNotBlank() -> rootMessage
                else -> "AI generation failed."
            }
        }

        fun serializeSources(sources: List<GroundingSource>): String? {
            if (sources.isEmpty()) return null
            val array = JSONArray()
            sources.forEach { src ->
                array.put(
                    JSONObject().apply {
                        put("title", src.title)
                        put("uri", src.uri)
                    }
                )
            }
            return array.toString()
        }

        fun deserializeSources(json: String?): List<GroundingSource> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    val title = obj.optString("title")
                    val uri = obj.optString("uri")
                    if (uri.isNotBlank()) GroundingSource(title.ifBlank { uri }, uri) else null
                }
            }.getOrDefault(emptyList())
        }
    }
}
