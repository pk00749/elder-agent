# AGENT.md — 代码实施规范

> 本文档只规定**代码怎么写**。产品需求、数据模型、API 契约、行为约束以 `prd.md` 为准。
>
> 适用范围：本仓库内的所有服务端代码。

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
- `AGENT.md` 修订在 PR 描述里说明影响的章节号。
- 不在 `AGENT.md` 里复述 `prd.md` 已有内容；只写引用。
