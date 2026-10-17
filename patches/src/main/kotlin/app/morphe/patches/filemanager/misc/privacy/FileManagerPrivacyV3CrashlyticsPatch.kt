package app.morphe.patches.filemanager.misc.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Privacy v3 stage 1: disable the Crashlytics automatic collection gate.
 *
 * JADX File Manager Plus 3.8.2:
 * ax.q8.x.d(): synchronized boolean
 * Checks explicit collection preference or global Firebase setting.
 *
 * No changes to Firebase Installations, cloud authentication, advertising,
 * Premium entitlement, Privacy v1/v2 or Pure AMOLED v6.
 *
 * Runtime and network validation are still required.
 */
private object CrashlyticsCollectionEnabledFingerprint : Fingerprint(
    definingClass = "Lax/q8/x;",
    name = "d",
    returnType = "Z",
    parameters = emptyList(),
)

@Suppress("unused")
val fileManagerPrivacyV3CrashlyticsPatch = bytecodePatch(
    name = "File Manager Plus - Privacy v3 (Crashlytics experimental)",
    description = "Desactiva la recopilación automática de Crashlytics mediante su control interno. No bloquea todavía AdMob.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        CrashlyticsCollectionEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """.trimIndent()
        )
    }
}
