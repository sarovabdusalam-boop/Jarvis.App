package com.jarvis.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val code: Int, message: String) : Exception(message)

object ClaudeClient {
    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-haiku-5-5"
    private const val SYSTEM =
        "Ты Джарвис — голосовой ассистент на телефоне. Отвечай по-русски, коротко (1–3 предложения), " +
            "дружелюбно и по делу. Не используй markdown, списки и эмодзи: ответ будет озвучен голосом."

    fun ask(apiKey: String, history: List<Pair<String, String>>): String {
        val messages = JSONArray()
        for ((role, text) in history) messages.put(JSONObject().put("role", role).put("content", text))
        val body = JSONObject().put("model", MODEL).put("max_tokens", 400)
            .put("system", SYSTEM).put("messages", messages)
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 40000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey.trim())
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(raw).getJSONObject("error").getString("message") }
                catch (_: Exception) { raw.take(150) }
                throw ApiException(code, msg)
            }
            val content = JSONObject(raw).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") sb.append(block.getString("text"))
            }
            return sb.toString().trim().ifEmpty { "Не знаю, что ответить." }
        } finally { conn.disconnect() }
    }
}