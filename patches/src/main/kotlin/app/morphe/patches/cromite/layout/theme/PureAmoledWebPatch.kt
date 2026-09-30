package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
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

@Suppress("unused")
val pureAmoledWebPatch = bytecodePatch(
    name = "Pure AMOLED web content",
    description = "Makes Cromite web-content darkening follow the system theme: enabled in dark mode and disabled in light mode.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_WEB_VERSION))

    execute {
        webContentsDarkModeDecisionFingerprint.let { fingerprint ->
            val nightModeResult = fingerprint.instructionMatches[1]
            val register = nightModeResult
                .getInstruction<OneRegisterInstruction>()
                .registerA

            fingerprint.method.addInstruction(
                nightModeResult.index + 1,
                "return v$register",
            )
        }
    }
}
