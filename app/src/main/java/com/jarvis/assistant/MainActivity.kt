package com.jarvis.assistant

import android.Manifest
import android.app.Activity
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
        col.addView(tv("Голосовой помощник. Пройди 5 шагов, потом нажми «Запустить».", 14f, grey, 6))
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
        col.addView(tv("3. Служба доступности (команды «назад», «домой», «недавние»)", 16f, top = 18))
        sAcc = tv("", 13f, grey); col.addView(sAcc)
        col.addView(tv(
            "Если переключатель серый: Настройки → Приложения → Джарвис → ⋮ (три точки) → «Разрешить ограниченные настройки». Потом вернись сюда.",
            12f, grey, 4
        ))
        col.addView(btn("Открыть настройки доступности") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(tv("4. Ключ Claude API (чтобы отвечать на вопросы)", 16f, top = 18))
        sKey = tv("", 13f, grey); col.addView(sKey)
        keyInput = EditText(this).apply {
            hint = "sk-ant-..."; setHintTextColor(0xFF607D8B.toInt()); setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        col.addView(keyInput)
        col.addView(btn("Сохранить ключ") {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty()) toast("Сначала вставь ключ Claude API")
            else { prefs.edit().putString("api_key", key).apply(); toast("Ключ сохранён на этом телефоне"); refresh() }
        })
        col.addView(btn("Удалить сохранённый ключ") {
            prefs.edit().remove("api_key").apply(); keyInput.text.clear(); toast("Ключ удалён с этого телефона"); refresh()
        })
        col.addView(tv("5. Не давать системе усыплять Джарвиса", 16f, top = 18))
        col.addView(tv("На Tecno ещё включи автозапуск и «Без ограничений» для батареи у этого приложения.", 12f, grey, 4))
        col.addView(btn("Открыть настройки батареи") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        startBtn = btn("Запустить Джарвиса") { toggle() }
        col.addView(startBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(26) })
        col.addView(tv(
            "Примеры команд:\n• «Открой ютуб», «запусти телеграм», «открой камеру»\n" +
                "• «Назад», «домой», «недавние приложения», «скриншот»\n" +
                "• Любой вопрос — ответит голосом (нужен ключ)",
            13f, grey, 22
        ))
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF0A1A2F.toInt()); addView(col) })
        prefs.getString("api_key", "")?.let { keyInput.setText(it) }
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }
    private fun refresh() {
        sOverlay.text = if (Settings.canDrawOverlays(this)) "✅ Включено" else "❌ Не включено"
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationsOk = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        sMic.text = buildString {
            append(if (micOk) "✅ Микрофон разрешён" else "❌ Нет доступа к микрофону")
            if (Build.VERSION.SDK_INT >= 33) append(if (notificationsOk) "\n✅ Уведомления разрешены" else "\n⚠️ Уведомления отключены")
        }
        sAcc.text = if (JarvisAccessibilityService.instance != null) "✅ Включено" else "❌ Не включено"
        sKey.text = if (!prefs.getString("api_key", "").isNullOrBlank()) "✅ Ключ сохранён" else "❌ Ключа нет"
        startBtn.text = if (JarvisService.running) "Остановить Джарвиса" else "Запустить Джарвиса"
    }
    private fun toggle() {
        if (JarvisService.running) stopService(Intent(this, JarvisService::class.java))
        else {
            if (!Settings.canDrawOverlays(this)) { toast("Сначала шаг 1"); return }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                toast("Сначала шаг 2"); return
            }
            startForegroundService(Intent(this, JarvisService::class.java))
            toast("Джарвис запущен — ищи кружок на экране")
        }
        ui.postDelayed({ refresh() }, 500)
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}