# PR #4 — `codex/elder-app-component-wiring`

**Commit**: `elder-app: wire up ElderEmptyState, LoadingState, NetworkYellowBar`
**Base**: `codex/elder-app-home-layout` (PR #3, local commit 5673900)
**对应 prd.md**: §4.7 空态 / §4.9 网络黄条 / §4.10 加载态 + AGENTS.md §A.3 组件表

## 改动

### `ElderDiaryRecentScreen.kt`
- 空态分支 `Box { Text(emptyMsg) }` 替换为
  `ElderEmptyState(text = emptyMsg, onTtsClick = { vm.ttsPlay(emptyMsg) })`
- 引用 `com.elder.android.ui.component.ElderEmptyState`

### `ElderDiaryRecentViewModel.kt`
- 新增 `fun ttsPlay(text: String)` — MVP 占位入口
- 文档说明：千问 TTS SDK 接入推迟到 §A.1 实现时回填；
  保留签名让 UI ↔ ViewModel 耦合点固定

### `ElderDiaryRecordScreen.kt`
- TopAppBar 下加 `NetworkYellowBar(visible = state.networkFailed, onRetry = vm::retryAsr)`
- `isProcessing -> LoadingState()` 已存在；删除 dead `private fun ProcessingState() = LoadingState()` 别名
- 引用 `com.elder.android.ui.component.NetworkYellowBar`

### `ElderDiaryRecordViewModel.kt`
- `DiaryRecordUiState` 新增 `networkFailed: Boolean = false`
- 新增 `private var lastAudioFile: File? = null`（retryAsr 复用）
- `processFile` 失败按 AppError 分流：
  - `AppError.AsrAuthFailed` → `topError = e.message` + `file.delete()`（用户去设置改 API Key）
  - 其余 ASR 错误 → `networkFailed = true` + KEEP `file`（保留 lastAudioFile）
- `startRecording` 清空 `networkFailed` + `lastAudioFile`（新录音时旧文件无可重试性）
- 新增 `fun retryAsr()`：复用 `lastAudioFile` 重新 processFile；文件不在时 `topError = "请重新录音"` + 清黄条

### 新增测试
- `ElderDiaryRecentViewModelTest` (1 test): `ttsPlay(text: String)` 必须是 public 1-arg 方法
- `ElderDiaryRecordViewModelTest` (2 tests):
  - `DiaryRecordUiState.networkFailed: Boolean` 字段存在
  - `ElderDiaryRecordViewModel.retryAsr()` 必须是 public 0-arg 方法

## 测试策略说明

`processFile` 行为测试（`AsrAuthFailed` vs `AsrUpstream` 分流）需要：
- `MockWebServer` 模拟百炼 WebSocket（AGENTS.md §A.8.6 要求）
- `ServiceLocator.asrApi` 测试 seam（当前无；ServiceLocator 全部 lateinit private set）

`processFile` 完整行为测试留给后续接 MockWebServer 的 PR。
当前 PR 测试只锁 surface API（UiState 字段 / ViewModel 公开方法），
与 `ElderHomeViewModelTest` 风格一致。

## 验证

- ✅ `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- ✅ `./gradlew :app:testDebugUnitTest --tests "com.elder.android.screen.elder.*"` 6 tests passed
  (PR #2: 1 + PR #3: 4 + PR #4: 2 reflection tests)
- ✅ `python3 scripts/check_no_hardcoded_tokens.py app/src` exit 0, PASS

## 关键决策

1. **`AsrAuthFailed` 走 topError 而非黄条**：auth 错需要用户去设置改 API Key，
   黄条对"重试"语义不对；其他可重试错误才走黄条
2. **黄条失败时 KEEP file**：`file.delete()` 改成只清理 auth 错；其他错让 retryAsr 复用
3. **`retryAsr` 文件不在时给提示**：避免空黄条（点了重试无效果）
4. **LoadingState 直接用，不留 alias**：删 `private fun ProcessingState() = LoadingState()` 死代码
5. **`ttsPlay` 是占位**：TTS SDK 没接不写假实现；签名先固定让 UI 解耦
