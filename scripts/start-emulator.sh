#!/usr/bin/env bash
# v3.0 MVP —— 一键起 emulator + install + launch APK
# 用法：
#   ./scripts/start-emulator.sh                  # 默认 API 30 / pixel_5
#   ./scripts/start-emulator.sh --cold          # 冷启（强制）
#   ./scripts/start-emulator.sh --no-launch     # 只起 emulator，不装 APK
set -euo pipefail

AVD_NAME="${AVD_NAME:-elder_api30}"
PKG="${PKG:-com.elder.android}"
ACTIVITY="${ACTIVITY:-com.elder.android.MainActivity}"
COLD=""

for arg in "$@"; do
  case "$arg" in
    --cold)       COLD="--no-snapshot-load --no-snapshot-save" ;;
    --no-launch)  NO_LAUNCH=1 ;;
    -h|--help)
      sed -n '2,8p' "$0"; exit 0 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# 1) 检查 AVD 镜像
need_install=0
if ! command -v emulator >/dev/null 2>&1; then
  echo "✗ 'emulator' 不在 PATH；先安装 Android SDK command-line tools"
  exit 1
fi
if ! emulator -list-avds 2>/dev/null | grep -q "^${AVD_NAME}$"; then
  echo "→ AVD ${AVD_NAME} 不存在，开 sdkmanager 装镜像"
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager "emulator" "system-images;android-30;google_apis;x86_64"
  echo "no" | avdmanager create avd -n "$AVD_NAME" \
      -k "system-images;android-30;google_apis;x86_64" \
      -d pixel_5 --force
  need_install=1
fi

# 2) 检查硬件加速
echo "→ 检查硬件加速："
if ! emulator -accel-check 2>&1 | grep -q "is installed and usable"; then
  echo "  ⚠ 硬件加速不可用；emulator 会非常慢"
  echo "  macOS Intel: 装 Intel HAXM"
  echo "  Linux:       确认 /dev/kvm 可用"
  echo "  Windows:     启用 Hyper-V / WHPX"
  echo "  Apple Silicon: 不要用 arm64 镜像——切到 x86_64 + qemu/tcg"
fi

# 3) 起 emulator（无头 + swiftshader，跑 CI 也行）
echo "→ 启动 emulator（headless，GPU=swiftshader_indirect）..."
emulator -avd "$AVD_NAME" \
    $COLD \
    -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect \
    -no-snapshot \
    -netfast \
    >/tmp/emulator-$$.log 2>&1 &
EMU_PID=$!
echo "  PID=$EMU_PID，日志=/tmp/emulator-$$.log"

# 4) 等设备就绪
echo "→ 等 adb..."
adb wait-for-device

ready=0
for i in $(seq 1 60); do
  boot=$(getprop sys.boot_completed 2>/dev/null || echo 0)
  if [ "$boot" = "1" ]; then
    ready=1; break
  fi
  sleep 2
done
if [ $ready -ne 1 ]; then
  echo "✗ 60 秒内未 boot 完成；看 /tmp/emulator-$$.log"
  exit 1
fi
echo "✓ boot 完成"

if [ "${NO_LAUNCH:-}" = "1" ]; then
  echo "→ --no-launch 模式，跳过 APK"
  exit 0
fi

# 5) 构建 + install
echo "→ assembleDebug..."
./gradlew :app:assembleDebug --no-daemon -q

echo "→ installDebug..."
./gradlew :app:installDebug --no-daemon -q

echo "→ 启动 MainActivity"
adb shell am start -n "${PKG}/${ACTIVITY}"

echo ""
echo "✓ emulator 已在 ${AVD_NAME} 上跑 ${PKG}"
echo "  卸载: adb uninstall ${PKG}"
echo "  看日志: adb logcat | grep -i elder"
echo "  截图: adb exec-out screencap -p > screen.png"
