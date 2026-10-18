package app.morphe.patches.filemanager.music

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * WAV/APIC thumbnail fallback for File Manager Plus 3.8.2.
 * No register-count changes: preserve original source in unused v1,
 * use v7 as the null sentinel, and patch only the terminal null return.
 */
@Suppress("unused")
val fileManagerAudioCoverThumbnailsPatch = bytecodePatch(
    name = "File Manager Plus - Audio Cover Thumbnails",
    description = "Adds WAV/APIC cover thumbnails after native and MediaStore lookup fail; other audio formats retain their original artwork.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))
    extendWith("extensions/filemanager.mpe")

    execute {
        val method = Fingerprint(
            definingClass = "Lcom/alphainventor/filemanager/file/x;",
            name = "s0",
            returnType = "Ljava/io/InputStream;",
            parameters = listOf("Ljava/lang/String;"),
        ).method
        val implementation = method.implementation
            ?: throw PatchException("Audio cover thumbnails: missing s0 implementation")
        if (implementation.registerCount != 10) {
            throw PatchException("Audio cover thumbnails: unexpected register count ${implementation.registerCount}")
        }
        val instructions = implementation.instructions.toList()
        fun uniqueRef(reference: String): Int {
            val hits = instructions.withIndex().filter {
                (it.value as? ReferenceInstruction)?.reference?.toString() == reference
            }
            if (hits.size != 1) throw PatchException("Audio cover thumbnails: expected one $reference, found ${hits.size}")
            return hits.single().index
        }
        val extract = uniqueRef("Lax/z3/B;->k(Landroid/content/Context;Ljava/lang/String;Ljava/io/FileDescriptor;)[B")
        // The actual terminal return is return-object v1 (register 1).
        // The original path is retained in v1 until fallback replaces its value.
        val terminal = instructions.indexOfLast {
            it.opcode == Opcode.RETURN_OBJECT
        }
        val terminalRegister = (instructions.getOrNull(terminal) as? OneRegisterInstruction)?.registerA
        if (terminal <= extract || extract < 1 || terminalRegister != 1) {
            throw PatchException(
                "Audio cover thumbnails: invalid s0 terminal return " +
                    "(extract=$extract, terminal=$terminal, register=$terminalRegister, count=${instructions.size})"
            )
        }

        // The original s0 has a single const/4 v1, 0 before the embedded-art
        // extraction. Save the original filename in v1 instead.
        val init = (0 until extract).lastOrNull {
            instructions[it].opcode == Opcode.CONST_4 &&
                (instructions[it] as? OneRegisterInstruction)?.registerA == 1
        } ?: throw PatchException("Audio cover thumbnails: null initialization not found")

        // The original provider uses move-object p1, v1 on the MediaStore
        // failure path. That must now use v7 (the zero sentinel).
        val nullMove = instructions.withIndex().filter {
            it.value.opcode == Opcode.MOVE_OBJECT &&
                (it.value as? com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction)?.let { i ->
                    i.registerA == 9 && i.registerB == 1
                } == true
        }
        if (nullMove.size != 1) throw PatchException("Audio cover thumbnails: MediaStore null path changed (hits=${nullMove.size})")
        if (init >= extract || nullMove.single().index >= terminal || terminal != instructions.lastIndex) {
            throw PatchException("Audio cover thumbnails: unexpected instruction ordering (init=$init, extract=$extract, nullMove=${nullMove.single().index}, terminal=$terminal)")
        }

        method.replaceInstruction(nullMove.single().index, "move-object p1, v7")
        method.replaceInstruction(extract, "invoke-static {v0, p1, v7}, Lax/z3/B;->k(Landroid/content/Context;Ljava/lang/String;Ljava/io/FileDescriptor;)[B")
        method.replaceInstruction(init, "move-object v1, p1")
        method.addInstruction(init + 1, "const/4 v7, 0x0")

        // All earlier non-null returns remain untouched. Replace the original
        // terminal return so existing :cond_2 branches still reach fallback.
        val end = terminal + 1
        method.replaceInstruction(
            end,
            "invoke-virtual {p0}, Lcom/alphainventor/filemanager/file/m;->p()Landroid/content/Context;",
        )
        method.addInstruction(end + 1, "move-result-object v0")
        method.addInstruction(
            end + 2,
            "invoke-static {v0, v1}, Lapp/morphe/extension/filemanager/AudioCoverThumbnails;->fallback(Landroid/content/Context;Ljava/lang/String;)Ljava/io/InputStream;",
        )
        method.addInstruction(end + 3, "move-result-object v1")
        method.addInstruction(end + 4, "return-object v1")
    }
}
