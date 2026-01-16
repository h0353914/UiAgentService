package com.example.uiagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * "方案 B"：透過 `adb shell am broadcast` 與 UiAgent 溝通。
 *
 * 目的：避免 WSL/Windows 的 127.0.0.1 / adb forward 造成的 ConnectionRefused。
 *
 * PC 端範例：
 *   # 檢查按鈕是否存在
 *   adb shell am broadcast -a com.example.uiagent.CMD --es cmd exists_rid --es rid com.sonyericsson.android.camera:id/main_button
 *
 *   # 點擊
 *   adb shell am broadcast -a com.example.uiagent.CMD --es cmd click_rid --es rid com.sonyericsson.android.camera:id/main_button
 *
 *   # 等待出現（最多 1200ms）
 *   adb shell am broadcast -a com.example.uiagent.CMD --es cmd wait_exists_rid --es rid com.sonyericsson.android.camera:id/main_button --ei timeout_ms 1200
 *
 * 回傳：透過 Broadcast result-data 回一段 JSON 字串。
 * `am broadcast` 會印出：
 *   data="{...}"
 */
class UiAgentCmdReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UiAgentCmdReceiver"
        const val ACTION_CMD = "com.example.uiagent.CMD"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CMD) return

        val cmd = intent.getStringExtra("cmd") ?: ""
        val rid = intent.getStringExtra("rid") ?: ""
        val desc = intent.getStringExtra("desc") ?: ""
        val timeoutMs = (intent.getIntExtra("timeout_ms", 1200)).coerceIn(50, 10000)

        // Some operations (click via dispatchGesture, waiting loops) must not block the main
        // looper thread, otherwise gesture dispatch/callbacks can stall and always time out.
        // Use goAsync() and run them in a background thread.
        val isAsyncCmd = cmd == "click_rid" || cmd == "click_desc" || cmd == "wait_exists_rid"

        if (isAsyncCmd) {
            val pending = goAsync()
            Thread {
                val t0 = System.nanoTime()
                val resp = runCommand(cmd, rid, desc, timeoutMs, t0)
                pending.resultData = resp
                pending.finish()
            }.start()
            return
        }

        val t0 = System.nanoTime()
        setResultData(runCommand(cmd, rid, desc, timeoutMs, t0))
    }

    private fun runCommand(
        cmd: String,
        rid: String,
        desc: String,
        timeoutMs: Int,
        t0: Long,
    ): String {
        val acc = UiAgentAccessibilityService.instance
        if (acc == null) {
            return "{\"ok\":false,\"error\":\"accessibility_not_enabled\"}"
        }

        return try {
            when (cmd) {
                "ping" -> {
                    "{\"ok\":true,\"cmd\":\"ping\",\"elapsed_ms\":${elapsedMs(t0)}}"
                }
                "exists_rid" -> {
                    val ex = acc.existsByViewId(rid)
                    "{\"ok\":true,\"cmd\":\"exists_rid\",\"rid\":${jsonQuote(rid)},\"exists\":$ex,\"elapsed_ms\":${elapsedMs(t0)}}"
                }
                "click_rid" -> {
                    val clicked = acc.clickByViewId(rid)
                    "{\"ok\":true,\"cmd\":\"click_rid\",\"rid\":${jsonQuote(rid)},\"clicked\":$clicked,\"elapsed_ms\":${elapsedMs(t0)}}"
                }
                "exists_desc" -> {
                    val ex = acc.existsByDesc(desc)
                    "{\"ok\":true,\"cmd\":\"exists_desc\",\"desc\":${jsonQuote(desc)},\"exists\":$ex,\"elapsed_ms\":${elapsedMs(t0)}}"
                }
                "click_desc" -> {
                    val clicked = acc.clickByDesc(desc)
                    "{\"ok\":true,\"cmd\":\"click_desc\",\"desc\":${jsonQuote(desc)},\"clicked\":$clicked,\"elapsed_ms\":${elapsedMs(t0)}}"
                }
                "wait_exists_rid" -> {
                    val end = System.currentTimeMillis() + timeoutMs
                    var ex = acc.existsByViewId(rid)
                    while (!ex && System.currentTimeMillis() < end) {
                        Thread.sleep(50)
                        ex = acc.existsByViewId(rid)
                    }
                    "{\"ok\":true,\"cmd\":\"wait_exists_rid\",\"rid\":${jsonQuote(rid)},\"exists\":$ex,\"timeout_ms\":$timeoutMs,\"elapsed_ms\":${elapsedMs(t0)}}"
                }
                else -> {
                    "{\"ok\":false,\"error\":\"unknown_cmd\",\"cmd\":${jsonQuote(cmd)}}"
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "cmd failed: $cmd", t)
            "{\"ok\":false,\"error\":${jsonQuote(t.toString())}}"
        }
    }

    private fun elapsedMs(t0: Long): Long = (System.nanoTime() - t0) / 1_000_000L

    private fun jsonQuote(s: String): String {
        val esc = s.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$esc\""
    }
}
