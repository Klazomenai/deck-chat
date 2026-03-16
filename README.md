# DeckChat

Android voice client for the AI crew at sea.

## Overview

DeckChat is the Android companion app for the Crew at Sea voice assistant.
It captures voice via Bluetooth headset, converts to text entirely on-device
(Sherpa-ONNX, offline), sends via E2EE Matrix, and speaks responses via
Piper TTS in crew member voices.

## Architecture

- **STT**: Sherpa-ONNX (Whisper-small, offline, Apache 2.0)
- **TTS**: Piper (offline, crew voice profiles, Apache 2.0)
- **Matrix**: matrix-android-sdk2 (E2EE, Apache 2.0)
- **Credentials**: Android Keystore (hardware-backed)

## License

Apache-2.0
