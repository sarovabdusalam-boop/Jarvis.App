package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
            private set
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    fun pasteIntoFocusedField(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Джарвис", text))
        return target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
    fun readVisibleScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || out.length > 12000) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return out.toString().trim()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}