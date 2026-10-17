package app.morphe.patches.filemanager.misc.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Experimental local Premium feature-gate override for File Manager Plus 3.8.2.
 *
 * JADX audit: ax.y3.h.g():boolean is an application-facing Premium gate,
 * consulted by MainActivity, PaymentActivity and other UI components.
 *
 * This changes only the app-facing feature gate, not Google Play Billing,
 * purchase receipts, account entitlements, subscription records or server
 * verification. It may not unlock features controlled by other gates.
 *
 * Keep separate from Privacy and AMOLED patches to allow independent testing.
 */
private object PremiumFeatureGateFingerprint : Fingerprint(
    definingClass = "Lax/y3/h;",
    name = "g",
    returnType = "Z",
    parameters = emptyList(),
)

@Suppress("unused")
val fileManagerPremiumLifetimeExperimentalPatch = bytecodePatch(
    name = "File Manager Plus - Premium (local feature gate experimental)",
    description = "Habilita el control local principal de funciones Premium; no simula compras ni garantiza todas las funciones. Experimental.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        PremiumFeatureGateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )
    }
}
