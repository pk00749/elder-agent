# 老友（Android）PRD — v2.0

> 文档版本：v2.0
> 文档状态：定稿
> 品牌：老友
> 平台：Android App（不再做微信小程序）
> 目标读者：产品 / 工程 / 测试
> 与 AGENT.md 的关系：PRD 是产品与需求的唯一真源；AGENT.md 仅承载服务端实施规范。

## 修订记录

| 版本 | 日期       | 作者  | 主要变更 |
|------|------------|-------|----------|
| v1.0 | 2026-08-23 | Codex | 推倒重来：平台改为 Android；功能缩到 MVP 三功能；引入 DeepSeek Harness + 腾讯云 Agent + COS |
| v2.0 | 2026-08-24 | Codex | PRD/AGENT.md 分工重排：所有需求迁移到 PRD；AGENT.md 只留实施规范；新增 §3.1.4 Agent 行为约束、§7 安全与隐私、§10 系统质量要求；D1–D4 锁定，新增 D5 |

---

## 0. 一页纸

**产品**：老友（Android App）

**形态**：单个 Android 应用，安装时选择身份——**老人** 或 **家属**；同一台设备可切换身份，但一个老人账号只绑定一个主设备。

**MVP 功能（3 + 3）**

| 端 | 功能 | 一句话 |
|----|------|--------|
| 老人 | 服药提醒 | 到点响铃 + 大字「该吃药啦」 + 一键「已吃」 |
| 老人 | AI 访谈写日志 | 长按说话，老友通过语音提问协助老人回忆当日并自动整理成日志 |
| 老人 | 就医提醒 | 到点响铃 + 大字「该去看医生啦」 + 一键「知道了」 |
| 家属 | 设置服药提醒 | 表单录入药名 / 时间 / 频次 |
| 家属 | 设置就医提醒 | 表单录入医院 / 科室 / 时间 / 备注 |
| 家属 | 查看日志 | 按日期查看老人当日语音与文字日志 |

**老年友好原则（硬约束）**
1. 默认正文字号 ≥ 20sp，按钮文字 ≥ 28sp
2. 主界面元素 ≤ 3 个（不堆叠）
3. 所有操作 ≤ 2 步（含确认）
4. 语音优先：默认按钮「按住说话」
5. 关键操作有震动反馈 + 语音播报反馈

**核心架构（一句话）**
> Android 客户端 + DeepSeek Harness SDK（腾讯云 SCF/CVM 跑 Agent Runtime）+ 腾讯云 CloudBase 元数据 + 腾讯云 COS 存日志（语音 + 文字）。

**Agent 能力范围（MVP）**
- 一个会话型 Agent（用于 AI 访谈写日志），行为约束见 §3.1.4
- 通用大模型 API（文本对话 / 日志整理 / 总结）
- 语音大模型 API（ASR 老人语音 → 文字；TTS Agent 回复 → 老人可听）

**架构决策（已锁定，见 §9）**
- D1：Android 客户端 = 原生 Kotlin
- D2：后端存储 = CloudBase（NoSQL）+ COS
- D3：语音能力 = ASR + TTS 两个独立 API
- D4：双端形态 = 单 App 切换身份
- D5：Android 不使用 DSH 客户端 SDK，服务端独占 DeepSeek Harness

---

## 1. 背景与目标

### 1.1 背景

国内 60+ 长者规模大，独居/空巢比例高，但**对手机 App 操作普遍存在抵触**——文字太小、流程太多、广告太多、隐私顾虑重。市面上的"长辈版"App 多停留在皮肤层（换大图标、放大字号），缺少**真正降低操作负担的语音优先交互**，也没有围绕老人真实高频场景（吃药、看病、记日常）打通子女端配置能力。

老友从这两个痛点切入：
- **老人侧**：操作只有"按住说话"和"按一下确认"，不出现任何二级菜单
- **子女侧**：用普通表单快速给老人配置任务，自己能看到老人近况

### 1.2 目标

- MVP 在 4–6 周内可演示
- 老人端冷启动到首次完成任意任务 ≤ 30 秒
- 老人端零文字输入也能完成全部 MVP 任务

### 1.3 非目标（MVP 不做）

- iOS / 鸿蒙 / iPad 适配（仅 Android 手机）
- 微信小程序 / Web / 桌面
- 多老人账号家庭组
- 紧急呼救、跌倒检测、医疗问答、转人工坐席
- 离线使用（必须有网）
- 老人端「自己配置提醒」（全部由家属端配置）
- 国际化（仅中文普通话；方言与多语言列入后续）

---

## 2. 用户与角色

| 角色 | 客户端身份 | 主要动作 |
|------|------------|----------|
| 老人 | 安装时选「我是老人」或被家属扫码绑定 | 收到提醒、说日志、查看今日 |
| 家属 | 安装时选「我是家属」 | 配置提醒、查看日志、添加老人 |

MVP 不做客服、医生、群组等其他角色。

---

## 3. MVP 功能详述

### 3.1 老人端

#### 3.1.1 服药提醒
- **触发**：到点（按家属配置的时间）弹出全屏卡片
- **界面**：
  - 顶部大标题：「该吃药啦」
  - 中部大图示：药丸图标
  - 中部副标题：「阿莫西林 · 1 粒」（药名 / 剂量从配置读取）
  - 底部一个大按钮：「✓ 已吃」
- **交互**：
  - 屏幕亮起 + 震动 + TTS 播报「该吃阿莫西林了」
  - 「已吃」点击后写入日志（时间戳 + 药名），关卡片，3 秒后回到主屏
  - 5 分钟未点：写入「未服药」日志，再 5 分钟再次提醒；30 分钟仍无应答则推送通知给家属（通道见 §11.2）
- **失败兜底**：
  - 网络断开：本地 AlarmManager 仍能响铃，标记为「离线已记录」，联网后同步（缓存位置见 §11.1）

#### 3.1.2 AI 访谈写日志（主流程）
- **入口**：主屏底部大按钮「✎ 说话写日记」
- **交互**：
  1. 点击按钮进入访谈界面，按住底部「按住说话」
  2. 松开后：先 ASR 转文字 → 送给 Agent（General LLM）
  3. Agent 回复一句话引导（例：「今天去看老朋友了吗？」），TTS 播报给老人
  4. 循环 3–6 轮后，Agent 自动总结成一段日记（≤ 100 字）
  5. 老人看到总结，可点「保存」或「✎ 改一下」（改一下回到第 4 轮加一轮）
  6. 保存：日记（文字 + 每轮语音 URL）写入 COS，日记元信息（日期 / 时长 / 关键词）写入后端
- **行为约束**：见 §3.1.4（Agent 必须遵守的硬规则，PRD 唯一真源）
- **兜底**：
  - ASR 失败：弹「没听清，再说一次」按钮，不打断老人
  - Agent 超时 30 秒：兜底回复「今天到这儿吧，明天继续」并保存已收集内容
  - 老人中途退出：保存已有语音 + 文字片段，不算完成

#### 3.1.3 就医提醒
- 与服药提醒同模板：
  - 标题：「该去看医生啦」
  - 副标题：「协和医院 · 心内科 · 上午 10:00」
  - 按钮：「✓ 知道了」
- 5 分钟无应答 → 推送家属（通道见 §11.2）

### 3.1.4 Agent 行为约束（硬规则）

> 本节是 AI 访谈写日志功能（§3.1.2）中 Agent 必须遵守的行为约束。服务端在系统提示词与 tool 定义中按本节实现。AGENT.md 中相关代码引用本节，不重复定义。

**A. 表达约束**
- A1. 每次回复只问一个问题或说一件事，说完即停；禁止连续追问
- A2. 单次回复不超过 25 个汉字
- A3. 使用口语化中文，避免书面语；可用常见感叹词（「嗯」「挺好的」「然后呢？」）
- A4. 不得复述老人说过的话，不得总结，不得用「1.2.3.4.」分点
- A5. 方言容忍：ASR 转写后保留口语化文本，不要因方言用法判定为无效输入

**B. 安全约束**
- B1. 老人提及金钱、转账、验证码、陌生链接时，立即调用 ask_clarify 工具跳过该话题并转向日常闲聊
- B2. 老人提及急救类关键词（「摔了」「喘不上气」「胸口疼」），立即调用 save_diary 工具保存当前轮并终止会话
- B3. 老人提及悲伤或健康话题时，不得主动给医疗建议；只用一句「听起来不容易」带过
- B4. 老人提及医疗问答（症状诊断、用药建议），调用 ask_clarify 换话题

**C. 收尾触发（满足任一即收尾）**
- C1. 信息维度足够：对话累计覆盖「时间 / 地点 / 人物 / 事件」≥ 2 项
- C2. 轮数达到硬上限（默认 8 轮，见 §11.5）
- C3. 老人明确说收尾（如「就到这」「不聊了」）
- 收尾动作：调用 save_diary 工具，将 text（≤ 100 字）+ summary（≤ 60 字）落库，audio_segments 含每轮语音 COS key 与 ASR 文字

**D. 输出约束**
- D1. 日记正文 text 长度 ≤ 100 字
- D2. 一句话摘要 summary 长度 ≤ 60 字
- D3. 必须同时返回 text + summary + audio_segments 三段；不得只返回其中之一

### 3.2 家属端

#### 3.2.1 设置服药提醒
- 入口：「给爸妈加个吃药提醒」按钮
- 表单字段：
  - 药名（必填，文本）
  - 剂量（必填，下拉：1 粒 / 半粒 / 1 勺 / 自定义）
  - 时间列表（必填，列表，至少 1 条；重复规则：每天 / 周一三五 / 自定义）
  - 备注（可选）
- 提交：写入后端；推送到老人设备（推送通道见 §11.2）
- 列表：已配置的所有服药提醒，可编辑 / 删除 / 暂停

#### 3.2.2 设置就医提醒
- 字段：
  - 医院（文本）
  - 科室（文本）
  - 时间（日期 + 时分）
  - 备注（可选）
- 提交：写入后端
- 列表：可编辑 / 删除

#### 3.2.3 查看日志
- 入口：「看看爸妈今天怎么样」
- 列表按日期倒序，每日一行卡片：日期 + 一句话摘要（来自当日 Agent 总结）+ 「▶ 听」按钮
- 点开日期：展开当日所有日志条目（每条：时间 + 文字 + 语音播放按钮）
- 底部「本周 / 本月」快捷筛选

### 3.3 跨端流程
- **绑定老人**：家属首次安装后输入老人手机号 → 发送短信验证码 → 老人侧收到绑定确认；或扫老人设备上的二维码
- **数据归属**：老人账号下所有数据；家属只读 + 配置权，不可删除日志（见 §11.3）

---

## 4. 老年友好设计（硬约束）

1. 默认正文字号 ≥ 20sp，按钮文字 ≥ 28sp，标题 ≥ 32sp
2. 主屏元素 ≤ 3 个：今日提醒卡片、「说话写日记」按钮、「设置」（隐藏入口，连续点 5 次主屏中央进入）
3. 所有操作 ≤ 2 步（含确认）
4. 默认开启 TTS 反馈，所有点击有语音确认
5. 无手势操作（不教划一划、长按拖拽、双指缩放）
6. 无广告、无运营弹窗、无红点
7. 启动后默认进入主屏，不出现引导页轮播

---

## 5. 数据模型

> 元数据进 CloudBase NoSQL；大文件（语音）进 COS。字段定义为唯一真源。

### 5.1 `family_user`（家属账号）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| phone | string | 是 | 手机号（唯一） |
| created_at | ISO datetime | 是 | |
| updated_at | ISO datetime | 是 | |

### 5.2 `elder_profile`（老人档案）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| name | string | 是 | 称呼 |
| device_token | string | 否 | 推送 token（唯一） |
| timezone | string | 是 | 设备本地时区（IANA 名，如 `Asia/Shanghai`） |
| created_at | ISO datetime | 是 | |
| updated_at | ISO datetime | 是 | |

### 5.3 `binding`（家属-老人绑定关系）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| family_id | UUID | 是 | 家属 user id（→ family_user.id） |
| elder_id | UUID | 是 | 老人 id（→ elder_profile.id） |
| role | enum | 是 | `primary` / `secondary` |
| created_at | ISO datetime | 是 | |

唯一索引：`(family_id, elder_id)`。
- `primary`：创建者，唯一；拥有全部配置权与解绑权（§11.6）
- `secondary`：被邀请的家属；只读 + 配置权，无解绑权

### 5.4 `reminder`（提醒）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| elder_id | UUID | 是 | 关联老人 |
| type | enum | 是 | `medication` / `appointment` |
| payload | object | 是 | 见下 |
| status | enum | 是 | `active` / `paused` |
| created_by | UUID | 是 | 家属 user id（family_user.id） |
| created_at | ISO datetime | 是 | |
| updated_at | ISO datetime | 是 | |

`payload` 内嵌：
- `medication`：`{ med_name: string, dosage: string, schedule: { time: 'HH:mm', repeat: 'daily' | 'weekdays' | 'custom', weekdays?: number[] }[], note?: string }`
- `appointment`：`{ hospital: string, department: string, datetime: ISO datetime, note?: string }`

索引建议：`(elder_id, type, status)`。

### 5.5 `reminder_ack`（提醒应答）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| reminder_id | UUID | 是 | 关联 reminder |
| elder_id | UUID | 是 | |
| action | enum | 是 | `taken` / `ack` / `missed` |
| at | ISO datetime | 是 | 应答时间 |
| offline_buffered | bool | 是 | true 表示离线缓存后联网补传 |

索引建议：`(reminder_id, at)`。

### 5.6 `diary_session`（Agent 访谈会话）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| elder_id | UUID | 是 | |
| status | enum | 是 | `active` / `finalized` / `abandoned` |
| turns | Turn[] | 是 | 见下内嵌结构 |
| created_at | ISO datetime | 是 | |
| updated_at | ISO datetime | 是 | |

`Turn` 内嵌结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| turn_no | int | 1-based |
| elder_text | string | ASR 转写后的文字 |
| elder_audio_cos_key | string | 老人本轮语音 COS key |
| assistant_text | string | Agent 本轮回复 |
| assistant_audio_cos_key | string | Agent 本轮回复音频 COS key |

索引建议：`(elder_id, status, created_at)`。

### 5.7 `diary_entry`（日志）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| elder_id | UUID | 是 | |
| session_id | UUID | 否 | 关联 diary_session.id |
| date | string (YYYY-MM-DD) | 是 | 按设备本地时区 |
| text | string | 是 | Agent 整理后的最终日记（≤ 100 字，见 §3.1.4.D） |
| summary | string | 是 | 一句话摘要（≤ 60 字） |
| audio_segments | AudioSegment[] | 是 | 每轮音频 |
| created_at | ISO datetime | 是 | |

`AudioSegment` 内嵌结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| cos_key | string | COS 上的语音文件 key |
| asr_text | string | 本段 ASR 文字 |
| duration_ms | int | 段时长（毫秒） |

索引建议：`(elder_id, date, created_at)`。

### 5.8 COS 对象 Key 约定

- 老人语音（永久）：`elder/{elder_id}/diary/{diary_id}/seg-{n}.m4a`
- Agent 回复语音（永久）：`elder/{elder_id}/diary/{diary_id}/reply-{n}.m4a`
- 临时上传（24h 生命周期，见 §11.13）：`tmp/{elder_id}/{uuid}.m4a`

---

## 6. API 契约

> 所有端点 JSON over HTTPS；鉴权：`Authorization: Bearer <jwt>`（见 §7.4）。
> 完整错误码见 §6.2；每个端点的鉴权角色与限流条款见 §6.1 表。

### 6.1 端点清单

| 端点 | 方法 | 鉴权角色 | 限流 | 说明 |
|------|------|----------|------|------|
| `/v1/auth/sms-code` | POST | 公开 | 60s 1 次 / 手机号 | 发送短信验证码 |
| `/v1/auth/login` | POST | 公开 | 5 次失败锁定 5 分钟 | 验证码登录，返回 JWT |
| `/v1/bind/elder` | POST | family | 10 次 / 小时 | 家属绑定老人 |
| `/v1/bind/elder/:id` | DELETE | family (primary) | — | 解绑（仅 primary） |
| `/v1/reminders` | GET | family / elder | — | 列出可访问的提醒 |
| `/v1/reminders` | POST | family | 30 次 / 小时 | 创建提醒 |
| `/v1/reminders/:id` | PATCH | family | 30 次 / 小时 | 编辑提醒 |
| `/v1/reminders/:id` | DELETE | family | 30 次 / 小时 | 删除提醒 |
| `/v1/reminders/:id/ack` | POST | elder | — | 老人应答 |
| `/v1/diary` | GET | family / elder | — | 按 elder + 日期范围列出 |
| `/v1/diary/:id` | GET | family / elder | — | 单条详情 + 预签 COS URL |
| `/v1/agent/asr` | POST | elder | — | 上行音频 URL → 文字 |
| `/v1/agent/tts` | POST | elder | — | 文字 → 音频 URL |
| `/v1/agent/diary-session` | POST | elder | 1 active session / minute / elder | 启动访谈会话 |
| `/v1/agent/diary-session/:id/turn` | POST | elder | 10s 1 turn / session | 老人语音 turn → Agent 回复 |
| `/v1/agent/diary-session/:id/finalize` | POST | elder | — | Agent 总结 + 落库 |

### 6.2 错误码全集

| HTTP | code | 含义 |
|------|------|------|
| 400 | `BAD_REQUEST` | 请求格式错误（非 schema 校验失败） |
| 401 | `UNAUTHORIZED` | JWT 无效 / 缺失 / 过期 |
| 403 | `FORBIDDEN` | 角色不符（如 elder 调 family-only、secondary 调解绑） |
| 404 | `NOT_FOUND` | 资源不存在（session / diary / reminder） |
| 409 | `CONFLICT` | 资源状态冲突（重复 finalize、重复绑定、并发写入） |
| 422 | `VALIDATION_ERROR` | schema 校验失败；响应 body 含 `field` 错误路径 |
| 429 | `RATE_LIMITED` | 触发限流；响应 body 含 `retry_after_seconds` |
| 500 | `INTERNAL_ERROR` | 未捕获的服务端异常 |
| 502 | `UPSTREAM_LLM` | LLM 不可用 |
| 503 | `UPSTREAM_ASR` / `UPSTREAM_TTS` | 语音上游不可用 |
| 504 | `UPSTREAM_TIMEOUT` | 30 秒超时 |

错误响应统一格式：
```json
{ "code": "VALIDATION_ERROR", "message": "elder_id required", "field": "body.elder_id" }
```

### 6.3 关键端点请求 / 响应

#### `POST /v1/agent/diary-session`
请求：`{ "elder_id": "uuid" }`
响应：`{ "session_id": "uuid", "created_at": "...", "max_turns": 8, "greeting_text": "...", "greeting_audio_url": "..." }`

#### `POST /v1/agent/diary-session/:id/turn`
请求：`{ "turn_no": 1, "elder_text": "...", "elder_audio_cos_key": "elder/{id}/tmp/{uuid}.m4a" }`
响应：`{ "turn_no": 1, "assistant_text": "...", "assistant_audio_url": "...", "should_finalize": false, "turns_left": 7 }`

#### `POST /v1/agent/diary-session/:id/finalize`
请求：`{ "turn_no": 4, "elder_text": "...", "elder_audio_cos_key": "..." }`
响应：`{ "diary_id": "uuid", "text": "今天和老张下了棋，赢了。", "summary": "和老张下棋，赢了" }`

#### `POST /v1/agent/asr`
请求：`{ "audio_url": "https://cos.../seg.m4a", "format": "m4a" }`
响应：`{ "text": "...", "confidence": 0.94 }`

#### `POST /v1/agent/tts`
请求：`{ "text": "今天过得怎么样？" }`
响应：`{ "audio_url": "https://cos.../tts-xxx.m4a", "expires_in": 600 }`

---

## 7. 安全与隐私

### 7.1 传输层
- 客户端到服务端强制 TLS 1.3；HTTP 明文请求一律 400
- 内部服务间（agent ↔ account ↔ reminder）走腾讯云内网

### 7.2 存储加密
- COS 对象存储强制启用 SSE-KMS（腾讯云托管密钥）
- CloudBase 静态加密（腾讯云默认开启）

### 7.3 端到端加密
- MVP **不启用**端到端加密（见 §11.7）；服务端可读以支持 Agent 处理与摘要生成

### 7.4 鉴权
- JWT（HS256），payload 含 `user_id`、`role`（`elder` / `family`）、`exp`、`iat`
- 服务端解码并校验签名与过期；`role` 与 `elder_id` 在各路由由业务中间件二次校验
- refresh token 机制见 §11.11

### 7.5 隐私原则
- **最小化**：Agent 不采集与日志整理无关的信息（不读取联系人、相册、定位等）
- **可解释**：日志查询接口返回的字段在 PRD §5 全部列清；客户端不得请求 PRD 之外的字段
- **客户端位置采集**：MVP 不主动采集 GPS（见 §11.12）；时区取设备系统时区

### 7.6 数据保留
- 日志永久保留（见 §11.4）
- 临时上传对象 24h 后由 COS 生命周期规则自动删除
- 家属解绑后，已绑定期间产生的日志仍归属老人；解绑不删除数据

### 7.7 审计
- 鉴权失败、敏感端点（bind、reminder 写、diary 读）写入 CLS 审计日志

---

## 8. 部署与基础设施

### 8.1 服务拆分（已锁定）
三个独立 HTTP 服务（仅 agent-service 使用 DeepSeek Harness SDK）：

| 服务 | 路径前缀 | 职责 |
|------|----------|------|
| agent-service | `/v1/agent/*` | AI 访谈写日志（ASR/TTS/LLM/Tools） |
| reminder-service | `/v1/reminders*` | 提醒 CRUD + 应答 |
| account-service | `/v1/auth/*` `/v1/bind/*` `/v1/diary*` | 鉴权、绑定、日志查询 |

### 8.2 基础设施
- **Agent Runtime**：腾讯云 SCF（Serverless）或 CVM 容器化部署 DeepSeek Harness SDK
- **元数据**：腾讯云 CloudBase（NoSQL 文档 DB，与 COS 同生态）
- **大文件**：腾讯云 COS——按 §5.8 Key 约定组织；上传用预签 URL
- **推送**：腾讯移动推送 TPush（默认，见 §11.2）
- **短信**：腾讯云 SMS（验证码）
- **监控**：腾讯云 CLS（日志）+ CAT（监控）

### 8.3 灰度
- MVP 灰度策略：单户手动开启（见 §11.14）
- 配置注入：环境变量；详见 AGENT.md §5.1

---

## 9. 架构决策（已锁定）

> 本节为已选定的架构决策。变更需走 PRD 修订流程。

| ID | 决策点 | 选定方案 | 备注 |
|----|--------|----------|------|
| D1 | Android 客户端栈 | **原生 Kotlin** | 性能好、UI 自由度高、直接调系统 API |
| D2 | 后端存储 | **CloudBase (NoSQL) + COS** | 元数据进 CloudBase，语音进 COS |
| D3 | 语音能力 | **ASR + TTS 两个独立 API** | 与 LLM 分工明确 |
| D4 | 双端形态 | **单 App 切换身份** | 一份安装包，`role` 字段区分 |
| D5 | Android 是否使用 DSH 客户端 SDK | **不使用，服务端独占** | DSH 仅跑在 agent-service 内；Android 不引入 DSH 客户端包 |

---

## 10. 系统质量要求

### 10.1 性能
- 老人端冷启动到主屏 ≤ 3 秒（中端 Android 机型）
- 老人端冷启动到首次完成任意任务 ≤ 30 秒
- Agent 单轮响应 ≤ 8 秒（95 分位）
- 日志列表查询（按 elder + 30 天范围）≤ 500ms（95 分位）

### 10.2 限流
- 通用默认：单 IP 60 req/min
- 关键端点专项：见 §6.1 表
- Agent 访谈：每 elder 1 active session / minute；每 session 1 turn / 10 秒

### 10.3 可用性
- Agent 服务月度可用性 ≥ 99.5%
- CloudBase 与 COS 由腾讯云 SLA 保障；服务端不引入单点

### 10.4 兼容性
- Android 最低版本：见 §11.10（默认 API 26 / Android 8.0）
- 仅适配 Android 手机；不承诺平板 / 折叠屏
- 仅中文普通话；方言容忍但不支持界面多语言

### 10.5 容量
- 单服务实例支撑 1000 日活老人 / 日访谈 ≤ 50 次
- COS 单老人每月音频存储 ≤ 100 MB（按平均 3 分钟/段、5 段/日 估算）

### 10.6 可观测性
- 所有 `/v1/*` 请求打印 CLS 结构化日志（含 user_id、role、latency_ms、code）
- 关键业务事件（session start / finalize、reminder ack、bind）写入 CLS 业务日志
- 告警：UPSTREAM_* 错误率 > 5% 持续 5 分钟触发

---

## 11. 待澄清 / 默认值

> 每项标注当前默认值；变更需更新本节。

| # | 项 | 当前默认值 | 状态 |
|---|----|-----------|------|
| 11.1 | 离线提醒应答缓存 | Android `Room` 临时表，联网后 POST `/v1/reminders/:id/ack` | 默认 |
| 11.2 | 通知通道（提醒 / 老人未应答推家属） | **TPush** | 默认 |
| 11.3 | 家属能否删日志 | **不能**（只读 + 配置权） | 已定 |
| 11.4 | 日志保留时长 | **永久** | 已定 |
| 11.5 | Agent 访谈轮数 | 默认 3–6 轮；硬上限 `MAX_TURNS=8` | 已定 |
| 11.6 | 多家属解绑 | 仅 `primary` 角色可解绑（`secondary` 无解绑权） | 默认 |
| 11.7 | 端到端加密 | **否**（服务端可读以支持 Agent 处理） | 默认 |
| 11.8 | 数据导出 | MVP **不做** | 已定 |
| 11.9 | 商业化 | **永久免费** | 已定 |
| 11.10 | Android 最低版本 | API 26（Android 8.0） | 默认 |
| 11.11 | JWT 续签机制 | 单 JWT 7 天有效；过期客户端重新走 `/v1/auth/sms-code` 登录 | 默认 |
| 11.12 | 客户端位置采集 | MVP 不采集 GPS；时区取系统时区 | 默认 |
| 11.13 | COS 临时对象生命周期 | 24h | 默认 |
| 11.14 | 灰度发布策略 | 单户手动开启 | 默认 |

---

## 12. 路线图（MVP 之外）

> 本次实现不做；列入后续版本。

- 紧急呼救 / 跌倒检测
- 医疗问答 / AI 健康助手
- 转人工客服
- iOS / 鸿蒙
- 微信小程序
- 老人端自主配置提醒
- 多家属协作（邀请、副本编辑）
- 多语言 / 方言（粤语 / 闽南语等 ASR 适配）
- 数据导出（PDF / ZIP 打包）
- 端到端加密（家庭密钥）

---

> **维护说明**：PRD 是产品需求的唯一真源；AGENT.md 中所有产品规则、数据字段、错误码、行为约束必须能在本 PRD 中找到对应条款。
