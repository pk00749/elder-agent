# 老友（Android）—— MVP 客户端

> 老友是一款面向长者的 Android 应用。当前已交付 v0.4.0 / PRD v3.0.1：录音 → 阿里云百炼 ASR → 本地 Room 日记。
>
> 当前 v0.5.0 / PRD §0.1：Android 本地 Agent 多轮访谈；用户自填 Key 直连千问 ASR/TTS 与 MiniMax M3，TTS 使用 `qwen3-tts-flash-realtime` / `Kiki`。
> 详细 PRD 见 `prd.md`；实施规范见 `AGENTS.md`。

## 仓库结构

```
elder-agent/
├── prd.md                                # 产品需求（唯一真源；v3.0.1 基线 + v0.5.0 目标）
├── AGENTS.md                             # 代码实施规范
├── app/                                  # 老友 Android 客户端
│   ├── build.gradle.kts                  # compileSdk=34 / minSdk=26（§11.10）
│   ├── src/main/java/com/elder/
│   │   ├── MainActivity.kt               # 入口
│   │   ├── ElderApplication.kt           # 启动 ServiceLocator
│   │   ├── di/ServiceLocator.kt          # 手动 DI（避 Hilt 复杂度）
│   │   ├── nav/                          # AppNavGraph + Route
│   │   ├── audio/AudioRecorder.kt        # §3.1.2 MediaRecorder 16kHz/mono/64kbps m4a
│   │   ├── data/
│   │   │   ├── AsrConfigRepository.kt    # §5.11 asr_config 仓储
│   │   │   ├── DiaryRepository.kt        # §5.10 diary_entry_local 仓储
│   │   │   ├── DeviceMetaRepository.kt   # §5.12 device_meta 仓储
│   │   │   ├── crypto/ApiKeyCipher.kt    # §3.1.9 EncryptedSharedPreferences + Keystore
│   │   │   ├── asr/AsrApiClient.kt       # §A.8 DashScope WebSocket duplex
│   │   │   ├── llm/MiniMaxClient.kt      # 0.5.0 MiniMax M3 SSE + tool calls
│   │   │   ├── tts/QwenTtsClient.kt      # 0.5.0 qwen3-tts-flash-realtime / Kiki
│   │   │   ├── InterviewRepository.kt    # 0.5.0 本地访谈会话
│   │   │   ├── PendingDiary*.kt          # 0.5.0 断网录音与联网补做
│   │   │   └── db/                       # Room v3：diary / asr_config / device_meta / interview / pending
│   │   ├── agent/InterviewAgent.kt       # 0.5.0 本地 Agent、工具调用和重试
│   │   ├── screen/elder/                 # §3.1.5 主屏 / §3.1.2 录音屏 / §3.1.7 时间轴 / §3.1.8 设置
│   │   ├── screen/interview/             # 0.5.0 多轮访谈与总结
│   │   ├── screen/asr/                   # §3.1.9 ASR 配置页
│   │   ├── error/AppError.kt             # §6.4 MVP 客户端错误码
│   │   └── util/                          # LocalDate / Permissions
│   └── src/test/                          # 单元测试（Robolectric + JUnit + MockWebServer）
│   ├── design/                            # §4 设计 token（Dimens.kt + Color / FontSize / Spacing）
│   └── ui/                                # §A.3 公共组件（ElderToast / NetworkYellowBar / LoadingState / ElderEmptyState）
├── services/                             # v2.x 服务端（v3.0 MVP-DEFER → v2.x，保留不删）
├── packages/common/                      # v2.x 跨服务共享（同上保留）
├── _v2x_archive/                          # v2.x 客户端 / 资源归档
├── build.gradle.kts / settings.gradle.kts # Gradle 9.x + AGP 8.5.2 + Kotlin 2.0.21 + KSP
├── gradle.properties
└── docker-compose.yml                     # v2.x 本地 mock infra（MVP 不需要）
```

## 快速开始

```bash
# 1. 装 JDK 17+（项目跑在 Java 25 也兼容；Robolectric 4.13 + Java 25 cleanup 异常由 ElderRobolectricTestRunner 兜住）
# 2. Gradle 9.x 自动下载（gradle wrapper 已 pin 9.3.0）
# 3. 编译 debug APK
./gradlew :app:assembleDebug
# 4. 单测全跑
./gradlew :app:testDebugUnitTest
# 5. 安装到设备/模拟器
./gradlew :app:installDebug
```

可选在线联调（不配置 Key 时自动跳过，不进常规 CI）：

```bash
# 千问 ASR + qwen3-tts-flash-realtime / Kiki
./gradlew :app:testDebugUnitTest \
  -PDASHSCOPE_API_KEY=sk-xxx \
  --tests "com.elder.android.data.asr.AsrApiClientLiveTest" \
  --tests "com.elder.android.data.tts.QwenTtsClientLiveTest"

# MiniMax-M3 文本 + tool call
./gradlew :app:testDebugUnitTest \
  -PMINIMAX_API_KEY=sk-xxx \
  --tests "com.elder.android.data.llm.MiniMaxClientLiveTest"
```

实时 TTS 使用 `wss://dashscope.aliyuncs.com/api-ws/v1/realtime`，需要具备该模型权限的 DashScope Key；workspace ASR Key 不一定可用于该端点。

## MVP 范围（PRD v3.0.1 §0）

**在做的**：
- §3.1.2 单次录音 → ASR → 本地日记（`ElderDiaryRecordScreen` + `AsrApiClient` + `DiaryRepository`）
- §3.1.5 主屏（`ElderHomeScreen`）：问候 + "点击开始写日志"按钮
- §3.1.7 今日记录（`ElderDiaryRecentScreen`）：今天/近7天 切换 + 手动改写
- §3.1.8 设置（隐藏入口，主屏问候区连点 5 次进入）：字体大小 / 语音播报 / AI 服务 / 音量 / 关于 / 退出登录
- §3.1.9 ASR 配置页（`AsrConfigScreen`）：Provider 只读卡片（百炼 / Qwen-Audio-3.0-ASR-Flash-Streaming / workspaceId）+ API Key 输入 + 测试一下 + 顶部 ✓ 保存

**不在 MVP（PRD §3 / §12 推迟到 v2.x）**：家属端整章、扫码绑定、§3.1.1 服药提醒卡、§3.1.3 就医提醒卡、§3.1.4 Agent 多轮访谈、§3.1.6 总结屏、服务端 / CloudBase / COS / TPush / SMS / KMS、TTS 播报。

## 关键约定

- 中文 docstring / 注释（AGENTS.md §6）
- Room v2 + Compose Material3 + AndroidX Lifecycle/Activity/Navigation（无 Hilt，无 Retrofit）
- 错误统一走 `AppError`（§6.4 客户端错误码 13 条）
- ASR key 走 EncryptedSharedPreferences + Keystore AES/GCM（§7.2 / §3.1.9）
- Robolectric 4.13 + Java 25 + `ElderRobolectricTestRunner` 兜住 Sonatype android-all 升级带来的 CookieManager cleanup 异常
- `org.json:json:20240303` 走 testImplementation，让 `AsrApiClientTest` 不依赖 Robolectric 也能跑
- 设计 token 统一从 `app/design/.../tokens/Dimens.kt` 读，Compose 禁止硬编码 `#RRGGBB` / `24sp` / `8.dp`（§A.2 / §18 红线）

## 里程碑

- ✅ **v3.0.1 MVP**（当前）：录音 → ASR → 本地日记 单端闭环；22/22 单测全绿；`assembleDebug` 出 12 MB APK
- ✅ **v0.5.0**：本地 Agent 状态机 + 千问 Realtime ASR + MiniMax M3 + `qwen3-tts-flash-realtime` / `Kiki`；用户 BYOK 直连；无 Gateway；断网录音补做；不支持语音打断
- ⏳ **v2.x**（PRD §12.1）：服务端基线（CloudBase + COS + 三个 FastAPI 服务）+ 家属端 9 子节 + 扫码绑定
- ⏳ **v2.2（历史路线，已被 v0.5.0 取代）**：DeepSeek Harness 服务端 Agent；0.5.0 改为本地 Agent + BYOK 直连
- ⏳ **v2.3**：服务端 ASR 代理（凭证上服务端、客户端只持 session）

## 已知 MVP 限制（PRD §12.3）

- 无家属端 / 无关怀回路
- 无云端备份 / 卸载即丢失（§3.1.8 退出登录二次提示兜底）
- ASR key 在本机（截图有外泄风险；生产环境必须服务端代理 v2.3）
- 音频明文上送第三方 ASR（隐私敏感场景需自行评估）
- 60 秒硬限到时无 TTS"时间到"提示（依赖服务端 v2.2）

## 命令速查

```bash
# 编译
./gradlew :app:assembleDebug                # 出 app-debug.apk
./gradlew :app:assembleRelease              # 出 app-release-unsigned.apk

# 测试
./gradlew :app:testDebugUnitTest            # 全部 22 个单测（AsrApi/DiaryDao/AppError/LocalDate）
./gradlew :app:lintDebug                    # Android Lint

# 安装
./gradlew :app:installDebug                 # adb install 装到当前连接设备
adb shell am start -n com.elder.android/.MainActivity
```
