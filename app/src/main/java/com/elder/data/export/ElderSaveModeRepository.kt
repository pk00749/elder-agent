// 对应 docs/v0.11.0.md §3.1：保存方式枚举 + 持久化仓库。
// 独立 prefs file "elder_save_mode"(沿用 §A.16.3 elder_oss_keys 同款独立文件策略;
// 不与 OSS 凭据同文件,避免 OAuth-style 混合存储)。
// 字段变更在文档锁定列表外;enum 加项 = 增加对应中文 label + zh-rHK + en 字符串。
package com.elder.android.data.export

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/** v0.11.0 保存方式。默认 LOCAL(见 Assumption §1)。 */
enum class SaveMode(val raw: String) {
    /** 仅本地:写 Room + 复制到 Downloads/老友日记/。 */
    LOCAL("local"),

    /** 仅云:写 Room + 触发 OssSyncWorker(需 oss_config 已配置;未配置静默 fallback LOCAL)。 */
    CLOUD("cloud"),

    /** 云+本地:两者都做。 */
    BOTH("both");

    companion object {
        val DEFAULT: SaveMode = LOCAL
        fun fromRaw(raw: String?): SaveMode = entries.firstOrNull { it.raw == raw } ?: DEFAULT
    }
}

/**
 * 老人端 SaveMode 持久化仓库。
 *
 * - 文件:独立的 [PREFS_FILE](Context.getSharedPreferences("elder_save_mode", MODE_PRIVATE))
 * - 字段:
 *   - [KEY_MODE]            当前模式(默认 LOCAL)
 *   - [KEY_VOICE_END_HINT]  语音退出黄条是否已被老人手动关闭过(true = 后续不再弹)
 * - 不在 Room 表内(§5 锁定列表外,允许新增 prefs file)。
 */
class ElderSaveModeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun current(): SaveMode =
        SaveMode.fromRaw(prefs.getString(KEY_MODE, SaveMode.DEFAULT.raw))

    fun setMode(mode: SaveMode) {
        prefs.edit { putString(KEY_MODE, mode.raw) }
    }

    fun isVoiceEndHintDismissed(): Boolean =
        prefs.getBoolean(KEY_VOICE_END_HINT, false)

    fun markVoiceEndHintDismissed() {
        // 使用 commit() 而不是 apply(),确保测试可立即读到;hint dismiss 是低频操作
        val ok = prefs.edit().putBoolean(KEY_VOICE_END_HINT, true).commit()
        if (!ok) Log.w("ElderSaveModeRepo", "markVoiceEndHintDismissed commit 返回 false")
    }

    companion object {
        const val PREFS_FILE = "elder_save_mode"
        const val KEY_MODE = "mode"
        const val KEY_VOICE_END_HINT = "voice_end_hint_seen"
    }
}
