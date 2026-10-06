#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
GRADLE_VERSION="8.11.1"
TOOLS_ARCHIVE="commandlinetools-linux-16111833_latest.zip"
mkdir -p "$SDK_ROOT/cmdline-tools" "$HOME/.cache/hexi-android"

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "Downloading Android command-line tools..."
  curl -fL --retry 3 "https://dl.google.com/android/repository/$TOOLS_ARCHIVE" -o "$HOME/.cache/hexi-android/$TOOLS_ARCHIVE"
  rm -rf "$HOME/.cache/hexi-android/cmdline-tools-unpack"
  mkdir -p "$HOME/.cache/hexi-android/cmdline-tools-unpack"
  unzip -q "$HOME/.cache/hexi-android/$TOOLS_ARCHIVE" -d "$HOME/.cache/hexi-android/cmdline-tools-unpack"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$HOME/.cache/hexi-android/cmdline-tools-unpack/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

if [[ ! -x "$HOME/.cache/hexi-android/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
  echo "Downloading Gradle $GRADLE_VERSION..."
  curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$HOME/.cache/hexi-android/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q "$HOME/.cache/hexi-android/gradle-$GRADLE_VERSION-bin.zip" -d "$HOME/.cache/hexi-android"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
echo "Accepting Android SDK licenses..."
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null
set -o pipefail
echo "Installing Android API 35 and build tools..."
"$SDKMANAGER" --sdk_root="$SDK_ROOT" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
cd "$ROOT"
"$HOME/.cache/hexi-android/gradle-$GRADLE_VERSION/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
printf '\nBootstrap complete.\nANDROID_SDK_ROOT=%s\nGradle=%s\n' "$SDK_ROOT" "$GRADLE_VERSION"
