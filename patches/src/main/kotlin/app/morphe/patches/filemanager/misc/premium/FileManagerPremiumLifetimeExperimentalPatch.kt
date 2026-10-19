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

private object PaymentActivityToolbarTitleFingerprint : Fingerprint(
    definingClass = "Lcom/alphainventor/filemanager/activity/PaymentActivity;",
    name = "j0",
    returnType = "V",
    parameters = emptyList(),
)

private object PaymentActivityPremiumStateFingerprint : Fingerprint(
    definingClass = "Lcom/alphainventor/filemanager/activity/PaymentActivity;",
    name = "y0",
    returnType = "V",
    parameters = listOf("Z"),
)

private object PremiumSettingsTitleFingerprint : Fingerprint(
    definingClass = "Lax/g3/s0;",
    name = "B3",
    returnType = "V",
    parameters = emptyList(),
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
        // Fail explicitly if the decoded resources do not contain the expected
        // string; silently skipping missing files previously concealed failures.
        var modified = 0
        for (path in listOf("res/values/strings.xml", "res/values-es/strings.xml")) {
            if (!get(path).exists()) continue
            document(path).use { document ->
                val nodes = document.documentElement.getElementsByTagName("string")
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("name") == "title_premium") {
                        element.textContent = "Premium Vitalicio"
                        modified++
                    }
                }
            }
        }
        check(modified > 0) {
            "File Manager Plus: title_premium not found in decoded resources; refusing silent no-op"
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
        // B3() resolves payment_screen into v2. Replace its title with a
        // CharSequence after the original method has finished configuring it.
        // This avoids relying on resource recompilation for the Settings label.
        val settingsMethod = PremiumSettingsTitleFingerprint.method
        val returnIndex = settingsMethod.implementation!!.instructions.size - 1
        settingsMethod.addInstructions(
            returnIndex,
            """
                iget-object v2, p0, Lax/g3/s0;->u1:Landroidx/preference/PreferenceScreen;
                const-string v1, "payment_screen"
                invoke-virtual {v2, v1}, Landroidx/preference/PreferenceGroup;->V0(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                move-result-object v2
                const-string v1, "Premium Vitalicio"
                invoke-virtual {v2, v1}, Landroidx/preference/Preference;->M0(Ljava/lang/CharSequence;)V
            """.trimIndent()
        )
        // j0() and y0(Z) both have four local registers in File Manager
        // Plus 3.8.2. Their Premium branches use the title_premium resource.
        // Set the title directly after the existing UI initialization, so the
        // resource fallback does not overwrite the desired local label.
        val paymentToolbarMethod = PaymentActivityToolbarTitleFingerprint.method
        paymentToolbarMethod.addInstructions(
            paymentToolbarMethod.implementation!!.instructions.size - 1,
            """
                invoke-static {}, Lax/y3/h;->g()Z
                move-result v0
                if-eqz v0, :premium_title_done
                const-string v0, "Premium Vitalicio"
                invoke-virtual {p0, v0}, Landroid/app/Activity;->setTitle(Ljava/lang/CharSequence;)V
                :premium_title_done
            """.trimIndent()
        )

        // y0(Z) may run again after onCreate, resetting both the toolbar title
        // and the feature-heading TextView. Its Premium branch is selected by
        // the same local gate used in the original implementation.
        val paymentStateMethod = PaymentActivityPremiumStateFingerprint.method
        paymentStateMethod.addInstructions(
            0,
            """
                invoke-static {}, Lax/y3/h;->g()Z
                move-result v0
                if-eqz v0, :premium_state_title_done
                const-string v0, "Premium Vitalicio"
                invoke-virtual {p0, v0}, Landroid/app/Activity;->setTitle(Ljava/lang/CharSequence;)V
                iget-object v1, p0, Lcom/alphainventor/filemanager/activity/PaymentActivity;->w0:Landroid/widget/TextView;
                if-eqz v1, :premium_state_title_done
                invoke-virtual {v1, v0}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                :premium_state_title_done
            """.trimIndent()
        )

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
