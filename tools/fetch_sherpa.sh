#!/usr/bin/env bash
# 下载离线语音识别库 sherpa-onnx（Apache-2.0）的官方 AAR 到 app/libs/。
# 文件约 37MB，不提交到仓库；CI 和本地编译前都先运行这个脚本。
set -euo pipefail
VER=1.13.8
SHA=b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/libs/sherpa-onnx-$VER.aar"
mkdir -p "$(dirname "$OUT")"
if [ -f "$OUT" ] && echo "$SHA  $OUT" | sha256sum -c --status; then
  echo "sherpa-onnx $VER 已存在"
  exit 0
fi
curl -fsSL --retry 3 -o "$OUT" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VER/sherpa-onnx-static-link-onnxruntime-$VER.aar"
echo "$SHA  $OUT" | sha256sum -c -
