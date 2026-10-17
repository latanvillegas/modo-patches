package app.morphe.patches.filemanager.misc.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Experimental Privacy v2: disable Firebase Sessions through its settings gate.
 *
 * JADX for File Manager Plus 3.8.2 identifies:
 * ax.W8.f.d(): boolean — resolves local/remote Sessions enablement,
 * defaulting to true. Both lifecycle registration and event publishing consult it.
 *
 * This patch does not remove Firebase components or alter Privacy v1.
 * Requires fingerprint resolution and on-device regression testing.
 */
private object FirebaseSessionsEnabledFingerprint : Fingerprint(
    definingClass = "Lax/W8/f;",
    name = "d",
    returnType = "Z",
    parameters = emptyList(),
)

@Suppress("unused")
val fileManagerPrivacyV2SessionsPatch = bytecodePatch(
    name = "File Manager Plus - Privacy v2 (Sessions experimental)",
    description = "Desactiva Firebase Sessions mediante su control de configuración; requiere pruebas en Android.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        // Instance method: p0 is always available (this).
        // Reuse its register to avoid requiring additional locals.
        FirebaseSessionsEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """.trimIndent()
        )
    }
}
