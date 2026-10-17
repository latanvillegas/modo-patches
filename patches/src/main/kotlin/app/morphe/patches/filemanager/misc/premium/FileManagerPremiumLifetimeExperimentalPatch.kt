package app.morphe.patches.filemanager.misc.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Local Premium feature gate for File Manager Plus 3.8.2.
 *
 * This gate is evaluated locally without a network request. It does not create
 * a purchased entitlement, alter signed receipts, or modify Google Play Billing.
 * PaymentActivity and license-manager paths remain separate.\n * The PaymentActivity fingerprint is intentionally not applied.
 *
 * Keep this patch independent of the eight existing privacy/AMOLED patches.
 */
private object PaymentActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/alphainventor/filemanager/activity/PaymentActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private object PremiumNoExpiryFingerprint : Fingerprint(
    definingClass = "Lax/y3/h;",
    name = "j",
    returnType = "Z",
    parameters = emptyList(),
)

private object PremiumNoSubscriptionManagementFingerprint : Fingerprint(
    definingClass = "Lax/y3/h;",
    name = "i",
    returnType = "Z",
    parameters = emptyList(),
)

private object PremiumFeatureGateFingerprint : Fingerprint(
    definingClass = "Lax/y3/h;",
    name = "g",
    returnType = "Z",
    parameters = emptyList(),
)

private val fileManagerPremiumLifetimeLabelPatch = resourcePatch(
    name = "File Manager Plus - Premium lifetime label",
    description = "Actualiza únicamente el título Premium para la interfaz local.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))

    execute {
        // Android falls back to res/values when a locale has no override.
        // Keep the original resource identifier used by Settings and PaymentActivity.
        for (path in listOf("res/values/strings.xml", "res/values-es/strings.xml")) {
            val resourceFile = get(path)
            if (!resourceFile.exists()) continue
            document(path).use { document ->
                val nodes = document.documentElement.getElementsByTagName("string")
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("name") == "title_premium") {
                        element.textContent = "Premium Vitalicio"
                    }
                }
            }
        }
    }
}

@Suppress("unused")
val fileManagerPremiumLifetimeExperimentalPatch = bytecodePatch(
    name = "File Manager Plus - Premium (local feature gate experimental)",
    description = "Activa el control local principal de funciones Premium sin consulta de red en ese control. Oculta controles de caducidad/suscripción en la interfaz Premium; no crea una licencia vitalicia firmada ni elimina Billing.",
    default = false,
) {
    compatibleWith("com.alphainventor.filemanager"("3.8.2"))
    dependsOn(fileManagerPremiumLifetimeLabelPatch)

    execute {
        PremiumNoExpiryFingerprint.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        PremiumNoSubscriptionManagementFingerprint.method.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        PremiumFeatureGateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )
    }
}
