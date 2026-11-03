package com.jarvis.assistant

import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.graphics.Color

class ChatActivity : Activity() {
    private lateinit var log: LinearLayout
    private lateinit var field: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20); setBackgroundColor(0xFF071321.toInt()) }
        root.addView(TextView(this).apply { text="ДЖАРВИС / ЧАТ"; textSize=22f; setTextColor(0xFF00E5FF.toInt()) })
        root.addView(Button(this).apply { text="Скачать локальный ИИ"; setOnClickListener { Toast.makeText(this@ChatActivity,"Модель будет подключена после проверки сборки",Toast.LENGTH_LONG).show() } })
        val scroll=ScrollView(this)
        log=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(log); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val row=LinearLayout(this)
        field=EditText(this).apply { hint="Напиши Джарвису"; setTextColor(Color.WHITE) }
        row.addView(field,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(Button(this).apply { text="➤"; setOnClickListener { send() } })
        root.addView(row); setContentView(root)
    }
    private fun send() {
        val q=field.text.toString().trim()
        if(q.isEmpty()) return
        field.text.clear()
        val secret=Commands.trySecret(this,q)
        add("Ты",q)
        add("Джарвис",secret ?: "Для ответов без интернета нужно установить локальную ИИ-модель. Секретные команды уже доступны.")
    }
    private fun add(w:String,t:String) {
        log.addView(TextView(this).apply { text="$w: $t"; textSize=16f; setTextColor(Color.WHITE); setPadding(12,12,12,12) })
    }
}