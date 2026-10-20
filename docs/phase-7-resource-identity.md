# Phase 7 — Raw resource identity and copy-admission gate

**Status:** internal gate implemented and executed locally on 2026-10-20;
**PARTIAL / BLOCKED for public raw-resource exposure**. This follows the
[resource-decoding feasibility gate](phase-7-resource-feasibility.md).
Test-only bounded copies preserve explicitly supplied input slots and original
names, but generic Jadx resource handles do not establish accepted-artifact
ownership. No production implementation, public route, OpenAPI/SDK model,
capability status, filesystem policy, dependency pin or native format changes.

## Scope and independent oracle

[RawResourceIdentityProbeTest](../src/test/java/dev/libjadx/probes/RawResourceIdentityProbeTest.java)
adds eight executable probes. ZIP fixtures are generated in JUnit temporary
directories from owned binary/empty entries, a nested ZIP and the existing
[owned Smali assembler](../src/test/java/dev/libjadx/probes/HierarchyFixture.java).
The runtime case contains real DEX and a public, unsaved native class rename.
No Android SDK, downloaded compiler or network is needed by these tests.

Two asymmetric five-byte payloads distinguish first/second occurrences and old/new
files. Duplicate names are made by replacing only equal-length local/central
name fields in an owned STORE archive. An independent local-header reader checks
both physical payloads before testing Jadx. The copied first payload's SHA-256
is independently checked with `printf '\000\377\003\010\015' | sha256sum`:
`0477f75981429fe533b2b25953e9546fd5a2984148900221c217a637c89597ea`.
The test does not derive expected content from a name lookup in the reader
whose duplicate behavior it is investigating.

## Pinned source audit

Jadx **1.5.6**, source
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, JDK 21, Gradle 8.14.3.
The original upstream archive again hashes to
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
An Apache-notice-preserving source subset and per-file hashes are retained in
excluded `.amp/in/artifacts/resource-identity/`.

| Pinned source | Relevant boundary |
| --- | --- |
| [JadxDecompiler](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java), `load`, `getResources`, `close` | Loading already enumerates/processes resources. The cached resource list and parser owners belong to the engine, not the caller. |
| [ResourcesLoader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/ResourcesLoader.java), `loadFile`, `defaultLoadFile`, `addEntry`, `decodeStream` | Ordinary input slots append independently, directories are skipped, ZIP directories are omitted and nested archives are opaque entries. Custom plugin loaders can differ. Raw archive reads use retained entries; standalone reads reopen the pathname. |
| [ResourceFile](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/ResourceFile.java) | Original name, mutable display alias and retained `IZipEntry` are distinct. There is no configured input-slot ID or consumed-artifact hash. |
| [ZipContent](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/ZipContent.java) | The entry list retains duplicates, but the eager name map replaces earlier occurrences. |
| [ZipReader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/ZipReader.java), [JadxZipParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/parser/JadxZipParser.java) | The ordinary parser copies archives smaller than 100 MiB into heap; larger files are mapped. Allocation happens before the entry-count check. Delayed fallback can reopen the pathname and resolve an entry by name. Neither behavior is a universal immutable-artifact contract. |
| [FallbackZipParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/fallback/FallbackZipParser.java), `open`, `getEntryStream` | The Java ZipFile is opened before Jadx's enumeration limits. Streams use Java entry lookup; occurrence identity needs runtime proof. |
| [JadxZipSecurity](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/security/JadxZipSecurity.java), [LimitedInputStream](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/io/LimitedInputStream.java) | Defaults filter unsafe names/sizes, cap each archive at 100000 entries and check expansion ratios starting at 25 MiB. These are not aggregate raw-byte/parser-memory limits or physical-inventory completeness. |

The mapped-file, delayed-fallback and plugin-loader observations are source-audit
limits, **not executed adversarial qualifications**. No large/mapped ZIP, mount,
ZIP64, hostile-allocation or hanging-input corpus was run in this gate.

## Executed results

| Probe | Result and permitted conclusion |
| --- | --- |
| Explicit slots, repeated inputs, aliases, empty/binary content | Calling the ordinary loader separately per known input preserves slot 0/1/2 even when slots 0 and 2 repeat a path. Same names in different slots return distinct expected bytes. Display alias changes do not change original names/content. Immutable copied strings survive engine close and file deletion; a retained STORE ResourceFile fails after close. No live Jadx object or mutable array is published. |
| Duplicate names and both actual parsers | The normal parser returns first/second physical payloads separately; its name lookup returns only the last occurrence. Forced fallback on the installed OpenJDK 21 returns the second payload for **both** enumerated entries. The candidate refuses duplicate original names within a slot rather than labeling these as two exact occurrences. This is a runtime counterexample, not a portable promise about every JDK. |
| Nested entries, filtering, directory input and names | A nested ZIP is returned byte-exact as one opaque resource, not recursively enumerated. The physical ZIP has four entries; the Jadx view excludes a directory and traversal entry. A Unicode entry is also excluded under the host's POSIX/ASCII filename encoding, but preserved under UTF-8. The actual directory input produces no resources. Neither view is a complete original archive/filesystem census. |
| Runtime artifact replacement/deletion | Under the same session/logical/settings/publication identity, a small archive keeps old bytes after replacement, while a standalone resource returns newly written bytes. Deleting both still permits the cached archive read but fails the standalone read. A failed callback releases the primary lease; restored inputs permit healthy reads and retain the pending class rename/dirty state. Conflicting primary reads fail PROJECT_BUSY. No save/export is requested. |
| Accepted baseline versus handles | Existing repository baseline checks reject a changed archive, retain both repeated accepted slots, and pass after restoration. Already enumerated resource handles independently return cached old bytes. A pathname baseline check is not proof of the bytes every handle consumes, nor protection against an intervening replace/restore. |
| Inclusive copy ceilings | Exact slot/count/name/per-entry/aggregate-byte boundaries pass; the first excess for each refuses the entire copy. Repeated slots count again. A healthy copy succeeds after refusals. |
| Actual bounded stream consumption | Empty/exact content passes; truncation, underreported length and one byte beyond the budget refuse. These in-memory stream controls validate the candidate's copying rule, not archive-parser admission. |
| Real malformed DEFLATE | An owned STORE payload relabeled as DEFLATE in both headers fails through the real resource stream. No partial list is returned; a healthy sibling remains readable. This does not qualify arbitrary malformed archives or sanitize upstream messages for HTTP. |

## Test-only candidate and rejected shortcuts

The private candidate copies only an already enumerated view. Experimental
ceilings are four explicit groups, 32 entries, 4096 original-name UTF-16 units,
8192 bytes per entry and 16384 aggregate raw bytes, with immutable Base64 and
SHA-256 strings. Metadata admission precedes copying; each read is independently
bounded to remaining capacity plus one sentinel byte and must match the declared
length. No partial candidate result escapes. Empty content differs from failure.
These are experiment limits, not configuration or new service guarantees.

Input-slot identity is supplied at enumeration, never guessed from canonical
pathname or entry name. The runtime freshness probe deliberately uses a single
flattened comparison group and makes **no original-slot provenance claim**.
Repeated paths cannot be disambiguated from the flattened view by pathname alone.
No manifest helper, alias or resource-table ID is an archive-entry identity.

Counting an existing `getResources()` list cannot prevent earlier ZIP buffers,
name maps, plugin extraction or table-parser allocations. Similarly, rejecting
duplicate names in the filtered view does not establish that every physical
entry was inventoried. Bounded `decodeStream` output alone solves neither issue.
No production security defaults are disabled or modified to make these cases pass.

## Decision and next work

**PARTIAL internally; public adoption remains BLOCKED.** The candidate establishes
bounded detached copies and conservative identity rules for its explicit ordinary
loader experiment. It does not establish generic accepted-input ownership or
pre-parser aggregate admission. Exposing this as the planned resource feature
would silently overstate those guarantees, so no dependent HTTP implementation
was added.

The smallest follow-on is a reviewed, narrow raw-archive acquisition design and
an executable **pre-parser** admission/ownership gate. It must specify which
ordinary archive/standalone formats are admitted; bind actually consumed bytes
to accepted ordered input slots without reopening paths; preserve original names;
refuse ambiguous duplicates, unsupported containers and unproved fallback paths;
bound archive bytes, metadata and expanded totals before costly parsing; and
report filtered versus physical coverage explicitly. Temporary input persistence,
a parser fork, native dependency or weaker allowed-root semantics is not approved
by this work. The separate [filesystem-containment decision](phase-7-owned-input-containment.md#proposed-design-decision--not-approved)
still applies to any new filesystem acquisition path. Only after a successful
ownership/admission gate should the public inventory/raw-content OpenAPI slice
and generated/handwritten SDK methods be implemented. XML/manifest/string
decoding remains a separate precision and allocation proof.

## Validation and delivery

Executed on Linux x86_64 kernel 6.18.55, Ubuntu OpenJDK
`21.0.12.1+1-1-26.04.4-Ubuntu`, with the existing locked dependencies. The local
`/.amp/in/` exclude rule was verified before writing source-audit and test
artifacts under `.amp/in/artifacts/resource-identity/`.

The first focused run failed because the independent Unicode-name expectation
assumed a UTF-8 host filename encoding. The actual POSIX runtime used ASCII;
Jadx's path-name security check excluded the entry. The retained control now
accounts for the independently observed JDK filename encoding and is exercised
in both locales, without weakening security. The first UTF-8 rerun stalled
before task execution in a Gradle cache-lock UDP notification; thread dumps
identified the unrelated daemon handshake. That command was terminated and the
idle cache-owner daemon stopped before rerunning. Neither attempt counts as a
passing validation.

```bash
./gradlew test --tests dev.libjadx.probes.RawResourceIdentityProbeTest --no-build-cache
# focused-3.log / XML: 8 passed, no failures/errors/skips; POSIX/ASCII name control.

LC_ALL=C.UTF-8 ./gradlew test --tests dev.libjadx.probes.RawResourceIdentityProbeTest \
  --no-build-cache --rerun-tasks
# focused-utf8-recovered.log / XML: all 8 executed/passed; UTF-8 name preserved.

LC_ALL=C.UTF-8 ./gradlew clean test check installDist --no-build-cache
# java-full.log / java-full-xml: BUILD SUCCESSFUL in 7m 3s; 594 tests,
# 578 passed, 16 existing opt-in matching-GUI skips, zero failures/errors.

# After deleting this task's disposable upstream-source downloads/extraction:
LC_ALL=C.UTF-8 ./gradlew test --offline --no-build-cache --rerun-tasks \
  --tests dev.libjadx.probes.RawResourceIdentityProbeTest
# offline-executed.log / XML: all 8 executed/passed in 4s, zero skips.

python/.venv/bin/python tests/validate-openapi.py
# openapi.log: valid 3.1; 23 operations, 42 JSON examples, 3 SSE frames.
python/.venv/bin/python tests/validate-documentation.py
# documentation.log: 62 Markdown files, 583 valid relative links/anchors.
python/.venv/bin/python tests/validate-documentation.py --self-test
# documentation-self-test.log: positive/negative controls passed.
python/.venv/bin/python tests/validate-license.py
# license.log: Apache-2.0 metadata, documentation and notices valid.
git diff --check
# Passed; the two new files also passed separate trailing-whitespace/newline checks.
```

Native serialization and public behavior are unchanged. Existing native
save/reload regressions are relevant; no fresh matching-GUI or release
qualification is claimed. Ordinary documentation also changes the strict
release fingerprint. No public contract/Python source changed, so SDK generation
and Python suites are not required for this internal gate. Changes remain local
and uncommitted at handoff; no publication is authorized.
