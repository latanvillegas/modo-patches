package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val TOOLBAR_VERSION = "153.0.8010.37"
private const val TOOLBAR_TABLET = "Lorg/chromium/chrome/browser/toolbar/top/ToolbarTablet;"

private val toolbarTabletBackgroundFingerprint = Fingerprint(
    definingClass = TOOLBAR_TABLET,
    name = "i",
    returnType = "V",
    parameters = listOf("I", "Z"),
)

private fun toolbarBackgroundSmali(theme: String): String = when (theme) {
    "black" -> """
        const p1, -0x1000000
    """
    "white" -> """
        const p1, -0x1
    """
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
    """
}

@Suppress("unused")
val pureAmoledToolbarPatch = bytecodePatch(
    name = "AMOLED - Toolbar",
    description = "Applies Device default / Pure Black / Pure White directly to the verified Cromite ToolbarTablet background setter.",
) {
    compatibleWith("org.cromite.cromite"(TOOLBAR_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"

        // ToolbarTablet.i(IZ)V writes p1 to both the toolbar itself and its
        // verified child container. Replace only that color argument, before
        // Chromium performs either setBackgroundColor(I) call.
        toolbarTabletBackgroundFingerprint.method.addInstructions(
            0,
            toolbarBackgroundSmali(selectedTheme),
        )
    }
}
