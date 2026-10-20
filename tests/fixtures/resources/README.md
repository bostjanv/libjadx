# Owned Android resource probe

All manifest, XML, string and asset content here is independently owned LibJadx
test material under the repository's Apache-2.0 license. No upstream application
or Jadx test fixture was used. `resources.apk` is a **resource-only, unsigned ZIP**,
not an installable Android application. It contains compiled binary manifest/XML,
a resource table and one UTF-8 asset. It includes no Android framework code.

[JadxResourceProbeTest](../../../src/test/java/dev/libjadx/probes/JadxResourceProbeTest.java)
adds a real, owned Smali-assembled `classes.dex`, binary/empty assets and isolated
negative variants in a temporary directory. Ordinary tests require **no Android
SDK, aapt2, network or device**; they use the committed 6486-byte compiled fixture
and the already pinned Smali runtime. The APK's missing component implementations
are intentional: this is a declaration/decoding probe, not an Android launch test.

## Regeneration

The compiler is a fixture-generation tool, not a new service/build dependency.
Use these exact official artifacts locally; do not add them to the distribution:

| Tool/input | Origin | SHA-256 |
| --- | --- | --- |
| aapt2 `9.2.1-15009934`, Linux; reports `2.20-15009934` | [Google Maven JAR](https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/9.2.1-15009934/aapt2-9.2.1-15009934-linux.jar) | `755f6727fb3f4cce5e319eac0f3618ed4b36b49a46d4bb2cbb6fa8e9175a54d6` |
| SDK Platform 35, revision 2 | [Official platform archive](https://dl.google.com/android/repository/platform-35_r02.zip) | `0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0` |
| `android-35/android.jar` from that ZIP | Local compiler symbol input only | `4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b` |

Extract `aapt2` from the Maven JAR and `android-35/android.jar` from the SDK ZIP.
The script performs no downloads, installation or SDK-license acceptance:

```bash
AAPT2=/absolute/path/to/aapt2 ANDROID_JAR=/absolute/path/to/android-35/android.jar \
  bash tests/fixtures/resources/build-fixture.sh
sha256sum tests/fixtures/resources/resources.apk
# 595459df2f52b61d96023c84e3dec69d4d961fa40f21d0cf7a22cbd0a8821b5e
```

The optional first script argument chooses another output for `cmp` checks.
The script sorts entries, uses stored ZIP data and fixes timestamps/permissions;
it excludes compiler `META-INF` metadata. The emitted IDs and independent
`aapt2 dump resources` / `aapt2 dump xmltree ... --file ...` are useful oracles.
Resource IDs are `array/choices=0x7f010000`, strings `alias/app_name/chain/framework/
greeting/missing=0x7f020000..0x7f020005`, and `xml/settings=0x7f030000`.

`@0x7f0200ff` deliberately becomes a **literal string**, not a reference, during
aapt2 linking. The probe separately changes one serialized typed XML reference
to that unknown ID. A string beginning with `@` alone is not relationship proof.
The rocket is intentionally retained: aapt2 decodes it correctly, while pinned
Jadx's UTF-8 string-pool path emits two replacement characters. Tests preserve
this counterexample; they do not repair it or claim lossless string decoding.

See the [resource feasibility report](../../../docs/phase-7-resource-feasibility.md)
for observed capabilities, source evidence, limits and the next API boundary.
