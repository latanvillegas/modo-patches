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

## Investigation update — 2026-10-12

- The existing feature branch and this document were verified before any write; avoid duplicate branches.
- `patches/build.gradle.kts` uses the Morphe patches DSL and `settings.gradle.kts` pins `app.morphe.patches` to `1.3.4`.
- The GitHub `release.yml` workflow triggers on pushes to `main`, so exploratory changes must remain isolated on this feature branch.
- Confirmed no frontend JavaScript is directly addressable as an individual APK ZIP entry from the user's listing. `.pak` substring checks alone are not proof of absent resources.
- Next investigation should inspect the actual existing patch paths and the APK's DevTools loading behavior before any implementation or Actions build.

## Termux API inspection — 2026-10-12

The user inspected `morphe-patcher/src/main/kotlin/app/morphe/patcher/patch/ResourcePatchContext.kt` lines 99–145. Public context operations are `operator fun get(path: String, copy: Boolean = true)`, `fun listApkEntries(prefix: String = "")`, and `fun delete(name: String, packageName: String? = null)`. `get` delegates to `resourceCoder.getFile`; its write/create behavior must be verified before treating it as a supported file-add API. `listApkEntries` lists original APK entries, not staged modifications. `delete` supports removing APK entries including assets and native libraries. `ResourcePatchContext.get()` returns other resource files, uncompressed paths, and deleted files for rebuilding.

The full frontend preflight initially failed because the original directory's `entrypoints/main/main.js` differed from the verified working file. The user made an independent copy at `~/amoled-test/devtools-audit/frontend-para-parche` containing 537 files, replacing `main.js` with SHA-256 `17db0138b7c9892f7e7849f0d007b8643aa9cefb6ba549d95a9cda33d5e2a9ed`; the copy operation reported PASS.

`find "$HOME" -type f` found no local `PureAmoledPatch.kt` or `HideStatusBarPatch.kt`. These must be fetched from GitHub with their actual paths, not guessed. Next feasibility gate: inspect `ResourceCoder.getFile` and `getOtherResourceFiles`, then prove Chromium's loader can access a bundled asset without external files.

## Loader evidence — 2026-10-12 (device + upstream)

- ADB shell identity: uid=2000(shell). `/data/local/tmp/chrome-command-line` and the frontend directory are owned by shell, so configuration tests are reversible without root.
- With `chrome-command-line` temporarily set to `chrome`, DevTools **still opens** with `devtools://devtools/bundled/devtools_app.html?remoteBase=https://chrome-devtools-frontend.appspot.com/serve_file/@b75a5a95ea1a1b55bdbfd6d9f...`, but the custom COPY button is absent. User restored the original switch and reported the working frontend again.
- APK `assets/resources.pak+40064988+` is DataPack v5, 7,322,576 bytes, 3,589 resources, 320 aliases. The user's index parser found **zero** resource payloads containing five plain-text DevTools indicators. This is not proof of absence of compressed/embedded DevTools resources.
- Upstream Chromium `chrome/browser/ui/webui/devtools_ui.cc` (historical implementation) shows a `DevToolsDataSource` that distinguishes bundled, remote, and custom requests. Custom routing uses the `--custom-devtools-frontend` switch; merely adding APK assets does not register them with the DevTools URL data source. Source: https://chromium.googlesource.com/experimental/chromium/src/+/refs/tags/71.0.3578.134/chrome/browser/ui/webui/devtools_ui.cc . **This is historical reference code, not proof of exact Cromite 153 implementation.**
- Upstream `chrome/browser/devtools/devtools_window.cc` builds a `remoteBase` query for the default DevTools URL (historical reference): https://chromium.googlesource.com/chromium/src/+/793ff51fe9dd802b273896cebc056b81e6413419/chrome/browser/devtools/devtools_window.cc .
- New implementation gate: inspect Cromite 153's matching `DevToolsDataSource` and URL routing implementation (or exact-version Chromium source). Establish a supported asset URL/data source or a precise, guarded native patch before generating a functional .mpp. Avoid assuming `file:///android_asset/` is supported or automatically recognized. Do not touch the stable APK or main branch.


## Proposed self-contained APK architecture (design, not yet implemented)

The device test without `--custom-devtools-frontend` showed that DevTools opens at `devtools://devtools/bundled/devtools_app.html?remoteBase=https://chrome-devtools-frontend.appspot.com/serve_file/@b75a5a95ea1a1b55bdbfd6d9f...`, but the COPY button is absent. The original command-line override was restored and the user confirmed it works again.

**Candidate architecture:** bundle the verified 537-file frontend as APK assets; extract the assets into a private app directory once per version (atomic staging, hash verification, path traversal protection); inject `--custom-devtools-frontend=file://<private-dir>/` into Chromium's command line *before* DevTools URL construction, without using external `chrome-command-line`. Do not presume Chromium can load ZIP assets or `file:///android_asset/` directly.

**Critical feasibility gates:**
1. Locate an actual Cromite 153 Java/Dex startup hook that runs before native Chromium command-line initialization. Confirm order in the target APK. A hook after native startup is insufficient.
2. Confirm app process can read the chosen private files via the native DevTools file-request code and that Chromium accepts the custom frontend URL. App-private path is a hypothesis, not yet validated.
3. Determine Morphe patch packaging for the frontend payload: Gradle build input, bundled patch resources, and how to write APK assets using `ResourcePatchContext` without external files at patch time.
4. Implement a version-guarded extraction and startup switch mechanism with rollback on failure; verify fresh-install and upgrade behavior.
5. Test a patched APK **with the external command-line switch disabled**, and validate DevTools opens with COPY and clipboard integration. Only then call the .mpp autonomous.

**Security/compatibility notes:** avoid a world-writable frontend directory; verify frontend file hashes; ensure all relative asset paths are normalized; do not patch `libchrome.so` by blind string replacement; preserve existing AMOLED and status bar patches and keep all experimental work on `feature/cromite-devtools-copy`.

**Alternative:** precise modification of the native DevTools data source to load APK-contained resources, if no suitable early startup hook exists. This requires an exact-version implementation analysis and is not a simple resourcePatch.
