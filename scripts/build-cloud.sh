#!/usr/bin/env bash
# Bootstrap a full JDK and the official SDK when absent from the snapshot.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -x /workspace/tools/jdk-17/bin/jlink ]; then
  export JAVA_HOME=/workspace/tools/jdk-17
elif ! command -v jlink >/dev/null || ! command -v javac >/dev/null; then
  mkdir -p /workspace/tools/jdk-17
  jdk_url='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.17%2B10/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz'
  curl --fail --location --retry 2 "$jdk_url" -o /workspace/tools/jdk17.tar.gz
  curl --fail --location --retry 2 "$jdk_url.sha256.txt" -o /workspace/tools/jdk17.sha256
  (cd /workspace/tools && sha256sum --check <(sed 's@OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz@jdk17.tar.gz@' jdk17.sha256))
  tar -xzf /workspace/tools/jdk17.tar.gz -C /workspace/tools/jdk-17 --strip-components=1
  export JAVA_HOME=/workspace/tools/jdk-17
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -f /etc/ssl/certs/java/cacerts ]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
export ANDROID_HOME="${ANDROID_HOME:-/workspace/android-sdk}"
if [ -n "${HTTPS_PROXY:-}" ]; then
  proxy_flags=$(python3 - <<'PY'
import os, urllib.parse
p = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port or 80} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port or 80}')
PY
)
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $proxy_flags"
fi
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  scratch_dir=$(mktemp -d)
  trap 'rm -rf "$scratch_dir"' EXIT
  curl --fail --location --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$scratch_dir/sdk.zip"
  unzip -q "$scratch_dir/sdk.zip" -d "$scratch_dir"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$scratch_dir/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
# sdkmanager requires acceptance of Google's Android SDK license terms.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
license_status=${PIPESTATUS[1]}
set -o pipefail
[ "$license_status" -eq 0 ]
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;34.0.0' 'platform-tools'
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest
