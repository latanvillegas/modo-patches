package app.morphe.patches.tv360

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * Suppress only explicitly verified content-click analytics in TV360.
 * Login, payment, account, player, and other event sources are untouched.
 */
@Suppress("unused")
val tv360TelemetryPatch = bytecodePatch(
    name = "TV360 Bitel - Content Analytics Controls",
    description = "Suppresses selected content-click analytics events without changing navigation.",
    default = false,
) {
    compatibleWith("com.bitel.tv360"("3.5"))

    execute {
        val method = Fingerprint(
            definingClass = "Lcom/viettel/tv360/ui/home/ContentItemClickListener;",
            name = "onClick",
            returnType = "V",
            parameters = emptyList(),
        ).method
        val implementation = method.implementation
            ?: throw PatchException("TV360 telemetry: onClick implementation missing")
        val instructions = implementation.instructions.toList()
        val dispatch =
            "Lcom/viettel/tv360/common/util/v;->d(Ljava/lang/String;Ljava/lang/String;)V"
        val target = instructions.withIndex().filter {
            it.value.opcode == Opcode.INVOKE_STATIC &&
                (it.value as? ReferenceInstruction)?.reference?.toString() == dispatch
        }
        if (target.size != 7) {
            throw PatchException(
                "TV360 telemetry: expected 7 content click dispatches, found ${target.size}"
            )
        }
        // Replace only analytics invocations with no-op. Do not change
        // branches, register assignments, click handling, or navigation.
        for (index in target.map { it.index }.sortedDescending()) {
            method.replaceInstruction(index, "nop")
        }
    }
}
