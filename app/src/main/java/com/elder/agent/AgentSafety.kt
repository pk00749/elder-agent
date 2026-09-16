package com.elder.android.agent

object AgentSafety {
    private val emergency = setOf(
        "摔了", "摔伤", "摔跤", "喘不上气", "喘不过气", "胸口疼", "胸口痛",
        "胸闷", "心慌", "晕倒", "昏迷", "中风", "心脏病发作", "脑溢血", "叫救护车",
    )

    private val moneyOrMedical = setOf(
        "钱", "转账", "汇款", "借我", "借点钱", "验证码", "密码", "链接", "网址",
        "银行卡", "二维码", "扫码支付", "什么病", "什么症状", "什么药", "能不能吃",
        "要不要吃", "吃多少", "剂量", "副作用", "诊断", "是不是病", "严重吗", "要不要去医院",
    )

    private val explicitClose = setOf(
        "就到这", "就到这儿", "不聊了", "今天到这", "今天到这儿", "够了", "就这样吧", "结束吧",
    )

    private val dimensions = mapOf(
        "time" to setOf(
            "今天", "今天早上", "今天下午", "今天晚上", "今早", "今晚", "早上", "下午",
            "晚上", "中午", "上午", "傍晚", "凌晨", "昨天", "明天", "刚才", "现在", "周末",
            "周一", "周二", "周三", "周四", "周五", "周六", "周日",
        ),
        "place" to setOf(
            "家", "家里", "外面", "公园", "医院", "超市", "市场", "菜市场", "学校",
            "广场", "楼下", "门口", "厨房", "客厅", "卧室", "阳台", "车上", "路上", "小区", "商场",
        ),
        "person" to setOf(
            "我", "老伴", "儿子", "女儿", "孙子", "孙女", "外孙", "外孙女", "老张", "老李",
            "老王", "老赵", "小明", "小红", "小丽", "爸爸", "妈妈", "爷爷", "奶奶", "邻居",
            "朋友", "医生", "护士",
        ),
        "event" to setOf(
            "吃", "喝", "玩", "看", "走", "跑", "坐", "聊天", "下棋", "打牌", "买菜", "做饭",
            "跳舞", "唱歌", "睡觉", "起床", "出门", "回家", "看病", "拿药", "体检", "散步",
            "锻炼", "刷手机", "看电视", "接送", "买", "逛", "聊",
        ),
    )

    fun isEmergency(text: String): Boolean = emergency.any(text::contains)

    fun isMoneyOrMedical(text: String): Boolean = moneyOrMedical.any(text::contains)

    fun isExplicitClose(text: String): Boolean = explicitClose.any(text::contains)

    fun dimensionCount(texts: List<String>): Int {
        if (texts.isEmpty()) return 0
        val joined = texts.joinToString(" ")
        return dimensions.values.count { keys -> keys.any(joined::contains) }
    }
}
