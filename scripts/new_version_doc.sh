#!/usr/bin/env bash
# 对应 AGENTS.md §A.16.1 + prd.md §12.5。
# 不依赖 git 的等价脚本:scripts/new_version_doc.sh <version> [<topic>]
#   v0.11.0                       -> docs/v0.11.0.md
#   v0.11.0-oss-sync              -> docs/v0.11.0-oss-sync.md
# 文件已存在 -> 拒绝并退出 2(避免覆盖人工内容;对应 §18 不写 mock 数据反向测试)
# 用法:
#   scripts/new_version_doc.sh v0.11.0
#   scripts/new_version_doc.sh v0.11.0 '阿里云 OSS 同步'

set -euo pipefail

usage() {
    cat <<USAGE
用法: scripts/new_version_doc.sh <version> [<topic>]

参数:
  version   形如 vX.Y 或 vX.Y.Z,可带 -slug(小写字母/数字/连字符;§A.16.1)
  topic     一句话主题(可选;默认占位"(请填写一句话主题)")

示例:
  scripts/new_version_doc.sh v0.11.0
  scripts/new_version_doc.sh v0.11.0-oss-sync '阿里云 OSS 同步'

写入位置: docs/\${version}[-\${slug}].md
USAGE
}

if [[ $# -lt 1 || "$1" == "-h" || "$1" == "--help" ]]; then
    usage
    exit 0
fi

VERSION_INPUT="$1"
TOPIC="${2:-(请填写一句话主题)}"

# 版本校验(ERE;bash 不支持 (?:...) 非捕获组,用普通捕获组 + 跳位引用)
#   [1] major  [2] minor  [3] .Z 含点  [4] Z  [5] -slug 含横线  [6] slug
VERSION_RE='^v([0-9]+)\.([0-9]+)(\.([0-9]+))?(-([a-z0-9][a-z0-9-]*))?$'

if [[ ! "${VERSION_INPUT}" =~ ${VERSION_RE} ]]; then
    echo "版本格式不合法: '${VERSION_INPUT}'" >&2
    echo "期望: vX.Y 或 vX.Y.Z,可带 -slug(小写字母/数字/连字符)" >&2
    echo "反例: V0.11.0(大写 V) / v0.11.0_Bad(slug 含 _) / v0.11.0-(slug 为空) / v0.11.0-OSS(大写 slug)" >&2
    exit 2
fi

MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[4]:-}"
SLUG="${BASH_REMATCH[6]:-}"

if [[ -z "${PATCH}" ]]; then
    VERSION="${MAJOR}.${MINOR}"
else
    VERSION="${MAJOR}.${MINOR}.${PATCH}"
fi

if [[ -n "${SLUG}" ]]; then
    FILENAME="v${VERSION}-${SLUG}.md"
else
    FILENAME="v${VERSION}.md"
fi

# 解析仓库根:优先 git,其次上层 scripts/ 推断
if REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    :
else
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
    if [[ ! -d "${REPO_ROOT}/.git" ]]; then
        echo "无法定位 git 仓库根: ${REPO_ROOT}" >&2
        exit 1
    fi
fi

DOCS_DIR="${REPO_ROOT}/docs"
TARGET="${DOCS_DIR}/${FILENAME}"

mkdir -p "${DOCS_DIR}"

if [[ -f "${TARGET}" ]]; then
    echo "docs/${FILENAME} 已存在,拒绝覆盖(对应 AGENTS.md §18 反向条款)" >&2
    exit 2
fi

cat > "${TARGET}" <<EOF
<!-- 对应 prd.md 修订记录 v${VERSION} 行;详细设计见本文件 -->
# v${VERSION} 目标定义

> 对应 AGENTS.md §A.16.1 (文档拆分规范) + prd.md §12.5 (版本文档索引)。
> 本版本主题:**${TOPIC}**。
> 本文件**只**定义产品 / 工程目标,具体代码约束在 AGENTS.md / prd.md。

---

## 0. 现状盘点 (${VERSION} 起点)

| 维度 | 现状 |
|------|------|
| 版本 | v${VERSION}(上一版本:请填) |
| docs/ 既有 | (请列已存在的 vX.Y.* 文件) |
| prd.md 行数 | (当前) |
| AGENTS.md 行数 | (当前) |

---

## 1. 目标 1:(请填章节标题)

### 1.1 (请填子节标题)

---

## 2. 目标 2:(可选,按需复制 §1 结构)

---

## N. 不动契约

- (请列哪些 §/接口/prompt 文件保留只读)

---

> **维护说明**:本文件由 \`scripts/new_version_doc.sh v${VERSION}\` 创建,内容由开发者补充完整。
> 命名约束:AGENTS.md §A.16.1 = \`v{X}.{Y}.{Z}-{slug}.md\`(无 Z 时 \`v{X}.{Y}-{slug}.md\`)。
> 与 prd.md §12.5 版本文档索引表 + 修订记录表同步(三个写入点必须同步完成)。
EOF

echo "[new_version_doc] 已写入 ${TARGET}"
echo "[new_version_doc] 下一步:补全 §1-§N + 在 prd.md §12.5 / 修订记录 追加两行"
exit 0
