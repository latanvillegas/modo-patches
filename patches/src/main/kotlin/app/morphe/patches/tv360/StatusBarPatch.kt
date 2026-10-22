package app.morphe.patches.tv360

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * Hide the status bar in TV360's home activity without hiding navigation bars.
 *
 * The original decor fitting, root insets listener, padding and PiP logic remain
 * unchanged. The injected calls use v7/v8 after the original listener setup.
 */
@Suppress("unused")
val tv360StatusBarPatch = bytecodePatch(
    name = "TV360 Bitel - Hide Status Bar",
    description = "Hides the home screen status bar while preserving navigation bars and gestures.",
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
            ?: throw PatchException("TV360 status bar: missing onCreate implementation")
        val instructions = implementation.instructions.toList()
        val listenerRef =
            "Landroidx/core/view/ViewCompat;->setOnApplyWindowInsetsListener(Landroid/view/View;Landroidx/core/view/OnApplyWindowInsetsListener;)V"
        val anchors = instructions.withIndex().filter {
            it.value.opcode == Opcode.INVOKE_STATIC &&
                (it.value as? ReferenceInstruction)?.reference?.toString() == listenerRef
        }
        if (anchors.size != 1) {
            throw PatchException("TV360 status bar: expected one insets listener, found ${anchors.size}")
        }
        val anchor = anchors.single().index
        val nextReference = instructions.drop(anchor + 1).firstOrNull {
            it is ReferenceInstruction
        } as? ReferenceInstruction
        val expectedNext =
            "Lcom/viettel/tv360/ui/home/HomeBoxActivity;->getDataFields()V"
        if (nextReference?.reference?.toString() != expectedNext) {
            throw PatchException("TV360 status bar: unexpected flow after insets listener")
        }
        val before = instructions.take(anchor)
        val windowRef =
            "Landroid/app/Activity;->getWindow()Landroid/view/Window;"
        if (before.none {
                (it as? ReferenceInstruction)?.reference?.toString() == windowRef
            }) {
            throw PatchException("TV360 status bar: expected window setup before listener")
        }
        if (implementation.registerCount < 11) {
            throw PatchException("TV360 status bar: v7/v8 local registers not available")
        }

        // Insert immediately after the existing listener installation.
        // Insert as one block to preserve the intended instruction order.
        val injected = listOf(
            "invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;",
            "move-result-object v7",
            "invoke-virtual {v7}, Landroid/view/Window;->getDecorView()Landroid/view/View;",
            "move-result-object v8",
            "invoke-static {v7, v8}, Landroidx/core/view/WindowCompat;->getInsetsController(Landroid/view/Window;Landroid/view/View;)Landroidx/core/view/WindowInsetsControllerCompat;",
            "move-result-object v8",
            "invoke-static {}, Landroidx/core/view/WindowInsetsCompat\$Type;->statusBars()I",
            "move-result v7",
            "invoke-virtual {v8, v7}, Landroidx/core/view/WindowInsetsControllerCompat;->hide(I)V",
        )
        method.addInstructions(anchor + 1, injected.joinToString("\n"))
    }
}
