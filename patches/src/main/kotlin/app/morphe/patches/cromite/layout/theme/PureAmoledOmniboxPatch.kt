package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val OMNIBOX_VERSION = "153.0.8010.37"
private const val LOCATION_BAR_TABLET = "Lorg/chromium/chrome/browser/omnibox/LocationBarTablet;"

private val locationBarTabletInflateFingerprint = Fingerprint(
    definingClass = LOCATION_BAR_TABLET,
    name = "onFinishInflate",
    returnType = "V",
    parameters = emptyList(),
)

private val locationBarTabletRefreshFingerprint = Fingerprint(
    definingClass = LOCATION_BAR_TABLET,
    name = "Q",
    returnType = "V",
    parameters = emptyList(),
)

private val setBackgroundCall = methodCall(
    definingClass = "Landroid/view/View;",
    name = "setBackground",
    returnType = "V",
    parameters = listOf("Landroid/graphics/drawable/Drawable;"),
)

private fun linearBackgroundSmali(
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
val pureAmoledOmniboxPatch = bytecodePatch(
    name = "AMOLED - Omnibox and search UI",
    description = "Applies Device default / Pure Black / Pure White to verified LocationBarTablet background setters without adding control-flow branches.",
) {
    compatibleWith("org.cromite.cromite"(OMNIBOX_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        fun patchVerifiedBackgroundSetters(method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod) {
            method.apply {
                findInstructionIndicesReversedOrThrow(setBackgroundCall).forEach { index ->
                    val invoke = getInstruction<FiveRegisterInstruction>(index)
                    val viewRegister = invoke.registerC
                    val drawableRegister = invoke.registerD

                    // Reuse the original Drawable argument only after setBackground()
                    // consumed it. Device mode is resolved arithmetically:
                    // light -> #FFFFFFFF, dark -> #FF000000.
                    // No labels, if/goto instructions, or new branch targets.
                    addInstructions(
                        index + 1,
                        linearBackgroundSmali(selectedTheme, viewRegister, drawableRegister),
                    )
                }
            }
        }

        patchVerifiedBackgroundSetters(locationBarTabletRefreshFingerprint.method)
        patchVerifiedBackgroundSetters(locationBarTabletInflateFingerprint.method)
    }
}
