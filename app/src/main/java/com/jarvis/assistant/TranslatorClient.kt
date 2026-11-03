package com.jarvis.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslatorClient {
    fun translateToRussian(raw: String): String {
        val text = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        if (text.isBlank()) return "На экране нет текста для перевода."
        val language = detectSourceLanguage(text)
        if (language == "ru") return "Похоже, текст уже на русском:\n$text"
        val chunks = splitUtf8(text, 430)
        val translated = chunks.map { translateChunk(it, language) }
        return translated.joinToString("\n").take(10000)
    }

    private fun detectSourceLanguage(text: String): String {
        val lower = text.lowercase()
        if (Regex("[ңғүұқөһі]").containsMatchIn(lower)) return "kk"
        val letters = lower.count { it.isLetter() }
        val latin = lower.count { it in 'a'..'z' }
        val cyrillic = lower.count { it in 'а'..'я' || it in 'ё'..'ё' }
        return when {
            latin > cyrillic -> "en"
            cyrillic > 0 -> "ru"
            else -> "en"
        }
    }

    private fun splitUtf8(text: String, maxBytes: Int): List<String> {
        val result = ArrayList<String>()
        var current = StringBuilder()
        var bytes = 0
        for (word in text.split(Regex("\\s+"))) {
            val size = word.toByteArray(Charsets.UTF_8).size + 1
            if (bytes + size > maxBytes && current.isNotEmpty()) {
                result.add(current.toString())
                current = StringBuilder()
                bytes = 0
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
            bytes += size
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun translateChunk(text: String, source: String): String {
        val query = URLEncoder.encode(text, "UTF-8")
        val pair = URLEncoder.encode("$source|ru", "UTF-8")
        val connection = (URL("https://api.mymemory.translated.net/get?q=$query&langpair=$pair").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Jarvis-Android/1.0")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Translation service HTTP $code")
            val json = JSONObject(body)
            val status = json.optInt("responseStatus", 200)
            if (status != 200) throw IllegalStateException(json.optString("responseDetails", "Translation failed"))
            return json.getJSONObject("responseData").getString("translatedText")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        } finally {
            connection.disconnect()
        }
    }
}
