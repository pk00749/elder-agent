# AGENT.md — 代码实施规范

> 本文档只规定**代码怎么写**。产品需求、数据模型、API 契约、行为约束以 `prd.md` 为准。
>
> 适用范围：本仓库内的所有服务端代码。

## 修订记录

| 版本 | 日期       | 作者  | 主要变更 |
|------|------------|-------|----------|
| v1.0 | 2026-08-23 | Codex | 首版：仓库结构 / 命名 / 文件组织 / 错误处理 / 静态检查 / 日志 / 配置 / API 路由 / 数据访问 / Agent / COS / 鉴权 / 测试 / CI / 提交 / 文档同步 共 17 章 |
| v2.0 | 2026-08-24 | Codex | 与 PRD 拆分；§11 Agent 行为约束引用迁移到 prd.md §3.1.4 |
| v2.1 | 2026-08-27 | Codex | 新增 §A.1-§A.10 增量：设计 token（色彩 / 字号 / 间距圆角 / 动效）、TTS 千问 + 粤语男声 + 系统铃声兜底、两条 TPush 推送通道、§4.6 权限 / §4.8 Toast / §4.9 黄条规范、剂量枚举收紧为 [PILL\|HALF\|SPOON]、advance_remind_min 30/60/120 默认 60 + channel_priority mid\|high、bind/confirm + bind/pending + diary/flush-pending 三端点服务端事务、ASR 粤语主识别 + 普通话兜底、声明数据模型不变（v2.0 保留） |
| v2.2 | 2026-09-14 | Codex | 新增 §A.11 Android 0.5.0 本地 Agent 规范：用户 BYOK 直连千问 ASR/TTS 与 MiniMax M3；APK 内版本化 Prompt；MiniMax tools + Kotlin 本地校验；Room v3 访谈 / 待补做 / 摘要；TTS 失败文字兜底；Kotlin 行为测试 |
| v2.3 | 2026-09-18 | Codex | 新增 §A.12 MiniMax Realtime ASR（客户端 WebSocket）+ §A.13 MiniMax T2A WebSocket（Cantonese_KindWoman）+ §18 「0.7.0 例外」条款；千问 / MiniMax 双 Provider 可切换；`asr_config` Migration 4→5 DROP+CREATE；MiniMax ASR 与 LLM 共用 Key、MiniMax TTS 独立 Key；`scripts/check_no_hardcoded_tokens.py` 仍绿 |
| v2.4 | 2026-09-24 | Codex | 新增 §18 PCM sink 收口反向条款：`AndroidPcmSink.drain()/release()` 必须调 `drainBuffer()` 等 `playbackHeadPosition` 追平 `bytesWritten/2` 再 `track.stop()`，避免 AudioTrack 内部 buffer 未播放 PCM 被砍导致 TTS 尾音丢失；配套 `AndroidPcmSinkDrainTest` 4 条回归测试 |

---

## 1. 仓库结构

```
elder-agent/
├── prd.md
├── AGENT.md
├── pyproject.toml          # uv workspace 根
├── uv.lock
├── .ruff.toml
├── mypy.ini
├── packages/
│   └── common/             # 跨服务共享：models / errors / auth / storage / logging / config
└── services/
    ├── agent_service/
    ├── reminder_service/
    └── account_service/
```

- **禁止**在多个服务里重复定义 Pydantic 模型或错误码；统一在 `packages/common`。
- **禁止**业务代码跨服务直接 `import`；跨服务只走 HTTP。

---

## 2. 命名

| 对象 | 规范 |
|---|---|
| 文件 | `snake_case.py`；测试 `test_*.py` |
| 类 | `PascalCase` |
| 函数 / 变量 | `snake_case` |
| 常量 | `UPPER_SNAKE_CASE`，集中放在各模块 `constants.py` |
| Pydantic 字段 | 与 `prd.md` 字段名完全一致（snake_case），不私自改名 |
| 路由路径 | 与 `prd.md` 端点清单完全一致，不加版本号 / 别名 |

---

## 3. 文件组织

- 单文件 ≤ 400 行；超出按职责拆分。
- 一个路由文件 = `prd.md` 中一个端点族。
- 一个模型文件 = `prd.md` 中一个领域实体。
- `__init__.py` 只 re-export，不写逻辑。
- 模型 / 客户端 / 路由 / 业务服务 分目录放置，不混在一起。

---

## 4. 错误处理

- 业务代码**只**通过异常抛出错误，**禁止**手动 `JSONResponse(status_code=...)`：

  ```python
  raise AppError(
      code="VALIDATION_ERROR",
      message="elder_id required",
      field="body.elder_id",
      http_status=422,
  )
  ```

- 全局异常处理器（`elder_common.errors.register_exception_handlers`）按 `prd.md` §6.2 输出统一响应体。
- 上游异常在 `elder_common.upstream` 适配层统一捕获、映射、重抛，**禁止**在路由层裸 `try/except`。
- 422 响应的 `field` 必须是 JSON Pointer（RFC 6901），例如 `body.reminder.payload.schedule[0].time`。

---

## 5. 类型与静态检查

- 所有公共函数必须有完整类型注解。
- `mypy --strict` 在 CI 强制通过，**禁止** `Any`。
- 不确定类型时用 `object` + 显式 `cast`；必要时 `# type: ignore[error-code] # <理由>`。
- Pydantic 字段可空用 `field: T | None = None`，不裸写 `Optional`。

---

## 6. 注释

- 默认中文（项目母语）；引用外部英文 API / 标准库术语保留英文。
- 函数 docstring 三段式：用途 / 入参约束 / 出参与副作用。
- 涉及 `prd.md` 条款的代码块顶部必须写引用：

  ```python
  # 对应 prd.md §3.1.4 B1
  ```
- **禁止**无意义注释（`# 赋值`、`# 调用函数`）。
- **禁止**注释掉的死代码；用 git 找回。

---

## 7. 日志

- 用 `structlog`，输出 JSON 一行（`elder_common.logging` 已封装）。
- 访问日志字段固定：`ts`、`request_id`、`user_id`、`role`、`route`、`method`、`status`、`latency_ms`、`code`、`upstream`、`error`。
- 业务事件统一入口 `emit_event(name, **fields)`，事件名集中在 `elder_common.logging.events` 常量。
- 写入日志前过 `elder_common.logging.sanitize()`，**禁止**写入原始音频 URL、ASR 全文、用户密码、密钥。
- dev 环境额外写本地文件；prod 写 stdout 由容器采集。

---

## 8. 配置

- 唯一入口 `from elder_common.config import settings`，**禁止**业务代码直接 `os.environ.get`。
- 启动时 `settings.validate()` 校验必填项，缺失即 panic。
- 密钥类配置（JWT、COS、DSH、ASR、TTS、TPush、SMS）一律从环境变量读，**禁止**硬编码或进 git。
- `.env` 加入 `.gitignore`；样例值进 `.env.example`。

---

## 9. API 路由

- 路径前缀严格按 `prd.md` §6.1；多余端点（health、metrics）走 `/internal/*`，不出网关。
- 每个路由显式标注鉴权装饰器：

  ```python
  @router.post("/v1/reminders", dependencies=[Depends(require_role("family")), Depends(rate_limit("reminder.create"))])
  ```

- 路由函数**只**做参数解析、调用 service、构造响应；**禁止**在路由层写业务逻辑或直接调数据库。
- 请求 / 响应 schema 单独放 `schemas.py`，不与路由文件混。

---

## 10. 数据访问

- 集合名按 `prd.md` §5 模型名 → 复数 snake_case（`family_user` → `family_users`）。
- 所有索引在 `deploy/scripts/create_indexes.py` **显式** 创建，**禁止**依赖默认索引。
- 业务代码不直接拼 SQL/NoSQL；通过 `packages/common` 的仓储函数访问。
- 写操作必须有 `created_at` / `updated_at` 字段；更新走 `updated_at` 乐观锁。
- `elder_id` 在仓储层二次校验 `current_user.elder_id == doc.elder_id`，避免越权。

---

## 11. Agent 相关（agent-service）

- 系统提示词位于 `services/agent_service/app/agent/prompts/system_v{N}.txt`，**必须**文件顶部以注释形式引用 `prd.md` §3.1.4 对应条款号。
- 提示词**版本化**：每次修改新建 `system_v{N+1}.txt`，旧版本保留；运行版本由 `PROMPT_VERSION` 环境变量选择。
- DSH tools 的 schema 在 `agent/tools/schemas.py` 用 Pydantic 定义；**禁止**在 schema 之外接参。
- 工具触发的关键词列表在 `agent/safety/keywords.py` 维护，每条注明 `prd.md` 条款号；命中即绕过 LLM 直接调 tool，**禁止**仅靠 LLM 自觉。
- 会话状态机定义在 `agent/state.py`，状态转移**只**通过 `transition(session, event)`；**禁止**在别处直接改 `status`。
- 长度截断（`text` ≤ 100、`summary` ≤ 60）在落库前**服务端**做，**禁止**依赖 LLM 自截。

---

## 12. COS / 对象存储

- 所有 COS key 通过 `cos_key_builder` 工厂方法生成（按 `prd.md` §5.8），**禁止**业务代码字符串拼接。
- 临时上传统一签发 24h 过期预签 URL（对应 `prd.md` §11.13），**禁止**返回长期 URL。
- 前端**永远**不持 COS 长期凭证。

---

## 13. 鉴权与安全

- 每个路由装饰器显式标注角色与资源归属校验（`require_role`、`require_elder_match`）。
- 缺 `Authorization` 头直接 401，**禁止**放行后再校验。
- JWT 密钥从环境变量读，prod 走 KMS 注入。
- 敏感端点（auth、bind 写、reminder 写、diary 读）的日志必须带 `audit=true`。

---

## 14. 测试

- 测试与实现同包，目录 `tests/`，命名 `test_*.py`。
- 上游（DSH / ASR / TTS / CloudBase / COS / TPush / SMS）**必须** mock，**禁止**测试中真实调用。
- 行为约束（`prd.md` §3.1.4 A1–D3）每条对应一个测试用例。
- LLM 输出用录制 fixture（`vcr.py` 或等价物），避免真实调用。

---

## 15. CI 门槛

任何 PR 必须同时满足：

- `uv run pytest` 全绿
- `uv run ruff check` 全绿
- `uv run mypy --strict` 全绿
- 行为约束测试覆盖（§14）

---

## 16. 提交规范

- 提交信息格式：`<scope>: <动词> <对象>`，例如 `agent-service: add diary session state machine`。
- 一个 commit 一件事；不相关的改动拆 PR。
- PR 描述必须写「对应 prd.md §X」或「对应 AGENT.md §Y」。

---

## 17. 文档同步

- `prd.md` 修订后，涉及的代码改动在同一个 PR 提交，并在 PR 描述里关联。
- `AGENTS.md` 修订在 PR 描述里说明影响的章节号。
- 不在 `AGENTS.md` 里复述 `prd.md` 已有内容；只写引用。

## 18. 反向条款（Do/Don't）

> 这是给 AI 代理的**强制反向防线**——列在这里的都是已知高踩坑点。违反任一条会被 PR 评审直接打回。

### 数据模型 / PRD 锁定

- **不要修改 `prd.md` §5 数据模型既有字段定义**（`family_user`/`elder_profile`/`binding`/`reminder`/`reminder_ack`/`diary_session`/`diary_entry`/COS key）。仅允许新增字段，不能改字段类型 / 默认值 / 必填。理由：v2.0 契约锁定 + 现网有数据；改既有字段会破坏客户端 SDK + 集成测试。
- **不要修改 `prd.md` §3.1.4 Agent 行为约束（A1–D3）**：这是 AI 访谈 Agent 必须遵守的硬规则。改动需走 prd.md 修订记录 + 产品评审，不在 PR 范围内自行修改。
- **不要删除 `services/*/migrations/` 下的迁移脚本**。审计轨迹必须保留；删除会导致回滚失败 + 数据丢失。
- **不要在 PR 里同时改 `prd.md` 既有章节 + 多服务代码**（违反 §16 一个 commit 一件事）。需要拆 PR。

### 客户端 UI / 设计 token

- **不要硬编码颜色 / 字号 / 间距 / 圆角到客户端代码**——必须从 `Dimens.kt`（或 `colors.xml`/`dimens.xml` 资源）读取。理由：§I-1 不追加品牌色；硬编码会让 prd.md §4 token 表不同步。
  - 检测命令见 §19
- **不要在客户端写"136****8888" / "+86" 等明文手机号样式**——统一走 `elder_common.redact.phone_tail(phone)`。理由：§7 日志 `sanitize` 会拦截明文 PII；客户端写死会被合规审计抓到。
- **不要在客户端引入新的上游 SDK**（千问 / TPush / COS / ASR / TTS）——v3.0.1 历史约束；App 0.5.0 仅允许按 §A.11 直连千问 ASR/TTS 与 MiniMax M3，其他上游仍必须统一在 `elder_common` 封装。新增上游必须先在 §A.x 增加子节 + 评审。
  - **0.7.0 例外**：`app/src/main/java/com/elder/data/asr/MiniMaxAsrClient.kt` 与 `app/src/main/java/com/elder/data/tts/MiniMaxTtsClient.kt` 作为 §A.12 / §A.13 唯一上游；不通过 `elder_common` 统一封装（0.5.0/0.6.0 直连架构不变）。后续任何新上游仍按原条款评审。
- **不要编写超过 500 行的客户端模块**（除非有文档化理由）。理由：与 OpenAI Codex 样例反模式一致；高触碰文件会吸引无关改动。

### 服务端 / 工程规范

- **不要在路由层写 `try/except` 或直接拼 SQL/NoSQL**——必须走 `packages/common` 仓储 + 全局异常处理器（§4 / §10）。理由：破坏错误码统一 + 越权校验丢失。
- **不要绕开 `require_role` / `require_elder_match` 装饰器**（§9 / §13）。理由：鉴权中间件是路由层唯一的角色校验；裸路由会立刻被 §14 测试拒。
- **不要使用 `# noqa: E501` / `# type: ignore[error-code]` 绕过 lint**——除非有显式注释 `# <理由>`。理由：CI §15 强制 ruff/mypy 全绿；无理由注释会被打回。
- **不要写 mock 数据反向测试**（mock 某个被删除逻辑的负向 case）。理由：v2.1 §A.4 紧致 `MedicationDosage.type` 枚举后，`type=custom` 测试已无意义——保留会过期；正向测试覆盖即可。
- **不要为静态定义值加测试**（常量、`UPPER_SNAKE_CASE` 配置）。理由：和 OpenAI Codex 样例的反模式一致——静态定义不会跑偏，加测试是 noise。
- **不要在客户端用 `if elder_id == current_user.elder_id`** 形式做越权校验——必须在仓储层做（§10）。理由：路由层 / 业务层越权校验易漏，仓储层是唯一真源。
- **不要在 `AndroidPcmSink.drain()` / `release()` 里直接调 `track.stop()`**——必须先调 `drainBuffer()` 等 `playbackHeadPosition` 追平 `bytesWritten / 2` 再 stop。理由：v0.x 已知 bug 复现路径；`AudioTrack.stop()` 会丢弃内部 buffer 中未播放 PCM（24kHz/mono buffer ≈ 500ms），导致 TTS 尾音丢失。详见 `app/src/main/java/com/elder/data/tts/PcmSink.kt:drainBuffer()`。

### 日志 / 隐私

- **不要在日志里写原始音频 URL / ASR 全文 / 用户密码 / 密钥**——必须过 `elder_common.logging.sanitize()`（§7）。理由：prod 容器采集写入 CloudBase，原始 PII 上传合规审计即失败。
- **不要为日记 / reminder 写告警**——业务事件统一入口 `emit_event(name, **fields)`（§7 末段）。告警字段集中在 `elder_common.logging.events` 常量。

### 工程工具 / CI

- **不要直接跑 `pytest` / `mypy --strict` 全量命令**——日常只跑对应服务的测试 / 类型。理由：全量 lint/test 在 PR 范围外运行是 noise；CI 会兜底。
- **不要提交 `.env` / 真实 COS key / 真实手机号**——`.env` 加 `.gitignore`（§8）；历史 commit 含敏感信息立刻 `git rm --cached` + 通知 Codex。

## 19. 本地命令速查（AI 代理可直接执行）

> 仓库内主要脚本 / 命令一站式速查；写 PR 前**主动**跑相关命令。

### 测试 / Lint / 类型检查

```bash
# 跑 v2.1 schema 回归测试（§A.4 / §A.7）
uv run pytest tests/integration/test_schema_v21.py -v

# 单服务测试（按 §1 服务拆分）
uv run pytest services/reminder_service/ -v

# 全部集成测试（仅在改 common / core / protocol 后跑；征求评审员同意）
uv run pytest tests/integration/

# 跳远程 LLM 调用（用 fixture）
uv run pytest -m "not llm"

# MyPy strict 类型检查
uv run mypy --strict services/ packages/

# Ruff lint
uv run ruff check

# Ruff format（写完代码自动跑）
uv run ruff format
```

### 客户端 token / 硬编码检查（对应 §A.2 + prd.md §4.1-§4.4）

```bash
# 颜色硬编码（必须是 #RRGGBB，且不在 Dimens.kt / colors.xml）
rg -t kotlin -t xml '#[0-9A-Fa-f]{6}' app/src   --glob '!**/Dimens.kt' --glob '!**/colors.xml'

# 字号硬编码（必须匹配 .sp，且不在 Dimens.kt / dimens.xml）
rg -t kotlin -t xml '[0-9]+\.?sp' app/src   --glob '!**/Dimens.kt' --glob '!**/dimens.xml'

# 间距 / 圆角硬编码
rg -t kotlin -t xml '[0-9]+\.?dp' app/src   --glob '!**/Dimens.kt' --glob '!**/dimens.xml'

# Token 表一致性检查（参考脚本，§A.2 占位）
uv run python scripts/check_no_hardcoded_tokens.py app/src
```

### 数据库迁移

```bash
# 同步 v2.1 schema（Pydantic 字段收紧 + 新增）
uv run python -m elder_common.migrate up --to v2_1

# 回退到 v2.0（仅调试用）
uv run python -m elder_common.migrate down --to v2_0

# 当前版本
uv run python -m elder_common.migrate current
```

### 本地基础设施

```bash
# 起 CloudBase / COS / TPush / ASR / TTS 本地 mock
docker compose up -d

# 查看 prd.md / AGENTS.md 的 token 表
$EDITOR prd.md      # §4 节（§4.1 色彩 / §4.2 字号 / §4.3 间距 / §4.4 圆角）
$EDITOR AGENTS.md   # §A 节（v2.1 集成规范）
```

### 推送 / 上线前自检（PR 评审清单）

```bash
# 跑这个组合检查所有 v2.1 改动覆盖
uv run pytest tests/integration/test_schema_v21.py -v   && uv run mypy --strict services/ packages/   && uv run ruff check   && uv run python scripts/check_no_hardcoded_tokens.py app/src
```

---

## A. v2.1 服务端 / 客户端集成规范（仅代码层）

> 本节是 v2.1 阶段为补全代码约束而新增，**所有产品/需求决策见 `prd.md`**——本文件不写需求。
> 引用规则：客户端组件名/Pydantic schema/SDK 调用/事务时序等代码层细节在本节；任何"做什么"回到 prd.md。

### A.1 上游 SDK 封装

```python
# elder_common/tts.py — 千问 TTS 封装
def synthesize(text: str) -> AudioUrl:
    """调用 prd.md §4.5 的千问 TTS（粤语男声）；失败抛 UPSTREAM_TTS"""

# elder_common/asr.py — 千问 ASR 封装
def recognize(audio_url: str) -> ASRResult:
    """调用 prd.md §10.4 的千问 ASR（粤语主识别 + 普通话兜底）；
    返回 confidence < 0.6 由客户端逻辑处理（不消耗轮数）"""

# elder_common/push/tpush.py — TPush 双通道
TPUSH_REMINDER_CHANNEL_ID: str     # 中优，服药提醒（prd.md §11.18）
TPUSH_APPOINTMENT_CHANNEL_ID: str  # 高优，就医提醒，绕过勿扰（prd.md §11.18）
```

> **0.5.0 override**：Android 本地 Agent 使用 `§A.11` 的 `qwen3-tts-flash-realtime` + `Kiki`，不调用本节服务端 TTS 封装；本节保留为 v2.x 服务端回滚参考。

- SDK 调用统一在 `elder_common`；路由层不允许直接 import 千问 / TPush SDK
- 任何 SDK 失败：抛 `elder_common.errors.AppError`，由 §4 全局异常处理器映射到 prd.md §6.2 错误码
- 失败兜底走法见 prd.md §4.5（H.17 系统铃声）；服务端**不**降级到第三方铃声 SDK

### A.2 客户端 token 读取约定（Android）

prd.md §4.1-§4.4 定义的 token 表在客户端**唯一**读取位置：

```kotlin
// app/design/src/main/.../tokens/Dimens.kt
object Dimens {
    // 色彩
    val Brand500 = Color(0xFF4A7A4A)
    val Error500 = Color(0xFFC44545)
    val NetYellow = Color(0xFFFFF4E6)
    // 字号档（prd.md §4.2）
    val BodyDefaultSp = 24
    val BodyLargeSp = 28
    val BodyXLargeSp = 32
    // 间距 / 圆角（prd.md §4.3-§4.4）
    val CornerCard = 8.dp
    val AnimDuration = 200
    val AnimEasing = FastOutSlowInEasing
}

// 禁止硬编码：客户端 grep 检查硬编码的颜色 / 字号字符串
```

- Compose / XML 视图**禁止**直接写 `#4A7A4A`、`24sp`、`8.dp`；必须引用 token
- Token 表**不**从服务端下发（prd.md §I-1：客户端/服务端配置都不引入品牌色变体）

### A.3 客户端系统组件命名

| 组件 | 文件 | 引用 |
|------|------|------|
| `ElderToast` | `app/ui/component/ElderToast.kt` | prd.md §4.8 |
| `NetworkYellowBar` | `app/ui/component/NetworkYellowBar.kt` | prd.md §4.9 |
| `ElderEmptyState` | `app/ui/component/ElderEmptyState.kt` | prd.md §4.7 |
| `LoadingState` | `app/ui/component/LoadingState.kt` | prd.md §4.10 |

- 每个组件**必须**在文档头注明 `// 对应 prd.md §X.Y`；组件不可绕过 §4 规范"自创新规范"
- 权限申请时机：麦克风 / 相机 / 通知按 prd.md §4.6 表格实现；**不二次引导**（§G.1 决议 29）

### A.4 Pydantic schema 收紧

```python
# reminder_service/app/schemas/reminder.py — v2.1 收紧
from typing import Literal
from datetime import datetime, timedelta
from pydantic import BaseModel, Field, model_validator

class MedicationDosage(BaseModel):
    # 对应 prd.md §3.2.3（§I-4.C）— 3 选 1 + 备注
    type: Literal["pill", "half", "spoon"]
    note: str | None = Field(default=None, max_length=100)

class AppointmentPayload(BaseModel):
    # 对应 prd.md §3.2.4 + §K.3a + §E.4 决议 13/14
    hospital: str = Field(min_length=1, max_length=20)
    department: str = Field(min_length=1, max_length=20)
    datetime: datetime
    advance_remind_min: Literal[30, 60, 120] = 60
    note: str | None = Field(default=None, max_length=100)
    repeat: Literal["none"] | None = None  # §K.3d：UI 不录入

    @model_validator(mode="after")
    def check_advance(self):
        # 对应 prd.md §3.2.4 §E.4 决议 14：日期 + 提前 > 现在
        from elder_common.time import now_utc
        if self.datetime - timedelta(minutes=self.advance_remind_min) <= now_utc():
            raise ValueError("reminder_time_past")
        return self

class ReminderBase(BaseModel):
    # 对应 prd.md §6.1 / §11.18
    type: Literal["medication", "appointment"]
    channel_priority: Literal["mid", "high"] = "mid"
```

- v2.0 schema 字段 (`type: "custom"`, `custom_value`) **删除**——不再读取；老数据迁移脚本在 `migrations/v2_1.py`
- 校验失败映射 prd.md §6.2 错误码：`ReminderTimePast` → 422 `REMINDER_TIME_PAST`
- §A.4 `channel_priority` 默认值由服务端按 `type` 自动设：medication → mid；appointment → high

### A.5 Bind 流程服务端事务

```python
# account_service/app/services/bind.py — 对应 prd.md §3.2.7 / §3.2.8 + §H.25
@router.post("/v1/bind/confirm")
async def bind_confirm(body: BindConfirmRequest):
    async with db.transaction():
        # 1. 创建 elder_profile
        elder = await elder_profile_repo.create(...)

        # 2. 创建 binding（primary 或降级 secondary，§H.25 时间最早成 primary）
        role = "primary"
        existing_primary = await binding_repo.find_primary(elder.id)
        if existing_primary is not None:
            role = "secondary"
        binding = await binding_repo.create(
            family_id=body.family_user_id,
            elder_id=elder.id,
            role=role,
        )

        # 3. 标记 bind_attempt.status = confirmed
        await bind_attempt_repo.update(
            body.bind_attempt_id,
            status="confirmed",
            elder_id=elder.id,
            binding_id=binding.id,
        )

    # 4. 任意步骤失败 → 整事务回滚（CloudBase NoSQL 不支持事务，用 idempotency pattern）
    return {"elder_id": elder.id, "binding_id": binding.id, "role": role}
```

```python
# 对应 prd.md §3.2.8 §F.4 决议 26 — 拒绝立即失效
@router.post("/v1/bind/confirm")
async def bind_reject(body: BindConfirmRequest):
    await bind_attempt_repo.update(
        body.bind_attempt_id,
        status="rejected",
    )
    # bind_code 立即失效；family_user 必须重新扫码发起新的 bind_code
    return Response(status_code=204)
```

### A.6 Flush-pending 幂等

```python
# agent_service/app/services/pending.py — 对应 prd.md §3.2.9 + §6.1 flush-pending
class PendingDiary(BaseModel):
    pending_id: UUID  # 客户端 UUID
    audio_cos_key: str
    turns: list[Turn]
    text: str = Field(max_length=100)   # prd.md §3.1.4.D1
    summary: str = Field(max_length=60) # prd.md §3.1.4.D2

@router.post("/v1/diary/flush-pending")
async def flush_pending(body: list[PendingDiary]):
    synced, dropped = [], []
    for pd in body:
        # 幂等去重 — 重复 flush 同 pending_id 返回原 diary_id 不重写
        existing = await diary_repo.find_by_pending_id(pd.pending_id)
        if existing:
            synced.append({"pending_id": pd.pending_id, "diary_id": existing.id})
            continue

        # audio_cos_key 24h 过期（prd.md §11.13）；过期但 text/summary 可保留
        if await cos.is_expired(pd.audio_cos_key):
            if pd.text:
                diary = await diary_repo.create(text=pd.text, summary=pd.summary)
                dropped.append({"pending_id": pd.pending_id, "reason": "audio_cos_key expired (>24h)"})
            else:
                dropped.append({"pending_id": pd.pending_id, "reason": "audio_cos_key expired (>24h) and no fallback text"})
            continue

        diary = await diary_repo.create_from_pending(pd)
        synced.append({"pending_id": pd.pending_id, "diary_id": diary.id})

    return {"synced": synced, "dropped": dropped}
```

- 服务端**不**新建 `pending_diary` 集合——它是客户端 Room 表（prd.md §3.2.9）

### A.7 数据模型契约（v2.0 字段保留）

v2.1 不引入新数据模型；以下 v2.0 字段必须保持，服务端 SDK / 测试 assertSchema 不能改：

- `family_user`/`elder_profile`/`binding`/`reminder`/`reminder_ack`/`diary_session`/`diary_entry`/`COS key`（prd.md §5）

**v2.1 唯一新增的 Pydantic 字段**：
- `Reminder.channel_priority: Literal["mid", "high"]`（prd.md §11.18）
- `AppointmentPayload.advance_remind_min: Literal[30, 60, 120]`（prd.md §3.2.4 决议 13）
- `AppointmentPayload.repeat: Literal["none"]`（prd.md §K.3d UI 不录入）

**删除的 Pydantic 字段**：
- `MedicationDosage.type: "custom"`（prd.md §I-4.C 决议）
- `MedicationDosage.custom_value: float`（同上）

- 迁移脚本 `services/*/migrations/v2_1.py`：双写期内新字段 nullable，老 enum 拒绝写入
- 集成测试 `tests/integration/test_schema_v21.py` 跑遍上述字段约束；任何回归 → CI 红

### A.8 阿里云百炼 ASR（v3.0 MVP 客户端唯一上游）

> v3.0 MVP 老人端独立运行（无服务端），客户端直连阿里云百炼 Realtime ASR：`qwen-audio-3.0-realtime-plus`。本节记录 v3.0 起唯一 ASR 集成的代码约束。

**§A.8.1 上游固定项（hardcoded constants）**

```kotlin
// app/src/main/java/com/elder/data/asr/AsrApiClient.kt
companion object {
    const val BAILIAN_WORKSPACE_ID = "llm-svrk4hi977f8t2fe"   // 租户 ID，非密钥
    const val BAILIAN_MODEL = "qwen-audio-3.0-realtime-plus"
    const val BAILIAN_PROVIDER = "bailian"                     // 写入 §5.11 diary_entry.asr_provider
    const val BAILIAN_REGION = "cn-beijing"
    // 对齐 scripts/realtime_quickstart.py；WorkspaceId 与 model 共同决定连接目标
    val WS_URL = "wss://$BAILIAN_WORKSPACE_ID.$BAILIAN_REGION.maas.aliyuncs.com/api-ws/v1/realtime?model=$BAILIAN_MODEL"
    const val AUDIO_FORMAT = "pcm"                              // AudioRecorder 实时回调裸 PCM
    const val SAMPLE_RATE = 16000                               // AudioRecorder 16kHz/mono
}
```

> endpoint、model、鉴权必须与 `scripts/realtime_quickstart.py` 保持同一协议族。模型需要在 workspace 中开通，否则握手或 `session.update` 会失败。

**§A.8.2 协议（Qwen-Audio Realtime WebSocket）**

- 端点：`wss://$BAILIAN_WORKSPACE_ID.$BAILIAN_REGION.maas.aliyuncs.com/api-ws/v1/realtime?model=$BAILIAN_MODEL`
- Auth：`Authorization: Bearer <API_KEY>`（API Key 由用户在 §3.1.9 设置页输入，Keystore-wrapped 密文落盘 §5.11 `api_key_enc`）
- 建连后先发 `session.update`，配置 `modalities=["text"]` 和 `turn_detection=null`（Manual 模式）
- 录音期间持续发送 `input_audio_buffer.append`，`audio` 为 16kHz/16bit/mono PCM 的 Base64
- `conversation.item.input_audio_transcription.delta` 的 `text + stash` 是实时显示文本
- 用户停止录音后发送 `input_audio_buffer.commit`，等待 `conversation.item.input_audio_transcription.completed.transcript` 作为最终文本
- 不发送 `response.create`，避免触发无关的模型回复

**§A.8.3 错误映射**

| 场景 | AppError | code |
|------|----------|------|
| WebSocket 握手 HTTP 401 / 403，或 error.code 表示鉴权失败 | `AsrAuthFailed` | `ASR_AUTH_FAILED` |
| error.code / error.message 表示限流 | `AsrRateLimited` | `ASR_RATE_LIMITED` |
| error.type=`invalid_request_error` / error.code 表示参数错误 | `AsrBadRequest` | `ASR_BAD_REQUEST` |
| WebSocket 失败、server_error、audio transcriber failed | `AsrUpstream` | `ASR_UPSTREAM` |
| WebSocket `onFailure` / IOException | `AsrUpstream` | `ASR_UPSTREAM` |
| session.updated 或 transcription.completed 超时 | `AsrUpstream` | `ASR_UPSTREAM` |
| completed.transcript 空串 | `AsrEmptyTranscript` | `ASR_EMPTY_TRANSCRIPT` |
| API Key 传空 | `AsrAuthFailed` | `ASR_AUTH_FAILED` |

**§A.8.4 客户端 Room schema（§5.11 `asr_config` v3.0.1）**

- `provider` / `endpoint` / `model` / `extra_headers_json` / `audio_format` 列**删除**（asr_config 不在 §18 锁定列表，`DROP TABLE asr_config` + `CREATE TABLE` 是允许的 schema 变更路径）
- 保留 `id` / `api_key_enc` / `updated_at` / `last_test_result`（`last_tested_at` 同义合并到 `updated_at`）
- Room 迁移 `MIGRATION_1_2`（version 1→2）强制丢弃旧 DashScope/Whisper/Custom 配置；用户必须在 §3.1.9 重输百炼 API Key
- DiaryEntry `asrProvider` / `asrModel` 字段保留（diary_entry 在 §18 锁定列表），统一写 `BAILIAN_PROVIDER` / `BAILIAN_MODEL`
- 0.5.0 在 `asr_config` 增加 `minimax_api_key_enc` / `minimax_last_test_result`；`api_key_enc` 继续承载千问 ASR/TTS Key

**§A.8.5 旧 §A.1 千问 ASR 封装的处理**

- `packages/common/elder_common/upstream/asr.py` 与 `AsrApiClient` 旧签名（`transcribe(provider, endpoint, apiKey, model, audioFile, extraHeaders, audioFormat)`）同步下线：
  - 服务端 `elder_common.upstream.asr.recognize` 仍保留（v2.x 服务端可能回滚为 v2.1 架构）；本 MVP 不调用
  - 客户端 `AsrApiClient.transcribe(apiKey, audioFile)` 是 v3.0 唯一签名
- §A.1 旧千问 ASR 描述保留作为 v2.x 回滚参考；v3.0 不读 §A.1

**§A.8.6 测试约束**

- 测试用 `MockWebServer` + `MockResponse.withWebSocketUpgrade(WebSocketListener)`，模拟服务端
- 覆盖 `session.update → append × N → delta → commit → completed` 成功流
- 覆盖 WebSocket 401 和 Realtime error → AppError 映射
- 覆盖空 `completed.transcript` → `AsrEmptyTranscript`
- 覆盖 API Key 空串 → `AsrAuthFailed`（不进 WS）
- 常规 CI 禁止真实调用；提供显式 API Key 时可运行 `AsrApiClientLiveTest` 做真实链路验证

### A.11 Android 0.5.0 本地 Agent

> 对应 `prd.md` §0.1 / §3.1.4 / §3.1.6。0.5.0 不使用 Gateway，用户 BYOK 直连上游。

**§A.11.1 上游与 SDK 边界**

- ASR：`app/src/main/java/com/elder/data/asr/AsrApiClient.kt`，继续使用千问 Realtime。
- LLM：通过 `app/src/main/java/com/elder/data/llm/LlmClientFactory.kt` 按 `asr_config.llm_provider` 路由到对应客户端：`MiniMaxClient`（默认 `minimax`，端点 `https://api.minimax.cn/v1/chat/completions` / 模型 `MiniMax-M3`）/ `QwenLlmClient`（`qwen`，端点 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` / 模型 `qwen-plus`）/ `DeepSeekLlmClient`（`deepseek`，端点 `https://api.deepseek.com/v1/chat/completions` / 模型 `deepseek-chat`）。
- **v0.8.0 修订**：LLM Provider 改为可切换；详见 §A.15。`MiniMaxClient` 代码本体不动，仅被 `LlmClientFactory` 路由引用。
- **0.7.0 默认 Provider 切换**：ASR 默认 `MiniMax Realtime`（§A.12），TTS 默认 `MiniMax Cantonese_KindWoman`（§A.13）；千问 / 百炼作为可选回滚路径保留。
- TTS：`app/src/main/java/com/elder/data/tts/QwenTtsClient.kt`，固定 `qwen3-tts-flash-realtime` + `Kiki`，PCM 24kHz mono 16-bit。
- MiniMax 使用 OkHttp 标准 `tools` / `tool_calls` 协议；不得在路由式 UI 代码中直接构造供应商 JSON。

**§A.11.2 Agent 与 Prompt**

- 本地 Agent 位于 `app/src/main/java/com/elder/agent/`。
- Prompt 使用 `app/src/main/assets/agent/system_vN.txt` / `save_vN.txt` 版本化；修改必须新增版本文件，旧版本保留。
- 安全关键词在调用 LLM 前本地拦截：emergency 直接保存，money / medical 直接换话题。
- MiniMax 工具只有 `ask_clarify` / `save_diary`；工具名、参数、长度和状态迁移必须由 Kotlin 再校验。
- 单轮回复截断 ≤ 25 字，正文 ≤ 100 字，摘要 ≤ 60 字，均在本地落库前执行。

**§A.11.3 Room v3**

- `diary_entry_local` 增加 `summary` / `session_id` / `pending_id`，旧 diary 不清空。
- 新增 `interview_session`：本地保存 `status` / `turns_json` / `draft_text` / `draft_summary`。
- 新增 `pending_diary`：断网录音补做队列；`pending_id` 是幂等键，禁止重复写 diary。
- Migration `2→3` 必须使用 `ALTER TABLE` + `CREATE TABLE`，禁止 drop diary 或 asr_config。

**§A.11.4 失败策略**

- LLM 仅对超时、限流、5xx、断流重试，最多 3 次；鉴权和参数错误立即终止。
- ASR WebSocket 断线时用本地完整音频重新建 session 并重放；不得声称服务端断点续传。
- TTS 失败只展示 `assistant_text`；不得重复调用 LLM，不得把对话轮判为失败。
- 断网自动写入 `pending_diary`，下一次进入主屏时尝试 ASR + LLM 补做。
- 0.5.0 不实现语音打断；录音和 TTS 播放互斥。

**§A.11.5 测试约束**

- `AgentSafetyTest` 覆盖本地安全短路。
- `InterviewAgentTest` 覆盖 tool call、长度限制和 3 次重试。
- `MiniMaxClientTest` 使用 MockWebServer 覆盖 SSE 文本、tool call 分片和鉴权错误。
- `InterviewRepositoryTest` 覆盖 Room 会话序列化；真实供应商调用只允许显式 LiveTest。
- `QwenTtsClientLiveTest` / `MiniMaxClientLiveTest` 通过 `-PDASHSCOPE_API_KEY` / `-PMINIMAX_API_KEY` 显式开启；未提供 Key 必须 skip。
- 实时 TTS 使用全局 DashScope Realtime endpoint，workspace-scoped ASR Key 可能返回 401，必须映射为 `TTS_AUTH_FAILED`。


---


### §A.15 LLM Provider 多源（v2.4 / App 0.8.0 新增；OpenAI 兼容统一封装）

> 对应 PRD §3.1.9 v0.8.0 修订 + §5.11 v0.8.0 扩展。0.8.0 起 LLM Provider 与 ASR / TTS 对齐，3 选 1（千问 / MiniMax / DeepSeek），客户端 BYOK 直连。`LlmClientFactory` 按 `cfg.llmProvider` 路由，与 §A.11 / §A.12 / §A.13 直连架构一致。

**§A.15.1 上游固定项（hardcoded constants）**

```kotlin
// app/src/main/java/com/elder/data/llm/LlmProviderCatalog.kt
object LlmProviderCatalog {
    fun endpointOf(p: LlmProvider): String = when (p) {
        LlmProvider.MINIMAX -> "https://api.minimax.cn/v1/chat/completions"
        LlmProvider.QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        LlmProvider.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
    }
    fun modelOf(p: LlmProvider): String = when (p) {
        LlmProvider.MINIMAX -> "MiniMax-M3"
        LlmProvider.QWEN -> "qwen-plus"
        LlmProvider.DEEPSEEK -> "deepseek-chat"
    }
    fun keyAliasOf(p: LlmProvider): ApiKeyCipher.KeyAlias = when (p) {
        LlmProvider.MINIMAX -> ApiKeyCipher.KEY_MINIMAX_API_KEY_ENC
        LlmProvider.QWEN -> ApiKeyCipher.KEY_QWEN_LLM_API_KEY_ENC
        LlmProvider.DEEPSEEK -> ApiKeyCipher.KEY_DEEPSEEK_LLM_API_KEY_ENC
    }
}
```

- 三个 Provider 协议相同（OpenAI Chat Completions），Body / SSE 流格式一致；Client 实现结构相同（`QwenLlmClient` / `DeepSeekLlmClient` 是 `MiniMaxClient` 的协议同构体），不复制 LLM 业务逻辑。
- 三个 Provider 在 v0.8.0 接入 `tools` / `tool_calls`；MiniMax 的 `tool_calls` 协议（§A.11.1 末段）千问 / DeepSeek 同样支持。
- 测试按钮调 `chat/completions` 带最小 prompt（1 token）；HTTP 200 视为「✓ 连接正常」；401 / 403 → `LLM_AUTH_FAILED`；5xx / 超时 / SSE 断流 → `LLM_UPSTREAM`（§6.4）。

**§A.15.2 错误映射**

| 场景 | AppError | code |
|------|----------|------|
| HTTP 401 / 403（含 SSE 协议层 401） | `LlmAuthFailed` | `LLM_AUTH_FAILED` |
| HTTP 429 | `LlmRateLimited` | `LLM_RATE_LIMITED` |
| HTTP 4xx 其他 / `error.code` 表示参数错 | `LlmBadRequest` | `LLM_BAD_REQUEST` |
| HTTP 5xx / 超时 / SSE 断流 / IOException | `LlmUpstream` | `LLM_UPSTREAM` |
| `completions` 返回空 `choices[0].message.content` | `LlmEmptyResponse` | `LLM_EMPTY_RESPONSE` |
| Provider raw 不在枚举（仅客户端内部态） | `LlmProviderUnknown` | `LLM_PROVIDER_UNKNOWN` |

- `LLM_AUTH_FAILED` / `LLM_RATE_LIMITED` / `LLM_UPSTREAM` 在 PRD §6.4 既有枚举中已存在；`LLM_BAD_REQUEST` / `LLM_EMPTY_RESPONSE` / `LLM_PROVIDER_UNKNOWN` 为 v0.8.0 新增（Kotlin 端 `AppError` 子类）。

**§A.15.3 Room schema（§5.11 asr_config v0.8.0）**

- Migration 5→6 走 `ALTER TABLE` 新增 7 列：`llm_provider` / `llm_endpoint` / `llm_model` / `qwen_llm_api_key_enc` / `qwen_llm_last_test_result` / `deepseek_llm_api_key_enc` / `deepseek_llm_last_test_result`。
- `asr_config` 不在 §18 锁定列表（§5.11 是 v3.0 MVP 本地表）；`ALTER TABLE` 是允许的 schema 变更路径；**不** drop 既有 16 列数据。
- `llm_provider` 默认值由 Migration 在 SQL 层写 `minimax`；客户端代码默认值兜底 `LlmProvider.MINIMAX`，与 PRD §5.11 默认一致。
- Key 列使用 `ApiKeyCipher` 三个独立 alias：`KEY_MINIMAX_API_KEY_ENC` / `KEY_QWEN_LLM_API_KEY_ENC` / `KEY_DEEPSEEK_LLM_API_KEY_ENC`，与 ASR / TTS Key 物理隔离。

**§A.15.4 Agent / Prompt 兼容**

- 三个 Client 都按 OpenAI Chat Completions 实现；`InterviewAgent` 通过 `LlmClientFactory.current()` 拿到当前 Provider 对应 client；`system_v2.txt` / `save_v2.txt` 提示词与 Provider 无关，三 Provider 共用。
- `tools` / `tool_calls` 协议千问 / DeepSeek 同样支持；`AgentSafety.kt` 的 keyword 短路 + `MiniMax` 工具校验逻辑不绑 Provider。
- 三次重试策略（超时 / 限流 / 5xx / 断流；鉴权 / 参数错立即终止）由 `LlmClient` 基类公共逻辑承载，三个 Client 复用。

**§A.15.5 测试约束**

- `QwenLlmClientTest` / `DeepSeekLlmClientTest` 用 `MockWebServer`，覆盖：
  - 200 + 正常 `choices[0].message.content` 流
  - 200 + `tool_calls` 分片（SSE 解析按 OpenAI 协议）
  - 401 / 403 → `LlmAuthFailed`
  - 5xx → `LlmUpstream`（含重试 3 次）
  - 空 `choices` → `LlmEmptyResponse`
- `LlmClientFactoryTest`：根据 `cfg.llmProvider` 路由返回正确 Client 实例。
- `ElderDatabaseMigrationTest` 覆盖 5→6：断言 7 列存在 / `llm_provider` 默认 `minimax`。
- `QwenLlmClientLiveTest` / `DeepSeekLlmClientLiveTest` 通过 `-PQWEN_LLM_API_KEY` / `-PDEEPSEEK_LLM_API_KEY` 显式开启；未提供 Key 必须 skip。
- `scripts/check_no_hardcoded_tokens.py` 仍绿：endpoint / model / key alias 全部写进 `LlmProviderCatalog` 静态常量，UI 不暴露 endpoint / model 输入。
### §A.12 MiniMax ASR（v2.3 / App 0.7.0 新增；客户端 HTTP REST + multipart + SSE 封装）

> 对应 PRD §3.1.9 / §11.28.2。0.7.0 起 ASR 可选 Provider 之一，默认 Provider。客户端 BYOK 直连 MiniMax REST endpoint（无 Gateway），与 §A.11 0.5.0 直连架构一致。
>
> v2.3 起从 WebSocket 占位实现切换为 HTTP REST + multipart 上传 + SSE 流式响应（参考 Python `requests.post(stream=True)` 协议族）；WebSocket 协议字段（`session.start` / `audio.chunk` / `transcript.partial` 等占位）已废弃，仅本节与 `MiniMaxAsrClient.kt` 需要同步调整。

**§A.12.1 上游固定项（hardcoded constants）**

```kotlin
// app/src/main/java/com/elder/data/asr/MiniMaxAsrClient.kt
companion object {
    const val MINIMAX_ASR_PROVIDER = "minimax_realtime"     // §5.11 asr_provider 落库值
    const val REST_URL = "https://api.minimax.cn/v1/speech_to_text"
    const val MINIMAX_ASR_MODEL = "asr-1.0"
    const val STREAM_FLAG = "true"                            // multipart 字段 stream
    const val SAMPLE_RATE = 16_000                            // 上传 .wav 的 PCM sample rate
}
```

> endpoint / model / 鉴权必须与 MiniMax `guides/speech-to-text` 文档保持一致；模型需要在 workspace 中开通，否则 multipart 上传后会回 4xx。

**§A.12.2 协议（HTTP REST + multipart + SSE）**

- 端点：`https://api.minimax.cn/v1/speech_to_text`
- Auth：`Authorization: Bearer <API_KEY>`（API Key 由用户在 §3.1.9 设置页输入，Keystore-wrapped 密文落盘 §5.11 `minimax_api_key_enc`——MiniMax ASR 与 LLM 共用 Key）
- 请求：`POST multipart/form-data`，三字段
  - `model = "asr-1.0"`
  - `stream = "true"`（开启 SSE 增量返回）
  - `file = <wav 文件>`，filename 必须以 `.wav` 结尾；服务端按 16kHz / mono / 16-bit PCM 解析
- 响应：`text/event-stream`，逐行 `data: {...}`，常见两种事件：
  - `data: {"delta":"今天"}` ← 增量文本，逐条累积；`onPartial` 回调触发
  - `data: {"finish":true}` ← 收口事件，停止读取
  - `data: {"error":{"type":..,"code":..,"message":..}}` ← 流中错误（按 §A.12.3 映射）
- 录音场景：`openSession` 内部把 `appendAudio` 累积的 PCM 16kHz/mono/16-bit 写到临时 `.wav`（首个 chunk 写入 RIFF/WAVE 头），`finish` 一次性 POST

**§A.12.3 错误映射**

| 场景 | AppError | code |
|------|----------|------|
| HTTP 401 / 403 | `AsrAuthFailed` | `ASR_AUTH_FAILED` |
| HTTP 429 | `AsrRateLimited` | `ASR_RATE_LIMITED` |
| HTTP 400-499 其他 | `AsrBadRequest` | `ASR_BAD_REQUEST` |
| HTTP 5xx / IOException / 网络断开 | `AsrUpstream` | `ASR_UPSTREAM` |
| 流中 `error` 含 `auth` / `api key` 关键字 | `AsrAuthFailed` | `ASR_AUTH_FAILED` |
| 流中 `error` 含 `throttl` / `rate limit` 关键字 | `AsrRateLimited` | `ASR_RATE_LIMITED` |
| 流中 `error` 含 `invalid` / `bad request` 关键字 | `AsrBadRequest` | `ASR_BAD_REQUEST` |
| 累积 `delta` 后空串 | `AsrEmptyTranscript` | `ASR_EMPTY_TRANSCRIPT` |
| API Key 传空 | `AsrAuthFailed` | `ASR_AUTH_FAILED` |
| 音频文件不存在 / 0 字节 | `AsrBadRequest` | `ASR_BAD_REQUEST` |

**§A.12.4 客户端 Room schema（§5.11 `asr_config` v0.7.0）**

- `asr_provider` / `asr_endpoint` / `asr_model` 列新增；写入 `minimax_realtime` / `REST_URL` / `MINIMAX_ASR_MODEL`
- `api_key_enc` 不动；MiniMax ASR 与 LLM 共用 `minimax_api_key_enc`
- `asr_config` 不在 §18 锁定列表，Migration 4→5 DROP+CREATE 是允许的 schema 变更路径
- DiaryEntry `asrProvider` / `asrModel` 字段保留（diary_entry 在 §18 锁定列表），写入 `MINIMAX_ASR_PROVIDER` / `MINIMAX_ASR_MODEL`

**§A.12.5 测试约束**

- 测试用 `MockWebServer` + `MockResponse.setBody("data: {...}\n...")` 模拟 MiniMax SSE 响应
- 覆盖 `delta × N → finish:true` 成功流 + `onPartial` 触发顺序
- 覆盖 HTTP 401 → `AsrAuthFailed`、HTTP 429 → `AsrRateLimited`、HTTP 400 → `AsrBadRequest`
- 覆盖流中 `error` 含 auth / rate_limit 关键字 → 对应 AppError
- 覆盖空 `finish`（无 delta）→ `AsrEmptyTranscript`
- 覆盖 API Key 空串 → `AsrAuthFailed`（不进 HTTP）
- 覆盖音频文件不存在 → `AsrBadRequest`（不进 HTTP）
- 覆盖 `SocketPolicy.DISCONNECT_AT_START` → `AsrUpstream`
- `MiniMaxAsrClientLiveTest` 走 `-PMINIMAX_API_KEY` 显式开启；未提供 Key 必须 skip

---

### §A.13 MiniMax T2A（TTS，v2.3 / App 0.7.0 新增；客户端 WebSocket `t2a_v2` 封装）

> 对应 PRD §3.1.9 / §11.15。0.7.0 起 TTS 可选 Provider 之一，默认 Provider；voice_id 硬编码为 `Cantonese_KindWoman`，不暴露 UI。客户端 BYOK 直连 MiniMax T2A endpoint。
>
> v2.3 起从 WebSocket 占位实现（`session.start` / `text.chunk` / `audio.delta` / `session.done`）切换为 MiniMax `t2a_v2` 协议族（`connected_success` / `task_start` / `task_started` / `task_continue` / `is_final` / `task_finish`），与 §A.12 ASR 协议家族保持一致；音频帧改 hex 解码，audio_setting 改 `pcm / 24000Hz / mono` 以匹配 `AndroidPcmSink`。

**§A.13.1 上游固定项（hardcoded constants）**

> ⚠️ **0.7.0 校验待办（`voice_id_validated=false` / `audio_format_validated=false`）**
>
> 下面 4 个常量依赖真实 MiniMax T2A 行为校验；当前仅有 Python 协议族 + Mock 测试覆盖，**真 Key 跑通前不能 100% 确认**。校验通过后请把这两个 `*_validated=false` 标志从本节删除，并在 §A.13.5 测试约束里补一行"LiveTest PASS 记录"。

```kotlin
// app/src/main/java/com/elder/data/tts/MiniMaxTtsClient.kt
companion object {
    const val MINIMAX_TTS_PROVIDER = "minimax"                 // §5.11 tts_provider 落库值
    const val WS_URL = "wss://api.minimax.cn/ws/v1/t2a_v2"      // 必须带 _v2 后缀
    const val MINIMAX_TTS_MODEL = "speech-2.8-hd"               // 与参考 Python 例子一致
    const val MINIMAX_TTS_VOICE_ID = "Cantonese_KindWoman"      // UI 不暴露，PRD §3.1.9 v0.7.0 修订
    const val SAMPLE_RATE = 24_000                               // 与 AndroidPcmSink 一致
    const val BITRATE = 128_000                                  // PCM 无效字段，保留占位
    const val AUDIO_FORMAT = "pcm"                               // 24kHz / mono / 16-bit
}
```

> - `voice_id = "Cantonese_KindWoman"` 硬编码（**`voice_id_validated=false`**）：来自 PRD §3.1.9 v0.7.0 决议，与 MiniMax `faq/system-voice-id` 文档未对齐校验。Python 参考例子用的是 `male-qn-qingse`（普通话男声·清澈），**不能直接确认粤语 voice 叫 `Cantonese_KindWoman`**。
>   - 真 Key 跑 `MiniMaxTtsClientLiveTest -PMINIMAX_TTS_API_KEY=...`：若 `task_failed` 含 `voice` / `invalid` 关键字 → 需查 MiniMax 文档替换；同步修改 `MINIMAX_TTS_VOICE_ID` 常量与 `asr_config.tts_voice_id` 落库值（已有用户数据需 Migration）。
> - `audio_setting.format = "pcm"` + `sample_rate = 24_000` + `channel = 1`（**`audio_format_validated=false`**）：与 `AndroidPcmSink`（24kHz / mono / 16-bit）匹配，音频 hex 解码后直喂 sink，**零额外依赖**。
>   - 真 Key 跑 LiveTest：若 `task_failed` 含 `audio_setting` / `format` / `sample_rate` 关键字 → 服务端拒绝此组合。两个回滚路径：
>     - 方案 A：`AUDIO_FORMAT` 改 `"mp3"` + `SAMPLE_RATE` 改 `32_000`，新增 `MediaCodec` MP3 → PCM 解码器喂 `AndroidPcmSink.create(32_000)`。
>     - 方案 B：保留 `format=pcm`，`SAMPLE_RATE` 改 `32_000`，`AndroidPcmSink.create(32_000)` 直喂（仅 `sample_rate` 不匹配）。
> - endpoint / model 与 MiniMax T2A 文档 `guides/speech-t2a-websocket` 保持同一协议族；模型需在 workspace 中开通。

**§A.13.2 协议（MiniMax T2A `t2a_v2` WebSocket）**

- 端点：`wss://api.minimax.cn/ws/v1/t2a_v2`
- Auth：`Authorization: Bearer <API_KEY>`（API Key 由用户在 §3.1.9 设置页输入，Keystore-wrapped 密文落盘 §5.11 `tts_minimax_api_key_enc`——MiniMax TTS 独立 Key，与 LLM/ASR 区分）
- 流程（与 §A.12 SSE 风格一致的事件命名）：
  1. 客户端 WebSocket 升级；服务端主动 push `{"event":"connected_success"}`
  2. 客户端发 `{"event":"task_start", model, voice_setting, audio_setting}`
  3. 服务端回 `{"event":"task_started"}`
  4. 客户端发 `{"event":"task_continue", text}`
  5. 服务端连续回流 `{"data":{"audio":"<hex>"}}` 音频块；hex 解码后写 `PcmSink`
  6. 服务端发 `{"is_final":true}`（顶层字段，非嵌套）作为收口
  7. 客户端发 `{"event":"task_finish"}`，再 close WebSocket
- 失败事件：`{"event":"task_failed", error:{code,message}}` → 按 §A.13.3 映射

**§A.13.3 错误映射**

| 场景 | AppError | code |
|------|----------|------|
| WebSocket 握手 HTTP 401 / 403 | `TtsAuthFailed` | `TTS_AUTH_FAILED` |
| `task_failed` 含 `auth` / `api key` / `401` / `403` 关键字 | `TtsAuthFailed` | `TTS_AUTH_FAILED` |
| `task_failed` 含 `invalid` / `bad request` 关键字 | `TtsUpstream` | `TTS_UPSTREAM` |
| `task_failed` 含 `throttl` / `rate limit` / `429` 关键字 | `TtsUpstream`（带 `429` code） | `TTS_UPSTREAM` |
| WebSocket 失败 / server_error / 5xx / IOException | `TtsUpstream` | `TTS_UPSTREAM` |
| 音频帧 hex 解析失败 | `TtsUpstream`（带"invalid hex"） | `TTS_UPSTREAM` |
| 握手超时 / 文本推送后无 `is_final` 超时 | `TtsUpstream`（带"timeout"） | `TTS_UPSTREAM` |
| API Key 传空 | `TtsAuthFailed` | `TTS_AUTH_FAILED` |

> v0.7.0 暂不新增 `TTS_RATE_LIMITED`，沿用既有 `TTS_UPSTREAM`（与 PRD §6.4 一致；保持最小破坏面）。

**§A.13.4 客户端 Room schema（§5.11 `asr_config` v0.7.0）**

- `tts_provider` / `tts_endpoint` / `tts_model` / `tts_voice_id` / `tts_minimax_api_key_enc` / `tts_minimax_last_test_result` 列新增
- `tts_voice_id` 写入 `MINIMAX_TTS_VOICE_ID`；UI 不暴露
- `asr_config` 不在 §18 锁定列表，Migration 4→5 DROP+CREATE 是允许的 schema 变更路径

**§A.13.5 测试约束**

- 测试用 `MockWebServer` + `MockResponse.withWebSocketUpgrade(WebSocketListener)`，listener 在 `onOpen` 钩子里 `send("connected_success")` 启动流程
- 覆盖 `connected_success → task_start → task_started → task_continue → audio.data × N → is_final → task_finish` 成功流 → 返回 `TtsResult`，sink.byteCount == 累积字节
- 覆盖 WebSocket 握手 HTTP 401 → `TtsAuthFailed`
- 覆盖 `task_failed` 含 auth 关键字 → `TtsAuthFailed`
- 覆盖 API Key 空串 → `TtsAuthFailed`（不发 WS）
- 覆盖 `voice_id` / `model` / `WS_URL` / `AUDIO_FORMAT` 与 §A.13.1 一致
- `MiniMaxTtsClientLiveTest` 走 `-PMINIMAX_TTS_API_KEY` 显式开启（兜底 `MINIMAX_API_KEY`）；未提供 Key 必须 skip
- **LiveTest PASS 记录**（真 Key 校验通过后填写，未填写则 §A.13.1 的 `*_validated=false` 警告不能删除）：
  - 校验日期：____-__-__
  - 校验人：____
  - voice_id 实际可用：`____`（确认 `Cantonese_KindWoman` 或替换为新值）
  - audio_setting 实际可用：`format=pcm / sample_rate=24000 / channel=1` ✅ / 替换为 `____`
  - 失败事件 / 重试路径：无 / 详见 PR #____
- `AppError.TtsUpstream` 失败时上层只展示 `assistant_text`，不重试 LLM（沿用 §A.11.4 TTS 失败策略）

---

### §A.14 v2.3 / App 0.7.0 Provider 切换统一约定

> 对应 PRD §3.1.9。0.7.0 起 ASR / TTS 双 Provider 可切换，统一约束：

- **`asr_provider` 取值**：`bailian`（默认回滚）/ `minimax_realtime`（默认）。
- **`tts_provider` 取值**：`qwen`（默认回滚）/ `minimax`（默认）。
- **ServiceLocator 工厂**：`asrClient(): AsrClient` / `ttsClient(): TtsClient` 按当前 `AsrConfig` 返回对应实例；ViewModel 在录音 / 播放前解析一次，不在 UI 层直接判断 Provider。
- **UI 入口**：Settings → AI 服务 → 两个入口卡（语音识别 / 语音播报）→ 点击进入对应 Provider 子页（Provider 选项 + Key 输入 + 测试）。
- **不暴露字段**：endpoint / model / voice_id 在 UI 不暴露；Provider 切换后字段从客户端硬编码常量同步到 `asr_config` 表。
- **回滚路径**：千问 / 百炼作为可选 Provider 保留，老人切到 MiniMax 出问题时可在 Settings 切回。
