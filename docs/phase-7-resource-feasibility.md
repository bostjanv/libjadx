# Phase 7 — Android resource-decoding feasibility

**Status:** internal feasibility gate executed locally on 2026-10-20;
**PARTIAL / DEGRADED**, not a public resource feature. Eight real-Jadx probes and
an independently owned compiled fixture establish raw-entry access, declared
manifest facts and configuration-scoped table records. They also retain actual
decode corruption and helper/failure counterexamples. `/api/v1/resources/query`
still returns `OPERATION_NOT_IMPLEMENTED` after readiness, has no OpenAPI operation
and advertises no formal resource capability. No production source, pins, SDK or
native persistence format changes.

This is an independent Phase 7 work item, not a workaround for the separately
blocked [owned-input containment gate](phase-7-owned-input-containment.md). No
filesystem policy is weakened and no public CFG work is enabled.

## Fixture and independent oracle

[Owned fixture sources and regeneration instructions](../tests/fixtures/resources/README.md)
include a binary manifest/XML, default and Slovenian strings, aliases/chains,
framework references, an array and UTF-8 asset. The committed unsigned resource
archive is compiled with aapt2 `9.2.1-15009934` and API 35 revision 2 symbols;
neither tool nor framework JAR is shipped. Tests add real owned DEX and empty,
binary and misleadingly named XML assets with the existing pinned assembler.
No device, SDK, compiler download or network is required by the Java test suite.

Expected permission/component attributes, resource IDs, variant values and raw
bytes come from the owned sources and independent aapt2 dumps, not Jadx output.
The compiled fixture has SHA-256
`595459df2f52b61d96023c84e3dec69d4d961fa40f21d0cf7a22cbd0a8821b5e`.
It is not an installable app and does not prove Android runtime behavior.

## Pinned source evidence

Audited source is Jadx **1.5.6**, commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, not current master. The downloaded
codeload archive has SHA-256
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
Audited source hashes and an Apache-notice-preserving subset archive are retained
with the local execution artifacts.

| Pinned source | Contract established by inspection |
| --- | --- |
| [JadxDecompiler](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java), `getResources()` / `load()` | Lazily cached resource enumeration; loading already processes the first resource table. It is not a pure independent resource parser. |
| [ResourceFile](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/ResourceFile.java) and [ResourcesLoader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/ResourcesLoader.java) | Original/display names, filename-based type and ZIP origin are separate. `decodeStream(ResourceFile, ResourceDecoder<T>)` supplies uncompressed entry bytes. `loadContent()` instead returns presentation content/links and catches exceptions into text plus stack trace. |
| [RootNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/RootNode.java), `loadResources` / `processResources` | First-table names update engine resources and generated R declarations; exceptions are logged rather than a resource availability result. Table/name processing belongs behind serialized engine access. |
| [BinaryXMLParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/BinaryXMLParser.java), `parse(InputStream)` / `decodeAttribute` | Header heuristic selects binary vs UTF-8 pass-through. Binary attributes name known references, preserve unknown IDs as hex and rewrite known `android:name` classes through aliases. Text pass-through is not XML validation. |
| [XmlDeobf](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/XmlDeobf.java), `deobfClassName` | Even an unrenamed known relative class name may become its full name. Decoded XML is an engine-derived view, not original bytes. |
| [ResTableBinaryParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/ResTableBinaryParser.java), [ResourceEntry](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/entry/ResourceEntry.java) and [ValuesParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/entry/ValuesParser.java) | Internal parser retains numeric IDs, type/key/package, qualifier string and typed scalar/bag records. Rendering names a reference but does not recursively resolve aliases or select an Android configuration. |
| [BinaryXMLStrings](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/BinaryXMLStrings.java), `extractString8` | Uses ordinary Java UTF-8 for the pool bytes. The owned aapt2 pool's surrogate-form supplementary character is corrupted; see executed counterexample below. |
| [ResXmlGen](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/ResXmlGen.java) and [ResContainer](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/xmlgen/ResContainer.java) | Generated values XML is a view, not original source files or a full file inventory. Container type must be checked before casting payloads; TEXT does not mean successful decode. |
| [AndroidManifestParser](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/utils/android/AndroidManifestParser.java) / [AppAttribute](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/utils/android/AppAttribute.java) | Metadata helper only: no comprehensive permission/component inventory; first manifest, literal `android:` prefix, first matching activity before aliases, no enabled/exported evaluation, one caller-provided strings document. |
| [JadxSecurity](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/security/impl/JadxSecurity.java) and [JadxZipSecurity](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-commons/jadx-zip/src/main/java/jadx/zip/security/JadxZipSecurity.java) | Default DOM parsing disables DOCTYPE/external entities. ZIP entry/ratio checks are not aggregate allocation budgets for binary parsers. |

## Executed results

[JadxResourceProbeTest](../src/test/java/dev/libjadx/probes/JadxResourceProbeTest.java)
runs eight tests using real pinned Jadx, including one through repository-backed
runtime admission and an ordinary public edit batch.

| Probe | Observation / permitted conclusion |
| --- | --- |
| Enumeration and compiled manifest/XML | Eight ZIP entries include `classes.dex` and assets, not just Android `res/` entries. Two requested permissions including maxSdk, declared permission, activities, disabled/exported flags, alias/target, service, receiver and provider/authority/permission survive decoding. This is declared metadata, not effective Android permission or component resolution. |
| Table variants and references | Ten ID/configuration records retain default and `-sl` variants; shared numeric IDs do not identify a unique variant. Alias and chain stay `@string/greeting` / `@string/alias`; framework reference names are recoverable. Raw typed array members remain distinct from scalar values. Generated values XML contains three files and no synthetic copy of the compiled XML entry. No locale selection or transitive resolution is proved. |
| Supplementary Unicode counterexample | Source and aapt2 dump read `Hello Živa 🚀` / `Pozdrav Živa 🚀`. The table contains `ed a0 bd ed ba 80`, the surrogate-form encoding for U+1F680, at two pool locations, not four-byte UTF-8. Jadx table values **and generated strings XML** instead contain U+FFFD twice. BMP `Ž` survives; the independent raw UTF-8 asset retains the rocket. The defect is retained, not repaired or excused as a fixture failure. Decoded strings are not lossless. |
| Raw bytes and lifecycle | Byte-exact empty, NUL/0xff-containing and UTF-8 assets; ordinary assets return RES_LINK through `loadContent()`. Binary manifest bytes remain distinct from decoded XML. Reads leave the owned class unprocessed, and repeat after Java generation. APK bytes remain unchanged; no `.jadx` or export is created and the test closes its decompiler. Deleting the input on Linux is a cleanup check, not proof of Windows handle release. |
| Manifest helper limits | Reports disabled `.DisabledActivity` even though the fixture has an active launcher alias. Label lookup requires the supplied strings document and changes when the caller supplies `-sl`. A text manifest with equivalent `a:` Android namespace remains readable but helper application/launcher extraction fails. Do not expose helper output as comprehensive resolved metadata. |
| Unsaved alias and runtime boundary | Public native class rename changes decoded `android:name` to `dev.libjadx.resourceprobe.RenamedActivity`; raw manifest bytes do not change. The callback uses serialized primary access; conflicting primary admission fails PROJECT_BUSY. Snapshot, dirty state and native input remain unchanged by the read. No save is requested. |
| Malformed XML/table and healthy controls | Invalid binary resource-map header and wrong-magic ARSC throw through direct parser paths, but `loadContent()` produces TEXT containing error/stack trace. Malformed text XML passes through until explicit DOM validation; DOCTYPE is rejected by the default secure DOM parser. A malformed table is logged during `load()` without making the engine unusable; raw assets and healthy Java still work. A healthy manifest decodes after binary-XML failure. These are small deterministic failure controls, not hostile-parser qualification. |
| Unknown vs literal references | The fixture compiler encodes `@0x7f0200ff` as STRING. A separate test changes exactly one serialized TYPE_REFERENCE's data to that unknown ID; decoded XML renders `0x7f0200ff`, while the literal stays `@0x7f0200ff`. Prefix matching cannot prove resource edges. |
| Multiple inputs / standalone file | Two manifests keep distinct archive backing paths, but the helper selects the first. Standalone absolute `AndroidManifest.xml` is classified XML, not MANIFEST, and has no ZIP backing. This does not prove nested-bundle/split/multitable identities or completeness. |

The initial compile attempt incorrectly used a package-private edit method and
the wrong dirty accessor; the probe now uses public `EditBatchService` and
`ProjectSnapshot.dirty()`. The next run deliberately expected lossless source
values and exposed the Unicode corruption, relative-name expansion and generic
array-rendering differences. Its failed XML/log is retained separately. Final
passing tests explicitly assert retained counterexamples; a green test suite
does not turn them into supported behavior.

## Decision and next slice

**PARTIAL / DEGRADED internal feasibility.** A next OpenAPI-first slice should
start with bounded resource inventory and raw uncompressed content, preserving
archive/input-slot identity and original entry names. Keep raw bytes distinct
from decoded/generated content; do not publish mutable `ResourceFile`, parser,
`ResourceEntry` or `ResContainer` objects. The stable identity policy for duplicate
entry names and nested/multiple archives needs explicit contract tests rather
than the helper's first-match behavior.

Decoded manifest/XML, variant strings and typed references need a separate
adapter with explicit AVAILABLE/UNAVAILABLE/FAILED/partial semantics, sanitized
errors and completeness/precision per operation. Never infer success from TEXT,
return a stack trace as source, silently follow aliases or drop configurations.
Do not advertise lossless strings until the supplementary-character defect has
a separately proved compatible solution. Any namespace-aware component extractor
should retain declarations/flags/relative spelling and report unresolved classes,
not promise Android package-manager or framework resolution.

Resource observations on the primary engine need existing serialized admission
and session/logical/settings/publication binding; raw content additionally needs
an explicitly proved artifact-validity/ownership policy. This gate does not solve
filesystem races, mount/ZIP identity, changed source archives or cache reuse.
Do not assume the owned-DEX path handles APK resources.

Before public decoding, prove limits for entry count, uncompressed input,
aggregate strings/records/output and nested archives **before** costly decoding.
Pinned pools and table counts drive allocations; bounded stream reads alone do
not guarantee bounded heap or work. No malicious-allocation/OOM/hang corpus was
run here, nor hard cancellation, original provenance, AAB/protobuf tables,
9-patch transformations, splits/overlays, runtime locale selection, exhaustive
relationships, classpath or resource editing. No worker, native dependency,
persisted input copy, parser fork or Jadx upgrade is proposed in this patch.

## Validation and delivery

Linux x86_64, kernel 6.18.55, Ubuntu OpenJDK
`21.0.12.1+1-1-26.04.4-Ubuntu`, Gradle 8.14.3 and existing locked Jadx 1.5.6.
Review artifacts are under excluded `.amp/in/artifacts/resource-feasibility/`;
the repository-local `/.amp/in/` rule was checked before writing there.

```bash
./gradlew test --tests dev.libjadx.probes.JadxResourceProbeTest
# focused-final.log / focused-final.xml: 8 passed, no failures/errors/skips.

AAPT2="$PWD/.amp/in/resource-feasibility/aapt2-dist/aapt2" \
  ANDROID_JAR="$PWD/.amp/in/resource-feasibility/android-35/android.jar" \
  TZ=Pacific/Honolulu bash tests/fixtures/resources/build-fixture.sh \
  "$PWD/.amp/in/resource-feasibility/rebuilt.apk"
cmp tests/fixtures/resources/resources.apk .amp/in/resource-feasibility/rebuilt.apk
bash -n tests/fixtures/resources/build-fixture.sh
# Byte-identical rebuilt fixture; syntax check passed. Repeated with the fixture
# sources copied to .amp/in/resource-feasibility/relocated/resources: same hash.

./gradlew clean test check installDist --no-build-cache
# java-full.log / java-full-xml: BUILD SUCCESSFUL in 7m 4s; 586 tests,
# 570 passed, 16 existing opt-in matching-GUI skips, zero failures/errors.

# After deleting this task's disposable Android tools/SDK/source downloads:
./gradlew test --offline --no-build-cache --rerun-tasks \
  --tests dev.libjadx.probes.JadxResourceProbeTest
# offline-no-sdk-executed.log / XML: all 8 executed and passed in a 5s build.
# An earlier offline command restored cached results and is NOT execution proof.

python/.venv/bin/python tests/validate-openapi.py
# openapi.log: valid OpenAPI 3.1; 23 operations, 42 JSON examples, 3 SSE frames.
python/.venv/bin/python tests/validate-documentation.py
# documentation.log: 61 Markdown files, 575 valid relative links/anchors.
python/.venv/bin/python tests/validate-documentation.py --self-test
# documentation-self-test.log: positive/negative controls passed.
python/.venv/bin/python tests/validate-license.py
# license.log: Apache-2.0 metadata, documentation and notices valid.
git diff --check
# Passed; all 9 new text files also passed separate no-index whitespace checks.
```

Native save/reload regression tests ran in the full Java suite. Serialization
and public behavior are unchanged; no new matching-GUI or release qualification
is claimed. No Python or public contract files changed, so SDK regeneration and
Python suites were not rerun. Ordinary documentation changes also enter the
strict release fingerprint. Changes remain local and uncommitted at handoff;
this work does not authorize publication.
