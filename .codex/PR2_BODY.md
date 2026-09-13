# PR #2 — elder-app: replace hidden 5-tap settings with visible button

对应 prd.md §3.1.8 设置入口 + a11y。

## 概述

老人端主屏原本用「问候区 5 次连点」触发设置入口：
- 老年用户对该手势触达率低
- TalkBack 完全无法发现（无可见控件）
- 视障用户永久不可用

PR #2 直接把设置入口改成可见按钮，符合 ui-ux-pro-max 的「指示清晰」原则。

## 改动

- **ElderHomeScreen.kt**：删除问候区 `pointerInput { detectTapGestures }`；在写日志按钮下方加 72dp 「⚙ 设置」文字按钮（`Icons.Default.Settings` + 设置文本），`onClick = onOpenSettings`
- **ElderHomeViewModel.kt**：删除 `tapCounter / settingsTrigger / todayRecordJustRecorded` 字段与 `onGreetingTap() / consumeSettingsTrigger() / consumeTodayRecordFlag()` 方法；清理 `combineState` 内 `diaryRepo.observeAll` 收集块的相关分支
- **ElderHomeViewModelTest**：反射断言 UiState 只剩 4 个业务字段、ViewModel 不再暴露 5-tap API（合成字段已过滤）
- **ElderHomeScreenTest (Robolectric)**：点击设置按钮触发 `onOpenSettings` 回调

## 设置按钮视觉权重

- 位置：Column 末尾独立 Box，不挤进 HomeDiaryArea
- 高度：72dp（`Size.SecondaryButtonHeight`）
- 底色：`BgGray`，文字色 `TextSecondary`，视觉权重明显低于主「写日志」按钮

## 保留不动

- 「今日已记录」红点圆点逻辑（`todayRecorded`）—— 仅删除 5 次点击隐藏触发
- ASR 未配置提示内联红字 —— PR #5 升级为红卡片
- strings.xml —— `home_settings_button = "设置"` PR #1 时已加

## 验证

- `./gradlew :app:testDebugUnitTest --tests com.elder.android.screen.elder.*` 全绿（3 tests）
- `./gradlew :app:assembleDebug` 通过
- `python3 scripts/check_no_hardcoded_tokens.py app/src` 退出码 0

## 假设

- 走「选项①」直接可见按钮；理由：TalkBack 可发现 + 老人手势触达率
- 不动 TopAppBar / Scaffold（主屏原本就没有 TopAppBar）
- 录音屏双按钮冗余、Switch 事件冲突修复、ASR 红卡片、保存 Icon 按钮 —— 留给 PR #5
