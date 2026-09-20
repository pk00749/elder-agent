#!/usr/bin/env python3
# 对应 prd.md §3.1.4 v0.6.0 验收：用 MiniMax M3 真实调用，分别用 v1 和 v2 prompt 跑同一组访谈脚本，
# 输出两版对话日志供人工 rubric 评分（scripts/interview_eval/rubric.md）。
#
# 不进 CI（需 MINIMAX_API_KEY；AGENTS.md §A.11.5 LiveTest 约定）。
#
# 用法：
#   export MINIMAX_API_KEY=...
#   python scripts/interview_eval/runner.py                      # 跑全部 fixtures
#   python scripts/interview_eval/runner.py --fixture basic.json  # 跑单个
#
# 输出：
#   scripts/interview_eval/report.md  ← v1/v2 对话日志 + 待评分
"""对应 prd.md §3.1.4 v0.6.0 验收：v1 vs v2 对照评分 runner（LiveTest）"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FIXTURES_DIR = ROOT / "fixtures"
SYSTEM_V1 = (ROOT.parent.parent / "app/src/main/assets/agent/system_v1.txt").read_text(encoding="utf-8")
SYSTEM_V2 = (ROOT.parent.parent / "app/src/main/assets/agent/system_v2.txt").read_text(encoding="utf-8")
SAVE_PROMPT = (ROOT.parent.parent / "app/src/main/assets/agent/save_v2.txt").read_text(encoding="utf-8")

MINIMAX_BASE = "https://api.minimax.cn/v1"
MINIMAX_MODEL = "MiniMax-M3"

TOOLS_V2 = [
    {
        "type": "function",
        "function": {
            "name": "ask_clarify",
            "description": "换话题",
            "parameters": {"type": "object", "properties": {"reason": {"type": "string"}}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "save_diary",
            "description": "保存日记",
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {"type": "string"},
                    "summary": {"type": "string"},
                },
                "required": ["text", "summary"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "mark_dimension_covered",
            "description": "标记维度已覆盖",
            "parameters": {
                "type": "object",
                "properties": {"dim": {"type": "string", "enum": ["time", "place", "person", "event", "feeling"]}},
                "required": ["dim"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "MOVE_ON",
            "description": "节奏控制",
            "parameters": {
                "type": "object",
                "properties": {"stage": {"type": "string", "enum": ["next_dimension", "closing"]}},
                "required": ["stage"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "remember_fact",
            "description": "记住事实",
            "parameters": {
                "type": "object",
                "properties": {
                    "type": {"type": "string", "enum": ["person", "place", "event", "preference", "health"]},
                    "content": {"type": "string"},
                    "confidence": {"type": "string", "enum": ["high", "medium", "low"]},
                },
                "required": ["type", "content", "confidence"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_memory",
            "description": "查记忆",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string"},
                    "type": {"type": "string"},
                    "limit": {"type": "integer"},
                },
                "required": ["query"],
            },
        },
    },
]


def chat(api_key: str, messages: list, system: str, tools: list | None = None) -> dict:
    """调 MiniMax M3 chat completion endpoint。返回原始 dict（含 tool_calls）。"""
    payload = {
        "model": MINIMAX_MODEL,
        "messages": [{"role": "system", "content": system}] + messages,
        "temperature": 0.4,
        "max_tokens": 200,
    }
    if tools:
        payload["tools"] = tools
    req = urllib.request.Request(
        f"{MINIMAX_BASE}/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def run_fixture(api_key: str, fixture: dict) -> dict:
    """跑一份 fixture：v1 vs v2 各跑一遍。"""
    system = fixture.get("system_override_v2") or SYSTEM_V2
    recent = fixture.get("recent_summaries", [])
    elder_facts = fixture.get("elder_facts", [])

    # 把 history 渲染进 system_v2 占位符
    if "{{recent_summaries_block}}" in system and recent:
        lines = "\n".join(f"- 【{s['date']}】{s['summary']}" for s in recent)
        system = system.replace("{{recent_summaries_block}}", f"<recent-summaries>\n{lines}\n</recent-summaries>")
    else:
        system = system.replace("{{recent_summaries_block}}", "<recent-summaries></recent-summaries>")
    if "{{elder_facts_block}}" in system and elder_facts:
        lines = "\n".join(f"- [{f['type']}, {f['confidence']}] {f['content']}" for f in elder_facts)
        system = system.replace("{{elder_facts_block}}", f"<elder-facts count=\"{len(elder_facts)}\">\n{lines}\n</elder-facts>")
    else:
        system = system.replace("{{elder_facts_block}}", "<elder-facts count=\"0\"></elder-facts>")
    system = system.replace("{{memory_policy_block}}", "")

    messages = []
    v1_log = []
    v2_log = []

    for turn in fixture["turns"]:
        elder = turn["elder"]
        messages.append({"role": "user", "content": elder})

        # v1: 不带工具 + 不带 facts
        sys_v1 = SYSTEM_V1
        r1 = chat(api_key, messages, sys_v1, tools=None)
        a1 = r1["choices"][0]["message"]
        v1_log.append({"elder": elder, "assistant": a1.get("content", "")})
        messages.append({"role": "assistant", "content": a1.get("content", "")})

    # 重跑 v2（独立 messages 流）
    messages2 = []
    for turn in fixture["turns"]:
        elder = turn["elder"]
        messages2.append({"role": "user", "content": elder})
        r2 = chat(api_key, messages2, system, tools=TOOLS_V2)
        a2 = r2["choices"][0]["message"]
        v2_log.append({"elder": elder, "assistant": a2.get("content", ""), "tool_calls": a2.get("tool_calls", [])})
        messages2.append({"role": "assistant", "content": a2.get("content", "")})

    return {"v1": v1_log, "v2": v2_log}


def main():
    parser = argparse.ArgumentParser(description="v0.6.0 v1 vs v2 对照评分 runner")
    parser.add_argument("--fixture", type=str, help="单个 fixture 文件名")
    args = parser.parse_args()

    api_key = os.environ.get("MINIMAX_API_KEY")
    if not api_key:
        print("MINIMAX_API_KEY 未设置；跳过（LiveTest）。", file=sys.stderr)
        sys.exit(0)

    fixtures = list(FIXTURES_DIR.glob("*.json"))
    if args.fixture:
        fixtures = [FIXTURES_DIR / args.fixture]

    results = {}
    for fp in fixtures:
        print(f"Running {fp.name} ...", file=sys.stderr)
        fixture = json.loads(fp.read_text(encoding="utf-8"))
        try:
            results[fp.name] = run_fixture(api_key, fixture)
        except urllib.error.HTTPError as e:
            print(f"  HTTPError: {e.code} {e.reason}", file=sys.stderr)
            results[fp.name] = {"error": f"{e.code} {e.reason}"}

    out = ROOT / "report.md"
    with out.open("w", encoding="utf-8") as f:
        f.write("# v0.6.0 对照评分报告\n\n")
        f.write("> 自动生成（runner.py）。需人工按 `rubric.md` 打分。\n\n")
        for name, log in results.items():
            f.write(f"## {name}\n\n")
            if "error" in log:
                f.write(f"**ERROR**: {log['error']}\n\n")
                continue
            f.write("### v1 (system_v1)\n\n")
            for turn in log["v1"]:
                f.write(f"- **老人**：{turn['elder']}\n")
                f.write(f"  **Agent**：{turn['assistant']}\n\n")
            f.write("### v2 (system_v2 + memory)\n\n")
            for turn in log["v2"]:
                f.write(f"- **老人**：{turn['elder']}\n")
                f.write(f"  **Agent**：{turn['assistant']}\n")
                if turn.get("tool_calls"):
                    for tc in turn["tool_calls"]:
                        if isinstance(tc, dict):
                            fn = tc.get("function", {}).get("name", "?")
                            args = tc.get("function", {}).get("arguments", "{}")
                            f.write(f"  **tool**：{fn}({args})\n")
                f.write("\n")
    print(f"输出：{out}", file=sys.stderr)


if __name__ == "__main__":
    main()
