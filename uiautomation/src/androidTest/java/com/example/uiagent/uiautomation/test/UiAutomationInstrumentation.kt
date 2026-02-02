package com.example.uiagent.uiautomation.test

import android.app.Instrumentation
import android.app.UiAutomation
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation 测试类，用于通过 UiAutomation API 访问系统权限对话框等受限 UI。
 *
 * 运行方式：
 *   adb shell am instrument -w -e class com.example.uiagent.uiautomation.test.UiAutomationInstrumentation \
 *     com.example.uiagent.uiautomation.test/androidx.test.runner.AndroidJUnitRunner
 *
 * 或通过 broadcast 触发特定操作：
 *   adb shell am broadcast -a com.example.uiagent.uiautomation.UIAUTOMATION_CMD --es cmd list_elements
 */
@RunWith(AndroidJUnit4::class)
class UiAutomationInstrumentation {

    companion object {
        private const val TAG = "UiAutomationInstr"

        // 用于从外部触发 UiAutomation 操作的 broadcast action
        const val ACTION_UIAUTOMATION_CMD = "com.example.uiagent.uiautomation.UIAUTOMATION_CMD"
    }

    private fun getInstrumentation(): Instrumentation {
        return InstrumentationRegistry.getInstrumentation()
    }

    private fun getUiAutomation(): UiAutomation {
        return getInstrumentation().uiAutomation
    }

    @Test
    fun startUiAutomationService() {
        Log.d(TAG, "UiAutomation service started")

        // 创建 UiAutomation 辅助类实例
        val uiAutomation = getUiAutomation()
        val helper = UiAutomationAccessor(uiAutomation)

        // 启动监听器，等待来自 broadcast 的命令
        val receiver = UiAutomationCmdReceiver(helper)
        val context = getInstrumentation().targetContext

        // 注册 broadcast receiver
        val filter = android.content.IntentFilter(ACTION_UIAUTOMATION_CMD)
        try {
            // Android 12+ 需要指定 flag
            context.registerReceiver(
                receiver,
                filter,
                android.content.Context.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            // Android 11 及以下版本
            try {
                context.registerReceiver(receiver, filter)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to register receiver: ${e2.message}")
                throw e2
            }
        }

        Log.d(TAG, "UiAutomation command receiver registered successfully")
        Log.d(TAG, "Ready to accept commands via: adb shell am broadcast -a $ACTION_UIAUTOMATION_CMD --es cmd <command>")

        // 保持运行，直到手动停止
        synchronized(this) {
            try {
                // 10 分钟后自动超时
                (this as Object).wait(10 * 60 * 1000)
            } catch (e: InterruptedException) {
                Log.d(TAG, "UiAutomation service interrupted")
            }
        }

        // 清理
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "Receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}")
        }
    }

    @Test
    fun testListAllElements() {
        val uiAutomation = getUiAutomation()
        val helper = UiAutomationAccessor(uiAutomation)

        val elements = helper.listAllElements()
        Log.d(TAG, "Found ${elements.size} elements")

        elements.forEachIndexed { index, element ->
            Log.d(
                TAG,
                "[$index] rid=${element["rid"]} text=${element["text"]} desc=${element["desc"]}"
            )
        }
    }

    @Test
    fun testFindPermissionButtons() {
        val uiAutomation = getUiAutomation()
        val helper = UiAutomationAccessor(uiAutomation)

        val buttons = helper.findPermissionDialogButtons()
        Log.d(TAG, "Found ${buttons.size} permission dialog buttons")

        buttons.forEach { (type, info) ->
            Log.d(TAG, "$type: rid=${info["rid"]} text=${info["text"]}")
        }
    }

    @Test
    fun testClickPermissionButton() {
        val uiAutomation = getUiAutomation()
        val helper = UiAutomationAccessor(uiAutomation)

        // 测试点击 "仅允许这一次" 按钮
        val clicked = helper.clickByResourceId(
            "com.android.permissioncontroller:id/permission_allow_one_time_button"
        )

        Log.d(TAG, "Click result: $clicked")
    }
}
