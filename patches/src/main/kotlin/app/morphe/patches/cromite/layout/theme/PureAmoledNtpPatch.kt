package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val NTP_CHROME_COLORS_PROPERTIES = "Lm19;"

/**
 * New Tab Page is intentionally isolated from the main browser UI patch.
 *
 * Cromite 153 / R8 maps NtpChromeColorsProperties to m19. Its method c is
 * getBackgroundColorFromNtpBackgroundData(...), where the NTP surface roles
 * are resolved after SemanticColorUtils has been inlined.
 */
private val ntpBackgroundColorFingerprint = Fingerprint(
    definingClass = NTP_CHROME_COLORS_PROPERTIES,
    name = "c",
    returnType = "I",
)

@Suppress("unused")
val pureAmoledNtpPatch = bytecodePatch(
    name = "AMOLED - New Tab Page",
    description = "Applies Device default / Pure Black / Pure White to the native Cromite New Tab Page background independently from toolbar, Settings, and WebContents.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme by browserThemeOption()

    execute {
        ntpBackgroundColorFingerprint.method.addInstructions(
            0,
            surfaceSmali(browserTheme ?: "device"),
        )
    }
}
