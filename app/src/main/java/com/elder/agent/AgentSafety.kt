// 对应 PRD §3.1.4 B1-B4 + C3；v0.6.0 弱化：dimensions 字典迁到 LLM 工具调用（mark_dimension_covered）。
package com.elder.android.agent

object AgentSafety {
    /** B2 急救关键词：命中即调 save_diary 终止会话 */
    private val emergency = setOf(
        "摔了", "摔伤", "摔跤", "喘不上气", "喘不过气", "胸口疼", "胸口痛",
        "胸闷", "心慌", "晕倒", "昏迷", "中风", "心脏病发作", "脑溢血", "叫救护车",
    )

    /** B1/B4 金钱/医疗：命中即 ask_clarify 换话题 */
    private val moneyOrMedical = setOf(
        "钱", "转账", "汇款", "借我", "借点钱", "验证码", "密码", "链接", "网址",
        "银行卡", "二维码", "扫码支付", "什么病", "什么症状", "什么药", "能不能吃",
        "要不要吃", "吃多少", "剂量", "副作用", "诊断", "是不是病", "严重吗", "要不要去医院",
    )

    /** C3 老人明确收尾 */
    private val explicitClose = setOf(
        "就到这", "就到这儿", "不聊了", "今天到这", "今天到这儿", "够了", "就这样吧", "结束吧",
    )

    /** B2 急救关键词判定 */
    fun isEmergency(text: String): Boolean = emergency.any(text::contains)

    /** B1/B4 金钱/医疗判定 */
    fun isMoneyOrMedical(text: String): Boolean = moneyOrMedical.any(text::contains)

    /** C3 明确收尾判定 */
    fun isExplicitClose(text: String): Boolean = explicitClose.any(text::contains)

    // v0.6.0 弱化：dimensionCount() 已废弃；维度判定改 LLM 显式 mark_dimension_covered 工具调用。
    // 保留函数以兼容 0.5.0 测试代码，但标记 @Deprecated。
    @Deprecated(
        message = "v0.6.0 改用 LLM mark_dimension_covered 工具调用；此函数仅作 keyword 兜底（feeling 维度）。",
        replaceWith = ReplaceWith("Dimension.FEELING in coveredDimensions"),
    )
    fun dimensionCount(texts: List<String>): Int {
        if (texts.isEmpty()) return 0
        // 保留仅 feeling 维度的兜底逻辑（v0.6.0 F7：preference / feeling 维度 keyword 兜底）
        val feelingKeywords = setOf("高兴", "难过", "开心", "伤心", "生气", "激动", "失望", "意外", "惊喜", "挺")
        return if (feelingKeywords.any { kw -> texts.joinToString(" ").contains(kw) }) 1 else 0
    }
}
