# LibJadx documentation

LibJadx is v0.1 experimental feature-complete for the defined first-release scope,
locally release-qualified by PR #22 and not tagged or externally published.
Phases 0–6 are completed milestone history; Phase 7 is the active extended-capability roadmap.

## Current user documentation

- [Project overview, build/run and quick start](../README.md).
- [Configuration, paths, readiness and shutdown](configuration.md).
- [Python SDK usage, revisions and explicit save](../python/README.md).
- [Authoritative 22-operation OpenAPI contract](../openapi/openapi.yaml).

## Current developer and architecture documentation

- [Agent/contributor rules](../AGENTS.md): preserve the qualified baseline and investigate capabilities before implementation.
- [Implemented design and Phase 7 boundaries](../DESIGN.md): current architecture and public invariants.
- [Completed acceptance criteria and active Phase 7 roadmap](../IMPLEMENTATION.md).
- [Domain vocabulary](../CONTEXT.md).
- [Python SDK architecture](phase-6-python-sdk.md): current generator/handwritten boundaries, with PR #21 validation history distinguished from subsequent PR #22 qualification.

## Compatibility and capabilities

[Compatibility](compatibility.md) records supported pins/platforms and qualified
inputs. The [feasibility matrix](feasibility-matrix.md) separates internal Jadx
probes from public service capabilities. [/capabilities evidence](pr-22-capabilities.json)
records the complete reviewed snapshot on all five ordinary-process client surfaces.
[The reviewed example](../openapi/examples/capabilities.json) defines formal capability statuses.

| Area / formal capability | v0.1 state |
| --- | --- |
| Java engine / `code.java` | SUPPORTED |
| Java endpoint / `code.java_source_endpoint`, method excerpts / `code.method_excerpt` | PARTIAL; verified metadata/range limits |
| Class listing / `class.list`, original resolution / `symbol.resolve` | PARTIAL; Jadx-visible entities, aliases separate from identity |
| Input provenance / `symbol.input_provenance` | UNKNOWN; exact input origin unavailable |
| Basic references / `references.method_uses` | PARTIAL; coverage and locations explicit |
| Class-name search / `search.class_names`, safe source regex / `search.regex_safe` | SUPPORTED |
| Member/source search / `search.member_names`, `search.source_text`, complete-index jobs / `search.complete_index_job` | PARTIAL; explicit coverage and failures |
| Declaration renames/LINE comments / `edit.class_rename`, `edit.method_rename`, `edit.field_rename`, `edit.class_comment`, `edit.method_comment`, `edit.field_comment` | SUPPORTED within validated native subsets |
| Native save / `project.native_save` | PARTIAL; explicit save only |
| Tiny v2 import/export / `edit.mapping_import`, `edit.mapping_export` | PARTIAL; strict declaration subset |
| Parameter rename / `edit.parameter_rename` | SUPPORTED conservative snapshot-bound subset |
| Related propagation / `edit.related_propagation` | PARTIAL conservative independently COMPLETE families |
| Local rename / `edit.local_rename` | UNSUPPORTED |
| Smali representation / `code.smali` | UNSUPPORTED; internal retrieval is not a public API |
| CFG / `analysis.cfg` | UNKNOWN; no public API, Phase 7 |
| Resource query API | Not implemented; Phase 7, no formal resource capability in v0.1 |
| Parallel primary Jadx reads / `analysis.concurrent_reads` | UNSUPPORTED; serialized, conflicting admission fails PROJECT_BUSY; multiple clients supported |
| Cancellation / `analysis.cancellation` | PARTIAL/cooperative; CANCELLING remains until work stops, no hard interruption guarantee |
| Python SDK | Implemented; installed wheels locally qualified on Linux CPython 3.11.13/3.14.4 |
| External publication | No tag, GitHub Release, PyPI or Maven publication |

## Release qualification evidence

- [Repeatable qualification runbook](phase-6-release-qualification.md): all 12 matching-GUI gates, cross-language installed-artifact checks, strict source identity and publication boundary.
- [PR #22 current candidate qualification report](pr-22-review.md): exact commands/outcomes and limits at source `55e46eb707d095abdb067424db77f51a31558e17`.
- [Validation](pr-22-validation.json), [artifact manifest](pr-22-artifacts.json), [route matrix](pr-22-route-matrix.json) and [error matrix](pr-22-error-matrix.json).

The evidence certifies its recorded source/artifacts, not every later main revision.
The [PR #23 freshness finding](phase-6-release-qualification.md#documentation-cleanup-and-evidence-freshness)
records why ordinary documentation changes fail existing evidence freshness checks.
Evidence and release-test semantics are preserved; no new runtime qualification is claimed.

## Phase 7 roadmap

See [all eight roadmap items](../IMPLEMENTATION.md#8-phase-7--extended-capabilities-post-initial-release--roadmap)
and [analysis goals](../DESIGN.md#6-analysis-capabilities): deeper references and
original offsets, independently verified CFGs, Smali/richer representations,
advanced annotations, resources/external classpaths, portable native export,
optional isolated workers and separately reviewed filesystem deletion.

## Historical milestone evidence

Historical files are retained as evidence of decisions and tests at that point
in time. Their “next milestone,” “pending” and intermediate support statements
are historical, not the current roadmap. Some remain technical subsystem references;
current public statuses above and OpenAPI take precedence over intermediate findings.

- [Phase 2 native lifecycle notes](phase-2.md).
- [Phase 3.2 jobs and events](phase-3-jobs-and-events.md).
- [Phase 3.1 operation coordination](phase-3-operation-coordination.md).
- [Phase 3.3 requested shutdown](phase-3-shutdown.md).
- [Phase 4.2: class Java source and metadata](phase-4-decompiled-source.md).
- [Phase 4.3: Jadx-reported references](phase-4-references.md).
- [Phase 4.1: original symbol identity and lookup](phase-4-symbol-identity.md).
- [Phase 5.2 native declaration editing slice](phase-5-editing.md).
- [PR #15: bounded original-input census and independent hierarchy verifier](phase-5-hierarchy-verifier.md).
- [Native local-variable rename retargeting feasibility (PR #20)](phase-5-local-rename-feasibility.md).
- [Phase 5.2: Tiny v2 mapping export evidence](phase-5-mapping-export.md).
- [Phase 5.2: strict Tiny v2 mapping import and merge](phase-5-mapping-import.md).
- [PR #16: verified family replay safety gate](phase-5-propagated-edits.md).
- [Verified related-method rename group admission (PR #19)](phase-5-related-group-admission.md).
- [PR #14: related-method propagation feasibility](phase-5-related-propagation.md).
- [Fresh-equivalent replacement-engine edit publication](phase-5-replacement-publication.md).
- [Safe native replay: PR #17 historical evidence and PR #18 approved interpretation](phase-5-safe-replay.md).
- [PR #13: snapshot-bound native parameter renames](phase-5-scoped-editing.md).
- [Phase 5.1: process-local incremental search](phase-5-search.md).
- [ADR 0001](adr/0001-defer-advanced-native-edits.md): historical native-edit decisions; final v0.1 outcomes are parameter subset SUPPORTED, related propagation PARTIAL and local rename UNSUPPORTED. The ADR remains unchanged.

## PR review records

PR #12–#21 are historical reviews. They retain exact commands, negative controls
and handoffs as written at each PR. PR #22 above is the current candidate report.

- [PR #12 review](pr-12-review.md).
- [PR #13 review](pr-13-review.md).
- [PR #14 review](pr-14-review.md).
- [PR #15 review](pr-15-review.md).
- [PR #16 review](pr-16-review.md).
- [PR #17 review](pr-17-review.md).
- [PR #18 review](pr-18-review.md).
- [PR #19 review](pr-19-review.md).
- [PR #20 review](pr-20-review.md).
- [PR #21 review](pr-21-review.md).

## Machine-generated qualification evidence

These JSON files are audit evidence, not narrative documentation. Do not manually
reformat, collapse, rename, delete or replace their source identities:

- [PR #20 source audit](pr-20-source-audit.json): exact pinned source inspected for local-variable feasibility.
- [PR #21 artifacts](pr-21-artifacts.json): local SDK foundation packaging fingerprints.
- [PR #22 artifacts](pr-22-artifacts.json): candidate archive hashes, contents and reproducibility.
- [PR #22 capabilities](pr-22-capabilities.json): complete ordinary-process wire snapshots on five client surfaces.
- [PR #22 errors](pr-22-error-matrix.json): all 19 published error codes and real wire/decoder scenarios.
- [PR #22 routes](pr-22-route-matrix.json): all 22 operations across five client surfaces, positive/negative parity.
- [PR #22 validation](pr-22-validation.json): qualified Git/source fingerprint, commands, tests and GUI/artifact outcomes.

[Changelog](changelog.md) entries are historical state at the time of each change.

## Documentation checks

Run `python/.venv/bin/python tests/validate-documentation.py` from the repository
root. It checks every tracked or unignored Markdown file's relative links and
heading/HTML anchors without network access, and rejects stale milestone phrases
only in current-facing docs. Run `--self-test` to exercise valid/broken links,
duplicate headings, reference links and historical-language exclusions.
External HTTP links are not fetched by this offline check.
