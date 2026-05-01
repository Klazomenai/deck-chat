# Contributing to DeckChat

Welcome aboard, ship's company. DeckChat is the Android voice client for
the Offshore Fleet — the captain's headset on deck, the offline ears
and voice that hails the bridge below. Below are the articles, set
down so the rigging stays tidy and the watch stays sharp. Read them
once, sign once, sail with us.

## The Crew's Bargain

Three things we ask of every hand who comes aboard:

1. **Sign the CLA.** Read the
   [Contributor Licence Agreement](https://gist.github.com/Klazomenai/b541b6605a823e234e3343a7145035de)
   first — every contributor signs it once, and the bot leaves a comment
   with the signing link on your first PR. Your copyright stays your own.
   You grant Klazomenai a perpetual sublicensable licence so future
   relicensing decisions can be made cleanly — but **bounded to
   OSI-approved open-source licences only**. The CLA does *not* grant
   Klazomenai the right to take your contribution proprietary or
   source-available. If that boundary ever needs to move, contributors
   are asked again. DeckChat currently sails under the [LICENSE.md](LICENSE.md)
   at the root of the repo (AGPL-3.0-or-later), and our commitments to
   contributors are set down in [STEWARDSHIP.md](STEWARDSHIP.md).
2. **Be kind in issues and reviews.** Disagreement is fine. Disagreement
   without respect is not. The watch is small and the world is large.
3. **Talk before you build.** Open an issue for anything bigger than a
   typo so we can chart the work together before the keel is laid.

## The Ship's Watch — Workflow

Aye, here's how we move the work from quayside to mast:

1. **Open an issue first** for anything bigger than a typo. Use the
   `enhancement` or `bug` template — both carry a `🏴‍☠️ Quartermaster's
   notes` section that the maintainer fills out before work starts.
   Keeps everyone aligned on motivation, scope, and acceptance.
2. **Branch off the trunk.** Name the branch
   `<type>/<issue-number>-<short-description>`. Types: `feat`, `fix`,
   `chore`, `refactor`, `docs`, `ci`, `security`, `test`.
3. **Commit in conventional form.**
   [Conventional Commits](https://www.conventionalcommits.org/) — subject
   lines `<type>(scope): <description>`. Optional emoji at the **end** of
   the subject (Conventional Commits parsers handle trailing emoji more
   reliably than leading ones).
4. **Sign your commits** (`git commit --gpg-sign` / `-S`). Branch
   protection requires it.
5. **Open a draft PR** the moment you have a working branch. PRs targeting
   `main` always start as drafts; mark ready when the diff is review-shaped.
6. **Wait for review.** Copilot reviews automatically; the maintainer
   follows. Address review comments in new commits — never amend or
   force-push; we squash on merge.
7. **Squash on merge.** The squash message becomes the canonical history
   entry, so write a good PR description.

## Fitting Out — Local Development

DeckChat is a Kotlin Android app, Gradle KTS, Nix devenv. The full
README covers prerequisites, Android SDK provisioning, emulator
quirks, and the model-pull dance. The minimum dance:

```bash
nix develop                                        # enter the dev shell (Android SDK, JDK 17, Gradle)
nix develop --command ./gradlew lint               # lint
nix develop --command ./gradlew test               # unit tests (Robolectric)
nix develop --command ./gradlew assembleDebug      # debug APK
nix develop --command ./gradlew assembleRelease    # release APK (R8 validation)
nix develop --command ./gradlew :app:licenseeRelease  # licence audit (matches CI)
```

CI runs lint + unit tests + debug APK + release APK + licence audit on
every PR. Instrumented tests run on a separate emulator job.

## The Quartermaster's Conventions

Where the rigging is dressed, every line in its place:

- **Branches off `main`** — no long-lived feature branches.
- **Issue/PR title emojis at the END** of the subject. Type emojis: 🐛 (fix),
  ✨ (feat), 📝 (docs), ♻️ (refactor), 🧪 (test), ⚙️ (chore), 🏗️ (ci),
  🔐 (security), ⚡ (perf), 🎓 (skill), 🗺️ (release).
- **Status emojis in issue/PR bodies**: ✅ Done · ❌ Blocked · ⏸️ Paused ·
  🚧 WIP · 📋 Planned.
- **Maritime preference**: ⛵ over 🚀 · 🏴‍☠️ for milestones · ⚓ for stable.
- **Newlines at EOF**, no trailing whitespace, LF line endings.
- **No emojis in source code, code comments, or branch names** — emojis
  belong in human prose (issue bodies, PR descriptions, commit messages),
  not in machine-readable identifiers or compiled artefacts.

## Labels

- Type: `enhancement`, `bug`, `chore`, `documentation`, `refactor`,
  `techdebt`, `security`, `epic`, `spike`
- Namespace: `app:deckchat` (and occasionally `app:bridge` for
  cross-cutting work)
- Domain: `domain:android`, `domain:ai`, `domain:matrix`,
  `domain:security`, `domain:ux`
- Priority: `priority:high`, `priority:medium`, `priority:low`

## Distribution & Licence Note

DeckChat targets **F-Droid** as primary distribution. Google Play has
historically had friction with AGPL on Android — F-Droid is fully
compatible and aligns with the open-source-by-default privacy posture.
If a Google Play presence is ever pursued, the legal landscape there
will need a fresh review.

## The Black Spot — Reporting Security Issues

Report security-sensitive findings privately via GitHub's
[Private Vulnerability Reporting](https://github.com/Klazomenai/deck-chat/security)
or by direct contact with the maintainer rather than in a public issue.
