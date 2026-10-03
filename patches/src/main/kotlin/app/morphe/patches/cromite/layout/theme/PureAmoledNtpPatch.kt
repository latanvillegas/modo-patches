package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val NTP_LAYOUT = "Lorg/chromium/chrome/browser/ntp/NewTabPageLayout;"
private const val NTP_VERSION = "153.0.8010.37"

private val ntpLayoutInflateFingerprint = Fingerprint(
    definingClass = NTP_LAYOUT,
    name = "onFinishInflate",
    returnType = "V",
    parameters = emptyList(),
)

private val setBackgroundColorCall = methodCall(
    definingClass = "Landroid/view/View;",
    name = "setBackgroundColor",
    returnType = "V",
    parameters = listOf("I"),
)

private fun linearNtpBackgroundSmali(
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

@Suppress("unused")
val pureAmoledNtpPatch = bytecodePatch(
    name = "AMOLED - New Tab Page",
    description = "Applies Device default / Pure Black / Pure White after the verified NewTabPageLayout background setter without recoloring tiles, WebContents, toolbar, Settings, or system bars.",
) {
    compatibleWith("org.cromite.cromite"(NTP_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        ntpLayoutInflateFingerprint.method.apply {
            findInstructionIndicesReversedOrThrow(setBackgroundColorCall).forEach { index ->
                val invoke = getInstruction<FiveRegisterInstruction>(index)
                val viewRegister = invoke.registerC
                val colorRegister = invoke.registerD

                // Chromium has just consumed the original color register.
                // Reuse it linearly and immediately override the same View.
                // No labels, branches, or new branch targets are introduced.
                addInstructions(
                    index + 1,
                    linearNtpBackgroundSmali(selectedTheme, viewRegister, colorRegister),
                )
            }
        }
    }
}
