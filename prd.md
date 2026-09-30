# 老友（Android）PRD — v3.0

> 文档版本：v3.0
> 文档状态：定稿
> 当前已交付基线：v0.9.1 / App 0.9.1
> 当前目标发布版本：0.10.0
> 品牌：老友
> 平台：Android App（不再做微信小程序）
> 目标读者：产品 / 工程 / 测试
> 与 AGENT.md 的关系：PRD 是产品与需求的唯一真源；AGENT.md 仅承载服务端实施规范。

## 修订记录

| 版本 | 日期       | 作者  | 主要变更 |
|------|------------|-------|----------|
| v0.11.x | 2026-10-01 | Codex | **bugfix:保存后 LoadingState 全屏霸屏修复(v0.11.0 收尾)**：(1) `InterviewScreen` 删 `LoadingState()` 在 `PREPARING` / `SAVED` 阶段的引用,新增 `private fun SavingStatusRow(text)` —— 与 `OpeningStatusRow` 同款 `Size.PrimaryButtonHeight` 固定行高 Column;不让 `fillMaxSize()` 抢占 `TranscriptCard(weight 1f)` 空间。(2) `InterviewViewModel.saveDiary` 调整顺序 —— DB 写入 → `_state.update { stage = SAVED }` → `onDone()` → 后台 sibling `launch { runCatching { saveExportRepo.exportIfNeeded(...) } }`;外层加 `.invokeOnCompletion { t -> if (t != null && t !is CancellationException) { Log.e + _state.update { stage = REVIEW, topError = "保存失败，请重试" } } }` 兜底(不让 state 停在 SAVED)。(3) `SaveExportRepository.exportIfNeeded` 拆 `private suspend fun runExport(...)` + 套 `withTimeoutOrNull(EXPORT_TIMEOUT_MS)`;`EXPORT_TIMEOUT_MS = 3_000L`;超时返回 `ExportOutcome.Failed("EXPORT_TIMEOUT", ...)` 而不是抛 `TimeoutCancellationException`。(4) `onEnter` 拆 `private suspend fun onEnterInternal()` + 外层 try-catch —— 抛异常时强制翻 `stage = READY` + `topError = "初始化失败，请返回重试"`,不再让 PREPARING 阶段霸屏。**测试**:`InterviewScreenLayoutTest`(2)+ `InterviewSaveFlowStructuralTest`(3)+ `SaveExportRepositoryStructuralTest`(2)+ `SaveExportRepositoryTest` +1 timeout 行为测试,共 8 条新增/扩展,全绿。**不动**:§5 数据模型既有字段 / §3.1.4 A1–D3 / B既有行为约束 / §3.1.2 录音规格 / §4.2 最小 24sp 起步硬约束 / 既有 migration / 既有 prompt 文件 / AndroidManifest / §18 既有 lock。详细设计见 [`docs/v0.11.x-bugfix.md`](docs/v0.11.x-bugfix.md) §1–§7。|
| v0.11.0 | 2026-10-01 | Codex | **设定 App 0.11.0 目标**:(1) 设置保存方式 — 新增 `ElderSaveModeRepository`(独立 prefs file `elder_save_mode`)+ `SaveMode { LOCAL, CLOUD, BOTH }` enum;`ElderSettingsScreen` 新增 `SettingRowSaveMode` 卡 + `SaveModeDialog` 三选一 AlertDialog;默认 LOCAL;`strings.xml` +9 条 `settings_save_mode_*`。(2) 本地导出 — 新增 `SaveExportRepository`(依赖 `OssSyncActions` 接口)+ `OssSyncActions` 抽接口便于测试桩替换;`OssSyncRepository` 实现 `OssSyncActions`(`override syncDiary` / `override enqueueDebounced`);本地路径 = `Downloads/老友日记/`;文件名 `diary_yyyyMMdd_HHmmss_xxxx.{md|m4a}`(4 位 hex 随机后缀防多设备冲突,设备本地时区);`.md` schema 5 字段(`# {date}` / `> {summary}` / `{text}` / `时长：{N} 秒` / `录音：{audio_filename}`);`AndroidManifest.xml` 加 `WRITE_EXTERNAL_STORAGE` `maxSdkVersion="28"`。(3) LLM 回复改顶栏 Toast — `InterviewScreen` 删 `AssistantCard`(约 30 行)+ 新增 `LLMReplyToast(text: String?)`(AnimatedVisibility + fadeIn 200ms + fadeOut 500ms);`TranscriptCard` 改 `Modifier.weight(1f)` 撑满剩余空间;`InterviewUiState` 加 `llmReplyToastText: String?` / `farewellText: String?`;`InterviewViewModel.speakOrShow()` 加 `showLlmReplyToast: Boolean = true` 参数;`llmToastShowMs = 2_500L` 后自动 dismiss;相同 text 幂等不重复 show。(4) 老人语音提前结束 — `AgentSafety.elderEnd` set 4 词粤语 end 词(结束 / 够了 / 拜拜 / 不聊了)+ `isElderEnd()` 函数;`SafetyAgent.Verdict.ELDER_EXPLICIT_END` 新增(优先级高于 EXPLICIT_CLOSE);`SafetyAgent.ELDER_END_GOODBYE = "好的，今天先聊到这。"`;`ChatAgent.finalizeViaExplicitEnd()` 新方法走 `SaveAgent.saveDiary()` + 设 `AgentFinalDraft.farewellText`;`AgentModels.AgentFinalDraft.farewellText: String? = null` 字段新增;黄条教学:访谈屏 `READY` 阶段顶部黄色提示条(`BrandColor.NetYellow` token),首次关闭后写 `elder_save_mode.voice_end_hint_seen=true`,后续不再弹。**§18 例外**:本次新增 §3.1.4 B 关键词 4 词粤语 end 词,由 [`docs/v0.11.0.md`](docs/v0.11.0.md) §3.4 锁定;不动 A1–A3 / B1–B3 / C / D 既有约束。**不动**:§5 数据模型既有字段 / §3.1.4 A/B/D 硬约束 / §3.1.2 录音规格 / 既有 migration / 既有 prompt 文件 / 既有 Room 表。详见 [`docs/v0.11.0.md`](docs/v0.11.0.md) §1–§8。|
| v0.10.0 | 2026-09-27 | Codex | **设定 App 0.10.0 目标**：(1) 文档瘦身 — prd.md 加 `§12.5 版本文档索引`,11 份 `docs/{version}.md` 命名规范,旧 `docs/prd-v2.1-*` / `docs/0.9.0-*` 改名 + `superseded` 注保留只读;详细设计见 [`docs/v0.10.0.md`](docs/v0.10.0.md) §1。(2) TTS 收尾 — `PcmSink.MAX_DRAIN_MS` 5_000L → 1_500L;AndroidPcmSinkDrainTest 新增第 5 条 `drainBuffer default cap is 1500ms` 锁定数值;§18 反向条款不变 (`drain()/release()` 必须先 `drainBuffer()`)。详见 [`docs/v0.10.0.md`](docs/v0.10.0.md) §2。(3) 删 v0.7.0 一次性升级 Toast — `ServiceLocator.detectUpgradeToast()` / `_upgradeToast` / `upgradeToast` getter / `consumeUpgradeToast()` 全删;`ElderHomeViewModel` 删 `upgradeToast` 字段 + 监听 + `dismissUpgradeToast()`;`ElderHomeScreen` 删 `uiState.upgradeToast?.let` + 死 import;`strings.xml` 删 `home_upgrade_0_7_0`;全新安装不再弹「0.7.0 升级」Toast。详见 [`docs/v0.10.0.md`](docs/v0.10.0.md) §3。(4) 第一句粤语 + Agent 多说 — `SafetyAgent.greetingFallback()` 改粤语(早晨,今日想去边度? / 中午,食咗饭未呀? / 今晚,今日过得点呀?);新增 `chat_v2.txt`(OPEN 段首句必含 ≥3 个粤语词;鼓励引导老人讲今天;RESPOND 段允许铺垫 1 句,总和 ≤25 字仍受 A2);新增 `system_v4.txt`;`AgentPrompts.CHAT_PROMPT` v1→v2 / `VERSION` v3→v4 / `SYSTEM_PROMPT_V1` 占位指向 v4;`SafetyAgentTest` +3 粤语断言;`ChatAgentOpenTest` 期望更新。详见 [`docs/v0.10.0.md`](docs/v0.10.0.md) §4。(5) 不限轮数 — `MAX_TURNS=8` 硬上限删除;`ChatAgent` / `InterviewAgent` 删 `maxTurns` 字段 + 早退分支 + `shouldFinalize` 硬截断分支;新增 `SOFT_TURN_HINT=20`(仅供 UI 进度提示,不参与最终化判定);`InterviewUiState.maxTurns` 改 `SOFT_TURN_HINT`;`canRevise` 改由 `session.status == SAVED` 判定;`InterviewViewModel.willFinalize` / `revise()` 删 `MAX_TURNS` 守卫;`InterviewAgentTest` 改写 `hard turn cap never creates a ninth turn` → `no hard turn cap beyond 8 rounds runs main LLM loop`(12 轮后仍走主循环)。详见 [`docs/v0.10.0.md`](docs/v0.10.0.md) §5。(6) 阿里云 OSS 同步(本地 + 云双保存) — 新增 `OssConfigEntity` 单行表 + `OssConfigDao` + 增量 5 列 `oss_object_key` / `oss_sync_status` / `oss_synced_at` / `oss_last_error` / `oss_attempts`(§5.10 锁定列表 → 仅 ALTER 追加)+ Migration 6→7;`OssKeyCipher`(独立 prefs file `elder_oss_keys`)+ `OssSyncClient` 接口 + `AliyunOssSyncClient`(反射 lazy init aliyun-sdk-oss,prod SDK 缺失时 `OssSyncException`)+ `OssSyncRepository`(syncDiary / retryPending / enqueueDebounced 60s 节流)+ `OssSyncWorker`(CoroutineWorker + `UNMETERED` Constraints + 指数 backoff)+ `OssConfigScreen` + `OssConfigViewModel`;ElderSettings 新增「同步到云」入口卡;`ServiceLocator` 注入 ossKeyCipher / ossSyncClient / ossSyncRepo;`Routes` 新增 `ElderOssConfig`;`strings.xml` +20 `oss_*` 字符串;`app/build.gradle.kts` + `androidx.work:work-runtime-ktx:2.9.1`(AGENTS.md §A.16.3 记录为 0.10.0 唯一新上游 SDK)。详见 [`docs/v0.10.0.md`](docs/v0.10.0.md) §6。(7) 日志详情 DiaryDetailScreen(浅色 / 暗色 token 联动) — 新增 `data class DiaryColorScheme` + `object DiaryColor { Light / Dark }` + `LocalIsDarkMode` compositionLocalOf;`ElderTheme` 加 `forceDarkMode` 参数;新增 `DiaryDetailScreen` + `DiaryDetailComponents` + `DiaryDetailViewModel`;新增 `ElderOssConfig` 同级 `ElderDiaryDetail("elder/diary/detail/{diaryId}")` 路由;`ElderDiaryRecentScreen` 列表项点击进详情;`DiaryRepository.findById(id)` 暴露给 VM;4 处必须翻(次要色 / 图标描边 / 遮罩终点 / 阴影);间距 / 字号 / 圆角 / 组件树全部不动;详见 [`docs/v0.10.0.md`](docs/v0.10.0.md) §7。**不动**:§5 数据模型既有字段定义 / §3.1.4 A1/A2/B/D 硬约束 / §3.1.2 录音规格 / §4.2 最小 24sp 起步硬约束 / 既有 migration / 既有 prompt 文件(`system_v1/v2/v3.txt` + `save_v1/v2/v3.txt` + `chat_v1.txt` + `memory_v1.txt` + `safety_v1.txt` 全部保留只读,新增 `chat_v2.txt` + `system_v4.txt`)/ AGENTS.md §18 反向条款。 |
| v1.0 | 2026-08-23 | Codex | 推倒重来：平台改为 Android；功能缩到 MVP 三功能；引入 DeepSeek Harness + 腾讯云 Agent + COS |
| v2.0 | 2026-08-24 | Codex | PRD/AGENT.md 分工重排：所有需求迁移到 PRD；AGENT.md 只留实施规范；新增 §3.1.4 Agent 行为约束、§7 安全与隐私、§10 系统质量要求；D1–D4 锁定，新增 D5 |
| v2.1 | 2026-08-27 | Codex | UI 设计 token 系统化：§4 → §4.1-§4.10（色彩 / 字号 / 间距 / 圆角 / 动效 / 声音 / 权限 / 空态 / Toast / 黄条 / 加载）；§3.1 老人端 4 → 8 子节（新增 §3.1.5 主屏 / §3.1.6 访谈总结 / §3.1.7 今日记录 / §3.1.8 设置）；§3.2 家属端 3 → 9 子节重排（主屏 / Tab / 表单 / 日志 / 绑定全部展开）；§6.1 端点 16 → 20（新增 bind/confirm + bind/pending + diary/flush-pending + bind/elder 修订）；§6.2 错误码新增 REMINDER_TIME_PAST / BIND_CODE_EXPIRED / BIND_ATTEMPT_NOT_FOUND；§10.4 兼容 TTS 粤语男声 + ASR 粤语主识别；§11 待澄清 14 → 31 项（11.15-§I.9 全闭环）；§J3 联动 §I-4 剂量枚举 [PILL\|HALF\|SPOON\|CUSTOM] 收紧为 [PILL\|HALF\|SPOON] 走备注 |
| v2.1.1 | 2026-08-27 | Codex | 字段表补齐（为开工扫除文档缺口）：§5.4 reminder 加 `channel_priority`；§5.4 medication payload 收紧为 `dosage ∈ [pill\|half\|spoon]` + `note?`；§5.4 appointment payload 加 `advance_remind_min ∈ [30\|60\|120]` 默认 60 + `repeat = none`；§5 新增 `bind_attempt` 数据模型（§3.2.7-§3.2.8 + §A.5 引用落地）；§3.1.7 图标熄灭时区规则明确为"设备本地时区零点"；§11 默认项 11.1 / 11.2 / 11.6 / 11.7 / 11.10 / 11.11 / 11.12 / 11.13 / 11.14 收口为已定 |
| v2.1.2 | 2026-09-05 | Codex | MVP 免 SMS：§5.1 `family_user` 新增 `device_token` 字段（phone 与 device_token 二选一非空）；§6.1 移除 `/v1/auth/sms-code` 与 `/v1/auth/login`，新增 `/v1/auth/anonymous-device`；§6.2 错误码新增 `DEVICE_TOKEN_REQUIRED`；§11.11 JWT 续签改为"过期走 `/v1/auth/anonymous-device` 续签"；§10.4 SMS 标记 MVP-DEFER，v2.x 启用；§3.2.x 文案"手机号绑定"同步去掉，详见 `docs/prd-v2.1.2-mvp-auth.md` |
| v3.0 | 2026-09-05 | Codex | **MVP 范围重定**：老人端完全离线可跑——服务端 / 家属端整段推迟到 v2.x；MVP 仅保留 §3.1.2 单次录音 → ASR → 本地日记 + 新增 §3.1.9 ASR API 配置页；§3.1.1 / §3.1.3 / §3.1.4 / §3.1.6 / §3.2 / §5（服务端集合）/ §6 全部标记 MVP-DEFER；§5 改为以本地 Room 表为真源；§0 / §1 / §10 / §11 同步精简 |
| v3.0.1 | 2026-09-06 | Codex | **ASR 切到阿里百炼 Qwen-Audio-Realtime Android SDK**（非 v3.0 自建 m4a WS duplex）：模型 `qwen-audio-3.0-realtime-flash`，AAR 落 `app/libs/`，SDK adapter 模式封装在 `app/data/asr/`；§A.8 整体重写；§3.1.2 录音改 `AudioRecord` PCM 16 kHz / 单声道 / 16-bit 流式 `updateAudio()`，进入录音屏即开始、底部"正在录音"+"停止录音"两按钮、正文实时显示返回文字、60 s 硬限；§3.1.5 主屏按钮文案改为"点击开始写日志"；§3.1.7 移除"▶ 重播自己语音"（SDK 流式消费，无 audio 文件落地）；§3.1.9 ASR 配置页：Provider 只读（百炼 / qwen-audio-3.0-realtime-flash / workspaceId），单字段 API Key，顶部 ✓ 升级为真 `TextButton` 调 `vm.save()`，删除正文"保存"按钮，"测试一下"改走 SDK 流式 + 5 s 合成 PCM；§11.28 11.28.1 / 11.28.2 / 11.28.3 / 11.28.5 同步更新；§9 加 D7（`nls_config.modalities = ["text"]`，MVP 关闭 SDK 音频输出） |
| v0.5.0 | 2026-09-14 | Codex | **设定 App 0.5.0 目标**：从 v3.0.1 的单次录音写日记升级为本地 Agent 多轮访谈；ASR 使用阿里千问 Realtime、LLM 使用 MiniMax M3、TTS 使用 `qwen3-tts-flash-realtime` + 音色 `Kiki`；0.5.0 不做 Gateway，沿用用户自填 Key 直连；Agent 状态机、会话状态、安全关键词和本地工具运行在 Android；0.5.0 不承诺离线 Agent；同步新增 D8-D12 架构决策并启用 §3.1.4 / §3.1.6 |
| v0.7.0 | 2026-09-18 | Codex | **设定 App 0.7.0 目标**：老人端 Settings「AI 服务」从单 Provider（千问）升级为 ASR/TTS 双 Provider 可切换
| v0.8.0 | 2026-09-19 | Codex | **AI 服务 LLM Provider 可切换**：Settings「AI 服务」新增「大语言模型」入口卡 + LLM 子页，Provider 选项：`千问 qwen-plus`（OpenAI 兼容 dashscope）/ `MiniMax M3`（默认，回滚路径）/ `DeepSeek deepseek-chat`（api.deepseek.com）；新增 `QwenLlmClient` / `DeepSeekLlmClient`（OpenAI 兼容协议），`MiniMaxClient` 不动；`asr_config` Migration 5→6 增加 `llm_provider` / `llm_endpoint` / `llm_model` / `qwen_llm_api_key_enc` / `qwen_llm_last_test_result` / `deepseek_llm_api_key_enc` / `deepseek_llm_last_test_result` 列；§3.1.9 / §5.11 / §A.11.1 / AGENTS.md §A.15 同步修订；不动服务端 / 家属端 / §3.1.4 行为约束 / Hermes 记忆层；按 §16 / §17 与客户端代码同 PR 合并提交（产品评审通过为前置） |：ASR 支持 `bailian`（千问百炼）/`minimax_realtime`（MiniMax Realtime），TTS 支持 `qwen`（千问 `Kiki`）/`minimax`（MiniMax `Cantonese_KindWoman`）；**默认 Provider = MiniMax ASR + MiniMax TTS**，千问作为可选回滚路径保留；MiniMax LLM 不动；不动服务端 / 家属端 / 记忆层 / §3.1.4 行为约束；AGENTS.md 新增 §A.12（MiniMax Realtime ASR）+ §A.13（MiniMax T2A WebSocket）+ §18 「0.7.0 例外」；§3.1.9 / §5.11 / §6.4 / §11.15 / §11.28.2 / §A.11.1 同步修订；`asr_config` 走 Migration 4→5 DROP+CREATE 强制重输 4 份 Key（`api_key_enc` 千问共用 / `minimax_api_key_enc` MiniMax LLM+ASR 共用 / `tts_minimax_api_key_enc` MiniMax TTS 独立）。 |
| v0.6.0 | 2026-09-17 | Codex | **设定 App 0.6.0 目标**：在 0.5.0 基础上让访谈 Agent 学习窦文涛式访谈手法（先共情命名情绪、再用细节阶梯追问）并加 Hermes 风格纯 Room 记忆层（`elder_facts` 表 + `remember_fact` / `search_memory` 工具 + 仅 `save_diary` 后 background learning + 三块 system prompt 注入 `<elder-facts>` / `<recent-summaries>` / `<memory-policy>`）；AGENTS.md §A.11 范围内，客户端 Kotlin 实现；`prd.md §3.1.4` 修订：A6 共情前置 + A4 澄清 + C1 修订；新增 §3.1.4.E 访谈技巧 E1–E6、§3.1.4.F 记忆层 F1–F7；纯文本边界：不引 embedding / 不引 FTS / 不引 MD 文件 / 不引新 SDK；对照脚本评分验收；走修订记录 + 产品评审门后合入 |
| v0.9.0 | 2026-09-22 | Codex | **设定 App 0.9.0 目标**：在 0.8.x 基础上做三件事：(1) 老人端 10 屏文字清晰度升级 —— 最小可视字号从 18/20sp 收紧到 22sp；`TitleLargeSp` 38→40 合并 `BodyHugeSp`；转写卡片字号 32→40sp 行高 48→56sp；访谈屏 ack 28sp 次级 + probe 38sp 粗体主色；进度行改 96dp 大圆环；主屏日期 24sp 次级→28sp 主色；时间轴列表行 72→96dp + 相对时间格式；(2) Agent 拆分 1→3 —— 把 `InterviewAgent` 拆为 `ChatAgent`(主循环,3 工具) + `SafetyAgent`(纯本地 object,emergency/money/medical 关键词) + `MemoryAgent`(background learning,Room 走);新增 `SaveAgent` 兼容 `summarize()` 老路径；prompt 文件新增 `chat_v1.txt`(从 `system_v3.txt` 抽,瘦身 30%) + `memory_v1.txt` + `safety_v1.txt`；旧 `system_v1/v2/v3.txt` + `save_v1/v2/v3.txt` 全部保留只读;`LlmCredentials` / `LlmClient` / `LlmClientFactory` 接口锁定不动;(3) ChatAgent 主动开问 —— 新增 `ChatAgent.open()` 入口 + `InterviewStage.OPENING` 阶段;进入访谈屏时调 LLM 生成回扣式问候(注入 `<recent-summaries>` + `<elder-facts>`,E6 跨会话回扣)+ TTS 必播第一句(Cantonese_KindWoman 粤语女声);LLM 失败走 `SafetyAgent.GREETING_FALLBACK` 静态兜底不调 TTS;第一句不计 turn 计数,8 轮上限不缩短。新增 strings.xml 多语言 fallback(`values-en` 英文 + `values-zh-rHK` 粤语口语,**仅**覆盖 v0.9.0 新增 5 个 key;UI 文字保留普通话,粤语仅作用于 LLM 输出)。不动 §5 数据模型字段;不动 §3.1.4 A1–D3 行为约束;不动 §4.2 最小 24sp 起步硬约束(0.9.0 在原表追加列);不动 §3.1.2 录音规格(60s / 16kHz / Mono);不动 Room schema + 既有 migration;不动 §18 锁定列表。详细设计见 `docs/0.9.0-goals.md` + `docs/0.9.0-chat-agent.md` + `docs/0.9.0-chat-agent-opening.md`。 |
| v0.9.1 | 2026-09-23 | Codex | **访谈屏 LLM 回复统一单段 28sp 次级**：§3.1.4.A 删除 A6 共情前置拆段（≤10 字 ack + ≤25 字 probe + 总长 ≤35 字）；A1/A2/B/C/D/E/F 硬规则保留；E1 引用 A6 处同步删除「(A6 已授权)」并改为可选前置（软指令）；§12.4.1 访谈屏字号规范修订：「ack 28sp 次级 + probe 38sp 粗体主色 + `·` 分隔」→「LLM 回复 28sp 次级单段（不分双段、不分隔符）」；OPENING 阶段（v0.9.0 新增的 ChatAgent.open()）走同一规格；§12.4.2 ChatAgent 工具集保留 3 个（不动）；客户端 Kotlin 同步重构 `ChatAgent.splitAckProbe()` + `MAX_ACK_CHARS/MAX_PROBE_CHARS/MAX_REPLY_TOTAL_CHARS` 删除；`AgentReply.ackText/probeText` 字段合并为 `assistantText` 单字段；`AssistantCard` 渲染改单 Text（28sp + TextSecondary + Normal）；prompts（chat_v1.txt / system_v3.txt）删 A6 指引；测试 `InterviewAgentTest_v2.kt` 中 3 个 ack/probe 拆段测试改为「单段 ≤25 字」断言；docs/0.9.0-chat-agent.md §2.1 职责表 + §2.2 类签名常量 + §2.4 安全短路 + §3.2 常量迁移表同步修订；**不动**：§5 数据模型字段；§3.1.4 A1/A2/A3/A4/A5/B/C/D/E2-E6/F；§3.1.2 录音规格；§4.2 最小 24sp 起步硬约束；既有 migration；既有 prompt 文件（system_v1/v2/v3.txt + save_v1/v2/v3.txt + chat_v1.txt + memory_v1.txt + safety_v1.txt 全部保留只读）。按 AGENTS.md §18 走 prd.md 修订记录 + 产品评审门后合入（用户决策 = 评审通过），按 §16 与客户端代码同 PR 合并提交。设计对应 docs/0.9.0-chat-agent.md v0.9.1 修订段。 |


---

## 0. 一页纸

### 0.7 0.7.0 版本目标（当前）

> **目标**：在 0.6.0 基础上把老人端 Settings「AI 服务」从单 Provider 升级为 ASR/TTS 双 Provider 可切换，默认切换到 MiniMax ASR + MiniMax TTS（音色 `Cantonese_KindWoman`）；LLM 仍是 MiniMax M3；不动服务端 / 家属端 / §3.1.4 行为约束 / Hermes 记忆层。

**目标链路**

```text
老人说话
  -> Provider-asr(MiniMax Realtime / 百炼) ASR（在线）   <-- v0.7.0 切换
  -> Android 本地 Agent 状态机 + 关键词安全规则
  -> loadRecentSummaries(3d) + loadElderFacts(top 50)        <-- v0.6.0 注入
  -> render system_v2 注入 <elder-facts> / <recent-summaries> / <memory-policy>  <-- v0.6.0
  -> MiniMax M3 LLM（在线，可调 remember_fact / search_memory）
  -> Provider-tts(MiniMax `Cantonese_KindWoman` / 千问 `Kiki`) TTS（在线）   <-- v0.7.0 切换
  -> Android 播放
  -> 本地收尾 → save_diary 后追加 background_learning 抽取 facts <-- v0.6.0
  -> elder_facts Room 表持久化
```

**0.7.0 功能目标**

- 启用 §3.1.9 ASR Provider 切换：百炼 / MiniMax Realtime 二选一；MiniMax Realtime 为默认。
- 启用 §3.1.9 TTS Provider 切换：千问 `Kiki` / MiniMax `Cantonese_KindWoman` 二选一；MiniMax 为默认。
- LLM 在 0.7.0 仍不切换；**v0.8.0 修订为可切换**——「AI 服务」顶层新增「大语言模型」入口卡，跳转至 LLM 子页。Provider 选项：`千问 qwen-plus`（OpenAI 兼容端点 dashscope）/ `MiniMax M3`（默认，回滚路径）/ `DeepSeek deepseek-chat`（api.deepseek.com）。每个 Provider 独立 Key：`minimax_api_key_enc`（MiniMax LLM，沿用 v0.7.0）/ `qwen_llm_api_key_enc`（千问 LLM，新增）/ `deepseek_llm_api_key_enc`（DeepSeek LLM，新增）；原有千问 ASR/TTS 共用 `api_key_enc` **不**承载 LLM Key。Client 通过 `LlmClientFactory` 按 `llm_provider` 路由到对应 OpenAI 兼容客户端，工具调用 / system prompt / 三次重试策略沿用 §A.11.4 / §A.11.2，沿用 v0.5.0/0.6.0 用户自填 Key 直连。
- 启用 AGENTS.md §A.12（MiniMax Realtime ASR）+ §A.13（MiniMax T2A WebSocket）+ §18 「0.7.0 例外」。
- `asr_config` Room 表走 Migration 4→5 DROP+CREATE，强制老人重输 4 份 Key（千问共用 / MiniMax LLM+ASR 共用 / MiniMax TTS 独立）。
- 主屏一次性 Toast 提示「0.7.0 升级：请到 AI 服务 重新配置 ASR 与 TTS」。

**0.7.0 性能目标（沿用 v0.6.0）**

- 正常 Wi-Fi / 5G 网络下，停止录音到 ASR final P95 ≤ 2 秒（百炼 / MiniMax Realtime 同限）。
- 正常 Wi-Fi / 5G 网络下，TTS 首段音频 P95 ≤ 2 秒（千问 / MiniMax 同限）。
- 切换 Provider 不引入额外延迟（Provider 选择只在配置阶段生效；录音 / 播放走对应 client）。

### 0.9 0.9.0 版本目标

> **目标**：在 0.8.x 基础上做三件事——**老人端 10 屏文字清晰度升级**（最小可视字号 22sp；`TitleLargeSp = BodyHugeSp = 40`；转写 40sp / 行高 56sp；进度圆环；相对时间）+ **Agent 拆分 1 → 3**（`ChatAgent + SafetyAgent + MemoryAgent`，新增 `SaveAgent` 兼容老路径；prompt `chat_v1.txt` 瘦身 30%；工具集 6 → 3）+ **ChatAgent 主动开问**（`open()` 入口 + `OPENING` 阶段 + TTS 必播第一句 + 回扣 `recent_summaries` / `elder_facts`，LLM 失败走静态兜底）。

**目标链路**

```text
老人进入访谈屏 (主屏 → §3.1.5)
  -> 0.9.0 新增:ChatAgent.open() 调 LLM 生成回扣式问候(注入 <recent-summaries> + <elder-facts>)
     -> 兜底链:SafetyAgent.GREETING_FALLBACK(timeOfDay) 静态中文(MORNING/NOON/EVENING)
  -> TTS 必播第一句(Cantonese_KindWoman §A.13 / 千问 Kiki §A.1)
  -> stage = OPENING(展示 "让我先打个招呼…" + 进度条)
  -> stage = READY(老人可按"点击说话")
  -> 老人说话 -> ASR(Provider-asr / MiniMax Realtime §A.12 / 百炼 §A.8)
  -> ChatAgent.respond(轮 1..N,工具集 6→3: ask_clarify / mark_dimension_covered / MOVE_ON)
     -> 命中 emergency/money/medical -> SafetyAgent.shortcut() 本地判定(**不调 LLM**)
     -> 命中 save_diary -> 收尾委托 SaveAgent.saveDiary(走 save_v3.txt,D1 text≤100 / D2 summary≤60)
     -> 落库后 -> MemoryAgent.backgroundLearning(走 memory_v1.txt,抽 facts_to_remember)
     -> elder_facts Room 表持久化(Kotlin 校验 type/confidence/content)
  -> TTS 播 ack+probe(Cantonese_KindWoman)
  -> 收尾 -> §3.1.6 访谈总结屏
```

**0.9.0 功能目标**

1. **文字清晰度升级**(不动 §4.2 最小 24sp 起步硬约束,只在原表追加 v0.9.0 列;新增 `BodyHugeSp = TitleLargeSp = 40` 合并档;`BodySmallSp` 18→22、`caption()` 20→22;详见 §4.2):
   - 老人端 10 屏 + 4 系统组件字号 / 行高 / 字重统一提升
   - 转写卡片 32→40sp / 行高 48→56sp
   - 访谈屏 ack 28sp 次级 + probe 38sp 粗体主色 + `·` 分隔;转写 38sp 粗体
   - 进度行 "第 N 轮 / 共 8 轮" → 96dp 大圆环(iOS Health 风格)
   - 主屏日期 24sp 次级 → 28sp 主色;问候语支持农历日期
   - 时间轴列表行 72→96dp;时间改"今天 上午 9:30"相对时间
   - 4 系统组件:ElderToast 24→28sp + 震动 + TTS 同步朗读;NetworkYellowBar 24→28sp + 右上 X 关闭;ElderEmptyState 24→28sp + 64dp 灰色插画占位 + 朗读按钮 96dp;LoadingState 24→28sp + 进度条 8→12dp
   - WCAG AA 对比:既有 `Brand500 / TextPrimary / TextSecondary / Error500` 全部 ≥ 4.5:1 通过;新增 `TextTertiary #999999` 仅装饰
2. **Agent 拆分 1 → 3**(不动 §3.1.4 A1–D3 / 不动 §5 Room schema / 不动 `LlmCredentials` 等接口):
   - `InterviewAgent.kt`(624 行) → `ChatAgent(主循环 + 工具集 6→3)+ SafetyAgent(纯本地 object)+ MemoryAgent(background learning + Room)+ SaveAgent(summarize 兼容)`
   - 新 prompt:`chat_v1.txt`(从 `system_v3.txt` 抽,≤70 行 / ≤2500 token,瘦 30%)+ `memory_v1.txt`(≤30 行)+ `safety_v1.txt`(≤10 行空模板)
   - 旧 `system_v1/v2/v3.txt` + `save_v1/v2/v3.txt` 全部保留只读(AGENTS.md §11 版本化要求);运行版本由 `PROMPT_VERSION` 环境变量选
   - ChatAgent 暴露给 LLM 的工具:`ask_clarify / mark_dimension_covered / MOVE_ON`;`save_diary` 由 ViewModel 显式调 SaveAgent;`remember_fact / search_memory` 由 MemoryAgent 后台
3. **ChatAgent 主动开问**(新增 `ChatAgent.open()` 入口 + `InterviewStage.OPENING`):
   - 进入访谈屏时调 LLM 生成第一句问候(≤25 字,不分 ack/probe)
   - 注入 `<recent-summaries>` + `<elder-facts>` 做 E6 跨会话回扣
   - 时段敏感:`早上好 / 中午好 / 晚上好` 按本地时间切换(MORNING 5-11 / NOON 12-17 / EVENING 18-4)
   - 粤语口语(对齐 §A.13 Cantonese_KindWoman)
   - TTS 必播第一句(走 §A.13 / §A.1)
   - LLM 失败走 `SafetyAgent.GREETING_FALLBACK(timeOfDay)` 静态兜底(不调 LLM / 不调 TTS)
   - **第一句不计 turn 计数**,8 轮上限不缩短
4. **多语言 fallback**(新增 `values-en/strings.xml` 英文 + `values-zh-rHK/strings.xml` 粤语口语):
   - UI 文字保留普通话(主 strings.xml)
   - 粤语口语**仅作用于 LLM 输出 `assistant_text`**(已在 system_v3.txt A3 + chat_v1.txt §OPEN 段落)
   - 文案字符数约束:UI 短文案 ≤ 12 字,长文案 ≤ 30 字;超过则拆分或缩写

**0.9.0 性能目标(沿用 0.8.x)**

- 进入访谈屏到 ChatAgent.open() 返回 + TTS 播完 P95 ≤ 3 秒(LLM 单次调用 + TTS 首段 ~1.5s)
- OPENING 阶段 UI 不阻塞(显示进度条 + "让我先打个招呼…")
- LLM 失败兜底路径 < 200ms(本地字符串查表,不调网络)
- emergency 路径省 1 次 LLM 调用(SafetyAgent 本地判定,原 v0.6.0 emergency 也跑 finalize 调 LLM)
- 切换 Provider / 模型不引入额外延迟(沿用 0.7.0 / 0.8.0 配置阶段生效)

**0.9.0 不动契约(AGENTS.md §18 红线)**

- **不**修改 prd.md §5 数据模型既有字段定义(只允许新增字段)
- **不**修改 §3.1.4 Agent 行为约束 A1–D3(硬规则)
- **不**删除 `services/*/migrations/` 下既有迁移脚本(审计轨迹)
- **不**改既有字段类型 / 默认值 / 必填
- **不**同时改 prd.md 既有章节 + 多服务代码(§16 一事一 commit)
- **不**删 `system_v1/v2/v3.txt` + `save_v1/v2/v3.txt`(只读,版本化要求)
- **不**改 `LlmCredentials` / `LlmClient` / `LlmClientFactory` 接口(§A.15 锁定)
- **不**改 Room schema / 既有 migration
- **不**为客户端引入新的上游 SDK(0.5.0 / 0.7.0 例外条款沿用,直连 MiniMax / 千问 ASR/TTS 与 MiniMax M3 LLM 保持)
- **不**改 §3.1.2 录音规格(60s 硬限 / 16kHz / 单声道 / 64kbps)
- **不**缩短 8 轮访谈上限(第一句不计 turn)
- §4.2 最小 24sp 起步硬约束保留(0.9.0 只在原表追加列,不改约束语义)

## 1. 背景与目标

### 1.1 背景

国内 60+ 长者规模大，独居/空巢比例高，但**对手机 App 操作普遍存在抵触**——文字太小、流程太多、广告太多、隐私顾虑重。市面上的"长辈版"App 多停留在皮肤层（换大图标、放大字号），缺少**真正降低操作负担的语音优先交互**，也没有围绕老人真实高频场景（吃药、看病、记日常）打通子女端配置能力。

老友从这两个痛点切入：
- **老人侧**：操作只有"按住说话"和"按一下确认"，不出现任何二级菜单
- **子女侧**：用普通表单快速给老人配置任务，自己能看到老人近况

### 1.2 目标

- MVP 在 2 周内可演示（v3.0 收窄后，工期按老人端单机版重排；服务端 / 家属端不占进度）
- 老人端冷启动到首次完成任意任务 ≤ 30 秒
- 老人端零文字输入也能完成全部 MVP 任务
- **老人端完全离线可跑**：录音 → 大模型 ASR → 本地日记 三步在老人端闭环完成，不依赖自建服务端 / 家属端 / 推送通道（详见 §3.1.2 / §3.1.9）
- **MVP 仅做老人侧可演示的最小集**：1 项核心功能（写日志）+ 1 项配置（ASR 凭证），其余全部推迟到 v2.x（详见 §12）

### 1.3 非目标（MVP 不做）

- iOS / 鸿蒙 / iPad 适配（仅 Android 手机）
- 微信小程序 / Web / 桌面
- **家属端 / 扫码绑定**（v2.x 启用，详见 §3.2）
- **服药提醒 / 就医提醒全屏卡**（v2.x 启用，详见 §3.1.1 / §3.1.3）
- **Agent 多轮访谈 / TTS 播报 / 访谈总结屏**（v2.x 启用，详见 §3.1.4 / §3.1.6）
- **服务端 / CloudBase / COS / TPush / SMS / KMS**（v2.x 启用，详见 §6 / §8）
- 多老人账号家庭组
- 紧急呼救、跌倒检测、医疗问答、转人工坐席
- **跨端同步 / 家属查看日志**（无服务端，无家属端，故无法实现）
- **云端备份 / 数据导出 / 换机恢复**（本机 Room；卸载即丢失，由 §3.1.8 第 6 项二次提示兜底）
- 国际化（仅中文普通话；方言与多语言列入后续）
- 完全离线（录音需要网，但**不依赖服务端**——老人自配的 ASR endpoint 可为任意公网 LLM ASR 服务）

---

## 2. 用户与角色

| 角色 | 客户端身份 | 主要动作 |
|------|------------|----------|
| 老人 | 安装时选「我是老人」或被家属扫码绑定 | 收到提醒、说日志、查看今日 |
| 家属 | 安装时选「我是家属」 | 配置提醒、查看日志、添加老人 |

MVP 不做客服、医生、群组等其他角色。

---

## 3. MVP 功能详述

> **v3.0 范围地图**：
> - **MVP 在做**：§3.1.2 AI 写日志（单次录音 → ASR → 本地日记）、§3.1.5 主屏、§3.1.7 今日记录图标、§3.1.8 设置、§3.1.9 ASR API 配置页（v3.0 新增）
> - **MVP 推迟（v2.x 重做）**：§3.1.1 服药提醒全屏卡、§3.1.3 就医提醒全屏卡、§3.1.4 Agent 行为约束、§3.1.6 访谈总结屏、§3.2 家属端整章、§3.2.9 未绑定前写日记暂存流程、§3.3 跨端流程中依赖服务端的部分
>
> 所有推迟章节**保留正文**（不删除）——v2.x 重新启用时按现描述实现；标题前一律加 `[MVP-DEFER → v2.x]` 标记。

### 3.1 老人端

#### 3.1.1 服药提醒全屏卡（v2.1 升级；**v3.0 MVP-DEFER → v2.x**）

> 本节为 v2.x 重做蓝图，**MVP 不实现**。MVP 老人端没有提醒卡，主屏见 §3.1.5 单日记按钮版。触发条件：v2.x 家属端上线 + 服务端 reminder CRUD 落地后，按现描述实现。

| 区域 | 内容 |
|------|------|
| 顶部 | 系统状态栏 + 大铃铛图标 96dp（暖橙 `#E07A3C`） |
| 标题 | "该吃药啦" 32sp 居中 |
| 副标题 | "阿莫西林 · 1 粒" 24sp 居中（药名 + 剂量来自 reminder.payload） |
| 备注 | 有则显示，20sp 居中灰 |
| 主按钮 | "✓ 已吃"——高 120dp，墨绿底，32sp 白字 |
| 兜底 | 5 分钟无点 → 自动写入「未服药」ack；再 5 分钟再次响铃；30 分钟仍无应答 → 推送家属（v2.0 §11.2）|

**行为**：
- 弹出瞬间：震动 800ms + TTS "该吃阿莫西林了"
- 屏幕保持亮（`FLAG_KEEP_SCREEN_ON`）
- 点「已吃」：写 reminder_ack（`action=taken`）→ 播放上扬"叮" + 震动 → 卡片淡出 → 回到主屏
- 音量键/TTS 重复：右上角小图标「再听一遍」24dp，点击重播当前 TTS

**半粒视觉（§K.1f + §I-4.C 决议）**：主图标按剂量切换：
- "1 粒" → 整药丸
- "半粒" → 切半药丸（完整药丸 + 半药丸并列）
- "1 勺" → 药瓶 + 勺
- 自定义剂量档已移除（§I-4.C）；特殊剂量由家属端"备注"字段承载

**多药并发（§K.1a 决议）**：同一时刻触发 ≥ 2 条 reminder 时，**合并为一张卡**——主标题保持「该吃药啦」；副标题区按行纵列所有药名剂量（行高 48dp，字号 24sp，最多支持 4 种药；超出则拆第二张）；主按钮文案改为「✓ 全部已吃」——点击一次性 ack 所有；TTS 播报按顺序读全部药名。

**漏服回顾（§K.1c 决议）**：老人端**不展示**漏服统计（避免心理压力）；漏服 ack 写入后端，仅家属端可见。

**临时暂停（§K.1e 决议）**：老人端**不提供**暂停入口；暂停/恢复统一在家属端（§3.2.x 吃药项开关）。

**兜底节奏（§I-3 决议）**：沿用 v2.0 5/30 分钟节奏；午睡场景列入 v2.2 复盘。

**失败兜底**：
- 网络断开：本地 AlarmManager 仍能响铃（§L.2 决议），标记为「离线已记录」，联网后同步

**后台 / 离线起播**：以本地 AlarmManager 注册为准（M2 起播）；`/v1/reminders` INCREMENTAL 同步刷新未来 7 天的闹钟列表。
#### 3.1.2 AI 写日志（v3.0 MVP 唯一核心功能）

MVP 老人端录音 → 日记 全流程在客户端独立完成，**不依赖自建服务端、不依赖家属端、不依赖推送通道**。老人自配的大模型 ASR endpoint 是唯一外部依赖（详见 §3.1.9）。

> **0.5.0 目标**：本节从“单次录音一次成稿”扩展为 §3.1.2 + §3.1.4 + §3.1.6 的多轮访谈链路。v3.0.1 单次录音保留为弱网降级路径；正常联网时默认进入多轮 Agent 访谈。

**MVP 流程**（单次录音一次成稿，无 multi-turn）：

1. **进入**：主页 A 区点 "按住说话写日志" → 跳到录音屏，进入即开始录音（toggle 模式，PR-A 已落地）
2. **录音中**：顶部红字 "正在录音… mm:ss" + 底部 160dp 墨绿圆按钮 "点击停止"
3. **停止 → ASR**：点 "点击停止" → MediaRecorder 停止 → 用 §3.1.9 配置的 ASR endpoint 调 HTTP → 拿到转写文字 + 置信度
4. **保存为日志**：转写文字 + 录音时长 → 落本地 Room 表 `diary_entry_local`（§5.10 schema：device_id/date/text/transcript/source/audio_path/duration_ms/asr_provider/asr_model/asr_confidence/asr_latency_ms/created_at/updated_at/deleted_at）
5. **完成态**：屏切到 "✓ 已录音 mm:ss" + 转写文字预览（24sp）+ 96dp 墨绿 "返回主页" 按钮 → 回主页（主屏「今日记录」图标点亮，见 §3.1.7）
- 音频文件落地：录音停止后立即写入 `cacheDir/audio/{uuid}.m4a`，与 Room `diary_entry.audio_path` 关联；§5.3 注：MVP **不**上传 COS（v2.x 再接）

**录音规格（沿用 PRD §L.3 + 方案 D）**：
- API：`MediaRecorder`，输出 AAC/m4a
- 采样率 / 编码：**16 kHz / 单声道 / 64 kbps**
- 单轮时长硬限：**60 秒**——到时自动停止（v2.x MVP 不做 TTS"时间到"，依赖服务端 URL）
- 屏幕保持亮：`FLAG_KEEP_SCREEN_ON`
- 麦克风权限拒绝（§4.6）：弹"需要麦克风权限才能写日记"+ 跳转系统设置

**异常处理（v3.0 MVP）**：
- **ASR 失败**：弹大按钮 "没听清，再说一次"（96dp 高），回录音重新开始（无 turn 计数）
- **网络断开**：弹"网络不通，请检查 Wi-Fi"+ 大按钮"再试一次"（不做"待重试"标记——MVP 不做离线缓存，避免复杂度爆炸；v2.x 接回服务端 + COS 时再做）
- **录音失败**：自动重试一次，仍失败走"再试一次"入口
- **老人中途退出（按返回）**：录音中 stop 释放 MediaRecorder，回主页；未保存录音文件保留在 cacheDir，下次启动清理
- **未配 ASR**（§3.1.9 未填 key / endpoint 为空）：进入录音屏前主屏 Toast「请先在设置 → AI 语音识别 配置 API」5 秒 + 红点提示 24h（不弹窗强制）
- **ASR 返回空文本**：弹"没听清，再说一次"+ 回录音重新开始；连续 3 次空 → Toast「换个安静点的环境再试」+ 回主屏

**v2.x 扩展（不在 MVP）**：
- multi-turn Agent 访谈（DSH agent 流）→ 留待 v2.x
- TTS 播报 Agent 回复 → 留待 v2.x（PRD §4.5 系统铃声兜底一起）
- 总结屏（§3.1.6）→ 留待 v2.x
- 方言 ASR 主识别（粤语）→ v2.x 用服务端千问 ASR
- 后端 agent turn 流（POST /v1/agent/diary-session/:id/turn 等）→ 全部延迟到 v2.x
- 后端 ASR 代理（v3.0 是老人端自配 endpoint 直连第三方）→ v2.x 服务端统一代理 ASR 凭证；MVP 用户自管的密钥不上服务端
- 服务端 LLM 日记整理 / 摘要 → v3.0 MVP **不**做服务端摘要，**仅**保存 ASR 转写原文（详见 §5.3 `source` 字段语义）

**已知 MVP 限制（v3.0 重申）**：
- ASR API key 存在客户端 EncryptedSharedPreferences（Keystore wrap），截图有外泄风险——仅适用 MVP 验收 / 内部演示；生产环境必须服务端代理（v2.x 改）
- 音频文件明文上送第三方 ASR，隐私敏感场景（医疗 / 家庭对话）需先经服务端做 PII 脱敏（v2.x）
- 60 秒硬限到时无 TTS"时间到"提示（依赖服务端 URL）→ v2.x 接回 agent 流后补
- 日记无云端备份——卸载 / 换机即丢失，§3.1.8 第 6 项退出登录必须二次提示兜底
- 日记无服务端摘要 / 整理——MVP 保存的就是 ASR 转写原文；用户在「今日记录」屏可手动改写（v2.x 接服务端 LLM 后再做摘要）

**删除权限（§K.2f 决议沿用）**：老人端**不提供任何删除入口**——既不能删 diary_entry，也不能删 audio 文件。MVP 由 §3.1.7 时间轴屏展示，**不可编辑 / 不可删除 / 不可分享**。v2.x 接家属端后，家属只读 + 配置权，无删除权。

#### 3.1.3 就医提醒全屏卡（v2.1 升级；**v3.0 MVP-DEFER → v2.x**）

> 本节为 v2.x 重做蓝图，**MVP 不实现**。MVP 老人端没有就医提醒全屏卡。触发条件：v2.x 家属端上线 + 服务端 reminder CRUD 落地后，按现描述实现。

结构基础同 §3.1.1，差异：

| 区域 | 内容 |
|------|------|
| 顶部 | 系统状态栏 + 医院十字图标 96dp（暖橙 `#E07A3C`） |
| 标题 | "该去看医生啦" 32sp 居中 |
| 副标题 | "协和医院 · 心内科 · 上午 10:00" 24sp 居中 |
| 准备清单 | 家属端填写的备注，20sp 居中灰（§K.3e）——例：「带医保卡、带上周验血报告」|
| 主按钮 | "✓ 知道了"——action=`ack` |
| 兜底 | 同 §3.1.1：5/30 分钟节奏 |

**提前提醒（§K.3a 决议）**：**默认提前 1 小时**先弹一次「准备出门」预备卡（高度同 §3.1.1 但无主按钮，只有"知道了"+ TTS 播报）；到点再弹正式全屏卡。提前时间家属端可调（§3.2.5 家属端就医表单新增「提前提醒」字段，默认 60 分钟，可选 30/60/120）。

**多条合并（§K.3b 决议）**：同时段多条就医提醒**叠**——依次弹出全屏卡，前一张「知道了」才能进下一张；不合并。

**改约同步（§K.3c 决议）**：家属端把就医时间**往后调**时，老人侧主屏提醒卡自动刷新（拉到最新 payload）；**仅支持往后调**——往前调（立即/过去时间）后端直接拒，返回 422 `VALIDATION_ERROR`。

**复诊周期（§K.3d 决议）**：v2.0 §5.4 `reminder.payload.appointment.repeat` 字段**不支持 UI 录入**；每次就医提醒家属手建。即使同医院同科室每周复诊，也不自动续期。

**路线导航（§K.3f 决议）**：v2.2 实现。

**完成反馈（§K.3g 决议）**：老人点「知道了」→ 写 ack + 后端发推送 → 家属端通知栏收到"老人已确认 10:00 就医"；家属端日志 Tab / 提醒 Tab 同时可见。

### 3.1.4 Agent 行为约束（硬规则；**0.5.0 启用 / v0.6.0 扩展**）

> 本节为 0.5.0 本地 Agent 多轮访谈的硬规则；v0.6.0 在不破既有 A1–A3、A5、B1–B4、C2、C3、D1–D3 的前提下，**扩展 A6 共情前置 + 修订 A4 + 修订 C1 + 新增 E 节访谈技巧 + 新增 F 节 Hermes 风格记忆层**。**Agent 状态机、安全关键词和本地工具运行在 Android；ASR / LLM / TTS 由 Android 使用用户自填 Key 直连在线模型 API**。
>
> 0.5.0 启用条件：千问 Realtime ASR + MiniMax M3 + 千问 `qwen3-tts-flash-realtime` 联调通过；客户端本地状态机、流式播放和凭据配置均落地。v3.0.1 单次录音模式继续作为断网降级路径。
>
> 0.6.0 启用条件：v0.5.0 联调通过 + `system_v2.txt` / `save_v2.txt` 版本化 prompt 落地 + Room Migration 3→4 + `elder_facts` 表 + `remember_fact` / `search_memory` / `mark_dimension_covered` / `MOVE_ON` 工具在 Kotlin 二次校验；对照脚本评分（`scripts/interview_eval/`）通过后方可宣称支持窦文涛式访谈 + Hermes 风格记忆。

**A. 表达约束**
- A1. 每次回复只问一个问题或说一件事，说完即停；禁止连续追问
- A2. 单次回复不超过 25 个汉字
- A3. 使用口语化中文，避免书面语；可用常见感叹词（「嗯」「挺好的」「然后呢？」）
- A4. 不得完整复述或总结老人**当前会话**说过的话；允许在共情前置中用 ≤3 个汉字轻量回扣前文细节（如「那老张…」「刚才那个…」）；不得用「1.2.3.4.」分点。**F 节注入的 `<elder-facts>` / `<recent-summaries>` 不算被 LLM 复述的对象**，但 LLM 不得在输出中原样复制记忆文本
- A5. 方言容忍：ASR 转写后保留口语化文本，不要因方言用法判定为无效输入
- ~~A6.（v0.6.0 新增）共情前置~~（**v0.9.1 删除**：取消 ack/probe 双段拆段与总长 ≤35 字硬约束；Kotlin 端 `InterviewAgent.respond()` / `ChatAgent.respond()` 不再做 splitAckProbe；单段回复 ≤25 字仍受 A2 约束；OPENING 阶段同样单段输出；详见 §12.4.1）

**B. 安全约束**
- B1. 老人提及金钱、转账、验证码、陌生链接时，立即调用 ask_clarify 工具跳过该话题并转向日常闲聊
- B2. 老人提及急救类关键词（「摔了」「喘不上气」「胸口疼」），立即调用 save_diary 工具保存当前轮并终止会话
- B3. 老人提及悲伤或健康话题时，不得主动给医疗建议；只用一句「听起来不容易」带过
- B4. 老人提及医疗问答（症状诊断、用药建议），调用 ask_clarify 换话题

**C. 收尾触发（满足任一即收尾）**
- C1.（v0.6.0 修订）信息维度足够：LLM 显式调用 `mark_dimension_covered(dim)` 工具累计覆盖 ≥2 项（维度枚举扩到 5：`time / place / person / event / feeling`），且 E2 细节阶梯至少走到 `how-felt` 阶段。维度判定由客户端 `AgentSafety.kt` 的 keyword 字典迁到 LLM 工具调用，`feeling` 维度保留 keyword 字典兜底
- C2. 轮数达到硬上限（默认 8 轮，见 §11.5）
- C3. 老人明确说收尾（如「就到这」「不聊了」）
- 收尾动作：调用 save_diary 工具，将 text（≤ 100 字）+ summary（≤ 60 字）落库，audio_segments 含每轮语音 COS key 与 ASR 文字

**D. 输出约束**
- D1. 日记正文 text 长度 ≤ 100 字
- D2. 一句话摘要 summary 长度 ≤ 60 字
- D3. 必须同时返回 text + summary + audio_segments 三段；不得只返回其中之一


**E. 访谈技巧（v0.6.0 新增；参考窦文涛《锵锵三人行》手法）**

访谈技巧是 **软指令**——通过 `system_v2.txt` 提示词 + few-shot 范例引导 LLM 采用；硬约束仍是 A1–A5 / B / C / D（v0.9.1 起；A6 删除）。技术细节由 `system_v2.txt` 文件承载（AGENTS.md §A.11.2 prompt 版本化），本节规定要点：

- E1 **命名情绪再探细节**（v0.9.1 软指令化，不再硬约束 ≤10 字前置）：可先用 ≤10 字情绪命名（情绪命名示例：`听起来挺高兴` / `有点意外吧` / `这事挺闹心的`，可选），再 ≤25 字追问；情绪命名与追问总和 ≤25 字受 A2 约束；不要冷启动追问
- E2 **细节阶梯**：按 `who → what → how-felt → small-detail` 顺序推进；后一阶是前一阶的具体化，不另起话题；C1 要求至少走到 `how-felt` 阶段
- E3 **开放探针句式**：优先用 `那…呢？` / `怎么…的？` / `当时…？` / `后来呢？`；避免 `是不是 / 对不对 / 几岁` 等闭式问句（闭式问句易让老人陷入"是 / 不是"短答）
- E4 **节奏控制**：连续两轮追问同一维度触发 `MOVE_ON` 工具，进入下一维度或收尾；防止"审问感"
- E5 **停顿与沉默**：老人回复 `嗯 / 对 / 是` 等 1–2 字时不主动转话题，等下一轮再探；让老人有时间回忆
- E6 **跨会话回扣**：基于 F 节注入的 `<recent-summaries>` / `<elder-facts>` 引用其中的人物 / 地点 / 事件作为追问锚点（例：`你常去公园，今天呢？`）；**禁止完整复述记忆文本**（A4 联动）

**F. Hermes 风格记忆层（v0.6.0 新增；纯 Room 方案）**

> v0.6.0 不引 embedding / 不引向量库 / 不引 Room FTS / 不引 MD 文件 / 不引新 SDK。记忆检索用 SQL `LIKE '%query%'`；事实抽取由 LLM 主动调用工具 + `save_diary` 后 background learning 双轨。参考 `NousResearch/hermes-agent` + `ClaudioDrews/memory-os` Layer 7 + `chandra447/pi-hermes-memory`，但剥离 FTS / 向量 / Markdown 文件层。

- **F1 数据源**：
  - `diary_entry_local.summary`：最近 3 天 summary（按 `date` 倒序，含当天）
  - `elder_facts` Room 表（v0.6.0 新增）：长期结构化事实
- **F2 `elder_facts` 表 schema**（AGENTS.md §A.11.3 兼容；Migration 3→4 用 `CREATE TABLE` + 索引）：
  - `id: UUID PRIMARY KEY`
  - `type: TEXT NOT NULL` —— 枚举 `person / place / event / preference / health`
  - `content: TEXT NOT NULL` —— ≤100 汉字（与 `diary_entry.text` 同限）
  - `confidence: TEXT NOT NULL` —— 枚举 `high / medium / low`（静态标注，v0.6.0 不引入 trust_score 反馈循环）
  - `last_used_at: INTEGER NOT NULL` —— epoch ms
  - `mention_count: INTEGER NOT NULL DEFAULT 0` —— 被 `search_memory` 命中次数
  - `source_session_id: TEXT` —— nullable；哪次访谈学到
  - `created_at: INTEGER NOT NULL` / `updated_at: INTEGER NOT NULL`
  - 索引：`(type, last_used_at DESC)`
- **F3 LLM 主动记忆工具**：
  - `remember_fact(type, content, confidence)` —— LLM 主动保存新事实；Kotlin 校验 type ∈ 五类枚举、content ≤100 字、confidence ∈ 三档；调用 `ElderFactDao.findSimilar(content)` 做 substring overlap > 0.6 去重；冲突时 update 而非 insert
  - `search_memory(query, limit=5)` —— LLM 主动查询；Kotlin 调 `ElderFactDao.searchByContent(query, typeFilter, limit)` 用 SQL `WHERE content LIKE '%query%' OR type = ?` 返回 top N；不读 MD 文件、不做 embedding
  - 工具名仅上述两个 + 既有 `ask_clarify` / `save_diary` + v0.6.0 新增 `mark_dimension_covered` / `MOVE_ON`；Kotlin 端 `InterviewAgent.validateCall()` 仍二次校验
- **F4 system prompt 三块注入**（`AgentPrompts.system(recentSummaries, elderFacts)` 渲染）：
  - `<elder-facts count="50">` —— 当前 `elder_facts` 全量（按 `mention_count DESC, last_used_at DESC` 排序，前 50 条）；每行格式 `- [type, confidence] content`
  - `<recent-summaries>` —— 最近 3 天 summary；每行格式 `- 【日期】summary`
  - `<memory-policy>` —— Hermes Layer 7 风格指令块；告诉 LLM 记忆权威、如何使用、何时调用工具；明确"不要复述原文 / 不要让老人察觉'查资料'"；明确"history 不参与 B 节安全判定"
- **F5 Background learning**（仅 `save_diary` 后触发）：
  - 时机：`save_diary` tool 处理完成 + diary 落库后，由 `InterviewAgent.backgroundLearning()` 追加调用
  - 输入：`prompts.saveForBackgroundLearning()`（复用 `save_v2.txt` 但要求返回 `{text, summary, facts_to_remember:[...]}`）+ 本次 transcript（≤4000 字）
  - 输出解析：`facts_to_remember[]` 每条走 `remember_fact` 工具同一路径（校验 + 去重 + 写入 Room）
  - 限制：单次抽取 ≤5 条 / fact content ≤100 字 / LLM 失败静默 `catch` + warning 日志（§7 sanitize 拦截 `elder_facts` 内容）；不阻塞 diary 保存路径
- **F6 隐私与边界**：
  - 本地 Room（`elder_v3.db`），不上传服务端；与 v0.5.0 一致
  - `elder_facts` 内容**不入日志**（§7 sanitize 必须过滤；含 PII 字段如手机号 / 身份证 / 密码 / 地址门牌 由 `save_v2.txt` 提示词禁止字段白名单 + Kotlin 二次过滤）
  - 历史 `<recent-summaries>` / `<elder-facts>` **不参与 B 节安全判定**（emergency / money / medical 仍只基于 `elderText`，不读 history）；但 `<recent-summaries>` 里有"摔了一跤"等急救记录时，Agent 在 prompt 层被要求"history 不引发 emergency 流程"
  - **纯文本边界**：不引 embedding / 不引向量库 / 不引 Room FTS / 不引 MD 文件 / 不引 Markdown 解析库 / 不引新 SDK
  - 不实现跨会话语义检索（v0.6.0 不引 MiniMax embedding / 千问 embedding）
- **F7 类型枚举与 C1 `feeling` 维度对齐**：
  - `person` → C1 `person` 维度
  - `place` → C1 `place` 维度
  - `event` → C1 `event` 维度
  - `preference` → C1 `feeling` 维度（替代 `AgentSafety.kt` 中 `dimensions` 字典的 keyword 兜底）
  - `health` → 单独类型（隐私敏感）；`search_memory` 不默认召回 `health` 类，必须 LLM 显式 `query` 字段包含健康关键词

#### 3.1.5 老人端主屏（v3.0 MVP 版）

主屏元素**恰好 2 个**（v3.0 收窄，移除今日提醒卡；隐藏设置入口不计；满足 §4 第 2 条"主屏元素 ≤ 3 个"硬约束）：

```
┌─────────────────────────────────┐
│  区域 A：问候（高 200dp）        │
│   「早上好」  40sp 居中          │
│   「8 月 24 日 周一 09:12」20sp   │
│   右上角 32dp：今日记录图标      │
├─────────────────────────────────┤
│  区域 C：写日记按钮（高 200dp）  │
│   「✎  按住说话写日记」          │
│   墨绿主色，文字 32sp 居中       │
└─────────────────────────────────┘
```

**v2.x 重做**：v2.x 接回 §3.1.1 / §3.1.3 提醒卡 + §3.2.7 家属绑定后，区域 B 按下表回归：
- 区域 B 今日提醒卡（高 120dp）：「下一项：吃阿莫西林 09:30」或「今日暂无提醒」
- 区域 C 写日记按钮（高 120dp）：保留，缩 80dp

**MVP 未配 ASR 提示（v3.0 新增）**：ASR 未配置（§3.1.9）时，A 区上方出现 24sp 红字「请在设置 → AI 语音识别 配置 API」一行，配红点；点设置入口直跳 §3.1.9。

**今日记录图标（§D.6 + §I-2 + §H.26）**：保存日志后点亮——位置在 A 区右上角 32dp；亮起 = 墨绿实心，未亮 = 灰色描边；点亮 = 颜色动画 300ms + 单次短震 100ms，不弹 Toast。

**隐藏设置入口**：连点 A 区中央 5 次（每次间隔 ≤ 500ms）→ 进入 §3.1.8 隐藏设置；进入时震动 + TTS"打开设置"。

**写日记按钮按下**：墨绿→`primary_pressed`，显示波形 + 时长（见 §3.1.2）。

**边缘情况**：
- 当前时间已有提醒触发中（v2.x 才有）：主屏直接被全屏提醒卡覆盖（§3.1.1 / §3.1.3）
- 设备锁屏：MVP 无提醒卡，锁屏态主屏仅显示问候区；点击亮屏回到主屏

#### 3.1.6 访谈总结屏（v2.1 新增；**0.5.0 启用**）

> 本节为 0.5.0 本地 Agent 多轮访谈的总结屏。**客户端完成状态机收尾并直接调用模型 API**；“改一下”加轮流程由本地状态机重新打开当前会话，达到硬上限后再次收尾。

进入条件：Agent 触发收尾（§3.1.4.C）。

布局：

```
┌─────────────────────────────────┐
│  标题：「今天记好了 ✓」  32sp    │
├─────────────────────────────────┤
│  日记正文卡                      │
│   「今天和老张下了盘棋，         │
│     我赢了，挺高兴。」          │
│   24sp 主文字，行高 1.6          │
├─────────────────────────────────┤
│  一句话摘要：                    │
│   「和老张下棋，赢了」  20sp 灰  │
├─────────────────────────────────┤
│  按钮组（垂直两枚，高 96dp）     │
│   「✓ 保存」  墨绿               │
│   「✎ 改一下」  描边墨绿         │
└─────────────────────────────────┘
```

**交互**：
- 默认 TTS 播 summary 一遍
- 点「保存」：写 `diary_entry` + finalize session → 主屏「今日记录」图标亮起 + 短震 + 上扬"叮" + 回主屏
- 点「改一下」：回到 §3.1.2 第 4 轮加一轮（最大再加 2 轮），再次进入收尾
- **不提供"删掉重录"入口（§K.2g 决议）**：误录只能走「改一下」加一轮补充，不允许丢弃已有 turns

#### 3.1.7 老人端「今日记录」图标点亮规则（v3.0 MVP 版）

```
未亮：灰色描边铃铛/笔图标
亮起条件：当日存在至少 1 条 `diary_entry`（v3.0 无 status 字段，所有落库条目即"已记"）
熄灭条件：跨过设备本地时区零点；或新一天首次进入主屏。时区取系统默认时区（v3.0 MVP 没有 `elder_profile` 表，与 `diary_entry.date`（§5.3）取同一时区，避免跨日区时图标状态错位。
位置：主屏 A 区右上角 32dp
点亮反馈：图标颜色动画（300ms 渐入墨绿）+ 单次短震 100ms；不弹 Toast
```

**点击行为（v3.0 MVP 版）**：点开 → 进入**近期日志屏**：
- 顶部时间范围胶囊：`今天` / `近7天`（MVP 不做本月/全部）
- 每条条目显示：时间（HH:mm）+ ASR 转写文字（24sp）+ ▶ 重播自己语音（点击 MediaPlayer 播 `audio_path`）+ **手动改写按钮（v3.0 新增，MVP 唯一编辑入口）**：点开弹全屏编辑框（24sp 多行，限 200 字；保存覆盖 `text` 字段；不弹 Toast，保存即生效）
- **v3.0 不展示** v2.x 的「▶ 听 Agent 回复语音」「summary」——MVP 没有 Agent 回复与摘要
- 列表为空时：分空态文案（今天："今天还没记"；近7天："近7天还没有日志"）+ TTS 按钮
- 全部条目仅展示，**可手动改写 text，不可删除 / 不可分享**

**v2.x 重做**：v2.x 接回 §3.1.6 总结屏 + §3.1.4 Agent 行为约束后，每条条目展开为 `text`（100 字）+ `summary`（60 字）+ `audio_segments[]` 时间轴，▶ 听分段 + ▶ 听 Agent 回复；手动改写入口废弃（统一走服务端 LLM 改写）。

**删除权限（§K.2f 决议沿用）**：老人端**不提供任何删除入口**——既不能删 diary_entry，也不能删 audio 文件；避免误操作。MVP 没有家属端，删除由卸载 App / 清理本地数据兜底；v2.x 接家属端后，家属只读 + 配置权，无删除权。

#### 3.1.8 老人端设置（隐藏入口，v3.0 MVP 版）

进入：主屏 A 区中央连点 5 次（每次间隔 ≤ 500ms）。

布局：单列列表，每行高 72dp，24sp 文字，右向箭头。

| 行 | 标题 | 说明 | 行为 |
|----|------|------|------|
| 1 | 字体大小 | "大 / 特大"二选一切换 | 档位 sp 值（§H.16 + §I-7 决议）：默认 24/32 → **大 28/38** → **特大 32/44**（body/title） |
| 2 | 语音播报 | 开 / 关 | 关时所有 TTS 静音，仅震动 |
| 3 | **AI 语音识别** | **v3.0 新增首位**——千问 ASR / Whisper / OpenAI Whisper 等大模型 ASR API 配置（老人自填 endpoint / key / model） | 进入 §3.1.9 配置页；未配置时该行右侧红点提示 |
| 3 | 音量 | 跳转系统媒体音量设置 | `Intent ACTION_SOUND_SETTINGS` |
| 4 | 音量 | 跳转系统媒体音量设置 | `Intent ACTION_SOUND_SETTINGS` |
| 5 | 关于 | 版本号 / 服务条款 / 开源 license | 只读 |
| 6 | 退出登录 | — | 清本地 Room + 清 ASR 配置 + 回身份选择屏（§B） |

**v3.0 删除**：原第 4 项「生成本机二维码」——MVP 无家属端，删除；v2.x 接回时按现描述恢复。

**退出登录对老人端的影响（§H.20 决议）**：
- **MVP 没有绑定状态**——直接走简化路径：弹确认卡，提示语必须含——
  - "退出后所有日志和设置都会清空"
  - **"日志只存在这台手机，退出后无法恢复"**
- 同意后：清本地 + 调 `/v1/bind/elder/:id`（仅 primary 可解绑，见 v2.0 §11.6）
- 同意后：清本地 Room + 清 ASR 配置（EncryptedSharedPreferences）+ 跳转身份选择屏（§B）
- **不需要**调任何服务端解绑接口（无服务端 / 无家属端）

**v2.x 重做**：v2.x 接回家属绑定后，按原 §H.20 描述实现——含"再装此 App 需要家人重新绑定"提示 + 调 `/v1/bind/elder/:id`。

#### 3.1.9 ASR API 配置页（v3.0 MVP 新增；老人端独立运行的核心配置）

> 本节是 v3.0 MVP 的**唯一新增功能章节**——老人端日记流程不依赖服务端，因此老人必须在本地配置一个大模型 ASR API 的凭证与 endpoint。本节定义配置页 UI、配置 schema、调用约定、安全边界。AGENT.md / Android 实现层在本节引用。
>
> **0.7.0 修订**：页面升级为「AI 服务」双 Provider 可切换——ASR 子页 Provider 选项：`百炼`（千问 qwen-audio-3.0-realtime-plus）/ `MiniMax Realtime`（MiniMax Realtime ASR WebSocket）；TTS 子页 Provider 选项：`千问 Kiki`（`qwen3-tts-flash-realtime`）/ `MiniMax Cantonese_KindWoman`（MiniMax T2A WebSocket）。**默认 Provider = MiniMax ASR + MiniMax TTS**（音色 `Cantonese_KindWoman`），千问作为可选回滚路径保留；voice_id 在 MiniMax TTS 上硬编码为 `Cantonese_KindWoman`，不暴露 UI。LLM（M3）Provider 不切换，沿用 v0.5.0/0.6.0。**0.5.0 修订（已废止）**：页面升级为「AI 服务」，包含千问 Key（ASR + TTS 共用）和 MiniMax Key（M3 LLM）。模型和 endpoint 固定，不再展示多 Provider 或 endpoint 编辑。

**进入路径**：§3.1.8 第 3 项「AI 语音识别」（v3.0 移到首位）。

**页面布局**：

```
┌──────────────────────────────────────────┐
│ ← 返回              AI 语音识别        ✓ │  ← 顶部 bar（高 64dp），右侧 ✓ 仅在所有必填有效时亮起
├──────────────────────────────────────────┤
│  说明卡（高 96dp）                        │
│   「把大模型 ASR 的配置填在这里，         │
│     老人端不依赖任何第三方服务器。         │
│     配置只保存在这台手机。」 20sp 灰      │
├──────────────────────────────────────────┤
│  Provider 只读卡片（v3.0.1 §A.1.b）       │
│   • 服务商：阿里云百炼                    │
│   • 模型：Qwen-Audio-3.0-ASR-Flash-       │
│     Streaming                             │
│   • WorkspaceId：llm-svrk4hi977f8t2fe    │
│  ↑ 不可改；上游单一化                     │
├──────────────────────────────────────────┤
│  表单字段（仅一项）：                     │
│   • API Key（必填，密码样式）             │
├──────────────────────────────────────────┤
│  「测试一下」按钮（高 96dp，主色填充）    │
│   → 调 Provider 默认 endpoint 上送一段   │
│     5 秒示例音频（客户端生成 440Hz 正弦  │
│     波）→ 显示返回文字 + 延迟 ms          │
├──────────────────────────────────────────┤
│  「保存」按钮（高 96dp，墨绿，✓ 亮起）   │
└──────────────────────────────────────────┘
```

**Provider 字段表**（v0.7.0 多 Provider；endpoint / voice_id 客户端硬编码，UI 不暴露）：

| Provider | Endpoint | Model | 请求体格式 |
|----------|----------|-------|-----------|
| 千问百炼（默认回滚路径） | `wss://llm-svrk4hi977f8t2fe.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime` | `qwen-audio-3.0-realtime-plus` | Qwen-Audio Realtime WS：`session.update` → `input_audio_buffer.append` × N → `commit` → `transcription.completed` || MiniMax Realtime（默认） | `wss://api.minimax.cn/ws/v1/stt` | `<待 0.7.0 真实抓包>`（AGENTS §A.12.1 占位） | MiniMax Realtime WS：`session.start` → `audio.chunk` × N → `transcript.partial` / `transcript.final` → `session.finish` || 千问 Kiki | `wss://dashscope.aliyuncs.com/api-ws/v1/realtime` | `qwen3-tts-flash-realtime` | DashScope TTS Realtime WS：`session.update`（`voice=Kiki` / `response_format=PCM_24000HZ_MONO_16BIT`）→ `response.audio.delta` × N → `response.done` || MiniMax Cantonese_KindWoman（默认） | `wss://api.minimax.cn/ws/v1/t2a` | `<待 0.7.0 真实抓包>`（AGENTS §A.13.1 占位） | MiniMax T2A WS：`session.start`（`voice=Cantonese_KindWoman`）→ `text.chunk` → `audio.delta` × N → `session.done` |

**字段约束**（v3.0.1 §A.1.b）：

| 字段 | 类型 | 必填 | 校验 |
|------|------|------|------|
| `api_key` | string | 是（千问 ASR + TTS 共用） | 长度 8–200；**保存前用 Keystore-wrapped EncryptedSharedPreferences 加密**，落盘不可读 || `minimax_api_key_enc` | string | 是（MiniMax LLM + MiniMax ASR 共用） | 同上加密方式 || `tts_minimax_api_key_enc` | string | 是（MiniMax TTS 独立 Key） | 同上加密方式；与 LLM/ASR 区分 || `asr_provider` | string | 是 | 取值 `bailian`（默认回滚）/ `minimax_realtime`（默认） || `tts_provider` | string | 是 | 取值 `qwen`（默认回滚）/ `minimax`（默认） || `tts_voice_id` | string | 否（仅 MiniMax TTS 写入） | MiniMax TTS 硬编码 `Cantonese_KindWoman`；UI 不暴露 |

**Room 表 schema（§5.11 `asr_config` v3.0.1 §A.1.b）**：仅 `id` / `api_key_enc` / `updated_at` / `last_tested_at` / `last_test_result`；详见 §5.11。
  last_test_result     TEXT                     -- JSON: {"text": "...", "latency_ms": 1234} 或 {"error": "..."}
)
```

**ASR 调用约定（§3.1.2 引用）**：
- 客户端从 Room 读 `asr_config.api_key_enc`；WebSocket 协议硬编码 WorkspaceId/model，路由到阿里云百炼上游（v3.0.1 §A.1.b）
- 请求前**不**打印 `api_key` 到日志（§7 sanitize 必须）
- 客户端→第三方 endpoint：HTTPS 强制；HTTP 端点仅 dev 模式允许（prod 拒绝，§7.1）
- 请求超时：30 秒（与 PRD §6.2 `UPSTREAM_TIMEOUT` 对齐）
- 响应统一解析为 `{text: str, confidence?: float}`；千问的二次轮询结果也走同一映射
- 失败映射 `AppError` 错误码（§6.2 客户端本地版）：
  - HTTP 401/403 → `ASR_AUTH_FAILED`（提示"API Key 不对"）
  - HTTP 429 → `ASR_RATE_LIMITED`（提示"太快了，等等再试"）
  - HTTP 4xx 其他 → `ASR_BAD_REQUEST`（提示"配置有误，去设置检查"）
  - HTTP 5xx / 超时 → `ASR_UPSTREAM`（提示"对方服务器没响应"）
  - 解析失败 → `ASR_RESPONSE_INVALID`（提示"对方返回看不懂"）

**安全边界**：
- API key **永远**不入 Room 明文；统一走 EncryptedSharedPreferences + Android Keystore（AES/GCM，§7.2）
- 测试按钮**只**送 5 秒 440Hz 正弦波（客户端合成的非敏感音频），不录真实声音——避免误触发"录音权限"理解
- 「保存」按 ✓ 后**立刻**清内存中的明文 API key（Java 引用置 null + GC）
- ASR 配置页**不**提供"显示明文 key"按钮——只显示 `••••••••` 掩码；想改 key 必须全量重输
- 退出 App / 切到后台 5 秒 → 清 ASR 测试按钮状态（避免 demo 屏残留明文片段）

**异常 UX**：
- 字段未填齐时 ✓ 灰；点击 ✓ 弹 Toast「请把必填项填好」
- 测试按钮调用中显示 loading + 30 秒超时 → 同 §3.1.2 网络断开 UX（弹"网络不通，请检查 Wi-Fi"）
- 测试返回错误时：`last_test_result` 存错误详情 + 表单顶部红条（24sp 红字，§4.8 Toast 规范）显示错误提示 5 秒

**v2.x 演进（不在 MVP）**：
- 服务端代理 ASR：用户 key 改存服务端（OAuth 流程），客户端只持 session；本节 Provider 列表由服务端动态下发
- Provider 插件化：用户可导入 .json Provider 配置包（第三方 ASR）
- 凭证分级：家庭共享 key（多人共用一个 ASR 凭证）——v2.x 接家属端后才有意义

### 3.2 家属端

> **v3.0 MVP-DEFER → v2.x 整章**：本节为 v2.x 重做蓝图，**MVP 不实现**。v3.0 老人端独立运行，没有家属端 App。家属端上线条件：v3.x 接回服务端（agent-service + reminder-service + account-service）后，按现描述实现。
>
> v2.x 启用范围（8 个子节全部）：家属主屏 / 提醒 Tab / 设置服药 / 设置就医 / 日志 Tab / 日志详情 / 扫码绑定 / 老人侧绑定确认 / 未绑定前写日记。

#### 3.2.1 家属主屏（v2.1 新增）

进入后判断本地状态：

**未绑定态（§E.1 决议 1，§I-8 实施）**：本地无 `binding` 记录时进入——只展示扫码入口（手机号绑定整体推 v2.2，§H.23）：
- 居中"扫老人手机上的二维码" 40sp 主色
- 大按钮「打开相机」96dp 高，宽 70% 屏
- 底部 16sp 灰字"输入手机号绑定（下版本开放）"，点开 Toast「该功能将在 v2.2 上线」（不进入实际表单）
- 流程见 §3.2.7 扫码绑定

**已绑定态**：本地存在至少 1 个 primary binding 时进入——
- 顶部老人卡（高 **96dp**）：「妈妈 · 王秀英」+ 设备状态（§E.1 决议 3）
- **设备状态（§E.1 决议 2）**：仅显示在线/离线 + 电量百分比（如「🟢 在线 · 电量 78%」）；其他信息点进次级屏
- Tab Bar（高 **64dp**）：[提醒] [日志]，两个 Tab 等宽；当前 Tab 文字 + 下划线 4dp 墨绿
- 头部总高度固定 **160dp**（§E.1 决议 3；MVP 单老人场景不压）
- Tab 内容区下面

#### 3.2.2 提醒 Tab（v2.1 新增，§E.2 决议）

布局：单屏两个分段（吃药 + 看医生），分段顶部 + 号入口。

**顶部进度行（§E.2 决议 7）**：分段标题之上固定一行 **48dp**，左对齐 **24sp** 主文字，如「今日服药：2 / 5」；无进度条、无图标；仅聚合当日全部「吃药」项的完成计数（老人端报到后实时刷新；**§K.1c 落地**：家属端可见漏服率，老人端不显）。

**吃药项行（默认行高 72dp）**：
- 左：药名 24sp + 剂量 20sp 灰（垂直两行）
- 中：**次数胶囊**「N 次/天」24sp 主色（§E.2 决议 5）
- 右：开关 48dp（关=暂停全部；on=启用）；长按菜单：编辑 / 删除
- **关停态视觉（§E.2 决议 4）**：toggle 关后**整行变灰** + 下方一行 14sp 灰字「老人不会收到此条提醒」；保留原位置不折叠；老人端保持沉默（§K.1e）
- **点行展开（§E.2 决议 5）**：默认折叠，点行后展开所有时间，每时间独立 56dp 子行（HH:mm + 独立开关，可单独暂停某次）+ 底部「编辑时间」按钮进 §3.2.3

**看医生项行（高 72dp）**：
- 左：医院 + 科室 24sp
- 中：日期时间 24sp，**时段映射（§E.2 决议 6）**：`今天 HH:mm` / `明天 HH:mm` / `MM-dd HH:mm`（超过 7 天全部 MM-dd 形式）
- 右：无开关（一次性事件）；长按菜单：编辑 / 删除

**删除二次确认（§E.2 决议 8）**：吃药项 + 就医项的「删除」均弹模态二次确认（标题「确定删除 X」+ 副标题「删除后无法恢复」+「取消」/「确认删除」）；编辑不弹。

**空状态**：整个 Tab 无任何提醒时居中显示「还没有提醒，点上面的 + 加一个」+ TTS 按钮 ▶。

#### 3.2.3 设置服药提醒（v2.0 §3.2.1 升级 + §E.3 决议）

- **入口**：「给爸妈加个吃药提醒」按钮

**表单字段**：

| 字段 | 控件 | 校验 |
|------|------|------|
| 药名 | 单行输入，28sp | 必填，1–20 字 |
| 剂量 | **单选卡片组（3 选 1）**：`1 粒` / `半粒` / `1 勺`（§I-4.C 决议：去除"自定义"档；特殊量走备注） | 必填 |
| 时间列表 | 列表，每行 `HH:mm` + 重复规则胶囊（**3 档**：`每天` / `周一三五` / `周二四六`，§E.3 决议 9）；底部 `+ 加一个时间`（默认 08:00）；至少 1 条；行末尾 ✕ 删除 + SnackBar 撤销（§E.3 决议 10） | — |
| 备注 | 多行输入，限 100 字 | 选填 |

- **必填校验（§E.3 决议 11）**：必填字段（药名 / 剂量 / ≥1 条时间）未填齐时，底部「保存」按钮 disabled 灰；填齐后激活为标准主色按钮
- **同时间冲突（§E.3 决议 12）**：静默允许（如 `1 粒 09:30 + 1 粒 09:30`）；MVP 不做冲突判断——处方上确实有"双倍剂量/天"的合理情况，避免误拦
- **底部主按钮**「保存」高 96dp；返回箭头在左上（系统行为，**不计入操作步骤**）
- **提交**：写入后端；推送到老人设备（推送通道见 §11.2 / §H.18 服药中优）
- **列表**：已配置的所有服药提醒，可编辑 / 删除 / **暂停/恢复开关**（§K.1e 决议）

#### 3.2.4 设置就医提醒（v2.0 §3.2.2 升级 + §E.4 决议）

| 字段 | 控件 | 校验 |
|------|------|------|
| 医院 | 单行输入，28sp | 必填，1–20 字 |
| 科室 | 单行输入，28sp | 必填，1–20 字 |
| 日期时间 | 日期选择器 + 时分选择器（系统 Picker，28sp） | 必填；**校验 = `日期时间 + 提前提醒分钟数 > 现在`（§E.4 决议 14，§K.3c「只支持往后调」落地）** |
| **提前提醒** | **单选胶囊组（3 选 1 必填）**：`30 分钟` / `60 分钟` / `120 分钟`，**默认 60**（§K.3a + §E.4 决议 13；§K.3d「每次都需要家属设置」） | 必填 |
| 备注 | 多行输入，限 100 字 | 选填 |

- **保存前摘要 Sheet（§E.4 决议 15）**：点「保存」**先弹底部 Sheet 摘要卡**——卡片显示「医院 + 科室 / 日期时间 / 提前分钟数」三层摘要 + 「确认创建」「再改改」两按钮；点击「确认创建」才真正 POST
- 底部主按钮「保存」高 96dp
- 提交：写入后端（推送通道见 §11.2 / §H.18 就医高优）
- 列表：可编辑 / 删除

#### 3.2.5 日志 Tab（v2.0 §3.2.3 升级 + §E.5 决议）

- **入口**：「看看爸妈今天怎么样」

**顶部筛选条（高 56dp）**：横向胶囊组 **`今天` / `近 7 天`**（§E.5 决议 16；**§K.5 不做关键词搜索**；§K.2a 对齐），单选；MVP 不做本月/全部。

**列表**：按日期倒序，每行高 96dp：

```
┌─────────────────────────────────┐
│  8 月 24 日 周一                 │
│  「和老张下棋，赢了」20sp 灰     │
│  (整行可点进入详情，§3.2.6 播放) │
└─────────────────────────────────┘
```

**列表项行为（§E.5 决议 17）**：**不放 ▶ 听按钮**，整行可点进 §3.2.6；避免就地播放打断浏览，并杜绝多曲并发。

**空状态**：「还没有日志」+ TTS 按钮 ▶。

#### 3.2.6 日志详情（v2.1 新增，§E.6 决议）

**顶部**：日期 + **「今天第 N 篇」** 24sp（N = 当天日记轮次计数，§E.6 决议 19；与 §3.2.5「本周档」删除对齐）。

**主体（双组件，符合 §3.1.4.D 三段语义，§E.6 决议 20）**：
- **文字卡**：`text` ≤ 100 字，24sp，行高 1.6；白底圆角，承载家属"读完即知"
- **时间轴**：`audio_segments[]` 每轮独立卡（时间戳 + ASR 文字 + ▶ 听按钮），承载"分段可听"

> §E.6 决议 20：列表项 (§3.2.5) 显示 `summary` 60 字摘要；详情页 (§3.2.6) 显示 `text` 100 字完整卡 + 时间轴分段——对应 §3.1.4.D 三段语义 (text / summary / audio_segments)。

**播放**：调 `/v1/diary/:id` 拿预签 COS URL → ExoPlayer 流式播（AGENT §3.4）

- **§E.6 决议 18**：**不自动播放**；每轮 ▶ 听显式存在；点 ▶ 才播；**单播放器**，点 ▶ 自动顶替当前正在播的一段
- **§E.6 决议 21**：单播放器 ▶ / ⏸ 切换；无进度条（每段 5-10 秒，进度条意义不大）；点 ▶ 别的段时，正在播的段自动停止并切到新段；**播放失败**（网络异常 / COS URL 失效）该段 ▶ 变灰 + Toast「听不了，请稍后再试」5 秒

#### 3.2.7 绑定流程总览 + 扫码绑定（v2.1 新增，§F.1 + §F.3 决议）

v2.1 仅支持**扫码绑定**（手机号绑定推 v2.2，§H.23 / §F.2 备忘）。

**家属侧流程**：
1. 在 §3.2.1 未绑定态点「打开相机」→ 调起相机（见 §4.6 权限）
2. 扫老人设备 §3.1.8 第 4 项生成的二维码（内容含 `device_token` + 临时 `bind_code`，**5 分钟有效**）

**老人侧流程**：
1. 进入 §3.1.8 第 4 项「生成本机二维码」→ 后端签发 `bind_code` → 在老人设备显示二维码（中心嵌 Logo）
2. 等待被扫（5 分钟内）；后台开始轮询 `/v1/bind/pending`（**10 秒一次**）

**等候屏视觉（§F.3 决议 22）**：老人设备显示一个**全屏遮罩**：
- 居中显示大二维码（边长 60% 屏宽，中心嵌 App Logo）
- 顶部 16sp 灰字倒计时「MM:SS 后过期」
- 底部「取消」按钮（96dp 高，墨绿描边）
- 倒计时归零：自动 Toast「二维码已过期，重生成」5 秒 + 「重生成」主按钮（96dp 高，主色填充）
- 倒计时归零前二维码持续 10 秒/次轮询 `/v1/bind/pending`

**等候期间交互（§F.3 决议 24）**：物理返回/Home 键**不取消流程**——仅退出遮罩，`bind_code` 仍保留 5 分钟有效；老人再次进入 App 时：仍在 5 分钟内 → 直接回到等候屏或 §3.2.8 确认卡；已过期 → 回 §3.1.5 主屏 + Toast「二维码已过期，重生成」5 秒。

**多人家属同时扫码**：5 分钟内任意次扫码都生成 `bind_attempt`；老人侧**队列模式**（§F.3 决议 23）：一次只显一张 §3.2.8 确认卡，同意/拒绝后出下一张；如无下一张则关闭弹窗。

#### 3.2.8 老人侧绑定确认卡（v2.1 新增，§F.4 决议）

| 元素 | 内容 |
|------|------|
| 标题 | **"XX 想绑定" 32sp**（XX = `family_user.name`；缺则回退手机号尾四位如 "136****8888"；**§F.4 决议 25 砍掉"儿子/女儿"关系词**，MVP 不引 `relation` 字段） |
| 副文字 | "绑定后，XX 可以给您设置提醒、看您记的日志" 20sp 灰 |
| 按钮 A | "同意"——墨绿，96dp 高 |
| 按钮 B | "拒绝"——描边灰，96dp 高 |

**点击"同意"时（§F.4 决议 28）**：
- 老人端体验：**全屏过渡 1 秒** + 主屏从 §3.1.7（未绑定态）切换到 §3.1.5（待提醒主屏）；**不要震动 + TTS**——避免老人启动瞬间被打断
- 流程：客户端调 `POST /v1/bind/confirm`，body `{ bind_code, elder_name }`；后端在该事务内**同时**：
  1. 创建 `elder_profile`（含 `name`、可选 `phone`、`device_token`、`timezone`）
  2. 创建 `binding`（`role=primary`）
  3. 返回 `elder_id`、`binding_id`
- 老人端写入本地 `elder_id` + JWT；主屏更新绑定状态
- 家属端收到推送/轮询"绑定成功"→ 进入 §3.2.1 已绑定态

**点击"拒绝"时（§F.4 决议 26 + 决议 27）**：
- 后端立即将该 `bind_attempt` 标记为 `rejected` + `bind_code` 失效（family_user 必须重新扫码发起）
- **不创建** `elder_profile` 也不创建 `binding`
- 老人端弹简短过渡卡"已拒绝"+ TTS 播报 → 1.5 秒后关闭弹窗回到主屏
- 家属端收到推送"对方未同意，请确认在场后再扫"
- **拒绝无理由选项**：MVP 不引入"不认识/再想想/选错"理由；UX 简单，避免老人端多一步操作

**Primary vs Secondary（§H.25 决议）**：
- 同一 `elder_profile` 上，**首个完成老人同意的家属 = primary**（有解绑权，见 §11.6）
- 后续家属邀请自动降级为 secondary；老人侧弹单卡"XX 想添加为家庭成员"（与首位邀请文案差异：标题无"主"字、副文字改为"可帮您设提醒、看日志，但不能解除其他家庭成员"）
- secondary 邀请**仍需老人同意**（隐私敏感，不绕过）

#### 3.2.9 未绑定前写日记（v2.1 新增，§F.5 + §H.24 决议）

老人首启到绑定成功之间，存在以下本地状态：
- 本地有 `role=elder` + `device_token`，**无** `elder_id`
- 主屏 §3.1.5「今日提醒卡」显示"等待家人配置提醒"
- 写日记按钮可用，录音 → ASR → 转写 → **暂存本地 Room 表 `pending_diary`**（不上传匿名 bucket），绑定成功后由 `flush_pending_diaries()` 批量 POST `/v1/diary/flush-pending`（带 `pending_id` 去重）

**Room schema**：

```
pending_diary(
  id           TEXT PRIMARY KEY,  -- 客户端 UUID
  created_at   INTEGER,           -- epoch ms
  audio_cos_key TEXT,             -- 已上传 COS 的临时音频 key（24h 过期）
  turns_json   TEXT,              -- Turn[] 序列化
  text         TEXT,
  summary      TEXT,
  status       TEXT               -- 'pending' | 'synced' | 'dropped'
)
```

**未绑定时退出 App / 卸载 App**：本地 Room 数据丢失，§3.1.8 第 6 项「退出登录」+「卸载 App」前应再次提示"未上传的日记会丢失"。

### 3.3 跨端流程

> **v3.0 MVP 范围内无跨端流程**——本节三条 v2.x 流程（扫码绑定 / 数据归属 / 未绑定前写日记暂存）全部依赖服务端，MVP 不存在。
>
> v2.x 启用条件：服务端 account-service + 三个端点（`/v1/bind/*` + `/v1/diary/flush-pending`）落地后，按原 §F 决议实现。

---

## 4. 老年友好设计（硬约束 + 设计 token + 系统级 UI 行为）

> 本节是面向客户端的"硬约束 + 设计 token + 系统级 UI 行为"统一集合；服务端字段、Agent 行为约束分别在 §5 / §3.1.4。本节是设计/开发的唯一真源，所有 §3.x 章节引用本节 token 表。

### 4.1 色彩 Token

| 角色 | 值 | 用途 |
|------|------|------|
| 主色 / 强调 | `#4A7A4A` | 主按钮、墨绿底色、Logo、§I-1 沿用色 |
| 辅色 | `#6B8E6B` | 次级元素、徽标背景、Today 亮起 |
| 警示 | `#C44545` | 错误 Toast 底（§4.8） |
| 网络黄 | `#FFF4E6` | 顶部黄条底（§4.9） |
| 文字主 | `#1A1A1A` | 主文字 |
| 文字次 | `#666666` | 次文字 / 灰字 |
| 背景灰 | `#F0F0F0` | 骨架屏占位 |
| 卡片白 | `#FFFFFF` | 卡片背景 |
| 暖橙 | `#E07A3C` | 全屏提醒卡的铃铛/十字图标（§3.1.1 / §3.1.3）|

**§I-1 决议：不追加品牌色**——Logo 沿用主色 `#4A7A4A`；不引入品牌色变体。

### 4.2 字号 Token（sp）

档位（§H.16 决议 / §I-7 沿用，无需复审）：

| 档位 | body | title | button |
|------|------|-------|--------|
| 默认 | 24sp | 32sp | 32sp |
| 大 | 28sp | 38sp | 38sp |
| 特大 | 32sp | 44sp | 44sp |

**v0.9.0 字号分档增量**（在原表之上加 5 档，**不**改既有约束语义；`BodyHugeSp = TitleLargeSp = 40` 合并档）：

| 档位 | v0.8.x | v0.9.0 | 用途 |
|------|--------|--------|------|
| `BodyHugeSp` | 40 | **40**（不变） | 转写卡片 / 录音时长（老人端最大字号） |
| `TitleLargeSp` | 38 | **40**（合并到 `BodyHugeSp`） | TopAppBar 标题 |
| `BodyXLargeSp` | 32 | 32（不变） | 摘要 / 列表主文本 |
| `BodyLargeSp` | 28 | 28（不变） | 主屏日期 / 副标题 |
| `BodyDefaultSp` | 24 | 24（不变，默认正文） | 正文 / 按钮 |
| `BodyInputSp` | 22 | 22（不变） | 输入框文本 |
| `BodySmallSp` | 18 | **22**（废除 18） | 副文案 / FilterChip |
| `caption()` | 20 | **22**（合并到 22） | 时间戳 / 红点旁 |

**v0.9.0 字重 / 行高 / 颜色约束增量**：
- 行高 = 字号 × **1.4**（转写 40sp → 56sp 行高；`TranscriptLineHeightSp` 48 → **56**）
- 主正文 `FontWeight` 从 `Normal` 改 **`Medium`**（老人反映"Normal 像没力气"）
- 标题 / 主操作保持 `FontWeight.Bold`
- 颜色对比(WCAG AA ≥ 4.5:1):`Brand500 / TextPrimary / TextSecondary / Error500` 已合规;新增 `TextTertiary #999999` 仅用于纯装饰(2.85:1,不承载信息)
- 中文长串:`maxLines = 2` + 展开按钮;`LineBreak.Heading` 中文按字断行;**不**用 `Ellipsis`(老人看不懂 `…`)
- 转写卡片:字号 40sp / 360dp 屏宽 → 每行 ~8 字;行高 56sp + 段间距 `Spacing.Lg`

约束：
- 默认正文字号 ≥ 24sp（旧 v2.0 ≥ 20sp，v2.1 收紧到 24sp 配合 §4.5 字号档位；v0.9.0 进一步收紧最小可视字号为 22sp）
- **v0.9.0 老人端最小可视字号 = 22sp**(`BodySmallSp` 18→22;`caption()` 20→22;原 18/20 档废除)
- 按钮文字 ≥ 32sp；最小按钮高度 96dp（§4.4）
- 老人端可手动切档（§3.1.8 第 1 项「字体大小」三档：默认 / 大 / 特大）
- 家属端沿用默认档（不放大）
- 主屏 body 用 32sp（顶部 40sp 问候）

### 4.3 间距 Token（dp）

基础间距 8dp（4dp 整数倍）：

| 档位 | 值 | 用途 |
|------|------|------|
| xs | 4dp | 内部紧凑 |
| sm | 8dp | 段落紧凑 |
| md | 16dp | 卡片内边距 |
| lg | 24dp | 卡片之间、组间距 |
| xl | 32dp | 区块之间 |
| xxl | 48dp | 主屏分区边界（区域 A / B / C 之间） |

### 4.4 圆角与动效

| 元素 | 圆角 |
|------|------|
| 卡片 | 8dp |
| 按钮 | 8dp |
| 输入框 | 8dp |
| Toast / 黄条 | 8dp |
| Tab Bar 胶囊 | 8dp（必要时改 9999dp 圆角） |

**动效约束**：
- 单次动效 ≤ **200ms ease-out**；无大片过渡动画
- 三种基础动效：**颜色变化 / 位移 / 放大**
- 不引入弹性动画、缩放、滑动关闭等复杂动作

### 4.5 声音与反馈

**TTS 供应商**：**千问 Realtime TTS**
**0.5.0 模型 / 音色**：`qwen3-tts-flash-realtime` + `Kiki`
**0.5.0 协议**：WebSocket；输出 24kHz / mono / 16-bit PCM；DashScope Key 用户自填
**历史音色配置**：v2.x 旧蓝图使用粤语男声（§H.15）；0.5.0 以 `Kiki` 为准
**TTS 失败兜底**（§H.17 决议）：
- **0.5.0 重定**：TTS 失败时保留并展示 `assistant_text`，老人可通过下一轮继续对话。
- 不因 TTS 失败重复调用 LLM，不把该轮判为对话失败。
- 系统铃声兜底仅保留为历史 v2.x 提醒场景，不用于 0.5.0 的 Agent 回复。

**反馈约束**：
- 默认开启 TTS 反馈，所有点击有语音确认（v2.0 §4 第 4 条沿用）
- 操作 ≤ 2 步（含确认，v2.0 §4 第 3 条沿用）
- 无手势操作（v2.0 §4 第 5 条沿用）——不教划一划、长按拖拽、双指缩放
- 无广告、无运营弹窗、无红点（v2.0 §4 第 6 条沿用）
- 启动后默认进入主屏，不出现引导页轮播（v2.0 §4 第 7 条沿用）

### 4.6 权限申请时机

| 权限 | 申请时机 | 拒绝兜底 |
|------|----------|----------|
| 麦克风 `RECORD_AUDIO` | 首次进入 AI 访谈屏（§3.1.2 入口） | 弹"需要麦克风权限才能写日记"+ 跳转系统设置；不进入访谈 |
| 相机 `CAMERA` | 家属点 §3.2.1「打开相机」 | 弹"需要相机权限"+ 跳转系统设置；返回未绑定态 |
| 通知 `POST_NOTIFICATIONS`（Android 13+） | 首启身份选择完成后 **5 秒** 延后（不在选择屏弹） | 提醒卡仍全屏弹出，但后台推送不到；不阻断（§I-6 决议：不补救常驻小提示） |
| 存储 | 不申请（MVP 不导出） | — |
| 位置 | 不申请（v2.0 §7.5） | — |

**§G.1 决议 29**：权限被永久拒绝后**不再二次弹引导**——依靠"跳系统设置"兜底；MVP 简洁。

权限弹窗文案按 v2.0 §4 第 5 条"无手势操作"——只点一次"允许/不允许"，不教学。

### 4.7 空状态

| 屏 | 空态文案 |
|----|----------|
| 提醒 Tab 整体 | "还没有提醒，点上面的 + 加一个" |
| 吃药分段 | "还没有吃药提醒" |
| 看医生分段 | "还没有就医提醒" |
| 日志 Tab | "还没有日志" |
| 日志详情空轮 | "本条日志没有语音" |
| 主屏提醒区（老人端） | "今日暂无提醒" |

所有空态配 TTS ▶ 按钮（24dp），点击播当前空态说明。

### 4.8 错误 Toast

| 维度 | 值 |
|------|----|
| 字号 | 24sp（v0.9.0：24→**28sp**，与 §4.2 最小可视字号 22sp 联动） |
| 时长 | 1.5 秒（v0.9.0：1.5→**4s**，给老人阅读时间） |
| 位置 | 底部，距底 96dp |
| 颜色 | 白字 / `#C44545` 底 |
| TTS | 同步播报错误文案一次（v0.9.0：增加 `VIBRATE` 短震 100ms 反馈；走 §A.13 Cantonese_KindWoman） |

常见文案：
- "网络好像断了，再试一次"
- "保存失败，再试一次"
- "服务器忙，过会儿再试"

4xx（客户端错误）走 Toast；5xx + 网络断开走 §4.9 黄条。

### 4.9 网络异常

顶部黄条（高 48dp，背景 `#FFF4E6`）出现于：
- 任意 `/v1/*` 请求 **5xx 或网络断开**（4xx 不挂黄条，走 §4.8 Toast）
- 文案："网络好像断了，正在重试…"24sp；右侧"重试"按钮 96×48dp
- 恢复后自动隐藏 + 短震

### 4.10 加载态

| 场景 | 表现 |
|------|------|
| 全屏加载 | 居中"加载中…" 24sp + 进度条（无菊花）（v0.9.0：24→**28sp**；进度条 8→**12dp**；v0.9.0 新增 `OPENING` 阶段变体"让我先打个招呼…" + 进度条） |
| 局部加载 | 占位骨架屏（灰块 + 主色淡边） |

**不出现任何 modal loading 遮罩**——会遮挡老人看到的内容。

---

## 5. 数据模型

> **v3.0 MVP 数据模型真源 = 本地 Room 表**（§5.10-§5.12）。§5.1-§5.9 服务端集合仅在 v2.x 接回服务端后存在，v3.0 MVP 全部延迟。MVP 没有任何 CloudBase NoSQL / COS 依赖——音频文件留在本地 `cacheDir/audio/`。
>
> 数据迁移原则：v2.x 接回服务端时，§5.10-§5.12 本地数据全量上传到对应服务端集合（`diary_entry` / `reminder` 等），迁移脚本在 `services/account_service/migrations/v2_x.py`。

### 5.1 `family_user`（家属账号）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键（**v3.0 MVP-DEFER → v2.x**，本表 v3.0 不创建） |
| phone | string | 否 | 手机号（**v2.x 启用后**唯一；MVP 留空） |
| created_at | ISO datetime | 是 | |
| updated_at | ISO datetime | 是 | |

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合，v3.0 MVP 不创建。MVP 老人端仅在本地 Room 维护 `device_meta`（§5.12）。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `device_token` | string | 否 | UUID v4，client 首启生成；anonymous-device 流程唯一标识（**v2.x 启用后必填**之一；MVP 不创建——MVP 的设备标识见 §5.12 `device_meta.device_id`） |

### 5.2 `elder_profile`（老人档案）

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合，v3.0 MVP 不创建。MVP 老人端在本机 Room 用 `device_meta`（§5.12）替代本表所有功能。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| name | string | 是 | 称呼 |
| device_token | string | 否 | 推送 token（唯一） |
| timezone | string | 是 | 设备本地时区（IANA 名，如 `Asia/Shanghai`） |
| created_at | ISO datetime | 是 | |
| updated_at | ISO datetime | 是 | |

### 5.3 `binding`（家属-老人绑定关系）

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合，v3.0 MVP 不创建。MVP 无家属端，无绑定关系。

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

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合，v3.0 MVP 不创建。MVP 老人端无提醒（§3.1.1 / §3.1.3 全推迟）。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| elder_id | UUID | 是 | 关联老人 |
| type | enum | 是 | `medication` / `appointment` |
| channel_priority | enum | 否 | `mid` / `high`；默认 `mid`；创建时按 `type` 自动设：`medication → mid`，`appointment → high`（§11.18） |
| payload | object | 是 | 见下 |
| status | enum | 是 | `active` / `paused` |
| created_by | UUID | 是 | 家属 user id（family_user.id） |
| created_at | ISO datetime | 是 | |
| updated_at | ISO datetime | 是 | |

`payload` 内嵌：
- `medication`：`{ med_name: string, dosage: { type: enum [pill|half|spoon], note?: string(≤100) }, schedule: { time: 'HH:mm', repeat: 'daily' | 'weekdays' | 'custom', weekdays?: number[] }[], note?: string(≤100) }`
- `appointment`：`{ hospital: string(1-20), department: string(1-20), datetime: ISO datetime, advance_remind_min: enum [30|60|120] = 60, repeat: enum [none] | null, note?: string(≤100) }`

索引建议：`(elder_id, type, status, channel_priority)`。

### 5.5 `reminder_ack`（提醒应答）

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合，v3.0 MVP 不创建。

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

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合，v3.0 MVP 不创建。MVP 无 Agent 多轮 turn 流；单次录音直接落 `diary_entry_local`（§5.11）。

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

### 5.7 `diary_entry`（日志；**v3.0 MVP-DEFER → v2.x** 服务端版本）

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合（含 Agent 整理后的 `text` / `summary` / `audio_segments[]`）。v3.0 MVP 用本地简化版 §5.11 `diary_entry_local`，不含 `summary` / `audio_segments[]`。
>
> v2.x 启用条件：服务端 agent-service + LLM 摘要落地；本地 §5.11 数据全量同步到本表（`elder_id` 由 §5.12 `device_meta.device_id` 映射为服务端 UUID）。

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

> **v3.0 MVP-DEFER → v2.x**：v3.0 MVP 不上传任何 COS 文件，音频留本地 `cacheDir/audio/`。下表 Key 约定仅在 v2.x 接回服务端代理时使用。

- 老人语音（永久）：`elder/{elder_id}/diary/{diary_id}/seg-{n}.m4a`
- Agent 回复语音（永久）：`elder/{elder_id}/diary/{diary_id}/reply-{n}.m4a`
- 临时上传（24h 生命周期，见 §11.13）：`tmp/{elder_id}/{uuid}.m4a`

### 5.9 `bind_attempt`（绑定尝试，v2.1.1 新增；**v3.0 MVP-DEFER → v2.x**）

> **v3.0 MVP-DEFER → v2.x**：本表为 v2.x 服务端集合，v3.0 MVP 不创建。MVP 无绑定流程。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | UUID | 是 | 主键 |
| bind_code | string | 是 | 临时码（5 分钟有效，§11.13），唯一索引 |
| family_user_id | UUID | 是 | 发起邀请的家属 user id（→ family_user.id） |
| elder_device_token | string | 是 | 老人设备 token（用于校验来源，§3.2.7） |
| status | enum | 是 | `pending` / `confirmed` / `rejected` / `expired` |
| elder_id | UUID | 否 | 确认后回填，关联 elder_profile.id（→ §5.2） |
| binding_id | UUID | 否 | 确认后回填，关联 binding.id（→ §5.3） |
| created_at | ISO datetime | 是 | |
| responded_at | ISO datetime | 否 | 老人端 accept / reject 时间 |

索引建议：
- `bind_code`（唯一）
- `(elder_device_token, status, created_at)` —— 支持 §3.2.7 老人端 10s/次轮询

### 5.10 `diary_entry_local`（v3.0 MVP 本地 Room 表）

> **v3.0 MVP 本地表**——§3.1.2 单次录音 → ASR → 本地日记的落库目标。v2.x 接回服务端时，全量同步到 §5.7 服务端 `diary_entry`（保留 `text` / `audio_path`，丢弃 `source` 调试字段）。
>
> **0.5.0 目标**：`text ≤ 100` 汉字、`summary ≤ 60` 汉字；新增 `summary` 与 Agent 访谈关联字段需要 Room migration。历史 v3.0.1 数据的 200 字正文不截断，仅对新写入的 0.5.0 Agent 日记执行 100 字限制。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | INTEGER | 是 | 主键，自增 |
| `device_id` | TEXT | 是 | 本机设备标识（→ §5.12 `device_meta.device_id`，MVP 固定字符串 `"local"`；v2.x 接回服务端时映射为服务端 UUID） |
| `date` | TEXT (YYYY-MM-DD) | 是 | 按设备本地时区（用于 §3.1.7 今日记录图标点亮判断） |
| `text` | TEXT | 是 | 日记正文（≤ 200 字，v3.0 放宽到 200——MVP 老人手动改写入口限 200，v2.x 接服务端 LLM 摘要后收紧到 100） |
| `transcript` | TEXT | 否 | ASR 转写原文（≤ 500 字；用于"重听+改写"功能，v3.0 不可见） |
| `source` | enum | 是 | `asr_original`（ASR 转写未改）/ `asr_edited`（老人手动改写过）/ `manual`（纯手动写） |
| `audio_path` | TEXT | 是 | 本机音频文件路径（相对 `cacheDir/audio/`，例 `{uuid}.m4a`）；v2.x 接服务端时上传到 COS 后该字段保留本地缓存路径 |
| `duration_ms` | INTEGER | 是 | 录音时长（毫秒） |
| `asr_provider` | TEXT | 否 | 当时调用的 ASR Provider（`dashscope` / `whisper_openai` / `custom`），用于排查 ASR 质量 |
| `asr_model` | TEXT | 否 | 当时的 ASR model 名（如 `paraformer-v2`） |
| `asr_confidence` | REAL | 否 | ASR 返回置信度 [0, 1]；< 0.6 时客户端标红但仍保存（§3.1.2 异常处理） |
| `asr_latency_ms` | INTEGER | 否 | ASR 调用往返耗时（毫秒） |
| `created_at` | INTEGER | 是 | epoch ms |
| `updated_at` | INTEGER | 是 | epoch ms；手动改写时更新 |
| `deleted_at` | INTEGER | 否 | epoch ms；MVP 软删除兜底（§K.2f 老人端不提供删除入口，但 Room 级清理 / 卸载仍按软删可恢复） |

索引建议：
- `(date, created_at)` —— 支持 §3.1.7 今日记录图标点亮 + §3.1.7 时间轴屏按日期分组
- `(device_id)` —— 支持 v2.x 全量同步

### 5.11 `asr_config`（v3.0 MVP 本地 Room 表）

> **v3.0 MVP 本地表**——§3.1.9 ASR API 配置页的落库目标。**单行表**（固定 `id=1`），MVP 只允许一份配置。
>
> **v3.0.1 修订**（§A.1.b）：客户端 ASR 上游单一化为阿里云百炼 `Qwen-Audio-3.0-ASR-Flash-Streaming`，`WorkspaceId` 与 `model` 硬编码进客户端代码；本地表只保留 `api_key_enc`。迁移脚本 `MIGRATION_1_2` 直接 `DROP TABLE asr_config` 重建，强制用户重新输入 API Key。
>
> **0.7.0 扩展**：列扩到 16 列，新增 Provider 切换 + MiniMax TTS 独立 Key。`asr_provider` 默认 `minimax_realtime` / `tts_provider` 默认 `minimax`；Migration `4→5` 走 `DROP TABLE asr_config` + `CREATE TABLE`，强制老人重输 4 份 Key（`api_key_enc` 千问共用 / `minimax_api_key_enc` MiniMax LLM+ASR 共用 / `tts_minimax_api_key_enc` MiniMax TTS 独立）。**0.5.0 扩展**：`api_key_enc` 供千问 ASR/TTS 共用；新增 `minimax_api_key_enc` / `minimax_last_test_result`。Migration `2→3` 保留旧千问 Key，只新增 nullable 列。
>
> **v0.8.0 扩展**：列扩到 23 列，新增 LLM Provider 切换 + 千问 LLM 独立 Key + DeepSeek LLM Key。`llm_provider` 默认 `minimax`；可选 `qwen` / `deepseek`。Migration `5→6` 走 `ALTER TABLE` 新增 7 列（`llm_provider` / `llm_endpoint` / `llm_model` / `qwen_llm_api_key_enc` / `qwen_llm_last_test_result` / `deepseek_llm_api_key_enc` / `deepseek_llm_last_test_result`），不 drop 既有数据；首次进入 v0.8.0 时 LLM 端会提示「AI 服务 → 大语言模型」补填 Key。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | INTEGER | 是 | 主键，固定值 1 |
| `api_key_enc` | TEXT | 是 | **Keystore-wrapped 密文**（EncryptedSharedPreferences + Android Keystore AES/GCM；客户端不持有明文） |
| `updated_at` | INTEGER | 是 | epoch ms |
| `last_tested_at` | INTEGER | 否 | epoch ms；最后一次"测试一下"按钮调用时间 |
| `last_test_result` | TEXT | 否 | JSON：`{"text": "...", "latency_ms": 1234}` 或 `{"error": "..."}` |
| `minimax_api_key_enc` | TEXT | 否 | MiniMax M3 Key 的 Keystore token；新增用户必填 |
| `minimax_last_test_result` | TEXT | 否 | MiniMax 测试结果 JSON |
| `asr_provider` | TEXT | 是 | 默认 `minimax_realtime`；可选 `bailian` |
| `asr_endpoint` | TEXT | 否 | Provider 硬编码 endpoint（写库常量） |
| `asr_model` | TEXT | 否 | Provider 硬编码 model（写库常量） |
| `tts_provider` | TEXT | 是 | 默认 `minimax`；可选 `qwen` |
| `tts_endpoint` | TEXT | 否 | Provider 硬编码 endpoint |
| `tts_model` | TEXT | 否 | Provider 硬编码 model |
| `tts_voice_id` | TEXT | 否 | MiniMax TTS 写 `Cantonese_KindWoman`；千问 TTS 留空 |
| `tts_minimax_api_key_enc` | TEXT | 否 | MiniMax TTS 独立 Key 的 Keystore token；与 LLM/ASR 区分 |
| `tts_minimax_last_test_result` | TEXT | 否 | MiniMax TTS 测试结果 JSON |
| `llm_provider` | TEXT | 是 | 默认 `minimax`；可选 `qwen` / `deepseek` |
| `llm_endpoint` | TEXT | 否 | Provider 硬编码 endpoint（写库常量） |
| `llm_model` | TEXT | 否 | Provider 硬编码 model |
| `qwen_llm_api_key_enc` | TEXT | 否 | 千问 LLM Key 密文；独立于 ASR/TTS 共用 Key |
| `qwen_llm_last_test_result` | TEXT | 否 | 千问 LLM 测试结果 JSON |
| `deepseek_llm_api_key_enc` | TEXT | 否 | DeepSeek LLM Key 密文 |
| `deepseek_llm_last_test_result` | TEXT | 否 | DeepSeek LLM 测试结果 JSON |


索引建议：单行表，无额外索引。

### 5.12 `device_meta`（v3.0 MVP 本机元数据）

> **v3.0 MVP 本地表**——替代 §5.2 `elder_profile` 的本机版本。MVP 只在本机维护设备维度元数据，不上传服务端。v2.x 接回服务端时，对应到 `elder_profile` 服务端集合。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | INTEGER | 是 | 主键，固定值 1 |
| `device_id` | TEXT | 是 | 本机 UUID v4（首启生成，存 SharedPreferences）；v2.x 映射为服务端 `elder_id` |
| `device_token` | TEXT | 否 | 预留给 v2.x `family_user.device_token` 字段；MVP 留空 |
| `font_scale` | enum | 是 | `default` / `large` / `xlarge`（§3.1.8 第 1 项） |
| `tts_enabled` | INTEGER (bool) | 是 | §3.1.8 第 3 项；默认 1 |
| `timezone` | TEXT | 是 | 设备本地时区（IANA 名，如 `Asia/Shanghai`），首启动态读取 |
| `app_version` | TEXT | 是 | 当前 App 版本号（BuildConfig.VERSION_NAME） |
| `first_launch_at` | INTEGER | 是 | epoch ms；首启时间，用于统计 |
| `last_active_at` | INTEGER | 是 | epoch ms；最近一次进入主屏，用于统计 |

索引建议：单行表，无额外索引。

---

## 6. API 契约

> **v3.0 MVP-DEFER → v2.x 整章**：本节所有 HTTP 端点 / 鉴权 / 错误码均为 v2.x 服务端契约，**v3.0 MVP 不实现**——MVP 老人端**没有任何自建服务端依赖**（§1.3）。老人端唯一外部 HTTP 依赖是 §3.1.9 配置的第三方大模型 ASR endpoint，**不走本节契约**。
>
> v2.x 启用条件：服务端 agent-service + reminder-service + account-service 三个独立服务上线 + 客户端接入；按 §6.1 / §6.2 / §6.3 现描述实现。
>
> MVP 客户端**本地**错误码见 §6.4（v3.0 新增）。

### 6.1 端点清单

| 端点 | 方法 | 鉴权角色 | 限流 | 说明 |
|------|------|----------|------|------|
| `/v1/auth/sms-code` | POST | 公开 | 60s 1 次 / 手机号 | 发送短信验证码 |
| `/v1/auth/anonymous-device` | POST | 公开 | 60s 1 次 / device_token | **v2.1.2 新增**：device_token 静默注册 / 登录，返回 JWT；MVP 阶段唯一鉴权端点；v2.x 接 SMS 后保留作为降级路径 |
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
| `/v1/bind/elder` | POST | family | — | **v2.1 修订**：扫码后家属 → 老人确认通过，调用此端点 |
| `/v1/bind/confirm` | POST | elder | 10s 1 次 / bind_code | **v2.1 新增（§F.4）**：老人点同意 / 拒绝 → 服务端事务 |
| `/v1/bind/pending` | GET | elder | 10s 1 次轮询 | **v2.1 新增（§F.3）**：老人轮询待绑定列表（队列模式）|
| `/v1/diary/flush-pending` | POST | elder | 1 次 / 绑定成功 30s | **v2.1 新增（§F.5）**：绑定成功后 flush_pending_diaries() 批量同步 |

### 6.2 错误码全集

| HTTP | code | 含义 |
|------|------|------|
| 400 | `BAD_REQUEST` | 请求格式错误（非 schema 校验失败） |
| 401 | `UNAUTHORIZED` | JWT 无效 / 缺失 / 过期 |
| 403 | `FORBIDDEN` | 角色不符（如 elder 调 family-only、secondary 调解绑） |
| 404 | `NOT_FOUND` | 资源不存在（session / diary / reminder） |
| 409 | `CONFLICT` | 资源状态冲突（重复 finalize、重复绑定、并发写入） |
| 422 | `VALIDATION_ERROR` | schema 校验失败；响应 body 含 `field` 错误路径 |
| 422 | `REMINDER_TIME_PAST` | **v2.1 新增**：reminder 时间 ≤ 现在（含提前提醒分钟数校验，§3.2.4 / §K.3c） |
| 422 | `BIND_CODE_EXPIRED` | **v2.1 新增**：bind_code 已过期（5 分钟后）或被拒绝后失效（§F.3 决议 22-26） |
| 422 | `BIND_ATTEMPT_NOT_FOUND` | **v2.1 新增**：bind_attempt 不存在或已被响应 |
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

#### `POST /v1/bind/confirm`（v2.1 新增，§F.4 决议 25-28）
请求：`{ "bind_code": "abc123", "elder_name": "王秀英", "decision": "accept" | "reject" }`
响应（accept）：
```json
{ "elder_id": "uuid", "binding_id": "uuid", "role": "primary|secondary" }
```
响应（reject）：`204 No Content`
副作用（accept 事务）：
- 创建 `elder_profile`（含 `name`、可选 `phone`、`device_token`、`timezone`）
- 创建 `binding`（`role=primary`，若已有 primary 则降级 secondary）
- 标记 `bind_attempt.status = confirmed`
- 失败回滚

#### `GET /v1/bind/pending`（v2.1 新增，§F.3 决议 23-24）
响应：
```json
{ "pending": [
    { "bind_attempt_id": "uuid", "family_user_id": "uuid",
      "family_user_name": "小明", "family_user_phone_tail": "8888",
      "bind_code": "abc123", "created_at": "..." }
] }
```
- 队列模式（§F.3 决议 23）：服务端返回所有未决议的 bind_attempt，按 created_at 倒序
- 老人端按队列顺序弹单卡确认（§3.2.8）

#### `POST /v1/diary/flush-pending`（v2.1 新增，§F.5 / §H.24）
请求：
```json
{ "pending_diaries": [
    { "pending_id": "client-uuid",
      "audio_cos_key": "elder/{id}/tmp/{uuid}.m4a",
      "turns": [ { "elder_text": "...", "elder_audio_cos_key": "..." } ],
      "text": "今天和老张下了棋，赢了",
      "summary": "和老张下棋，赢了" }
] }
```
响应：
```json
{ "synced": [ { "pending_id": "...", "diary_id": "uuid" } ],
  "dropped": [ { "pending_id": "...", "reason": "audio_cos_key expired (>24h)" } ] }
```
- `pending_id` 用于去重（重复 flush 同 `pending_id` 服务端幂等）
- 24h 后 audio_cos_key 过期的 drop 但保留 `text + summary`（避免完全丢失）

### 6.4 MVP 客户端错误码（v3.0 新增；0.5.0 扩展）

> MVP 客户端**没有自建服务端**，仅与第三方 ASR endpoint 通信。客户端错误码统一在 Kotlin 端用 `AppError` / `ElderError` 类承载，UI 层根据错误码定位提示文案。**不**走 §6.2 服务端错误码格式（无 HTTP status 概念）。

| 客户端 code | 触发场景 | UI 提示（24sp 主色 + 24sp 灰副） | 兜底动作 |
|-------------|----------|------------------------------------|----------|
| `ASR_AUTH_FAILED` | 千问 ASR 返回 401/403 | 「API Key 不对」+「去设置 → AI 服务 检查」 | 不重试；高亮"配置有误"红条 5 秒 |
| `ASR_RATE_LIMITED` | 第三方 ASR 返回 429 | 「太快了，等等再试」 | 5 秒后自动重试 1 次 |
| `ASR_BAD_REQUEST` | 第三方 ASR 返回 4xx（其他） | 「配置有误，去设置检查」 | 不重试 |
| `ASR_UPSTREAM` | 第三方 ASR 返回 5xx / 超时 | 「对方服务器没响应」 | 弹"再试一次"按钮 |
| `ASR_RESPONSE_INVALID` | 第三方 ASR 返回 JSON 解析失败 | 「对方返回看不懂」 | 弹"再试一次"按钮 |
| `ASR_NOT_CONFIGURED` | §5.11 `asr_config` 缺少千问或 MiniMax Key | 「请先在设置 → AI 服务 配置 Key」 | Toast + 红点提示，不弹窗强制 |
| `ASR_EMPTY_TRANSCRIPT` | 第三方 ASR 返回 `text` 为空字符串 | 「没听清，再说一次」 | 自动回录音重新开始（最多 3 次） |
| `LLM_AUTH_FAILED` | MiniMax 返回 401/403 | 「MiniMax Key 不对」 | 不重试；提示去 AI 服务检查 |
| `LLM_RATE_LIMITED` | MiniMax 返回 429 | 「AI 太忙了，等等再试」 | 指数退避后重试，最多 3 次 |
| `LLM_UPSTREAM` | MiniMax 超时 / 5xx / SSE 断流 | 「AI 没回答上来」 | 最多重试 3 次；全部失败后进入离线补做 |
| `TTS_AUTH_FAILED` | 千问 TTS 鉴权失败 | 「语音 Key 不对」 | 保留文字回复，不判对话失败 |
| `TTS_ASR_AUTH_FAILED` | MiniMax Realtime ASR 返回 401/403 | 「API Key 不对」 | UI 区分 Provider 来源；不重试 |
| `TTS_ASR_RATE_LIMITED` | MiniMax Realtime ASR 返回 429 | 「太快了，等等再试」 | 5 秒后自动重试 1 次 |
| `TTS_ASR_UPSTREAM` | MiniMax Realtime ASR 返回 5xx / 超时 | 「对方服务器没响应」 | 弹"再试一次"按钮 |
| `TTS_UPSTREAM` | TTS WebSocket / PCM 播放失败 | 「语音暂时说不出来」 | 只展示 `assistant_text`，继续下一轮 |
| `RECORDING_PERMISSION_DENIED` | MediaRecorder 启动时缺 RECORD_AUDIO 权限 | 「需要麦克风权限才能写日志」 | 弹跳转系统设置按钮 |
| `RECORDING_FAILED` | MediaRecorder 启动 / 写入失败 | 「录音没成功，再试一次」 | 弹"再试一次"按钮 |
| `NETWORK_UNAVAILABLE` | 检测到无网（§3.1.2 网络断开 UX） | 「网络不通，请检查 Wi-Fi」 | 弹"再试一次"按钮 |
| `STORAGE_FULL` | cacheDir 写入失败（磁盘满） | 「手机存储满了」 | Toast + 红点提示，录音屏拒绝进入 |
| `ROOM_CORRUPTED` | Room 打开失败 / 迁移失败 | 「本地数据损坏」 | 弹"重置数据"按钮（清 Room + 跳 §3.1.8 退出登录） |
| `UNKNOWN` | 兜底 | 「出了点小问题，再试一次」 | 弹"再试一次"按钮 |

---

## 7. 安全与隐私

> **v3.0 范围**：§7.1 传输层 + §7.2 存储加密（部分）+ §7.5 隐私原则（精简版）在 MVP 适用；其余子节（§7.3 端到端加密 / §7.4 鉴权 / §7.6 数据保留 / §7.7 审计）均为 v2.x 服务端相关，v3.0 MVP-DEFER。

### 7.1 传输层
- MVP 客户端 → 第三方 ASR endpoint 强制 HTTPS（§3.1.9 dev 模式除外）；HTTP 明文 endpoint prod 拒绝（§3.1.9 Provider 校验）
- MVP **没有**客户端到自建服务端——本节"客户端到服务端"条款不适用
- v2.x 启用条件：服务端上线后追加"客户端到服务端强制 TLS 1.3；内部服务间走腾讯云内网"原条款

### 7.2 存储加密

> **v3.0 MVP 范围**（保留）

- **本机 API key**（§5.11 `asr_config.api_key_enc`）：**EncryptedSharedPreferences + Android Keystore AES/GCM**——落盘为 Keystore-wrapped 密文，客户端**永远不持有明文**；运行时由 Keystore 解密，引用置 null + GC 后清内存（§3.1.9 安全边界）
- **本机日记正文**（§5.10 `diary_entry_local.text`）：**MVP 不加密**——MVP 本地数据库视为可信环境；v2.x 接服务端后改为服务端落盘前加密 + 客户端 AES 解密（密钥由家属配）
- **本机音频**（`cacheDir/audio/*.m4a`）：**MVP 不加密**——文件系统权限兜底（Android 沙箱）；v2.x 接服务端后改为录音时实时 AES-256-CBC 加密（密钥派生自 elder_id）
- COS 对象存储强制启用 SSE-KMS（腾讯云托管密钥）—— **v3.0 MVP-DEFER → v2.x**
- CloudBase 静态加密（腾讯云默认开启）—— **v3.0 MVP-DEFER → v2.x**

### 7.3 端到端加密

> **v3.0 MVP-DEFER → v2.x**：本节条款为 v2.x 服务端相关，v3.0 MVP 无服务端、无家属共享密钥、无 Agent 处理链，**整体不适用**。v2.x 启用后按原"MVP 不启用端到端加密"描述实现。

### 7.4 鉴权

> **v3.0 MVP-DEFER → v2.x**：MVP **没有任何鉴权**——没有 JWT、没有服务端、没有鉴权装饰器（§13 / AGENT.md §13 不适用）。MVP 客户端单端运行，本机数据视为可信。
>
> v2.x 启用条件：服务端上线 + 客户端接入 JWT 鉴权；按原"JWT HS256 / refresh token / 角色校验"描述实现。MVP 期间不需要实现 refresh token / JWT 持久化等基础设施。

### 7.5 隐私原则

> **v3.0 MVP 范围**（保留 + 精简）

- **最小化**：客户端**只**申请 RECORD_AUDIO + INTERNET 两个权限；不读联系人 / 相册 / 定位 / 通讯录 / 短信
- **可解释**：本机 Room 表字段全部列在 §5.10 / §5.11 / §5.12；客户端**不**新建 §5 之外的字段；不向任何服务端字段写入
- **客户端位置采集**：MVP 不采集 GPS；时区取 `TimeZone.getDefault()`（即设备系统时区，见 §5.12 `device_meta.timezone`）
- **Agent 最小化**（v3.0 MVP-DEFER → v2.x）：v2.x Agent 不采集与日志整理无关的信息（不读联系人、相册、定位等）
- **日志查询接口可解释**（v3.0 MVP-DEFER → v2.x）：v2.x 接回服务端后，日志查询接口返回字段在 §5.7 全部列清

### 7.6 数据保留

> **v3.0 MVP-DEFER → v2.x**：本节条款为 v2.x 服务端相关。v3.0 MVP 数据保留策略：
>
> - 日记：跟随 §5.10 `diary_entry_local.deleted_at` 软删除；用户主动点"退出登录"（§3.1.8 第 6 项）→ 清 Room 全表；用户卸载 App → 系统清 cacheDir + App 私有 Room
> - 音频：跟随 §5.10 `audio_path`；MVP 不上传 COS，仅本地 cacheDir；App 清理 cache 时一并清
> - ASR 配置：跟随 §5.11 `asr_config`；退出登录一并清
> - 设备元数据：跟随 §5.12 `device_meta`；首启生成，卸载丢失
>
> v2.x 接回服务端后，按原"日志永久保留 / 临时上传 24h 删 / 家属解绑不删"描述实现。

### 7.7 审计

> **v3.0 MVP-DEFER → v2.x**：MVP **没有 CLS / 没有审计日志**——客户端仅在本地 Logcat 输出 structlog，不上传任何服务端日志通道。
>
> v2.x 启用条件：服务端 agent-service / reminder-service / account-service 上线 + CLS 日志采集；按原"鉴权失败 / bind / reminder 写 / diary 读"敏感端点审计日志实现。

---

## 8. 部署与基础设施

> **v3.0 MVP-DEFER → v2.x 整章**：本节所有条款（服务拆分 / 腾讯云 SCF/CVM/CloudBase/COS/TPush/SMS/CLS/CAT/灰度）均为 v2.x 服务端基础设施，**v3.0 MVP 不部署任何自建服务端**。
>
> v3.0 MVP 部署形态：**只发布 Android App**（Google Play / 应用市场 / APK 直装）；后端仅需第三方大模型 ASR endpoint（§3.1.9）。
>
> v2.x 启用条件：服务端三个独立服务上线 + 客户端接入；按 §8.1 / §8.2 / §8.3 现描述实现。

### 8.1 服务拆分（已锁定）
三个独立 HTTP 服务（仅 agent-service 使用 DeepSeek Harness SDK）：

> **0.5.0 修订**：上述 DSH 服务形态不再适用于 App 0.5.0。0.5.0 不部署 agent-service，Android 使用用户自填 DashScope / MiniMax Key 直连上游；具体职责以 D8-D12 为准。

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
> D1-D7 记录历史决策；App 0.5.0 以 D8-D12 为准，冲突时由 D8-D12 覆盖。

| ID | 决策点 | 选定方案 | 备注 |
|----|--------|----------|------|
| D1 | Android 客户端栈 | **原生 Kotlin** | 性能好、UI 自由度高、直接调系统 API |
| D2 | 后端存储 | **CloudBase (NoSQL) + COS** | 元数据进 CloudBase，语音进 COS |
| D3 | 语音能力 | **ASR + TTS 两个独立 API** | 与 LLM 分工明确 |
| D4 | 双端形态 | **单 App 切换身份** | 一份安装包，`role` 字段区分 |
| D5 | Android 是否使用 DSH 客户端 SDK | **不使用，服务端独占** | DSH 仅跑在 agent-service 内；Android 不引入 DSH 客户端包 |
| D8 | 0.5.0 Agent Runtime | **Android 本地** | 状态机、会话状态、关键词安全、本地工具均在 App 内；服务端不保存访谈状态 |
| D9 | 0.5.0 模型 API | **千问 Realtime ASR + MiniMax M3 + `qwen3-tts-flash-realtime` / `Kiki`** | 均为在线 API；TTS 使用 WebSocket + 24kHz mono 16-bit PCM |
| D10 | 0.5.0 上游访问 | **用户 BYOK 直连，无 Gateway** | DashScope Key 供 ASR/TTS，MiniMax Key 供 LLM；两份 Key 分别 Keystore 加密；Gateway 延后到后续版本 |
| D11 | 0.5.0 离线策略 | **不支持离线 Agent** | 断网只保留录音和稍后重试；可回退 v3.0.1 单次录音模式，不得伪装多轮访谈成功 |
| D12 | 0.5.0 DSH 依赖 | **不使用** | 0.5.0 不启动 DSH 或 Node 子进程；D5 仅描述历史服务端方案 |

---

## 10. 系统质量要求

> **v3.0 MVP 范围**：§10.1 性能 / §10.4 兼容性（部分）在 MVP 适用；其余子节（§10.2 限流 / §10.3 可用性 / §10.5 容量 / §10.6 可观测性）均为 v2.x 服务端相关，v3.0 MVP-DEFER。

### 10.1 性能

> **v3.0 MVP 范围**（保留 + 精简）

- 老人端冷启动到主屏 ≤ 3 秒（中端 Android 机型）
- 老人端冷启动到首次完成任意任务 ≤ 30 秒（**v3.0 任务 = 完成 1 篇日记**）
- **第三方 ASR 端到端响应** ≤ 8 秒（95 分位，含上传 + 解析）——v3.0 唯一网络耗时
- 日志列表查询（§3.1.7 时间轴屏）≤ 200ms（95 分位）——MVP 走 Room 单机查询
- Agent 单轮响应 ≤ 8 秒 / 日志列表查询 ≤ 500ms（v2.x 服务端性能）—— **v3.0 MVP-DEFER → v2.x**

### 10.2 限流

> **v3.0 MVP-DEFER → v2.x**：MVP **没有服务端限流**——客户端**没有**自建 HTTP 入口；唯一外部请求是 ASR endpoint（§3.1.9），由第三方 ASR 服务商自带限流策略。
>
> v2.x 启用条件：服务端上线 + 客户端接入；按原"通用 60 req/min / 关键端点专项 / Agent 访谈轮限"实现。

### 10.3 可用性

> **v3.0 MVP-DEFER → v2.x**：MVP **没有服务端可用性指标**——客户端可用性取决于第三方 ASR endpoint + 本机 Room；本机 Room 崩溃率 < 0.1% 即可。
>
> v2.x 启用条件：服务端上线后按原"Agent 服务月度可用性 ≥ 99.5% / CloudBase 与 COS 由腾讯云 SLA"实现。

### 10.4 兼容性

> **v3.0 MVP 范围**（保留 + 修订）

- Android 最低版本：API 26（Android 8.0）——见 §11.10（v3.0 沿用 v2.1 默认）
- 仅适配 Android 手机；不承诺平板 / 折叠屏
- **TTS 多语言（v3.0 修订）**：MVP **没有 TTS**（§3.1.2 / §4.5）——v2.x 启用后按原"千问 TTS 默认粤语男声"实现
- **ASR 策略（v3.0 修订）**：MVP 客户端直连**用户在 §3.1.9 自配**的 ASR endpoint（千问 DashScope / Whisper / 自定义 HTTP）；不强制服务端做"粤语主识别 + 普通话兜底"——方言能力由用户选的 Provider 决定；置信度 < 0.6 走 §3.1.2「没听清，再说一次」录音重试流程
- ASR 强制 HTTPS（§3.1.9 / §7.1）；HTTP endpoint 仅 dev 模式允许

### 10.5 容量

> **v3.0 MVP-DEFER → v2.x**：MVP **没有服务端容量指标**——本机 Room 容量按设备磁盘算；cacheDir 单日记音频 ≤ 100 KB / 60 秒（16 kHz / 单声道 / 64 kbps m4a）——单设备 ≤ 5000 篇日记 = ≤ 500 MB；远低于典型 Android 设备的 100 GB+ 可用空间。
>
> v2.x 启用条件：服务端上线后按原"单服务 1000 日活老人 / COS 单老人 ≤ 100 MB / 月"实现。

### 10.6 可观测性

> **v3.0 MVP-DEFER → v2.x**：MVP **没有 CLS / 没有服务端可观测性**——客户端仅本地 Logcat 输出 structlog；网络层（ASR 调用）记录 `latency_ms` / `provider` / `http_status` 到 §5.11 `asr_config.last_test_result` 字段，供设置页调试。
>
> v2.x 启用条件：服务端上线后按原"`/v1/*` 请求 CLS 结构化日志 / 业务事件 / UPSTREAM_* 错误率告警"实现。

---

## 11. 待澄清 / 默认值

> 每项标注当前默认值；变更需更新本节。

> **v3.0 范围声明**：§11.1-§11.26 中标记"已定"的项目中，**绝大多数为 v2.x 服务端相关，v3.0 MVP-DEFER**——MVP 范围内只有少量项目生效。MVP 实际生效的项目见下表 §11.28+。
>
> §11.27 §I 决议中：
> - **v3.0 沿用**：§I-1 色彩 token 不追加、§I-2 主屏 3 元素 → v3.0 简化为 2 元素、§I-3 兜底节奏 → MVP 无提醒、§I-4 剂量枚举 → MVP 无提醒、§I-6 通知权限 → MVP 无通知、§I-7 字体档位
> - **v3.0 跳过**：§I-5 日志搜索（无搜索）、§I-8 绑定方式（无绑定）、§I-9 未绑定前写日记暂存（无服务端）

| # | 项 | 当前默认值 | 状态 |
|---|----|-----------|------|
| 11.1 | 离线提醒应答缓存 | Android `Room` 临时表，联网后 POST `/v1/reminders/:id/ack` | 已定（§3.1.1） |
| 11.2 | 通知通道（提醒 / 老人未应答推家属） | **TPush** | 已定（§11.18 双通道） |
| 11.3 | 家属能否删日志 | **不能**（只读 + 配置权） | 已定 |
| 11.4 | 日志保留时长 | **永久** | 已定 |
| 11.5 | Agent 访谈轮数 | 默认 3–6 轮；硬上限 `MAX_TURNS=8` | 已定 |
| 11.6 | 多家属解绑 | 仅 `primary` 角色可解绑（`secondary` 无解绑权） | 已定（§3.2.8 / §11.25） |
| 11.7 | 端到端加密 | **否**（服务端可读以支持 Agent 处理） | 已定（§7.3） |
| 11.8 | 数据导出 | MVP **不做** | 已定 |
| 11.9 | 商业化 | **永久免费** | 已定 |
| 11.10 | Android 最低版本 | API 26（Android 8.0） | 已定（§11.10） |
| 11.11 | JWT 续签机制 | 单 JWT 7 天有效；过期客户端重新走 `/v1/auth/sms-code` 登录 | 已定（§7.4 / 无 refresh token） |
| 11.12 | 客户端位置采集 | MVP 不采集 GPS；时区取系统时区 | 已定（§7.5） |
| 11.13 | COS 临时对象生命周期 | 24h | 已定（§5.8 / §7.6） |
| 11.14 | 灰度发布策略 | 单户手动开启（`elder_id` 白名单，配置注入见 §8.3） | 已定（§8.3） |
| 11.15 | TTS 默认音色与语速 | 历史 v2.x 默认粤语男声；0.5.0 以 `qwen3-tts-flash-realtime` + `Kiki` 为准；**0.7.0 默认 `MiniMax` + `Cantonese_KindWoman`（MiniMax T2A WebSocket，§A.13）**，千问 `Kiki` 作为回滚路径保留 | 0.5.0 / 0.7.0 重定（§4.5 / §3.1.9） |
| 11.16 | 老人端字体档位 sp 值 | **默认 24/32 → 大 28/38 → 特大 32/44**（body/title） | 已定（§H.16 / §I-7）|
| 11.17 | TTS 失败兜底 | **0.5.0 只展示文字并继续对话**；系统铃声仅保留为历史 v2.x 提醒场景 | 0.5.0 重定（§4.5） |
| 11.18 | 通知优先级 | 拆两条 TPush 通道：**服药 = 中优**（声音 + 通知栏，无全屏），**就医 = 高优**（绕过勿扰） | 已定（§H.18 / §3.1.1 §3.1.3）|
| 11.19 | 老人端绑定二维码内容 | QR 仅含 `bind_code`；归属信息靠 UI 标题/Logo/倒计时传达 | 已定（§H.19）|
| 11.20 | 老人重装 App 账号恢复 | **不支持恢复**；§3.1.8 第 6 项退出登录需加提示"再装此 App 需要家人重新绑定" | 已定（§H.20）|
| 11.21 | 离线应答同步冲突 | **服务端时间优先**——保留最早收到那条，后到的写另一状态 | 已定（§H.21）|
| 11.22 | 家属端跨时区提醒显示 | 家属端显示跟随**设备系统时区**，不标注；MVP 不处理老人移动场景 | 已定（§H.22）|
| 11.23 | 短信绑定降级 H5 | **短信绑定整体不进 MVP**，仅做扫码绑定（§3.2 / §I-8） | 已定（§H.23）|
| 11.24 | 老人未绑定前写日记暂存 | **本地 Room 暂存**（不上传匿名 bucket），绑定成功后 `flush_pending_diaries()` 批量同步 | 已定（§H.24 / §3.2.9）|
| 11.25 | 多家属同时邀请 | **时间最早成 primary**；后续降级 secondary，邀请仍需老人同意 | 已定（§H.25 / §3.2.8）|
| 11.26 | 「今日记录」图标点击 | 点开 → **当日日志时间轴**（TTS 重播每条）| 已定（§H.26 / §3.1.7）|

### 11.27 Round 6 评审新增决议（§I 9 项）

| # | 项 | 决议 |
|---|----|------|
| I-1 | §4.1 色彩 token 是否追加品牌色 | **A. 不追加**——Logo 沿用 `#4A7A4A` |
| I-2 | §3.1.5 主屏 3 元素是否含问候区 | **A. 含**——3 元素 = 问候 + 今日提醒卡 + 写日记按钮；隐藏设置入口不计 |
| I-3 | §3.1.1 5/30 分钟兜底节奏是否过密 | **A. 沿用**——v2.0 节奏不调；午睡场景列入 v2.2 复盘 |
| I-4 | §3.2.3 剂量"自定义剂量"是否需要 | **C. 不做自定义剂量**——仅 `1粒 / 半粒 / 1勺` 三选一，特殊量走备注补充；**联动修改 §3.1.1 半粒视觉** |
| I-5 | §3.2.5 日志筛选是否需要关键词搜索 | **A. 不做**——仅按日期浏览（§3.2.5） |
| I-6 | §4.6 通知权限延后 5 秒后是否要在主屏给常驻小提示 | **A. 不补救**——5 秒后无提示，错过等下次提醒再说 |
| I-7 | §3.1.8 字体档位 sp 值（§11.16） | **沿用 §11.16**：默认 24/32 → 大 28/38 → 特大 32/44，无需复审 |
| I-8 | §3.2.7 / §3.2.8 绑定方式是否同时上 MVP | **§3.2 仅支持扫码**（§11.23），手机号绑定推 v2.2 |
| I-9 | §3.2.9 未绑定前写日记暂存 | **进 MVP**：本地 Room 暂存 + 绑定后 `flush_pending_diaries()` 同步（§11.24），无需再议 |

### 11.28 v3.0 MVP 专用默认（取代 §11.1-§11.26 中所有服务端相关项目）

| # | 项 | v3.0 MVP 默认 | 状态 |
|---|----|---------------|------|
| 11.28.1 | **ASR API 配置** | 用户在 §3.1.9 自填 Provider / Endpoint / Key / Model；首次启动 §3.1.9 未配置时主屏 Toast + 红点提示，**不强制** | 已定 |
| 11.28.2 | **ASR Provider 列表** | 0.7.0 起 2 个：`dashscope_bailian` / `minimax_realtime`（默认）；历史 `dashscope` / `whisper_openai` / `custom` 标记 MVP-DEFER，0.7.0 不启用 | 0.7.0 修订（§3.1.9 / §A.12） |
| 11.28.3 | **录音规格** | MediaRecorder AAC/m4a；16 kHz / 单声道 / 64 kbps；60 秒硬限 | 已定（§3.1.2）|
| 11.28.4 | **日记正文上限** | 200 字（v3.0 放宽，v2.x 接服务端 LLM 后收紧到 100） | 已定（§5.10）|
| 11.28.5 | **ASR 置信度低处理** | < 0.6 时 §3.1.2 弹"没听清，再说一次"回录音重新开始；连续 3 次低 → 回主屏 | 已定（§3.1.2）|
| 11.28.6 | **未配 ASR 状态** | 主屏 A 区上方 24sp 红字一行提示 + 设置入口红点 | 已定（§3.1.5）|
| 11.28.7 | **退出登录** | 弹确认卡，含"日志只存在本机，退出后无法恢复"；同意后清 Room + 清 ASR 配置 + 跳身份选择屏 | 已定（§3.1.8）|
| 11.28.8 | **本机 Room 数据库** | 3 张表：`diary_entry_local`（§5.10） / `asr_config`（§5.11） / `device_meta`（§5.12） | 已定（§5）|
| 11.28.9 | **ASR key 加密** | EncryptedSharedPreferences + Android Keystore AES/GCM；明文引用置 null + GC | 已定（§3.1.9 / §7.2）|
| 11.28.10 | **第三方 ASR 端到端超时** | 30 秒 | 已定（§3.1.9 / §10.1）|
| 11.28.11 | **本机字体档位** | 默认 24/32 → 大 28/38 → 特大 32/44（body/title），§3.1.8 第 1 项切换 | 已定（§3.1.8，沿用 §11.16）|
| 11.28.12 | **本机商业化** | **永久免费**（沿用 §11.9） | 已定 |
| 11.28.13 | **本机隐私策略** | 仅申请 RECORD_AUDIO + INTERNET 两权限；不读联系人 / 相册 / 定位 | 已定（§7.5）|
| 11.28.14 | **本机首启生成** | `device_meta.device_id` UUID v4，落 SharedPreferences；`device_meta.timezone` 动态读 `TimeZone.getDefault()` | 已定（§5.12）|
| 11.28.15 | **日志无云端备份** | 卸载 App / 清 cacheDir 即丢失；§3.1.8 第 6 项退出登录兜底二次提示 | 已定（§0 / §3.1.8）|

---

### §A 0.7.0 新增上游引用（对应 AGENTS.md §A.12 / §A.13 / §18 例外）

> 0.7.0 引入两个客户端直属上游——**AGENTS §A.12 MiniMax Realtime ASR WebSocket**：客户端直连 `wss://api.minimax.cn/ws/v1/stt`；**AGENTS §A.13 MiniMax T2A WebSocket**：客户端直连 `wss://api.minimax.cn/ws/v1/t2a`，voice_id 硬编码 `Cantonese_KindWoman`。这两个客户端不在 `elder_common` 统一封装（沿用 0.5.0/0.6.0 直连架构）；AGENTS §18 「不要在客户端引入新上游 SDK」条款为它们开后门。

---

## 12. 路线图（MVP 之外）

> **v3.0 范围声明**：本节"路线图"重新组织为 **v2.x（接回服务端 + 家属端）** + **v3.x（增量功能）** 两块。v3.0 MVP 不在路线图范围内——MVP 已在 §0 / §3 落地。

### 12.1 v2.x 接回服务端 + 家属端（按优先级）

> v2.x 起点：服务端三个独立服务（agent-service + reminder-service + account-service）+ 客户端鉴权 + CloudBase + COS + TPush + CLS 全部上线；按 §3 / §5 / §6 / §7 / §8 / §10 中标记 `v3.0 MVP-DEFER → v2.x` 的条款逐条落地。

- **v2.1 服务端基线**（依赖度高，先做）：
  - CloudBase NoSQL 集合创建（§5.1-§5.7 / §5.9）；COS Bucket 创建 + 24h 生命周期规则（§5.8）
  - 服务端三服务拆 + 部署（§8.1 / §8.2）：agent-service / reminder-service / account-service
  - JWT 鉴权 + refresh token + 鉴权装饰器（§6.1 / §7.4）；`/v1/auth/anonymous-device` 实现（§11.11）
  - 老人端集成鉴权 + 服务端集合读写
  - 服药提醒全屏卡（§3.1.1）+ 就医提醒全屏卡（§3.1.3）服务端实现
  - 家属端 9 个子节全部实现（§3.2.1-§3.2.9）
  - 扫码绑定流程（§3.2.7）+ 老人侧确认卡（§3.2.8）+ bind 事务（AGENT.md §A.5）
  - 未绑定前写日记暂存 + `flush_pending_diaries`（§3.2.9）
- **v2.2 Agent 多轮访谈**（依赖 v2.1）：
  > **0.5.0 修订**：Agent 多轮访谈与 TTS 提前到 App 0.5.0，采用 Android 本地 Agent + 用户 BYOK 直连；本节 DSH 服务端方案仅作为历史路线，不再作为 0.5.0 的前置依赖。
  - Agent 服务端 + DeepSeek Harness SDK 部署（§8.1 / §8.2）；DSH tools schema（AGENT.md §11）
  - §3.1.4 Agent 行为约束 A1-D3 全部落地 + 服务端 system prompt（AGENT.md §11）
  - §3.1.6 访谈总结屏 + §3.2.6 日志详情 + TTS 粤语男声（§3.1.4 / §H.15 / §A.1）
  - 服务端 LLM 摘要生成（`diary_entry.text` ≤ 100 / `summary` ≤ 60，AGENT.md §11）
  - 日记正文长度从 200 收紧到 100（§5.10 / §11.28.4 v2.x 修订）
- **v2.3 服务端 ASR 代理**（隐私升级）：
  - 用户在 §3.1.9 的 ASR key 改存服务端（OAuth 流程），客户端只持 session
  - 音频文件上传前服务端 PII 脱敏（§3.1.2 已知 MVP 限制）
  - 老人音频明文不再出客户端（§7.2 v2.x 修订）
- **v2.4 增量**：
  - 短信验证码绑定（§H.23 推迟项；§11.11 SMS 启用）
  - 端到端加密家庭密钥（§11.7 / §7.3）
  - 多家属协作：邀请、副本编辑、secondary 配置权细化（§3.2.8 §H.25）
  - 数据导出 PDF / ZIP 打包（§11.8）
  - 灰度发布单户手动（§11.14 / §8.3）

### 12.2 v3.x 增量功能（MVP 已上线后的下一波）

- iOS / 鸿蒙适配（§1.3 非目标）
- 微信小程序（§1.3 非目标）
- 紧急呼救 / 跌倒检测
- 医疗问答 / AI 健康助手
- 转人工客服
- 老人端自主配置提醒（§1.3 非目标，v2.x 家属端独占）
- 多语言 / 方言 ASR 适配（粤语 / 闽南语 / 四川话等；MVP 仅依赖 Provider 能力）
- 路线导航（就医提醒 §3.1.3 §K.3f 推迟）
- 复诊周期 `repeat` 字段（§3.1.3 §K.3d；MVP 家属端不允许录入）

### 12.3 MVP 已知限制（v3.0 不解决，仅声明）

- **无家属端 / 无关怀回路**——老人看不到家人，家人也看不到老人（§0 / §3.2 整章 v2.x）
- **无云端备份 / 无换机恢复**——日记只在本机，卸载即丢失（§3.1.8 第 6 项二次提示兜底）
- **ASR key 在本机**——用户自填，截图有外泄风险；生产环境必须服务端代理（§3.1.9 安全边界 / §12.1 v2.3）
- **音频明文上送第三方 ASR**——隐私敏感场景（医疗 / 家庭对话）需自行评估（§3.1.9 已知限制）
- **60 秒硬限到时无 TTS"时间到"提示**（§3.1.2 录音规格；v2.x 接 Agent 流后补）
- **无服务端摘要**——MVP 日记就是 ASR 转写原文；老人手动改写（§5.10 `source` 字段）
- **无推送通道**——MVP 无 TPush 接入，锁屏态仅显示主屏（§3.1.5 边缘情况）

### 12.4 v0.9.0 增量（v0.8.x 之后；客户端体验升级）

> v0.9.0 主题:**让老人端 10 屏文字"看得清" + 把 1 个 Agent 拆成 3 个 + ChatAgent 主动开问**。
> 不动 §5 数据模型字段、§3.1.4 A1–D3、Room schema、既有 migration、`LlmCredentials / LlmClient / LlmClientFactory` 接口(§A.15 锁定);旧 `system_v1/v2/v3.txt` + `save_v1/v2/v3.txt` 全部保留只读。
> 详细设计见 `docs/0.9.0-goals.md` + `docs/0.9.0-chat-agent.md` + `docs/0.9.0-chat-agent-opening.md`。

#### 12.4.1 文字清晰度升级（§4.2 + §4.8 / §4.9 / §4.10 + 10 屏 + 4 系统组件）

- `Dimens.FontSize` 调整:`BodySmallSp` 18→**22**;`caption()` 20→**22**;`TitleLargeSp` 38→**40**(与 `BodyHugeSp` 合并);最小可视字号 = **22sp**;`BodyDefaultSp = 24` 仍为默认正文
- 行高 = 字号 × **1.4**;`TranscriptLineHeightSp` 48→**56**;主正文字重 `Normal`→**`Medium`**
- 老人端 10 屏字号统一提升:
  - 主屏:日期 24sp 次级→**28sp 主色**;问候语支持农历日期;ASR 红卡简化为单行动作
  - 录音屏:信纸字号 32→**40sp `BodyHugeSp`**;时长 `MM:SS`→`MM分SS秒`;行高 48→56sp
  - 访谈屏:**LLM 回复 28sp 次级单段（`TextSecondary` + `Normal`）** —— v0.9.1 修订（取消 v0.9.0 双段拆段 + 38sp 粗体主色 + `·` 分隔）；OPENING 阶段（`ChatAgent.open()` 主动开问）走同一规格；转写 38sp 粗体;**进度行 → 96dp 大圆环**;状态行加打字光标 `|`
  - 时间轴:列表项 24→28sp;行 72→96dp;时间改"今天 上午 9:30"相对时间;编辑弹窗 160→240dp
  - 设置:字体 chip 18→24sp;Switch 加"开/关"二字;媒体音量加"X/15"反馈
  - AI 服务:Provider 入口卡 28sp 统一;placeholder 24→28sp
- 4 系统组件:`ElderToast`(28sp + 震动 + TTS);`NetworkYellowBar`(28sp + 右上 X 关闭);`ElderEmptyState`(28sp + 64dp 插画占位 + 朗读按钮 96dp);`LoadingState`(28sp + 进度条 12dp + OPENING 变体)
- 国际化:**仅**对 v0.9.0 新增字符串(`interview_opening_status` / `interview_opening_fallback` / `safety_greeting_*` 三件套)新增 `values-en` 英文 + `values-zh-rHK` 粤语口语。既有字符串(`home_diary_button` / `home_settings_button` 等)粤语化 / 英文化不在 v0.9.0 范围,留待 v1.0.0 独立 PR(避免打破既有 Compose 测试断言)。UI 文字默认普通话;粤语口语仅作用于 LLM 输出 `assistant_text`(经 §A.13 MiniMax Cantonese_KindWoman TTS 承担)

#### 12.4.2 Agent 拆分 1 → 3（§3.1.4 + §A.11）

| 新 Agent | Prompt 文件 | 工具集 | 设计要点 |
|---------|------------|--------|---------|
| `ChatAgent`(主循环) | `chat_v1.txt`(新建,≤70 行 / ≤2500 token) | `ask_clarify / mark_dimension_covered / MOVE_ON` (3 个) | 从 `system_v3.txt` 抽 60 行;删 B/D/F 节工具说明;瘦身 30% |
| `SafetyAgent`(纯本地 object) | `safety_v1.txt`(新建,≤10 行空模板) | `save_diary / ask_clarify` (本地直接调) | **不上 LLM**;emergency / money / medical 关键词命中 → 固定 reply;省 1 次 LLM 调用 |
| `MemoryAgent`(后台) | `memory_v1.txt`(新建,≤30 行) | `remember_fact / search_memory` (2 个) | 走 Room,**不污染** ChatAgent 对话上下文;`MAX_BACKGROUND_FACTS=5` |
| `SaveAgent`(新,兼容老路径) | `save_v3.txt`(沿用) | 无工具 | `summarize()` 给 `PendingDiaryBackfill` 用;`saveDiary()` 走 save_v3 产 text≤100 / summary≤60 |

- `InterviewAgent.kt`(624 行) → 拆为 4 个新类;ServiceLocator / ViewModel / PendingDiaryBackfill 切到 ChatAgent / SaveAgent
- ChatAgent 暴露给 LLM 工具集:**6 → 3**;`save_diary` 收尾由 ViewModel 显式调 SaveAgent;`remember_fact / search_memory` 由 MemoryAgent 后台
- 既有 prompt 文件**全部保留只读**:`system_v1/v2/v3.txt` + `save_v1/v2/v3.txt`;运行版本由 `PROMPT_VERSION` 环境变量选
- 测试矩阵:既有 14 个 Agent 测试 + 新增 27 个 = **41 个 Agent 测试**;既有 37 个项目测试保持绿
- AGENTS.md 新增 §A.16(ChatAgent 拆分细则)+ §A.17(ChatAgent 主动开问);§11 / §18 约束不动

#### 12.4.3 ChatAgent 主动开问（§3.1.4 E1 / E6）

- 新增 `ChatAgent.open(credentials, timeOfDay, recentSummaries, elderFacts): String` 入口
- 新增 `InterviewStage.OPENING` 阶段(显示 `StatusRow("让我先打个招呼…")` + 进度条)
- 进入访谈屏时调 LLM 生成第一句问候(≤25 字,不分 ack/probe)
- 注入 `<recent-summaries>` + `<elder-facts>` 做 **E6 跨会话回扣**:"昨天你说起老张,今天又碰面了?"
- 时段敏感:`早上好 / 中午好 / 晚上好` 按本地时间切换(MORNING 5-11 / NOON 12-17 / EVENING 18-4)
- 粤语口语(对齐 §A.13 Cantonese_KindWoman TTS 音色)
- TTS 必播第一句(走 §A.13 / §A.1);LLM 失败走 `SafetyAgent.GREETING_FALLBACK(timeOfDay)` 静态兜底(**不调 LLM / 不调 TTS**)
- **第一句不计 turn 计数**;8 轮上限不缩短
- `chat_v1.txt` 加 §OPEN 模式段;`AgentPrompts` 接口拆为 `ChatPrompts / SafetyPrompts / MemoryPrompts / SavePrompts`
- 测试:`ChatAgentOpenTest` 5 用例(注入 recent_summaries / elder_facts / 时段 / LLM 失败 / 字数截断)+ `AgentPromptContractTest` 3 用例(prompt 头必含 `// 对应 prd.md §X.Y`)
- UX 时序:0ms OPENING + 进度条 → 1.5s TTS 播完 → 3.0s 屏显示 38sp 粗体问候 → 5.0s 老人自然接话

#### 12.4.4 v0.9.0 性能与不动契约

- 进入访谈屏到 `ChatAgent.open()` 返回 + TTS 播完 **P95 ≤ 3 秒**(LLM 单次 + TTS 首段 ~1.5s)
- OPENING 阶段 UI 不阻塞(进度条 + "让我先打个招呼…")
- LLM 失败兜底路径 < 200ms(本地字符串查表)
- emergency 路径省 1 次 LLM 调用
- **不动契约**(AGENTS.md §18):
  - 不改 §5 数据模型字段;不改 §3.1.4 A1–D3;不改 §3.1.2 录音规格
  - 不删既有 migration;不改 `LlmCredentials / LlmClient` 接口
  - 不删 `system_v1/v2/v3.txt` + `save_v1/v2/v3.txt`(只读)
  - 不为客户端引入新的上游 SDK(0.5.0 / 0.7.0 例外沿用)
  - 不缩短 8 轮访谈上限(第一句不计 turn)
  - §4.2 最小 24sp 起步硬约束保留(0.9.0 只追加列,不改约束语义)
- 交付物:10 屏 UI 调整 + 4 系统组件 + 3 个新 prompt + 4 个新 Agent 类 + 32 个新测试 = **46 个 Agent 测试**(从 14 → 46,翻 3 倍)

---

### 12.5 版本文档索引（v0.10.0 拆分；§12.5 §A.16.1）

> v0.10.0 commit 1 拆分 prd.md 1750 行 → 1726 行,把每个版本的设计/行为约束挪到 `docs/{version}.md`;prd.md §5 / §6 / §7 / §8 / §9 / §10 跨版本仍生效的章节保留。版本 md 文件命名规范:`v{X}.{Y}.{Z}-{slug}.md`。

| 版本 | 文件 | 一句话目的 |
|------|------|-----------|
| v2.1 | `docs/v2.1-archive.md` | 服务端版本归档（保留只读;git blame 审计） |
| v2.1.2 | `docs/v2.1.2-mvp-auth.md` | MVP 匿名设备认证（prd.md §5.1 `family_user.device_token` / §6.1 `/v1/auth/anonymous-device`） |
| v3.0 | `docs/v3.0-mvp.md` | MVP 范围重定（老人端独立运行;服务端/家属端推迟到 v2.x） |
| v3.0.1 | `docs/v3.0.1-bailian-asr.md` | 阿里百炼 Qwen-Audio-Realtime Android SDK 集成（§A.1.b / §A.8 整体重写） |
| v0.5.0 | `docs/v0.5.0-local-agent.md` | 本地 Agent 多轮访谈（§3.1.4 / §3.1.6 / §A.11） |
| v0.6.0 | `docs/v0.6.0-hermes.md` | Hermes 风格记忆层（§F1–F7;`elder_facts` 表 + Room SQL `LIKE '%query%'`） |
| v0.7.0 | `docs/v0.7.0-dual-provider.md` | ASR/TTS 双 Provider 可切换（§A.13 / §A.14） |
| v0.8.0 | `docs/v0.8.0-llm-provider.md` | LLM 三 Provider（§A.15;千问 / MiniMax / DeepSeek） |
| v0.9.0 | `docs/v0.9.0-display-clarity.md` | 10 屏文字清晰度 + Agent 拆分 1→3 + ChatAgent 主动开问（主索引;详见 `docs/v0.9.0-chat-agent.md` + `docs/v0.9.0-chat-agent-opening.md`） |
| v0.9.1 | `docs/v0.9.1-single-paragraph.md` | 单段回复（取消 v0.6.0 A6 拆段;ack/probe 合并为 assistantText 单字段） |
| **v0.10.0** | **`docs/v0.10.0.md`** | **本版本：文档瘦身 + TTS 收尾（1500ms）+ 第一句粤语 + 不限轮数 + 阿里云 OSS 同步** |
| **v0.11.0** | `docs/v0.11.0.md` | 本版本：设置保存方式（云/本地/双存） + 本地导出（diary_yyyyMMdd_HHmmss_xxxx.md/.m4a） + LLM 回复改顶栏 Toast + 老人语音提前结束（4 词粤语） |
| **v0.11.x** | `docs/v0.11.x-bugfix.md` | **bugfix 增量：保存后 LoadingState 全屏霸屏修复** —— InterviewScreen 用 SavingStatusRow 替换 LoadingState + saveDiary 加 sibling launch + invokeOnCompletion 兜底 + SaveExportRepository 加 withTimeoutOrNull(3s) 超时 + onEnter 加 try-catch |

- **本版本（v0.10.0）新引入**：迁移到 `docs/{version}.md` 命名规范（§A.16.1）
- **后续 PR 必做**：每个新版本需先在 `docs/` 下创建对应 `vX.Y.Z-*.md`,并在本表追加一行;不再往 `prd.md §12` 写增量（除跨版本章节 §5/§6/§7/§8/§9/§10）
- **保留旧文件**:`docs/v0.9.0-chat-agent-opening.md` + `docs/v0.9.0-chat-agent.md` 加 `<!-- superseded by v0.9.0-display-clarity.md -->` 注;git blame 审计可继续走
- **prd.md 目标行数**：≤ 700 行（当前 1736 → 后续按版本逐步瘦身;不在 v0.10.0 一次性切到 700）

---

> **维护说明**：PRD 是产品需求的唯一真源；AGENT.md 中所有产品规则、数据字段、错误码、行为约束必须能在本 PRD 中找到对应条款。
