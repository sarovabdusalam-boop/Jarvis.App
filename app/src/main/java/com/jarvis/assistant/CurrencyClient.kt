package com.jarvis.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CurrencyClient {
    fun usdToKzt(): String {
        val connection = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        try {
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(response)
            if (json.optString("result") != "success") throw IllegalStateException("Сервис курса временно недоступен")
            val rate = json.getJSONObject("rates").getDouble("KZT")
            val updated = json.optString("time_last_update_utc")
            return "По последним доступным данным, один доллар США стоит примерно %.2f тенге. Обновление курса: %s".format(rate, updated)
        } finally {
            connection.disconnect()
        }
    }
}