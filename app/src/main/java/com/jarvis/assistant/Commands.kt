package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import java.util.Calendar
import java.util.Locale

object Commands {
    private data class AppInfo(val label: String, val lower: String, val pkg: String)

    private val stopWords = setOf("пожалуйста", "джарвис", "мне", "на")
    private val openVerbs = setOf("открой", "открыть", "запусти", "запустить", "включи", "включить", "покажи", "показать")
    private val closeVerbs = setOf("закрой", "закрыть", "выключи", "выключить", "останови", "остановить")
    private val numberWords = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "два" to 2, "две" to 2, "три" to 3,
        "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8,
        "девять" to 9, "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12,
        "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15,
        "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18,
        "девятнадцать" to 19, "двадцать" to 20, "тридцать" to 30,
        "сорок" to 40, "пятьдесят" to 50
    )
    private val aliases = mapOf(
        "чат гпт" to listOf("chatgpt"), "чатгпт" to listOf("chatgpt"), "chat gpt" to listOf("chatgpt"),
        "ютуб" to listOf("youtube"), "телеграм" to listOf("telegram"), "телеграмм" to listOf("telegram"),
        "ватсап" to listOf("whatsapp"), "вотсап" to listOf("whatsapp"), "инстаграм" to listOf("instagram"),
        "инста" to listOf("instagram"), "тикток" to listOf("tiktok"), "хром" to listOf("chrome"),
        "браузер" to listOf("chrome", "browser"), "поиск" to listOf("googlequicksearchbox"),
        "камера" to listOf("camera"), "галерея" to listOf("gallery", "photos"), "фото" to listOf("photos", "gallery"),
        "настройки" to listOf("settings"), "калькулятор" to listOf("calculator"), "часы" to listOf("clock"),
        "календарь" to listOf("calendar"), "контакты" to listOf("contacts"), "телефон" to listOf("dialer", "phone"),
        "сообщения" to listOf("messaging", "messages", "mms"), "смс" to listOf("messaging", "messages", "mms"),
        "карты" to listOf("maps"), "почта" to listOf("gmail", "mail")
    )

    fun tryHandle(ctx: Context, input: String): String? {
        val cleaned = input.lowercase(Locale("ru")).replace(Regex("[.,!?;:«»\\\"()]"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return "Повтори, пожалуйста."

        if (cleaned.contains("отложи будильник") || cleaned.contains("поставь на повтор") ||
            cleaned.contains("ещё поспать") || cleaned.contains("еще поспать")) return snooze(ctx, cleaned)

        if (cleaned.contains("поставь будильник") || cleaned.contains("разбуди меня") ||
            cleaned.contains("будильник на")) return setAlarm(ctx, cleaned)

        val timer = Regex("через\\s+(\\d+)\\s*(секунд\\w*|минут\\w*|час\\w*)").find(cleaned)
        if (timer != null) {
            val n = timer.groupValues[1].toIntOrNull() ?: return "Не понял время таймера."
            val unit = timer.groupValues[2]
            val seconds = when { unit.startsWith("секунд") -> n; unit.startsWith("час") -> n * 3600; else -> n * 60 }
            if (seconds !in 1..86400) return "Таймер должен быть не больше 24 часов."
            return launch(ctx, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true).putExtra(AlarmClock.EXTRA_MESSAGE, "Джарвис"))
                .let { if (it == null) "Ставлю таймер на $n $unit." else it }
        }

        if (cleaned == "записывай" || cleaned == "начни диктовку" || cleaned == "режим диктовки" ||
            cleaned.startsWith("запиши текст")) {
            JarvisService.dictationMode = true
            return "Режим диктовки включён. Говори текст, я буду вставлять его в активное поле. Скажи стоп, чтобы закончить."
        }
        if (cleaned == "стоп диктовка" || cleaned == "закончи диктовку" || cleaned == "останови запись") {
            JarvisService.dictationMode = false
            return "Диктовка остановлена."
        }

        if (cleaned.startsWith("найди ") || cleaned.startsWith("поищи ") || cleaned.startsWith("зайди в гугл") ||
            cleaned.startsWith("открой гугл и найди") || cleaned.startsWith("погугли ")) {
            val query = cleaned.removePrefix("зайди в гугл").removePrefix("открой гугл и найди")
                .removePrefix("найди").removePrefix("поищи").removePrefix("погугли").trim()
            if (query.isNotBlank()) {
                val url = "https://www.google.com/search?q=" + Uri.encode(query)
                return launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url))) ?: "Открываю поиск: $query"
            }
        }

        if (cleaned.contains("уведомлен")) return global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Открываю уведомления")
        if (cleaned.contains("сделай скриншот") || cleaned.contains("скрин экрана"))
            return global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Делаю скриншот")
        if (cleaned.contains("заблокируй экран") || cleaned.contains("выключи экран"))
            return global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Блокирую экран")
        if (cleaned.contains("недавние приложения") || cleaned.contains("последние приложения") || cleaned.contains("покажи недавние"))
            return global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Открываю недавние приложения")
        if (cleaned == "домой" || cleaned.contains("на главный экран") || cleaned.contains("иди домой"))
            return global(AccessibilityService.GLOBAL_ACTION_HOME, "На главный экран")
        if (cleaned == "назад" || cleaned.contains("вернись назад"))
            return global(AccessibilityService.GLOBAL_ACTION_BACK, "Назад")

        val words = cleaned.split(" ")
        val first = words.first()
        if (first in openVerbs) {
            val name = cleaned.substringAfter(first).trim().removePrefix("приложение ").trim()
            if (name.isEmpty()) return null
            val app = findApp(ctx, name) ?: return "Не нашёл приложение $name. Проверь, установлено ли оно."
            val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return "Не могу открыть " + app.label
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(intent)
            return "Открываю " + app.label
        }
        if (first in closeVerbs) return global(AccessibilityService.GLOBAL_ACTION_HOME, "Возвращаю на главный экран")
        return null
    }

    private fun setAlarm(ctx: Context, text: String): String {
        val numeric = Regex("(\\d{1,2})(?:[:.](\\d{1,2}))?").find(text)
        var hour: Int? = numeric?.groupValues?.get(1)?.toIntOrNull()
        var minute: Int = numeric?.groupValues?.get(2)?.toIntOrNull() ?: 0
        if (hour == null) {
            val tokens = text.split(" ")
            val idx = tokens.indexOfFirst { it in numberWords }
            if (idx >= 0) {
                hour = numberWords[tokens[idx]]
                if (idx + 1 < tokens.size && tokens[idx + 1] in numberWords &&
                    (tokens[idx + 1] == "пять" || tokens[idx + 1] == "десять" || tokens[idx + 1] == "двадцать" || tokens[idx + 1] == "тридцать" || tokens[idx + 1] == "сорок" || tokens[idx + 1] == "пятьдесят")) {
                    minute = numberWords[tokens[idx + 1]] ?: 0
                }
            }
        }
        if (hour == null) return "На какое время поставить будильник?"
        if (hour !in 0..23 || minute !in 0..59) return "Проверь время будильника."
        val morning = text.contains("утра") || text.contains("утром")
        val evening = text.contains("вечера") || text.contains("вечером")
        if (hour in 1..12 && evening && hour < 12) hour += 12
        if (hour == 12 && morning) hour = 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Джарвис")
        val result = launch(ctx, intent)
        return result ?: "Ставлю будильник на %02d:%02d.".format(hour, minute)
    }

    private fun snooze(ctx: Context, text: String): String {
        val duration = Regex("(\\d+)\\s*минут").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 10
        if (duration !in 1..120) return "Отложить можно от 1 до 120 минут."
        val intent = Intent(AlarmClock.ACTION_SNOOZE_ALARM)
            .putExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, duration)
        val result = launch(ctx, intent)
        return result ?: "Откладываю будильник на $duration минут."
    }

    private fun launch(ctx: Context, intent: Intent): String? {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(ctx.packageManager) == null) "На телефоне нет приложения, которое поддерживает эту команду."
            else { ctx.startActivity(intent); null }
        } catch (_: Exception) { "Не удалось выполнить команду на этом телефоне." }
    }

    private fun global(action: Int, ok: String): String {
        val service = JarvisAccessibilityService.instance ?: return "Включи службу специальных возможностей Джарвиса в настройках."
        return if (service.performGlobalAction(action)) ok else "Не удалось выполнить команду."
    }

    private fun findApp(ctx: Context, query: String): AppInfo? {
        val q = query.trim().lowercase(Locale("ru"))
        if (q.isEmpty()) return null
        val apps = ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map {
            val label = it.loadLabel(ctx.packageManager).toString()
            AppInfo(label, label.lowercase(Locale("ru")), it.activityInfo.packageName)
        }.filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }
        val keys = listOf(q) + (aliases[q] ?: emptyList())
        for (key in keys) apps.firstOrNull { it.lower == key }?.let { return it }
        for (key in keys) apps.firstOrNull { it.lower.contains(key) || it.pkg.lowercase(Locale.ROOT).contains(key) }?.let { return it }
        return apps.firstOrNull { it.lower.length > 2 && q.contains(it.lower) }
    }
}