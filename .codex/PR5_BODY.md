# PR #5 — `codex/elder-app-detail-polish`

**Commit**: `elder-app: polish recording button / switch conflict / asr hint / save icon`
**Base**: `codex/elder-app-component-wiring` (PR #4, local commit f0441a4)
**对应 ui-ux-pro-max**: Touch & Interaction + Forms & Feedback

## 改动

### `ElderDiaryRecordScreen.kt` — 录音屏双按钮冗余
- 160dp 圆按钮保留不动
- **下方新增** 72dp 全宽次级按钮 `Size.SecondaryButtonHeight`，共享 `onStop` 回调
- 圆按钮 + 次级按钮双冗余，避免单点风险；core icons 没有 Stop，用 `Icons.Default.Close`（X 视觉同表达"停止"）
- 新增 `diary_recording_stop_secondary = "停止录音"` 字符串

### `ElderSettingsScreen.kt` — Switch 冲突修复
- `SettingRow.onClick: () -> Unit` 改为 `onClick: (() -> Unit)? = null`
- `.clickable(onClick = onClick)` 改为 `Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)`
- `SettingRow3Tts` 传 `onClick = null` → 整行不挂 clickable → Switch `onCheckedChange` 不被外层吞掉

### `ElderHomeScreen.kt` — ASR 红字 → 独立红卡
- 内联红字 `Text` 替换为 `Surface` 独立卡片，位置 `align(Alignment.BottomCenter)`（不与 today record icon 冲突）
- `BgGray` 底 + `Error500` 边（`Size.AsrCardBorderWidth = 2.dp`） + `Icons.Default.Warning` + 高度 `heightIn(min = Size.SecondaryButtonHeight)`
- 加 `testTag = "home_asr_hint_card"` 便于测试断言
- clickable → `onOpenSettings` 回调

### `AsrConfigScreen.kt` — 保存 emoji → IconButton
- TopAppBar `actions` 的 `Text("✓", ...)` 静态展示升级为 `IconButton(onClick = vm::save, enabled = state.allRequiredValid) { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.common_save), ...) }`
- 删 dead `private fun SaveButton(...)` 私有函数（49 行代码）

### `ElderEmptyState.kt` — TTS emoji → Icon
- ▶ emoji 升级为 `Icons.Default.PlayArrow`（core icons 没有 VolumeUp，PlayArrow 视觉同原三角形）
- Button 加 `.semantics { contentDescription = "朗读" }` 保持 a11y（Icon 自身 `contentDescription = null`）

### `Dimens.kt` — 新增 token
- `Size.AsrCardBorderWidth = 2.dp`（红边宽度）

### `strings.xml` — 新增字符串
- `diary_recording_stop_secondary = "停止录音"`

## 验证

- ✅ `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- ✅ `./gradlew :app:testDebugUnitTest --tests "com.elder.android.screen.elder.*"` 6 tests passed
- ✅ `python3 scripts/check_no_hardcoded_tokens.py app/src` exit 0, PASS

## 关键决策

1. **录音圆按钮保留**：不做激进改成宽按钮；用"主 + 次级全宽"双按钮冗余
2. **SettingRow 改 nullable 而非加 enabled 标志**：原签名侵入小、调用方只需传 null 表达"无点击"
3. **ASR 卡片放 BottomCenter**：Today record icon 在 TopEnd，不冲突；视觉上是"屏幕底部"显著提示
4. **Close 而非 Stop**：core icons 没 Stop；Close (X) 视觉最接近"停止"语义 + 文本双冗余
5. **PlayArrow 而非 VolumeUp**：core icons 没 VolumeUp；PlayArrow 视觉同原 ▶；Button 的 semantics 仍念"朗读"
