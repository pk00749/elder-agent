// 对应 AGENTS.md §15 / §A.8：Robolectric 4.13 + Java 25 环境下 Shadows.reset() 在
// ShadowCookieManager.reset() 阶段会抛 NoClassDefFoundError 指向
// android.webkit.RoboCookieManager——其字节码已被 Sonatype 重新编译为 Java 25（major 69），
// 而 Robolectric 4.13 内嵌的 asm 9.7.1 只支持到 Java 23。
//
// 这只发生在 finallyAfterTest 的 cleanup 阶段，测试断言已经跑过、结果已落。
// 兜住这个异常让测试通过；非 CookieManager / 非 Java 25 的异常照常抛。
package com.elder.android.testing

import android.util.Log
import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner

class ElderRobolectricTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    // Robolectric 4.13 实际签名 finallyAfterTest(FrameworkMethod)（JUnit Statement 风格，单参）
    override fun finallyAfterTest(method: FrameworkMethod) {
        try {
            super.finallyAfterTest(method)
        } catch (e: Throwable) {
            // walk both cause chain AND suppressed exceptions（AndroidTestEnvironment 把
            // Robolectric 内部的 NoClassDefFoundError 挂到 suppressed，不是 cause）
            val seen = HashSet<Throwable>()
            val msgs = mutableListOf<String>()
            val queue = ArrayDeque<Throwable>()
            queue.add(e)
            while (queue.isNotEmpty()) {
                val t = queue.removeFirst()
                if (!seen.add(t)) continue
                msgs += t.message.orEmpty() + " | " + (t.javaClass.name)
                t.cause?.let(queue::add)
                t.suppressed?.forEach(queue::add)
            }
            val joined = msgs.joinToString(" || ")
            val isCookieManagerResetFailure =
                joined.contains("RoboCookieManager") ||
                joined.contains("Unsupported class file major version 69")
            if (isCookieManagerResetFailure) {
                Log.w(
                    "ElderRobolectric",
                    "Suppressed Robolectric 4.13 + Java 25 cleanup error in ${method.name}: " +
                        "Shadows.reset() 在 android.webkit.RoboCookieManager 影子类加载时失败。" +
                        "测试断言已通过，仅 cleanup 受影响。",
                )
            } else {
                throw e
            }
        }
    }
}
