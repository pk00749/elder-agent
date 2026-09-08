#!/usr/bin/env bash
# v3.0 MVP —— 一键生成本地签名 keystore（仅开发自用 / 内部演示，不上架）
# 用法：./scripts/generate-keystore.sh
set -euo pipefail

KEYSTORE="${HOME}/.android/elder-debug.keystore"
ALIAS="elder"
ALIAS_PASS="elderpass"
STORE_PASS="elderpass"
VALIDITY=10000

mkdir -p "$(dirname "$KEYSTORE")"

if [ -f "$KEYSTORE" ]; then
  echo "✓ keystore 已存在：$KEYSTORE"
  echo "  删除后重跑可重新生成（注意：重新生成会导致旧 release APK 升级时签名不一致需先卸载旧版）"
  exit 0
fi

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity "$VALIDITY" \
  -storepass "$STORE_PASS" -keypass "$ALIAS_PASS" \
  -dname "CN=Local Dev, OU=Elder, O=Personal, L=Shanghai, S=Shanghai, C=CN"

echo ""
echo "✓ 生成完成：$KEYSTORE"
echo "  alias=$ALIAS, 密码=$ALIAS_PASS（仅本机开发用）"
echo "  这俩密码写进了 app/build.gradle.kts 的 signingConfigs，build 直接用"
