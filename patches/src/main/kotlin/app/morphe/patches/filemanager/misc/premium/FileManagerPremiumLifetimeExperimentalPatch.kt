package app.morphe.patches.filemanager.misc.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Local Premium feature gate for File Manager Plus 3.8.2.
 *
 * This gate is evaluated locally without a network request. It does not create
 * a purchased entitlement, alter signed receipts, or modify Google Play Billing.
 * PaymentActivity and license-manager paths remain separate. The onCreate\n * fingerprint below is not applied until its superclass invocation is verified.
 *
 * Keep this patch independent of the eight existing privacy/AMOLED patches.
 */
private object PaymentActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/alphainventor/filemanager/activity/PaymentActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private object PremiumManagedDisplayFingerprint : Fingerprint(
    definingClass = "Lax/y3/h;",
    name = "j",
    returnType = "Z",
    parameters = emptyList(),
)

private object PremiumFeatureGateFingerprint : Fingerprint(
    definingClass = "Lax/y3/h;",
    name = "g",
    returnType = "Z",
    parameters = emptyList(),
)

@Suppress("unused")
val fileManagerPremiumLifetimeExperimentalPatch = bytecodePatch(
    name = "File Manager Plus - Premium (local feature gate experimental)",
    description = "Activa el control local principal de funciones Premium sin consulta de red en ese control. Oculta controles de caducidad/suscripción en la interfaz Premium; no crea una licencia vitalicia firmada ni elimina Billing.",
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
