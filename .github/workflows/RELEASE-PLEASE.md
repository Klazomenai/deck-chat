# Release Process

## Overview

DeckChat uses [release-please](https://github.com/googleapis/release-please) to automate
versioning, changelog generation, and GitHub Releases. Each release builds a signed APK
and attaches it directly to the GitHub Release as a downloadable asset.

This allows developers to install the app on a physical device by downloading the `.apk`
from the Releases page — no ADB, USB debugging, or zip extraction required. The phone's
browser downloads the file directly and prompts for installation.

## How It Works

1. Conventional commits (`feat:`, `fix:`, etc.) merged to `main` are tracked by
   release-please
2. release-please maintains an open PR that accumulates unreleased changes and updates
   the changelog
3. Merging the release PR creates a GitHub Release with a semver tag
4. The `build-apk` workflow job builds a signed release APK with STT/TTS models included
5. The APK is attached to the release as `deck-chat-<version>.apk`

The release PR acts as a gate — changes accumulate until the team decides to cut a release
by merging the PR. This is not continuous deployment; releases are deliberate.

## Versioning Strategy

Versions follow [Semantic Versioning](https://semver.org/) with prerelease suffixes that
reflect the maturity of each milestone.

| Milestone | prerelease-type | Example Versions | Promotion |
|-----------|----------------|------------------|-----------|
| M1: First Watch | `alpha` | `0.1.0-alpha.0`, `0.1.0-alpha.1` | → `0.1.0` |
| M2: Full Complement | `alpha` | `0.2.0-alpha.0`, `0.2.0-alpha.1` | → `0.2.0` |
| M3: Open Ocean | `beta` | `0.3.0-beta.0`, `0.3.0-beta.1` | → `0.3.0` |
| Future | _(stable)_ | `0.4.0`, `1.0.0` | — |

### Reasoning

- **Alpha** (M1–M2): Sideload testing only. Breaking changes expected. APK downloaded
  from GitHub Releases by developers.
- **Beta** (M3): F-Droid candidate. Feature-complete, stability-focused. Wider testing.
- **Stable** (post-M3): Published to F-Droid. Semver guarantees apply.

### Transitioning Between Phases

Each transition is a one-line change in `release-please-config.json`:

- **Alpha → Beta**: Change `"prerelease-type": "alpha"` to `"prerelease-type": "beta"`
- **Beta → Stable**: Set `"prerelease": false` and remove `"prerelease-type"` and
  `"versioning-strategy"` fields

## Configuration Files

| File | Purpose |
|------|---------|
| `release-please-config.json` | Release type, prerelease strategy, changelog sections, extra-files |
| `.release-please-manifest.json` | Tracks the current released version |
| `.github/workflows/release-please.yml` | Workflow: release-please action + signed APK build |

The `extra-files` config uses a generic updater to patch `versionName` in
`app/build.gradle.kts` via the `// x-release-please-version` marker comment.

## Changelog Sections

Conventional commit types map to changelog sections:

| Commit Type | Changelog Section | Visible |
|-------------|------------------|---------|
| `feat` | Added | Yes |
| `fix` | Fixed | Yes |
| `perf`, `refactor` | Changed | Yes |
| `revert` | Reverted | Yes |
| `security` | Security | Yes |
| `docs`, `style`, `chore`, `test`, `build`, `ci` | _(hidden)_ | No |

## APK Signing

Release APKs are signed with a dedicated release keystore. The signing config in
`build.gradle.kts` reads credentials from environment variables (CI) or a local
`keystore.properties` file (developer builds).

### Generating a Release Keystore

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias release \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

`keytool` will prompt for passwords and distinguished name fields interactively.

> **Important**: The keystore file is the identity of the app. If lost, existing
> installs cannot receive updates — users must uninstall and reinstall. Store it
> securely outside the repository.

### GitHub Actions Secrets

The workflow requires four repository secrets:

| Secret | Description | How to Set |
|--------|-------------|------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore file | `base64 -w 0 release.keystore \| gh secret set RELEASE_KEYSTORE_BASE64` |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password | `gh secret set RELEASE_KEYSTORE_PASSWORD` |
| `RELEASE_KEY_ALIAS` | Key alias within the keystore | `gh secret set RELEASE_KEY_ALIAS -b "release"` |
| `RELEASE_KEY_PASSWORD` | Key password | `gh secret set RELEASE_KEY_PASSWORD` |

### Local Development

Copy `keystore.properties.example` to `keystore.properties` (gitignored) and fill in
the values to build signed release APKs locally:

```bash
cp keystore.properties.example keystore.properties
# Edit keystore.properties with your keystore path and credentials
./gradlew assembleRelease
```

## Signing and Upgrades

Android refuses to upgrade an app if the signing key changes. This has implications:

- **Debug → Release**: A debug APK (auto-signed with the debug keystore) cannot be
  upgraded to a release APK. The debug build must be uninstalled first.
- **Release → Release**: APKs signed with the same keystore upgrade seamlessly.
- **Release → F-Droid**: F-Droid re-signs with its own key. An existing release APK
  must be uninstalled before installing the F-Droid version.

For consistent sideload testing, always use the release APK from GitHub Releases.
