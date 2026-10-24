package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// TabGridView.s() is intentionally not patched: on Cromite 153.0.8010.37,
// rewriting this method produced an ART VerifyError (invalid branch target).
// Keep the dialog-only hook until a verified, safe grid implementation exists.
private val tabGridDialogBackgroundFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/tasks/tab_management/TabGridDialogView;",
    name = "b",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)

private fun tabsBackgroundSmali(theme: String) = when (theme) {
    "black" -> """
        const v0, -0x1000000
        invoke-virtual {p0, v0}, Landroid/view/View;->setBackgroundColor(I)V
    """.trimIndent()
    "white" -> """
        const v0, -0x1
        invoke-virtual {p0, v0}, Landroid/view/View;->setBackgroundColor(I)V
    """.trimIndent()
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
    """.trimIndent()
}

@Suppress("unused")
val pureAmoledTabsPatch = bytecodePatch(
    name = "AMOLED - Tabs and tab strip",
    description = "Applies Device default / Pure Black / Pure White to the tab grid dialog background. TabGridView modification is disabled to prevent a verified startup crash.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    val browserTheme = recoveredBrowserThemeOption

    execute {
        val instructions = tabsBackgroundSmali(browserTheme.value ?: "device")
        tabGridDialogBackgroundFingerprint.method.apply {
            addInstructions(0, instructions)
        }
    }
}
