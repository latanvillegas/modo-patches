# Cromite DevTools Copy — integration investigation

Status: **research only; not a functional Morphe patch**.

## Confirmed baseline
- Package: `org.cromite.cromite`, version `153.0.8010.37`.
- Baseline APK SHA-256: `9db12af1af021f42b4371e76d0e9fe476a7085bbbbc76bc410a538d028bf6071`.
- Working DevTools frontend main.js SHA-256: `17db0138b7c9892f7e7849f0d007b8643aa9cefb6ba549d95a9cda33d5e2a9ed`.
- The working UI inserts a native-themed COPY button into the DevTools right toolbar and invokes `InspectorFrontendHostInstance.copyText`.
- Current deployment uses `--custom-devtools-frontend=file:///data/local/tmp/devtools-cromite-153/`, with files installed outside the APK via Shizuku.
- APK has no separately named DevTools frontend JS files; raw string scans of `assets/resources.pak+40064988+`, `chrome_100_percent.pak+40064988+` and `chrome_200_percent.pak+40064988+` found none of six searched DevTools strings.
- `libchrome.so` contains references to `custom-devtools-frontend` and `devtools://devtools/bundled/devtools_app.html`. These strings alone do **not** establish a patchable implementation or the actual frontend storage format.

## Verified patch project
- Repository uses `app.morphe.patches` Gradle plugin version `1.3.4`.
- Existing Cromite patches `PureAmoledPatch.kt` and `HideStatusBarPatch.kt` show `bytecodePatch` / `resourcePatch` conventions and version gating.
- No evidence yet that the existing APK patch pipeline can inject an entire DevTools web frontend or configure a bundled `file://` path without external files.

## Design gates before implementation
1. Determine actual DevTools frontend delivery path when the external override is absent (remote, embedded, or other).
2. Inspect Morphe Patcher resource/raw ZIP mutation APIs and Manager's native payload format, including installation semantics and security boundaries.
3. Determine whether frontend assets can be bundled into APK assets and served through a Chromium-supported URL, or whether a native Chromium modification / source rebuild is unavoidable.
4. Capture the full frontend directory (not just main.js), preserve its Chromium revision compatibility and hash each payload.
5. Implement only after (1)–(4) establish a self-contained, verifiable route. Fail closed on unsupported APK versions or missing anchors.
6. Test without launching a full native Chromium build initially. Validate output APK contents, app startup, DevTools startup, selection, clipboard copy and coexistence with AMOLED/status-bar patches.

## Explicit non-goals until feasibility proven
- Do not silently ship a patch that only installs `main.js` without a working loader.
- Do not change `libchrome.so` by blind string replacement.
- Do not alter the stable APK, existing AMOLED patches, or main branch.
- Do not claim a functioning `.mpp` or remove the external frontend until an end-to-end patched APK passes validation.
