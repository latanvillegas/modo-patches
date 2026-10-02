package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Settings is intentionally a separate selectable patch.
 *
 * Cromite 153 keeps SemanticColorUtils.getSettingsBackgroundColor(Context)
 * as pac.l. Patching that semantic boundary avoids coupling SettingsActivity
 * to the main-browser tab/toolbar hook.
 */
@Suppress("unused")
val pureAmoledSettingsPatch = bytecodePatch(
    name = "AMOLED - Settings",
    description = "Applies the selected Device default / Pure Black / Pure White palette to the native Cromite Settings background independently from the main browser UI.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme by browserThemeOption()

    execute {
        settingsBackgroundColorFingerprint.method.addInstructions(
            0,
            surfaceSmali(browserTheme ?: "device"),
        )
    }
}
