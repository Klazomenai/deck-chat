{ pkgs, ... }:

let
  androidComposition = pkgs.androidenv.composeAndroidPackages {
    buildToolsVersions = [ "36.0.0" ];
    platformVersions = [ "35" "36" ];
    includeNDK = false;
    includeEmulator = true;
    includeSources = false;
    includeSystemImages = true;
    systemImageTypes = [ "google_apis" ];
    abiVersions = [ "x86_64" ];
  };
in

{
  # Android SDK has an unfree license; explicit license acceptance required by androidenv
  nixpkgs.config.allowUnfree = true;
  nixpkgs.config.android_sdk.accept_license = true;

  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
  };

  packages = [
    pkgs.gradle
    androidComposition.androidsdk
    pkgs.git
    pkgs.curl
    pkgs.gnutar
    pkgs.bzip2
  ];

  env = {
    ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
    ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
  };

  scripts = {
    wrapper.exec = ''
      gradle wrapper --gradle-version=9.4.0 --distribution-type=bin
      chmod +x gradlew
      echo "Gradle wrapper regenerated (9.4.0)"
    '';

    check-gms.exec = ''
      ./gradlew dependencies | grep -i "gms\|play-services\|firebase" || echo "No GMS dependencies found"
    '';

    install-debug.exec = ''
      ./gradlew installDebug "$@"
    '';

    device-test.exec = ''
      ./gradlew connectedDebugAndroidTest "$@"
    '';

    logcat.exec = ''
      if [ -z "$ANDROID_SERIAL" ]; then
        device_lines="$(adb devices | tail -n +2 | grep -v '^\*' | sed '/^[[:space:]]*$/d')"
        if [ -z "$device_lines" ]; then
          echo "No device connected — plug in via USB and enable USB debugging."
          exit 1
        fi
        if printf '%s\n' "$device_lines" | grep -q '[[:space:]]unauthorized'; then
          echo "Device unauthorized — accept the USB debugging prompt on your device."
          exit 1
        fi
        device_count="$(printf '%s\n' "$device_lines" | awk '$2 == "device" {count++} END {print count+0}')"
        if [ "$device_count" -gt 1 ]; then
          echo "Multiple devices detected — set ANDROID_SERIAL or use 'adb -s <serial>'."
          adb devices -l
          exit 1
        fi
      fi
      pid="$(adb shell pidof -s dev.klazomenai.deckchat | tr -d '\r')"
      if [ -z "$pid" ]; then
        echo "DeckChat is not running — launch the app on your device first."
        exit 1
      fi
      adb logcat --pid="$pid" "$@"
    '';

    devices.exec = ''
      adb devices -l
    '';

    download-models.exec = ''
      set -euo pipefail
      REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
      "$REPO_ROOT/scripts/download-stt-models.sh"
      "$REPO_ROOT/scripts/download-tts-models.sh"
    '';

    emulator.exec = ''
      set -euo pipefail
      AVD_NAME="deckchat-test"
      AVD_DIR="$HOME/.android/avd/$AVD_NAME.avd"
      if [ ! -d "$AVD_DIR" ]; then
        echo "Creating AVD '$AVD_NAME' (API 35, x86_64)..."
        echo no | avdmanager create avd \
          --name "$AVD_NAME" \
          --package "system-images;android-35;google_apis;x86_64" \
          --device "pixel_6" \
          --force
      fi
      echo "Starting emulator '$AVD_NAME'..."
      emulator -avd "$AVD_NAME" -no-snapshot-save "$@"
    '';
  };

  enterShell = ''
    echo ""
    echo "DeckChat — Android Development Shell"
    echo ""
    echo "ANDROID_HOME: $ANDROID_HOME"
    echo "JAVA_HOME:    $JAVA_HOME"
    echo ""
    echo "Build:"
    echo "  ./gradlew assembleDebug    — Build debug APK"
    echo "  ./gradlew lint test        — Lint + unit tests"
    echo "  install-debug              — Build + install debug APK to device"
    echo ""
    echo "Device:"
    echo "  devices                    — List connected devices (adb)"
    echo "  emulator                   — Launch Android emulator (API 35)"
    echo "  logcat                     — Filtered logcat for DeckChat"
    echo "  device-test                — Run instrumented tests on device"
    echo "  adb                        — Android Debug Bridge (direct)"
    echo ""
    echo "Setup:"
    echo "  download-models            — Download STT + TTS model files"
    echo "  wrapper                    — Regenerate Gradle wrapper (9.4.0)"
    echo "  check-gms                  — Audit for Google Play Services deps"
    echo ""
    if [ -e /dev/kvm ] && [ ! -w /dev/kvm ]; then
      echo "⚠  /dev/kvm not accessible — emulator will be slow without KVM."
      echo "   Fix: sudo usermod -aG kvm $USER && newgrp kvm"
      echo ""
    fi
  '';
}
