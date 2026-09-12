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
- **不要在客户端引入新的上游 SDK**（千问 / TPush / COS / ASR / TTS）——所有上游调用统一在 `elder_common` 封装（§A.1）。新增上游必须先在 §A.x 增加子节 + 评审。
- **不要编写超过 500 行的客户端模块**（除非有文档化理由）。理由：与 OpenAI Codex 样例反模式一致；高触碰文件会吸引无关改动。

### 服务端 / 工程规范

- **不要在路由层写 `try/except` 或直接拼 SQL/NoSQL**——必须走 `packages/common` 仓储 + 全局异常处理器（§4 / §10）。理由：破坏错误码统一 + 越权校验丢失。
- **不要绕开 `require_role` / `require_elder_match` 装饰器**（§9 / §13）。理由：鉴权中间件是路由层唯一的角色校验；裸路由会立刻被 §14 测试拒。
- **不要使用 `# noqa: E501` / `# type: ignore[error-code]` 绕过 lint**——除非有显式注释 `# <理由>`。理由：CI §15 强制 ruff/mypy 全绿；无理由注释会被打回。
- **不要写 mock 数据反向测试**（mock 某个被删除逻辑的负向 case）。理由：v2.1 §A.4 紧致 `MedicationDosage.type` 枚举后，`type=custom` 测试已无意义——保留会过期；正向测试覆盖即可。
- **不要为静态定义值加测试**（常量、`UPPER_SNAKE_CASE` 配置）。理由：和 OpenAI Codex 样例的反模式一致——静态定义不会跑偏，加测试是 noise。
- **不要在客户端用 `if elder_id == current_user.elder_id`** 形式做越权校验——必须在仓储层做（§10）。理由：路由层 / 业务层越权校验易漏，仓储层是唯一真源。

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
