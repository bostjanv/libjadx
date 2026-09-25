# Phase 4.2: class Java source and metadata

`POST /api/v1/decompile` accepts an original `CLASS` or `METHOD` `SymbolRef`.
It returns the exact Java string produced by Jadx 1.5.6 for the emitted source
owner. A method request returns that same complete class string and, when
verified, a `methodRange` and `methodSource`. The endpoint is read-only;
unsaved aliases and comments are visible in memory and require an explicit
native project save to persist. No source or index sidecar is written.

```json
{"ref":{"kind":"METHOD","originalClassDescriptor":"Lprobe/SymbolFixture;","originalName":"mix","originalDescriptor":"(I)I"},"representation":"JAVA"}
```

The response uses the P4.1 `RESOLVED`, `NOT_FOUND`, `AMBIGUOUS`, and
`PROVENANCE_UNAVAILABLE` outcomes. A supplied `inputIdentity` never selects a
guessed JAR entry. `status` describes the emitted Java string: `COMPLETE`
means Jadx returned text with no class or visible-method error recorded by
the checked nodes, `PARTIAL` means a checked node has a Jadx error, and
`UNAVAILABLE` means there is no trustworthy source to return. `COMPLETE`
does not imply complete reference metadata, exact input provenance, or
verified original-bytecode mapping. Each capability is labeled separately.

The code owner can differ from the queried class. Jadx 1.5.6 inlines the owned
`SymbolFixture$1` anonymous class into `SymbolFixture`. The response identifies
`SymbolFixture` in `sourceOwnerRef` and returns its full Java text; it never
claims the anonymous class has a separate generated file. If no owner can be
proved, the source is null and the capability is `UNAVAILABLE`.
The adapter processes the original outer class before resolving Jadx's code
parent, so a first request for an anonymous class sees its emitted owner.
A suppressed child needs Jadx's anonymous or inline ownership marker;
a suppressed class without that marker does not inherit unrelated parent text.

## Locations and source identity

`source` is the exact `ICodeInfo.getCodeStr()` Java string, including its line
endings. Every `offsetUtf16` is a zero-based Java UTF-16 code-unit boundary in
that string. Ranges are half-open. `line` is 1-based and
`columnCodePoints` is 0-based; a supplementary Unicode character uses two
UTF-16 units and one column. CRLF is one line break: the boundary on CR and
the boundary on LF both belong to the preceding line; the boundary after LF
starts the next line. Lone CR and LF also start a new line after their
character. An offset inside a surrogate pair is rejected rather than rounded.
Original debug source lines and bytecode instruction positions use distinct
coordinate systems and are not returned by this endpoint.

`sourceSnapshotId` is `sha256:` plus SHA-256 over length-prefixed UTF-16BE fields
in this order: `libjadx-source-v1`, session UUID, logical revision, engine
publication epoch, effective settings fingerprint, source owner's original
descriptor, and exact Java text. The fingerprint is volatile. A native save
publication, reload, mappings rebuild, mutation, mode change, restart, or
different generated text changes it. A method and class query for the same
owner, session, revision, mode and text share the class source snapshot.
`expectedSessionId` and `expectedLogicalRevision` must occur together;
`expectedSourceSnapshotId` checks the actual source after decompilation.
Mismatches return 409 `STALE_REVISION`.

## Verified annotations and method excerpts

The pinned `ICodeMetadata.getAsMap()` returns integer code positions.
`NodeDeclareRef` identifies a node and its name-token position; direct
`CLASS`, `METHOD` and `FIELD` annotations can point to references. The adapter
converts supported nodes to original refs, validates that the displayed token
starts at the reported source offset, rejects surrogate-split/out-of-bounds
positions, sorts and deduplicates immutable entries. Each returned position is
`EXACT` for its token in the returned string. This is a **partial set** of
declarations and references: unresolved and structural metadata is omitted,
and Jadx does not promise all use positions. `includeAnnotations=false` skips
this conversion and reports unknown annotation coverage.

A method excerpt needs an original-method `NodeDeclareRef` in the emitted
owner's metadata, the displayed method name at that exact offset, a balanced
body found by a bounded lexer that skips quoted strings, comments and text
blocks, and a `NodeEnd` at the computed boundary. The range is returned only
after the method is resolved by its complete original descriptor. The
returned excerpt is `source.substring(startOffsetUtf16,endOffsetUtf16)`.
`NodeEnd` by itself has no method identity. Semicolon declarations, class
initializers and any case without a verified end use
`methodRangeAvailable=false`, null range and null excerpt, with the full
class source retained. The owned `<clinit>` is a deterministic fallback.
Overloads and the owned return-type-only classfile pair are tested against
their original descriptors. A lexical guess never becomes an exact range.

## Limits and errors

Requests are capped at 64 KiB. Generated Java is capped at 4 MiB UTF-8 and
returned annotations at 20,000 per synchronous operation; an over-limit
response is 429 `RESOURCE_LIMIT`, without truncating source and invalidating
offsets. Diagnostics are bounded and class/method Jadx error and warning
messages are sanitized before returning. A complete class-local error count,
including all inlined child nodes, is unproved and remains null. Original
debug-line origin and `OFFSET` annotation precision are unproved; raw debug
lines and bytecode offsets remain unavailable. `includeRawDebugLines=true`
adds an unavailability diagnostic. Strict mode returns 409
`INCOMPLETE_ANALYSIS` if source is partial/unavailable, a requested method
range is unverified, requested annotation coverage is not proven complete, or
requested debug lines are unavailable. Because Jadx does not establish
complete declaration/reference coverage, a strict source-only request should
set `includeAnnotations=false`. Domain misses remain 200
typed outcomes even with strict. Unsupported representations return 422
`UNSUPPORTED_CAPABILITY`.

The primary path holds one conservative `CLASS_READ` lease while resolving the
symbol, getting code and converting metadata. A conflicting save, reload or
shutdown fails promptly with `PROJECT_BUSY`. A different requested
`decompilationMode` creates one isolated engine from the current logical
native edit snapshot. It includes unsaved renames/comments, releases the
engine after the request, and does not change the primary mode, dirty bit,
revision, mappings or native files. There is no shared source cache.

## Pinned source and executable evidence

All paths below refer to Jadx commit
`4c0ac37699aa8c9803f1c73cfaacd9205acb044b` (release `v1.5.6`):

- `jadx-core/src/main/java/jadx/api/JavaClass.java`: `getCodeInfo()`,
  `isNoCode()`, `getTopParentClass()`, and `getCodeParent()`.
- `jadx-core/src/main/java/jadx/api/ICodeInfo.java` and
  `jadx-core/src/main/java/jadx/api/metadata/ICodeMetadata.java`: exact Java
  string, code-position map and line mapping.
- `jadx-core/src/main/java/jadx/api/metadata/annotations/NodeDeclareRef.java`
  and `NodeEnd.java`: identified declarations and unattributed end markers.
- `jadx-core/src/main/java/jadx/core/codegen/ClassGen.java`: method-end
  annotation emission after the closing brace.
- `jadx-core/src/main/java/jadx/core/dex/visitors/debuginfo/DebugInfoAttachVisitor.java`
  and `jadx-core/src/main/java/jadx/core/codegen/RegionGen.java`: original
  debug lines can be attached to instructions, then source lines are attached
  to generated regions. This suggests a line-map lineage, but does not prove
  exact original correspondence after Jadx transformations.
- `jadx-core/src/main/java/jadx/core/dex/attributes/nodes/JadxError.java`
  and `JadxCommentsAttr.java`: pinned class/method diagnostics.

`JadxSourceProbeTest` records the declaration and end-marker behavior on the
owned `SymbolFixture.java` JAR. `SourceFixture.java` covers abstract and
interface declarations, constructors, strings, text blocks and a lambda.
`DecompileEndpointsTest` checks exact class
text against the same pinned engine, field/call annotation tokens, method
substrings, overload identity, anonymous ownership, native mapping and
unsaved edit visibility, temporary mode isolation, save/reload/restart
snapshots and typed failures. Its pinned-node error injection is explicitly
simulated; no naturally failing Jadx fixture was established for this slice.
`SourceCoordinatesTest` covers LF/CRLF/CR,
surrogates, combining marks and negative ranges. `OpenApiDocumentTest`
validates reviewed examples against their OpenAPI schemas. The installed
distribution test requests source without test-only GUI dependencies.

The capability matrix in [feasibility-matrix.md](feasibility-matrix.md)
records bounded follow-up for failed-code fixtures, debug-line origin and
original bytecode offsets. The next API slice is P4.3 basic references.
