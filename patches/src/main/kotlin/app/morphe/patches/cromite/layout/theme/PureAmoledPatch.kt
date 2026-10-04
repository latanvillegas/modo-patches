package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption

private const val CROMITE_VERSION = "153.0.8010.37"
private const val SEMANTIC_COLOR_UTILS = "Lpac;"
private const val CHROME_COLORS = "Lq72;"
private const val DYNAMIC_COLORS = "Lqb4;"
private const val DYNAMIC_COLORS_OPTIONS = "Ltb4;"
private const val TAB_UI_THEME_UTILS = "Laqe;"
private const val CONTEXT = "Landroid/content/Context;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val PURE_BLACK = "#FF000000"
private const val PURE_WHITE = "#FFFFFFFF"
private const val THEME_DEVICE = "device"
private const val THEME_BLACK = "black"
private const val THEME_WHITE = "white"

internal val browserThemeOption = stringOption(
    key = "browserTheme",
    default = THEME_DEVICE,
    values = mapOf(
        "Device default" to THEME_DEVICE,
        "Pure Black" to THEME_BLACK,
        "Pure White" to THEME_WHITE,
    ),
    title = "Browser theme",
    description = "Choose Device default, Pure Black (#000000), or Pure White (#FFFFFF).",
)

private fun semanticSurfaceFingerprint(name: String) = Fingerprint(
    definingClass = SEMANTIC_COLOR_UTILS,
    name = name,
    returnType = "I",
    parameters = listOf(CONTEXT),
)

private val colorSurfaceFingerprint = semanticSurfaceFingerprint("c")
private val colorSurfaceBrightFingerprint = semanticSurfaceFingerprint("d")
private val colorSurfaceContainerFingerprint = semanticSurfaceFingerprint("e")
private val colorSurfaceContainerHighestFingerprint = semanticSurfaceFingerprint("f")
private val colorSurfaceContainerLowFingerprint = semanticSurfaceFingerprint("g")
private val defaultBgColorFingerprint = semanticSurfaceFingerprint("h")
private val defaultIconColorFingerprint = semanticSurfaceFingerprint("i")
private val defaultTextColorFingerprint = semanticSurfaceFingerprint("j")
internal val settingsBackgroundColorFingerprint = semanticSurfaceFingerprint("l")

private val primaryBackgroundColorFingerprint = Fingerprint(
    definingClass = CHROME_COLORS,
    name = "b",
    returnType = "I",
    parameters = listOf(CONTEXT, "Z"),
)

private val tabStripBackgroundDefaultFingerprint = Fingerprint(
    definingClass = TAB_UI_THEME_UTILS,
    name = "c",
    returnType = "I",
    parameters = listOf(CONTEXT, "Z"),
)

private val dynamicColorsApplyFingerprint = Fingerprint(
    definingClass = DYNAMIC_COLORS,
    name = "a",
    returnType = "V",
    parameters = listOf(ACTIVITY, DYNAMIC_COLORS_OPTIONS),
)

// Device default intentionally consumes Chromium's effective UI Configuration.
// ThemeSettingsFragment persists ui_theme_setting as 0=system, 1=light, 2=dark;
// Chromium resolves that preference before these native color hooks run. Reading
// Configuration.uiMode here therefore follows both the browser selector and system
// night-mode changes without coupling this patch to SharedPreferences or WebContents.
internal fun foregroundSmali(theme: String): String = when (theme) {
    THEME_BLACK -> """
        const v0, -0x1
        return v0
    """
    THEME_WHITE -> """
        const v0, -0x1000000
        return v0
    """
    else -> """
        invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
        move-result-object v0
        invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
        move-result-object v0
        iget v0, v0, Landroid/content/res/Configuration;->uiMode:I
        and-int/lit8 v0, v0, 0x30
        xor-int/lit8 v0, v0, 0x20
        if-nez v0, :pure_foreground_light
        const v0, -0x1
        return v0
        :pure_foreground_light
        const v0, -0x1000000
        return v0
    """
}

internal fun surfaceSmali(theme: String): String = when (theme) {
    THEME_BLACK -> """
        const v0, -0x1000000
        return v0
    """
    THEME_WHITE -> """
        const v0, -0x1
        return v0
    """
    else -> """
        invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
        move-result-object v0
        invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
        move-result-object v0
        iget v0, v0, Landroid/content/res/Configuration;->uiMode:I
        and-int/lit8 v0, v0, 0x30
        xor-int/lit8 v0, v0, 0x20
        if-nez v0, :pure_light
        const v0, -0x1000000
        return v0
        :pure_light
        const v0, -0x1
        return v0
    """
}

@Suppress("unused")
val pureAmoledPatch = bytecodePatch(
    name = "AMOLED - Main browser UI",
    description = "Main Cromite browser UI theme. Device default follows Cromite/System light-dark state; manual Pure Black/Pure White override it. Web content is not recolored.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_VERSION))
    // Main browser UI is the user-facing AMOLED entry point. Make the isolated
    // AppMenu bytecode patch an explicit dependency so it cannot be omitted by
    // patch selection while its resource-only effects make the build look applied.
    dependsOn(pureAmoledAppMenuPatch)
    val browserTheme by browserThemeOption()

    execute {
        val selectedTheme = browserTheme ?: THEME_DEVICE
        val nativeSurfaceTargets = listOf(
            colorSurfaceFingerprint, colorSurfaceBrightFingerprint, colorSurfaceContainerFingerprint,
            colorSurfaceContainerHighestFingerprint, colorSurfaceContainerLowFingerprint,
            defaultBgColorFingerprint, primaryBackgroundColorFingerprint,
        )
        nativeSurfaceTargets.forEach { fingerprint ->
            fingerprint.method.addInstructions(0, surfaceSmali(selectedTheme))
        }

        // TabUiThemeUtil.getTabStripBackgroundColorDefault(Context, boolean) survives R8
        // as aqe.c. Its source path bypasses the surviving pac surface wrappers, so
        // patch it explicitly; this is native tab-strip UI only, never WebContents.
        tabStripBackgroundDefaultFingerprint.method.addInstructions(
            0,
            surfaceSmali(selectedTheme),
        )

        // These two SemanticColorUtils methods survive R8 in Cromite 153 as pac.i/pac.j.
        // Patch them independently from surfaces so Pure Black never leaves black
        // primary text/icons on a black browser surface (and vice versa for Pure White).
        listOf(defaultIconColorFingerprint, defaultTextColorFingerprint).forEach { fingerprint ->
            fingerprint.method.addInstructions(0, foregroundSmali(selectedTheme))
        }

        // Dynamic Material colors would re-introduce tinted gray/colored surfaces.
        dynamicColorsApplyFingerprint.method.addInstructions(0, "return-void")
    }
}
