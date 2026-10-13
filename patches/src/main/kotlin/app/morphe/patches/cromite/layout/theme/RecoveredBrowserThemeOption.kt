package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.patch.stringOption

/**
 * Shared option recovered from PureAmoledPatchKt.class in patches-1.50.0.mpp.
 *
 * The original 1.50.0 bytecode exposes getBrowserThemeOption() and each
 * AMOLED patch registers that same option instead of declaring its own.
 *
 * Kept separate from the existing PureAmoledPatch.kt to avoid changing
 * currently working patches during the recovery process.
 */
internal val recoveredBrowserThemeOption = stringOption(
    key = "browserTheme",
    default = "device",
    values = mapOf(
        "Device default" to "device",
        "Pure Black" to "black",
        "Pure White" to "white",
    ),
    title = "Browser theme",
    description = "Choose Device default, Pure Black (#000000), or Pure White (#FFFFFF).",
)
