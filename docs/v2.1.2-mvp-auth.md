# PRD v2.1.2 — MVP 免 SMS 验证码（详细补充）

> 本文档是 prd.md v2.1.2 修订记录的展开，聚焦鉴权相关章节（§5.1 / §6.1 / §6.2 / §11.11）。
> 与 prd.md / AGENTS.md 的关系：本文档不重复既有内容，只补充必要的代码层细节与 v2.x 迁移指引。

## 背景

MVP 阶段不接腾讯云 SMS，理由：

- **成本可控但仍属额外依赖**：每个验证码 ¥0.04-0.05，月活 100-1000 人量级月成本两位数元；非阻断因素
- **老人操作障碍**：6 位验证码输入 + 倒计时对老人侧 UX 是负担（§I-1 设计原则）
- **MVP 范围控制**：短信 SDK + KMS 密钥管理 + 反垃圾号段维护 三件套不进 MVP

家属端走"匿名设备 token + 静默注册"流程；v2.x 接 SMS 时只加端点，不动现有绑定/提醒/日记主流程。

## 数据模型变更（§5.1）

`family_user` 加 `device_token: string | None` 字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `device_token` | string | 否 | UUID v4；anonymous-device 流程唯一标识（**MVP 必填之一**；v2.x 接 SMS 后可选） |

**MVP 阶段字段约束**：`phone` 与 `device_token` **二选一非空**：

- MVP 匿名设备流程：`phone = ""`、`device_token = UUID`（UUID 由 client 首启生成，存 DataStore）
- v2.x 接 SMS 后：`phone = "13800138000"`、`device_token = null`（或保留）

**AGENTS.md §18 红线处理**：`phone` 仍标"必填"以满足 §18；通过 prd.md v2.1.2 修订记录**放宽业务约束**（phone/device_token 二选一），schema 必填属性不动。

## API 变更（§6.1）

### 新增 `/v1/auth/anonymous-device`

```http
POST /v1/auth/anonymous-device
Content-Type: application/json
```

**请求**：
```json
{ "device_token": "550e8400-e29b-41d4-a716-446655440000" }
```

**字段约束**：
- `device_token`: string, 长度 8-128, 正则 `^[A-Za-z0-9\-_]{8,128}$`

**响应 200**：
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user_id": "c24778e8-ed8f-4116-895d-2fdcc8ca91f4",
  "role": "family"
}
```

**行为**：

1. 查 `family_users` 集合中 `device_token == body.device_token` 的记录
2. **找到** → 复用现有 `family_user.id`，签发新 JWT 返回（实现 §11.11 JWT 7 天过期的"过期续签"）
3. **没找到** → 新建 `family_user`（`phone = ""`、`device_token = body.device_token`），签发 JWT 返回

**MVP 阶段不限流**——鉴权路径无上游依赖、滥用风险低；v2.x 接 SMS 后由网关层做 per-IP 限流（per-device_token 限流在 idempotent 路径不可触发，参考 PR 1 review）。

**错误码**：

| HTTP | code | 含义 |
|------|------|------|
| 422 | `VALIDATION_ERROR` | schema 校验失败（device_token 缺失 / 长度 / 正则） |
| 500 | `INTERNAL_ERROR` | 服务端异常 |

### 删除 `/v1/auth/sms-code` 与 `/v1/auth/login`

路由删除；SMS 上游模块（`elder_common/upstream/sms.py`）**保留并标 MVP-DEFER**，v2.x 接 SMS 时：

1. 取消 routes/auth.py 中 `# @router.post("/sms-code")` 注释
2. 取消 `# @router.post("/login")` 注释
3. 启用 SMS 上游：`elder_common.upstream.sms.send_code(...)`（保留，零改动）
4. 重新启用 `AUTH_SMS_SENT` / `AUTH_LOGIN` 事件

## 错误码新增（§6.2）

| HTTP | code | 含义 |
|------|------|------|
| 422 | `DEVICE_TOKEN_REQUIRED` | `/v1/auth/anonymous-device` 入参缺 device_token 或格式不合法 |

## JWT 续签（§11.11）

**修订前**：JWT 7 天有效 → 过期客户端重新走 `/v1/auth/sms-code` 登录
**修订后**：JWT 7 天有效 → 过期客户端**静默重新**走 `/v1/auth/anonymous-device`（不通知、不弹窗）

幂等：同 device_token 重复调用，复用 user_id，签发新 JWT。

## 客户端流程

### 家属端

```
[首启] 用户点「我是家人」
  → ensureDeviceToken() (本地生成 UUID, 存 DataStore)
  → POST /v1/auth/anonymous-device
  → 保存 JWT (role=family) 到 DataStore
  → 跳转 FamilyHome

[后续启动] DataStore 有 device_token + role=family
  → 直接 FamilyHome（bootstrap 按 snap.role 路由）

[JWT 过期] 任意 API 返回 401
  → 静默重调 /v1/auth/anonymous-device（同 device_token）
  → 拿到新 JWT 后重试原请求
```

### 老人端

```
[首启] 用户点「我是爸妈」
  → ensureDeviceToken() (本地生成 UUID, 存 DataStore)
  → saveRole(role="elder") 写到 DataStore
  → 跳转 ElderHome

[绑定流程] elder 进入设置 → 生成本机二维码
  → POST /v1/bind/code (X-Elder-Device-Token 头)
  → 显示二维码 + 5 分钟倒计时

[家属扫码后] 家属端 POST /v1/bind/elder → 创建 bind_attempt
[elder 轮询] GET /v1/bind/pending (X-Elder-Device-Token)
[elder 同意] POST /v1/bind/confirm (X-Elder-Device-Token)
  → 服务端创建 elder_profile + binding + 签发 elder JWT
  → 客户端 save(token, userId=elder_id, role=elder, elderId=elder_id)
  → 此后所有请求改用 JWT（Authorization: Bearer）

[后续启动] DataStore 有 device_token + role=elder
  → 直接 ElderHome（无论是否已绑定都能看到 diary 按钮）
```

**MVP 阶段没有"老人重装恢复"**——与 §11.20 / §H.20 一致；重装即作废，需家属重新扫码。

## v2.x 迁移开关

接 SMS 时按以下顺序切换（参考 AGENTS.md §A.x 增量条目）：

1. **服务端**：取消 routes/auth.py 中 SMS 路由注释；启用 SMS 上游模块（已就绪）
2. **客户端**：在 TokenStore 增加 phone 字段；从 IdentitySelectionScreen 引导家属首次绑定手机号（一次性"升级账号"卡）
3. **数据**：将已有匿名 family_user 的 phone 字段从空字符串改为 nullable（schema 迁移脚本 `migrations/v2_x_sms_upgrade.py`）
4. **灰度**：用 `AUTH_MODE` 环境变量切回（`anonymous | sms | both`），先 10% 流量
5. **回回**：SMS 代码全保留在 git 历史；切回只需一个开关

## 限制与风险

| 项 | MVP 现状 | v2.x 改进 |
|---|---|---|
| 账号恢复 | 不支持（重装即作废） | SMS 找回 + 历史记录绑定 |
| 多设备 | 一设备一 family_user（device_token 唯一） | SMS phone 唯一允许多设备 |
| 滥用防护 | 无（鉴权路径零成本） | 网关层 per-IP 限流 + 设备指纹 |
| 老年用户身份证明 | 走绑定流程（家属扫码 + 老人确认） | 同 MVP + 可选手机号二次确认 |
