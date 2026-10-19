package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

/**
 * Reconstructed directly from PureAmoledOmniboxPatchKt.class in
 * patches-1.50.0.mpp, including its StringConcatFactory smali templates.
 */
private val locationBarTabletInflateFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/omnibox/LocationBarTablet;",
    name = "onFinishInflate",
    returnType = "V",
)

private val locationBarTabletRefreshFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/omnibox/LocationBarTablet;",
    name = "Q",
    returnType = "V",
)

private val omniboxSetBackgroundCall = methodCall(
    "Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V",
)

private fun linearBackgroundSmali(theme: String, viewRegister: Int, colorRegister: Int): String =
    when (theme) {
        "black" -> """
            const v$colorRegister, -0x1000000
            invoke-virtual {v$viewRegister, v$colorRegister}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        "white" -> """
            const v$colorRegister, -0x1
            invoke-virtual {v$viewRegister, v$colorRegister}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        else -> """
            invoke-virtual {v$viewRegister}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object v$colorRegister
            invoke-virtual {v$colorRegister}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object v$colorRegister
            iget v$colorRegister, v$colorRegister, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 v$colorRegister, v$colorRegister, 0x20
            ushr-int/lit8 v$colorRegister, v$colorRegister, 0x5
            neg-int v$colorRegister, v$colorRegister
            ushr-int/lit8 v$colorRegister, v$colorRegister, 0x8
            not-int v$colorRegister, v$colorRegister
            invoke-virtual {v$viewRegister, v$colorRegister}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
    }

@Suppress("unused")
val pureAmoledOmniboxPatch = bytecodePatch(
    name = "AMOLED - Omnibox and search UI",
    description = "Applies Device default / Pure Black / Pure White to verified LocationBarTablet background setters without adding control-flow branches.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val theme = browserTheme.value ?: "device"

        fun patchVerifiedBackgroundSetters(method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod) {
            val indices = method.findInstructionIndicesReversedOrThrow(omniboxSetBackgroundCall)
            indices.forEach { index ->
                val instruction = method.getInstruction(index) as FiveRegisterInstruction
                method.addInstructions(
                    index + 1,
                    linearBackgroundSmali(theme, instruction.registerC, instruction.registerD),
                )
            }
        }

        patchVerifiedBackgroundSetters(locationBarTabletRefreshFingerprint.method)
        patchVerifiedBackgroundSetters(locationBarTabletInflateFingerprint.method)
    }
}
