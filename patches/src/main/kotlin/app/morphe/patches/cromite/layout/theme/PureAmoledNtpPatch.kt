package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val NTP_LAYOUT = "Lorg/chromium/chrome/browser/ntp/NewTabPageLayout;"
private const val NTP_VERSION = "153.0.8010.37"

private val ntpLayoutInflateFingerprint = Fingerprint(
    definingClass = NTP_LAYOUT,
    name = "onFinishInflate",
    returnType = "V",
    parameters = emptyList(),
)

private fun ntpBackgroundSmali(theme: String): String = when (theme) {
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
        and-int/lit8 v0, v0, 0x20
        ushr-int/lit8 v0, v0, 0x5
        neg-int v0, v0
        ushr-int/lit8 v0, v0, 0x8
        not-int v0, v0
        invoke-virtual {p0, v0}, Landroid/view/View;->setBackgroundColor(I)V
    """
}

@Suppress("unused")
val pureAmoledNtpPatch = bytecodePatch(
    name = "AMOLED - New Tab Page",
    description = "Applies Device default / Pure Black / Pure White directly to Cromite NewTabPageLayout without recoloring WebContents, toolbar, Settings, or system bars.",
) {
    compatibleWith("org.cromite.cromite"(NTP_VERSION))
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: "device"
        ntpLayoutInflateFingerprint.method.addInstructions(
            0,
            ntpBackgroundSmali(selectedTheme),
        )
    }
}
