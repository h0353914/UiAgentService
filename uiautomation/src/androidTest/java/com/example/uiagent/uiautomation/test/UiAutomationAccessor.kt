package com.example.uiagent.uiautomation.test

import android.app.UiAutomation
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * UiAutomation 访问器 - 独立于 Accessibility Service 的 UI 自动化模块。
 *
 * 这个类专门用于通过 Instrumentation/UiAutomation API 访问系统级 UI，
 * 包括权限对话框等 Accessibility Service 无法访问的元素。
 *
 * 重要：不要在 Accessibility Service 的 Context 中使用此类！
 */
class UiAutomationAccessor(private val uiAutomation: UiAutomation) {

    companion object {
        private const val TAG = "UiAutomationAccessor"
    }

    /**
     * 获取当前活动窗口的根节点。
     */
    fun getRootInActiveWindow(): AccessibilityNodeInfo? {
        return try {
            uiAutomation.rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get root node", e)
            null
        }
    }

    /**
     * 递归查找指定 resource-id 的节点。
     */
    fun findNodeByResourceId(resourceId: String): AccessibilityNodeInfo? {
        // 方法 1：在活动窗口中查找
        val root = getRootInActiveWindow()
        if (root != null) {
            val result = findNodeByResourceIdRecursive(root, resourceId)
            if (result != null) return result
            root.recycle()
        }

        // 方法 2：在所有窗口中查找
        try {
            for (window in uiAutomation.windows) {
                val windowRoot = window.root ?: continue
                val result = findNodeByResourceIdRecursive(windowRoot, resourceId)
                if (result != null) {
                    windowRoot.recycle()
                    return result
                }
                windowRoot.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search all windows: ${e.message}")
        }

        return null
    }

    private fun findNodeByResourceIdRecursive(
        node: AccessibilityNodeInfo,
        resourceId: String
    ): AccessibilityNodeInfo? {
        try {
            if (node.viewIdResourceName == resourceId) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findNodeByResourceIdRecursive(child, resourceId)
                if (result != null) return result
                child.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findNodeByResourceIdRecursive", e)
        }

        return null
    }

    /**
     * 查找包含指定文本的节点。
     */
    fun findNodeByText(text: String, exact: Boolean = true): AccessibilityNodeInfo? {
        // 方法 1：在活动窗口中查找
        val root = getRootInActiveWindow()
        if (root != null) {
            val result = findNodeByTextRecursive(root, text, exact)
            if (result != null) return result
            root.recycle()
        }

        // 方法 2：在所有窗口中查找
        try {
            for (window in uiAutomation.windows) {
                val windowRoot = window.root ?: continue
                val result = findNodeByTextRecursive(windowRoot, text, exact)
                if (result != null) {
                    windowRoot.recycle()
                    return result
                }
                windowRoot.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search all windows: ${e.message}")
        }

        return null
    }

    private fun findNodeByTextRecursive(
        node: AccessibilityNodeInfo,
        text: String,
        exact: Boolean
    ): AccessibilityNodeInfo? {
        try {
            val nodeText = node.text?.toString() ?: ""
            val match = if (exact) {
                nodeText == text
            } else {
                nodeText.contains(text)
            }

            if (match) return node

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findNodeByTextRecursive(child, text, exact)
                if (result != null) return result
                child.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findNodeByTextRecursive", e)
        }

        return null
    }

    /**
     * 通过 resource-id 点击节点。
     */
    fun clickByResourceId(resourceId: String): Boolean {
        val node = findNodeByResourceId(resourceId) ?: run {
            Log.w(TAG, "Node not found: $resourceId")
            return false
        }

        return try {
            val result = if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                Log.w(TAG, "Node is not clickable: $resourceId")
                false
            }
            node.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking node: $resourceId", e)
            false
        }
    }

    /**
     * 通过文本点击节点。
     */
    fun clickByText(text: String, exact: Boolean = true): Boolean {
        val node = findNodeByText(text, exact) ?: run {
            Log.w(TAG, "Node not found with text: $text")
            return false
        }

        return try {
            val result = if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                Log.w(TAG, "Node is not clickable with text: $text")
                false
            }
            node.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking node with text: $text", e)
            false
        }
    }

    /**
     * 列出所有 UI 元素。
     */
    fun listAllElements(): List<Map<String, String>> {
        val elements = mutableListOf<Map<String, String>>()

        // 方法 1：尝试活动窗口
        val root = getRootInActiveWindow()
        if (root != null) {
            Log.d(TAG, "Found active window root")
            listAllElementsRecursive(root, elements)
            root.recycle()
        } else {
            Log.w(TAG, "Active window root is null")
        }

        // 方法 2：尝试所有窗口（Android 8+）
        try {
            val windows = uiAutomation.windows
            Log.d(TAG, "Trying to access ${windows.size} windows")

            for (i in 0 until windows.size) {
                try {
                    val window = windows[i]
                    val windowRoot = window.root
                    if (windowRoot != null) {
                        Log.d(TAG, "Window[$i]: ${window.title} - found root")
                        listAllElementsRecursive(windowRoot, elements)
                        windowRoot.recycle()
                    } else {
                        Log.d(TAG, "Window[$i]: ${window.title} - root is null")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error accessing window[$i]: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to access all windows: ${e.message}")
            // getWindows() may not be available on older Android versions
        }

        Log.d(TAG, "Found ${elements.size} total elements")
        return elements
    }

    private fun listAllElementsRecursive(
        node: AccessibilityNodeInfo,
        elements: MutableList<Map<String, String>>
    ) {
        try {
            val rid = node.viewIdResourceName
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""

            if (rid != null || text.isNotEmpty() || desc.isNotEmpty()) {
                val item = mutableMapOf<String, String>()
                if (rid != null) item["rid"] = rid
                if (text.isNotEmpty()) item["text"] = text
                if (desc.isNotEmpty()) item["desc"] = desc
                elements.add(item)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                listAllElementsRecursive(child, elements)
                child.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in listAllElementsRecursive", e)
        }
    }

    /**
     * 查找权限对话框按钮。
     */
    fun findPermissionDialogButtons(): Map<String, Map<String, String>> {
        val buttons = mutableMapOf<String, Map<String, String>>()

        val buttonIds = mapOf(
            "allow_foreground" to "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "allow_once" to "com.android.permissioncontroller:id/permission_allow_one_time_button",
            "deny" to "com.android.permissioncontroller:id/permission_deny_button",
            "allow" to "com.android.permissioncontroller:id/permission_allow_button"
        )

        for ((type, id) in buttonIds) {
            val node = findNodeByResourceId(id)
            if (node != null) {
                val info = mapOf(
                    "rid" to id,
                    "text" to (node.text?.toString() ?: "")
                )
                buttons[type] = info
                node.recycle()
            }
        }

        return buttons
    }

    /**
     * 检查 resource-id 是否存在。
     */
    fun existsByResourceId(resourceId: String): Boolean {
        val node = findNodeByResourceId(resourceId)
        if (node != null) {
            node.recycle()
            return true
        }
        return false
    }

    /**
     * 检查文本是否存在。
     */
    fun existsByText(text: String, exact: Boolean = true): Boolean {
        val node = findNodeByText(text, exact)
        if (node != null) {
            node.recycle()
            return true
        }
        return false
    }
}
