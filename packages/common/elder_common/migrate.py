# 数据库迁移 CLI（§19 命令速查）。
# PR 1 stub：打印 manifest 不实际执行；PR 2 起接真实迁移脚本。
from __future__ import annotations

import argparse
import sys


def main() -> int:
    parser = argparse.ArgumentParser(prog="python -m elder_common.migrate")
    sub = parser.add_subparsers(dest="cmd", required=True)

    up = sub.add_parser("up")
    up.add_argument("--to", required=True, help="目标版本，如 v2_1")

    down = sub.add_parser("down")
    down.add_argument("--to", required=True)

    sub.add_parser("current")

    args = parser.parse_args()

    if args.cmd == "current":
        print("v2.0 (no migrations applied yet)")
        return 0

    target = getattr(args, "to", "?")
    print(f"[migrate] {args.cmd} --to {target}  (PR 1 stub, 不实际执行)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
