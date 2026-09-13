# 客户端 token 硬编码检查（AGENTS.md §A.2 / §18 / §19）。
# 检测 app/src 下硬编码的颜色 hex / sp / dp —— 全部应走 Dimens.kt。
# 排除 res/drawable/（vector drawable 的 width/height/tint 是资产本身属性，
# 不是运行时 UI token 用法；按 AGENTS.md §18 的"客户端 UI 代码"限定）。
# 用法：uv run python scripts/check_no_hardcoded_tokens.py app/src
from __future__ import annotations

import re
import sys
from pathlib import Path

ALLOWED_FILES = {"Dimens.kt", "colors.xml", "dimens.xml"}
EXCLUDED_DIRS = {"drawable", "drawable-v24", "mipmap-*"}

COLOR_RE = re.compile(r"#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")
SP_RE = re.compile(r"\b\d+\.?sp\b")
DP_RE = re.compile(r"\b\d+\.?dp\b")

# 注释豁免：注释里的数字是文档性质（如 "// 区域 A：问候（高 240dp，§3.1.5）"），
# 不会参与运行时 UI token 计算，§18 红线的目的是拦截真实硬编码。
def _strip_comments(text: str, suffix: str) -> str:
    out_lines: list[str] = []
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip()
        if suffix == ".kt":
            if stripped.startswith("//"):
                continue
            if stripped.startswith("/*"):
                continue
            line = re.sub(r"/\*.*?\*/", "", line)
        elif suffix == ".xml":
            if stripped.startswith("<!--"):
                continue
            line = re.sub(r"<!--.*?-->", "", line, flags=re.DOTALL)
        out_lines.append(line)
    return "".join(out_lines)


def is_excluded(path: Path) -> bool:
    parts = {p for p in path.parts}
    return any(d in parts or d.replace("-*", "") in str(path) for d in EXCLUDED_DIRS)


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: check_no_hardcoded_tokens.py <path>", file=sys.stderr)
        return 2
    root = Path(sys.argv[1])
    if not root.exists():
        print(f"Path not found: {root}", file=sys.stderr)
        return 2

    findings: list[str] = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if path.name in ALLOWED_FILES:
            continue
        if is_excluded(path):
            continue
        if path.suffix not in (".kt", ".xml"):
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        text = _strip_comments(text, path.suffix)
        for m in COLOR_RE.finditer(text):
            findings.append(f"{path}:{m.start()}: color hardcode: {m.group()}")
        for m in SP_RE.finditer(text):
            findings.append(f"{path}:{m.start()}: sp hardcode: {m.group()}")
        for m in DP_RE.finditer(text):
            findings.append(f"{path}:{m.start()}: dp hardcode: {m.group()}")

    if findings:
        print("FAIL: token hardcode findings:")
        for line in findings:
            print(f"  {line}")
        return 1

    print("PASS: no token hardcode found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
