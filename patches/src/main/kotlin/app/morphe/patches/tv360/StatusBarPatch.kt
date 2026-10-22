package app.morphe.patches.tv360

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * TV360 Bitel 3.5: hide only the status bar on HomeBoxActivity startup.
 *
 * The existing window-insets listener and decor-fitting logic remain untouched.
 * The patch is opt-in while navigation/gesture and top-margin behavior are tested.
 */
@Suppress("unused")
val tv360StatusBarPatch = bytecodePatch(
    name = "TV360 Bitel - Hide Status Bar",
    description = "Hides the status bar on the home screen without explicitly hiding navigation bars.",
    default = false,
) {
    compatibleWith("com.bitel.tv360"("3.5"))

    execute {
        val method = Fingerprint(
            definingClass = "Lcom/viettel/tv360/ui/home/HomeBoxActivity;",
            name = "onCreate",
            returnType = "V",
            parameters = listOf("Landroid/os/Bundle;"),
        ).method

        val implementation = method.implementation
            ?: throw PatchException("TV360 status bar: onCreate has no implementation")

        val instructions = implementation.instructions.toList()
        val anchorReference =
            "Landroidx/core/view/ViewCompat;->setOnApplyWindowInsetsListener(Landroid/view/View;Landroidx/core/view/OnApplyWindowInsetsListener;)V"
        val anchors = instructions.withIndex().filter {
            it.value.opcode == Opcode.INVOKE_STATIC &&
                (it.value as? ReferenceInstruction)?.reference?.toString() == anchorReference
        }
        if (anchors.size != 1) {
            throw PatchException("TV360 status bar: expected one root insets listener, found ${anchors.size}")
        }
        val anchor = anchors.single().index

        // The activity's listener must already be installed. We inject immediately
        // after that call; the original register allocation is not changed.
        // p0 is the HomeBoxActivity instance, v7/v8 are NOT used as scratch.
        // Android's WindowInsetsController.hide(statusBars) uses platform API 30+.
        // This first implementation intentionally uses a compatibility-safe helper
        // only after confirming an available injection mechanism and API behavior.
        throw PatchException(
            "TV360 status bar: injection intentionally disabled pending verified " +
                "controller/helper integration (listener instruction index=$anchor)"
        )
    }
}
