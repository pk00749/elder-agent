"""输出约束 + DSH 响应解析（AGENTS.md §11 + §3.1.4 A2 / D1 / D2）。

PRD 硬规则：
- A2：单次回复 ≤ 25 汉字（GB-level；服务端兜底）
- D1：日记 text ≤ 100 字
- D2：摘要 summary ≤ 60 字
- D3：text + summary + audio_segments 三段都返回
"""

from __future__ import annotations

import json
from collections.abc import Iterable

MAX_REPLY_CHARS = 25  # §A2
MAX_TEXT_CHARS = 100  # §D1
MAX_SUMMARY_CHARS = 60  # §D2

# §3.1.4 §3.1.5：硬上限轮数（PRD §11.5）
MAX_TURNS = 8


def truncate_chars(text: str, max_chars: int) -> str:
    """截断到 max_chars 汉字（GB-level；§3.1.4 A2 / D1 / D2 服务端兜底）。"""
    if len(text) <= max_chars:
        return text
    return text[:max_chars]


def parse_dsh_chat_response(content: str) -> tuple[str, bool]:
    """DSH 普通回复解析 —— 兼容 JSON `{"reply","should_finalize"}` 和纯文本。

    mock 端只返回纯文本；真 LLM 应返回 JSON；服务端两种都接受。
    """
    text = content.strip()
    if text.startswith("{"):
        try:
            data = json.loads(text)
            if isinstance(data, dict):
                reply = str(data.get("reply", ""))
                should_finalize = bool(data.get("should_finalize", False))
                return reply, should_finalize
        except json.JSONDecodeError:
            pass
    return text, False


def parse_dsh_save_response(content: str) -> tuple[str, str]:
    """DSH finalize 回复解析 —— 期望 JSON `{"text","summary"}`。"""
    text = content.strip()
    if text.startswith("{"):
        try:
            data = json.loads(text)
            if isinstance(data, dict):
                t = data.get("text", "")
                s = data.get("summary", "")
                return str(t), str(s)
        except json.JSONDecodeError:
            pass
    # fallback：直接用整段做 text（截断），summary 取前 60 字
    return text, text[:MAX_SUMMARY_CHARS]


def estimate_duration_ms(text: str) -> int:
    """§5.7 AudioSegment.duration_ms 估时 —— 中速 3 字/秒，最小 1 秒。"""
    return max(1000, len(text) * 300)


def build_dsh_messages(
    system_prompt_text: str,
    turns: Iterable[tuple[str, str]],
    new_user_text: str | None = None,
) -> list[dict[str, str]]:
    """拼装 DSH messages = [system] + [(user, assistant), ...] + [new_user]。

    turns 是 (elder_text, assistant_text) 元组列表。
    """
    messages: list[dict[str, str]] = [{"role": "system", "content": system_prompt_text}]
    for elder_text, assistant_text in turns:
        messages.append({"role": "user", "content": elder_text})
        messages.append({"role": "assistant", "content": assistant_text})
    if new_user_text is not None:
        messages.append({"role": "user", "content": new_user_text})
    return messages
