package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

private const val CROMITE_WEB_VERSION = "153.0.8010.37"
private const val CONTROLLER =
    "Lapp/morphe/extension/cromite/amoled/AmoledBrightnessController;"

/**
 * Preserve Chromium's existing true branches (forced dark and manual/per-site
 * darkening). Only replace the final false result with the opt-in AMOLED
 * automatic decision. The helper returns false unless both AMOLED and
 * automatic mode are selected.
 */
private val webContentsDarkModeDecisionFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/night_mode/WebContentsThemeClient;",
    name = "isForceDarkWebContentEnabled",
    returnType = "Z",
    parameters = listOf("Lorg/chromium/content_public/browser/WebContents;"),
)

@Suppress("unused")
val pureAmoledWebPatch = bytecodePatch(
    name = "AMOLED automatic web darkening",
    description = "Adds automatic AMOLED web darkening while retaining Cromite's manual and per-site darkening.",
    default = false,
) {
    compatibleWith("org.cromite.cromite"(CROMITE_WEB_VERSION))
    dependsOn(cromiteAmoledBrightnessPatch)

    execute {
        classDefBy(CONTROLLER)
        val method = webContentsDarkModeDecisionFingerprint.method
        val instructions = method.implementation!!.instructions.toList()
        val last = instructions.lastIndex
        check(last > 0 && instructions[last].opcode == Opcode.RETURN) {
            "AMOLED web: expected final boolean return in WebContentsThemeClient"
        }
        val result = instructions[last] as OneRegisterInstruction
        val zero = instructions[last - 1]
        val tail = instructions.mapIndexed { index, instruction ->
            val register = (instruction as? OneRegisterInstruction)?.registerA
            val literal = (instruction as? NarrowLiteralInstruction)?.narrowLiteral
            "$index:${instruction.opcode}(reg=$register,lit=$literal)"
        }.joinToString(" | ")
        check(zero.opcode == Opcode.CONST_4 &&
            zero is NarrowLiteralInstruction && zero.narrowLiteral == 0 &&
            (zero as OneRegisterInstruction).registerA == result.registerA) {
            "AMOLED web: final return is not a verified constant-false path; DEX instructions: $tail"
        }

        // Insert in reverse order to preserve the original return index.
        // Reuse the return register; no extra locals or register-count changes.
        method.addInstruction(last, "move-result v${result.registerA}")
        method.addInstruction(last, "invoke-static {}, $CONTROLLER->shouldAutoDarkenWebContents()Z")
    }
}
