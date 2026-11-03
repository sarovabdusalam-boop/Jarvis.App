package com.jarvis.assistant

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class ChatActivity : Activity() {
    private lateinit var log: LinearLayout
    private lateinit var field: EditText
    private lateinit var info: TextView
    private val prefs by lazy { getSharedPreferences("jarvis_chat", Context.MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,16,16,16); setBackgroundColor(0xFF071321.toInt()) }
        root.addView(TextView(this).apply { text="ДЖАРВИС / ЧАТ"; textSize=22f; setTextColor(0xFF00E5FF.toInt()) })
        info=TextView(this).apply { setTextColor(0xFFB0BEC5.toInt()); textSize=12f }
        root.addView(info)
        root.addView(Button(this).apply { text="Скачать локальный ИИ (647 МБ, Wi-Fi)"; setOnClickListener { downloadModel() } })
        val scroll=ScrollView(this)
        log=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(log); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val row=LinearLayout(this)
        field=EditText(this).apply { hint="Напиши Джарвису"; setTextColor(Color.WHITE) }
        row.addView(field,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(Button(this).apply { text="➤"; setOnClickListener { send() } })
        root.addView(row); setContentView(root)
        restore()
        refresh()
    }
    private fun downloadModel() {
        if (LocalAi.isDownloaded(this)) { refresh(); return }
        try {
            val req=DownloadManager.Request(android.net.Uri.parse("https://huggingface.co/litert-community/Qwen2-0.5B-Instruct/resolve/main/Qwen2_0.5B_Instruct.litertlm"))
                .setTitle("Локальный мозг Джарвиса").setDescription("Qwen 0.5B • около 647 МБ")
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,LocalAi.MODEL_FILE)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            info.text="Загрузка началась. Следи за уведомлением Android."
        } catch(e:Exception) { info.text="Ошибка загрузки: ${e.message}" }
    }
    private fun refresh() {
        info.text=if(LocalAi.isReady()) "Локальный ИИ готов — работает без интернета."
        else if(LocalAi.isDownloaded(this)) "Модель установлена. Первый ответ загрузит её в память."
        else "Скачай модель один раз по Wi-Fi. После этого ответы будут офлайн."
    }
    private fun send() {
        val q=field.text.toString().trim(); if(q.isEmpty()) return
        field.text.clear(); add("Ты",q); save("Ты",q)
        val custom=Commands.trySecret(this,q)
        if(custom!=null) { add("Джарвис",custom); save("Джарвис",custom); return }
        if(!LocalAi.isDownloaded(this)) {
            val answer="Сначала скачай локальную модель кнопкой выше. Она бесплатная и после загрузки работает без интернета."
            add("Джарвис",answer); save("Джарвис",answer); return
        }
        add("Джарвис","Думаю…")
        Thread {
            val answer=try { LocalAi.ask(this,q) } catch(e:Exception) { "Ошибка локального ИИ: ${e.message}" }
            runOnUiThread {
                if(log.childCount>0) log.removeViewAt(log.childCount-1)
                add("Джарвис",answer); save("Джарвис",answer); refresh()
            }
        }.start()
    }
    private fun add(w:String,t:String) {
        log.addView(TextView(this).apply { text="$w: $t"; textSize=15f; setTextColor(if(w=="Ты") 0xFF00E5FF.toInt() else Color.WHITE); setPadding(12,12,12,12) })
    }
    private fun save(w:String,t:String) {
        val a=try { JSONArray(prefs.getString("messages","[]")) } catch(_:Exception) { JSONArray() }
        a.put(JSONObject().put("who",w).put("text",t)); while(a.length()>80) a.remove(0)
        prefs.edit().putString("messages",a.toString()).apply()
    }
    private fun restore() {
        val a=try { JSONArray(prefs.getString("messages","[]")) } catch(_:Exception) { JSONArray() }
        for(i in 0 until a.length()) { val o=a.optJSONObject(i)?:continue; add(o.optString("who"),o.optString("text")) }
    }
}