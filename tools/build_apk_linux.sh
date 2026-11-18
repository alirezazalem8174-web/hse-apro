#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_SDK_ROOT:=${ANDROID_HOME:-}}"
python3 tools/qa_preflight.py
python3 tools/qa_adversarial.py
python3 tools/verify_build_env.py
if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME is not set." >&2
  exit 2
fi
GRADLE_CMD="${GRADLE_CMD:-}"
if [[ -z "$GRADLE_CMD" ]]; then
  if [[ -x "./gradlew" ]]; then
    GRADLE_CMD="./gradlew"
  elif command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD="gradle"
  else
    echo "Gradle 8.7 is required. Install Gradle or use the included GitHub Actions workflow." >&2
    exit 2
  fi
fi
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/build-tools/35.0.0:$PATH"
"$GRADLE_CMD" --no-daemon --stacktrace --warning-mode all :app:lintDebug :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
sha256sum "$APK"
echo "APK: $APK"
