package app.morphe.patches.lawnchair.layout.icons

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Experimental, independently selectable bitmap-resolution correction.
 *
 * The original Lawnchair 15 Beta 3 LauncherIcons.Pool.obtain() passes
 * iconBitmapSize, whereas IconCache uses max(iconBitmapSize,
 * allAppsIconBitmapSize). Align the factory's bitmap size with the cache.
 *
 * No visual icon dimensions, shapes, filters or colors are changed.
 * v0 temporarily holds iconBitmapSize and is restored to mPool before
 * the constructor invocation. This requires no new registers.
 */
@Suppress("unused")
val lawnchairIconBitmapResolutionExperimentalPatch = bytecodePatch(
    name = "Lawnchair - Icon Bitmap Resolution (Experimental)",
    description = "Use the larger workspace/all-apps bitmap size in LauncherIcons pool; icon appearance and AMOLED patches remain unchanged.",
    default = false,
) {
    compatibleWith("app.lawnchair"("15.Beta 3"))
    execute {
        val poolType = "Lcom/android/launcher3/icons/LauncherIcons\$Pool;"
        val profileType = "Lcom/android/launcher3/InvariantDeviceProfile;"
        var matchedMethods = 0
        var matchedAnchors = 0

        classDefForEach { classDef ->
            if (classDef.type != poolType) return@classDefForEach
            val mutableClass = mutableClassDefBy(classDef)
            classDef.methods.forEach { method ->
                if (method.name != "obtain" ||
                    method.returnType != "Lcom/android/launcher3/icons/LauncherIcons;" ||
                    method.parameterTypes.isNotEmpty()) return@forEach

                matchedMethods++
                val instructions = method.implementation?.instructions?.toList()
                    ?: error("Lawnchair: LauncherIcons.Pool.obtain has no implementation")

                for (i in 1 until instructions.size - 1) {
                    val insn = instructions[i]
                    if (insn.opcode != Opcode.IGET) continue
                    val field = (insn as? ReferenceInstruction)?.reference as? FieldReference ?: continue
                    val regs = insn as? TwoRegisterInstruction ?: continue
                    if (field.definingClass != profileType ||
                        field.name != "iconBitmapSize" ||
                        field.type != "I" ||
                        regs.registerA != 1 || regs.registerB != 1) continue

                    // The audited instruction must immediately follow the
                    // fillResIconDpi read and precede the LauncherIcons ctor.
                    val previous = instructions[i - 1]
                    val prevField = (previous as? ReferenceInstruction)?.reference as? FieldReference
                    val prevRegs = previous as? TwoRegisterInstruction
                    check(previous.opcode == Opcode.IGET &&
                          prevField?.definingClass == profileType &&
                          prevField.name == "fillResIconDpi" &&
                          prevRegs?.registerA == 4 && prevRegs.registerB == 1) {
                        "Lawnchair: unexpected pool DPI anchor"
                    }

                    val next = instructions[i + 1]
                    val ctor = (next as? ReferenceInstruction)?.reference as? MethodReference
                    check(next.opcode == Opcode.INVOKE_DIRECT &&
                          ctor?.definingClass == "Lcom/android/launcher3/icons/LauncherIcons;" &&
                          ctor.name == "<init>" &&
                          ctor.parameterTypes.map { it.toString() } ==
                              listOf("Landroid/content/Context;", "I", "I",
                                  "Ljava/util/concurrent/ConcurrentLinkedQueue;")) {
                        "Lawnchair: unexpected LauncherIcons constructor anchor"
                    }

                    matchedAnchors++
                    check(matchedAnchors == 1) { "Lawnchair: ambiguous icon pool bitmap anchor" }
                    val mutableMethod = mutableClass.findMutableMethodOf(method)

                    // Keep v1 as the profile object until both fields are read.
                    // v0 held mPool; restore it before the original constructor.
                    mutableMethod.replaceInstruction(
                        i, "iget v0, v1, $profileType->iconBitmapSize:I"
                    )
                    mutableMethod.addInstructions(
                        i + 1,
                        """
                        iget v1, v1, $profileType->allAppsIconBitmapSize:I
                        invoke-static {v0, v1}, Ljava/lang/Math;->max(II)I
                        move-result v1
                        iget-object v0, p0, $poolType->mPool:Ljava/util/concurrent/ConcurrentLinkedQueue;
                        """.trimIndent()
                    )
                }
            }
        }

        check(matchedMethods == 1 && matchedAnchors == 1) {
            "Lawnchair: expected one pool obtain and bitmap anchor; methods=$matchedMethods anchors=$matchedAnchors"
        }
    }
}
