package com.jarvis.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class JarvisService : Service() {
    companion object {
        const val ACTION_STOP = "com.jarvis.assistant.STOP"
        const val CHANNEL_ID = "jarvis"
        @Volatile var running = false
        @Volatile var dictationMode = false
    }

    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var bubble: JarvisOrbView
    private lateinit var params: WindowManager.LayoutParams
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var wakeMode = false
    private var wakeArmed = true
    private val wakeRetry = Runnable { if (running && wakeArmed && !listening) startWakeListening() }
    private var added = false
    private val history = ArrayList<Pair<String, String>>()
    private val hideRunnable = Runnable { status.visibility = View.GONE }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("ru", "RU"))
                ttsReady = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (!added) addOverlay()
        running = true
        wakeArmed = getSharedPreferences("jarvis", MODE_PRIVATE).getBoolean("wake_word_enabled", true)
        if (wakeArmed) main.postDelayed(wakeRetry, 900)
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Джарвис", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, JarvisService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Джарвис работает")
            .setContentText("Нажми на кружок и говори")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Выключить", stop).build())
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun addOverlay() {
        val dp = resources.displayMetrics.density
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        status = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; visibility = View.GONE
            maxWidth = (240 * dp).toInt()
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable().apply { setColor(0xCC0A1A2F.toInt()); cornerRadius = 16 * dp }
        }
        bubble = JarvisOrbView(this).apply {
            contentDescription = "Джарвис. Нажми, чтобы заговорить"
        }
        root.addView(status, LinearLayout.LayoutParams(-2, -2))
        root.addView(bubble, LinearLayout.LayoutParams((92 * dp).toInt(), (92 * dp).toInt()).apply {
            topMargin = (4 * dp).toInt()
        })
        params = WindowManager.LayoutParams(
            -2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (16 * dp).toInt(); y = (220 * dp).toInt() }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) { params.x = startX + dx.toInt(); params.y = startY + dy.toInt(); wm.updateViewLayout(root, params) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onBubbleTap(); true }
                else -> false
            }
        }
        wm.addView(root, params); added = true
        showStatus("Я здесь. Скажи «Джарвис» или нажми на кружок."); hideLater(4000)
    }

    private fun showStatus(t: String) { main.removeCallbacks(hideRunnable); status.text = t; status.visibility = View.VISIBLE }
    private fun hideLater(ms: Long = 7000) { main.removeCallbacks(hideRunnable); main.postDelayed(hideRunnable, ms) }
    private fun setListening(on: Boolean) {
        listening = on
        bubble.setMode(if (on) JarvisOrbView.Mode.LISTENING else JarvisOrbView.Mode.IDLE)
    }
    private fun onBubbleTap() { if (listening) recognizer?.stopListening() else { wakeMode = false; main.removeCallbacks(wakeRetry); startListening() } }

    private fun startWakeListening() {
        if (!wakeArmed || !running || listening) return
        wakeMode = true
        startListening()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showStatus("Нет доступа к микрофону. Открой приложение Джарвис и разреши."); hideLater(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showStatus("На телефоне нет распознавания речи (нужно приложение Google)."); hideLater(); return
        }
        tts?.stop(); recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(i)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) { setListening(true); showStatus("Слушаю…") }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { setListening(false) }
        override fun onError(error: Int) {
            setListening(false)
            if (wakeMode && wakeArmed) { main.removeCallbacks(wakeRetry); main.postDelayed(wakeRetry, 2500); return }
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не расслышал. Нажми ещё раз."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Нет интернета для распознавания речи."
                else -> "Ошибка распознавания ($error)."
            }
            showStatus(msg); hideLater()
        }
        override fun onResults(results: Bundle?) {
            setListening(false)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (wakeMode && wakeArmed) {
                val spoken = text.orEmpty()
                val normalized = spoken.lowercase(Locale("ru")).replace("ё", "е")
                val index = normalized.indexOf("джарвис")
                if (index < 0) {
                    main.removeCallbacks(wakeRetry); main.postDelayed(wakeRetry, 350)
                    return
                }
                val command = spoken.substring((index + "джарвис".length).coerceAtMost(spoken.length)).trim(' ', ',', '.', '!', '?', ':')
                wakeMode = false
                if (command.isBlank()) {
                    reply("Слушаю.")
                    main.removeCallbacks(wakeRetry); main.postDelayed(wakeRetry, 1300)
                } else process(command)
                return
            }
            if (text.isNullOrBlank()) { showStatus("Не расслышал."); hideLater() } else process(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) showStatus(t)
        }
        override fun onEvent(eventType: Int, p: Bundle?) {}
    }

    private fun process(text: String) {
        showStatus("Вы: " + text)
        val currencyQuery = text.lowercase(Locale("ru"))
        if ((currencyQuery.contains("курс") && (currencyQuery.contains("доллар") || currencyQuery.contains("usd"))) ||
            currencyQuery.contains("сколько тенге за доллар")) {
            bubble.setMode(JarvisOrbView.Mode.THINKING)
            showStatus("Проверяю актуальный курс…")
            Thread {
                val answer = try { CurrencyClient.usdToKzt() }
                catch (_: Exception) { "Не удалось получить курс сейчас. Проверь подключение к интернету и попробуй ещё раз." }
                main.post { reply(answer) }
            }.start()
            return
        }
        val normalized = text.trim().lowercase(Locale("ru"))
        if (normalized == "переведи" || normalized == "переведи экран" || normalized == "переведи текст на экране") {
            val visible = JarvisAccessibilityService.instance?.readVisibleScreenText().orEmpty()
            if (visible.isBlank()) {
                reply("Не вижу текста на экране. Открой страницу с текстом и повтори команду.")
                return
            }
            bubble.setMode(JarvisOrbView.Mode.THINKING)
            showStatus("Перевожу текст с экрана…")
            Thread {
                val answer = try { TranslatorClient.translateToRussian(visible) }
                catch (_: Exception) { "Не удалось перевести текст. Проверь интернет и попробуй ещё раз." }
                main.post { reply(answer) }
            }.start()
            return
        }
        if (dictationMode) {
            if (normalized == "стоп" || normalized == "хватит" || normalized == "закончи" ||
                normalized == "останови диктовку" || normalized == "стоп диктовка") {
                dictationMode = false
                reply("Диктовка остановлена.")
                return
            }
            val pasted = JarvisAccessibilityService.instance?.pasteIntoFocusedField(text) == true
            reply(if (pasted) "Записал." else "Не вижу активного поля ввода. Открой место, куда нужно писать, и снова скажи текст.")
            return
        }
        val secret = try { Commands.trySecret(this, text) } catch (_: Exception) { null }
        if (secret != null) { reply(secret); return }
        val local = try { Commands.tryHandle(this, text) } catch (e: Exception) { "Не получилось: " + e.message }
        if (local != null) { reply(local); return }
        val key = getSharedPreferences("jarvis", MODE_PRIVATE).getString("api_key", "").orEmpty()
        if (key.isBlank()) {
            if (!LocalAi.isDownloaded(this)) { reply("Для бесплатного ИИ открой приложение Джарвис → Текстовый чат → скачай локальную модель по Wi-Fi."); return }
            bubble.setMode(JarvisOrbView.Mode.THINKING)
            showStatus("Запускаю локальный мозг…")
            Thread {
                val answer = try { LocalAi.ask(this, text) } catch (e: Exception) { "Локальный ИИ: " + e.message }
                main.post { reply(answer) }
            }.start()
            return
        }
        bubble.setMode(JarvisOrbView.Mode.THINKING)
        showStatus("Думаю…")
        if (history.size >= 8) { history.removeAt(0); history.removeAt(0) }
        history.add("user" to text)
        val snapshot = ArrayList(history)
        Thread {
            var ok = true
            val answer = try { AIClient.ask(key, snapshot) }
            catch (e: ApiException) {
                ok = false
                when (e.code) {
                    401 -> "Неверный ключ Gemini."
                    429 -> "Слишком много запросов. Подожди минуту."
                    else -> "Ошибка Gemini: " + e.message
                }
            } catch (e: Exception) { ok = false; "Нет связи с интернетом." }
            main.post {
                if (ok) history.add("assistant" to answer) else if (history.isNotEmpty()) history.removeAt(history.size - 1)
                reply(answer)
            }
        }.start()
    }

    private fun reply(text: String) {
        bubble.setMode(JarvisOrbView.Mode.IDLE)
        showStatus(text); hideLater(6000L + text.length * 60L)
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        if (wakeArmed && running) {
            main.removeCallbacks(wakeRetry)
            main.postDelayed(wakeRetry, (1800L + text.length * 65L).coerceAtMost(9000L))
        }
    }

    override fun onDestroy() {
        running = false; dictationMode = false; main.removeCallbacksAndMessages(null)
        if (added) try { wm.removeView(root) } catch (_: Exception) {}
        recognizer?.destroy(); tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }
}
