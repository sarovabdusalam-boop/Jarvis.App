package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the selected messenger with a prefilled message.
 * Android keeps the final recipient selection and Send action under the user's control.
 */
object Messaging {
    private val pattern = Regex(
        "^(?:напиши|отправь|сообщение)\\s+(?:(?:в\\s+)?(whatsapp|ватсап|вотсап|telegram|телеграм|телеграмм)\\s+)?(.+?)\\s*[:—-]\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )

    fun tryHandle(context: Context, input: String): String? {
        val match = pattern.find(input.trim()) ?: return null
        val messenger = match.groupValues[1].lowercase()
        val recipient = match.groupValues[2].trim()
        val message = match.groupValues[3].trim()
        if (recipient.isBlank() || message.isBlank()) return "Укажи получателя и текст после двоеточия."
        if (message.length > 2000) return "Сообщение слишком длинное. Сократи его до 2000 символов."

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val packageName = when (messenger) {
            "whatsapp", "ватсап", "вотсап" -> "com.whatsapp"
            "telegram", "телеграм", "телеграмм" -> "org.telegram.messenger"
            else -> null
        }
        if (packageName != null && isInstalled(context, packageName)) {
            intent.setPackage(packageName)
            return try {
                context.startActivity(intent)
                "Открыл $messenger с готовым текстом. Выбери контакт «$recipient», проверь сообщение и нажми «Отправить»."
            } catch (_: Exception) {
                openChooser(context, message, recipient)
            }
        }
        return openChooser(context, message, recipient)
    }

    private fun openChooser(context: Context, message: String, recipient: String): String {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(Intent.createChooser(send, "Кому отправить сообщение «$recipient»")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Открыл выбор приложения. Выбери мессенджер и контакт «$recipient», проверь текст и отправь."
        } catch (_: Exception) {
            "Не удалось открыть приложение для отправки. Проверь, установлен ли мессенджер."
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
