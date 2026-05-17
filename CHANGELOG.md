# Changelog

## [0.1.0-alpha.8](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha.7...v0.1.0-alpha.8) (2026-05-17)


### 🔧 Hull Repairs

* **matrix:** allow hyphens in crew prefix regex verbosity group 🐛 ([#242](https://github.com/Klazomenai/deck-chat/issues/242)) ([5e08617](https://github.com/Klazomenai/deck-chat/commit/5e08617dc42062153604d767d43fcdfb197cf6df)), closes [#241](https://github.com/Klazomenai/deck-chat/issues/241)
* **release:** use absolute URLs in release-notes banter for cross-context rendering 🐛 ([#186](https://github.com/Klazomenai/deck-chat/issues/186)) ([4db2fd8](https://github.com/Klazomenai/deck-chat/commit/4db2fd8387f1d93dfd022d3f4e60695c2f2c275e)), closes [#185](https://github.com/Klazomenai/deck-chat/issues/185)
* **viewmodel:** bound matrixClient.stop() with withTimeoutOrNull during teardown 🐛 ([#223](https://github.com/Klazomenai/deck-chat/issues/223)) ([32431aa](https://github.com/Klazomenai/deck-chat/commit/32431aa00077a52a617abdb3e3abc062590a497e))
* **viewmodel:** classify ClosedReceiveChannelException as PipelineCancelled 🐛 ([#224](https://github.com/Klazomenai/deck-chat/issues/224)) ([e1fa3ba](https://github.com/Klazomenai/deck-chat/commit/e1fa3bac5125ca700971ace97d6640f6e546f166))


### ♻️ Refitted

* **ci:** extract fail-closed gh-api status-case as shared helper ([#220](https://github.com/Klazomenai/deck-chat/issues/220)) ([44c2f5f](https://github.com/Klazomenai/deck-chat/commit/44c2f5f4b0afb8741714a87f47d23a987c22233e))
* **test:** convert MainViewModelTest to Robolectric and pin SDK 34 across the suite ([#221](https://github.com/Klazomenai/deck-chat/issues/221)) ([3847acb](https://github.com/Klazomenai/deck-chat/commit/3847acbf2daab322658e3450f581cbd698ae368a))
* **viewmodel:** cancel viewModelScope in releaseResources for test safety ♻️ ([#222](https://github.com/Klazomenai/deck-chat/issues/222)) ([5c52d45](https://github.com/Klazomenai/deck-chat/commit/5c52d454b19d77390c607c0dc3294af6df7fa252)), closes [#160](https://github.com/Klazomenai/deck-chat/issues/160)

## [0.1.0-alpha.7](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha.6...v0.1.0-alpha.7) (2026-04-27)


### ⛵ New Rigging

* add debug mode with transcript display ✨ ([#156](https://github.com/Klazomenai/deck-chat/issues/156)) ([373e21e](https://github.com/Klazomenai/deck-chat/commit/373e21e187e7a49c1d63ac5c0cefc1990c3cc0f8))
* add pipeline timing display to debug mode ✨ ([#165](https://github.com/Klazomenai/deck-chat/issues/165)) ([d010e3b](https://github.com/Klazomenai/deck-chat/commit/d010e3b42c69f65ec774c9169bd0475ea48d1067))
* **ci:** assert release artifact integrity post-publish ✨ ([#179](https://github.com/Klazomenai/deck-chat/issues/179)) ([5b94d32](https://github.com/Klazomenai/deck-chat/commit/5b94d32d44d83f2b96141bbe563513104bb8aebc)), closes [#170](https://github.com/Klazomenai/deck-chat/issues/170)
* configurable response timeout with elapsed time feedback ⛵ ([#143](https://github.com/Klazomenai/deck-chat/issues/143)) ([95fd764](https://github.com/Klazomenai/deck-chat/commit/95fd764699684e73e5adc986c86d8cd222f64690))
* enable E2EE key backup and UTD handling ✨ ([#166](https://github.com/Klazomenai/deck-chat/issues/166)) ([dc11999](https://github.com/Klazomenai/deck-chat/commit/dc11999a4b591c1af1d3439dac680a790bbc0fa6)), closes [#141](https://github.com/Klazomenai/deck-chat/issues/141)
* surface UTD events in debug transcript ✨ ([#168](https://github.com/Klazomenai/deck-chat/issues/168)) ([daad607](https://github.com/Klazomenai/deck-chat/commit/daad607fa1d206a33ef7bbf7ab0dc6b428e2bf5f)), closes [#167](https://github.com/Klazomenai/deck-chat/issues/167)


### 🔧 Hull Repairs

* **devenv:** add setup-emulator script for SwiftShader stability 🐛 ([#149](https://github.com/Klazomenai/deck-chat/issues/149)) ([0d22752](https://github.com/Klazomenai/deck-chat/commit/0d227528b43638245769c391f0b2834c05a24e4b))
* queue crew messages and speak final response for delegation chains 🐛 ([#158](https://github.com/Klazomenai/deck-chat/issues/158)) ([5534265](https://github.com/Klazomenai/deck-chat/commit/55342655e83d34be28e29e351afef7446150823e))
* retry getRoom() with backoff after Sliding Sync start 🐛 ([#138](https://github.com/Klazomenai/deck-chat/issues/138)) ([831a2e4](https://github.com/Klazomenai/deck-chat/commit/831a2e42ba87e95bd3533ad17d7a675505f9a439))
* **test:** wrap tearDown in try/finally to guarantee Dispatchers.resetMain() 🐛 ([#151](https://github.com/Klazomenai/deck-chat/issues/151)) ([082a60e](https://github.com/Klazomenai/deck-chat/commit/082a60e26ad69dee59a22b7844e4b37dfd686033)), closes [#150](https://github.com/Klazomenai/deck-chat/issues/150)


### ♻️ Refitted

* **ci:** split release publishing into reusable workflow_call ♻️ ([#177](https://github.com/Klazomenai/deck-chat/issues/177)) ([11e868d](https://github.com/Klazomenai/deck-chat/commit/11e868d756aad2af2cf83eaa0a4bca0ab6337183)), closes [#169](https://github.com/Klazomenai/deck-chat/issues/169)
* **ci:** use grouped redirect in release-please.yml summary step ♻️ ([#175](https://github.com/Klazomenai/deck-chat/issues/175)) ([e90152d](https://github.com/Klazomenai/deck-chat/commit/e90152d8e041562f1db9da62db40c8e8a64f9df6)), closes [#174](https://github.com/Klazomenai/deck-chat/issues/174)

## [0.1.0-alpha.6](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha.5...v0.1.0-alpha.6) (2026-04-03)


### 🔧 Hull Repairs

* download and configure tiny.en-tokens.txt for Whisper STT 🐛 ([#134](https://github.com/Klazomenai/deck-chat/issues/134)) ([0b78be5](https://github.com/Klazomenai/deck-chat/commit/0b78be529d245413fdfac338a2867ef69f7f8e53))

## [0.1.0-alpha.5](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha.4...v0.1.0-alpha.5) (2026-04-03)


### 🔧 Hull Repairs

* add defensive logging to SherpaOnnxSttEngine transcription path 🐛 ([#131](https://github.com/Klazomenai/deck-chat/issues/131)) ([49d3bce](https://github.com/Klazomenai/deck-chat/commit/49d3bce41c17c7ad483b487feedaabaae0687870))

## [0.1.0-alpha.4](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha.3...v0.1.0-alpha.4) (2026-04-03)


### 🔧 Hull Repairs

* use wildcard dontwarn for JNA java.awt references 🐛 ([#126](https://github.com/Klazomenai/deck-chat/issues/126)) ([880ff2e](https://github.com/Klazomenai/deck-chat/commit/880ff2e0d7171e3e90f0d118e23c97b2563d9a4d)), closes [#122](https://github.com/Klazomenai/deck-chat/issues/122)

## [0.1.0-alpha.3](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha.2...v0.1.0-alpha.3) (2026-04-03)


### 🔧 Hull Repairs

* add dontwarn for JNA java.awt references missing on Android 🐛 ([#123](https://github.com/Klazomenai/deck-chat/issues/123)) ([3400401](https://github.com/Klazomenai/deck-chat/commit/34004011df64eb4a42b87a023689954d07b9b316)), closes [#122](https://github.com/Klazomenai/deck-chat/issues/122)

## [0.1.0-alpha.2](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha.1...v0.1.0-alpha.2) (2026-04-03)


### 🔧 Hull Repairs

* correct JNA ProGuard keep rule to com.sun.jna package 🐛 ([#119](https://github.com/Klazomenai/deck-chat/issues/119)) ([a12963a](https://github.com/Klazomenai/deck-chat/commit/a12963a2ebe58c67151df6bdd5c5048ac3324f16)), closes [#118](https://github.com/Klazomenai/deck-chat/issues/118)

## [0.1.0-alpha.1](https://github.com/Klazomenai/deck-chat/compare/v0.1.0-alpha...v0.1.0-alpha.1) (2026-04-02)


### ⛵ New Rigging

* add global UncaughtExceptionHandler for crash logging 🐛 ([#112](https://github.com/Klazomenai/deck-chat/issues/112)) ([41b986d](https://github.com/Klazomenai/deck-chat/commit/41b986d4a8e3f62da768d583f3bd8c670a615158))


### 🔧 Hull Repairs

* add defensive logging and error recovery to onboarding login 🐛 ([#114](https://github.com/Klazomenai/deck-chat/issues/114)) ([58e2941](https://github.com/Klazomenai/deck-chat/commit/58e294105091a9326e22cea2e9617adfef887d62))
* add defensive logging to MainViewModel Matrix sync init 🐛 ([#117](https://github.com/Klazomenai/deck-chat/issues/117)) ([01cd196](https://github.com/Klazomenai/deck-chat/commit/01cd19688934807cf433cda95a47d49fa8551c8e))
* log SecureStorage decryption failures instead of silent discard 🐛 ([#115](https://github.com/Klazomenai/deck-chat/issues/115)) ([ad04ab2](https://github.com/Klazomenai/deck-chat/commit/ad04ab2c384dad747e32985be66c727a8c0dc996)), closes [#109](https://github.com/Klazomenai/deck-chat/issues/109)
* wrap native library loading with error handling and logging 🐛 ([#116](https://github.com/Klazomenai/deck-chat/issues/116)) ([7918028](https://github.com/Klazomenai/deck-chat/commit/79180281041da0c66c70e033fb380738744b147d))

## [0.1.0-alpha](https://github.com/Klazomenai/deck-chat/compare/v0.0.1...v0.1.0-alpha) (2026-03-29)


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
* remove bump-patch-for-minor-pre-major from release-please config 🐛 ([#101](https://github.com/Klazomenai/deck-chat/issues/101)) ([937e329](https://github.com/Klazomenai/deck-chat/commit/937e329559b949cab11bb70037c3e8b132e7d300)), closes [#98](https://github.com/Klazomenai/deck-chat/issues/98)
* **ui:** surface specific error messages for all pipeline failure types 🐛 ([#91](https://github.com/Klazomenai/deck-chat/issues/91)) ([6600cb5](https://github.com/Klazomenai/deck-chat/commit/6600cb5f64b4797b4aba348268e5aa464892d41c)), closes [#32](https://github.com/Klazomenai/deck-chat/issues/32)
