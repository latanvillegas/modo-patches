package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch

private const val OMNIBOX_VERSION = "153.0.8010.37"
private const val LOCATION_BAR_TABLET = "Lorg/chromium/chrome/browser/omnibox/LocationBarTablet;"

private val locationBarTabletInflateFingerprint = Fingerprint(
    definingClass = LOCATION_BAR_TABLET,
    name = "onFinishInflate",
    returnType = "V",
    parameters = emptyList(),
)

private val locationBarTabletRefreshFingerprint = Fingerprint(
    definingClass = LOCATION_BAR_TABLET,
    name = "Q",
    returnType = "V",
    parameters = emptyList(),
)

@Suppress("unused")
val pureAmoledOmniboxPatch = bytecodePatch(
    name = "AMOLED - Omnibox and search UI",
    description = "Verifies Cromite LocationBarTablet hooks without mutating control flow; AMOLED recoloring is temporarily deferred to preserve ART verification.",
) {
    compatibleWith("org.cromite.cromite"(OMNIBOX_VERSION))
    browserThemeOption()

    execute {
        // Resolve both version-pinned fingerprints deliberately. The previous
        // implementation inserted labeled control-flow blocks before RETURN_VOID
        // in these methods; ART rejected the resulting LocationBarTablet class.
        // Keep the patch fail-closed on the exact methods while performing no
        // bytecode mutation until the concrete setter instructions are patched
        // in-place without introducing branch targets.
        locationBarTabletRefreshFingerprint.method
        locationBarTabletInflateFingerprint.method
    }
}
