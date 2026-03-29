# Changelog

## [0.0.2-alpha](https://github.com/Klazomenai/deck-chat/compare/v0.0.1...v0.0.2-alpha) (2026-03-29)


### Added

* add release-please and signed APK release pipeline ⛵ ([#94](https://github.com/Klazomenai/deck-chat/issues/94)) ([e96102b](https://github.com/Klazomenai/deck-chat/commit/e96102bed8962e8ae9f517154e7b2d3c773237b8))
* **devenv:** add convenience scripts for physical device workflow ([#64](https://github.com/Klazomenai/deck-chat/issues/64)) ([1fffc32](https://github.com/Klazomenai/deck-chat/commit/1fffc32fe7fffdb02d05c52d336201deabfce104)), closes [#56](https://github.com/Klazomenai/deck-chat/issues/56)
* **devenv:** enable Android emulator (local + CI) ⛵ ([#70](https://github.com/Klazomenai/deck-chat/issues/70)) ([e02687c](https://github.com/Klazomenai/deck-chat/commit/e02687c7375480a74176879b3f6b334c766db229))
* implement crew registry and voice profile mapping ✨ ([#50](https://github.com/Klazomenai/deck-chat/issues/50)) ([2f1f083](https://github.com/Klazomenai/deck-chat/commit/2f1f083c564ccc503ee747488490ddd227f96f7e))
* implement headset button receiver, recording service, and Bluetooth audio routing ✨ ([#18](https://github.com/Klazomenai/deck-chat/issues/18)) ([a5d37de](https://github.com/Klazomenai/deck-chat/commit/a5d37deac266013b69e2e0a81e6c03881527eb83))
* implement Matrix client with E2EE via matrix-rust-sdk ✨ ([#48](https://github.com/Klazomenai/deck-chat/issues/48)) ([1b57a42](https://github.com/Klazomenai/deck-chat/commit/1b57a4228bbc4246215608ecef1291d27e4ba6d3))
* implement Piper TTS engine with crew voice profiles ✨ ([#16](https://github.com/Klazomenai/deck-chat/issues/16)) ([f662a48](https://github.com/Klazomenai/deck-chat/commit/f662a4824924c95e3a1d0e9806f528a35463d170))
* implement settings with secure storage for Matrix session ✨ ([#24](https://github.com/Klazomenai/deck-chat/issues/24)) ([2989c2c](https://github.com/Klazomenai/deck-chat/commit/2989c2ca76086955aec0f6a172b77118ab7e8791))
* implement Sherpa-ONNX STT engine (interface + implementation) ✨ ([#11](https://github.com/Klazomenai/deck-chat/issues/11)) ([e6254f2](https://github.com/Klazomenai/deck-chat/commit/e6254f2fe129e7598bbf19937f07e38c3767ae04))
* **ui:** add on-screen push-to-talk FAB ⛵ ([#86](https://github.com/Klazomenai/deck-chat/issues/86)) ([8b9def6](https://github.com/Klazomenai/deck-chat/commit/8b9def60f3a31084226fe4f2357c68c68c74dcba)), closes [#30](https://github.com/Klazomenai/deck-chat/issues/30)
* **ui:** add pipeline state machine and main activity UI ⛵ ([#75](https://github.com/Klazomenai/deck-chat/issues/75)) ([7149f51](https://github.com/Klazomenai/deck-chat/commit/7149f51746c98850cd106d7381067f6fb676bae1)), closes [#29](https://github.com/Klazomenai/deck-chat/issues/29)
* **ui:** add recording duration timer and transcription text display ⛵ ([#90](https://github.com/Klazomenai/deck-chat/issues/90)) ([8177fc9](https://github.com/Klazomenai/deck-chat/commit/8177fc9d64f61cb9899a72a95f5452b3deecb300)), closes [#31](https://github.com/Klazomenai/deck-chat/issues/31)
* **ui:** add runtime permission request flows for microphone ⛵ ([#79](https://github.com/Klazomenai/deck-chat/issues/79)) ([bd63b03](https://github.com/Klazomenai/deck-chat/commit/bd63b035ac756af24c89989b78b8becf463b8d21)), closes [#34](https://github.com/Klazomenai/deck-chat/issues/34)
* **ui:** add state transition animations and dark mode colours ⛵ ([#87](https://github.com/Klazomenai/deck-chat/issues/87)) ([7c8540d](https://github.com/Klazomenai/deck-chat/commit/7c8540dbcfd199231e69db5ea2b039c32c756cdb))
* **ui:** implement first-run onboarding wizard ⛵ ([#92](https://github.com/Klazomenai/deck-chat/issues/92)) ([84df4ba](https://github.com/Klazomenai/deck-chat/commit/84df4ba032324d4ce77ba392371f3adc73431289)), closes [#33](https://github.com/Klazomenai/deck-chat/issues/33)
* wire end-to-end voice pipeline orchestration ⛵ ([#89](https://github.com/Klazomenai/deck-chat/issues/89)) ([7dcc58d](https://github.com/Klazomenai/deck-chat/commit/7dcc58d20afad86c7cf3359d9a6b96bf385d9f69))


### Fixed

* add ProGuard keep rules and uses-feature declarations 🐛 ([#74](https://github.com/Klazomenai/deck-chat/issues/74)) ([11d58f5](https://github.com/Klazomenai/deck-chat/commit/11d58f5778e6dfaeb220f3c8a63b6cb98be3f775)), closes [#12](https://github.com/Klazomenai/deck-chat/issues/12)
* add R8 dontwarn rules for Tink JSR-305 annotations 🐛 ([#96](https://github.com/Klazomenai/deck-chat/issues/96)) ([5b34fd4](https://github.com/Klazomenai/deck-chat/commit/5b34fd4b39667961961655b560980ce3dd0bc400)), closes [#93](https://github.com/Klazomenai/deck-chat/issues/93)
* **ci:** scope push trigger to main to avoid duplicate runs ([#65](https://github.com/Klazomenai/deck-chat/issues/65)) ([2305a0d](https://github.com/Klazomenai/deck-chat/commit/2305a0d72441ba5adf046907e9cf9c9e3310bfb0)), closes [#15](https://github.com/Klazomenai/deck-chat/issues/15)
* correct release-please versioning config field name 🐛 ([#99](https://github.com/Klazomenai/deck-chat/issues/99)) ([4e21b67](https://github.com/Klazomenai/deck-chat/commit/4e21b67b75842ae7b0beb8482a43300aa844307d)), closes [#98](https://github.com/Klazomenai/deck-chat/issues/98)
* **devenv:** replace removed nixpkgs.config with local import 🐛 ([#77](https://github.com/Klazomenai/deck-chat/issues/77)) ([b14077c](https://github.com/Klazomenai/deck-chat/commit/b14077c1158d87cd65e3be2d1decafb75e8b8e00)), closes [#76](https://github.com/Klazomenai/deck-chat/issues/76)
* **gradle:** remove kotlin.android plugin — not needed in AGP 9.0+ ([a0b71a9](https://github.com/Klazomenai/deck-chat/commit/a0b71a9b01f7b2464125709ec93c46398420c734)), closes [#2](https://github.com/Klazomenai/deck-chat/issues/2)
* **gradle:** remove kotlinOptions — not available without Kotlin plugin ([72c5455](https://github.com/Klazomenai/deck-chat/commit/72c54553a206972da1fa1ad229c61e912643629a)), closes [#2](https://github.com/Klazomenai/deck-chat/issues/2)
* **manifest:** remove ic_launcher icon refs — mipmap resources not yet added ([8ce71db](https://github.com/Klazomenai/deck-chat/commit/8ce71db00205d95bc5ab0f1ee4268332d3fb86cc)), closes [#2](https://github.com/Klazomenai/deck-chat/issues/2)
* **nix:** accept Android SDK license in nixpkgs config ([3b8f4fd](https://github.com/Klazomenai/deck-chat/commit/3b8f4fd608dd2a350b29a6b67c1a1c322884aad7)), closes [#2](https://github.com/Klazomenai/deck-chat/issues/2)
* **nix:** allow unfree packages for Android SDK ([3560619](https://github.com/Klazomenai/deck-chat/commit/3560619a06d3df14df1c517fa56ef08c1294abe5)), closes [#2](https://github.com/Klazomenai/deck-chat/issues/2)
* **ui:** surface specific error messages for all pipeline failure types 🐛 ([#91](https://github.com/Klazomenai/deck-chat/issues/91)) ([6600cb5](https://github.com/Klazomenai/deck-chat/commit/6600cb5f64b4797b4aba348268e5aa464892d41c)), closes [#32](https://github.com/Klazomenai/deck-chat/issues/32)
