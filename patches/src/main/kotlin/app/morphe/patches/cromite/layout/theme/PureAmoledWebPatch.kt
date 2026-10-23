package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val CROMITE_WEB_VERSION = "153.0.8010.37"

/**
 * Cromite 153: the old no-argument darken_websites_enabled method is gone.
 * R8 mapping and the target classes.dex identify this retained Chromium method.
 *
 * Preserve the existing null/profile/night-mode and per-site opt-out checks.
 * Only bypass the two feature-flag gates:
 *  - WebContentsForceDark must not return true before checking night mode.
 *  - DarkenWebsitesCheckboxInThemesSetting must not block the night-mode path.
 *
 * This enables Chromium's existing algorithmic darkening; it does not by
 * itself guarantee pure #000000 backgrounds on every website.
 */
private val webContentsForceDarkFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/night_mode/WebContentsThemeClient;",
    name = "isForceDarkWebContentEnabled",
    returnType = "Z",
    parameters = listOf("Lorg/chromium/content_public/browser/WebContents;"),
    filters = listOf(
        string("WebContentsForceDark"),
        string("DarkenWebsitesCheckboxInThemesSetting"),
    ),
)

@Suppress("unused")
val pureAmoledWebPatch = bytecodePatch(
    name = "Pure AMOLED web content",
    description = "Enables Cromite web-content darkening in night mode while preserving per-site exclusions; pure-black rendering is not yet implemented.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_WEB_VERSION))

    execute {
        val fingerprint = webContentsForceDarkFingerprint
        val method = fingerprint.method
        val matches = fingerprint.instructionMatches
        check(matches.size == 2) {
            "Cromite 153 web dark-mode feature anchors changed"
        }

        val instructions = method.implementation?.instructions?.toList()
            ?: error("Cromite 153 web dark-mode method has no implementation")
        val forceDark = matches[0].index
        val checkbox = matches[1].index

        check(forceDark + 5 < checkbox && checkbox + 4 < instructions.size) {
            "Cromite 153 web dark-mode instruction layout changed"
        }

        fun requireOpcodes(start: Int, vararg expected: Opcode) {
            expected.forEachIndexed { offset, opcode ->
                check(instructions[start + offset].opcode == opcode) {
                    "Cromite 153 web dark-mode opcode changed at ${start + offset}: " +
                        "expected $opcode, found ${instructions[start + offset].opcode}"
                }
            }
        }

        // Exact sequence observed in Cromite 153.0.8010.37 classes.dex.
        requireOpcodes(
            forceDark,
            Opcode.CONST_STRING,
            Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT,
            Opcode.CONST_4,
            Opcode.IF_EQZ,
            Opcode.RETURN,
        )
        requireOpcodes(
            checkbox,
            Opcode.CONST_STRING,
            Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT,
            Opcode.IF_NEZ,
            Opcode.GOTO,
        )

        // Replace with same-size no-ops; preserve all existing labels and
        // branch targets, and leave the downstream night-mode/site logic intact.
        method.replaceInstruction(checkbox + 4, "nop")
        method.replaceInstruction(forceDark + 5, "nop")
    }
}
