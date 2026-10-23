# Cromite patch 14 — implementation contract (WIP)

Target: `org.cromite.cromite` `153.0.8010.37`. This file documents *verified target DEX ABI*, not a completed feature.

## Verified DEX interfaces (from APK mapping and JADX)

- `Lkoa;->Z1()Landroidx/preference/PreferenceScreen;`: obtains existing preference screen; may return null.
- `Lkoa;->Y1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;`: preference lookup; may return null.
- `Landroidx/preference/Preference;-><init>(Landroid/content/Context;)V`.
- `Landroidx/preference/Preference;->R(Ljava/lang/String;)V`: key.
- `Landroidx/preference/Preference;->a0(Ljava/lang/CharSequence;)V`: title.
- `Landroidx/preference/Preference;->X(Ljava/lang/CharSequence;)V`: summary.
- `Lnoa;->f0(Landroidx/preference/Preference;)V`: inserts preference (R8 changed original boolean return to void).
- `Landroidx/preference/Preference;->z()V`: virtual onClick, empty base implementation.
- `Landroidx/preference/Preference;->J()V`: final performClick; invokes `z()` when enabled/selectable.
- `Landroidx/preference/Preference;->L(Landroid/view/View;)V`: delegates to `J()`.
- `Lorg/chromium/chrome/browser/ChromeTabbedActivity;->onResume()V`: lifecycle hook candidate.
- `Lorg/chromium/chrome/browser/night_mode/settings/ThemeSettingsFragment;->a2(Ljava/lang/String;Landroid/os/Bundle;)V`: theme preference initialization hook candidate. Verify class and return paths against actual dexlib2 before mutation.

**Important:** Names above are DEX-level names. Decompiled Java is *not* assemble-ready Smali. Never assume a register number, return insertion index, or class descriptor from the mapping alone.

## Proposed feature boundaries

1. Add a uniquely keyed `morphe_amoled_brightness` preference to the *existing* theme screen after initialization. Check `Z1()` and `Y1(key)` for null/duplicates. Opening its panel must be implemented by an extension class injected into the APK; a patcher-only Kotlin class cannot be invoked by the app at runtime.
2. Persist explicit theme mode `device|black|white` (default `device`), AMOLED enablement, optional manual window brightness, and independent web luminance. A patch-time `browserTheme` option is **not** a runtime setting.
3. Window brightness: set `WindowManager.LayoutParams.screenBrightness` on the current Activity's Window. Default `-1f` leaves system brightness intact; manual percentages map to the valid Android window brightness range, with special consideration for 0%.
4. Web luminance: do **not** equate window brightness with WebContents rendering. Verify WebContents JavaScript/CSS injection, navigation and tab lifecycle before claiming this feature works; CSS brightness filters affect images and colors, not white alone.
5. Keep Cromite's manual `darken_websites_enabled` preference and URL-specific decisions. Existing `PureAmoledWebPatch.kt` inserts an early return and must be assessed for compatibility without silently modifying the original 13 patches.
6. Confirm fingerprints and injected instruction register constraints, compile, then smoke-test: theme changes, settings reopen, rotation, tab switch, navigation, background/foreground, darken-websites checkbox, and brightness restore.

## Delivery status

- [x] Theme preference screen access, preference creation, insertion, click dispatch identified in target DEX.
- [ ] Runtime extension implementation and APK integration.
- [ ] Patch 14 Kotlin hooks.
- [ ] WebContents lifecycle integration.
- [ ] Gradle compilation and Android smoke tests.

Existing patches are deliberately untouched until the new module is ready and validated.
