# DeckChat

Android voice client for the AI crew at sea. Offline STT/TTS, E2EE Matrix, no cloud speech processing.

## Overview

DeckChat is your voice line to the crew. Press the headset button, hail the
bridge, and your words are transcribed on-device by Sherpa-ONNX, carried
below decks over E2EE Matrix, and the crew's reply spoken back in their own
voice — your voice never leaves the ship.

## Architecture

```mermaid
flowchart TD
    A[Bluetooth headset] --> B[RecordingService\n16kHz mono PCM]
    B --> C[Sherpa-ONNX STT\nWhisper Tiny EN, int8 ONNX]
    C --> D[Matrix E2EE\nmatrix-rust-sdk, Simplified Sliding Sync]
    D --> E[Crew bridge bot\nMatrix, external]
    E --> F[Sherpa-ONNX TTS\nPiper VITS crew voices]
    F --> G[Bluetooth headset / speaker]

    click E href "https://github.com/Klazomenai/bridge" "Bridge repository" _blank
```

| Component | Implementation | License |
|-----------|---------------|---------|
| STT | Sherpa-ONNX + Whisper Tiny EN int8 ONNX | Apache 2.0 |
| TTS | Sherpa-ONNX + Piper VITS voice models | Apache 2.0 / MIT |
| Matrix | matrix-rust-sdk (UniFFI Kotlin bindings) | Apache 2.0 |
| Credentials | Android Keystore (EncryptedSharedPreferences) | — |
| Audio | Android AudioRecord/AudioTrack, Bluetooth SCO | — |

### Crew voices

| Crew | Voice model | Accent |
|------|------------|--------|
| Maren | `vits-piper-en_GB-cori-high` | British English |
| Crest | `vits-piper-en_US-lessac-high` | US English |

### Android targets

| Target | Version |
|--------|---------|
| minSdk | 28 |
| targetSdk | 36 |
| compileSdk | 36 |
| Java | 17 |

## Development

### Prerequisites

- [Nix](https://nixos.org/download/) with flakes enabled
- [devenv](https://devenv.sh/) (recommended — provides convenience scripts below)

### Dev shell

```bash
devenv shell        # recommended — includes convenience scripts
nix develop         # alternative — Gradle + SDK only, no convenience scripts
```

`devenv shell` provides JDK 17, Gradle, Android SDK (build-tools 36, platforms 35 and 36),
`adb`, emulator, and convenience scripts. On entry it prints all available commands.
`nix develop` provides the same toolchain but without the devenv scripts —
use `./gradlew` and `adb` commands directly.

> **Note:** Commands like `install-debug`, `device-test`, `logcat`, `devices`,
> and `download-models` are devenv convenience scripts. `nix develop` users
> can use the underlying `./gradlew` and `adb` equivalents shown in their
> descriptions.

### Emulator

The dev shell includes an Android emulator (API 35, x86_64). On Linux, enable
KVM for hardware acceleration (the emulator can run without it but will be slower):

```bash
sudo usermod -aG kvm $USER    # one-time setup on Linux (requires logout/login)
emulator                       # devenv shell — creates AVD on first run, then launches
```

The `emulator` command is a devenv convenience script. `nix develop` users can
create and launch the AVD manually with `avdmanager` and `$ANDROID_HOME/emulator/emulator`.

SettingsActivity, HeadsetButtonReceiver, and permission-denial tests run on emulator.
Bluetooth and microphone-dependent tests skip (no mic/BT hardware).

### Building

```bash
./gradlew assembleDebug     # debug APK
install-debug               # build + install to connected device
```

### Testing

```bash
./gradlew lint test          # lint + unit tests (no device needed)
device-test                  # instrumented tests on device or emulator
```

Unit tests use mock engines (`MockSttEngine`, `MockTtsEngine`) — no JNI or
model files required. Instrumented tests require a connected device or emulator.

### Models

STT and TTS models are not committed to the repo (~200 MB total). Download
them before building for real device use:

```bash
download-models                                          # devenv shell
scripts/download-stt-models.sh && scripts/download-tts-models.sh  # nix develop
```

This fetches:
- **STT**: Whisper Tiny EN int8 ONNX (~37 MB) from HuggingFace — encoder, decoder, and tokens file
- **TTS**: Piper VITS voices (~80 MB each) from k2-fsa/sherpa-onnx releases

Models are placed in `app/src/main/assets/stt/` and `app/src/main/assets/tts/`
(both gitignored). All three STT files (`tiny.en-encoder.int8.onnx`,
`tiny.en-decoder.int8.onnx`, `tiny.en-tokens.txt`) are required — a missing
tokens file causes a silent native crash (SIGSEGV).

### Physical device

Connect a device via USB with USB debugging enabled:

```bash
devices                      # list connected devices
install-debug                # build + install
logcat                       # filtered log output for DeckChat
```

`logcat` detects no-device, unauthorized, and multi-device states with
actionable messages. Set `ANDROID_SERIAL` to target a specific device when
multiple are connected.

When using `nix develop` (without devenv scripts), install directly via adb:

```bash
nix develop --impure --command bash -c './gradlew assembleDebug'
nix develop --impure --command adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Debugging

**Logcat tags:**

| Tag | Level | Source |
|-----|-------|--------|
| `DeckChat.CRASH` | E | `DeckChatApplication` — global uncaught exception handler |
| `DeckChat.Onboarding` | D/E | `OnboardingActivity` — login flow |
| `DeckChat.ViewModel` | D/E | `MainViewModel` — Matrix sync init |
| `DeckChat.MatrixClient` | D/E | `RustMatrixClient` — room sync, Sliding Sync retry |
| `DeckChat.SttEngine` | D/E | `SherpaOnnxSttEngine` — transcription pipeline |
| `DeckChat.TtsEngine` | E | `SherpaOnnxTtsEngine` — native library load |
| `DeckChat.SecureStorage` | E | `SecureStorage` — decryption failures |

**Logcat commands:**

```bash
# Filter by DeckChat process (all tags)
nix develop --impure --command bash -c \
  'adb logcat --pid=$(adb shell pidof -s dev.klazomenai.deckchat)'

# Filter by specific tags
nix develop --impure --command bash -c \
  'adb logcat -s "DeckChat.SttEngine:D" "DeckChat.CRASH:E" "AndroidRuntime:E"'

# Pipe to file for analysis
nix develop --impure --command bash -c \
  'adb logcat --pid=$(adb shell pidof -s dev.klazomenai.deckchat) > /tmp/deckchat.log 2>&1'
```

> **Note:** The devenv `logcat` convenience script does not work with
> `nix develop --command` — use `adb logcat` directly as shown above.

**Debug vs release APK:**

- **Debug**: no R8 minification, no signing, no model download needed for tests (mocks)
- **Release**: R8 minification on, requires keystore + model download
- Switching between debug and release (or uninstalling/reinstalling) creates a
  new E2EE session. The bridge must create new megolm keys — restart the bridge
  pod to force key re-sharing.

**Bridge context recovery:**

If the bridge returns HTTP 400 with `"unexpected tool_use_id"`, the conversation
context buffer is poisoned (an orphaned `tool_result` without its `tool_use`).
All subsequent messages will fail until the pod is restarted:

```bash
kubectl delete pod -l app.kubernetes.io/name=bridge -n matrix
```

### Other commands

```bash
wrapper                      # regenerate Gradle wrapper (9.4.0)
check-gms                    # audit for Google Play Services deps (F-Droid)
```

## Project structure

```
./
├── app/
│   └── src/main/java/dev/klazomenai/deckchat/
│       ├── MainActivity.kt              # launch activity
│       ├── SettingsActivity.kt          # Matrix credentials + preferences
│       ├── SecureStorage.kt             # Android Keystore wrapper
│       ├── CrewRegistry.kt              # crew member definitions + voice mapping
│       ├── MatrixClient.kt              # Matrix client interface + crew message parsing
│       ├── RustMatrixClient.kt          # matrix-rust-sdk implementation
│       ├── SttEngine.kt                 # STT interface
│       ├── SherpaOnnxSttEngine.kt       # Sherpa-ONNX Whisper wrapper (JNI)
│       ├── TtsEngine.kt                 # TTS interface
│       ├── SherpaOnnxTtsEngine.kt       # Sherpa-ONNX Piper wrapper (JNI)
│       ├── RecordingService.kt          # foreground service, 16kHz PCM capture
│       ├── HeadsetButtonReceiver.kt     # media button broadcast receiver
│       └── DeckChatAudioManager.kt      # Bluetooth SCO + audio routing
└── scripts/
    ├── download-stt-models.sh           # fetch Whisper models from HuggingFace
    └── download-tts-models.sh           # fetch Piper voices from k2-fsa releases
```

## Privacy

What's said aboard stays aboard. Voice is transcribed and spoken entirely
on-device — no whisper reaches open water. Matrix messages are end-to-end
encrypted. Session tokens are encrypted with Android Keystore (StrongBox when available).
No telemetry. No analytics. No Google Play Services. No exceptions.

## License

Apache-2.0
