# Cromite 153 DevTools: Morphe API integration gate (2026-10-12)

This is a source-level feasibility audit, **not** a working .mpp.

## Verified against MorpheApp/morphe-patcher main

- `ResourcePatchContext.get("assets/devtools-cromite-153.zip", copy = false)` returns a writable staging `File` under the other-resources root; `ArsclibResourceCoder.getFile` creates parent directories for entries not in the APK.
- `ArsclibResourceCoder.getOtherResourceFiles` moves touched root entries into the rebuilt APK's other-resource payload, including newly created assets. This supports **adding a ZIP asset** from a bundled patch resource, provided the patch artifact actually contains the bytes and resource processing is enabled.
- `BytecodePatchContext` compiles class definitions into multi-DEX; FULL uses `DexReadWrite.writeMultiDexFile`; STRIP_SAFE/STRIP_FAST can emit new DEX files for modified/new classes. This supports **adding classes through the bytecode model**, but does not prove that copying an arbitrary prebuilt `classes3.dex` into the resource staging area is safe. The resource coder deliberately excludes DEX entries from ordinary root staging.
- Existing `DevToolsFrontendInstaller.kt` is patcher-side and is **not** the Android runtime `DevToolsBootstrap`. The runtime bootstrap must be injected as a class definition or via a separately proven DEX-merge API.
- The experimental apktool solution moved `Lv60;` from primary DEX to secondary DEX because primary DEX had 65536 method IDs. Morphe's own multi-DEX writer may avoid that exact method-limit problem; test rather than hardcode a class move.
- The `Lj72;->b(Lkm1;)V` hook is structurally demonstrated only in the apktool experiment. Startup order relative to Chromium native command-line parsing remains **unverified**.
- Do not run release workflows, touch `main`, install over CromiteOrg-signed Cromite, or label the experimental bundle functional.

## Next implementation checks

1. Determine the exact Morphe dependency version and bytecode mode used by Manager 1.50.0, not just upstream main.
2. Prove the patch bundle can embed and stream a 4,500,011-byte ZIP at patch time; fail on missing resource or SHA-256 mismatch.
3. Import the Java bootstrap class using Morphe's supported bytecode APIs (or establish an explicit DEX merge method). Ensure `org.chromium.base.CommandLine.d.a` resolves.
4. Add a version-locked fingerprint and guarded instruction insertion for `Lj72;->b(Lkm1;)V`; avoid unsafe assumptions about registers or native startup.
5. Perform one disposable Manager patch run with DevTools + AMOLED + status-bar patches, inspect DEX and asset hashes, and test runtime on a separate installation/device only.

## Sources

- https://github.com/MorpheApp/morphe-patcher/blob/main/src/main/kotlin/app/morphe/patcher/patch/ResourcePatchContext.kt
- https://github.com/MorpheApp/morphe-patcher/blob/main/src/main/kotlin/app/morphe/patcher/resource/coder/ArsclibResourceCoder.kt
- https://github.com/MorpheApp/morphe-patcher/blob/main/src/main/kotlin/app/morphe/patcher/patch/BytecodePatchContext.kt
