package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// Reconstructed from the original 1.50.0 PureAmoledToolbarPatchKt bytecode.
// Verified target: ToolbarTablet.i(IZ)V in Cromite 153.0.8010.37.
private val toolbarTabletBackgroundFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/toolbar/top/ToolbarTablet;",
    name = "i",
    returnType = "V",
    parameters = listOf("I", "Z"),
)

private fun toolbarBackgroundSmali(theme: String) = when (theme) {
    "black" -> "const p1, -0x1000000"
    "white" -> "const p1, -0x1"
    else -> """
        invoke-virtual {p0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
        move-result-object p1
        invoke-virtual {p1}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
        move-result-object p1
        iget p1, p1, Landroid/content/res/Configuration;->uiMode:I
        and-int/lit8 p1, p1, 0x30
        xor-int/lit8 p1, p1, 0x20
        if-nez p1, :pure_toolbar_light
        const p1, -0x1000000
        goto :pure_toolbar_apply
        :pure_toolbar_light
        const p1, -0x1
        :pure_toolbar_apply
    """.trimIndent()
}

@Suppress("unused")
val pureAmoledToolbarPatch = bytecodePatch(
    name = "AMOLED - Toolbar",
    description = "Applies Device default / Pure Black / Pure White directly to the verified Cromite ToolbarTablet background setter.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))

    val browserTheme = recoveredBrowserThemeOption

    execute {
        toolbarTabletBackgroundFingerprint.method.addInstructions(
            0,
            toolbarBackgroundSmali(browserTheme.value ?: "device"),
        )
    }
}
