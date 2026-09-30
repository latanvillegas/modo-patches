package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val CROMITE_TOOLBAR_VERSION = "153.0.8010.37"

/**
 * Structural fingerprint for ToolbarPhone.updateToolbarBackground(int).
 *
 * This intentionally does not depend on the R8 class or method name. The Chromium
 * implementation reads the current ColorDrawable color and then writes the new color
 * through framework methods whose descriptors survive R8.
 */
private val toolbarBackgroundRuntimeFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/graphics/drawable/ColorDrawable;->getColor()I",
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/graphics/drawable/ColorDrawable;->setColor(I)V",
        ),
    ),
)

private const val FORCE_NIGHT_TOOLBAR_BLACK_SMALI = """
    invoke-virtual {p0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
    move-result-object v0
    iget v0, v0, Landroid/content/res/Configuration;->uiMode:I
    and-int/lit8 v0, v0, 0x30
    xor-int/lit8 v0, v0, 0x20
    if-nez v0, :pure_toolbar_keep_color
    const p1, -0x1000000
    :pure_toolbar_keep_color
"""

@Suppress("unused")
val pureAmoledToolbarRuntimePatch = bytecodePatch(
    name = "Pure AMOLED toolbar runtime fix",
    description = "Forces ToolbarPhone's runtime background to #000000 only while Android is in dark mode. Light mode is left unchanged.",
) {
    compatibleWith("org.cromite.cromite"(CROMITE_TOOLBAR_VERSION))

    execute {
        toolbarBackgroundRuntimeFingerprint.method.addInstructions(
            0,
            FORCE_NIGHT_TOOLBAR_BLACK_SMALI,
        )
    }
}
