# Phase 4.1: original symbol identity and lookup

The experimental API now supports `GET /api/v1/classes` and `POST
/api/v1/symbols/resolve`. It enumerates **Jadx-visible** declarations in the
current fixed project. It cannot promise a census of every definition in every
input, because Jadx 1.5.6 can discard duplicate original definitions.

## Identity and aliases

`SymbolRef.originalClassDescriptor` is a canonical JVM/DEX class descriptor,
such as `Lprobe/SymbolFixture$Inner;`. Members add their **original** name and
full descriptor: `mix([Ljava/lang/String;I)Ljava/lang/String;` or
`names:[Ljava/lang/String;` as separate `originalName` and
`originalDescriptor` fields. Constructors retain `<init>`; class initializers
retain `<clinit>` when Jadx exposes them. Method return types and field types
participate in exact lookup. Invalid arrays, void parameters/fields, dotted
class descriptors, whitespace, and unknown JSON fields are rejected with 400.
No alias, Java object ID, or guessed input filename is an identity key.

The `displayName` and `displayQualifiedName` values show current Jadx aliases.
Native Tiny mappings and unsaved in-memory class/member renames can change them
without changing the original ref. `inputIdentity` is null until exact
per-input attribution is proven. A request with a non-null input identity
returns the 200 `PROVENANCE_UNAVAILABLE` outcome; it never picks a class by
filename. A descriptor-only hit can return `RESOLVED` while its symbol's
`provenance` is `UNAVAILABLE`. A known duplicate among Jadx-visible classes
returns `AMBIGUOUS` with at most 16 candidate descriptions and no winner.
`NOT_FOUND` means absent among *currently Jadx-visible declarations*.

## Listing and cursors

`GET /classes` defaults to 50 items and permits 1–100. It sorts by original
class descriptor. Inner and anonymous classes are included by default. A
class Jadx marks `DONT_GENERATE` remains listed with `codeAvailable=false`;
this occurs for the owned anonymous-class fixture. `includeInner=false`
excludes inner and anonymous classes. `packagePrefix` matches an exact
original dotted package and its dot-delimited descendants. `nameContains`
is a case-sensitive substring of the full dotted original name by default;
`nameDomain=alias` changes it to the current displayed qualified name.

Each page reports `scope=JADX_VISIBLE`, `sourceCoverage=UNVERIFIED`, and a
`complete` flag. `complete=true` means the **filtered visible enumeration** is
exhausted, not that every original input definition survived Jadx. A token
binds the session UUID, logical revision, engine publication epoch, normalized
filters, and last raw ordering key. It is signed, bounded to 4096 characters,
and expires on edits, mapping rebuilds, native reload, save publication, or
server restart. A stale token returns 409 `STALE_REVISION`; malformed or
filter-incompatible tokens return 400 `INVALID_REQUEST`. The HMAC is verified
before any payload field is used to classify the cursor; altered payloads
with an old signature return 400. A private 32-byte signing key is generated
once under `$XDG_STATE_HOME/libjadx/cursor-signing.key` (or
`~/.local/state/libjadx/cursor-signing.key`) so an authentic cursor from a
previous process can still be recognized as stale. This key is server
operational state: it contains no project data and is never written to a
`.jadx` file. On POSIX, the key must be owner-only; an unreadable or invalid
existing key prevents listener startup rather than silently changing cursor
semantics. The in-memory catalog is limited to 100,000 entries and 8 million copied characters; exceeding it
returns 429 `RESOURCE_LIMIT` rather than allocating indefinitely.

Both endpoints take a fail-fast conservative `CLASS_READ` lease. A conflicting
operation returns retryable 409 `PROJECT_BUSY`. Member resolution may trigger
decompilation of the one requested class; class listing does not call member
accessors or generate all class code. The catalog and results contain only
immutable DTOs, not Jadx nodes, and rebuild on a session/revision/engine
publication change. Neither endpoint edits or saves native files.

## Pinned source and executable evidence

Pinned Jadx source: `v1.5.6`, commit
`4c0ac37699aa8c9803f1c73cfaacd9205acb044b`.

- `jadx-core/src/main/java/jadx/api/JadxDecompiler.java`: `getClasses()`
  excludes inner and `DONT_GENERATE` classes; `getClassesWithInners()` covers
  `root.getClasses()`.
- `jadx-core/src/main/java/jadx/api/JavaClass.java`: `getRawName()` separates
  original name from `getFullName()`/`getName()` aliases; `getMethods()` and
  `getFields()` call `load()`.
- `jadx-core/src/main/java/jadx/core/dex/info/MethodInfo.java` and
  `FieldInfo.java`: original name and raw argument/return/field types are
  distinct from aliases. `TypeGen.signature` yields JVM type descriptors.
  These version-sensitive calls are isolated in `JadxSymbolAdapter`.

`JadxSymbolProbeTest` compiles the owned `SymbolFixture.java` to a JAR. The
probe observed `probe.SymbolFixture`, `$Inner`, and `$1`; `$1` had
`isNoCode=true`. It checked constructor and overloaded method descriptors,
primitive/object/array field types, unchanged original refs after class,
method, and field aliases, and unchanged class process state during catalog
enumeration. An owned classfile fixture with two `value()` methods differing
only in return descriptor proves exact `()I` versus `()Ljava/lang/String;`
lookup with the pinned loader. The two-JAR duplicate fixture exposed **one**
`Lduplicate/Clash;` class through Jadx 1.5.6, so the API reports its input
provenance as unavailable. This is JAR evidence only; multidex attribution
and duplicate survival remain unproven. The native fixture verifies Tiny
mapping aliases and unsaved class renames with stable original refs.

The OpenAPI document and examples define the wire schema. The generated
Python transport is still a Phase 6 task. Phase 4.2 can build class-oriented
decompiled source on top of this identity and lease boundary.
