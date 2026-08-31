# 老友（Android）—— 服务端 monorepo

> 老友是一款面向长者的 Android 应用，配套的服务端 monorepo。MVP 三功能（老人端：吃药提醒 / AI 访谈写日志 / 就医提醒；家属端：配置提醒 / 查看日志）。  
> 详细 PRD 见 `prd.md`；实施规范见 `AGENTS.md`。

## 仓库结构

```
elder-agent/
├── prd.md                     # 产品需求（唯一真源）
├── AGENTS.md                  # 代码实施规范（仓库结构 / 错误处理 / 静态检查 / 日志 / 配置 / 路由 / 数据 / 鉴权 / 测试 / CI）
├── pyproject.toml             # uv workspace 根
├── uv.lock
├── .ruff.toml                 # ruff 配置
├── mypy.ini                   # mypy --strict 配置
├── .env.example                # 所有密钥占位
├── .gitignore
├── .gitattributes
├── docker-compose.yml         # 本地 mock infra
├── packages/
│   └── common/                # 跨服务共享：errors / config / logging / upstream / cos
└── services/
    ├── agent_service/         # PRD §8.1  /v1/agent/*
    ├── reminder_service/      # PRD §8.1  /v1/reminders/*
    └── account_service/       # PRD §8.1  /v1/auth/* /v1/bind/* /v1/diary*
```

## 三服务端口（PRD §8.1）

| 服务 | 端口 | 路径前缀 |
|---|---|---|
| agent-service | 8001 | `/v1/agent/*` |
| reminder-service | 8002 | `/v1/reminders*` |
| account-service | 8003 | `/v1/auth/*` `/v1/bind/*` `/v1/diary*` |

## 快速开始

```bash
# 1. 安装 uv（0.11.16+）
curl -LsSf https://astral.sh/uv/install.sh | sh

# 2. 同步 workspace + 安装所有服务依赖
uv sync --all-packages

# 3. 跑本地 mock infra（DSH / ASR / TTS / COS / TPush / SMS）
docker compose up -d

# 4. 复制环境变量
cp .env.example .env

# 5. 跑三个服务（每个一个终端）
uv run --package agent-service uvicorn app.main:app --port 8001 --reload
uv run --package reminder-service uvicorn app.main:app --port 8002 --reload
uv run --package account-service uvicorn app.main:app --port 8003 --reload
```

## 命令速查（AGENTS.md §19）

```bash
# 静态检查
uv run ruff check packages services mock
uv run ruff format packages services mock
uv run mypy --strict packages
uv run mypy --strict --explicit-package-bases services/agent_service/app
uv run mypy --strict --explicit-package-bases services/reminder_service/app
uv run mypy --strict --explicit-package-bases services/account_service/app
uv run mypy --strict --explicit-package-bases mock

# 测试
uv run pytest tests/ -v                          # 全部
uv run pytest tests/ -m "not external"            # 不依赖 mock
MOCK_INFRA=enabled uv run pytest tests/ -m external   # 需 docker compose up

# 客户端 token 检查（CI §19）
uv run python scripts/check_no_hardcoded_tokens.py app/src

# 数据库迁移（PR 2 起接真实 SDK）
uv run python -m elder_common.migrate current
uv run python -m elder_common.migrate up --to v2_1
uv run python -m elder_common.migrate down --to v2_0

# 索引 manifest
uv run python scripts/create_indexes.py
```

## 里程碑

- ✅ **PR 1（当前）**：基建 + 设计 token + 主屏空态 + mock infra
- ⏳ **PR 2**：账号服务（auth / bind 三个端点）+ 家属端扫码
- ⏳ **PR 3**：提醒服务（CRUD + 应答 + AlarmManager + TPush 推送）
- ⏳ **PR 4**：Agent 服务（ASR / TTS / LLM + 访谈屏 + 日记屏）

## 关键约定

- 中文 docstring / 注释（AGENTS.md §6）
- Pydantic v2 + FastAPI
- 结构化日志（structlog）+ sanitize 屏蔽敏感字段
- mypy `--strict` + 禁 `Any`（用 `object` + cast）
- Ruff 全 workspace 一致
- 任何 `/v1/*` 端点前先看 PRD §6.1
- 任何数据模型字段先看 PRD §5
