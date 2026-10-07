# Cromite 153 binary/mapping analysis

## Evidence boundary

- Analyzed artifact report: `reporte-cromite.txt`.
- Analyzed R8 mapping: `arm64_ChromePublic.apk.mapping`, SHA-256 `386718f086f68a852d9a8c5a9ada27767f863a5c2dc9a161468b502108ffaf35`.
- The supplied binary reports confirm package/version/SDK metadata and that the APK contains `classes.dex` plus `classes2.dex`; the APK bytes themselves are not materialized in the current validation environment, so class-to-DEX attribution is not inferred.
- The report records APK SHA-256 `9db12af1af021f42b4371e76d0e9fe476a7085bbbbc76bc410a538d028bf6071`, matching the official Cromite 153 arm64 release asset.

## Build identity

| Field | Verified value | Source |
| --- | --- | --- |
| Package | `org.cromite.cromite` | `apk-badging.txt` + mapping `pg_map_id` |
| Variant | `ChromePublic` / arm64 asset | mapping + report filename |
| R8 | `9.5.7-dev` | mapping header |
| R8 min API | `29` | mapping header |
| pg_map_id | `chromium-ChromePublic.apk-stable-801003702 (org.cromite.cromite)` | mapping header |
| Version name | `153.0.8010.37` | `apk-badging.txt` |
| APK SHA-256 | `9db12af1af021f42b4371e76d0e9fe476a7085bbbbc76bc410a538d028bf6071` | supplied report + release |
| Version code | `801003702` | `apk-badging.txt` |
| Min SDK | `29` | `apk-badging.txt` |
| Target SDK | `36` | `apk-badging.txt` |
| Compile SDK | `37` | `apk-badging.txt` |
| Native ABI | `arm64-v8a` | `apk-badging.txt` |


## R8 identities used by patches

| Original | R8 name | Kind | Source file |
| --- | --- | --- | --- |
| `org.chromium.components.browser_ui.accessibility.AccessibilitySettings` | unchanged | class | `AccessibilitySettings.java` |
| `mForceTabletUIDelegate` | `I0` | field | `AccessibilitySettings.java` |
| `onPreferenceChange(Preference,Object)` | `c0` | method | `AccessibilitySettings.java` |
| `onCreatePreferencesCromite(Bundle,String)` | `i2` | method | `AccessibilitySettings.java` |
| `ChromeAccessibilitySettingsDelegate` | `h42` | class | `ChromeAccessibilitySettingsDelegate.java` |
| `DeviceFormFactor` | unchanged | class | `DeviceFormFactor.java` |
| `mForceTabletUI` | `a` | field | `DeviceFormFactor.java` |
| `ChromeSharedPreferences` | unchanged | class | `ChromeSharedPreferences.java` |
| `ChromePreferenceKeys` | `db2` | class | `ChromePreferenceKeys.java` |
| `FullscreenHtmlApiHandlerBase` | `mg5` | class | `FullscreenHtmlApiHandlerBase.java` |
| `FullscreenHtmlApiHandlerCompat` | `ng5` | class | `FullscreenHtmlApiHandlerCompat.java` |
| `hideSystemBars(View,FullscreenOptions)` | `p` | method | `FullscreenHtmlApiHandlerCompat.java` |
| `showSystemBars(View)` | `A` | method | `FullscreenHtmlApiHandlerCompat.java` |
| `ImmersiveModeController` | `xa6` | class | `ImmersiveModeController.java` |
| `WindowInsetsControllerCompat` | `lng` | class | AndroidX |
| `WindowInsetsCompat` | `gng` | class | AndroidX |
| `WindowInsetsCompat.Type` | `fng` | class | AndroidX |
| `SemanticColorUtils` | `pac` | class | `SemanticColorUtils.java` |
| `getDefaultBgColor(Context)` | `h` | method | `SemanticColorUtils.java` |
| `getDefaultTextColor(Context)` | `j` | method | `SemanticColorUtils.java` |

## Force Tablet Mode

The exact Cromite patch adds `force_tablet_ui`, stores it through `ChromeSharedPreferences`, warms the preference store before form-factor decisions, and applies the state through `DeviceFormFactor.setForceTabletUI(boolean)`. The supplied mapping independently confirms the compiled Force Tablet delegate, preference reads, and `mForceTabletUI` field.

## Status bar

Chromium 153 uses AndroidX `WindowCompat.getInsetsController(...)` and `WindowInsetsCompat.Type.statusBars()` for status-bar visibility. The mapping confirms the compiled fullscreen/insets classes and methods. The existing patch-time `android:windowFullscreen=true` implementation is therefore not suitable for a reversible Force-Tablet-only setting and must be replaced by runtime conditional behavior.

## Theme

The mapping validates the existing `Lpac;` descriptor for `SemanticColorUtils`. It also confirms `getDefaultBgColor(Context) -> h` and `getDefaultTextColor(Context) -> j`. This supports semantic surface/text hooks and does not justify global color transforms of web media.

## DEX attribution

The R8 mapping does not encode the destination DEX. `dex-y-recursos.txt` confirms two DEX containers: `classes.dex` and `classes2.dex`. The mapping still does not encode which DEX contains a mapped class, so class-to-DEX attribution remains unverified and is deliberately not guessed.
