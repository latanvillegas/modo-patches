package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val CROMITE_WEB_VERSION = "153.0.8010.37"
private const val DARKEN_WEBSITES_PREF = "darken_websites_enabled"

/**
 * Chromium's web-content dark-mode decision is the conjunction of the global
 * night-mode state and the darken-websites preference.  Anchor on the stable
 * preference key, but capture the first boolean result produced before that
 * key is read: this is the global night-mode provider result.
 *
 * Returning that value immediately makes algorithmic web darkening follow
 * Android/Cromite night mode directly, without depending on the checkbox.
 */
private val webContentsDarkModeDecisionFingerprint = Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    strings = listOf(DARKEN_WEBSITES_PREF),
    filters = listOf(
        opcode(Opcode.INVOKE_INTERFACE),
        opcode(Opcode.MOVE_RESULT),
        string(DARKEN_WEBSITES_PREF),
    ),
)

internal val pureAmoledWebPatch = bytecodePatch(
    description = "Internal web-content theme synchronization for the Cromite browser theme patch.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_WEB_VERSION))

    execute {
        webContentsDarkModeDecisionFingerprint.let { fingerprint ->
            val nightModeResult = fingerprint.instructionMatches[1]
            val register = nightModeResult
                .getInstruction<OneRegisterInstruction>()
                .registerA

            val injected = when (browserThemeOption.value) {
                "black" -> "const/4 v$register, 0x1\nreturn v$register"
                "white" -> "const/4 v$register, 0x0\nreturn v$register"
                else -> "return v$register"
            }
            fingerprint.method.addInstructions(nightModeResult.index + 1, injected)
        }
    }
}
