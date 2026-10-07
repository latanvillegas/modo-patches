package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode

private const val TABS_VERSION = "153.0.8010.37"
private const val TAB_GRID_VIEW = "Lorg/chromium/chrome/browser/tasks/tab_management/TabGridView;"
private const val TAB_GRID_DIALOG_VIEW = "Lorg/chromium/chrome/browser/tasks/tab_management/TabGridDialogView;"

private val tabGridViewRefreshFingerprint = Fingerprint(
    definingClass = TAB_GRID_VIEW,
    name = "s",
    returnType = "V",
    parameters = emptyList(),
)

private val tabGridDialogBackgroundFingerprint = Fingerprint(
    definingClass = TAB_GRID_DIALOG_VIEW,
    name = "b",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)

private fun tabsBackgroundSmali(theme: String): String = when (theme) {
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
        if-nez v0, :pure_tabs_light
        const v0, -0x1000000
        goto :pure_tabs_apply
        :pure_tabs_light
        const v0, -0x1
        :pure_tabs_apply
        invoke-virtual {p0, v0}, Landroid/view/View;->setBackgroundColor(I)V
    """
}

@Suppress("unused")
val pureAmoledTabsPatch = bytecodePatch(
    name = "AMOLED - Tabs and tab strip",
    description = "Applies Device default / Pure Black / Pure White directly to verified Cromite TabGridView and TabGridDialogView backgrounds.",
) {
    compatibleWith("org.cromite.cromite"(TABS_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        val smali = tabsBackgroundSmali(selectedTheme)

        // TabGridView.s() owns the card background refresh path. Apply the
        // selected surface after Chromium finishes its resource/drawable updates.
        tabGridViewRefreshFingerprint.method.apply {
            val returnIndex = indexOfFirstInstructionOrThrow(Opcode.RETURN_VOID)
            addInstructions(returnIndex, smali)
        }

        // TabGridDialogView.b(View) reapplies the group-dialog background.
        // Keep the hook local to this concrete native container.
        tabGridDialogBackgroundFingerprint.method.apply {
            val returnIndex = indexOfFirstInstructionOrThrow(Opcode.RETURN_VOID)
            addInstructions(returnIndex, smali)
        }
    }
}
