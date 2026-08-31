// 对应 prd.md §3.1.8 隐藏设置入口
// A 区中央连点 5 次（每次间隔 ≤ 500ms）→ 进入 §3.1.8 设置。
// PR 1：仅弹 Toast「设置即将开放」，不进设置屏。
package com.elder.android.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable

object HomeHiddenSettingTrigger {
    private const val MAX_INTERVAL_MS = 500L
    private const val REQUIRED_TAPS = 5
    private var tapCount by mutableIntStateOf(0)
    private var lastTapAt by mutableLongStateOf(0L)
    private var pendingToast by mutableLongStateOf(0L)

    @Composable
    fun Install() {
        // 提供一个 Composable hook 用于挂 Toast context；实际触发在 onTap 中
        // 这里不做事，仅占位
    }

    fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapAt > MAX_INTERVAL_MS) {
            tapCount = 0
        }
        tapCount += 1
        lastTapAt = now
        if (tapCount >= REQUIRED_TAPS) {
            tapCount = 0
            pendingToast = now
        }
    }
}
