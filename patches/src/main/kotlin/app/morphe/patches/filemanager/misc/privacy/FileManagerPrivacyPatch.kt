package app.morphe.patches.filemanager.misc.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * File Manager Plus 3.8.2: ax.y3.k.o(Context) reads "usage_diagnostics".
 *
 * The original method defaults to true (including its exception fallback).
 * The application's analytics event paths and Analytics initialization consult
 * this method. Returning false prevents those paths from opting in.
 *
 * This is deliberately not a blanket network blocker: cloud storage and
 * remote filesystem features must remain functional.
 */
private object UsageDiagnosticsFingerprint : Fingerprint(
    definingClass = "Lax/y3/k;",
    name = "o",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    strings = listOf("usage_diagnostics"),
)

@Suppress("unused")
val fileManagerPrivacyPatch = bytecodePatch(
    name = "File Manager Plus - Privacy",
    description = "Desactiva las estadísticas de uso y los eventos de Analytics controlados por usage_diagnostics en File Manager Plus 3.8.2.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        // Reuse p0, the method's only parameter register, so this injection
        // does not depend on the original method's local-register count.
        // The early return leaves the original implementation untouched.
        UsageDiagnosticsFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """.trimIndent()
        )
    }
}
