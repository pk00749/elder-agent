"""§3.1.4 安全 / 收尾关键词 —— 服务端在调 LLM 之前先过滤（AGENTS.md §11 红线）。

命中后绕过 LLM：
- EMERGENCY → §B2：触发 finalize 保存当前 turn 并终止
- MONEY / MEDICAL_QA → §B1 / §B4：返回 canned「嗯」回复并继续闲聊
- EXPLICIT_CLOSE → §C3：触发 finalize

DIMENSION_KEYWORDS 用于 §C1 收尾判断（累计维度 ≥ 2 时服务端强制收尾）。
"""

from __future__ import annotations

# §B2：急救信号 —— 命中即触发 finalize
EMERGENCY_KEYWORDS: frozenset[str] = frozenset(
    {
        "摔了",
        "摔伤",
        "摔跤",
        "喘不上气",
        "喘不过气",
        "胸口疼",
        "胸口痛",
        "胸闷",
        "心慌",
        "晕倒",
        "昏迷",
        "中风",
        "心脏病发作",
        "脑溢血",
        "叫救护车",
    }
)

# §B1：金钱 / 转账 / 验证码 / 链接 —— 命中即转日常闲聊
MONEY_KEYWORDS: frozenset[str] = frozenset(
    {
        "钱",
        "转账",
        "汇款",
        "汇款给",
        "借我",
        "借点钱",
        "验证码",
        "密码",
        "链接",
        "网址",
        "银行卡",
        "二维码",
        "扫码支付",
    }
)

# §B4：医疗问答 —— 命中即转日常闲聊
MEDICAL_QA_KEYWORDS: frozenset[str] = frozenset(
    {
        "什么病",
        "什么症状",
        "什么药",
        "能不能吃",
        "要不要吃",
        "吃多少",
        "剂量",
        "副作用",
        "诊断",
        "是不是病",
        "严重吗",
        "要不要去医院",
    }
)

# §C3：老人明确收尾
EXPLICIT_CLOSE_KEYWORDS: frozenset[str] = frozenset(
    {
        "就到这",
        "就到这儿",
        "不聊了",
        "今天到这",
        "今天到这儿",
        "够了",
        "就这样吧",
        "结束吧",
        "没了",
        "完了",
    }
)

# §C1：访谈维度 —— 时间 / 地点 / 人物 / 事件；累计 ≥ 2 项即触发收尾
DIMENSION_KEYWORDS: dict[str, frozenset[str]] = {
    "time": frozenset(
        {
            "今天",
            "今天早上",
            "今天下午",
            "今天晚上",
            "今早",
            "今晚",
            "早上",
            "下午",
            "晚上",
            "中午",
            "上午",
            "傍晚",
            "凌晨",
            "昨天",
            "明天",
            "刚才",
            "现在",
            "几点",
            "周末",
            "周一",
            "周二",
            "周三",
            "周四",
            "周五",
            "周六",
            "周日",
        }
    ),
    "place": frozenset(
        {
            "家",
            "家里",
            "外面",
            "公园",
            "医院",
            "超市",
            "市场",
            "菜市场",
            "学校",
            "广场",
            "楼下",
            "门口",
            "厨房",
            "客厅",
            "卧室",
            "阳台",
            "车上",
            "路上",
            "小区",
            "商场",
        }
    ),
    "person": frozenset(
        {
            "我",
            "老伴",
            "儿子",
            "女儿",
            "孙子",
            "孙女",
            "外孙",
            "外孙女",
            "老张",
            "老李",
            "老王",
            "老赵",
            "小明",
            "小红",
            "小丽",
            "爸爸",
            "妈妈",
            "爷爷",
            "奶奶",
            "邻居",
            "朋友",
            "医生",
            "护士",
        }
    ),
    "event": frozenset(
        {
            "吃",
            "喝",
            "玩",
            "看",
            "走",
            "跑",
            "坐",
            "聊天",
            "下棋",
            "打牌",
            "买菜",
            "做饭",
            "跳舞",
            "唱歌",
            "睡觉",
            "起床",
            "出门",
            "回家",
            "看病",
            "拿药",
            "体检",
            "散步",
            "锻炼",
            "刷手机",
            "看电视",
            "接送",
            "买",
            "逛",
            "聊",
        }
    ),
}


def detect_emergency(text: str) -> bool:
    """§B2：急救信号检测 —— 命中即触发 finalize 保存。"""
    return any(kw in text for kw in EMERGENCY_KEYWORDS)


def detect_money_or_medical(text: str) -> bool:
    """§B1 / §B4：金钱 / 医疗问答 —— 命中即绕过 LLM 返回 canned 回复。"""
    return any(kw in text for kw in MONEY_KEYWORDS) or any(kw in text for kw in MEDICAL_QA_KEYWORDS)


def detect_explicit_close(text: str) -> bool:
    """§C3：老人明确收尾 —— 命中即触发 finalize。"""
    return any(kw in text for kw in EXPLICIT_CLOSE_KEYWORDS)


def count_dimensions(turns_text: list[str]) -> int:
    """§C1：累计覆盖「时间 / 地点 / 人物 / 事件」维度数。"""
    if not turns_text:
        return 0
    joined = " ".join(turns_text)
    return sum(
        1 for keywords in DIMENSION_KEYWORDS.values() if any(kw in joined for kw in keywords)
    )
