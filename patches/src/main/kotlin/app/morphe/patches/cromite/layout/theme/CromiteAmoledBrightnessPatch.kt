package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private const val CROMITE_PATCH14_VERSION = "153.0.8010.37"
private const val BRIGHTNESS_CONTROLLER =
    "Lapp/morphe/extension/cromite/amoled/AmoledBrightnessController;"

/**
 * First executable slice of patch 14: bundles the Cromite Android extension
 * and applies persisted per-window brightness whenever the tabbed activity
 * resumes. The controller defaults to -1f (system brightness).
 *
 * Do not insert a ThemeSettingsFragment hook here until the click listener
 * and target obfuscated Preference API are validated end-to-end.
 */
private val cromiteTabbedActivityResumeFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;",
    name = "onResume",
    returnType = "V",
    parameters = listOf(),
)

@Suppress("unused")
val cromiteAmoledBrightnessPatch = bytecodePatch(
    name = "Cromite AMOLED brightness controls",
    description = "Injects the Cromite runtime controller and restores the saved window brightness on resume.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"(CROMITE_PATCH14_VERSION))

    // Morphe packages the Android extension as extensions/cromite.mpe.
    // The extension is NOT supplied by the patcher Kotlin classpath.
    extendWith("extensions/cromite.mpe")

    execute {
        // Fail closed if the extension wasn't bundled into the target DEX.
        classDefBy(BRIGHTNESS_CONTROLLER)

        // p0 is the Activity receiver, valid for invoke-static/range.
        // Insert before the original onResume instructions so every normal
        // resume applies the preference, without consuming scratch registers.
        cromiteTabbedActivityResumeFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, " +
                "$BRIGHTNESS_CONTROLLER->applyWindowBrightness(Landroid/app/Activity;)V",
        )
    }
}
