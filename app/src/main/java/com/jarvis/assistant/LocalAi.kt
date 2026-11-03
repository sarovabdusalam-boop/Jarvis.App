package com.jarvis.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

object LocalAi {
    const val MODEL_FILE = "Qwen2_0.5B_Instruct.litertlm"
    private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2-0.5B-Instruct/resolve/main/Qwen2_0.5B_Instruct.litertlm"
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var loading = false

    fun modelFile(ctx: Context): File = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), MODEL_FILE)
    fun isDownloaded(ctx: Context): Boolean = modelFile(ctx).exists() && modelFile(ctx).length() > 100_000_000L
    fun isReady(): Boolean = conversation != null

    fun download(ctx: Context): Long {
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Мозг Джарвиса")
            .setDescription("Скачивается локальная ИИ-модель Qwen 0.5B (около 647 МБ)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, MODEL_FILE)
        return (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    @Synchronized
    fun initialize(ctx: Context) {
        if (conversation != null || loading) return
        check(isDownloaded(ctx)) { "Сначала скачай модель Джарвиса (647 МБ) по Wi-Fi." }
        loading = true
        try {
            val e = Engine(EngineConfig(modelPath = modelFile(ctx).absolutePath))
            e.initialize()
            val c = e.createConversation(
                ConversationConfig(systemInstruction = Contents.of(
                    "Ты Джарвис — простой, дружелюбный русскоязычный помощник на телефоне. " +
                    "Отвечай кратко, честно и понятно. Если не знаешь — скажи об этом. " +
                    "Ты работаешь локально без интернета и не имеешь доступа к свежим данным."
                ))
            )
            engine = e
            conversation = c
        } finally {
            loading = false
        }
    }

    @Synchronized
    fun ask(ctx: Context, prompt: String): String {
        initialize(ctx)
        return conversation?.sendMessage(prompt)?.text?.trim().orEmpty().ifBlank { "Не получилось сформировать ответ." }
    }

    @Synchronized
    fun close() {
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
        conversation = null
        engine = null
    }
}
