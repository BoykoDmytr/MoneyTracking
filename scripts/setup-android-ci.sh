#!/usr/bin/env bash
# Use a pinned, verified SDK installer instead of depending on runner image tools.
set -euo pipefail

if [[ -n "${RUNNER_TEMP:-}" ]]; then
  moneytracking_sdk_root="$RUNNER_TEMP/moneytracking-android-sdk"
else
  moneytracking_sdk_root="${ANDROID_HOME:-${TMPDIR:-/tmp}/moneytracking-android-sdk}"
fi
export ANDROID_HOME="$moneytracking_sdk_root"
export ANDROID_SDK_ROOT="$moneytracking_sdk_root"
moneytracking_cli_root="$ANDROID_HOME/cmdline-tools/12.0"
moneytracking_cli_sha256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258

if [[ ! -x "$moneytracking_cli_root/bin/sdkmanager" ]]; then
  moneytracking_sdk_download=$(mktemp -d)
  trap 'rm -rf "$moneytracking_sdk_download"' EXIT
  curl --fail --location --retry 3 --silent --show-error \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    -o "$moneytracking_sdk_download/tools.zip"
  printf '%s  %s\n' "$moneytracking_cli_sha256" "$moneytracking_sdk_download/tools.zip" | sha256sum --check
  unzip -q "$moneytracking_sdk_download/tools.zip" -d "$moneytracking_sdk_download"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$moneytracking_sdk_download/cmdline-tools" "$moneytracking_cli_root"
fi

export PATH="$moneytracking_cli_root/bin:$ANDROID_HOME/platform-tools:$PATH"
if [[ -n "${GITHUB_ENV:-}" ]]; then
  printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$ANDROID_HOME" "$ANDROID_SDK_ROOT" >> "$GITHUB_ENV"
fi
if [[ -n "${GITHUB_PATH:-}" ]]; then
  printf '%s\n%s\n' "$moneytracking_cli_root/bin" "$ANDROID_HOME/platform-tools" >> "$GITHUB_PATH"
fi

# yes may exit with SIGPIPE when sdkmanager finishes; sdkmanager's status is decisive.
set +o pipefail
yes | "$moneytracking_cli_root/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
moneytracking_license_status=${PIPESTATUS[1]}
set -o pipefail
test "$moneytracking_license_status" -eq 0

if [[ $# -eq 0 ]]; then set -- 'platforms;android-35' 'build-tools;34.0.0'; fi
"$moneytracking_cli_root/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "$@"
test -x "$ANDROID_HOME/build-tools/34.0.0/apksigner"
