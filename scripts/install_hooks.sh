#!/usr/bin/env bash
# 对应 AGENTS.md §A.16.1。
# 把 scripts/hooks/{commit-msg,prepare-commit-msg} 安装到 .git/hooks/,chmod +x。
# 幂等:已存在且内容相同 -> 跳过;不同 -> 提示;--force 覆盖。
# 用法:
#   scripts/install_hooks.sh           安装全部
#   scripts/install_hooks.sh --force   强制覆盖已有 hook
#   scripts/install_hooks.sh --uninstall   卸载(从 .git/hooks/ 删除)

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_SRC="${REPO_ROOT}/scripts/hooks"
HOOKS_DST="${REPO_ROOT}/.git/hooks"

HOOKS=(
    "commit-msg:对应 AGENTS.md §A.16.1 主 hook;版本标记 -> 自动写 docs/vX.Y.Z[-slug].md"
    "prepare-commit-msg:配套 hook;扫 staged diff 注入 [vX.Y.Z] 前缀"
)

usage() {
    cat <<USAGE
用法: scripts/install_hooks.sh [--force | --uninstall]
  --force      强制覆盖 .git/hooks/ 中已有同名 hook
  --uninstall  从 .git/hooks/ 删除本仓库安装的 hook(其它仓库的 hook 不动)
  -h, --help   显示本帮助
USAGE
}

FORCE=0
UNINSTALL=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --force) FORCE=1; shift ;;
        --uninstall) UNINSTALL=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "未知参数: $1" >&2; usage; exit 2 ;;
    esac
done

if [[ ! -d "${HOOKS_DST}" ]]; then
    echo ".git/hooks/ 不存在 (当前目录可能不在 git 仓库内)" >&2
    exit 1
fi

if [[ ${UNINSTALL} -eq 1 ]]; then
    echo "卸载 hook..."
    for entry in "${HOOKS[@]}"; do
        name="${entry%%:*}"
        target="${HOOKS_DST}/${name}"
        if [[ -f "${target}" ]] && head -3 "${target}" 2>/dev/null | grep -q 'AGENTS.md §A.16.1'; then
            rm -f "${target}"
            echo "  - ${name} 已卸载"
        elif [[ -f "${target}" ]]; then
            echo "  ! ${name} 不是本仓库安装的(顶部无 §A.16.1 标记),跳过" >&2
        fi
    done
    echo "卸载完成。"
    exit 0
fi

echo "安装 hook 到 ${HOOKS_DST}/ ..."
for entry in "${HOOKS[@]}"; do
    name="${entry%%:*}"
    desc="${entry#*:}"
    src="${HOOKS_SRC}/${name}"
    dst="${HOOKS_DST}/${name}"

    if [[ ! -f "${src}" ]]; then
        echo "  ! 源文件不存在: ${src}" >&2
        exit 1
    fi

    if [[ -f "${dst}" ]]; then
        if [[ ${FORCE} -eq 0 ]]; then
            if cmp -s "${src}" "${dst}"; then
                echo "  = ${name} 已安装且与源一致,跳过"
                chmod +x "${dst}"
                continue
            fi
            echo "  ! ${name} 已存在且内容不同。重新安装请 --force" >&2
            echo "    备份路径: ${dst}.bak.$(date +%s)" >&2
            exit 1
        fi
        # --force:备份原文件
        cp "${dst}" "${dst}.bak.$(date +%s)"
        echo "  ! ${name} 已存在,备份到 ${dst}.bak.* 然后覆盖" >&2
    fi

    cp "${src}" "${dst}"
    chmod +x "${dst}"
    echo "  + ${name} 已安装 (${desc})"
done

echo
echo "安装完成。验证:"
echo "  bash scripts/install_hooks.sh --help"
echo "  git config core.hooksPath   # 应仍为空(走默认 .git/hooks/)"
