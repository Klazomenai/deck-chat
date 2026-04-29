# Quartermaster's Log — Release Banter

This directory holds optional curated banter files that the
[`build-and-attach-apk.yml`](../workflows/build-and-attach-apk.yml)
workflow prepends to the GitHub Release body, above the auto-generated
changelog and section summary.

For the workflow integration (auto-summary, milestone callout, idempotency,
fail-soft semantics), see the "Annotate Release with Quartermaster's Log"
step in [`build-and-attach-apk.yml`](../workflows/build-and-attach-apk.yml)
and the [Backfilling recipe](../workflows/RELEASE-PLEASE.md#backfilling-a-releases-body-retroactively)
for retroactive body edits.

## What this directory is

Each file describes a single release in a few paragraphs of curated prose.
The workflow's annotation step:

1. Looks for `.github/release-notes/<git-tag>.md` (e.g.
   `v0.1.0-alpha.7.md`) when publishing tag `v0.1.0-alpha.7`
2. If present, the file's content is rendered at the very top of the
   Release body, above the milestone callout and section summary
3. If absent, the Log header still renders with just the auto-summary —
   no error, no warning

## When to write one

Banter is **optional, not required**. Most routine releases are well-served
by the auto-summary alone. Write a banter file when the release has a story
worth telling that the auto-summary cannot capture on its own:

- **Milestone bookends** — the first or last release in a milestone, or any
  release that marks a phase transition (alpha → beta, beta → stable)
- **Incidents and recoveries** — a release that fixes a notable production
  issue, or a republish after an incident
- **Significant features or pivots** — a release that introduces or removes
  something that future-us will want to remember the moment of
- **Anything you'd want a reader to know on landing the page** — if the
  auto-summary leaves a question unanswered, the banter is the place

If a release doesn't need any of the above, skip the file. Silence is fine.

## Naming

```
.github/release-notes/v<version>.md
```

The filename matches the git tag verbatim, including the leading `v`.
Examples:

- `v0.1.0-alpha.7.md`
- `v0.2.0-beta.1.md`
- `v1.0.0.md`

The workflow looks for the exact filename based on its `tag` input. A
mismatch (missing `v`, wrong casing) means the file is not picked up; the
auto-summary still renders without it.

## Format

- **Pure markdown** — the file content is concatenated verbatim above the
  milestone callout, with one blank line as separator. No frontmatter, no
  preprocessing.
- **Blockquote-led** is the established convention. The leading `>` lines
  visually distinguish the curated banter from the auto-generated material
  that follows.
- **Length: ~150 words ideally**, hard cap ~250 words. The banter is a
  header, not an essay. Readers landing on the Release page should be able
  to absorb it in under 30 seconds.
- **No code blocks or tables** unless they're load-bearing. The banter
  precedes a Markdown changelog with its own structure; visual contrast is
  the point.
- **In banter files, links must be absolute URLs.** Banter content is
  rendered on the GitHub Release page, where relative links like
  `../workflows/RELEASE-PLEASE.md` do not resolve (no logical "current
  directory" exists in that context). Use full URLs
  (`https://github.com/<owner>/<repo>/blob/main/...`) so links work in any
  rendering context.
- **EOF newline** — Unix convention. Matches the rest of the repo.

## Tone

- **Pirate vibes welcome.** Match the project's broader idiom (the
  `release-please-config.json` section names like "⛵ New Rigging" and
  "🔧 Hull Repairs" are doing the same job at the changelog layer).
- **Honest about alpha/beta/stable state.** "First watch stood" carries
  the weight of "M1 milestone closed" without claiming the project is
  finished. Avoid language that implies stability or arrival when the
  project is still in alpha. Saying "alpha series, more hands on deck"
  is honest; saying "production ready" would not be.
- **Specific to *this* release.** Generic prose ("another solid release",
  "great progress this cycle") adds no signal. Reference real artefacts:
  PR numbers, issues, milestones, runbook sections, prior incidents.
- **Concrete over decorative.** A line like "the release pipeline that
  publishes this very release was rebuilt during this milestone" is
  worth fifty lines of "we worked hard". Specificity earns the pirate
  flavour; without it, the flavour reads as filler.

## Idempotency note

The workflow's annotation step is idempotent — re-runs against an
already-annotated release skip the operation. This means manual edits to a
Release body (including hand-tuning the curated banter post-publish)
**survive subsequent reruns**. If you spot a typo after publish, edit the
banter file *and* the live Release body; the workflow won't undo your fix.

## Worked example

[`v0.1.0-alpha.7.md`](v0.1.0-alpha.7.md) — the inaugural banter file. M1
banner; references the Recovery runbook and the four integrity assertions;
carries the M1 narrative without overstating. Length: 6 sentences across
2 paragraphs. Use it as the template for tone and length.
