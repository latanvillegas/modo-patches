package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch

private const val CROMITE_WEB_VERSION = "153.0.8010.37"

/**
 * Compatibility guard for Cromite's existing per-site web darkening.
 *
 * In this version, WebContentsThemeClient.isForceDarkWebContentEnabled checks
 * WebContentsForceDark, DarkenWebsitesCheckboxInThemesSetting, the current
 * profile, night mode and the native per-site decision. Replacing that logic
 * with an unconditional return would silently discard manual controls.
 *
 * Keep this patch independent of AMOLED window brightness. It validates that
 * the original decision method is present but intentionally does not rewrite it.
 */
private val webContentsDarkModeDecisionFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/night_mode/WebContentsThemeClient;",
    name = "isForceDarkWebContentEnabled",
    returnType = "Z",
    parameters = listOf("Lorg/chromium/content_public/browser/WebContents;"),
)

@Suppress("unused")
val pureAmoledWebPatch = bytecodePatch(
    name = "Preserve Cromite web darkening",
    description = "Keeps Cromite's manual and per-site web darkening unchanged; AMOLED brightness is controlled separately.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"(CROMITE_WEB_VERSION))

    execute {
        // Validate the method against the exact supported APK. No bytecode
        // modification: preserve all native and user-controlled darkening paths.
        webContentsDarkModeDecisionFingerprint.method
    }
}
