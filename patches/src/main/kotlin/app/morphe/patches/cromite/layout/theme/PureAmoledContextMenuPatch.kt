package app.morphe.patches.cromite.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

// Reconstruction based on PureAmoledContextMenuPatchKt.class from 1.50.0.
// Fail closed if Cromite's verified layout/instruction structure has changed.
private val contextMenuSurfaceFingerprint = Fingerprint(
    definingClass = "Lg03;",
    name = "b",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/util/List;", "Landroid/view/View;", "Lh75;", "Ll75;"),
    filters = listOf(
        methodCall("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;", Opcode.INVOKE_VIRTUAL),
        methodCall("Landroid/view/View;->findViewById(I)Landroid/view/View;", Opcode.INVOKE_VIRTUAL),
    ),
)

private fun contextMenuBackgroundSmali(theme: String, viewRegister: Int, scratchRegister: Int): String {
    val view = "v$viewRegister"
    val scratch = "v$scratchRegister"
    return when (theme) {
        "black" -> """
            const $scratch, -0x1000000
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        "white" -> """
            const $scratch, -0x1
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
        else -> """
            invoke-virtual {$view}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
            move-result-object $scratch
            invoke-virtual {$scratch}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
            move-result-object $scratch
            iget $scratch, $scratch, Landroid/content/res/Configuration;->uiMode:I
            and-int/lit8 $scratch, $scratch, 0x20
            ushr-int/lit8 $scratch, $scratch, 0x5
            neg-int $scratch, $scratch
            ushr-int/lit8 $scratch, $scratch, 0x8
            not-int $scratch, $scratch
            invoke-virtual {$view, $scratch}, Landroid/view/View;->setBackgroundColor(I)V
        """.trimIndent()
    }
}

@Suppress("unused")
val pureAmoledContextMenuPatch = bytecodePatch(
    name = "AMOLED - Context menu",
    description = "Applies Device default / Pure Black / Pure White to the verified Cromite context-menu content surface while preserving the transparent outer dialog window.",
) {
    compatibleWith("org.cromite.cromite"("153.0.8010.37"))
    val browserTheme = recoveredBrowserThemeOption

    execute {
        val matches = contextMenuSurfaceFingerprint.instructionMatches
        check(matches.size == 2) {
            "AMOLED Context menu: expected 2 structural anchors, found ${matches.size}"
        }
        val inflateIndex = matches[0].index
        val findViewIndex = matches[1].index
        check(inflateIndex < findViewIndex) {
            "AMOLED Context menu bytecode verification failed: structural anchors are out of order."
        }
        val method = contextMenuSurfaceFingerprint.method
        val invoke = method.getInstruction(inflateIndex)
        val layout = method.getInstruction(inflateIndex - 2)
        val result = method.getInstruction(inflateIndex + 1)
        check(
            invoke.opcode == Opcode.INVOKE_VIRTUAL &&
                invoke is FiveRegisterInstruction &&
                invoke.registerCount == 3 &&
                layout is OneRegisterInstruction &&
                layout is WideLiteralInstruction &&
                layout.wideLiteral == 2131624183L &&
                invoke.registerD == layout.registerA &&
                result.opcode == Opcode.MOVE_RESULT_OBJECT &&
                result is OneRegisterInstruction
        ) {
            "AMOLED Context menu bytecode verification failed: layout 0x7f0e00f7 inflate structure differs from verified APK."
        }
        val viewRegister = (result as OneRegisterInstruction).registerA
        val scratchRegister = (layout as OneRegisterInstruction).registerA
        method.addInstructions(
            inflateIndex + 2,
            contextMenuBackgroundSmali(browserTheme.value ?: "device", viewRegister, scratchRegister),
        )
    }
}
