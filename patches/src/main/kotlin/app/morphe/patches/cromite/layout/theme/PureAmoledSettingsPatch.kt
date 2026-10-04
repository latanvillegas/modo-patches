package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val SETTINGS_VERSION = "153.0.8010.37"
private const val SETTINGS_ACTIVITY =
    "Lorg/chromium/chrome/browser/settings/SettingsActivity;"

private val settingsActivityBackgroundFingerprint = Fingerprint(
    definingClass = SETTINGS_ACTIVITY,
    filters = listOf(
        methodCall(
            definingClass = "Ldr7;",
            name = "e",
            returnType = "Landroid/util/TypedValue;",
            parameters = listOf(
                "Landroid/content/Context;",
                "I",
                "Ljava/lang/String;",
            ),
        ),
        methodCall(
            definingClass = "Lis7;",
            name = "f",
            returnType = "I",
            parameters = listOf(
                "Landroid/content/Context;",
                "Landroid/util/TypedValue;",
            ),
        ),
    ),
)

private val settingsSetBackgroundColorCall = methodCall(
    definingClass = "Landroid/view/View;",
    name = "setBackgroundColor",
    returnType = "V",
    parameters = listOf("I"),
)

private fun settingsBackgroundSmali(
    theme: String,
    viewRegister: Int,
    scratchRegister: Int,
): String {
    val view = "v$viewRegister"
    val scratch = "v$scratchRegister"

    return when (theme) {
        "black" -> """
            const $scratch, -0x1000000
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
        "white" -> """
            const $scratch, -0x1
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
        else -> """
            invoke-virtual {$view}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object $scratch
            invoke-virtual {$scratch}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object $scratch
            iget $scratch, $scratch, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 $scratch, $scratch, 0x20
            ushr-int/lit8 $scratch, $scratch, 0x5
            neg-int $scratch, $scratch
            ushr-int/lit8 $scratch, $scratch, 0x8
            not-int $scratch, $scratch
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """
    }
}

/**
 * Settings is intentionally a separate selectable patch.
 *
 * Cromite 153 resolves the Settings root/AppBar background inside SettingsActivity
 * from theme attribute 0x7f050157 and then applies the same color through two
 * View.setBackgroundColor(I) calls. Patch those verified consumers directly instead
 * of changing pac.l globally, because pac.l also has unrelated consumers.
 */
@Suppress("unused")
val pureAmoledSettingsPatch = bytecodePatch(
    name = "AMOLED - Settings",
    description = "Applies the selected Device default / Pure Black / Pure White palette to the verified native Cromite Settings root and AppBar backgrounds without changing global SemanticColorUtils consumers.",
) {
    compatibleWith("org.cromite.cromite"(SETTINGS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        settingsActivityBackgroundFingerprint.method.apply {
            val backgroundIndices =
                findInstructionIndicesReversedOrThrow(settingsSetBackgroundColorCall)
            if (backgroundIndices.size != 2) {
                throw PatchException(
                    "AMOLED Settings verification failed for Cromite $SETTINGS_VERSION: " +
                        "expected exactly two SettingsActivity View.setBackgroundColor(I) calls, " +
                        "found ${backgroundIndices.size}.",
                )
            }

            // Both verified calls consume the same theme-derived color register.
            // Override each exact View immediately after Chromium's original setter.
            // Reusing the consumed color register avoids allocating registers and
            // introduces no branch targets around invoke/move-result pairs.
            backgroundIndices.forEach { index ->
                val invoke = getInstruction<FiveRegisterInstruction>(index)
                val viewRegister = invoke.registerC
                val colorRegister = invoke.registerD
                addInstructions(
                    index + 1,
                    settingsBackgroundSmali(selectedTheme, viewRegister, colorRegister),
                )
            }
        }
    }
}
