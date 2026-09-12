# PR #3 — `codex/elder-app-home-layout`

**Commit**: `elder-app: rebalance home screen with weight-based column + safe area`
**Base**: `develop` (PR #1 `98df20b` + PR #2 `978b01a` already merged)
**对应 ui-ux-pro-max**: Layout & Responsive + Touch & Interaction

## 改动

### `app/src/main/java/com/elder/screen/elder/ElderHomeScreen.kt`
- Column 顶级加 `windowInsetsPadding(WindowInsets.systemBars)` + `imePadding()`，
  `verticalArrangement = Arrangement.spacedBy(Spacing.Md)`
- **区域 A 问候 Box**: `.height(Size.HomeGreetingArea)` → `.weight(0.4f)` + `.semantics { testTag = "home_greeting_area" }`
- **区域 C 写日志 Box**: `.height(Size.HomeDiaryArea)` → `.weight(0.6f)` + `.semantics { testTag = "home_diary_area" }`，
  内层 Button 仍 `.height(Size.HomeDiaryButton = 120.dp)` 写死
- **区域 D 设置 Box**: `.height(Size.SecondaryButtonHeight)` → `.wrapContentHeight()` + `.semantics { testTag = "home_settings_area" }`，
  内层 Button 加回 `.fillMaxWidth().height(Size.SecondaryButtonHeight = 72.dp)`
- 加 imports: `Arrangement / WindowInsets / systemBars / windowInsetsPadding / imePadding / wrapContentHeight / semantics / testTag`
- 删错的 `weight` 顶级 import（`Modifier.weight` 是 `ColumnScope` 扩展，编译器自动 receiver）

### `app/src/test/java/com/elder/android/screen/elder/ElderHomeScreenTest.kt`
新增 3 个 layout 比例 + 多档屏幕测试：
- `weightProportions_5inch` (`w360dp-h640dp`): 3 个区域 Box 全部存在 + `diaryH > greetH`（weight 0.6 > 0.4 强制约束）+ 文本节点存在
- `homeScreen_rendersInLandscape_noOverflow` (`w640dp-h360dp-land`): 横屏 3 个区域 Box 全部存在 + "设置" 文本存在
- `homeScreen_rendersAt7inchTablet_noClipping` (`w600dp-h960dp`): 7" 平板写日志 + 设置文本均存在

## Token 影响

- `Size.HomeGreetingArea = 240.dp` 保留（token 表条目不删，仅本屏不引用）
- `Size.HomeDiaryArea = 200.dp` 保留（同上）
- `Size.HomeDiaryButton = 120.dp` 仍用（写日志按钮高度）
- `Size.SecondaryButtonHeight = 72.dp` 仍用（设置按钮高度）

## 验证

- ✅ `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- ✅ `./gradlew :app:testDebugUnitTest --tests "com.elder.android.screen.elder.ElderHomeScreenTest"` 4 tests passed
- ✅ `./gradlew :app:testDebugUnitTest --tests "com.elder.android.screen.elder.*"` BUILD SUCCESSFUL (3 tests)
- ✅ `python3 scripts/check_no_hardcoded_tokens.py app/src` exit 0, PASS

## 关键决策

1. **保留所有 token**：仅修改本屏对 token 的引用方式，不动 `Dimens.kt` 条目
2. **不引入 verticalScroll**：主屏内容固定，滚动会和系统手势冲突
3. **imePadding 放 Column 顶级**：未来加键盘场景全局处理，不绑特定按钮
4. **不删 PR #2 设置按钮逻辑**：5 次点击隐藏手势已删，仅做布局重平衡
5. **不删 PR #5 ASR 红字**：当前 `BrandColor.Error500` 内联 Text 在 PR #5 升级为红卡片
6. **测试用语义断言代替像素测量**：Robolectric 跑 dp→px + system insets 不可靠，
   用 `assertExists` + 区域相对高度比较（`diaryH > greetH`）覆盖 weight 比例意图
