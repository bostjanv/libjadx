# Phase 7 — Owned-input filesystem-containment gate

**Status:** gate implemented and executed locally on 2026-10-20;
**BLOCKED/DEGRADED** for hostile-filesystem containment. This follows the
[internal owned-DEX integration](phase-7-owned-input-integration.md). Real
acquisition follows moved directory handles outside the allowed root, and FIFO
replacement blocks native file and directory opens. The passing tests retain
these counterexamples; they do not mean containment passed. Production acquisition
policy, public contracts and capability statuses are unchanged. `analysis.cfg`
remains UNKNOWN and `/analysis/cfg` unimplemented.

## Gate scope and retained proof obligations

Determine whether the internal owned-DEX acquisition can enforce its existing
allowed-root and regular-file policy across ancestor relocation and file-type
replacement, without an indefinitely blocked special-file open. Preserve failing
fixtures and record an evidence-backed decision before any public graph work.

This is a source-review and executable feasibility slice, not permission to wire
a CFG endpoint or redesign filesystem access. First reproduce the current
behavior. A test-only candidate may explore the smallest compatible acquisition
adjustment; production adoption follows review of that evidence. If enforcement
requires a new native dependency, JDK-internal access, persisted input copies,
worker processes or weaker path semantics, stop dependent work and propose the
smallest design change for explicit human approval.

Keep Jadx 1.5.6 at source `28ff15e4ae69950aebea110a13e5ab895d234dfc`, JDK 21
and Gradle 8.14.3. The preceding integration ran on Linux x86_64 with OpenJDK
21.0.12.1; record the actual vendor/build, provider, kernel and filesystem used
for this gate. Neither a Linux result nor a Java API name proves cross-platform
containment or a hard wall-clock bound on arbitrary filesystem I/O.

## Current ownership path and evidence to reuse

- [NativeProjectRepository.acceptedInputs](../src/main/java/dev/libjadx/project/NativeProjectRepository.java)
  supplies already accepted, immutable ordered path/fingerprint pairs and roots.
  Repeated slots remain distinct. Acquisition must not accept a newly observed
  hash, implicitly reload or rehash under repository/lifecycle monitors.
- [ProjectRuntime.withOwnedDexRead](../src/main/java/dev/libjadx/app/ProjectRuntime.java)
  captures native edits, settings, revisions and publication identity under the
  brief temporary lease, then releases the snapshot gate before acquisition.
  It has no HTTP caller. Preserve one-temporary-operation and shutdown semantics.
- [OwnedDexAnalysis.acquire / secureOpen / descend](../src/main/java/dev/libjadx/jadxadapter/OwnedDexAnalysis.java)
  checks canonical spelling and lexical root membership, walks from the filesystem
  root using `SecureDirectoryStream` and `NOFOLLOW_LINKS`, checks regular-file
  attributes, then separately opens a read channel. Bounded bytes are compared
  with accepted content before same-reader census and isolated inputless loading.
  The attributes and open are separate operations; retained directory handles
  are not evidence that the directory remains below its original allowed root.
- [OwnedDexAdmissionTest](../src/test/java/dev/libjadx/app/OwnedDexAdmissionTest.java)
  contains the earlier **test-only** acquisition copy and replacement/read hooks.
  Its parent-symlink control runs before secure traversal, not after opening an
  ancestor. Keep those controls and the inputless-rename counterexample intact.
- [OwnedDexAnalysisTest](../src/test/java/dev/libjadx/jadxadapter/OwnedDexAnalysisTest.java)
  and [OwnedDexIntegrationTest](../src/test/java/dev/libjadx/app/OwnedDexIntegrationTest.java)
  exercise production acquisition, same-reader loading, cleanup and repository
  snapshots. Reuse [HierarchyFixture](../src/test/java/dev/libjadx/probes/HierarchyFixture.java),
  [CfgCases.smali](../tests/fixtures/cfg/CfgCases.smali) and the existing immutable
  [normalized graph observer](../src/test/java/dev/libjadx/app/NormalizedCfgIsolationTest.java).

Audit the exact executing JDK's `SecureDirectoryStream` implementation, Unix path
resolution, attribute lookup and channel-open path, including native open flags
and interruption/close behavior. Resolve implementation source to that build and
record source locations and hashes; distinguish API guarantees from provider and
kernel observations. Review whether ordinary public JDK APIs can bind type checks
to the opened object and prevent blocking at open. Do not assume `NOFOLLOW_LINKS`
means regular-file-only or that a pre/post path hash, file key or real-path check
closes a race. Reuse the prior pinned Jadx audit rather than changing loader APIs.

## Deterministic probes

Run acquisition races against the actual production path, not just the copied
test harness. If a timing seam is necessary, keep it narrow, package-local and
per invocation at the acquisition boundary; avoid global hooks or a second opener
whose passing behavior could diverge from production. A test-only candidate must
be identified separately from unchanged production results. Use latches/explicit
stage messages, not sleeps or probabilistic racing, and record which gate fired.

| Case and pause point | Required observation / independent control |
| --- | --- |
| Healthy acquisition with no replacement | Accepted bytes feed census and real Jadx; original LE loop observation, ordered fingerprints and revisions match. This is the positive control for every refusal. |
| After opening a descendant directory handle, before opening the leaf | Move that subtree from inside the allowed root to a sibling outside it on the same filesystem. Test the allowed root itself moving separately, and both an absent original name and a replacement directory at that name. Record whether traversal follows the held directory rather than the current allowed pathname. |
| After a regular leaf channel is opened, before its first read | Relocate its ancestor outside the allowed root. Establish what membership policy the opened object can actually enforce; do not silently redefine an already-open handle as permanently in-root. |
| After regular attributes succeed, before `newByteChannel` | Replace the leaf with a FIFO with no writer. Distinguish blocking at open from rejection before open, read, parse or callback. Repeat with a directory and final symlink as nonblocking type-policy controls. |
| Before the attribute check | Supply the same FIFO, directory and symlink replacements. Separate existing refusal from the check/open race. Do not create devices or require privileged filesystem changes. |
| During bounded reading and between capture and load | Retain growth/in-place mutation, deletion, symlink and replace/restore controls. After full accepted-byte capture, loading must use only those owned bytes even if every source path disappears. |
| Secondary/repeated input slots | Race a later slot after an earlier slot succeeds; verify whole-operation refusal, no partial census/engine publication and release of earlier acquired resources. |

Use two relocation controls: an outside object containing **identical accepted
DEX bytes**, and distinguishable replacement content (the existing LE versus GT
fixture). Hash mismatch alone cannot detect the first case or prove that no
outside-root bytes were read. Establish the moved object's actual location and
observe open/read stages, using narrowly scoped syscall evidence when Java-level
instrumentation cannot distinguish them. Returning an error after reading an
outside object is not a passing path-security result. A rename entirely within
an allowed root is a useful control; record legitimate refusals rather than
requiring broader admission to make that control pass.

Potentially blocking probes must execute in a disposable **test child JVM** with
a parent-enforced finite timeout, explicit readiness handshake, captured output,
and guaranteed termination/reaping and fixture cleanup. This is test isolation,
not a proposed production worker architecture. Do not rely on JUnit interrupt
timeouts or cancelling a future to stop a blocked native open. A watchdog kill
is evidence of a failing acquisition/liveness gate, never successful cooperative
cancellation. Record missing platform facilities as skips and limitations, not
passes. Linux relocation and FIFO controls are required for a conclusive gate.

## Acceptance and outcome report

Report each case's unchanged behavior, candidate behavior if explored, refusal
stage, bytes actually read, result/exception, elapsed timeout outcome and cleanup.
Keep the accepted baseline, dirty state, logical/search revisions and native
files unchanged by reads or refusals. After every non-hanging failure, restore
the fixture and prove healthy repository-backed owned analysis works, with no
leaked temporary lease or engine. Cover active-operation shutdown refusal; for
a killed child, prove process/resource cleanup rather than claiming its runtime
recovered. Preserve same-reader census, aggregate limits, native initialization,
unsaved replay and immutable result bindings.

Choose one evidence-backed outcome:

- **PASS for the stated platform/policy:** the audited mechanism and adversarial
  probes establish containment and avoid the demonstrated special-file blocking
  sequence. State the exact enforcement point/lifetime and residual races; a
  finite collection of tests alone is not proof of arbitrary hostile-filesystem
  safety. A test-only candidate still needs reviewed production adoption and
  rerun production probes before the public-contract slice.
- **BLOCKED/DEGRADED:** retain counterexamples, identify the violated property
  and propose the smallest compatible adjustment or explicit human design
  decision. Do not equate accepted content with allowed location, silently narrow
  allowed-root semantics to path strings/old handles, or expose a graph endpoint.

Neither outcome proves hostile-DEX parser safety, hard heap/stack/deadline bounds,
mount-namespace safety, mapping ownership, complete exception edges or original
CFGs. Initial repository hashing, primary loading and existing path-based Java
overrides remain outside this slice. Native persistence, public OpenAPI, Python
SDK, pins and formal capability statuses stay unchanged. GUI qualification is
not needed for test-only acquisition probes; any proposed persistence change is
outside scope and needs its own approval and matching-GUI gate.

## Executed source audit

Execution used Linux x86_64 kernel 6.18.55, Ubuntu OpenJDK
`21.0.12.1+1-1-26.04.4-Ubuntu`, default `sun.nio.fs.LinuxFileSystemProvider` and
`sun.nio.fs.UnixSecureDirectoryStream`. The repository is on overlay; the JUnit
fixtures used `/tmp` tmpfs. The two FIFO blocking sequences were additionally
reproduced on the repository's overlay filesystem under syscall tracing. No
other OS/provider is qualified.

The installed source ZIP link was dangling. Downloaded, without installing or
changing the JDK, the matching Ubuntu `openjdk-21-source` package and the exact
[source package description](https://archive.ubuntu.com/ubuntu/pool/universe/o/openjdk-21/openjdk-21_21.0.12.1+1-1~26.04.4.dsc),
original source and Debian patches. SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| `openjdk-21-source_21.0.12.1+1-1~26.04.4_all.deb` | `b28c38179c22410b7aac55fc23a50f932ffe2ac951681c368c6bb17d6c9a89ce` |
| `openjdk-21_21.0.12.1+1.orig.tar.xz` | `ec270145b9c8870e6f0263597efefc6e2c90d99dbe41135b3d8fb8b887499aa5` |
| `openjdk-21_21.0.12.1+1-1~26.04.4.debian.tar.xz` | `d15be08c3add59c3459d35c2d4d6a7b352b43b72df4497d2ed43a52871f7359b` |

The source package's Java implementations match the original archive byte for
byte. The two distro patches touching `UnixNativeDispatcher.c` concern time
symbol lookup and a 32-bit MIPS stat wrapper, not `openat0`. Audited Java source
was cross-checked against installed `javap -p -c` bytecode; runtime module/native
library hashes and source manifests are retained locally. Upstream links below
use the matching `jdk-21.0.12.1+1` tag, not current master:

- [`SecureDirectoryStream.java`, lines 32–54](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12.1%2B1/src/java.base/share/classes/java/nio/file/SecureDirectoryStream.java#L32-L54)
  explicitly promises access through the opened directory even after an attacker
  moves/replaces it. That is stable handle access, not current-root containment.
- [`UnixSecureDirectoryStream.java`, lines 57–68 and 103–164](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12.1%2B1/src/java.base/unix/classes/sun/nio/fs/UnixSecureDirectoryStream.java#L103-L164)
  holds a read lock across native opens; close needs the write lock. Directory
  acquisition uses `O_RDONLY | O_NOFOLLOW`, with neither `O_DIRECTORY` nor
  `O_NONBLOCK`, and calls `fdopendir` only after open returns.
- [`UnixChannelFactory.java`, lines 185–293](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12.1%2B1/src/java.base/unix/classes/sun/nio/fs/UnixChannelFactory.java#L185-L293)
  maps READ/NOFOLLOW to `O_RDONLY | O_NOFOLLOW`, without regular-file-only or
  nonblocking flags. It creates the channel only after native open succeeds.
- [`UnixNativeDispatcher.java`, lines 81–95](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12.1%2B1/src/java.base/unix/classes/sun/nio/fs/UnixNativeDispatcher.java#L81-L95)
  and [`UnixNativeDispatcher.c`, lines 554–571](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12.1%2B1/src/java.base/unix/native/libnio/fs/UnixNativeDispatcher.c#L554-L571)
  forward flags to `openat64`; the native RESTARTABLE macro retries EINTR.
  `Blocker.begin/end` compensates virtual-thread scheduling, not cancellation.
- [`FileChannel.java`, lines 45–58](https://github.com/openjdk/jdk21u/blob/jdk-21.0.12.1%2B1/src/java.base/share/classes/java/nio/channels/FileChannel.java#L45-L58)
  does not expose file metadata; public file channels have no descriptor-bound
  regular-file predicate. The directory's named-child attributes use `fstatat`,
  not the descriptor returned for that child channel. A repeated path check
  cannot atomically bind the check to that open or stop an earlier blocking open.

Reused the preceding integration's audited Jadx source/manifest and owned Smali
assembler without changing APIs, pins, graph copying or the initialization pass.
All third-party source/license material remains excluded audit evidence, not
first-party distributed code.

## Executed probes and decision

[OwnedDexContainmentTest](../src/test/java/dev/libjadx/jadxadapter/OwnedDexContainmentTest.java)
adds 20 invocations. Its package-local, per-invocation acquisition observer pauses
the **same production opener**, not the old test-only acquisition copy. The public
entry point delegates with a no-op observer; no acquisition checks or policies
are changed. The observer adds no global mutable state or public-route hook.

| Probe | Observed outcome |
| --- | --- |
| Eight ancestor-relocation cases: subtree or allowed root; directory-handle or leaf-channel pause; original name absent or occupied | All read the complete 1044 accepted bytes after the object moved outside the allowed root and produced the original LE graph. Replacement paths contained separately verified GT content. Accepted hashes do not detect location loss, even with identical bytes. |
| Within-root move and deletion after capture | Graph remains LE from private captured bytes with no later path reopening. |
| Three real-opener content changes: before-open rewrite, growth and in-place mutation after the first read | Existing external-modification refusal after reading; no census/engine publication. Restoration permits healthy analysis. |
| Four directory/symlink leaf controls before attributes or after regular attributes | Before attributes: security refusal, no open/read. After attributes: symlink open refuses; directory opens but read fails with `Is a directory`, zero content bytes. A precheck is not descriptor-bound type enforcement. |
| Repeated second input slot replaced by a directory | First slot was read; second refuses before open. No partial census is returned. Accepted baselines, dirty/logical state and restored healthy behavior remain intact. |
| FIFO installed before attributes, in a disposable child | Security refusal, normal zero exit, no file-channel/read/census publication. |
| FIFO installed after regular attributes, or at a directory component before descent, in disposable children | Both remain blocked after the parent's two-second observation window. `jcmd Thread.print` confirms the main thread in native `UnixNativeDispatcher.openat0`, through the respective channel/directory caller. No file-channel/read/census publication occurs. Parent watchdog forcibly terminates and reaps each child. This is not cooperative cancellation or an in-process recovery claim. |

Separate bounded Python-parent runs of the test child under locally extracted
strace 6.19 confirm the actual `openat(..., "input.dex", O_RDONLY|O_NOFOLLOW)`
and `openat(..., "nested", O_RDONLY|O_NOFOLLOW)` calls do not return before
termination. `leaf-openat.log`, `directory-openat.log` and
`traced-children-final.log` retain flags, stage handshakes and watchdog outcomes.
Neither open uses `O_NONBLOCK`; the directory open also lacks `O_DIRECTORY`.

Refusal and non-hanging counterexample paths restore their fixtures and load real
Jadx again. `/proc/self/fd` controls check no descriptors remain under fixture
roots after close/refusal. Killed children disappear from `/proc`; replacement
FIFOs are removed and healthy acquisition in the parent succeeds. Existing
repository/runtime integration tests separately retain unsaved replay,
revision/search identity, same-reader census, snapshot/save/reload behavior,
temporary lease release and active-operation shutdown refusal. Their ordinary
refusal controls are not a claim that a blocked native opener can recover.

**Decision: BLOCKED/DEGRADED.** No compatible ordinary public-JDK acquisition
adjustment was established that enforces both strict current allowed-root
membership under hostile relocation and bounded special-file acquisition.
No production candidate was adopted. Extra hashes, real-path checks, file keys
or an interrupt timeout would leave the demonstrated races.

## Proposed design decision — not approved

The smallest next step is an explicit input-namespace/threat-model design
decision, not another graph feature: either retain the strict requirement and
investigate an OS-enforced boundary that actually prevents relocation/substitution,
or propose a narrower trusted/quiescent-filesystem support model for human
approval. The latter would weaken the current promise and is **not approved by
this gate**. A native `O_NONBLOCK` plus descriptor type-check boundary could
address the FIFO example, but needs separate approval and does not prevent an
opened subtree moving outside the root; `openat2` beneath-root resolution alone
also does not pin future ancestry. A worker JVM can contain a hang, not establish
filesystem ancestry. Do not present any of these as a complete or proved fix.

Until that decision and its executable proof, keep the owned path internal and
public CFG work blocked. Persistence, SDK, OpenAPI, capabilities and existing
loading behavior remain unchanged. No fresh GUI or release qualification is
claimed; source and ordinary documentation invalidate the previous strict
release fingerprint.

## Validation and delivery

Source archives/manifests, logs, thread dumps in JUnit output and XML are retained
under excluded `.amp/in/artifacts/owned-input-containment/`. The repository-local
exclude rule was checked before writing them. Failed initial compilation (JUnit
generic inference) and the next run's wrong URL-classloader-only assumption are
retained as `focused-1.log` and `focused-2.log`/XML, not counted as passes. The
matching package sources were extracted with Python's standard-library tarfile
because the host lacks `xz`; source files were not edited. A native objdump was
unavailable; installed bytecode, native-open thread dumps and syscall evidence
provide the runtime cross-check instead.

An initial attempt to trace the entire Gradle process stalled before task
execution; its thread dump showed Gradle cache-lock notification, not DEX
acquisition. That command was stopped, and tracing was narrowed to the disposable
probe JVMs. The first direct tracing script obtained both native-open traces but
then tried to stat a leaf below the intentionally replaced directory; its
post-check was corrected to retain the pre-replacement byte count and the final
bounded run passed. The subsequent clean build initially stalled in this task's
old Gradle daemon while sending the cache-lock release notification. The stalled
command was stopped and only that daemon was restarted before rerunning the
build. These attempts do not count as test passes; their logs/thread dumps are
retained separately.

Executed focused regression command:

```bash
./gradlew test --tests '*OwnedDex*Test' --tests '*InputCensusTest' \
  --tests '*NativeProjectRepositoryTest' --tests '*NormalizedCfgIsolationTest' \
  --tests '*TemporaryAnalysisTest' --tests '*OperationCoordinatorTest' \
  --tests '*SettingsRebuildFailureTest'
# regressions-1.log / regressions-1-xml: 98 passed; zero failures/errors/skips.
```

Final clean build and contract/documentation checks:

```bash
./gradlew clean test check installDist --no-build-cache
# java-full.log / java-full-xml: BUILD SUCCESSFUL in 7m 7s; 578 total,
# 562 passed, 16 existing opt-in matching-GUI skips, zero failures/errors.
# All 20 OwnedDexContainmentTest invocations ran without skips.

python/.venv/bin/python tests/validate-openapi.py
# openapi.log: valid OpenAPI 3.1; 23 operations, 42 JSON examples, 3 SSE frames.
python/.venv/bin/python tests/validate-documentation.py
# documentation.log: 59 Markdown files, 565 valid relative links/anchors.
python/.venv/bin/python tests/validate-documentation.py --self-test
# documentation-self-test.log: positive/negative controls passed.
git diff --check
# Passed; the two untracked files also passed separate no-index checks.
```

No Python or public contract files changed, so SDK regeneration and Python suites
were not rerun for this gate. Native save/reload regression tests ran in the Java
suite; no serialization changes or fresh matching-GUI qualification are claimed.

The implementation handoff initially left changes local and uncommitted.
Subsequent Git history records delivery; no release tag, deployment or
publication is implied.
