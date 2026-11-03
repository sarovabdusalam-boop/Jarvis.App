package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import java.util.Locale

object Commands {
    private data class AppInfo(val label: String, val lower: String, val pkg: String)

    private val stopWords = setOf("пожалуйста", "джарвис", "мне", "на")
    private val openVerbs = setOf("открой", "открыть", "запусти", "запустить", "включи", "включить", "покажи", "показать")
    private val closeVerbs = setOf("закрой", "закрыть", "выключи", "выключить", "останови", "остановить")
    private val aliases = mapOf(
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
        val cleaned = input.lowercase(Locale("ru")).replace(Regex("[.,!?;:«»\\\"()]"), " ")
        val words = cleaned.split(" ").filter { it.isNotBlank() && it !in stopWords }
        if (words.isEmpty()) return "Повтори, пожалуйста."
        val text = words.joinToString(" ")
        val first = words[0]

        if (text.contains("уведомлен")) return global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Открываю уведомления")
        if (text.contains("сделай скриншот") || text.contains("скрин экрана"))
            return global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Делаю скриншот")
        if (text.contains("заблокируй экран") || text.contains("выключи экран"))
            return global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Блокирую экран")
        if (text.contains("недавние приложения") || text.contains("последние приложения") || text.contains("покажи недавние"))
            return global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Открываю недавние приложения")
        if (text == "домой" || text.contains("на главный экран") || text.contains("иди домой"))
            return global(AccessibilityService.GLOBAL_ACTION_HOME, "На главный экран")
        if (text == "назад" || text.contains("вернись назад"))
            return global(AccessibilityService.GLOBAL_ACTION_BACK, "Назад")

        if (first in openVerbs) {
            val name = words.drop(1).joinToString(" ").removePrefix("приложение ").trim()
            if (name.isEmpty()) return null
            val app = findApp(ctx, name) ?: return "Не нашёл приложение " + name
            val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return "Не могу открыть " + app.label
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(intent)
            return "Открываю " + app.label
        }
        if (first in closeVerbs) return global(AccessibilityService.GLOBAL_ACTION_HOME, "Возвращаю на главный экран")
        return null
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