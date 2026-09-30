// 对应 PRD §3.1.4 B1-B4 + C3；v0.6.0 弱化：dimensions 字典迁到 LLM 工具调用（mark_dimension_covered）。
// v0.9.0：新增 isMedical / isMoney 拆分（medical 优先于 money），供 SafetyAgent.check() 优先级判定。
package com.elder.android.agent

object AgentSafety {
    /** B2 急救关键词：命中即调 save_diary 终止会话 */
    private val emergency = setOf(
        "摔了", "摔伤", "摔跤", "喘不上气", "喘不过气", "胸口疼", "胸口痛",
        "胸闷", "心慌", "晕倒", "昏迷", "中风", "心脏病发作", "脑溢血", "叫救护车",
    )

    /** B4 医疗问答关键词（v0.9.0 拆分）：症状 / 诊断 / 用药 / 剂量 / 副作用 */
    private val medical = setOf(
        "什么病", "什么症状", "什么药", "能不能吃",
        "要不要吃", "吃多少", "剂量", "副作用", "诊断", "是不是病", "严重吗", "要不要去医院",
    )

    /** B1 金钱 / 转账 / 验证码 / 陌生链接（v0.9.0 拆分） */
    private val money = setOf(
        "钱", "转账", "汇款", "借我", "借点钱", "验证码", "密码", "链接", "网址",
        "银行卡", "二维码", "扫码支付",
    )

    /** v0.5.0 / v0.6.0 / v0.7.0 / v0.8.x 兼容：medical + money 并集 */
    private val moneyOrMedical = medical + money

    /** C3 老人明确收尾 */
    private val explicitClose = setOf(
        "就到这", "就到这儿", "不聊了", "今天到这", "今天到这儿", "够了", "就这样吧", "结束吧",
    )

    /** v0.11.0 §3.4：老人主动结束对话的粤语关键词。
     *  命中即落幕 TTS + finalizeViaSafety + 落库。
     *  对应 docs/v0.11.0.md §3.4（§18 例外：扩展 §3.1.4 B 关键词表）。
     */
    private val elderEnd = setOf(
        "结束",       // 对应 docs/v0.11.0.md §3.4
        "够了",       // 对应 docs/v0.11.0.md §3.4
        "拜拜",       // 对应 docs/v0.11.0.md §3.4
        "不聊了",     // 对应 docs/v0.11.0.md §3.4
    )

    /** B2 急救关键词判定 */
    fun isEmergency(text: String): Boolean = emergency.any(text::contains)

    /** B1/B4 金钱/医疗判定（合并判定；保留兼容 0.5.0-0.8.x 测试） */
    fun isMoneyOrMedical(text: String): Boolean = moneyOrMedical.any(text::contains)

    /** B4 医疗问答判定（v0.9.0 新增） */
    fun isMedical(text: String): Boolean = medical.any(text::contains)

    /** B1 金钱判定（v0.9.0 新增） */
    fun isMoney(text: String): Boolean = money.any(text::contains)

    /** C3 明确收尾判定 */
    fun isExplicitClose(text: String): Boolean = explicitClose.any(text::contains)

    /** v0.11.0 §3.4：老人主动结束对话判定（粤语关键词）。 */
    fun isElderEnd(text: String): Boolean = elderEnd.any(text::contains)

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
