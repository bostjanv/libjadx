# Phase 7 — Memory-backed raw archive admission gate

**Status:** test-only gate executed locally on 2026-10-20;
**PARTIAL / BLOCKED for public adoption**. This implements the narrow follow-on
investigation from the [raw-resource identity gate](phase-7-resource-identity.md).
Encoded-byte admission and detached STORE-entry copies work on already supplied
buffers. The ordinary JDK stream does not validate a complete archive: missing
central directories, truncated next headers and local/central disagreements are
retained counterexamples. No production acquisition, resource API, OpenAPI/SDK,
capability, native format, dependency or filesystem-policy changes.

## Investigated design and ownership boundary

The proposed narrow shape is ordered accepted input slots, bounded private bytes,
then metadata/content admission, then immutable original-name/content values.
It must never reopen paths, infer input slots from names, publish borrowed Jadx
handles or silently accept replacement content. Physical archive completeness
must be established separately from a filtered Jadx resource view.

[RawResourceAdmissionProbeTest](../src/test/java/dev/libjadx/probes/RawResourceAdmissionProbeTest.java)
implements a private **local-record observation**, deliberately not an admitted
complete archive result. It uses the existing public JDK `ZipInputStream`, not
a parser fork or reflection. Fixture callers supply buffers; the repository case
supplies the actual accepted ordered fingerprints, including a repeated path.
All slot/encoded-byte limits precede cloning or parsing; all clones are hashed
against supplied accepted fingerprints before any ZIP stream is constructed.
Only those private arrays feed the stream. Path deletion before parsing and
detached-result mutation controls distinguish this from path-backed handles.

The fixture's `Files.readAllBytes` is **not** a proposed acquisition mechanism.
The experiment starts after acquisition and makes no claim about bounded file
opens, allowed-root containment, concurrent buffer writers or production accepted
artifact ownership. The separate [filesystem decision](phase-7-owned-input-containment.md#proposed-design-decision--not-approved)
remains required; hashes establish content equality, not filesystem location.

Experimental limits: four ordered slots, 32768 encoded bytes per archive,
65536 encoded bytes in aggregate, 32 local entries, 4096 aggregate original-name
UTF-16 units, 8192 bytes per entry and 16384 aggregate content bytes. Repeated
slots count again. STORE only; DEFLATE is refused before entry-data consumption.
Directories, default-Jadx-unsafe names and same-slot duplicate names refuse the
whole observation rather than being filtered. Nested ZIPs are opaque byte leaves.
No table/XML/manifest decoder, plugin, extraction, source generation or persisted
copy is invoked. Results contain only immutable IDs, SHA-256 and Base64 strings;
all streams close on success/refusal, and no partial list is returned on an error.

These are probe limits, not service configuration. JDK entry-header allocation
occurs before the caller sees an entry; a name limit after `getNextEntry` is not
a pre-allocation name limit. Encoded-byte bounds constrain supplied data, not
the heap already used to acquire it or every parser/native allocation. No hard
heap, stack, deadline or hostile-parser safety guarantee follows.

## Source evidence

Reused and read the preceding gate's audited Jadx source subset, originally from
**1.5.6**, source `28ff15e4ae69950aebea110a13e5ab895d234dfc`, archive SHA-256
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
No API signatures were taken from current master.

- [ZipReader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/ZipReader.java)
  exposes `open(File)`, not a public byte-array/buffer/stream archive opener.
- [JadxZipParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/parser/JadxZipParser.java)
  loads its private buffer from a file before checking entry limits; delayed
  fallback reopens that file. `DONT_USE_FALLBACK` does not replace the initial
  file read. `REPORT_TAMPERING` logs some size/method disagreements, not a strict
  whole-archive validation. It does not compare local and central entry names.
- [FallbackZipParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/fallback/FallbackZipParser.java)
  constructs a file-backed `ZipFile` before its enumeration controls.
- [ResourcesLoader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/ResourcesLoader.java)
  accepts already supplied `IZipEntry` objects, but that is not an in-memory ZIP
  parser/admission contract. No supported memory archive opener was found in
  these four classes. Reflective private-buffer injection was not attempted.

Audited the matching Ubuntu OpenJDK `21.0.12.1+1-1-26.04.4-Ubuntu` source ZIP
retained by the containment gate, with per-file hashes and installed
`javap -p -c` output retained under excluded
`.amp/in/artifacts/resource-admission/`. The matching upstream
[ZipInputStream source](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12.1%2B1/src/java.base/share/classes/java/util/zip/ZipInputStream.java)
documents local-header-only access. `readLOC` returns null on EOF while reading
the next fixed header, or a non-local signature, without central-directory
validation. Name/extra allocation precedes caller admission. STORE reads check
payload CRC and unexpected EOF; `close()` releases the inflater/input without
draining a refused entry. Those are narrower properties than valid ZIP identity.

## Executed probes and independent controls

All synthetic archives come from owned STORE fixtures generated in JUnit temp
directories; mutations change known local/central fields, not production parser
code. Two asymmetric payloads and a fixed independent payload hash distinguish
slots and content. The existing owned 6486-byte [compiled resource fixture](../tests/fixtures/resources/README.md)
retains its fixed SHA-256; no SDK, compiler download or network is needed.

| Probe | Observation and permitted conclusion |
| --- | --- |
| Accepted ordered slots and detached lifetime | Actual repository fingerprints bind first/second/repeated buffers. Copies retain expected distinct bytes and empty content after paths are deleted, real Jadx closes and caller/result arrays are modified. Accepted baselines stay unchanged. This proves post-acquisition memory behavior, not a new filesystem policy. |
| Encoded and all-slot hash admission | Exact slot/per-archive/aggregate encoded limits pass; first excess refuses. An otherwise unparsable first archive plus a mismatched later hash reports HASH, establishing that parsing has not started before all hashes pass. Healthy work succeeds after refusal. |
| Local metadata/content limits | Exact entry/name/per-entry/aggregate limits pass; first excess refuses, including repeated slots and a dishonest huge declared length. No allocation uses that declared content length. |
| Unsupported/ambiguous inputs | Same-slot duplicate local names, traversal/absolute names and directories refuse. Real DEFLATE with a data descriptor/high expansion refuses before reading entry data. No entry is extracted. ZIP64, encrypted, split and other containers are not qualified or established to be rejected by this experiment. |
| Real compiled APK and opaque nested ZIP | Four raw entries byte-match pinned Jadx with fallback disabled, including binary manifest/XML/table bytes, without invoking binary decoders. Nested ZIP content stays one byte-exact leaf. This is not a general Android package-format admission guarantee. |
| Payload corruption/truncation | Changed STORE payload CRC and truncated payload throw through the real JDK stream, including after a healthy earlier slot. Healthy observation succeeds afterwards; no partial result escapes. |
| Missing central directory / next header | Removing the central directory leaves the same successful local payload, while actual `ZipFile` rejects the file. Three non-ZIP bytes appear as an empty stream; a partial next local header is ignored. End-of-local-stream is not valid-archive completion. |
| Central/local identity and inventory disagreement | Changing only the central name makes `ZipFile` report `assets/next.bin`, while the local stream and pinned Jadx REPORT_TAMPERING report `assets/item.bin`. Zeroing central entry counts leaves one local observation but no entries from pinned Jadx. Neither view alone is an independently verified physical inventory. |

Passing tests retain the latter failures; they do **not** mean archive admission
passed. The malformed fixtures remain reproducible in the probe source.

## Decision and next approval boundary

**PARTIAL / BLOCKED.** Do not adopt `localView` or label it COMPLETE/PARTIAL public
inventory. Even the STORE-only subset lacks a fail-closed whole-archive validator.
Adding a magic/EOCD presence check, counting local records, combining two filtered
lists or enabling REPORT_TAMPERING would not establish local/central consistency,
nonoverlap, lengths, names, flags and complete consumption.

The smallest next design decision is whether to authorize investigation of a
strict, memory-backed archive validator with explicit pre-allocation budgets
(an upstream API extension or a separately approved parser boundary), **alongside**
the existing filesystem-acquisition decision. This gate does not establish that
such a validator is available or safe, and adds no parser dependency/fork, private
field access, temporary persisted input or worker. A decision to weaken inventory
semantics or allowed-root guarantees needs explicit human approval instead; it
must not be smuggled into an implementation. Until both boundaries have executable
proof, public resource inventory/raw content remains deferred. Standalone files,
DEFLATE, decoding precision and parser work/heap admission need their own proof.

Native persistence, primary loading, existing temporary overrides, public
capabilities and revision semantics remain unchanged. GUI qualification is not
needed for these test-only observations; no fresh GUI/release qualification is
claimed. Source and ordinary documentation change the strict release fingerprint.

## Validation and delivery

Linux x86_64, OpenJDK 21.0.12.1, Gradle 8.14.3 and locked Jadx 1.5.6. The local
`/.amp/in/` exclude rule was verified before writing artifacts. The initial
focused compile used the fingerprint record where a hash string was required;
`focused-1.log` retains that failure. The corrected run uses the record's
`sha256()` value and its existing `sha256:` prefix. No failed command counts
as a pass.

```bash
LC_ALL=C.UTF-8 ./gradlew test --tests dev.libjadx.probes.RawResourceAdmissionProbeTest --no-build-cache
# focused-2.log / XML: eight passed, zero failures/errors/skips.

LC_ALL=C.UTF-8 ./gradlew test --tests '*Resource*ProbeTest' \
  --tests '*NativeProjectRepositoryTest' --no-build-cache
# regressions.log / regressions-xml: 34 passed, zero failures/errors/skips.

LC_ALL=C.UTF-8 ./gradlew clean test check installDist --no-build-cache
# java-full.log / java-full-xml: BUILD SUCCESSFUL in 6m 57s; 602 total,
# 586 passed, 16 existing opt-in GUI skips, zero failures/errors.
# All eight new probes executed, without skips.

# After removing this task's disposable extracted Jadx source subset:
LC_ALL=C.UTF-8 ./gradlew test --offline --no-build-cache --rerun-tasks \
  --tests dev.libjadx.probes.RawResourceAdmissionProbeTest
# offline.log / XML: all eight executed and passed in a 4s build; no skips.

python/.venv/bin/python tests/validate-openapi.py
# openapi.log: valid OpenAPI 3.1; 23 operations, 42 JSON examples, 3 SSE frames.
python/.venv/bin/python tests/validate-documentation.py
# documentation.log: 63 Markdown files, 591 valid relative links/anchors.
python/.venv/bin/python tests/validate-documentation.py --self-test
# documentation-self-test.log: positive/negative controls passed.
python/.venv/bin/python tests/validate-license.py
# license.log: Apache-2.0 metadata, documentation and notices valid.
git diff --check
# Passed; the two new files also passed newline/trailing-whitespace checks.
```

Existing native save/reload, concurrency, cancellation and conflict regressions
ran in the full Java suite. No fresh matching-GUI gate was requested or claimed:
this slice changes only test sources and documentation. At implementation handoff,
no Python or public contract source had changed, so SDK generation and Python
suites had not been rerun.

The implementation handoff initially left changes local and uncommitted.
Subsequent Git history records delivery; no release tag, deployment or package
publication is implied.
