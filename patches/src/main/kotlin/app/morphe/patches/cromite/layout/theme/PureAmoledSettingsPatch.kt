package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val SETTINGS_VERSION = "153.0.8010.37"
/**
 * Settings is intentionally a separate selectable patch.
 *
 * Cromite 153 keeps SemanticColorUtils.getSettingsBackgroundColor(Context)
 * as pac.l. Runtime verification showed that the previously guessed Settings
 * resource names do not exist in the decoded APK, so this patch intentionally
 * uses only the verified semantic bytecode boundary.
 */
@Suppress("unused")
val pureAmoledSettingsPatch = bytecodePatch(
    name = "AMOLED - Settings",
    description = "Applies the selected Device default / Pure Black / Pure White palette to native Cromite Settings independently from the main browser UI and NTP.",
) {
    compatibleWith("org.cromite.cromite"(SETTINGS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        settingsBackgroundColorFingerprint.method.addInstructions(
            0,
            surfaceSmali(selectedTheme),
        )
    }
}
