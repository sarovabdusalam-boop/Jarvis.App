package com.jarvis.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(val code: Int, message: String) : Exception(message)

object AIClient {
    private const val MODEL = "gemini-3.8-flash"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val SYSTEM =
        "Ты Джарвис — голосовой ассистент на телефоне. Отвечай по-русски, коротко (1–3 предложения), " +
            "дружелюбно и по делу. Не используй markdown, списки и эмодзи: ответ будет озвучен голосом."

    fun ask(apiKey: String, history: List<Pair<String, String>>): String {
        val contents = JSONArray()
        for ((role, text) in history) {
            contents.put(JSONObject()
                .put("role", if (role == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text))))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 400).put("temperature", 0.7))

        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val conn = URL("$API_URL?key=$encodedKey").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 40000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(raw).getJSONObject("error").getString("message") }
                catch (_: Exception) { raw.take(180) }
                throw ApiException(code, msg)
            }
            val candidates = JSONObject(raw).getJSONArray("candidates")
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val answer = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) answer.append(part.getString("text"))
            }
            return answer.toString().trim().ifEmpty { "Не знаю, что ответить." }
        } finally {
            conn.disconnect()
        }
    }
}
