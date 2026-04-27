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
| M1: First Watch | `alpha` | `0.1.0-alpha`, `0.1.0-alpha.1` | → `0.1.0` |
| M2: Full Complement | `alpha` | `0.2.0-alpha`, `0.2.0-alpha.1` | → `0.2.0` |
| M3: Open Ocean | `beta` | `0.3.0-beta`, `0.3.0-beta.1` | → `0.3.0` |
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

## Version Code

Android requires a monotonically increasing integer `versionCode` for each release.
DeckChat computes `versionCode` from `versionName` at build time in `app/build.gradle.kts`
using a deterministic formula:

```
versionCode = major * 1_000_000 + minor * 10_000 + patch * 100 + prerelease
```

Stable releases use `prerelease = 99` (the maximum), ensuring they always have a higher
versionCode than any prerelease of the same version.

| versionName | versionCode | Notes |
|-------------|-------------|-------|
| `0.1.0-alpha` | `10000` | Bare label (first prerelease after bump) |
| `0.1.0-alpha.6` | `10006` | |
| `0.1.0` | `10099` | Stable beats all 0.1.0 prereleases |
| `0.3.0-beta.1` | `30001` | Any label works (alpha, beta, rc, ...) |
| `0.3.0` | `30099` | |
| `1.0.0` | `1000099` | |

Prerelease versions must use a lowercase alphabetic label in the form `-label` or
`-label.N`; only the trailing number affects `versionCode`, and a bare label is treated
as prerelease 0. The build fails if any slot exceeds its range
(minor/patch must be 0–99; prerelease must be 0–98, since 99 is reserved for stable).

No per-release commits are needed — release-please bumps `versionName` via the
`x-release-please-version` marker, and `versionCode` follows automatically.

## Configuration Files

| File | Purpose |
|------|---------|
| `release-please-config.json` | Release type, prerelease strategy, changelog sections, extra-files |
| `.release-please-manifest.json` | Tracks the current released version |
| `.github/workflows/release-please.yml` | Workflow: release-please action + signed APK build |

The `extra-files` config uses a generic updater to patch `versionName` in
`app/build.gradle.kts` via the `// x-release-please-version` marker comment.

### CI vs local toolchain

Local development uses `devenv shell`, which provides the full environment including
emulator and convenience scripts. CI workflows use `nix develop` (via `flake.nix`)
instead, because `devenv.nix` includes Android emulator system images (~4 GB) that
exceed the GitHub Actions runner disk budget when combined with the Nix store.

`flake.nix` provides the same SDK and build toolchain without emulator packages.
The `instrumented-tests` CI job uses `actions/setup-java` and
`reactivecircus/android-emulator-runner` for emulator lifecycle, independent of Nix.

CI also runs `assembleRelease` (without signing) to catch R8 minification errors
before merge.

## Changelog Sections

Conventional commit types map to changelog sections:

| Commit Type | Changelog Section | Visible |
|-------------|------------------|---------|
| `feat` | ⛵ New Rigging | Yes |
| `fix` | 🔧 Hull Repairs | Yes |
| `perf` | ⚡ Trimmed the Sails | Yes |
| `refactor` | ♻️ Refitted | Yes |
| `revert` | ↩️ Struck from the Log | Yes |
| `security` | 🔐 Battened Hatches | Yes |
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

## Recovery

When a release ends up half-published — tag without Release, Release without APK,
APK present but corrupted — the recovery path is the dedicated
[`republish-release.yml`](republish-release.yml) workflow. This workflow rebuilds the
signed APK from an existing tag and (re)attaches it to the GitHub Release, with safety
guards against accidentally clobbering a good release.

### Failure modes

Four known patterns produce a half-published release. The integrity assertions in
[`build-and-attach-apk.yml`](build-and-attach-apk.yml) (introduced in #170) make these
failures visible at publish time rather than hours or days later:

| Pattern | Symptom |
|---|---|
| **Tag exists, Release object missing** | `gh api .../releases/tags/<tag>` returns 404, but `gh api .../git/refs/tags/<tag>` succeeds. Browser shows GitHub's bare-tag fallback page (no APK download, no curated notes). The original v0.1.0-alpha.6 incident pattern. |
| **Release exists, APK asset missing** | Release page shows source archive only — no `deck-chat-<version>.apk` in the assets list. Typically a build/upload failure mid-flight. |
| **APK build failure** | `assembleRelease` failed in CI (disk space, network, build-system regression). Manifests as either of the two patterns above depending on whether the failure landed before or after `gh release create`. |
| **Manual deletion / cleanup** | Someone deleted a botched Release expecting to retry, but the tag survived. Subsequent re-runs of `release-please` see the existing tag and silently no-op. Forensically opaque after the fact. |

### Diagnosis

Inspect the actual state with the GitHub API:

```bash
# Does the tag exist on the remote?
gh api repos/<owner>/<repo>/git/refs/tags/<tag>

# Does the Release object exist?
gh api repos/<owner>/<repo>/releases/tags/<tag>

# Which workflow runs hit the merge commit?
gh api "repos/<owner>/<repo>/actions/runs?head_sha=<sha>"

# Which step failed in which attempt? (multi-attempt runs hide failures)
gh api repos/<owner>/<repo>/actions/runs/<id>/attempts/<n>/jobs
```

> **Important**: a release-please run that "looks successful" in the GitHub Actions UI
> may have failed on attempt 1 and silently no-op'd on attempts 2+. Always enumerate
> all attempts when diagnosing a release where the artefact is missing — the run summary
> view shows only the latest attempt. The v0.1.0-alpha.6 incident hid in attempt 1 of
> run `23957744115`; attempts 2–4 reported "success" without doing any work.

### Recovery procedure

The decision tree:

| State | Action |
|---|---|
| Tag missing | **Fix the underlying issue first.** Do not republish — the upstream process never completed. Investigate the release-please workflow run and address the failure. |
| Tag exists, Release missing | Run `republish-release.yml` with no `force`. The 404 path will fire naturally and create the Release. |
| Release exists, APK missing | Run `republish-release.yml` with no `force`. The asset-aware guard will recognise the legitimate-recovery case and proceed without refusing. |
| Release exists, APK present but wrong/corrupted | Run `republish-release.yml` with `force: true`. **Only when the existing APK is provably wrong** — `force` disables the safety guard. |

Invocation:

```bash
gh workflow run republish-release.yml \
  --repo <owner>/<repo> \
  -f tag=v<version> \
  -f version=<version>

# Add `-f force=true` only when intentionally overwriting a good Release.
```

What to expect after dispatch:

1. **`guard` job** runs first. Queries the Release state and decides whether to proceed
   (404 path / Release-without-APK path) or refuse (Release-with-APK path, unless `force`).
2. **`build-apk` job** runs the standard reusable build flow:
   - Validates the `tag`/`version` inputs against the regex from
     `app/build.gradle.kts`'s `computeVersionCode`
   - Checks out `refs/tags/<tag>` (forces tag-only resolution)
   - Builds and signs the APK
   - Calls `gh release upload --clobber` (200 path) or `gh release create --prerelease
     --generate-notes` (404 path)
3. **Integrity verification** asserts four post-publish invariants. Each failure exits 1
   with a named-field error message:
   - Assertion 1: Release exists for the tag
   - Assertion 2: `prerelease == true`
   - Assertion 3: `deck-chat-<version>.apk` attached
   - Assertion 4: asset size > 0

If the workflow exits successfully, all four assertions passed and the release is
properly published.

### Don't

- **Don't delete a half-broken Release before identifying the root cause.** The Release
  object is forensic evidence — its existence (or absence) is part of the diagnostic
  signal, and deleting it loses information. Diagnose first, recover second.
- **Don't push a fresh tag for the same version.** Mutating tags breaks the audit trail
  and confuses git clients that have cached the old tag. Use the existing tag and
  republish.
- **Don't pass `force: true` reflexively.** The safety guard refuses to overwrite an
  existing Release-with-APK because that's almost always wrong. Use `force` only when
  you've confirmed the existing APK is the wrong artefact and have a clear reason to
  replace it.
- **Don't manually upload a locally-built APK.** Local builds bypass the CI provenance
  chain. The signing key and build environment are the same in both cases, but the
  audit trail is not — every published artefact should be traceable to a CI workflow run.

### Worked example: the v0.1.0-alpha.6 incident (2026-04-03 → 2026-04-27)

The original incident is the canonical case study for this runbook. Both the failure
and the recovery happened on the production deck-chat repository.

**Failure (2026-04-03)**:

| Time (UTC) | Event |
|---|---|
| 18:41:21 | PR #135 (`chore(main): release 0.1.0-alpha.6 🗺️`) merged to `main` at commit `b6e5c89` |
| 18:41:23 | "Release Please" workflow run [`23957744115`](https://github.com/Klazomenai/deck-chat/actions/runs/23957744115) attempt 1 starts |
| 18:41:30 | release-please action creates tag `v0.1.0-alpha.6` AND GitHub Release |
| 18:45:13 | "Download STT models" step fails — `nix develop` ran out of disk while building `android-sdk-system-image-35-google_apis-x86_64`. Log line: `note: build failure may have been caused by lack of free disk space` |
| 18:45:19 | Attempt 1 conclusion: **failure** |
| 18:49:30 → 18:56:51 | Attempts 2, 3, 4 — release-please re-runs, sees the tag at HEAD, outputs `release_created=false`, `build-apk` skipped on each retry. All three attempts report "success" without doing any work. |
| _later_ | The Release object was deleted manually post-incident; the tag survived. |

**Reactive fix (2026-04-05)**: PR #148 (`ci: add assembleRelease to CI and trim flake
for disk budget 🏗️`) trimmed the dev shell flake to fit the runner's disk budget. The
underlying disk-space failure was addressed within 48 hours, but the alpha.6 republish
itself was deferred.

**Recovery (2026-04-27, tracked as #171)**: After the M1 release-pipeline-hardening chain
(#169 reusable workflow architecture, #170 integrity assertions, #173 actionlint
enforcement) landed, recovery became a one-line dispatch:

```bash
gh workflow run republish-release.yml \
  --repo Klazomenai/deck-chat \
  -f tag=v0.1.0-alpha.6 \
  -f version=0.1.0-alpha.6
```

| Time (UTC) | Event |
|---|---|
| 12:30 | Run [`24995016615`](https://github.com/Klazomenai/deck-chat/actions/runs/24995016615) dispatched |
| 12:30:xx | `guard` job: `404 path → "No existing Release for tag v0.1.0-alpha.6 — proceeding"` |
| 12:31–12:34 | `build-apk` job: validate inputs → checkout `refs/tags/v0.1.0-alpha.6` (commit `b6e5c89`) → build → `gh release create --prerelease --generate-notes` → integrity assertions all pass |
| 12:35:16 | Release [`v0.1.0-alpha.6`](https://github.com/Klazomenai/deck-chat/releases/tag/v0.1.0-alpha.6) published with `deck-chat-0.1.0-alpha.6.apk` (605,318,335 bytes) attached, prerelease=true |

**Lessons**:

1. Silent skips were invisible in the run summary view. Diagnosing the original failure
   required enumerating attempts 1–4 individually; only attempt 1 showed the actual
   error. **Always check all attempts when a release artefact is missing.**
2. The integrity assertions added in #170 close this gap going forward — a future build
   that fails the same way will exit the workflow with a named-field error rather than
   reporting success-with-broken-output.
3. The republish workflow's safety guards (asset-aware, fail-closed on transient API
   errors) make recovery low-risk: an operator dispatching against the wrong tag, or
   against a healthy Release, will be refused with a clear message rather than silently
   clobbering.
