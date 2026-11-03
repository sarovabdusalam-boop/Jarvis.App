package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
            private set
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}