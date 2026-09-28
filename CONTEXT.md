# LibJadx

LibJadx exposes the analysis and native edits of one fixed Jadx project.

## Language

**Mapping import**:
A merge of supported declarations from a mapping file into pending native edits.
_Avoid_: attachment, save

**Mapping attachment**:
The project's reference to a mapping file that supplies aliases and comments.
_Avoid_: import

**Logical state**:
The project's currently accepted settings, attached mappings and pending native edits.
_Avoid_: saved state

**Explicit native save**:
The user's requested persistence of pending edits in the native Jadx project.
_Avoid_: source export, mapping export

**Original-input census**:
The original declarations supplied by configured inputs, including definitions
that Jadx omits when selecting among duplicates.
_Avoid_: visible class catalog, decompiled source

**Override candidate set**:
Jadx's reported related methods, which may omit members or differ by seed.
_Avoid_: complete override family

**Verified override family**:
The complete set of original method declarations connected by supported override
relationships within a fully understood hierarchy. Verification alone does not
authorize or persist edits.
_Avoid_: candidate set, propagated rename
