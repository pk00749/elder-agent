"""account_service 共享依赖（v2.1.2 §C anonymous-device 流程）。

MVP 阶段 anonymous-device 不限流 —— 鉴权路径零成本（无上游 SMS 调用），
滥用风险低；v2.x 接 SMS 后由网关层做全局限流（per-IP + per-device_token 双维度）。
"""

from __future__ import annotations

# MVP 阶段预留：v2.x 接 SMS 时在此加 device_token 限流器（per-IP 60s 1 次）。
