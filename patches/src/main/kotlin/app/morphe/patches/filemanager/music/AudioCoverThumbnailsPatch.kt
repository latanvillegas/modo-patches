package app.morphe.patches.filemanager.music

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Independent WAV cover fallback for file-list thumbnails.
 * Targets File Manager Plus 3.8.2, original DEX class file/x.
 *
 * This patch deliberately runs after BOTH native embedded-art extraction
 * and MediaStore album-art lookup have returned no image.
 */
@Suppress("unused")
val fileManagerAudioCoverThumbnailsPatch = bytecodePatch(
    name = "File Manager Plus - Audio Cover Thumbnails",
    description = "Shows embedded WAV/APIC artwork in file-list thumbnails only when the original cover lookup fails. Other audio formats remain unchanged.",
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
            ?: throw PatchException("Audio cover thumbnails: s0 has no implementation")

        // Original method has .locals 8 and two parameter registers.
        // Preserve the original source in the new local v8 before p1 is reused.
        if (implementation.registerCount != 10) {
            throw PatchException(
                "Audio cover thumbnails: expected 10 registers, got ${implementation.registerCount}",
            )
        }

        // Preserve the original DEX instructions and branch labels.
        // Only the terminal null return is replaced; non-null returns stay untouched.
        val returns = implementation.instructions.withIndex().filter {
            val instruction: Instruction = it.value
            instruction.opcode.name == "RETURN_OBJECT" &&
                (instruction as? OneRegisterInstruction)?.registerA == 1
        }.toList()
        if (returns.size != 1) {
            throw PatchException(
                "Audio cover thumbnails: expected one return-object v1, got ${returns.size}",
            )
        }
        val lastReturn = returns.single().index
        if (lastReturn != implementation.instructions.size - 1) {
            throw PatchException("Audio cover thumbnails: null return is not terminal")
        }

        // Expanding locals shifts physical parameter registers, but p0/p1
        // references are resolved by the patcher's instruction assembler.
        implementation.registerCount = 11
        method.addInstruction(0, "move-object v8, p1")

        // Inserting at the original terminal return keeps incoming :cond_2
        // branches attached to the fallback instruction.
        val terminal = lastReturn + 1
        method.replaceInstruction(
            terminal,
            "invoke-static {v1, p0, v8}, Lapp/morphe/extension/filemanager/AudioCoverThumbnails;->fallback(Ljava/io/InputStream;Ljava/lang/Object;Ljava/lang/String;)Ljava/io/InputStream;",
        )
        method.addInstruction(terminal + 1, "move-result-object v1")
        method.addInstruction(terminal + 2, "return-object v1")
    }
}
