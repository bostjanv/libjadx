# Phase 5.1: process-local incremental search

LibJadx 0.1 exposes `POST /api/v1/search` and `POST /api/v1/search/build-index`.
Search is read-only. It does not save native projects, write an index sidecar, or
change `logicalRevision` or `dirty`. The index and all result cursors disappear
on process restart. `indexGeneration` tracks search materialization and eviction;
it is separate from native `RevisionSet.indexRevision`.

## Search domains and meaning

| Domain | Population | Complete means |
|---|---|---|
| `CLASS_NAME` | One lightweight `getClassesWithInners()` catalog; no class Java generation | Every currently Jadx-visible class was cataloged. |
| `MEMBER_NAME` | A class is processed by `/decompile` or the complete-index job | Every currently Jadx-visible class's original method/field refs and aliases was processed, including empty member lists. |
| `SOURCE_TEXT` | Exact emitted owner Java from primary-mode `/decompile` or the complete-index job | Every eligible emitted owner was indexed or verified as having no emitted Java, with no failure or eviction. |
| `STRING_LITERAL` | Not indexed | Rejected with 422 `UNSUPPORTED_CAPABILITY`; emitted-token versus original-bytecode ownership has not been proved. |

Annotation, constant, resource metadata and reference search are deferred.
`sourceInputCoverage` remains `UNVERIFIED` even when a supported domain reports
`COMPLETE`: Jadx may merge or discard original definitions. `SOURCE_TEXT` hits
are owner-level and do not guess a method. Their ranges use half-open UTF-16
offsets in the exact Java string and the same `sourceSnapshotId` as `/decompile`.
Source errors, evictions and pending owners keep coverage `PARTIAL`; a successful
job can therefore return `completeness=PARTIAL`.

For example:

```json
{"query":"mix","domains":["MEMBER_NAME","SOURCE_TEXT"],"matchMode":"CONTAINS","pageSize":25}
```

`EXACT` matches a whole symbol name or descriptor; for emitted Java it requires
Java identifier boundaries around the literal text. `CONTAINS` finds substrings.
`REGEX` is available for `SOURCE_TEXT` only. It uses pinned RE2/J 1.8 rather
than Java's backtracking regex engine; unsupported constructs such as
backreferences and lookaround return 422. Regexes are limited to 256
characters, zero-width matches are omitted, and a query exceeding 5,000 hits
or 4 MiB of copied hit DTO content returns 429. Results are sorted by original
identity and source position, never by alias alone. Each page contains at most
100 hits. An empty partial page does not prove absence.

`strict=true` rejects requested partial domains with 409 `INCOMPLETE_ANALYSIS`.
`requireComplete=true` submits or reuses a complete-index job and returns 202
when selected domains are incomplete. The caller polls the existing
`/jobs/{jobId}` route or its SSE stream, then repeats the search. With both
flags set, the build happens first. If a completed build still has failures or
evictions, another `requireComplete` query returns 409 rather than starting an
endless sequence of jobs; `force=true` on `/search/build-index` explicitly
retries. A matching running build can be reused while its engine lease blocks
ordinary searches. A rejected forced submission leaves the prior index and
cursors intact; resetting selected domains starts only after the job is admitted.
Cancellation is cooperative between classes and consistency passes.
An in-progress Jadx call cannot be interrupted safely by a deadline.

The index retains up to 64 MiB of copied member/source fragments. LRU eviction
increments `indexGeneration` and degrades coverage. Result snapshots have a
separate 16 MiB aggregate budget, 256-entry maximum and ten-minute TTL. The
catalog can also exceed the index budget; search then returns 429 `RESOURCE_LIMIT`
while an already produced `/decompile` Java response is still returned. The
signed cursor is compact, binds the session, logical revision, publication,
settings, index generation, normalized query and result snapshot, and is
verified before interpreting its fields. Tampering or changing filters yields
400 `INVALID_REQUEST`; authentic stale or expired snapshots yield 409
`STALE_REVISION` (`SEARCH_SNAPSHOT_EVICTED` for expired/evicted result storage).
The operational cursor signing key is outside the native project.

## Pinned source and executable observations

Jadx 1.5.6 is pinned to commit `4c0ac37699aa8c9803f1c73cfaacd9205acb044b`.
The audited public APIs are `jadx/api/JavaClass.java` (`getRawName`,
`getOriginalTopParentClass`, `getTopParentClass`, `getCodeInfo`, `getMethods`,
`getFields`, `getAccessInfo`, `isNoCode`), `jadx/api/JavaMethod.java`,
`JavaField.java`, `ICodeInfo.java` and `metadata/ICodeMetadata.java`.
`JadxSearchAdapter` delegates original full member signatures to the already
audited `JadxSymbolAdapter`, whose pinned internal uses are `MethodInfo`,
`FieldInfo` and `TypeGen.signature`. No Jadx type enters the index store.

`JadxSearchProbeTest` proves cold class/owner enumeration leaves the Jadx
process state unchanged and preserves return-type-only overload identities.
`SearchEndpointsTest` checks real Jadx owner source, aliases, Unicode ranges,
RE2/J behavior, partial/complete results and cursor authentication.
`SearchBuildJobTest` tests a latch-controlled cancellation and the owned empty
synthetic class. Jadx 1.5.6 does **not** mark that fixture with `isNoCode()`;
after source extraction, it is skipped only when Jadx emits no Java and its
public access flags show a synthetic class with no visible fields or methods.
Other unavailable Java is `FAILED`, never silently skipped. The source
adapter's existing 4 MiB UTF-8 limit still applies. The job makes a bounded
second source observation and stability check after visiting all visible
classes; a source that continues changing is marked failed.
The `SearchBuildJobTest` failure case deliberately injects an adapter exception
on a real pinned-Jadx class; it is a simulation of failure handling, not a
naturally failing Jadx fixture.

RE2/J 1.8 is distributed under the Go/RE2 BSD-style terms in
[`licenses/RE2J-LICENSE`](../licenses/RE2J-LICENSE). Its license file is included
with the installed distribution. Regex syntax and linear-time engine behavior
are documented by [RE2/J](https://github.com/google/re2j).

## Completion audit and validation

The Phase 5.1 work order takes precedence over the older broad Phase 5 wording:
source search preserves exact emitted Java (no normalization); string-token,
annotation, constant, resource and reference search are explicitly deferred.
There is no persistent primary analysis-mode update route in this baseline;
temporary mode requests remain isolated and never populate the primary index.
Mapping rebuild, native code-data replacement, save, reload and restart are
covered publication boundaries. Python generation remains Phase 6.

The 64 MiB budget includes conservative catalog, identity, fragment and status
accounting. A catalog that cannot fit is rejected with 429. The result budget
counts full symbol identities, display strings and diagnostics, not just hit
counts. Consistency checks retain source hashes rather than duplicate Java
strings. Snippets never split a surrogate pair and are copied directly from a
bounded range, including for regex matches spanning a large source.
Cancellation is checked after expensive extraction and before publication.
A cancelled or interrupted build remains partial until a later completed build
verifies the domain. Source/member coverage stays partial during consistency
checks. Immutable queries still use conservative coordinator admission and can
return PROJECT_BUSY while a build owns the engine.

`SearchCursorTest` uses a deterministic monotonic clock for expiry and exercises
result LRU eviction, stable page coverage and authentication before stale
classification. `SearchBuildJobTest` additionally holds an isolated test task
to verify bounded queue capacity and stale rejection after a queued build's
project reload. `SearchIndexStoreTest` covers catalog limits, fragment eviction,
unfinished-build coverage and a latch-controlled stale publication race.

After the Gradle tests, validate the full OpenAPI 3.1 document and reviewed
examples plus captured actual search HTTP responses with:

```sh
python tests/validate-search-contract.py
```

The interpreter must provide `openapi-spec-validator`, `jsonschema` and `PyYAML`.
Captures are transient build artifacts under `build/search-contract-responses`;
they include actual 200/202 responses and typed 400/409/415/422/429/503 errors.
No production dependency or persisted project artifact is introduced.
