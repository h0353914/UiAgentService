package com.example.uiagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UiAgentAccessibilityService : AccessibilityService() {

    // dispatchGesture callbacks are delivered on the Handler thread you pass in.
    // We use a dedicated HandlerThread so we can safely wait synchronously
    // (e.g. from BroadcastReceiver / binder call paths) without deadlocking
    // the main thread/looper.
    private var gestureThread: HandlerThread? = null
    private var gestureHandler: Handler? = null
    private var mainHandler: Handler? = null

    companion object {
        @Volatile
        var instance: UiAgentAccessibilityService? = null

        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getInt(
                ctx.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (!enabled) return false

            val setting = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val expected = ctx.packageName + "/" + UiAgentAccessibilityService::class.java.name
            val parts = setting.split(':')
            return parts.any { TextUtils.equals(it, expected) }
        }
    }

    override fun onServiceConnected() {
        instance = this

        mainHandler = Handler(Looper.getMainLooper())

        // Start (or restart) the gesture callback thread.
        gestureThread?.quitSafely()
        gestureThread = HandlerThread("uiagent-gesture").apply { start() }
        gestureHandler = Handler(gestureThread!!.looper)
    }

    override fun onDestroy() {
        instance = null

        gestureThread?.quitSafely()
        gestureThread = null
        gestureHandler = null
        mainHandler = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op; operate on-demand via socket commands
    }

    override fun onInterrupt() {
        // no-op
    }

    fun existsByViewId(fullRid: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findFirstByViewId(root, fullRid) != null
    }

    fun clickByViewId(fullRid: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findFirstByViewId(root, fullRid) ?: return false
        return performClickUpTree(node)
    }

    fun existsByDesc(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findFirstByContentDesc(root, desc) != null
    }

    fun clickByDesc(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findFirstByContentDesc(root, desc) ?: return false
        return performClickUpTree(node)
    }

    private fun performClickUpTree(node: AccessibilityNodeInfo): Boolean {
        // 1) Prefer ACTION_CLICK on a clickable ancestor (including self)
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) {
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                // Clickable but action failed -> keep walking up, then fallback to gesture.
            }
            n = n.parent
        }

        // 2) Fallback: tap the original node bounds center using dispatchGesture
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false

        val cx = r.centerX().toFloat()
        val cy = r.centerY().toFloat()
        return dispatchTap(cx, cy)
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val doneLatch = CountDownLatch(1)
        val acceptLatch = CountDownLatch(1)
        var ok = false
        var accepted = false

        // dispatchGesture is safest when invoked on the main looper.
        val mh = mainHandler
        if (mh == null) return false

        mh.post {
            accepted = dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        ok = true
                        doneLatch.countDown()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        ok = false
                        doneLatch.countDown()
                    }
                },
                gestureHandler
            )
            acceptLatch.countDown()
            if (!accepted) {
                // Ensure waiters are released even if gesture was rejected immediately.
                doneLatch.countDown()
            }
        }

        // Wait briefly for the dispatchGesture() call to run.
        acceptLatch.await(250, TimeUnit.MILLISECONDS)
        if (!accepted) return false

        // Wait for completion/cancel up to ~900ms.
        doneLatch.await(900, TimeUnit.MILLISECONDS)
        return ok
    }

    private fun findFirstByViewId(root: AccessibilityNodeInfo, fullRid: String): AccessibilityNodeInfo? {
        if (fullRid == root.viewIdResourceName) return root
        for (i in 0 until root.childCount) {
            val c = root.getChild(i) ?: continue
            val hit = findFirstByViewId(c, fullRid)
            if (hit != null) return hit
        }
        return null
    }

    private fun findFirstByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val cd = root.contentDescription?.toString()
        if (cd == desc) return root
        for (i in 0 until root.childCount) {
            val c = root.getChild(i) ?: continue
            val hit = findFirstByContentDesc(c, desc)
            if (hit != null) return hit
        }
        return null
    }
}
