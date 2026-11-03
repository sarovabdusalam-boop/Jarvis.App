package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var sOverlay: TextView
    private lateinit var sMic: TextView
    private lateinit var sAcc: TextView
    private lateinit var sKey: TextView
    private lateinit var keyInput: EditText
    private lateinit var startBtn: Button
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("jarvis", MODE_PRIVATE) }
    private val cyan = 0xFF00E5FF.toInt()
    private val grey = 0xFFB0BEC5.toInt()
    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, size: Float = 14f, color: Int = Color.WHITE, top: Int = 0) =
        TextView(this).apply { text = t; textSize = size; setTextColor(color); setPadding(0, px(top), 0, 0) }
    private fun btn(t: String, action: () -> Unit) = Button(this).apply {
        text = t; setAllCaps(false); setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(36), px(20), px(28))
        }
        col.addView(tv("Джарвис", 30f, cyan))
        col.addView(tv("Личный голосовой помощник", 14f, grey, 6))
        col.addView(tv("1. Поверх других приложений", 16f, top = 22))
        sOverlay = tv("", 13f, grey); col.addView(sOverlay)
        col.addView(btn("Открыть настройку") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(tv("2. Микрофон и уведомления", 16f, top = 18))
        sMic = tv("", 13f, grey); col.addView(sMic)
        col.addView(btn("Разрешить") {
            val perms = if (Build.VERSION.SDK_INT >= 33)
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.RECORD_AUDIO)
            requestPermissions(perms, 1)
        })
        col.addView(tv("3. Служба доступности", 16f, top = 18))
        sAcc = tv("", 13f, grey); col.addView(sAcc)
        col.addView(tv("Нужна для системных команд и вставки текста.", 12f, grey, 4))
        col.addView(btn("Открыть настройки доступности") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        col.addView(tv("4. Секретные команды", 18f, cyan, 22))
        col.addView(tv("Придумай собственные слова-триггеры. Например: «огурец» → «открой ютуб».", 13f, grey, 4))
        col.addView(btn("＋ Создать секретную команду") { editSecret(null, null) })
        col.addView(btn("Управление секретными командами") { showSecrets() })

        col.addView(tv("5. Текстовый чат", 18f, cyan, 18))
        col.addView(tv("Пиши Джарвису, когда неудобно говорить. Чат сохранит переписку на телефоне.", 13f, grey, 4))
        col.addView(btn("Открыть чат Джарвиса") { startActivity(Intent(this, ChatActivity::class.java)) })

        col.addView(tv("6. ИИ-режим", 16f, top = 18))
        sKey = tv("", 13f, grey); col.addView(sKey)
        keyInput = EditText(this).apply {
            hint = "Необязательно: ключ Gemini"; setHintTextColor(0xFF607D8B.toInt()); setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(true)
        }
        col.addView(keyInput)
        col.addView(btn("Получить ключ Gemini (необязательно)") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
        })
        col.addView(btn("Сохранить ключ") {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty()) toast("Поле пустое")
            else { prefs.edit().putString("api_key", key).apply(); toast("Ключ сохранён"); refresh() }
        })
        col.addView(btn("Удалить сохранённый ключ") {
            prefs.edit().remove("api_key").apply(); keyInput.text.clear(); toast("Ключ удалён"); refresh()
        })
        col.addView(tv("7. Запуск Джарвиса", 16f, top = 18))
        col.addView(tv("Для Tecno также включи автозапуск и режим батареи «Без ограничений».", 12f, grey, 4))
        col.addView(btn("Открыть настройки батареи") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        startBtn = btn("Запустить Джарвиса") { toggle() }
        col.addView(startBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(24) })
        col.addView(tv(
            "Примеры: «открой ютуб», «поставь будильник на 7:30», «переведи экран», «начни диктовку».\n" +
                "Секретные слова срабатывают и голосом, и в чате.",
            13f, grey, 20
        ))
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF0A1A2F.toInt()); addView(col) })
        prefs.getString("api_key", "")?.let { keyInput.setText(it) }
    }

    private fun editSecret(old: String?, oldAction: String?) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(18), px(8), px(18), 0) }
        val trigger = EditText(this).apply { hint = "Секретное слово, например огурец"; setText(old.orEmpty()); setSingleLine(true) }
        val action = EditText(this).apply { hint = "Действие, например открой ютуб"; setText(oldAction.orEmpty()) }
        layout.addView(trigger); layout.addView(action)
        AlertDialog.Builder(this).setTitle(if (old == null) "Новая команда" else "Изменить команду")
            .setView(layout).setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val word = trigger.text.toString().trim().lowercase()
                val command = action.text.toString().trim()
                if (word.isBlank() || command.isBlank()) { toast("Заполни оба поля"); return@setPositiveButton }
                val map = secretMap(); map[word] = command; saveSecrets(map); toast("Секретная команда сохранена")
            }.show()
    }

    private fun secretMap(): LinkedHashMap<String, String> {
        val raw = prefs.getString("secret_commands", "") ?: ""
        val map = LinkedHashMap<String, String>()
        raw.split("\\n").forEach { line ->
            val p = line.split("\\t", limit = 2)
            if (p.size == 2 && p[0].isNotBlank()) map[p[0]] = p[1]
        }
        return map
    }
    private fun saveSecrets(map: Map<String, String>) {
        prefs.edit().putString("secret_commands", map.entries.joinToString("\\n") { it.key + "\\t" + it.value }).apply()
    }
    private fun showSecrets() {
        val map = secretMap()
        if (map.isEmpty()) { toast("Пока нет секретных команд"); return }
        val entries = map.entries.toList()
        AlertDialog.Builder(this).setTitle("Твои секретные команды")
            .setItems(entries.map { "✦ ${it.key}  →  ${it.value}" }.toTypedArray()) { _, which ->
                val e = entries[which]
                AlertDialog.Builder(this).setItems(arrayOf("Изменить", "Удалить")) { _, choice ->
                    if (choice == 0) editSecret(e.key, e.value)
                    else { val updated = secretMap(); updated.remove(e.key); saveSecrets(updated); toast("Команда удалена") }
                }.show()
            }.setPositiveButton("Готово", null).show()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }
    private fun refresh() {
        sOverlay.text = if (Settings.canDrawOverlays(this)) "✓ Включено" else "✕ Не включено"
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationsOk = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        sMic.text = (if (micOk) "✓ Микрофон разрешён" else "✕ Нет доступа к микрофону") +
            if (Build.VERSION.SDK_INT >= 33) (if (notificationsOk) "\\n✓ Уведомления разрешены" else "\\n⚠ Уведомления отключены") else ""
        sAcc.text = if (JarvisAccessibilityService.instance != null) "✓ Включено" else "✕ Не включено"
        sKey.text = if (!prefs.getString("api_key", "").isNullOrBlank()) "Облачный ключ сохранён (необязательно)" else "Облачный ключ не задан. Секретные команды работают без него."
        startBtn.text = if (JarvisService.running) "Остановить Джарвиса" else "Запустить Джарвиса"
    }
    private fun toggle() {
        if (JarvisService.running) stopService(Intent(this, JarvisService::class.java))
        else {
            if (!Settings.canDrawOverlays(this)) { toast("Сначала разреши показ поверх окон"); return }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { toast("Разреши микрофон"); return }
            startForegroundService(Intent(this, JarvisService::class.java)); toast("Джарвис запущен")
        }
        ui.postDelayed({ refresh() }, 500)
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
