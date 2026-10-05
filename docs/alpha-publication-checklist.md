# Alpha publication checklist

This is preparation for a later explicit human publication instruction.
PR #24 does not authorize or execute tagging, uploads or external publication.

## Before publication

- PR #24 is merged and current main belongs to the intended qualified source/evidence lineage.
- The full local release gate reports `QUALIFIED`, with current evidence freshness validation passing.
- Artifact SHA-256 values match the committed evidence and local `SHA256SUMS` output.
- Root/Python Apache-2.0 licenses match, package metadata agrees and dependency notices remain present.
- Release notes are finalized; the checkout is clean and the tag version is confirmed.
- Confirm the current `libjadx` PyPI project state before upload; historical name availability is insufficient.

## Tag identity

Proposed tag: `v0.1.0-alpha.1`. Choose its target deliberately:

1. The **qualified source commit** contains the source/artifact inputs actually tested.
2. The **evidence-only descendant** contains committed reports and changelog results.

Recommended default: tag the final evidence-only descendant only if freshness
validation proves its source digest is identical and every post-qualification
path is under `docs/release-evidence/**` or `docs/changelog.md`. If uncertain,
stop before tagging and ask the human. PR #24 creates neither target's tag.

## Proposed publication assets

GitHub Release title: **LibJadx 0.1.0-alpha.1**.

- `libjadx-0.1.0-alpha.1.zip`
- `libjadx-0.1.0-alpha.1.tar`
- `libjadx-0.1.0a1-py3-none-any.whl`
- `libjadx-0.1.0a1.tar.gz`
- `SHA256SUMS`

Proposed PyPI package: `libjadx==0.1.0a1`. These Java/Python version spellings
identify the same alpha. No Maven Central publication is planned: the Java
deliverable is a standalone service distribution. Signing is a separate decision.

## After separately authorized publication

- Verify the GitHub tag resolves to the chosen qualified lineage.
- Download assets and verify their hashes against committed evidence.
- Install from PyPI in a fresh virtualenv and check Apache-2.0 metadata.
- Smoke-test that installed SDK against the extracted Java release asset.
- Update release-page links and publication status in documentation.

These steps are documentation only in PR #24. No credentials or automatic
publication workflow are introduced.
