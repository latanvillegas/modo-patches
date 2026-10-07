package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

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

private val settingsSearchSurfaceFingerprint = Fingerprint(
    definingClass = "Ldic;",
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/content/Context;->getDrawable(I)Landroid/graphics/drawable/Drawable;"),
        methodCall(opcode = Opcode.INVOKE_STATIC, smali = "Lis7;->c(Landroid/content/Context;ILjava/lang/String;)I"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/graphics/drawable/Drawable;->setTint(I)V"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V"),
    ),
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

        // Settings search has its own drawable tint path in dic and bypasses the
        // generic surface wrappers. Cromite 153 loads drawable 0x7f0905ab,
        // resolves its semantic tint, then assigns that same drawable to the two
        // search surfaces. Override only the tint argument immediately before
        // Drawable.setTint(); do not alter the shared semantic attribute/resource.
        settingsSearchSurfaceFingerprint.apply {
            if (instructionMatches.size != 5) {
                throw IllegalStateException(
                    "AMOLED Settings search verification failed for Cromite $CROMITE_VERSION: " +
                        "expected 5 structural anchors, found ${instructionMatches.size}."
                )
            }

            val semanticIndex = instructionMatches[1].index
            val tintIndex = instructionMatches[2].index
            val firstBackgroundIndex = instructionMatches[3].index
            val secondBackgroundIndex = instructionMatches[4].index
            if (!(semanticIndex < tintIndex &&
                    tintIndex < firstBackgroundIndex &&
                    firstBackgroundIndex < secondBackgroundIndex)) {
                throw IllegalStateException(
                    "AMOLED Settings search verification failed for Cromite $CROMITE_VERSION: " +
                        "structural anchors are out of order."
                )
            }

            val semanticInstruction = method.getInstruction(semanticIndex)
            val tintInstruction = method.getInstruction(tintIndex)
            if (semanticInstruction !is FiveRegisterInstruction ||
                semanticInstruction.registerCount != 3 ||
                tintInstruction !is FiveRegisterInstruction ||
                tintInstruction.registerCount != 2) {
                throw IllegalStateException(
                    "AMOLED Settings search verification failed for Cromite $CROMITE_VERSION: " +
                        "unexpected SemanticColorUtils/setTint register shape."
                )
            }

            val contextRegister = semanticInstruction.registerC
            val colorRegister = tintInstruction.registerD
            val searchTintSmali = when (selectedTheme) {
                THEME_BLACK -> "const v$colorRegister, -0x1000000"
                THEME_WHITE -> "const v$colorRegister, -0x1"
                else -> """
                    const/4 v$colorRegister, 0x0
                    invoke-static {v$contextRegister, v$colorRegister}, Lq72;->b(Landroid/content/Context;Z)I
                    move-result v$colorRegister
                """.trimIndent()
            }
            method.addInstructions(tintIndex, searchTintSmali)
        }

        // Dynamic Material colors would re-introduce tinted gray/colored surfaces.
        dynamicColorsApplyFingerprint.method.addInstructions(0, "return-void")
    }
}
