package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode

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

private fun omniboxBackgroundSmali(theme: String): String = when (theme) {
    "black" -> """
        const v0, -0x1000000
        invoke-virtual {p0, v0}, Landroid/view/View;->setBackgroundColor(I)V
    """
    "white" -> """
        const v0, -0x1
        invoke-virtual {p0, v0}, Landroid/view/View;->setBackgroundColor(I)V
    """
    else -> """
        invoke-virtual {p0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
        move-result-object v0
        invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
        move-result-object v0
        iget v0, v0, Landroid/content/res/Configuration;->uiMode:I
        and-int/lit8 v0, v0, 0x30
        xor-int/lit8 v0, v0, 0x20
        if-nez v0, :pure_omnibox_light
        const v0, -0x1000000
        goto :pure_omnibox_apply
        :pure_omnibox_light
        const v0, -0x1
        :pure_omnibox_apply
        invoke-virtual {p0, v0}, Landroid/view/View;->setBackgroundColor(I)V
    """
}

@Suppress("unused")
val pureAmoledOmniboxPatch = bytecodePatch(
    name = "AMOLED - Omnibox and search UI",
    description = "Applies Device default / Pure Black / Pure White directly to Cromite LocationBarTablet after initial inflation and dynamic background refreshes.",
) {
    compatibleWith("org.cromite.cromite"(OMNIBOX_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        val smali = omniboxBackgroundSmali(selectedTheme)

        // Q() reapplies Chromium's default tablet omnibox drawables. Appending our
        // targeted background after the method's original logic prevents those
        // dynamic state/theme refreshes from restoring a gray surface.
        locationBarTabletRefreshFingerprint.method.apply {
            val returnIndex = indexOfFirstInstructionOrThrow(Opcode.RETURN_VOID)
            addInstructions(returnIndex, smali)
        }

        // onFinishInflate() establishes the initial LocationBarTablet background.
        // Reapply the selected AMOLED surface after Chromium finishes inflation.
        locationBarTabletInflateFingerprint.method.apply {
            val returnIndex = indexOfFirstInstructionOrThrow(Opcode.RETURN_VOID)
            addInstructions(returnIndex, smali)
        }
    }
}
