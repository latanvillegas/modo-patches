package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.bytecodePatch

private const val CROMITE_WEB_VERSION = "153.0.8010.37"

/**
 * Web-content AMOLED support for Cromite 153.
 *
 * Chromium 153 drives preferred_color_scheme from the Android night-mode
 * delegate and force_dark_mode_enabled from IsForceDarkWebContentEnabled().
 * The previous implementation attempted to fingerprint the historical
 * "darken_websites_enabled" Java preference path. That path is not present in
 * Cromite 153's DEX and caused patch application to fail.
 *
 * Keep this patch application-safe until the exact obfuscated Cromite 153
 * method backing IsForceDarkWebContentEnabled() is fingerprinted from the
 * target APK/mapping. Do not ship a speculative DEX fingerprint.
 */
@Suppress("unused")
val pureAmoledWebPatch = bytecodePatch(
    name = "Pure AMOLED web content",
    description = "Cromite 153 web AMOLED hook. Safe placeholder pending an exact fingerprint of the current force-dark delegate.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_WEB_VERSION))

    execute {
        // Intentionally no bytecode mutation yet.
        // This removes the known-bad historical fingerprint so patching the
        // target APK no longer aborts with Failed to match the fingerprint.
    }
}
